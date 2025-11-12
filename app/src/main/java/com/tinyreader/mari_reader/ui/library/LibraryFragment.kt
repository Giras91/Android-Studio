package com.tinyreader.mari_reader.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tinyreader.mari_reader.MainActivity
import com.tinyreader.mari_reader.R
import com.tinyreader.mari_reader.data.LibraryManga
import com.tinyreader.mari_reader.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LibraryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = (activity as MainActivity).viewModel

        recyclerView = view.findViewById(R.id.recycler_view_library)
        recyclerView.layoutManager = GridLayoutManager(context, 3)
        adapter = LibraryAdapter({ manga ->
            // When a manga is clicked, try to resume last-read chapter. If none, open chapter list.
            lifecycleScope.launch {
                try {
                    val last = viewModel.getLastReadChapter(manga.mangaUrl)
                    if (last != null) {
                        val bundle = Bundle().apply {
                            putString("chapterUrl", last.chapterUrl)
                            putString("mangaUrl", manga.mangaUrl)
                        }
                        findNavController().navigate(R.id.navigation_reader, bundle)
                    } else {
                        val bundle = Bundle().apply {
                            putString("mangaUrl", manga.mangaUrl)
                        }
                        findNavController().navigate(R.id.navigation_chapter_list, bundle)
                    }
                } catch (e: Exception) {
                    // Fallback: open chapter list on error
                    val bundle = Bundle().apply {
                        putString("mangaUrl", manga.mangaUrl)
                    }
                    findNavController().navigate(R.id.navigation_chapter_list, bundle)
                }
            }
        }, viewModel)
        recyclerView.adapter = adapter

        viewModel.allLibraryManga.observe(viewLifecycleOwner) { mangaList ->
            adapter.submitList(mangaList)
        }

        val historyButton = view.findViewById<View>(R.id.history_button)
        historyButton.setOnClickListener {
            showReadingHistory()
        }
    }

    private fun showReadingHistory() {
        lifecycleScope.launch {
            val historyList = viewModel.allReadingHistory.value ?: emptyList()
            val mangaList = viewModel.allLibraryManga.value ?: emptyList()
            val mangaMap = mangaList.associateBy { it.mangaUrl }

            val sources = mangaList.map { it.sourceUrl }.distinct().plus("All").toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("Select Source")
                .setItems(sources) { _, which ->
                    val selectedSource = sources[which]
                    showFilteredHistory(historyList, mangaMap, selectedSource)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showFilteredHistory(historyList: List<com.tinyreader.mari_reader.data.ReadingHistory>, mangaMap: Map<String, LibraryManga>, selectedSource: String) {
        // Group by mangaUrl and take the latest read
        val groupedHistory = historyList.groupBy { it.mangaUrl }.mapValues { it.value.maxByOrNull { h -> h.lastReadAt } }.filterValues { it != null }.mapValues { it.value!! }

        val filteredHistory = if (selectedSource == "All") {
            groupedHistory.values
        } else {
            groupedHistory.values.filter { mangaMap[it.mangaUrl]?.sourceUrl == selectedSource }
        }

        val historyStrings = filteredHistory.sortedByDescending { it.lastReadAt }.map { history ->
            val manga = mangaMap[history.mangaUrl]
            val title = manga?.title ?: history.mangaUrl
            val source = manga?.sourceUrl ?: "Unknown"
            "Title: $title\nSource: $source\nLast read: ${java.util.Date(history.lastReadAt)}"
        }.toTypedArray()

        if (historyStrings.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("Reading History")
                .setMessage("No reading history available for selected source.")
                .setPositiveButton("OK", null)
                .show()
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("Reading History ($selectedSource)")
                .setItems(historyStrings, null)
                .setPositiveButton("OK", null)
                .setNeutralButton("Change Filter") { _, _ -> showReadingHistory() }
                .show()
        }
    }
}
