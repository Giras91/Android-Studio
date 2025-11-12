package com.tinyreader.mari_reader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tinyreader.mari_reader.data.Chapter
import com.tinyreader.mari_reader.data.MariReaderDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tinyreader.mari_reader.scraper.ScraperManager
import org.jsoup.Jsoup
import android.util.Log

class UpdateWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = MariReaderDatabase.getDatabase(applicationContext)
            val mangaDao = database.mangaDao()
            val chapterDao = database.chapterDao()
            val sourceDao = database.sourceDao()

            val mangas = mangaDao.getAllMangasSync()

            for (manga in mangas) {
                try {
                    val profileJson = try { sourceDao.getSource(manga.sourceUrl)?.profileJson } catch (_: Exception) { null }

                    // If profile requires JS extraction, skip in background (can't run WebView here).
                    if (ScraperManager.profileRequiresJs(profileJson)) {
                        Log.i("UpdateWorker", "Skipping JS-only profile for ${manga.mangaUrl} in background")
                        continue
                    }

                    // Use ScraperManager to fetch chapter list (server-side heuristics or profile selector)
                    val results = try { ScraperManager.fetchChapters(manga.mangaUrl, profileJson) } catch (e: Exception) { emptyList<Pair<String,String>>() }

                    if (results.isEmpty()) {
                        // As a last resort, try Jsoup generic selector (robust fallback)
                        try {
                            val doc = Jsoup.connect(manga.mangaUrl).get()
                            val chapterElements = doc.select("a[href*='chapter']")
                            val existingChapters = chapterDao.getChaptersForMangaSync(manga.mangaUrl)
                            val existingUrls = existingChapters.map { it.chapterUrl }.toSet()
                            for (element in chapterElements) {
                                val url = element.absUrl("href")
                                val title = element.text().trim()
                                if (url.isNotEmpty() && title.isNotEmpty() && url !in existingUrls) {
                                    val chapter = Chapter(chapterUrl = url, mangaUrl = manga.mangaUrl, title = title)
                                    chapterDao.insert(chapter)
                                }
                            }
                        } catch (_: Exception) {}
                    } else {
                        val existingChapters = chapterDao.getChaptersForMangaSync(manga.mangaUrl)
                        val existingUrls = existingChapters.map { it.chapterUrl }.toSet()
                        for ((idx, pair) in results.withIndex()) {
                            val (title, url) = pair
                            if (url.isNotBlank() && url !in existingUrls) {
                                val num = com.tinyreader.mari_reader.scraper.ScraperManager.extractChapterNumber(title, url)
                                val chapterNum = if (num > 0f) num else (idx + 1).toFloat()
                                val chapter = Chapter(chapterUrl = url, mangaUrl = manga.mangaUrl, title = title, chapterNumber = chapterNum)
                                chapterDao.insert(chapter)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Log error for this manga
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}
