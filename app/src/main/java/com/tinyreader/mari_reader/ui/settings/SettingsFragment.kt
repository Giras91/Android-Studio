package com.tinyreader.mari_reader.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.widget.EditText
import android.widget.Toast
import android.text.InputType
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import com.tinyreader.mari_reader.R
import com.tinyreader.mari_reader.UpdateWorker
import java.util.concurrent.TimeUnit
import java.io.File

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var pickDirLauncher: ActivityResultLauncher<Uri?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickDirLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // Persist permission and save URI
                requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                prefs.edit().putString("mari_export_uri", uri.toString()).apply()
                Toast.makeText(requireContext(), "Export folder saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Export folder selection cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val addUrlPref = findPreference<Preference>("pref_add_url")
        addUrlPref?.setOnPreferenceClickListener {
            showAddUrlDialog()
            true
        }

        val manageUrlsPref = findPreference<Preference>("pref_manage_urls")
        manageUrlsPref?.setOnPreferenceClickListener {
            showManageUrlsDialog()
            true
        }

        val exportFolderPref = findPreference<Preference>("pref_export_folder")
        exportFolderPref?.setOnPreferenceClickListener {
            // launch SAF chooser
            pickDirLauncher.launch(null)
            true
        }

        val clearExportPref = findPreference<Preference>("pref_clear_export")
        clearExportPref?.setOnPreferenceClickListener {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val saved = prefs.getString("mari_export_uri", null)
            if (!saved.isNullOrBlank()) {
                try {
                    val uri = Uri.parse(saved)
                    val pm = requireContext().contentResolver
                    // Attempt to release persisted permissions
                    val persisted = pm.persistedUriPermissions
                    for (p in persisted) {
                        if (p.uri == uri) pm.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } catch (_: Exception) {}
                prefs.edit().remove("mari_export_uri").apply()
                Toast.makeText(requireContext(), "Cleared saved export folder", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "No export folder saved", Toast.LENGTH_SHORT).show()
            }
            true
        }

        val updateIntervalPref = findPreference<androidx.preference.ListPreference>("pref_update_interval")
        updateIntervalPref?.setOnPreferenceChangeListener { _, newValue ->
            val intervalMillis = (newValue as String).toLong()
            scheduleUpdateWork(intervalMillis)
            true
        }

        // Schedule initial work
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val interval = prefs.getString("pref_update_interval", "3600000")?.toLong() ?: 3600000L
        scheduleUpdateWork(interval)

        val viewLogsPref = findPreference<Preference>("pref_view_worker_logs")
        viewLogsPref?.setOnPreferenceClickListener {
            val logsDir = File(requireContext().filesDir, "worker_logs")
            if (!logsDir.exists() || logsDir.listFiles().isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No worker logs found", Toast.LENGTH_SHORT).show()
                return@setOnPreferenceClickListener true
            }
            val files = logsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            val names = files.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Worker logs")
                .setItems(names) { _, which ->
                    try {
                        val text = files[which].readText()
                        AlertDialog.Builder(requireContext()).setTitle(names[which]).setMessage(text).setPositiveButton("OK", null).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Failed to read log: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Close", null)
                .show()
            true
        }

        val clearWorkerLogsPref = findPreference<Preference>("pref_clear_worker_logs")
        clearWorkerLogsPref?.setOnPreferenceClickListener {
            val logsDir = File(requireContext().filesDir, "worker_logs")
            if (!logsDir.exists()) {
                Toast.makeText(requireContext(), "No worker logs to clear", Toast.LENGTH_SHORT).show()
                return@setOnPreferenceClickListener true
            }
            val files = logsDir.listFiles()?.filter { it.isFile } ?: emptyList()
            var deleted = 0
            for (f in files) {
                try {
                    if (f.delete()) deleted++
                } catch (_: Exception) {}
            }
            Toast.makeText(requireContext(), "Deleted $deleted worker log(s)", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun showAddUrlDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = "https://example.com"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }

        AlertDialog.Builder(ctx)
            .setTitle("Add URL")
            .setView(input)
            .setPositiveButton("Add") { _: android.content.DialogInterface, _: Int ->
                val url = input.text.toString().trim()
                if (Patterns.WEB_URL.matcher(url).matches()) {
                    // Save URL (example: append with timestamp key)
                    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                    val key = "saved_url_${System.currentTimeMillis()}"
                    prefs.edit().putString(key, url).apply()
                    Toast.makeText(ctx, "URL saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Invalid URL", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManageUrlsDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val allPrefs = prefs.all
        val urls = allPrefs.filterKeys { it.startsWith("saved_url_") }.values.map { it.toString() }.toTypedArray()

        if (urls.isEmpty()) {
            Toast.makeText(requireContext(), "No saved URLs", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Saved URLs")
            .setItems(urls) { _, which ->
                // On click, perhaps delete
                val keyToDelete = allPrefs.filterValues { it == urls[which] }.keys.firstOrNull()
                if (keyToDelete != null) {
                    prefs.edit().remove(keyToDelete).apply()
                    Toast.makeText(requireContext(), "URL deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun scheduleUpdateWork(intervalMillis: Long) {
        // Configure and schedule the periodic work
        val workRequest = PeriodicWorkRequestBuilder<UpdateWorker>(intervalMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "UpdateWorker",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }
}
