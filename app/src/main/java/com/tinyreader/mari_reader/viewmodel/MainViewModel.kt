package com.tinyreader.mari_reader.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tinyreader.mari_reader.data.Chapter
import com.tinyreader.mari_reader.data.LibraryManga
import com.tinyreader.mari_reader.data.MariReaderRepository
import com.tinyreader.mari_reader.data.Source
import kotlinx.coroutines.launch

class MainViewModel(private val repository: MariReaderRepository) : ViewModel() {

    val allSources = repository.allSources.asLiveData()
    val allLibraryManga = repository.allManga.asLiveData()

    fun insertSource(source: Source) {
        viewModelScope.launch {
            repository.insertSource(source)
        }
    }

    fun updateSource(source: Source) {
        viewModelScope.launch {
            repository.updateSource(source)
        }
    }

    fun deleteSource(source: Source) {
        viewModelScope.launch {
            repository.deleteSource(source)
        }
    }

    fun insertManga(manga: LibraryManga) {
        viewModelScope.launch {
            repository.insertManga(manga)
        }
    }

    fun updateManga(manga: LibraryManga) {
        viewModelScope.launch {
            repository.updateManga(manga)
        }
    }

    fun deleteManga(manga: LibraryManga) {
        viewModelScope.launch {
            repository.deleteManga(manga)
        }
    }

    fun insertChapter(chapter: Chapter) {
        viewModelScope.launch {
            repository.insertChapter(chapter)
        }
    }

    fun updateChapter(chapter: Chapter) {
        viewModelScope.launch {
            repository.updateChapter(chapter)
        }
    }

    fun deleteChapter(chapter: Chapter) {
        viewModelScope.launch {
            repository.deleteChapter(chapter)
        }
    }

    fun getChapters(mangaUrl: String) = repository.getChapters(mangaUrl)

    suspend fun getManga(mangaUrl: String) = repository.getManga(mangaUrl)

    suspend fun getSource(sourceUrl: String) = repository.getSource(sourceUrl)

    val allReadingHistory = repository.allReadingHistory

    fun insertReadingHistory(history: com.tinyreader.mari_reader.data.ReadingHistory) {
        viewModelScope.launch {
            repository.insertReadingHistory(history)
        }
    }

    suspend fun getLastReadChapter(mangaUrl: String) = repository.getLastReadChapter(mangaUrl)
}
