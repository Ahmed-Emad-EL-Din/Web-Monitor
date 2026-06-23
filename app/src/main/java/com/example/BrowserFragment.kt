package com.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.TrackingRule
import com.example.databinding.FragmentBrowserBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowserFragment : Fragment(), TrackingBottomSheet.TrackingListener {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!

    private var pendingSyncFreq: Int = 15
    private var pendingIsPremium: Boolean = false
    private var pendingAiPrompt: String? = null
    private var pendingRequiresJS: Boolean = false
    private var pendingListenerIds: List<Int> = emptyList()

    private val tabs = mutableListOf<WebView>()
    private var currentTabIndex = -1

    private val currentWebView: WebView?
        get() = if (currentTabIndex in tabs.indices) tabs[currentTabIndex] else null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.toolbar.inflateMenu(R.menu.browser_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_history -> {
                    HistoryBottomSheet().show(childFragmentManager, "HistoryBottomSheet")
                    true
                }
                R.id.action_passwords -> {
                    PasswordsBottomSheet().show(childFragmentManager, "PasswordsBottomSheet")
                    true
                }
                R.id.action_captcha -> {
                    CaptchaBottomSheet().show(childFragmentManager, "CaptchaBottomSheet")
                    true
                }
                else -> false
            }
        }

        binding.btnBack.setOnClickListener {
            if (currentWebView?.canGoBack() == true) {
                currentWebView?.goBack()
            }
        }

        binding.btnForward.setOnClickListener {
            if (currentWebView?.canGoForward() == true) {
                currentWebView?.goForward()
            }
        }

        binding.btnRefresh.setOnClickListener {
            currentWebView?.reload()
        }

        binding.btnTabs.setOnClickListener {
            addNewTab("https://www.google.com")
            Toast.makeText(requireContext(), "Opened new tab", Toast.LENGTH_SHORT).show()
        }

        binding.fabTrack.setOnClickListener {
            val bottomSheet = TrackingBottomSheet()
            bottomSheet.setTrackingListener(this)
            bottomSheet.show(parentFragmentManager, "TrackingBottomSheet")
        }

        if (tabs.isEmpty()) {
            addNewTab("https://www.google.com")
        } else {
            showTab(currentTabIndex)
        }
    }

    private fun addNewTab(url: String) {
        val webView = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            addJavascriptInterface(WebAppInterface { selector, text ->
                onElementSelectedFromJs(selector, text)
            }, "Android")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                    if (this@apply == currentWebView) {
                        binding.tvUrl.text = url
                    }
                    
                    url?.let {
                        val pageTitle = view?.title
                        lifecycleScope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(requireContext())
                            db.browsingHistoryDao().insert(com.example.data.BrowsingHistory(url = it, title = pageTitle))
                        }
                    }
                }
            }
        }
        tabs.add(webView)
        showTab(tabs.size - 1)
        webView.loadUrl(url)
    }

    private fun showTab(index: Int) {
        if (index !in tabs.indices) return
        currentTabIndex = index
        binding.webViewContainer.removeAllViews()
        val webView = tabs[index]
        binding.webViewContainer.addView(webView)
        binding.tvUrl.text = webView.url ?: "Loading..."
    }

    override fun onTrackWholePage(syncFreqMin: Int, isPremium: Boolean, aiPrompt: String?, requiresJS: Boolean, listenerIds: List<Int>) {
        val currentUrl = currentWebView?.url ?: return
        saveRule(
            TrackingRule(
                url = currentUrl,
                cssSelector = null,
                isTrackWholePage = true,
                syncFrequencyMin = syncFreqMin,
                isPremiumRule = isPremium,
                aiConditionPrompt = aiPrompt,
                lastKnownText = "", // Or fetch body text
                requiresJS = requiresJS
            ),
            listenerIds
        )
    }

    override fun onTrackElements(syncFreqMin: Int, isPremium: Boolean, aiPrompt: String?, requiresJS: Boolean, listenerIds: List<Int>) {
        pendingSyncFreq = syncFreqMin
        pendingIsPremium = isPremium
        pendingAiPrompt = aiPrompt
        pendingRequiresJS = requiresJS
        pendingListenerIds = listenerIds

        binding.bannerContainer.visibility = View.VISIBLE
        injectInspectorJs()
    }

    private fun injectInspectorJs() {
        val js = """
            javascript:(function() {
                var handler = function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    var el = e.target;
                    el.style.border = '3px dashed #14B8A6';
                    el.style.backgroundColor = 'rgba(20, 184, 166, 0.1)';
                    
                    var path = '';
                    var current = el;
                    while(current && current.nodeType === Node.ELEMENT_NODE) {
                        var selector = current.nodeName.toLowerCase();
                        if (current.id) {
                            selector += '#' + current.id;
                            path = selector + (path ? ' > ' + path : '');
                            break;
                        } else if (current.className && typeof current.className === 'string') {
                            var cls = current.className.trim().split(/\s+/).join('.');
                            if(cls) selector += '.' + cls;
                        }
                        path = selector + (path ? ' > ' + path : '');
                        current = current.parentNode;
                    }
                    Android.onElementSelected(path, el.innerText || '');
                    
                    document.removeEventListener('touchstart', handler, true);
                    document.removeEventListener('click', handler, true);
                };
                
                document.addEventListener('touchstart', handler, true);
                document.addEventListener('click', handler, true);
            })();
        """.trimIndent()
        currentWebView?.evaluateJavascript(js, null)
    }

    private fun onElementSelectedFromJs(selector: String, text: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.bannerContainer.visibility = View.GONE
            val currentUrl = currentWebView?.url ?: return@launch
            saveRule(
                TrackingRule(
                    url = currentUrl,
                    cssSelector = selector,
                    isTrackWholePage = false,
                    syncFrequencyMin = pendingSyncFreq,
                    isPremiumRule = pendingIsPremium,
                    aiConditionPrompt = pendingAiPrompt,
                    lastKnownText = text.take(100), // Optional truncate
                    requiresJS = pendingRequiresJS
                ),
                pendingListenerIds
            )
        }
    }

    private fun saveRule(rule: TrackingRule, listenerIds: List<Int>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val ruleId = db.trackingRuleDao().insertRule(rule).toInt()
            
            listenerIds.forEach { listenerId ->
                db.ruleListenerCrossRefDao().insert(com.example.data.RuleListenerCrossRef(ruleId, listenerId))
            }
            
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<TrackingWorker>(
                rule.syncFrequencyMin.toLong(), java.util.concurrent.TimeUnit.MINUTES
            )
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .setInputData(androidx.work.workDataOf("RULE_ID" to ruleId))
            .build()
            
            androidx.work.WorkManager.getInstance(requireContext())
                .enqueueUniquePeriodicWork(
                    "track_$ruleId",
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Tracking rule saved!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadUrl(url: String) {
        if (currentWebView == null) {
            addNewTab(url)
        } else {
            currentWebView?.loadUrl(url)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class WebAppInterface(private val onSelected: (String, String) -> Unit) {
        @JavascriptInterface
        fun onElementSelected(selector: String, text: String) {
            onSelected(selector, text)
        }
    }
}
