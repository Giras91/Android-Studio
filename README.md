# Mari Reader 📖

A modern Android manga reader app that scrapes manga from various online sources. Built with Kotlin, Room database, and traditional Android Views for optimal performance and compatibility.

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-orange.svg)](https://developer.android.com/studio/releases/platforms)

## ✨ Features

### 📚 Library Management
- **Personal Library**: Organize your favorite manga in a clean grid layout
- **Reading Progress**: Automatically tracks your last-read chapter
- **Favorites**: Mark manga as favorites for quick access
- **Smart Resume**: Tap a manga to continue from where you left off

### 🌐 Source Management
- **Multiple Sources**: Add and manage various manga websites
- **Custom Profiles**: Configure site-specific scraping rules with JSON profiles
- **Web Scraping**: Intelligent scraping with fallback heuristics
- **Browser Integration**: WebView support for JavaScript-heavy sites

### 📖 Reading Experience
- **Vertical Scrolling**: Smooth vertical scrolling through manga pages
- **Chapter Navigation**: Easy navigation between chapters
- **Offline Reading**: Download chapters for offline viewing
- **Image Optimization**: Efficient image loading and caching

### ⚙️ Advanced Features
- **Background Updates**: Automatic chapter checking with configurable intervals
- **Download Manager**: Batch download chapters with progress tracking
- **Export Functionality**: Export downloads to external storage via SAF
- **Theme Support**: Light, dark, and system theme options
- **Font Size Control**: Adjustable reading font size

### 🔧 Technical Features
- **Foreground Downloads**: Reliable downloads with Android 14+ foreground service support
- **WorkManager Integration**: Robust background task scheduling
- **Room Database**: Local storage with migration support
- **Modern Architecture**: MVVM pattern with Repository layer

## 🚀 Installation

### Prerequisites
- **Android Studio**: Arctic Fox or later
- **Android SDK**: API 24+ (Android 7.0)
- **Java**: JDK 17 (included with Android Studio)

### Build Instructions

1. **Clone the repository**
   ```bash
   git clone https://github.com/Giras91/Android-Studio.git
   cd Android-Studio
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the cloned directory

3. **Build the project**
   ```bash
   # Clean build (recommended)
   .\gradlew.bat clean

   # Build debug APK
   .\gradlew.bat assembleDebug

   # Install to connected device
   .\gradlew.bat installDebug
   ```

4. **Run on device/emulator**
   - Connect an Android device or start an emulator
   - Click "Run" in Android Studio or use:
   ```bash
   .\gradlew.bat installDebug
   ```

## 📱 Usage Guide

### Getting Started

1. **Add Sources**: Go to Sources tab → Tap the + button to add manga websites
2. **Browse Manga**: Tap on a source to browse available manga
3. **Add to Library**: Long-press manga to add them to your library
4. **Start Reading**: Tap any manga in your library to start reading

### Adding Custom Sources

Mari Reader supports custom scraping profiles for better compatibility:

```json
{
  "chapterList": {
    "selector": "a[href*='chapter']",
    "urlAttr": "href",
    "allowJs": false
  },
  "images": {
    "selector": "img.chapter-image",
    "allowJs": true
  }
}
```

### Download Management

- **Download Chapters**: Long-press chapters to download for offline reading
- **Export Downloads**: Configure export folder in Settings for automatic copying
- **Storage Access**: Uses Android's Storage Access Framework for external storage

### Background Updates

Configure automatic chapter checking in Settings:
- Update intervals: 30 minutes to 24 hours
- Background processing respects device battery and data settings
- JS-heavy sources are skipped in background (WebView limitation)

## 🏗️ Architecture

### Tech Stack
- **Language**: Kotlin
- **UI**: Traditional Android Views + ViewBinding
- **Database**: Room ORM (SQLite)
- **Networking**: Jsoup for web scraping, OkHttp for downloads
- **Background**: WorkManager for scheduled tasks
- **Image Loading**: Glide
- **Navigation**: Jetpack Navigation Component

### Project Structure
```
app/src/main/java/com/tinyreader/mari_reader/
├── data/           # Room entities, DAOs, and Repository
├── scraper/        # Web scraping logic and managers
├── ui/             # UI fragments and adapters
│   ├── library/    # Library management screens
│   ├── sources/    # Source management screens
│   ├── reader/     # Manga reading interface
│   ├── settings/   # App settings and preferences
│   └── downloads/  # Download management
├── viewmodel/      # Shared ViewModel
└── MainActivity.kt # Single-activity architecture
```

### Data Model
```
Source (sources) ← LibraryManga (library_manga) ← Chapter (chapters)
                                                 ← ReadingHistory
```

- **URLs as Primary Keys**: All entities use absolute URLs as unique identifiers
- **Foreign Key Relationships**: Maintains referential integrity across entities
- **Migration Support**: Automatic database migrations for schema updates

## 🔧 Configuration

### Build Configuration
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 16)
- **Build Tools**: 36.1.0
- **Kotlin**: 2.1.0
- **Gradle**: 8.13.1

### Dependencies
Key dependencies managed via version catalogs (`gradle/libs.versions.toml`):
- **Room**: 2.6.1 (Database)
- **WorkManager**: 2.9.0 (Background tasks)
- **Jsoup**: 1.17.2 (Web scraping)
- **Glide**: 4.16.0 (Image loading)
- **Navigation**: 2.9.6 (UI navigation)

## 🐛 Troubleshooting

### Common Issues

**Scraping Fails**
- Check internet connection
- Some sites may require JavaScript (use browser mode)
- Verify source URL format (must include http/https)

**Downloads Not Working**
- Ensure storage permissions granted
- Check available storage space
- Android 14+: Foreground service permission required

**Background Updates Not Running**
- Check WorkManager permissions
- Verify battery optimization settings
- JS-heavy sources are intentionally skipped in background

**Database Issues**
- Clear app data to reset database
- Check for migration errors in logs
- Ensure URLs are properly formatted

### Debug Logging
Enable verbose logging in Android Studio:
- Filter logs by tag: `ScraperManager`, `UpdateWorker`, `DownloadWorker`
- Check device logs for scraping errors and network issues

## 🤝 Contributing

We welcome contributions! Please follow these guidelines:

### Development Setup
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make your changes following the existing code style
4. Test thoroughly on multiple Android versions
5. Submit a pull request

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add documentation for complex logic
- Maintain consistent error handling patterns

### Testing
- Test on multiple Android versions (7.0+)
- Verify scraping works with various manga sites
- Test offline functionality and downloads
- Check background task reliability

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with modern Android development practices
- Uses open-source libraries for web scraping and image loading
- Inspired by the need for a lightweight, privacy-focused manga reader

## 📞 Support

For issues, questions, or feature requests:
- Open an issue on GitHub
- Check existing issues for similar problems
- Provide detailed reproduction steps and device information

---

**Note**: This app scrapes content from third-party websites. Please respect website terms of service and copyright laws. The developers are not responsible for how this app is used.</content>
<parameter name="filePath">d:\Android Studio\README.md