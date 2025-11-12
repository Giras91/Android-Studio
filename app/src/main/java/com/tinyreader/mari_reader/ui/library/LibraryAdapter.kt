package com.tinyreader.mari_reader.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tinyreader.mari_reader.R
import com.tinyreader.mari_reader.data.LibraryManga
import com.tinyreader.mari_reader.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class LibraryAdapter(private val onItemClick: (LibraryManga) -> Unit, private val viewModel: MainViewModel) : ListAdapter<LibraryManga, LibraryAdapter.LibraryViewHolder>(LibraryDiffCallback()) {

    inner class LibraryViewHolder(itemView: View, private val onItemClick: (LibraryManga) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val coverImage: ImageView = itemView.findViewById(R.id.cover_image)
        private val titleText: TextView = itemView.findViewById(R.id.title_text)
        private val lastReadText: TextView = itemView.findViewById(R.id.last_read_text)
        private val favoriteButton: ImageButton = itemView.findViewById(R.id.favorite_button)

        fun bind(manga: LibraryManga) {
            titleText.text = manga.title
            Glide.with(itemView.context)
                .load(manga.coverImageUrl)
                .placeholder(R.drawable.ic_launcher_background)
                .into(coverImage)

            // Set last read
            // For now, skip

            favoriteButton.setImageResource(if (manga.isFavorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
            favoriteButton.setOnClickListener {
                // Toggle favorite
                val updatedManga = manga.copy(isFavorite = !manga.isFavorite)
                viewModel.updateManga(updatedManga)
            }

            itemView.setOnClickListener {
                onItemClick(manga)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_library_manga, parent, false)
        return LibraryViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LibraryDiffCallback : DiffUtil.ItemCallback<LibraryManga>() {
        override fun areItemsTheSame(oldItem: LibraryManga, newItem: LibraryManga): Boolean {
            return oldItem.mangaUrl == newItem.mangaUrl
        }

        override fun areContentsTheSame(oldItem: LibraryManga, newItem: LibraryManga): Boolean {
            return oldItem == newItem
        }
    }
}
