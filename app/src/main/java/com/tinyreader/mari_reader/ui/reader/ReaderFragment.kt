package com.tinyreader.mari_reader.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tinyreader.mari_reader.MainActivity
import com.tinyreader.mari_reader.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jsoup.Jsoup
import kotlin.coroutines.resume
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.io.File
import androidx.work.BackoffPolicy
import java.util.concurrent.TimeUnit

class ReaderFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReaderAdapter
    private lateinit var webView: WebView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reader, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val chapterUrl = arguments?.getString("chapterUrl") ?: ""
        val mangaUrl = arguments?.getString("mangaUrl") ?: ""

        // Save reading history
        val history = com.tinyreader.mari_reader.data.ReadingHistory(
            mangaUrl = mangaUrl,
            chapterUrl = chapterUrl,
            lastReadAt = System.currentTimeMillis()
        )
        (activity as MainActivity).viewModel.insertReadingHistory(history)

        recyclerView = view.findViewById(R.id.recycler_view_reader)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = ReaderAdapter()
        recyclerView.adapter = adapter

        webView = view.findViewById(R.id.web_view_reader)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        val fabFavorite = view.findViewById<FloatingActionButton>(R.id.fab_favorite)
        val fabDownload = view.findViewById<FloatingActionButton>(R.id.fab_download_chapter)

        lifecycleScope.launch {
            val manga = (activity as MainActivity).viewModel.getManga(mangaUrl)
            val sourceProfileJson = try {
                val src = manga?.let { (activity as MainActivity).viewModel.getSource(it.sourceUrl) }
                src?.profileJson
            } catch (e: Exception) {
                null
            }

            if (manga != null) {
                fabFavorite.setImageResource(if (manga.isFavorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
                fabFavorite.setOnClickListener {
                    val updatedManga = manga.copy(isFavorite = !manga.isFavorite)
                    (activity as MainActivity).viewModel.updateManga(updatedManga)
                    fabFavorite.setImageResource(if (updatedManga.isFavorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
                }
            }

            // Perform scraping with optional profile (can be JS-based if profile requests)
            val imageUrls = fetchImageUrls(chapterUrl, sourceProfileJson)
            if (imageUrls.isNotEmpty()) {
                recyclerView.visibility = View.VISIBLE
                webView.visibility = View.GONE
                adapter.submitList(imageUrls)

                // show download FAB
                fabDownload.visibility = View.VISIBLE
                fabDownload.setOnClickListener {
                    // enqueue download worker
                    val imageJson = org.json.JSONArray(imageUrls).toString()
                    val data = Data.Builder()
                        .putString(com.tinyreader.mari_reader.DownloadWorker.KEY_IMAGE_URLS, imageJson)
                        .putString(com.tinyreader.mari_reader.DownloadWorker.KEY_MANGA_TITLE, manga?.title ?: "manga")
                        .putString(com.tinyreader.mari_reader.DownloadWorker.KEY_CHAPTER_TITLE, "chapter")
                        .build()

                    val work = OneTimeWorkRequestBuilder<com.tinyreader.mari_reader.DownloadWorker>()
                        .setInputData(data)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                        .addTag("download")
                        .build()

                    // persist work params so we can retry if needed
                    try {
                        val paramsDir = File(requireContext().filesDir, "work_params")
                        paramsDir.mkdirs()
                        val jsonObj = org.json.JSONObject()
                        jsonObj.put("imageUrlsJson", imageJson)
                        jsonObj.put("mangaTitle", manga?.title ?: "manga")
                        jsonObj.put("chapterTitle", "chapter")
                        val paramsFile = File(paramsDir, "${work.id}.json")
                        paramsFile.writeText(jsonObj.toString())
                    } catch (_: Exception) {
                    }

                    WorkManager.getInstance(requireContext()).enqueue(work)
                    Toast.makeText(requireContext(), "Download queued", Toast.LENGTH_SHORT).show()
                }
                // If caller requested auto-download (via navigation bundle), enqueue automatically
                val autoDownload = arguments?.getBoolean("autoDownload") ?: false
                if (autoDownload) {
                    fabDownload.performClick()
                }
            } else {
                // Fallback: load the chapter page in a WebView so users can still read
                recyclerView.visibility = View.GONE
                webView.visibility = View.VISIBLE
                fabDownload.visibility = View.GONE
                if (chapterUrl.isNotEmpty()) {
                    webView.loadUrl(chapterUrl)
                }
            }
        }
    }

    private suspend fun extractImagesViaWebView(chapterUrl: String, selector: String, attrs: List<String>): List<String> = suspendCancellableCoroutine { cont ->
        try {
            // Setup a temporary JS interface to receive results
            val jsInterface = object {
                @JavascriptInterface
                fun onResult(json: String) {
                    try {
                        val arr = org.json.JSONArray(json)
                        val out = mutableListOf<String>()
                        for (i in 0 until arr.length()) out.add(arr.getString(i))
                        cont.resume(out)
                    } catch (e: Exception) {
                        cont.resume(emptyList())
                    }
                }
            }

            webView.addJavascriptInterface(jsInterface, "Extractor")

            val jsAttrs = attrs.joinToString(",") { "'${it}'" }
            val js = "(function() { try { var sel = ${org.json.JSONObject.quote(selector)}; var attrs = [${jsAttrs}]; var elements = document.querySelectorAll(sel); var results = []; elements.forEach(function(el) { for (var i=0;i<attrs.length;i++){ var a=attrs[i]; var v = el.getAttribute(a) || el[a] || ''; if (v && v.indexOf(',')>-1) v=v.split(',').pop().trim().split(' ')[0]; if (v) { if (v.startsWith('//')) v='https:'+v; if (!v.startsWith('http')) { try { v = new URL(v, window.location.href).href; } catch(e){} } results.push(v); break; } } }); Extractor.onResult(JSON.stringify(results)); } catch(e) { Extractor.onResult(JSON.stringify([])); } })();"

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        view?.evaluateJavascript(js, null)
                    } catch (_: Throwable) {
                        cont.resume(emptyList())
                    }
                }
            }

            webView.loadUrl(chapterUrl)

            cont.invokeOnCancellation {
                // cleanup
                webView.removeJavascriptInterface("Extractor")
            }
        } catch (e: Exception) {
            cont.resume(emptyList())
        }
    }

    private suspend fun fetchImageUrls(chapterUrl: String, profileJson: String?): List<String> = withContext(Dispatchers.IO) {
        // If profileJson provided, attempt to parse simple image selector configuration
        try {
            if (!profileJson.isNullOrBlank()) {
                try {
                    val json = org.json.JSONObject(profileJson)
                    val images = json.optJSONObject("images")
                    if (images != null) {
                        val selector = images.optString("selector").takeIf { it.isNotBlank() }
                        val attrsJson = images.optJSONArray("attrs")
                        val attrs = mutableListOf<String>()
                        if (attrsJson != null) {
                            for (i in 0 until attrsJson.length()) {
                                attrs.add(attrsJson.optString(i))
                            }
                        }

                        val allowJs = images.optBoolean("allowJs", false)

                        if (!selector.isNullOrBlank()) {
                            if (allowJs) {
                                // Switch to main thread for WebView extraction and wait for result
                                return@withContext withContext(Dispatchers.Main) {
                                    try {
                                        extractImagesViaWebView(chapterUrl, selector, attrs)
                                    } catch (e: Exception) {
                                        emptyList()
                                    }
                                }
                            } else {
                                // Use profile selector server-side (Jsoup)
                                val doc = Jsoup.connect(chapterUrl).get()
                                val baseUri = doc.baseUri().ifEmpty { chapterUrl }
                                val elements = doc.select(selector)
                                val urls = elements.mapNotNull { element ->
                                    var found: String? = null
                                    for (a in attrs) {
                                        val v = element.attr(a)
                                        if (!v.isNullOrBlank()) { found = v; break }
                                    }
                                    if (found.isNullOrBlank()) found = element.attr("src")
                                    if (found.isNullOrBlank()) return@mapNotNull null

                                    var src = found
                                    if (src.contains(",")) {
                                        val parts = src.split(",").map { it.trim() }
                                        src = parts.last().split(" ").firstOrNull() ?: parts.last()
                                    }

                                    val abs = element.absUrl("src").ifBlank { element.absUrl(attrs.firstOrNull() ?: "src") }
                                    val resolved = when {
                                        abs.isNotBlank() -> abs
                                        src.startsWith("//") -> "https:$src"
                                        src.startsWith("http") -> src
                                        else -> if (baseUri.endsWith("/")) baseUri + src else "$baseUri/$src"
                                    }

                                    resolved
                                }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

                                val filtered = urls.filter { it.matches(Regex("https?://.*\\.(png|jpg|jpeg|webp|gif|bmp)(\\?.*)?", RegexOption.IGNORE_CASE)) }
                                if (filtered.isNotEmpty()) return@withContext filtered
                                return@withContext urls
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore profile parse errors and fall back to generic scraping
                }
            }

            // Generic scraping (same as before)
            val doc = Jsoup.connect(chapterUrl).get()
            val baseUri = doc.baseUri().ifEmpty { chapterUrl }

            val imgElements = doc.select("img[src], img[data-src], img[data-srcset], img[srcset]")
            val urls = imgElements.mapNotNull { element ->
                var src = element.attr("data-src")
                if (src.isBlank()) src = element.attr("data-original")
                if (src.isBlank()) src = element.attr("src")
                if (src.isBlank()) src = element.attr("srcset")
                if (src.isBlank()) src = element.attr("data-srcset")

                if (src.isBlank()) return@mapNotNull null

                if (src.contains(",")) {
                    val parts = src.split(",").map { it.trim() }
                    src = parts.last().split(" ").firstOrNull() ?: parts.last()
                }

                val absFromElement = element.absUrl("src").ifBlank { element.absUrl("data-src") }
                val resolved = when {
                    absFromElement.isNotBlank() -> absFromElement
                    src.startsWith("//") -> "https:$src"
                    src.startsWith("http") -> src
                    else -> if (baseUri.endsWith("/")) baseUri + src else "$baseUri/$src"
                }

                resolved
            }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

            val filtered = urls.filter { it.matches(Regex("https?://.*\\.(png|jpg|jpeg|webp|gif|bmp)(\\?.*)?", RegexOption.IGNORE_CASE)) }
            if (filtered.isNotEmpty()) return@withContext filtered
            urls
        } catch (_: Exception) {
            emptyList()
        }
    }
}
