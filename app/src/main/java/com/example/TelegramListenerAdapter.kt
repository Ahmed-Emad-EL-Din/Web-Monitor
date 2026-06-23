package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.data.TelegramListener

class TelegramListenerAdapter(
    private val onDeleteClicked: (TelegramListener) -> Unit,
    private val onItemClicked: (TelegramListener) -> Unit
) : ListAdapter<TelegramListener, TelegramListenerAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_telegram_listener, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvListenerName.text = item.listenerName
        holder.tvChatId.text = "Chat ID: ${item.chatId}"
        
        holder.btnDeleteListener.setOnClickListener {
            onDeleteClicked(item)
        }
        
        holder.itemView.setOnClickListener {
            onItemClicked(item)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvListenerName: TextView = view.findViewById(R.id.tv_listener_name)
        val tvChatId: TextView = view.findViewById(R.id.tv_chat_id)
        val btnDeleteListener: ImageButton = view.findViewById(R.id.btn_delete_listener)
    }

    class DiffCallback : DiffUtil.ItemCallback<TelegramListener>() {
        override fun areItemsTheSame(oldItem: TelegramListener, newItem: TelegramListener) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TelegramListener, newItem: TelegramListener) = oldItem == newItem
    }
}
