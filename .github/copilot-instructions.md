# Mari Reader - AI Coding Agent Instructions

## Project Overview
Mari Reader is an Android manga reader app built with Kotlin that scrapes manga from various sources. Uses traditional Android Views (not Compose), Room database, Jetpack Navigation, and WorkManager for background tasks.

**Package:** `com.tinyreader.mari_reader`  
**Min SDK:** 24 (Android 7.0) | **Target SDK:** 36

## Architecture

### Core Components
- **MainActivity**: Single-activity architecture with Bottom Navigation + NavHostFragment
- **MainViewModel**: Shared ViewModel accessed via `(activity as MainActivity).viewModel` in Fragments
- **MariReaderRepository**: Mediates between ViewModels and Room DAOs (wraps all DB ops with `withContext(Dispatchers.IO)`)
- **MariReaderDatabase**: Room database (v4) with 4 entities: `Source`, `LibraryManga`, `Chapter`, `ReadingHistory`

### Data Model
```
Source (sources) ← LibraryManga (library_manga) ← Chapter (chapters)
                                                 ← ReadingHistory
```

- **Source**: Manga website with optional `profileJson` (JSON scraping config)
- **LibraryManga**: User's library entries (FK to Source via `sourceUrl`)
- **Chapter**: Chapter list per manga (FK to LibraryManga via `mangaUrl`)
- **ReadingHistory**: Tracks last-read chapter per manga

**Primary Keys:** All use URL strings as PKs (`sourceUrl`, `mangaUrl`, `chapterUrl`)

### Scraping System (`scraper/ScraperManager.kt`)
**Two-tier scraping strategy:**
1. **Profile-based**: If `Source.profileJson` exists, use site-specific selectors
   - `chapterList.selector` + `urlAttr` for chapter links
   - `images.selector` for reader images
   - `allowJs: true` flags content needing WebView JavaScript extraction
2. **Heuristic fallback**: Generic patterns (links containing "chapter" or ending in digits)

**Critical:** Background workers (`UpdateWorker`) **cannot** scrape profiles with `allowJs: true` (no WebView access). Use `ScraperManager.profileRequiresJs()` check.

## Key Workflows

### Build & Run
```powershell
.\gradlew.bat assembleDebug          # Build APK
.\gradlew.bat installDebug           # Install to device
.\gradlew.bat clean                  # Clean build artifacts
```

### Database Migrations
When modifying entities, increment `MariReaderDatabase` version and create migration:
```kotlin
private val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE ...")
    }
}
// Add to .addMigrations() in getDatabase()
```

### Background Work
- **UpdateWorker**: Periodic manga chapter updates (uses WorkManager periodic tasks)
- **DownloadWorker**: Chapter downloads to internal storage with foreground notification
  - Requires `android.permission.FOREGROUND_SERVICE_DATA_SYNC` (Android 14+)
  - Saves to `context.filesDir/mari_reader_downloads/{manga}/{chapter}/`

**Schedule updates in SettingsFragment:**
```kotlin
WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
    "manga_update", ExistingPeriodicWorkPolicy.REPLACE,
    PeriodicWorkRequestBuilder<UpdateWorker>(intervalMillis, TimeUnit.MILLISECONDS).build()
)
```

## Project-Specific Conventions

### ViewModel Access Pattern
Fragments **do not** create their own ViewModels. Instead:
```kotlin
lateinit var viewModel: MainViewModel
override fun onViewCreated(...) {
    viewModel = (activity as MainActivity).viewModel
}
```

### Dependency Management
Uses **version catalogs** (`gradle/libs.versions.toml`):
- Reference in `build.gradle.kts`: `implementation(libs.androidx.core.ktx)`
- Add new dependencies to `[libraries]` section, versions to `[versions]`

### Annotations Conflict Resolution
Project forces `org.jetbrains:annotations:23.0.0` to resolve conflicts with `com.intellij:annotations`:
```kotlin
configurations.all {
    resolutionStrategy { force("org.jetbrains:annotations:23.0.0") }
    exclude(group = "com.intellij", module = "annotations")
}
```
**Keep this pattern** when adding KSP processors (Room, Glide).

### Navigation
Uses Navigation Component with Safe Args disabled:
- Pass data via Bundle in `navigate()` calls
- Retrieve in destination Fragment's `onViewCreated()` via `arguments?.getString(KEY)`

### ViewBinding
Enabled but **not DataBinding**. Typical Fragment pattern:
```kotlin
private var _binding: FragmentXyzBinding? = null
private val binding get() = _binding!!

override fun onCreateView(...): View {
    _binding = FragmentXyzBinding.inflate(inflater, container, false)
    return binding.root
}

override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
}
```

## UI Structure
- **ui/library/**: Library grid + manga detail views
- **ui/sources/**: Source management and browsing
- **ui/reader/**: Chapter reader with vertical RecyclerView of images
- **ui/chapter/**: Chapter list for a manga
- **ui/browser/**: WebView for JS-required scraping
- **ui/settings/**: App settings (uses PreferenceFragmentCompat)
- **ui/downloads/**: Download management

## Testing & Debugging
- Android device/emulator required (scraping needs network)
- Check logs for scraping: `Log.i("ScraperManager", ...)` / `Log.i("UpdateWorker", ...)`
- Test profile JSON via Settings → Add/Manage URLs → long-press source

## Common Pitfalls
1. **Don't use Compose APIs** - project uses traditional Views only
2. **All DB operations must be in Dispatchers.IO** - wrap with `withContext` in Repository
3. **UpdateWorker skips JS profiles** - handle gracefully, don't crash
4. **URLs as PKs** - ensure absolute URLs with proper scheme (`http://` or `https://`)
5. **WorkManager foreground service** - must declare `foregroundServiceType="dataSync"` in manifest
