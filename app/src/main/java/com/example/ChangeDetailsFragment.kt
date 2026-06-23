package com.example

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.databinding.FragmentChangeDetailsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangeDetailsFragment : Fragment() {
    private var _binding: FragmentChangeDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangeDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ruleId = arguments?.getInt("ruleId") ?: -1

        if(ruleId != -1) {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(requireContext())
                val rule = db.trackingRuleDao().getRuleById(ruleId)
                val history = db.trackingHistoryDao().getHistoryForRule(ruleId).firstOrNull()?.firstOrNull()
                
                withContext(Dispatchers.Main) {
                    if (rule != null) {
                        binding.tvDetailsUrl.text = "URL: ${rule.url}"
                        binding.btnLiveView.setOnClickListener {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(rule.url))
                            startActivity(browserIntent)
                        }
                    }
                    if (history != null) {
                        if (!history.aiSummary.isNullOrEmpty()) {
                            binding.tvAiSummary.visibility = View.VISIBLE
                            binding.tvAiSummary.text = "AI Alert: ${history.aiSummary}"
                        }
                        binding.tvDetailsText.text = buildDiff(history.oldText, history.newText)
                    } else if (rule != null) {
                        binding.tvDetailsText.text = "Current Text:\n\n${rule.lastKnownText}"
                    }
                }
            }
        }
    }

    private fun buildDiff(oldText: String, newText: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val deletedSpan = StrikethroughSpan()
        val deletedColor = BackgroundColorSpan(Color.parseColor("#FFCDD2"))
        val newColor = BackgroundColorSpan(Color.parseColor("#C8E6C9"))

        builder.append("Old Data:\n")
        val startOld = builder.length
        builder.append(oldText)
        builder.setSpan(deletedSpan, startOld, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(deletedColor, startOld, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        builder.append("\n\nNew Data:\n")
        val startNew = builder.length
        builder.append(newText)
        builder.setSpan(newColor, startNew, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return builder
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
