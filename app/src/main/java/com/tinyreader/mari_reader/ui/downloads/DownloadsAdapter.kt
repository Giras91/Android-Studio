package com.tinyreader.mari_reader.ui.downloads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tinyreader.mari_reader.R
import java.io.File

class DownloadsAdapter(
    private val onItemClick: (File) -> Unit,
    private val onItemLongClick: (File) -> Unit
) : ListAdapter<File, DownloadsAdapter.ViewHolder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(com.tinyreader.mari_reader.R.layout.item_download_dir, parent, false)
        return ViewHolder(v, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View, val onItemClick: (File) -> Unit, val onItemLongClick: (File) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(com.tinyreader.mari_reader.R.id.dir_title)
        fun bind(file: File) {
            title.text = file.name
            itemView.setOnClickListener { onItemClick(file) }
            itemView.setOnLongClickListener { onItemLongClick(file); true }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean = oldItem.absolutePath == newItem.absolutePath
        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean = oldItem.absolutePath == newItem.absolutePath
    }
}
