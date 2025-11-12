package com.tinyreader.mari_reader.data

import androidx.lifecycle.asLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MariReaderRepository(
    private val sourceDao: SourceDao,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val readingHistoryDao: ReadingHistoryDao
) {
    // Source operations
    suspend fun insertSource(source: Source) = withContext(Dispatchers.IO) {
        sourceDao.insert(source)
    }

    suspend fun updateSource(source: Source) = withContext(Dispatchers.IO) {
        sourceDao.update(source)
    }

    suspend fun deleteSource(source: Source) = withContext(Dispatchers.IO) {
        sourceDao.delete(source)
    }

    suspend fun getSource(url: String) = withContext(Dispatchers.IO) {
        sourceDao.getSource(url)
    }

    val allSources = sourceDao.getAllSources()

    // Manga operations
    suspend fun insertManga(manga: LibraryManga) = withContext(Dispatchers.IO) {
        mangaDao.insert(manga)
    }

    suspend fun updateManga(manga: LibraryManga) = withContext(Dispatchers.IO) {
        mangaDao.update(manga)
    }

    suspend fun deleteManga(manga: LibraryManga) = withContext(Dispatchers.IO) {
        mangaDao.delete(manga)
    }

    suspend fun getManga(url: String) = withContext(Dispatchers.IO) {
        mangaDao.getManga(url)
    }

    val allManga = mangaDao.getAllManga()

    // Chapter operations
    suspend fun insertChapter(chapter: Chapter) = withContext(Dispatchers.IO) {
        chapterDao.insert(chapter)
    }

    suspend fun updateChapter(chapter: Chapter) = withContext(Dispatchers.IO) {
        chapterDao.update(chapter)
    }

    suspend fun deleteChapter(chapter: Chapter) = withContext(Dispatchers.IO) {
        chapterDao.delete(chapter)
    }

    fun getChapters(mangaUrl: String) = chapterDao.getChapters(mangaUrl).asLiveData()

    // Reading history operations
    suspend fun insertReadingHistory(history: ReadingHistory) = withContext(Dispatchers.IO) {
        readingHistoryDao.insert(history)
    }

    val allReadingHistory = readingHistoryDao.getAllHistory().asLiveData()

    suspend fun getLastReadChapter(mangaUrl: String) = withContext(Dispatchers.IO) {
        readingHistoryDao.getLastReadChapter(mangaUrl)
    }
}
