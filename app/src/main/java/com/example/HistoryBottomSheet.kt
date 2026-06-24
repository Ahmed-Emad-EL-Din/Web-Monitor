package com.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.data.AppDatabase
import com.example.data.BrowsingHistory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val rvHistory = view.findViewById<RecyclerView>(R.id.rv_history)
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        val adapter = HistoryAdapter { url ->
            (parentFragment as? BrowserFragment)?.loadUrl(url)
            dismiss()
        }
        rvHistory.adapter = adapter
        
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            db.browsingHistoryDao().getAllHistory().collectLatest { history ->
                adapter.submitList(history)
            }
        }
    }

    class HistoryAdapter(
        private val onItemClick: (String) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<BrowsingHistory, HistoryAdapter.ViewHolder>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<BrowsingHistory>() {
            override fun areItemsTheSame(oldItem: BrowsingHistory, newItem: BrowsingHistory) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: BrowsingHistory, newItem: BrowsingHistory) = oldItem == newItem
        }
    ) {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_title)
            val tvUrl: TextView = view.findViewById(R.id.tv_url)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.tvTitle.text = item.title ?: item.url
            holder.tvUrl.text = item.url
            holder.itemView.setOnClickListener {
                onItemClick(item.url)
            }
        }
    }
}
