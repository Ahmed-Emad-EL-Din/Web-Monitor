package com.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.AppDatabase
import com.example.data.TrackingRule
import com.example.databinding.FragmentDashboardBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TrackingRuleAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = TrackingRuleAdapter(
            onSwitchChanged = { rule, isActive ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(requireContext())
                    db.trackingRuleDao().updateRule(rule.copy(isActive = isActive))
                    if (isActive) {
                        scheduleWorker(rule.id, rule.syncFrequencyMin)
                    } else {
                        WorkManager.getInstance(requireContext()).cancelUniqueWork("track_${rule.id}")
                    }
                }
            },
            onHistoryClicked = { rule ->
                val bundle = Bundle().apply { putInt("ruleId", rule.id) }
                findNavController().navigate(R.id.changeDetailsFragment, bundle)
            },
            onForceSyncClicked = { rule, onComplete ->
                val workRequest = OneTimeWorkRequestBuilder<TrackingWorker>()
                    .setInputData(androidx.work.workDataOf("RULE_ID" to rule.id, "IS_MANUAL_SYNC" to true))
                    .build()
                
                WorkManager.getInstance(requireContext()).enqueue(workRequest)
                
                WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(workRequest.id)
                    .observe(viewLifecycleOwner) { workInfo ->
                        if (workInfo != null && workInfo.state.isFinished) {
                            onComplete(workInfo.state == WorkInfo.State.SUCCEEDED)
                            if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                                val hasChanges = workInfo.outputData.getBoolean("HAS_CHANGES", false)
                                if (hasChanges) {
                                    Toast.makeText(requireContext(), "Update found!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "Check complete. No changes.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(requireContext(), "Check failed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            },
            onDeleteClicked = { rule ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Monitor")
                    .setMessage("Are you sure you want to delete this tracker and all its history?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(requireContext())
                            db.trackingRuleDao().deleteRule(rule)
                            WorkManager.getInstance(requireContext()).cancelUniqueWork("track_${rule.id}")
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val rule = adapter.currentList[position]
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(requireContext())
                    db.trackingRuleDao().deleteRule(rule)
                    WorkManager.getInstance(requireContext()).cancelUniqueWork("track_${rule.id}")
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        binding.ivSettings.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_settingsFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            db.trackingRuleDao().getAllRules().collectLatest { rules ->
                adapter.submitList(rules)
                binding.emptyStateContainer.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun scheduleWorker(ruleId: Int, syncFrequencyMin: Int) {
        val workRequest = PeriodicWorkRequestBuilder<TrackingWorker>(
            syncFrequencyMin.toLong(), TimeUnit.MINUTES
        )
        .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
        .setInputData(androidx.work.workDataOf("RULE_ID" to ruleId))
        .build()
        
        WorkManager.getInstance(requireContext())
            .enqueueUniquePeriodicWork(
                "track_$ruleId",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
