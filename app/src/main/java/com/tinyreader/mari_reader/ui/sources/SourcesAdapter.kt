package com.tinyreader.mari_reader.ui.sources

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tinyreader.mari_reader.R
import com.tinyreader.mari_reader.data.Source

class SourcesAdapter(
    private val onItemClick: (Source) -> Unit,
    private val onItemLongClick: (Source) -> Unit
) : ListAdapter<Source, SourcesAdapter.SourceViewHolder>(SourceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_source, parent, false)
        return SourceViewHolder(view, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
        val source = getItem(position)
        holder.bind(source)
    }

    class SourceViewHolder(
        itemView: View,
        private val onItemClick: (Source) -> Unit,
        private val onItemLongClick: (Source) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val iconImage: ImageView = itemView.findViewById(R.id.icon_image)
        private val nameText: TextView = itemView.findViewById(R.id.name_text)
        private val urlText: TextView = itemView.findViewById(R.id.url_text)

        fun bind(source: Source) {
            nameText.text = source.name
            urlText.text = source.sourceUrl
            Glide.with(itemView.context)
                .load(source.iconUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(iconImage)

            itemView.setOnClickListener {
                onItemClick(source)
            }

            itemView.setOnLongClickListener {
                onItemLongClick(source)
                true
            }
        }
    }

    class SourceDiffCallback : DiffUtil.ItemCallback<Source>() {
        override fun areItemsTheSame(oldItem: Source, newItem: Source): Boolean {
            return oldItem.sourceUrl == newItem.sourceUrl
        }

        override fun areContentsTheSame(oldItem: Source, newItem: Source): Boolean {
            return oldItem == newItem
        }
    }
}
