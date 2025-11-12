package com.tinyreader.mari_reader.ui.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tinyreader.mari_reader.R
import androidx.work.WorkManager
import androidx.work.WorkInfo
import java.io.File

class DownloadsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DownloadsAdapter
    private lateinit var activeRecycler: RecyclerView
    private lateinit var activeAdapter: ActiveDownloadsAdapter

    private var exportPendingDir: File? = null
    private lateinit var pickDirLauncher: ActivityResultLauncher<Uri?>
    private val PREF_EXPORT_URI = "mari_export_uri"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickDirLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                Toast.makeText(requireContext(), "Export cancelled", Toast.LENGTH_SHORT).show()
                exportPendingDir = null
                return@registerForActivityResult
            }
            val dir = exportPendingDir ?: return@registerForActivityResult
            exportPendingDir = null
            // Persist permission
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Save the chosen tree for future exports
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            prefs.edit().putString(PREF_EXPORT_URI, uri.toString()).apply()

            val copied = exportDirToUri(dir, uri)
            Toast.makeText(requireContext(), "Exported $copied files", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(com.tinyreader.mari_reader.R.layout.fragment_downloads, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activeRecycler = view.findViewById(com.tinyreader.mari_reader.R.id.recycler_view_active_downloads)
        activeRecycler.layoutManager = LinearLayoutManager(context)
        activeAdapter = ActiveDownloadsAdapter({ info ->
            // cancel work
            WorkManager.getInstance(requireContext()).cancelWorkById(info.id)
        }, { info ->
            // retry: find params file and re-enqueue
            try {
                val paramsDir = File(requireContext().filesDir, "work_params")
                val f = File(paramsDir, "${info.id}.json")
                if (f.exists()) {
                    val text = f.readText()
                    val obj = org.json.JSONObject(text)
                    val imageJson = obj.optString("imageUrlsJson", "[]")
                    val mangaTitle = obj.optString("mangaTitle", "manga")
                    val chapterTitle = obj.optString("chapterTitle", "chapter")
                    val data = androidx.work.Data.Builder()
                        .putString(com.tinyreader.mari_reader.DownloadWorker.KEY_IMAGE_URLS, imageJson)
                        .putString(com.tinyreader.mari_reader.DownloadWorker.KEY_MANGA_TITLE, mangaTitle)
                        .putString(com.tinyreader.mari_reader.DownloadWorker.KEY_CHAPTER_TITLE, chapterTitle)
                        .build()
                    val work = androidx.work.OneTimeWorkRequestBuilder<com.tinyreader.mari_reader.DownloadWorker>()
                        .setInputData(data)
                        .addTag("download")
                        .build()
                    androidx.work.WorkManager.getInstance(requireContext()).enqueue(work)
                    Toast.makeText(requireContext(), "Retry queued", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "No stored params to retry", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Retry failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        })
        activeRecycler.adapter = activeAdapter

        recyclerView = view.findViewById(com.tinyreader.mari_reader.R.id.recycler_view_downloads)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = DownloadsAdapter({ dir: File ->
            val bundle = Bundle().apply { putString("dirPath", dir.absolutePath) }
            findNavController().navigate(R.id.navigation_downloaded_viewer, bundle)
        }, { dir: File ->
            // Long-press: offer export or delete. If saved export URI exists, show that option first.
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val savedUriStr = prefs.getString(PREF_EXPORT_URI, null)
            val autoExport = prefs.getBoolean("mari_auto_export", true)
            val opts = mutableListOf<String>()
            opts.add(if (autoExport) "Disable auto-export" else "Enable auto-export")
            if (!savedUriStr.isNullOrBlank()) opts.add("Export to saved folder")
            opts.add("Export to external storage (SAF)")
            opts.add("Delete folder")

            AlertDialog.Builder(requireContext())
                .setTitle(dir.name)
                .setItems(opts.toTypedArray()) { _, which ->
                    when (opts[which]) {
                        "Disable auto-export", "Enable auto-export" -> {
                            // toggle
                            val current = prefs.getBoolean("mari_auto_export", true)
                            prefs.edit().putBoolean("mari_auto_export", !current).apply()
                            Toast.makeText(requireContext(), "Auto-export set to ${!current}", Toast.LENGTH_SHORT).show()
                        }
                        "Export to saved folder" -> {
                            val saved = prefs.getString(PREF_EXPORT_URI, null) ?: return@setItems
                            try {
                                val uri = Uri.parse(saved)
                                val copied = exportDirToUri(dir, uri)
                                Toast.makeText(requireContext(), "Exported $copied files to saved folder", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "Export to saved folder failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Export to external storage (SAF)" -> {
                            // Launch SAF pick folder flow
                            exportPendingDir = dir
                            pickDirLauncher.launch(null)
                        }
                        "Delete folder" -> {
                            // Delete folder
                            if (dir.exists()) {
                                dir.deleteRecursively()
                                Toast.makeText(requireContext(), "Deleted ${dir.name}", Toast.LENGTH_SHORT).show()
                                // refresh list
                                val baseDir = File(requireContext().filesDir, "mari_reader_downloads")
                                val dirs = if (baseDir.exists()) baseDir.listFiles()?.filter { it.isDirectory } ?: emptyList() else emptyList()
                                adapter.submitList(dirs)
                            }
                        }
                    }
                }
                .show()
        })
        recyclerView.adapter = adapter

        // List downloaded directories
        val baseDir = File(requireContext().filesDir, "mari_reader_downloads")
        val dirs = if (baseDir.exists()) baseDir.listFiles()?.filter { it.isDirectory } ?: emptyList() else emptyList()
        adapter.submitList(dirs)

        // Observe running downloads
        WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData("download").observe(viewLifecycleOwner) { infos ->
            val running = infos.filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            activeAdapter.submitList(running)
        }
    }

    private fun exportDirToUri(dir: File, uri: Uri): Int {
        val docTree = DocumentFile.fromTreeUri(requireContext(), uri) ?: return 0
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        var copied = 0
        for (f in files) {
            try {
                val name = f.name ?: continue
                // Create or replace existing file
                val existing = docTree.findFile(name)
                existing?.delete()
                val dest = docTree.createFile("image/*", name) ?: continue
                requireContext().contentResolver.openOutputStream(dest.uri)?.use { out ->
                    f.inputStream().use { inp ->
                        inp.copyTo(out)
                    }
                }
                copied++
            } catch (e: Exception) {
                // skip
            }
        }
        return copied
    }
}
