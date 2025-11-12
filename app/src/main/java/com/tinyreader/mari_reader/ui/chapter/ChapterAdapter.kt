package com.tinyreader.mari_reader.ui.chapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tinyreader.mari_reader.R
import com.tinyreader.mari_reader.data.Chapter

class ChapterAdapter(private val onItemClick: (Chapter) -> Unit) : ListAdapter<Chapter, ChapterAdapter.ChapterViewHolder>(ChapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chapter, parent, false)
        return ChapterViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        val chapter = getItem(position)
        holder.bind(chapter)
    }

    class ChapterViewHolder(itemView: View, private val onItemClick: (Chapter) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.title_text)
        private val numberText: TextView = itemView.findViewById(R.id.number_text)
        private val readText: TextView = itemView.findViewById(R.id.read_text)

        fun bind(chapter: Chapter) {
            titleText.text = chapter.title
            numberText.text = chapter.chapterNumber.toString()
            readText.text = if (chapter.isRead) "Read" else "Unread"

            itemView.setOnClickListener {
                onItemClick(chapter)
            }
        }
    }

    class ChapterDiffCallback : DiffUtil.ItemCallback<Chapter>() {
        override fun areItemsTheSame(oldItem: Chapter, newItem: Chapter): Boolean {
            return oldItem.chapterUrl == newItem.chapterUrl
        }

        override fun areContentsTheSame(oldItem: Chapter, newItem: Chapter): Boolean {
            return oldItem == newItem
        }
    }
}
