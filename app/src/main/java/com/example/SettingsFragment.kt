package com.example

import android.app.Activity
import android.app.AlertDialog
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
import org.json.JSONObject

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
