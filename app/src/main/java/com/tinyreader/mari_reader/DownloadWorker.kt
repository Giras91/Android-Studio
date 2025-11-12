package com.tinyreader.mari_reader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import android.content.pm.ServiceInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import android.util.Log

class DownloadWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_IMAGE_URLS = "imageUrlsJson"
        const val KEY_MANGA_TITLE = "mangaTitle"
        const val KEY_CHAPTER_TITLE = "chapterTitle"
        const val PROGRESS_KEY = "progress"
        const val PROGRESS_TOTAL = "total"
        const val NOTIF_CHANNEL_ID = "mari_downloads"
        const val NOTIF_ID = 42
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Setup foreground notification
            setForegroundAsync(createForegroundInfo(0, 0))

            val imageUrlsJson = inputData.getString(KEY_IMAGE_URLS) ?: "[]"
            val mangaTitle = inputData.getString(KEY_MANGA_TITLE) ?: "manga"
            val chapterTitle = inputData.getString(KEY_CHAPTER_TITLE) ?: "chapter"

            val arr = org.json.JSONArray(imageUrlsJson)
            val urls = mutableListOf<String>()
            for (i in 0 until arr.length()) urls.add(arr.getString(i))

            val baseDir = File(applicationContext.filesDir, "mari_reader_downloads")
            val outDir = File(baseDir, "${sanitize(mangaTitle)}/${sanitize(chapterTitle)}")
            outDir.mkdirs()

            val total = urls.size
            for ((index, urlStr) in urls.withIndex()) {
                var attempt = 0
                var success = false
                while (attempt < 3 && !success) {
                    attempt++
                    try {
                        val url = URL(urlStr)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 15000
                        conn.readTimeout = 15000
                        conn.instanceFollowRedirects = true
                        conn.requestMethod = "GET"
                        conn.connect()
                        val code = conn.responseCode
                        if (code in 200..299) {
                            val input = conn.inputStream
                            val bytes = input.readBytes()
                            input.close()

                            val ext = urlStr.substringAfterLast('.', "jpg").substringBefore('?')
                            val f = File(outDir, String.format(Locale.US, "%03d.%s", index + 1, ext))
                            f.writeBytes(bytes)

                            // report progress
                            setProgressAsync(androidx.work.Data.Builder().putInt(PROGRESS_KEY, index + 1).putInt(PROGRESS_TOTAL, total).build())
                            // update notification
                            setForegroundAsync(createForegroundInfo(index + 1, total))

                            success = true
                        } else if (code in 500..599) {
                            // server error, retry
                        } else {
                            // client error, skip
                            success = true
                        }
                    } catch (_: Exception) {
                        // transient network error: retry
                    }
                }
            }

            // On success, remove stored work params if present
            try {
                val paramsDir = File(applicationContext.filesDir, "work_params")
                val paramsFile = File(paramsDir, "${id}.json")
                if (paramsFile.exists()) paramsFile.delete()
            } catch (_: Exception) {
            }

            // Auto-export to saved SAF folder if user selected one and auto-export enabled
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val doAuto = prefs.getBoolean("mari_auto_export", true)
                val saved = prefs.getString("mari_export_uri", null)
                if (doAuto && !saved.isNullOrBlank()) {
                    val treeUri = Uri.parse(saved)
                    val docTree = DocumentFile.fromTreeUri(applicationContext, treeUri)
                    if (docTree != null && outDir.exists()) {
                        val files = outDir.listFiles()?.filter { it.isFile } ?: emptyList()
                        for (f in files) {
                            try {
                                val name = f.name ?: continue
                                val existing = docTree.findFile(name)
                                existing?.delete()
                                val dest = docTree.createFile("image/*", name) ?: continue
                                applicationContext.contentResolver.openOutputStream(dest.uri)?.use { out ->
                                    f.inputStream().use { inp -> inp.copyTo(out) }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }

            Result.success()
        } catch (ex: Exception) {
            // Write structured JSON log for diagnostics and rotate old logs
            try {
                val logDir = File(applicationContext.filesDir, "worker_logs")
                logDir.mkdirs()
                val entry = org.json.JSONObject()
                entry.put("timestamp", System.currentTimeMillis())
                entry.put("workId", id.toString())
                entry.put("error", ex::class.java.name)
                entry.put("message", ex.message ?: "")
                entry.put("stack", ex.stackTraceToString())
                val lf = File(logDir, "${id}.log")
                lf.appendText(entry.toString() + "\n")

                // Rotate: keep at most MAX_LOG_FILES recent logs
                val MAX_LOG_FILES = 50
                val files = logDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
                if (files.size > MAX_LOG_FILES) {
                    files.drop(MAX_LOG_FILES).forEach { try { it.delete() } catch (_: Exception) {} }
                }
            } catch (_: Exception) { /* ignore logging failure */ }
            Result.retry()
        }
    }

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val channelId = NOTIF_CHANNEL_ID
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW))
            }
        }

        val title = "Downloading chapter"
        val progress = if (total > 0) (done * 100 / total) else 0
        val notif = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText("$done / $total")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, total == 0)
            .build()

        // Use explicit foreground service type (data sync) to satisfy newer Android target SDK policies
        return if (android.os.Build.VERSION.SDK_INT >= 31) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            try {
                ForegroundInfo(NOTIF_ID, notif, type)
            } catch (e: Exception) {
                Log.w("DownloadWorker", "Failed to create ForegroundInfo with typed service, falling back: ${e.message}")
                ForegroundInfo(NOTIF_ID, notif)
            }
        } else {
            ForegroundInfo(NOTIF_ID, notif)
        }
    }

    private fun sanitize(s: String): String = s.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
