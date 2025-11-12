package com.tinyreader.mari_reader.ui.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tinyreader.mari_reader.R
import java.io.File

class DownloadedViewerFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DownloadedViewerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(com.tinyreader.mari_reader.R.layout.fragment_downloaded_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(com.tinyreader.mari_reader.R.id.recycler_view_downloaded)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = DownloadedViewerAdapter()
        recyclerView.adapter = adapter

        val dirPath = arguments?.getString("dirPath") ?: return
        val dir = File(dirPath)
        val files = if (dir.exists()) dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList() else emptyList()
        adapter.submitList(files)
    }

    class DownloadedViewerAdapter : androidx.recyclerview.widget.ListAdapter<File, DownloadedViewerAdapter.ViewHolder>(DiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(com.tinyreader.mari_reader.R.layout.item_downloaded_image, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ImageView = itemView.findViewById(com.tinyreader.mari_reader.R.id.downloaded_image)
            fun bind(file: File) {
                Glide.with(itemView.context).load(file).into(imageView)
            }
        }

        class DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean = oldItem.absolutePath == newItem.absolutePath
            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean = oldItem.absolutePath == newItem.absolutePath
        }
    }
}
