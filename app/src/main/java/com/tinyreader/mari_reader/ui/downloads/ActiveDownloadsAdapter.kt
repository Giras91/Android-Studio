package com.tinyreader.mari_reader.ui.downloads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tinyreader.mari_reader.R
import androidx.work.WorkInfo

class ActiveDownloadsAdapter(private val onCancel: (WorkInfo) -> Unit, private val onRetry: (WorkInfo) -> Unit) : ListAdapter<WorkInfo, ActiveDownloadsAdapter.ViewHolder>(Diff()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(com.tinyreader.mari_reader.R.layout.item_active_download, parent, false)
        return ViewHolder(v, onCancel, onRetry)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View, val onCancel: (WorkInfo) -> Unit, val onRetry: (WorkInfo) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(com.tinyreader.mari_reader.R.id.active_title)
        private val progress: ProgressBar = itemView.findViewById(com.tinyreader.mari_reader.R.id.active_progress)
        private val cancel: TextView = itemView.findViewById(com.tinyreader.mari_reader.R.id.active_cancel)
        private val retry: TextView = itemView.findViewById(com.tinyreader.mari_reader.R.id.active_retry)

        fun bind(info: WorkInfo) {
            title.text = info.id.toString()
            val p = info.progress
            val done = p.run { getInt(com.tinyreader.mari_reader.DownloadWorker.PROGRESS_KEY, 0) }
            val total = p.run { getInt(com.tinyreader.mari_reader.DownloadWorker.PROGRESS_TOTAL, 0) }
            if (total > 0) {
                progress.max = total
                progress.progress = done
            } else {
                progress.isIndeterminate = true
            }
            cancel.setOnClickListener { onCancel(info) }
            retry.setOnClickListener { onRetry(info) }
        }
    }

    class Diff : DiffUtil.ItemCallback<WorkInfo>() {
        override fun areItemsTheSame(oldItem: WorkInfo, newItem: WorkInfo): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: WorkInfo, newItem: WorkInfo): Boolean = oldItem.state == newItem.state && oldItem.progress == newItem.progress
    }
}
