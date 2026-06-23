package com.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.example.databinding.BottomSheetTrackingBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TrackingBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTrackingBinding? = null
    private val binding get() = _binding!!
    private var listener: TrackingListener? = null

    interface TrackingListener {
        fun onTrackWholePage(syncFreqMin: Int, isPremium: Boolean, aiPrompt: String?)
        fun onTrackElements(syncFreqMin: Int, isPremium: Boolean, aiPrompt: String?)
    }

    fun setTrackingListener(listener: TrackingListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val syncOptions = arrayOf("15 Min", "30 Min", "1 Hr", "6 Hr", "24 Hr")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, syncOptions)
        binding.actvSync.setAdapter(adapter)
        binding.actvSync.setText(syncOptions[0], false)

        binding.switchPremium.setOnCheckedChangeListener { _, isChecked ->
            binding.tilAiPrompt.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnTrackWhole.setOnClickListener {
            listener?.onTrackWholePage(
                getSyncFreq(),
                binding.switchPremium.isChecked,
                binding.etAiPrompt.text?.toString()
            )
            dismiss()
        }

        binding.btnTrackElements.setOnClickListener {
            listener?.onTrackElements(
                getSyncFreq(),
                binding.switchPremium.isChecked,
                binding.etAiPrompt.text?.toString()
            )
            dismiss()
        }
    }

    private fun getSyncFreq(): Int {
        return when (binding.actvSync.text.toString()) {
            "15 Min" -> 15
            "30 Min" -> 30
            "1 Hr" -> 60
            "6 Hr" -> 360
            "24 Hr" -> 1440
            else -> 15
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
