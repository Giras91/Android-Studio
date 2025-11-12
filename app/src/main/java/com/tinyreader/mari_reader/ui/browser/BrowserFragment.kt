package com.tinyreader.mari_reader.ui.browser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tinyreader.mari_reader.MainActivity
import com.tinyreader.mari_reader.R
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import android.widget.Toast
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class BrowserFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var fab: FloatingActionButton
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var viewModel: com.tinyreader.mari_reader.viewmodel.MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_browser, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = (activity as MainActivity).viewModel

        val sourceUrl = arguments?.getString("sourceUrl") ?: "https://example.com"

        webView = view.findViewById(R.id.web_view)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36"
        webView.addJavascriptInterface(WebAppInterface(viewModel, sourceUrl), "Android")

        // Setup a single WebViewClient that handles ad-blocking, swipe refresh indicator, and optional preview run
        val previewProfile = arguments?.getString("previewProfileJson")
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        swipeRefresh.setOnRefreshListener { webView.reload() }

        webView.webViewClient = object : AdBlockingWebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                try { swipeRefresh.isRefreshing = true } catch (_: Exception) {}
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                try { swipeRefresh.isRefreshing = false } catch (_: Exception) {}
                // If previewProfile present, run test once
                if (!previewProfile.isNullOrBlank()) {
                    try { runProfileTestOnCurrentPage(previewProfile) } catch (_: Exception) {}
                }
            }
        }

        webView.loadUrl(sourceUrl)

        // Refresh FAB
        val fabRefresh = view.findViewById<FloatingActionButton>(R.id.fab_refresh)
        fabRefresh.setOnClickListener { webView.reload() }

        fab = view.findViewById(R.id.fab_add_manga)
        fab.setOnClickListener {
            val script = "javascript:(function() { var title = document.querySelector('meta[property=\\\"og:title\\\"]')?.content || document.title; var cover = document.querySelector('meta[property=\\\"og:image\\\"]')?.content; if (cover && !cover.startsWith('http')) { cover = new URL(cover, window.location.href).href; } Android.addMangaToLibrary(window.location.href, title, cover); })()"
            webView.loadUrl(script)
        }

        // Long-press the add button to show profile helpers (Import, Auto-generate, Test, Selector tool)
        fab.setOnLongClickListener {
            showProfileHelperMenu()
            true
        }

        val fabFavorite = view.findViewById<FloatingActionButton>(R.id.fab_favorite_manga)
        fabFavorite.setOnClickListener {
            val script = "javascript:(function() { var title = document.querySelector('meta[property=\\\"og:title\\\"]')?.content || document.title; var cover = document.querySelector('meta[property=\\\"og:image\\\"]')?.content; if (cover && !cover.startsWith('http')) { cover = new URL(cover, window.location.href).href; } Android.addMangaToFavorites(window.location.href, title, cover); })()"
            webView.loadUrl(script)
        }

        // Manual Sync Now FAB - scrape chapters for current page
        val fabSync = view.findViewById<FloatingActionButton>(R.id.fab_sync_manga)
        fabSync.setOnClickListener {
            // Show chapter list dialog for the current page (fetch via ScraperManager)
            val currentUrl = webView.url ?: ""
            val origin = try { val u = currentUrl.toUri(); (u.scheme ?: "https") + "://" + (u.host ?: currentUrl) } catch (_: Exception) { currentUrl }
            if (currentUrl.isBlank()) {
                Toast.makeText(requireContext(), "No page loaded to sync", Toast.LENGTH_SHORT).show()
            } else {
                showChaptersDialog(currentUrl, origin)
            }
        }

    }

    // Fetch chapters (using ScraperManager) and show dialog allowing open or save-all into DB
    private fun showChaptersDialog(mangaUrl: String, sourceUrl: String) {
        val progress = AlertDialog.Builder(requireContext()).setTitle("Fetching chapters...").setMessage("Please wait").setCancelable(false).show()
        lifecycleScope.launch {
            try {
                val src = try { viewModel.getSource(sourceUrl) } catch (_: Exception) { null }
                val profileJson = src?.profileJson
                val results = try {
                    com.tinyreader.mari_reader.scraper.ScraperManager.fetchChapters(mangaUrl, profileJson)
                } catch (_: Exception) { emptyList<Pair<String,String>>() }

                progress.dismiss()

                if (results.isEmpty()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("No chapters found")
                        .setMessage("No chapters could be fetched for this page.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@launch
                }

                val titles = results.map { it.first.ifBlank { it.second } }
                val urls = results.map { it.second }

                var selectedIndex = 0
                val builder = AlertDialog.Builder(requireContext())
                    .setTitle("Chapters found: ${results.size}")
                    .setSingleChoiceItems(titles.toTypedArray(), 0) { _, which -> selectedIndex = which }

                builder.setPositiveButton("Open") { _, _ ->
                    val url = urls.getOrNull(selectedIndex)
                    if (!url.isNullOrBlank()) {
                        // open in reader so images can be loaded and optionally downloaded
                        val bundle = Bundle().apply {
                            putString("chapterUrl", url)
                            putString("mangaUrl", mangaUrl)
                            putBoolean("autoDownload", false)
                        }
                        findNavController().navigate(R.id.navigation_reader, bundle)
                    }
                }

                builder.setNeutralButton("Download Selected") { _, _ ->
                    val url = urls.getOrNull(selectedIndex)
                    if (!url.isNullOrBlank()) {
                        val bundle = Bundle().apply {
                            putString("chapterUrl", url)
                            putString("mangaUrl", mangaUrl)
                            putBoolean("autoDownload", true)
                        }
                        findNavController().navigate(R.id.navigation_reader, bundle)
                    }
                }

                builder.setNegativeButton("Save all") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            var idx = 0
                            val seen = mutableSetOf<String>()
                            for ((t, u) in results) {
                                if (u.isBlank()) continue
                                if (seen.contains(u)) continue
                                seen.add(u)
                                val num = com.tinyreader.mari_reader.scraper.ScraperManager.extractChapterNumber(t,u)
                                val chapterNum = if (num > 0f) num else (idx + 1).toFloat()
                                val chapter = com.tinyreader.mari_reader.data.Chapter(chapterUrl = u, mangaUrl = mangaUrl, title = t, chapterNumber = chapterNum)
                                try { viewModel.insertChapter(chapter) } catch (_: Exception) {}
                                idx++
                            }
                            Toast.makeText(requireContext(), "Saved ${results.size} chapters", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(requireContext(), "Failed to save chapters", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                builder.setCancelable(true)
                builder.show()

            } catch (_: Exception) {
                try { progress.dismiss() } catch (_: Exception) {}
                AlertDialog.Builder(requireContext()).setTitle("Error").setMessage("Failed to fetch chapters").setPositiveButton("OK", null).show()
            }
        }
    }

    private fun showProfileHelperMenu() {
        val options = arrayOf("Import/Paste Profile", "Auto-generate Profile", "Test Current Profile on Page", "Interactive Selector Tool")
        AlertDialog.Builder(requireContext())
            .setTitle("Profile Helpers")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showImportProfileDialog()
                    1 -> autoGenerateProfile()
                    2 -> testCurrentProfile()
                    3 -> startInteractiveSelector()
                }
            }
            .show()
    }

    private fun startInteractiveSelector() {
        // Inject JS to highlight clicked elements and store a CSS selector in window.__mari_selector
        val installJs = """(function(){
            if(window.__mari_selector_installed) return 'installed';
            window.__mari_selector_installed = true;
            window.__mari_selector = '';
            function uniqueSelector(el){
                if(!el) return '';
                if(el.id) return el.tagName.toLowerCase() + '#' + el.id;
                var path = [];
                while(el && el.nodeType===1){
                    var name = el.tagName.toLowerCase();
                    var parent = el.parentNode;
                    if(parent){
                        var siblings = Array.from(parent.children).filter(function(c){ return c.tagName===el.tagName});
                        if(siblings.length>1){
                            var idx = Array.prototype.indexOf.call(parent.children, el)+1;
                            name += ':nth-child('+idx+')';
                        }
                    }
                    path.unshift(name);
                    el = el.parentNode;
                }
                return path.join(' > ');
            }
            document.addEventListener('click', function(ev){
                ev.preventDefault(); ev.stopPropagation();
                var el = ev.target;
                var sel = uniqueSelector(el);
                window.__mari_selector = sel;
                // add a temporary outline
                var prev = document.querySelectorAll('.__mari_sel_outline');
                prev.forEach(function(p){ p.classList.remove('__mari_sel_outline'); p.style.outline=''; });
                el.classList.add('__mari_sel_outline');
                el.style.outline = '3px solid rgba(255,0,0,0.8)';
                return false;
            }, true);
            return 'ok';
        })();"""
        webView.evaluateJavascript(installJs, null)

        // Show a dialog with instructions and a button to fetch the selector
        val btnGet = "Get selector"
        AlertDialog.Builder(requireContext())
            .setTitle("Interactive Selector Tool")
            .setMessage("Tap an element on the page to highlight it. Then press 'Get selector' to copy the CSS selector into the profile editor.")
            .setPositiveButton(btnGet) { _, _ ->
                webView.evaluateJavascript("(function(){return window.__mari_selector||'';})()") { sel ->
                    val selector = sel?.trim('"') ?: ""
                    val edit = EditText(requireContext())
                    edit.setText(selector)
                    AlertDialog.Builder(requireContext())
                        .setTitle("Selector")
                        .setView(edit)
                        .setPositiveButton("Use") { _, _ ->
                            // copy into import dialog flow: show Import dialog pre-filled
                            showImportProfileDialogWithSelector(edit.text.toString())
                        }
                        .setNegativeButton("Close", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showImportProfileDialogWithSelector(selector: String) {
        val currentUrl = webView.url ?: ""
        val origin = try {
            val uri = currentUrl.toUri()
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: uri.toString()
            "$scheme://$host"
        } catch (_: Exception) {
            currentUrl
        }

        val edit = EditText(requireContext())
        val suggested = org.json.JSONObject()
        val im = org.json.JSONObject()
        im.put("selector", selector)
        val arr = org.json.JSONArray()
        arr.put("data-src"); arr.put("data-original"); arr.put("src"); arr.put("srcset")
        im.put("attrs", arr)
        im.put("allowJs", true)
        suggested.put("images", im)
        edit.setText(suggested.toString(2))

        AlertDialog.Builder(requireContext())
            .setTitle("Import Scraper Profile for $origin")
            .setView(edit)
            .setPositiveButton("Save") { _, _ ->
                val json = edit.text.toString().trim().takeIf { it.isNotEmpty() }
                if (json != null) confirmAndSaveProfile(origin, json)
            }
            .setNeutralButton("Preview") { _, _ ->
                val json = edit.text.toString().trim().takeIf { it.isNotEmpty() }
                if (!json.isNullOrBlank()) runProfileTestOnCurrentPage(json)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showImportProfileDialog() {
        val currentUrl = webView.url ?: ""
        val origin = try {
            val uri = currentUrl.toUri()
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: uri.toString()
            "$scheme://$host"
        } catch (_: Exception) {
            currentUrl
        }

        val edit = EditText(requireContext())
        edit.hint = "Paste scraper JSON here"

        AlertDialog.Builder(requireContext())
            .setTitle("Import Scraper Profile for $origin")
            .setView(edit)
            .setPositiveButton("Save") { _, _ ->
                val json = edit.text.toString().trim().takeIf { it.isNotEmpty() }
                if (json != null) confirmAndSaveProfile(origin, json)
            }
            .setNeutralButton("Preview") { _, _ ->
                val json = edit.text.toString().trim().takeIf { it.isNotEmpty() }
                if (!json.isNullOrBlank()) runProfileTestOnCurrentPage(json)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun autoGenerateProfile() {
        // Improved heuristic: find containers with multiple images and return selector for the most likely container
        val js = """(function(){try{var imgs = Array.from(document.querySelectorAll('img')); if(imgs.length==0) return JSON.stringify({}); var parents = {}; imgs.forEach(function(i){var p = i.closest('div,section,article') || i.parentElement; if(!p) return; var key = p.tagName + (p.id?('#'+p.id): (p.className?('.'+p.className.split(/\s+/).join('.')):'')); if(!parents[key]) parents[key]=0; parents[key]++; }); var best=null; var max=0; for(var k in parents){ if(parents[k]>max){ max=parents[k]; best=k}}; var selector = null; if(best){ selector = best; } else selector='img'; var out = {chapterList:{selector:"a[href*=chapter]", urlAttr:"href"}, images:{selector:selector, attrs:["data-src","data-original","src","srcset"], allowJs:false}}; return JSON.stringify(out);}catch(e){return JSON.stringify({});}})();"""
        webView.evaluateJavascript(js) { result ->
            try {
                val parsed = org.json.JSONObject(result)
                val suggested = org.json.JSONObject()
                if (parsed.has("chapterList")) suggested.put("chapterList", parsed.getJSONObject("chapterList"))
                if (parsed.has("images")) suggested.put("images", parsed.getJSONObject("images"))

                // Show suggested profile for editing/saving
                val edit = EditText(requireContext())
                edit.setText(suggested.toString(2))
                AlertDialog.Builder(requireContext())
                    .setTitle("Auto-generated profile")
                    .setView(edit)
                    .setPositiveButton("Save") { _, _ ->
                        val json = edit.text.toString().trim().takeIf { it.isNotEmpty() }
                        if (json != null) {
                            val currentUrl = webView.url ?: ""
                            val origin = try {
                                val uri = currentUrl.toUri()
                                val scheme = uri.scheme ?: "https"
                                val host = uri.host ?: uri.toString()
                                "$scheme://$host"
                            } catch (_: Exception) { currentUrl }

                            confirmAndSaveProfile(origin, json)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Auto-generate failed")
                    .setMessage("Could not auto-generate profile: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun testCurrentProfile() {
        // Attempt to fetch existing stored profile for the current page origin and run tests
        val currentUrl = webView.url ?: ""
        val origin = try {
            val uri = currentUrl.toUri()
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: uri.toString()
            "$scheme://$host"
        } catch (_: Exception) { currentUrl }

        // Fetch source and profile
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    val src = viewModel.getSource(origin)
                    val profile = src?.profileJson
                    if (!profile.isNullOrBlank()) {
                        runProfileTestOnCurrentPage(profile)
                    } else {
                        AlertDialog.Builder(requireContext())
                            .setTitle("No profile found")
                            .setMessage("No scraper profile saved for $origin")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } catch (e: Exception) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Error")
                        .setMessage("Failed to fetch profile: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun confirmAndSaveProfile(origin: String, json: String) {
        try {
            val obj = org.json.JSONObject(json)
            val images = obj.optJSONObject("images")
            val allowJs = images?.optBoolean("allowJs", false) ?: false
            if (allowJs) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Enable JS Extraction?")
                    .setMessage("This profile enables JavaScript-based extraction (images.allowJs = true).\nRunning JS on third-party pages can be a security risk. Proceed only if you trust the site.")
                    .setPositiveButton("Save") { _, _ ->
                        val source = com.tinyreader.mari_reader.data.Source(sourceUrl = origin, name = origin, iconUrl = null, profileJson = obj.toString())
                        viewModel.insertSource(source)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                val source = com.tinyreader.mari_reader.data.Source(sourceUrl = origin, name = origin, iconUrl = null, profileJson = obj.toString())
                viewModel.insertSource(source)
            }
        } catch (e: Exception) {
            AlertDialog.Builder(requireContext()).setTitle("Invalid profile").setMessage("${e.message}").setPositiveButton("OK", null).show()
        }
    }

    private fun runProfileTestOnCurrentPage(profileJson: String) {
        try {
            val json = org.json.JSONObject(profileJson)
            // Test chapterList if present
            val chapterList = json.optJSONObject("chapterList")
            if (chapterList != null) {
                val selector = chapterList.optString("selector")
                val urlAttr = chapterList.optString("urlAttr", "href")
                val js = "(function(){try{var sel=${org.json.JSONObject.quote(selector)};var attr=${org.json.JSONObject.quote(urlAttr)};var els=Array.from(document.querySelectorAll(sel));var out=[];els.forEach(function(e){out.push({url:e[attr]||e.href||'', title: (e.textContent||'').trim()});});return JSON.stringify(out);}catch(e){return JSON.stringify([]);}})();"
                webView.evaluateJavascript(js) { result ->
                    try {
                        val arr = org.json.JSONArray(result)
                        val sb = StringBuilder()
                        val urls = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            sb.append("${i+1}. ${o.optString("title")} -> ${o.optString("url")}\n")
                            urls.add(o.optString("url"))
                            if (i >= 49) break
                        }
                        val message = if (sb.isEmpty()) "No chapter links found with selector $selector" else sb.toString()
                        AlertDialog.Builder(requireContext())
                            .setTitle("Chapter test results")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                    } catch (e: Exception) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Test failed")
                            .setMessage("Failed to parse results: ${e.message}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }

            val images = json.optJSONObject("images")
            if (images != null) {
                val selector = images.optString("selector")
                val attrsJson = images.optJSONArray("attrs")
                val attrs = mutableListOf<String>()
                if (attrsJson != null) { for (i in 0 until attrsJson.length()) attrs.add(attrsJson.optString(i)) }

                val allowJs = images.optBoolean("allowJs", false)
                if (allowJs) {
                    // Use JS to extract image attributes
                    val jsAttrs = attrs.joinToString(",") { "'${it}'" }
                    val js = "(function(){try{var sel=${org.json.JSONObject.quote(selector)};var attrs=[${jsAttrs}];var els=Array.from(document.querySelectorAll(sel));var out=[];els.forEach(function(el){for(var i=0;i<attrs.length;i++){var a=attrs[i];var v=el.getAttribute(a)||el[a]||''; if(v){ if(v.indexOf(',')>-1) v=v.split(',').pop().trim().split(' ')[0]; if(v.startsWith('//')) v='https:'+v; if(!v.startsWith('http')){try{v=new URL(v,window.location.href).href}catch(e){}} out.push(v); break;}});return JSON.stringify(out);}catch(e){return JSON.stringify([]);}})();"
                    webView.evaluateJavascript(js) { result ->
                        try {
                            val arr = org.json.JSONArray(result)
                            val urls = mutableListOf<String>()
                            for (i in 0 until arr.length()) { urls.add(arr.optString(i)); if (i>=49) break }
                            val sb = StringBuilder()
                            for (i in 0 until Math.min(50, urls.size)) { sb.append("${i+1}. ${urls[i]}\n") }
                            val message = if (sb.isEmpty()) "No images found with selector $selector" else sb.toString()
                            AlertDialog.Builder(requireContext()).setTitle("Image test results").setMessage(message).setPositiveButton("OK", null).show()
                            if (urls.isNotEmpty()) showImagePreview(urls)
                        } catch (e: Exception) {
                            AlertDialog.Builder(requireContext()).setTitle("Test failed").setMessage("Failed to parse image results: ${e.message}").setPositiveButton("OK", null).show()
                        }
                    }
                } else {
                    // Server-side test via Jsoup
                    lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            try {
                                val doc = org.jsoup.Jsoup.connect(webView.url ?: "").get()
                                val els = doc.select(selector)
                                val sb = StringBuilder()
                                val urls = mutableListOf<String>()
                                for (i in 0 until Math.min(50, els.size)) {
                                    val e = els[i]
                                    val candidate = attrs.firstNotNullOfOrNull { a -> e.attr(a).takeIf { it.isNotBlank() } } ?: e.attr("src")
                                    sb.append("${i+1}. $candidate\n")
                                    urls.add(candidate)
                                }
                                val message = if (sb.isEmpty()) "No images found with selector $selector" else sb.toString()
                                AlertDialog.Builder(requireContext()).setTitle("Image test results").setMessage(message).setPositiveButton("OK", null).show()
                                if (urls.isNotEmpty()) showImagePreview(urls)
                            } catch (e: Exception) {
                                AlertDialog.Builder(requireContext()).setTitle("Test failed").setMessage("Failed to run server-side test: ${e.message}").setPositiveButton("OK", null).show()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AlertDialog.Builder(requireContext()).setTitle("Invalid profile").setMessage("${e.message}").setPositiveButton("OK", null).show()
        }
    }

    private fun showImagePreview(urls: List<String>) {
        // Show up to 4 thumbnails in a horizontal LinearLayout
        val max = Math.min(urls.size, 4)
        val container = LinearLayout(requireContext())
        container.orientation = LinearLayout.HORIZONTAL
        container.setPadding(8)
        for (i in 0 until max) {
            val iv = ImageView(requireContext())
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.weight = 1f
            iv.layoutParams = lp
            iv.adjustViewBounds = true
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(requireContext()).load(urls[i]).into(iv)
            container.addView(iv)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Preview")
            .setView(container)
            .setPositiveButton("OK", null)
            .show()
    }

    open class AdBlockingWebViewClient : WebViewClient() {
        private val AD_HOSTS = setOf(
            "doubleclick.net",
            "admob.com",
            "googleadservices.com",
            "googlesyndication.com",
            "adsystem.amazon.com"
        )

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            request?.url?.host?.let { host ->
                if (AD_HOSTS.any { host.contains(it) }) {
                    return WebResourceResponse("text/plain", "UTF-8", 200, "OK", mapOf(), null)
                }
            }
            return super.shouldInterceptRequest(view, request)
        }
    }

    inner class WebAppInterface(val viewModel: com.tinyreader.mari_reader.viewmodel.MainViewModel, val sourceUrl: String) {
        @Suppress("unused")
        @android.webkit.JavascriptInterface
        fun addMangaToLibrary(mangaUrl: String, title: String, coverUrl: String?) {
            val cover = if (coverUrl == "null" || coverUrl.isNullOrEmpty()) null else coverUrl
            val manga = com.tinyreader.mari_reader.data.LibraryManga(
                mangaUrl = mangaUrl,
                sourceUrl = sourceUrl,
                title = title,
                coverImageUrl = cover,
                description = null
            )
            viewModel.insertManga(manga)
        }

        @Suppress("unused")
        @android.webkit.JavascriptInterface
        fun addMangaToFavorites(mangaUrl: String, title: String, coverUrl: String?) {
            val cover = if (coverUrl == "null" || coverUrl.isNullOrEmpty()) null else coverUrl
            val manga = com.tinyreader.mari_reader.data.LibraryManga(
                mangaUrl = mangaUrl,
                sourceUrl = sourceUrl,
                title = title,
                coverImageUrl = cover,
                description = null,
                isFavorite = true
            )
            viewModel.insertManga(manga)
            // After inserting as favorite, scrape chapters for this manga and save them
            try {
                this@BrowserFragment.lifecycleScope.launch {
                    try {
                        scrapeChaptersForManga(mangaUrl, sourceUrl)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        @Suppress("unused")
        @android.webkit.JavascriptInterface
        fun scrapeChapters(mangaUrl: String, chaptersJson: String) {
            try {
                val jsonArray = org.json.JSONArray(chaptersJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val url = obj.getString("url")
                    val title = obj.getString("title")
                    val chapter = com.tinyreader.mari_reader.data.Chapter(
                        chapterUrl = url,
                        mangaUrl = mangaUrl,
                        title = title
                    )
                    viewModel.insertChapter(chapter)
                }
            } catch (_: Exception) {
                // Handle error
            }
        }
    }

    // Scrape chapters for a manga and insert them into the DB. Uses ScraperManager and profile support.
    private suspend fun scrapeChaptersForManga(mangaUrl: String, sourceUrl: String) {
        // Get source and profile (origin is scheme://host)
        val origin = try { val u = java.net.URL(sourceUrl); "${u.protocol}://${u.host}" } catch (_: Exception) { sourceUrl }
        val src = viewModel.getSource(origin) ?: viewModel.getSource(sourceUrl)
        val profileJson = src?.profileJson

        // If profile requires JS and WebView is currently on the same page, prefer JS extraction
        if (com.tinyreader.mari_reader.scraper.ScraperManager.profileRequiresJs(profileJson)) {
            try {
                // If WebView currently displays the manga page, run JS to extract chapter links and call the JS bridge
                val current = try { webView.url } catch (_: Exception) { null }
                if (current != null && current.startsWith(mangaUrl)) {
                    // Run a script to collect chapter anchors using the profile selector (if present)
                    val js = if (!profileJson.isNullOrBlank()) {
                        try {
                            val prof = org.json.JSONObject(profileJson)
                            val ch = prof.optJSONObject("chapterList")
                            val sel = ch?.optString("selector") ?: ""
                            val attr = ch?.optString("urlAttr", "href") ?: "href"
                            // JS: find elements by selector, return JSON array of {title, url}
                            "(function(){try{var arr=[]; var els=document.querySelectorAll(${org.json.JSONObject.quote(sel)}); for(var i=0;i<els.length;i++){var e=els[i]; var u = e.getAttribute(${org.json.JSONObject.quote(attr)}) || e.href || ''; if(!u) continue; if(u.indexOf('http')!==0){ try{u=new URL(u,window.location.href).href;}catch(e){} } var t=e.textContent||u; arr.push({title:t.trim(), url:u}); } return JSON.stringify(arr);}catch(e){return JSON.stringify([]);}})();"
                        } catch (_: Exception) {
                            "(function(){return JSON.stringify([]);})();"
                        }
                    } else {
                        // Generic JS that attempts to find anchors with 'chapter' in text or href
                        "(function(){try{var arr=[]; var anchors=document.querySelectorAll('a[href]'); for(var i=0;i<anchors.length;i++){var a=anchors[i]; var u=a.href||a.getAttribute('href')||''; var t=a.textContent||u; if(u.toLowerCase().indexOf('chapter')>-1 || t.toLowerCase().indexOf('chapter')>-1 || /\\/\\d+$/.test(u)){ if(u.indexOf('http')!==0){ try{u=new URL(u,window.location.href).href;}catch(e){} } arr.push({title:t.trim(), url:u}); } } return JSON.stringify(arr);}catch(e){return JSON.stringify([]);}})();"
                    }

                    // Evaluate JS on main thread and parse results
                    val jsResult = suspendCancellableCoroutine<String?> { cont ->
                        try {
                            webView.evaluateJavascript(js) { res ->
                                cont.resume(res)
                            }
                        } catch (_: Exception) {
                            cont.resume(null)
                        }
                    }

                    if (!jsResult.isNullOrBlank()) {
                        try {
                            val arr = org.json.JSONArray(jsResult)
                            val chapters = mutableListOf<Pair<String,String>>()
                            for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)
                                val u = o.optString("url")
                                val t = o.optString("title")
                                if (u.isNullOrBlank()) continue
                                chapters.add(t to u)
                            }
                            if (chapters.isNotEmpty()) {
                                // insert
                                val seen = mutableSetOf<String>()
                                for ((idx, pair) in chapters.withIndex()) {
                                    val (t,u) = pair
                                    if (u.isBlank()) continue
                                    if (seen.contains(u)) continue
                                    seen.add(u)
                                    val num = com.tinyreader.mari_reader.scraper.ScraperManager.extractChapterNumber(t,u)
                                    val chapterNum = if (num > 0f) num else (idx + 1).toFloat()
                                    val chapter = com.tinyreader.mari_reader.data.Chapter(chapterUrl = u, mangaUrl = mangaUrl, title = t, chapterNumber = chapterNum)
                                    try { viewModel.insertChapter(chapter) } catch (_: Exception) {}
                                }
                                // After successful manual JS scrape, try deriving a selector to persist for this origin if none exists
                                try {
                                    if (src?.profileJson.isNullOrBlank()) {
                                        val sampleUrl = chapters.firstOrNull()?.second
                                        if (!sampleUrl.isNullOrBlank()) {
                                            val derived = com.tinyreader.mari_reader.scraper.ScraperManager.deriveSelectorFromPage(mangaUrl, sampleUrl)
                                            if (!derived.isNullOrBlank()) {
                                                val prof = org.json.JSONObject()
                                                val ch = org.json.JSONObject()
                                                ch.put("selector", derived)
                                                ch.put("urlAttr", "href")
                                                prof.put("chapterList", ch)
                                                val source = com.tinyreader.mari_reader.data.Source(sourceUrl = origin, name = origin, iconUrl = null, profileJson = prof.toString())
                                                viewModel.insertSource(source)
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                                return
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback: use ScraperManager (server-side heuristics)
        try {
            val results = com.tinyreader.mari_reader.scraper.ScraperManager.fetchChapters(mangaUrl, profileJson)
            if (results.isNotEmpty()) {
                val seen = mutableSetOf<String>()
                for ((idx, pair) in results.withIndex()) {
                    val (t, u) = pair
                    if (u.isBlank()) continue
                    if (seen.contains(u)) continue
                    seen.add(u)
                        val num = com.tinyreader.mari_reader.scraper.ScraperManager.extractChapterNumber(t,u)
                        val chapterNum = if (num > 0f) num else (idx + 1).toFloat()
                        val chapter = com.tinyreader.mari_reader.data.Chapter(chapterUrl = u, mangaUrl = mangaUrl, title = t, chapterNumber = chapterNum)
                        try { viewModel.insertChapter(chapter) } catch (_: Exception) {}
                 }
            }
        } catch (_: Exception) {}
    }

    // Find adjacent chapter link (next/prev) from the currently loaded WebView page.
    @Suppress("unused")
    private suspend fun findAdjacentChapterUrl(direction: String): String? {
         return suspendCancellableCoroutine { cont ->
             try {
                val dirQuoted = org.json.JSONObject.quote(direction)
                val js = "(function(){try{var dir=${dirQuoted}; var selectors=['a[rel=next]','a[rel=\"next\"]','a.next','a[class*=\"next\" i]','a[aria-label*=\"next\" i]']; if(dir==='prev'){ selectors=['a[rel=prev]','a[rel=\"prev\"]','a.prev','a[class*=\"prev\" i]','a[aria-label*=\"prev\" i]']; } for(var s of selectors){ var el=document.querySelector(s); if(el && (el.href||el.getAttribute('href'))) return (el.href||el.getAttribute('href')); } var anchors=Array.from(document.querySelectorAll('a[href]')); var targetWords = dir==='next'? ['next','next chapter','newer','>','>>','»','›','→'] : ['prev','previous','previous chapter','older','<','<<','«','‹','←']; for(var a of anchors){ var t=(a.textContent||a.innerText||'').trim().toLowerCase(); var href=a.getAttribute('href')||a.href||''; if(!href) continue; for(var w of targetWords){ if(t.indexOf(w)!==-1) { try{ if(href.indexOf('http')!==0) href=new URL(href,window.location.href).href;}catch(e){} return href; } } } return ''; }catch(e){return '';}})();"

                webView.evaluateJavascript(js) { result ->
                     try {
                         if (result == null) { cont.resume(null); return@evaluateJavascript }
                         var res = result.trim()
                        // result is a JS string literal (quoted), remove surrounding quotes if present
                        if (res.length >= 2 && (res.first() == '"' || res.first() == '\'')) {
                            res = res.substring(1, res.length - 1)
                        }
                        // Unescape common escaped characters
                        res = res.replace("\\\"", "\"").replace("\\/","/")
                        if (res.isBlank() || res == "null") cont.resume(null) else cont.resume(res)
                     } catch (_: Exception) {
                        cont.resume(null)
                     }
                }
             } catch (_: Exception) {
                try { cont.resume(null) } catch (_: Exception) {}
             }
         }
     }
}
