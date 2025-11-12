package com.tinyreader.mari_reader.ui.sources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tinyreader.mari_reader.MainActivity
import com.tinyreader.mari_reader.R
import com.tinyreader.mari_reader.data.Source
import com.tinyreader.mari_reader.viewmodel.MainViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SourcesFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SourcesAdapter
    private lateinit var fab: FloatingActionButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_sources, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = (activity as MainActivity).viewModel

        recyclerView = view.findViewById(R.id.recycler_view_sources)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = SourcesAdapter({ source ->
            val bundle = Bundle().apply { putString("sourceUrl", source.sourceUrl) }
            findNavController().navigate(R.id.navigation_browser, bundle)
        }, { source ->
            showSourceOptions(source)
        })
        recyclerView.adapter = adapter

        fab = view.findViewById(R.id.fab_add_source)
        fab.setOnClickListener { showAddSourceDialog() }
        fab.setOnLongClickListener {
            showProfilesActions()
            true
        }

        viewModel.allSources.observe(viewLifecycleOwner) { sources ->
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val savedUrls = prefs.all.filterKeys { it.startsWith("saved_url_") }.values.map { it.toString() }
            val prefSources = savedUrls.map { Source(it, it) }
            val combined = sources + prefSources
            adapter.submitList(combined)
        }
    }

    private fun showAddSourceDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_source, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.edit_name)
        val urlEdit = dialogView.findViewById<EditText>(R.id.edit_url)
        AlertDialog.Builder(requireContext())
            .setTitle("Add Source")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameEdit.text.toString().trim()
                val url = urlEdit.text.toString().trim()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    viewModel.insertSource(Source(sourceUrl = url, name = name))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSourceOptions(source: Source) {
        val opts = arrayOf("View/Edit Scraper Profile", "Delete Source")
        AlertDialog.Builder(requireContext())
            .setTitle(source.name)
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> showEditProfileDialog(source)
                    1 -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Delete Source")
                            .setMessage("Delete ${source.name}?")
                            .setPositiveButton("Delete") { _, _ -> viewModel.deleteSource(source) }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showEditProfileDialog(source: Source) {
        val edit = EditText(requireContext())
        edit.hint = "Paste or edit scraper JSON here"
        edit.setText(source.profileJson ?: "")
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Scraper Profile for ${source.sourceUrl}")
            .setView(edit)
            .setPositiveButton("Save") { _, _ ->
                val json = edit.text.toString().trim().takeIf { it.isNotEmpty() }
                viewModel.updateSource(source.copy(profileJson = json))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProfilesActions() {
        val opts = arrayOf("Export profiles", "Import profiles (paste JSON)", "Import example profiles")
        AlertDialog.Builder(requireContext())
            .setTitle("Profiles")
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> exportProfiles()
                    1 -> importProfilesDialog()
                    2 -> importExampleProfiles()
                }
            }
            .show()
    }

    private fun exportProfiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = viewModel.allSources.value ?: emptyList()
            val profiles = list.filter { !it.profileJson.isNullOrBlank() }
            if (profiles.isEmpty()) {
                AlertDialog.Builder(requireContext()).setTitle("No profiles").setMessage("No saved profiles to export.").setPositiveButton("OK", null).show()
                return@launch
            }
            val arr = org.json.JSONArray()
            profiles.forEach { s ->
                val obj = org.json.JSONObject()
                obj.put("sourceUrl", s.sourceUrl)
                obj.put("name", s.name)
                obj.put("profileJson", org.json.JSONObject(s.profileJson!!))
                arr.put(obj)
            }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(requireContext().filesDir, "profiles_export_$ts.json")
            file.writeText(arr.toString(2))
            AlertDialog.Builder(requireContext()).setTitle("Exported").setMessage("Profiles exported to: ${file.absolutePath}").setPositiveButton("OK", null).show()
        }
    }

    private fun importProfilesDialog() {
        val edit = EditText(requireContext())
        edit.hint = "Paste array of profiles here"
        AlertDialog.Builder(requireContext())
            .setTitle("Import Profiles")
            .setView(edit)
            .setPositiveButton("Import") { _, _ ->
                val text = edit.text.toString().trim()
                if (text.isNotEmpty()) importProfilesFromJson(text)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importProfilesFromJson(jsonText: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tok = org.json.JSONTokener(jsonText)
                val root = tok.nextValue()
                if (root is org.json.JSONArray) {
                    for (i in 0 until root.length()) {
                        val obj = root.getJSONObject(i)
                        handleImportedObject(obj)
                    }
                    AlertDialog.Builder(requireContext()).setTitle("Import complete").setPositiveButton("OK", null).show()
                } else if (root is org.json.JSONObject) {
                    handleImportedObject(root)
                    AlertDialog.Builder(requireContext()).setTitle("Import complete").setPositiveButton("OK", null).show()
                } else {
                    AlertDialog.Builder(requireContext()).setTitle("Import failed").setMessage("Invalid JSON format").setPositiveButton("OK", null).show()
                }
            } catch (e: Exception) {
                AlertDialog.Builder(requireContext()).setTitle("Import failed").setMessage(e.message).setPositiveButton("OK", null).show()
            }
        }
    }

    private fun handleImportedObject(obj: org.json.JSONObject) {
        val sourceUrl = obj.optString("sourceUrl").trim()
        val name = obj.optString("name", sourceUrl).trim()
        val profileObj = obj.optJSONObject("profileJson")
        val profile = profileObj?.toString() ?: obj.optString("profileJson", "").trim()
        if (sourceUrl.isNotBlank() && profile.isNotBlank()) {
            try {
                val p = org.json.JSONObject(profile)
                val s = Source(sourceUrl = sourceUrl, name = name.ifEmpty { sourceUrl }, profileJson = p.toString())
                viewModel.insertSource(s)
            } catch (_: Exception) {
                // ignore invalid profile
            }
        }
    }

    private fun importExampleProfiles() {
        val examples = org.json.JSONArray()

        // Example 1: simple chapter list and images by selector
        val ex1 = org.json.JSONObject()
        ex1.put("sourceUrl", "https://example-manga-site.com")
        ex1.put("name", "Example Manga Site")
        val profile1 = org.json.JSONObject()
        val chap = org.json.JSONObject()
        chap.put("selector", "a[href*=chapter]")
        chap.put("urlAttr", "href")
        profile1.put("chapterList", chap)
        val imgs = org.json.JSONObject()
        imgs.put("selector", "div.page img")
        imgs.put("attrs", org.json.JSONArray().put("data-src").put("src"))
        imgs.put("allowJs", false)
        profile1.put("images", imgs)
        ex1.put("profileJson", profile1)
        examples.put(ex1)

        // Example 2: JS-driven gallery
        val ex2 = org.json.JSONObject()
        ex2.put("sourceUrl", "https://js-manga-site.example")
        ex2.put("name", "JS Manga Site")
        val profile2 = org.json.JSONObject()
        val imgs2 = org.json.JSONObject()
        imgs2.put("selector", "div.viewer img")
        imgs2.put("attrs", org.json.JSONArray().put("data-src").put("src"))
        imgs2.put("allowJs", true)
        profile2.put("images", imgs2)
        ex2.put("profileJson", profile2)
        examples.put(ex2)

        // Insert examples into DB
        val added = mutableListOf<String>()
        for (i in 0 until examples.length()) {
            try {
                val o = examples.getJSONObject(i)
                val srcUrl = o.optString("sourceUrl")
                val name = o.optString("name", srcUrl)
                val profile = o.optJSONObject("profileJson")?.toString() ?: ""
                if (srcUrl.isNotBlank() && profile.isNotBlank()) {
                    val s = Source(sourceUrl = srcUrl, name = name, profileJson = profile)
                    viewModel.insertSource(s)
                    added.add(name)
                }
            } catch (_: Exception) {}
        }
        AlertDialog.Builder(requireContext()).setTitle("Imported examples").setMessage("Imported: ${added.joinToString(", ")}").setPositiveButton("OK", null).show()
    }
}
