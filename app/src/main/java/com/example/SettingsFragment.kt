package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import com.example.data.TrackingRule
import com.example.databinding.FragmentSettingsBinding
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import org.json.JSONObject
import com.example.data.TelegramListener
import java.util.UUID
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.delay

import androidx.appcompat.app.AlertDialog
import com.example.data.RuleListenerCrossRef

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var appPreferences: AppPreferences

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                exportData(uri)
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importData(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appPreferences = AppPreferences(requireContext())

        binding.etApiKey.setText(appPreferences.geminiApiKey)

        val models = arrayOf("gemini-2.5-flash", "gemini-2.5-pro")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, models)
        binding.actvModel.setAdapter(adapter)
        binding.actvModel.setText(appPreferences.geminiModel, false)

        binding.btnTestConnection.setOnClickListener {
            val key = binding.etApiKey.text.toString()
            val modelName = binding.actvModel.text.toString()

            if (key.isBlank()) {
                Toast.makeText(context, "Please enter an API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            appPreferences.geminiApiKey = key
            appPreferences.geminiModel = modelName

            testConnection(key, modelName)
        }

        binding.btnBatteryOpt.setOnClickListener {
            requestBatteryOptimization()
        }

        binding.btnExportData.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "web_monitor_rules.json")
            }
            exportLauncher.launch(intent)
        }

        binding.btnImportData.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            importLauncher.launch(intent)
        }

        binding.etTelegramBotToken.setText(appPreferences.telegramBotToken)

        binding.btnTestTelegram.setOnClickListener {
            val token = binding.etTelegramBotToken.text.toString()
            if (token.isBlank()) {
                showError("Please enter a Telegram Bot Token")
                return@setOnClickListener
            }
            testTelegramConnection(token)
        }

        binding.rvTelegramListeners.layoutManager = LinearLayoutManager(requireContext())
        val telegramAdapter = TelegramListenerAdapter(
            onDeleteClicked = { listener ->
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(requireContext()).telegramListenerDao().delete(listener)
                }
            },
            onItemClicked = { listener ->
                showTrackerSelectionDialog(listener)
            }
        )
        binding.rvTelegramListeners.adapter = telegramAdapter

        lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).telegramListenerDao().getAllListeners().collect {
                telegramAdapter.submitList(it)
            }
        }

        binding.btnAddListener.setOnClickListener {
            val token = appPreferences.telegramBotToken
            val botUsername = appPreferences.telegramBotUsername
            if (token.isNullOrBlank() || botUsername.isNullOrBlank()) {
                showError("Please Test & Connect your bot token first")
                return@setOnClickListener
            }
            startMagicLinkFlow(token, botUsername)
        }
    }

    private fun showTrackerSelectionDialog(listener: TelegramListener) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val allRules = db.trackingRuleDao().getAllRulesList()
            val activeRuleIds = db.ruleListenerCrossRefDao().getRulesForListener(listener.id).toSet()

            val ruleNames = allRules.map { if (it.url.length > 40) it.url.take(40) + "..." else it.url }.toTypedArray()
            val checkedItems = allRules.map { activeRuleIds.contains(it.id) }.toBooleanArray()

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Trackers for ${listener.listenerName}")
                    .setMultiChoiceItems(ruleNames, checkedItems) { _, which, isChecked ->
                        val rule = allRules[which]
                        lifecycleScope.launch(Dispatchers.IO) {
                            if (isChecked) {
                                db.ruleListenerCrossRefDao().insert(RuleListenerCrossRef(rule.id, listener.id))
                            } else {
                                db.ruleListenerCrossRefDao().delete(RuleListenerCrossRef(rule.id, listener.id))
                            }
                        }
                    }
                    .setPositiveButton("Done", null)
                    .show()
            }
        }
    }

    private fun testTelegramConnection(token: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$token/getMe")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val json = JSONObject(responseBody)
                        if (json.getBoolean("ok")) {
                            val botUsername = json.getJSONObject("result").getString("username")
                            withContext(Dispatchers.Main) {
                                appPreferences.telegramBotToken = token
                                appPreferences.telegramBotUsername = botUsername
                                showSuccess("Connected to @$botUsername")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                showError("Invalid token")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showError("Connection failed")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Error: ${e.message}")
                }
            }
        }
    }

    private var isPolling = false
    private var pollingDialog: AlertDialog? = null

    private fun startMagicLinkFlow(token: String, botUsername: String) {
        val uuid = UUID.randomUUID().toString()
        val tgLink = "tg://resolve?domain=$botUsername&start=$uuid"
        val httpsLink = "https://t.me/$botUsername?start=$uuid"
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_listener, null)
        val tvHttpsLink = dialogView.findViewById<android.widget.TextView>(R.id.tvHttpsLink)
        val btnCopy = dialogView.findViewById<android.widget.Button>(R.id.btnCopy)
        val btnConnectMyself = dialogView.findViewById<android.widget.Button>(R.id.btnConnectMyself)
        
        tvHttpsLink.text = httpsLink
        
        btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Telegram Link", httpsLink)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        
        btnConnectMyself.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tgLink)))
            } catch (e: Exception) {
                showError("Telegram is not installed")
            }
        }
        
        pollingDialog = AlertDialog.Builder(requireContext())
            .setTitle("Add Telegram Listener")
            .setView(dialogView)
            .setNegativeButton("Cancel") { _, _ -> isPolling = false }
            .setOnDismissListener { isPolling = false }
            .show()

        if (isPolling) return
        isPolling = true

        lifecycleScope.launch(Dispatchers.IO) {
            var lastUpdateId = 0L
            val client = OkHttpClient()

            while (isPolling) {
                try {
                    val request = Request.Builder()
                        .url("https://api.telegram.org/bot$token/getUpdates?offset=${lastUpdateId + 1}")
                        .build()

                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && responseBody != null) {
                            val json = JSONObject(responseBody)
                            if (json.getBoolean("ok")) {
                                val results = json.getJSONArray("result")
                                for (i in 0 until results.length()) {
                                    val update = results.getJSONObject(i)
                                    val updateId = update.getLong("update_id")
                                    if (updateId > lastUpdateId) lastUpdateId = updateId

                                    if (update.has("message")) {
                                        val message = update.getJSONObject("message")
                                        val text = message.optString("text")
                                        if (text == "/start $uuid") {
                                            val chat = message.getJSONObject("chat")
                                            val chatId = chat.getLong("id")
                                            val username = chat.optString("username", chat.optString("first_name", "Unknown"))
                                            
                                            AppDatabase.getDatabase(requireContext()).telegramListenerDao().insert(
                                                TelegramListener(chatId = chatId, listenerName = username)
                                            )

                                            withContext(Dispatchers.Main) {
                                                showSuccess("Successfully Linked to $username!")
                                                pollingDialog?.dismiss()
                                            }
                                            isPolling = false
                                            return@launch
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore and retry
                }
                delay(2000)
            }
        }
    }

    private fun exportData(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                val rules = db.trackingRuleDao().getAllRules().firstOrNull() ?: emptyList()
                val jsonArray = JSONArray()
                rules.forEach { rule ->
                    val obj = JSONObject()
                    obj.put("url", rule.url)
                    obj.put("cssSelector", rule.cssSelector)
                    obj.put("isTrackWholePage", rule.isTrackWholePage)
                    obj.put("syncFrequencyMin", rule.syncFrequencyMin)
                    obj.put("isPremiumRule", rule.isPremiumRule)
                    obj.put("aiConditionPrompt", rule.aiConditionPrompt)
                    obj.put("requiresJS", rule.requiresJS)
                    jsonArray.put(obj)
                }
                
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonArray.toString(2).toByteArray())
                }
                withContext(Dispatchers.Main) {
                    showSuccess("Data exported successfully")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Export failed: ${e.message}")
                }
            }
        }
    }

    private fun importData(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonString = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (jsonString != null) {
                    val jsonArray = JSONArray(jsonString)
                    val db = AppDatabase.getDatabase(requireContext())
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val rule = TrackingRule(
                            url = obj.getString("url"),
                            cssSelector = if (obj.has("cssSelector") && !obj.isNull("cssSelector")) obj.getString("cssSelector") else null,
                            isTrackWholePage = obj.getBoolean("isTrackWholePage"),
                            syncFrequencyMin = obj.getInt("syncFrequencyMin"),
                            isPremiumRule = obj.getBoolean("isPremiumRule"),
                            aiConditionPrompt = if (obj.has("aiConditionPrompt") && !obj.isNull("aiConditionPrompt")) obj.getString("aiConditionPrompt") else null,
                            requiresJS = if (obj.has("requiresJS")) obj.getBoolean("requiresJS") else false
                        )
                        db.trackingRuleDao().insertRule(rule)
                    }
                    withContext(Dispatchers.Main) {
                        showSuccess("Data imported successfully")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Import failed: ${e.message}")
                }
            }
        }
    }

    private fun requestBatteryOptimization() {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = requireContext().packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(requireContext())
                .setTitle("Background Tracking")
                .setMessage("To ensure tracking works consistently in the background, we need to bypass battery optimizations. Please allow this in the next screen.")
                .setPositiveButton("OK") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            Toast.makeText(requireContext(), "Battery Optimization already disabled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testConnection(apiKey: String, modelName: String) {
        lifecycleScope.launch {
            try {
                val generativeModel = GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey
                )
                val response = generativeModel.generateContent("Say hello")
                if (response.text.isNullOrBlank()) {
                    showError("API error: empty response")
                } else {
                    showSuccess("Connection successful!")
                }
            } catch (e: Exception) {
                showError("Connection failed: ${e.message}")
            }
        }
    }

    private fun showSuccess(message: String) {
        val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        toast.show()
    }

    private fun showError(message: String) {
        val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        toast.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
