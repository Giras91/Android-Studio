package com.tinyreader.mari_reader.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tinyreader.mari_reader.MainActivity
import com.tinyreader.mari_reader.R
import com.tinyreader.mari_reader.data.Chapter
import kotlinx.coroutines.launch

class MangaFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChapterAdapter
    private lateinit var emptyText: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_manga, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mangaUrl = arguments?.getString("mangaUrl") ?: ""

        recyclerView = view.findViewById(R.id.recycler_view_chapters)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = ChapterAdapter { chapter ->
            // Navigate to reader
            val bundle = Bundle().apply {
                putString("chapterUrl", chapter.chapterUrl)
                putString("mangaUrl", mangaUrl)
            }
            findNavController().navigate(R.id.navigation_reader, bundle)
        }
        recyclerView.adapter = adapter

        emptyText = view.findViewById(R.id.empty_text)

        lifecycleScope.launch {
            val chaptersLiveData = (activity as MainActivity).viewModel.getChapters(mangaUrl)
            chaptersLiveData.observe(viewLifecycleOwner) { chapters ->
                adapter.submitList(chapters)
                if (chapters.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }
}
