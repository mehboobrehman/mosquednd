package com.mosquednd.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mosquednd.R
import com.mosquednd.data.Mosque
import com.mosquednd.databinding.ItemMosqueBinding

class MosqueAdapter(
    private val onDeleteClick: (Mosque) -> Unit,
    private val onItemClick: (Mosque) -> Unit
) : ListAdapter<Mosque, MosqueAdapter.MosqueViewHolder>(MosqueDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MosqueViewHolder {
        val binding = ItemMosqueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MosqueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MosqueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MosqueViewHolder(
        private val binding: ItemMosqueBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(mosque: Mosque) {
            binding.textMosqueName.text = mosque.name
            binding.textMosqueAddress.text = mosque.address.ifEmpty {
                itemView.context.getString(R.string.coord_format, mosque.latitude, mosque.longitude)
            }
            binding.textRadius.text = itemView.context.getString(R.string.radius_meters_format, mosque.geofenceRadius)
            
            binding.root.setOnClickListener {
                onItemClick(mosque)
            }
            
            binding.btnDelete.setOnClickListener {
                onDeleteClick(mosque)
            }
        }
    }
}

class MosqueDiffCallback : DiffUtil.ItemCallback<Mosque>() {
    override fun areItemsTheSame(oldItem: Mosque, newItem: Mosque): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Mosque, newItem: Mosque): Boolean {
        return oldItem == newItem
    }
}
