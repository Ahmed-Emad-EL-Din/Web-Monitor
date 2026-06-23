package com.example

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.data.AppDatabase
import com.example.data.CaptchaLog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CaptchaBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_captcha_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val rvCaptcha = view.findViewById<RecyclerView>(R.id.rv_captcha)
        rvCaptcha.layoutManager = LinearLayoutManager(requireContext())
        val adapter = CaptchaAdapter { log ->
            // Pass to BrowserFragment
            val parent = parentFragment
            if (parent is BrowserFragment) {
                parent.loadUrl(log.url)
                dismiss()
            }
        }
        rvCaptcha.adapter = adapter
        
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            db.captchaLogDao().getAllLogs().collectLatest { logs ->
                adapter.submitList(logs)
            }
        }
    }

    class CaptchaAdapter(private val onSolveClick: (CaptchaLog) -> Unit) : androidx.recyclerview.widget.ListAdapter<CaptchaLog, CaptchaAdapter.ViewHolder>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<CaptchaLog>() {
            override fun areItemsTheSame(oldItem: CaptchaLog, newItem: CaptchaLog) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: CaptchaLog, newItem: CaptchaLog) = oldItem == newItem
        }
    ) {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvUrl: TextView = view.findViewById(R.id.tv_url)
            val tvTime: TextView = view.findViewById(R.id.tv_time)
            val btnSolve: Button = view.findViewById(R.id.btn_solve)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_captcha, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.tvUrl.text = item.url
            holder.tvTime.text = DateUtils.getRelativeTimeSpanString(item.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            holder.btnSolve.setOnClickListener {
                onSolveClick(item)
            }
        }
    }
}
