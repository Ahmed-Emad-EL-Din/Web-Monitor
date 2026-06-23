package com.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.databinding.BottomSheetTrackingBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrackingBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTrackingBinding? = null
    private val binding get() = _binding!!
    private var listener: TrackingListener? = null
    private val selectedListenerIds = mutableSetOf<Int>()

    interface TrackingListener {
        fun onTrackWholePage(syncFreqMin: Int, isPremium: Boolean, aiPrompt: String?, requiresJS: Boolean, listenerIds: List<Int>)
        fun onTrackElements(syncFreqMin: Int, isPremium: Boolean, aiPrompt: String?, requiresJS: Boolean, listenerIds: List<Int>)
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

        loadTelegramListeners()

        binding.btnTrackWhole.setOnClickListener {
            listener?.onTrackWholePage(
                getSyncFreq(),
                binding.switchPremium.isChecked,
                binding.etAiPrompt.text?.toString(),
                binding.cbRequiresJs.isChecked,
                selectedListenerIds.toList()
            )
            dismiss()
        }

        binding.btnTrackElements.setOnClickListener {
            listener?.onTrackElements(
                getSyncFreq(),
                binding.switchPremium.isChecked,
                binding.etAiPrompt.text?.toString(),
                binding.cbRequiresJs.isChecked,
                selectedListenerIds.toList()
            )
            dismiss()
        }
    }

    private fun loadTelegramListeners() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            db.telegramListenerDao().getAllListeners().collect { listeners ->
                withContext(Dispatchers.Main) {
                    if (listeners.isNotEmpty()) {
                        binding.tvTelegramListenersTitle.visibility = View.VISIBLE
                        binding.llTelegramListeners.removeAllViews()
                        listeners.forEach { listener ->
                            val cb = CheckBox(requireContext())
                            cb.text = listener.listenerName
                            cb.setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) selectedListenerIds.add(listener.id)
                                else selectedListenerIds.remove(listener.id)
                            }
                            binding.llTelegramListeners.addView(cb)
                        }
                    } else {
                        binding.tvTelegramListenersTitle.visibility = View.GONE
                        binding.llTelegramListeners.removeAllViews()
                    }
                }
            }
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
