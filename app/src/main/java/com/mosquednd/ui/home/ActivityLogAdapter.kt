package com.mosquednd.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mosquednd.R
import com.mosquednd.data.DNDLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView ListAdapter for displaying AutoDND activity log entries.
 */
class ActivityLogAdapter : ListAdapter<DNDLogEntity, ActivityLogAdapter.LogViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dnd_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, dateFormat)
    }

    fun getItemAt(position: Int): DNDLogEntity = getItem(position)

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textEventName: TextView = itemView.findViewById(R.id.text_event_name)
        private val textTimestamp: TextView = itemView.findViewById(R.id.text_timestamp)
        private val textDescription: TextView = itemView.findViewById(R.id.text_description)
        private val textMosqueName: TextView = itemView.findViewById(R.id.text_mosque_name)

        fun bind(log: DNDLogEntity, dateFormat: SimpleDateFormat) {
            textEventName.text = log.eventName
            textTimestamp.text = dateFormat.format(Date(log.timestamp))
            textDescription.text = log.description

            if (!log.mosqueName.isNullOrBlank()) {
                textMosqueName.text = log.mosqueName
                textMosqueName.visibility = View.VISIBLE
            } else {
                textMosqueName.visibility = View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DNDLogEntity>() {
        override fun areItemsTheSame(oldItem: DNDLogEntity, newItem: DNDLogEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DNDLogEntity, newItem: DNDLogEntity): Boolean {
            return oldItem == newItem
        }
    }
}
