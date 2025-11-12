package com.tinyreader.mari_reader.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object ScraperManager {

    /**
     * Fetch chapter links for a manga URL.
     * - If profileJson contains a chapterList with selector/urlAttr, use it (server-side).
     * - Otherwise fallback to heuristics (href contains 'chapter' or ends with digits).
     * Returns a list of Pair(title, absoluteUrl).
     */
    suspend fun fetchChapters(mangaUrl: String, profileJson: String?): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Pair<String, String>>()

        try {
            // If profile provided, try to use its selector first
            if (!profileJson.isNullOrBlank()) {
                try {
                    val prof = org.json.JSONObject(profileJson)
                    val chapterList = prof.optJSONObject("chapterList")
                    if (chapterList != null) {
                        val selector = chapterList.optString("selector")
                        val urlAttr = chapterList.optString("urlAttr", "href")
                        val doc = Jsoup.connect(mangaUrl).get()
                        val els = doc.select(selector)
                        for (e in els) {
                            val href = e.absUrl(urlAttr).ifBlank { e.attr(urlAttr) }
                            if (href.isBlank()) continue
                            val title = e.text().ifBlank { href }
                            out.add(title to href)
                        }
                        if (out.isNotEmpty()) return@withContext out
                    }
                } catch (_: Exception) {
                    // fallthrough to heuristics
                }
            }

            // Heuristic fallback: anchors with 'chapter' or numeric endings
            try {
                val doc = Jsoup.connect(mangaUrl).get()
                val anchors = doc.select("a[href]")
                val seen = mutableSetOf<String>()
                for (a in anchors) {
                    val href = a.absUrl("href").ifBlank { a.attr("href") }
                    if (href.isBlank()) continue
                    val lowered = href.lowercase()
                    if (lowered.contains("chapter") || Regex(".*/\\d+$").matches(href)) {
                        if (seen.add(href)) {
                            val title = a.text().ifBlank { href }
                            out.add(title to href)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {}

        return@withContext out
    }

    /**
     * Returns true if the provided profile indicates JS-only extraction for chapter list.
     * Profiles may include images.allowJs or chapterList.allowJs flag; we check both.
     */
    fun profileRequiresJs(profileJson: String?): Boolean {
        if (profileJson.isNullOrBlank()) return false
        return try {
            val prof = org.json.JSONObject(profileJson)
            val images = prof.optJSONObject("images")
            val imagesAllow = images?.optBoolean("allowJs", false) ?: false
            val chapter = prof.optJSONObject("chapterList")
            val chapterAllow = chapter?.optBoolean("allowJs", false) ?: false
            imagesAllow || chapterAllow
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Try to parse a chapter number from title or url. Returns -1f if unknown.
     */
    fun extractChapterNumber(title: String?, url: String?): Float {
        try {
            val t = (title ?: "").lowercase()
            val u = (url ?: "")

            // Common patterns: "chapter 123", "ch. 123", "ch123", "c123"
            val patterns = listOf(
                Regex("chapter\\s*([0-9]+(\\.[0-9]+)?)"),
                Regex("ch(?:apter)?\\.?\\s*([0-9]+(\\.[0-9]+)?)"),
                Regex("(?:^|[^0-9])([0-9]+(\\.[0-9]+)?)(?:$|[^0-9])")
            )

            for (p in patterns) {
                val mt = p.find(t)
                if (mt != null && mt.groups.size >= 2) {
                    val num = mt.groupValues[1]
                    return num.toFloatOrNull() ?: -1f
                }
                val mu = p.find(u)
                if (mu != null && mu.groups.size >= 2) {
                    val num = mu.groupValues[1]
                    return num.toFloatOrNull() ?: -1f
                }
            }

            // If URL ends with digits
            val m = Regex(".*/([0-9]+(\\.[0-9]+)?)/?$").find(u)
            if (m != null && m.groups.size >= 2) {
                return m.groupValues[1].toFloatOrNull() ?: -1f
            }
        } catch (_: Exception) {}
        return -1f
    }

    /**
     * Derive a simple selector for chapter links from the page by examining an example chapter URL.
     * Best-effort only: prefer id or class-based selectors, otherwise fall back to a contains-href selector.
     */
    suspend fun deriveSelectorFromPage(mangaUrl: String, exampleChapterUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(mangaUrl).get()
            val anchors = doc.select("a[href]")
            var matched: org.jsoup.nodes.Element? = null
            for (a in anchors) {
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                if (href.isNotBlank() && (href == exampleChapterUrl || href.contains(exampleChapterUrl))) {
                    matched = a
                    break
                }
            }
            if (matched == null && anchors.isNotEmpty()) {
                // fallback: try anchors that look like chapter links
                for (a in anchors) {
                    val href = a.absUrl("href").ifBlank { a.attr("href") }
                    if (href.isNotBlank() && (href.contains("chapter", ignoreCase = true) || Regex(".*/\\d+$").matches(href))) {
                        matched = a
                        break
                    }
                }
            }
            if (matched != null) {
                val id = matched.id()
                if (id.isNotBlank()) return@withContext "${matched.tagName()}#${id}"
                val className = matched.className().trim()
                if (className.isNotBlank()) {
                    val cls = className.split(Regex("\\s+")) .filter { it.isNotBlank() }.joinToString(".")
                    return@withContext "${matched.tagName()}.$cls"
                }
                // if parent has id/class, use parent
                val p = matched.parent()
                if (p != null) {
                    val pid = p.id()
                    if (pid.isNotBlank()) return@withContext "${p.tagName()}#${pid} ${matched.tagName()}"
                    val pclass = p.className().trim()
                    if (pclass.isNotBlank()) {
                        val pcs = pclass.split(Regex("\\s+")) .filter { it.isNotBlank() }.joinToString(".")
                        return@withContext "${p.tagName()}.$pcs ${matched.tagName()}"
                    }
                }
                // fallback to contains href
                val lastSegment = try { java.net.URL(exampleChapterUrl).path.substringAfterLast('/') } catch (_: Exception) { exampleChapterUrl }
                if (lastSegment.any { it.isDigit() }) {
                    return@withContext "a[href*='${lastSegment}']"
                }
                return@withContext "a[href*='chapter']"
            }
        } catch (_: Exception) {}
        return@withContext null
    }
}
