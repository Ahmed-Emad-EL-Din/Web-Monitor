package com.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.data.AppPreferences
import com.example.databinding.FragmentSettingsBinding
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var appPreferences: AppPreferences

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
        // basic toast, customization might require custom toast layout
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
