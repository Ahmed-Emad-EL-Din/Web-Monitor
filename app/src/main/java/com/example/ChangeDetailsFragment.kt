package com.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.databinding.FragmentChangeDetailsBinding
import kotlinx.coroutines.Dispatchers
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
                withContext(Dispatchers.Main) {
                    if (rule != null) {
                        binding.tvDetailsUrl.text = "URL: ${rule.url}"
                        binding.tvDetailsText.text = "Current Text:\n\n${rule.lastKnownText}"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
