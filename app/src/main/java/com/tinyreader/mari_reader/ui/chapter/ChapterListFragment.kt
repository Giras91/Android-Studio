package com.tinyreader.mari_reader.ui.chapter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tinyreader.mari_reader.MainActivity
import com.tinyreader.mari_reader.R
import com.tinyreader.mari_reader.data.Chapter
import com.tinyreader.mari_reader.viewmodel.MainViewModel

class ChapterListFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChapterAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chapter_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = (activity as MainActivity).viewModel

        val mangaUrl = arguments?.getString("mangaUrl") ?: ""

        recyclerView = view.findViewById(R.id.recycler_view_chapters)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = ChapterAdapter { chapter ->
            val bundle = Bundle().apply {
                putString("chapterUrl", chapter.chapterUrl)
                putString("mangaUrl", mangaUrl)
            }
            findNavController().navigate(R.id.navigation_reader, bundle)
        }
        recyclerView.adapter = adapter

        viewModel.getChapters(mangaUrl).observe(viewLifecycleOwner) { chapters: List<com.tinyreader.mari_reader.data.Chapter> ->
            adapter.submitList(chapters)
        }
    }
}
