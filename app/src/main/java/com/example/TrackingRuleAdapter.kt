package com.example

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.data.TrackingRule
import com.example.databinding.ItemTrackingRuleBinding
import java.net.URL

class TrackingRuleAdapter(
    private val onSwitchChanged: (TrackingRule, Boolean) -> Unit,
    private val onHistoryClicked: (TrackingRule) -> Unit
) : ListAdapter<TrackingRule, TrackingRuleAdapter.ViewHolder>(RuleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrackingRuleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTrackingRuleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: TrackingRule) {
            try {
                val url = URL(rule.url)
                binding.tvUrl.text = url.host + url.path
            } catch (e: Exception) {
                binding.tvUrl.text = rule.url
            }

            binding.tvSyncFreq.text = "Sync: ${rule.syncFrequencyMin} Min"
            
            if (rule.lastChecked > 0) {
                val timeAgo = DateUtils.getRelativeTimeSpanString(rule.lastChecked, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                binding.tvLastChecked.text = "Last Checked: ${timeAgo}"
            } else {
                binding.tvLastChecked.text = "Last Checked: Never"
            }

            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = rule.isActive
            binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
                onSwitchChanged(rule, isChecked)
            }

            binding.btnHistory.setOnClickListener {
                onHistoryClicked(rule)
            }
        }
    }

    class RuleDiffCallback : DiffUtil.ItemCallback<TrackingRule>() {
        override fun areItemsTheSame(oldItem: TrackingRule, newItem: TrackingRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrackingRule, newItem: TrackingRule): Boolean {
            return oldItem == newItem
        }
    }
}
