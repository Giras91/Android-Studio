# Mari Reader - App Icon Installation Guide

## Your Icon
The Mari Reader icon (`MARI READER.png`) is located at: `D:\Android Studio\MARI READER.png`

## Method 1: Using Python Script (Recommended)

### Prerequisites
1. Install Python from https://www.python.org/downloads/
2. Install Pillow library:
   ```
   pip install Pillow
   ```

### Steps
1. Open Command Prompt or PowerShell
2. Navigate to the project directory:
   ```
   cd "D:\Android Studio"
   ```
3. Run the conversion script:
   ```
   python convert_icon.py
   ```
4. Clean and rebuild the app:
   ```
   .\gradlew.bat clean assembleDebug
   ```

## Method 2: Using Android Studio (Easy - No Python Required)

### Steps
1. Open Android Studio with your Mari Reader project
2. In the Project view, navigate to: `app/src/main/res`
3. Right-click on `res` folder
4. Select: **New → Image Asset**
5. In the Asset Studio dialog:
   - **Icon Type**: Launcher Icons (Adaptive and Legacy)
   - **Name**: ic_launcher
   - **Asset Type**: Image
   - **Path**: Browse and select `D:\Android Studio\MARI READER.png`
   - **Trim**: No (already has proper padding)
   - **Resize**: Keep default (100%)
6. Click **Next**, then **Finish**
7. Clean and rebuild: **Build → Clean Project**, then **Build → Rebuild Project**

## Method 3: Manual Copy (Advanced)

You need to create icons in these sizes:
- mipmap-mdpi: 48x48 px
- mipmap-hdpi: 72x72 px
- mipmap-xhdpi: 96x96 px
- mipmap-xxhdpi: 144x144 px
- mipmap-xxxhdpi: 192x192 px

### Steps
1. Use an image editor (Photoshop, GIMP, Paint.NET) to resize your icon
2. Save as PNG with these names:
   - `ic_launcher.png`
   - `ic_launcher_round.png` (same image)
3. Place in corresponding folders:
   - `app/src/main/res/mipmap-mdpi/`
   - `app/src/main/res/mipmap-hdpi/`
   - `app/src/main/res/mipmap-xhdpi/`
   - `app/src/main/res/mipmap-xxhdpi/`
   - `app/src/main/res/mipmap-xxxhdpi/`

## Verification

After installing the icon, verify in `AndroidManifest.xml`:
```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

## Testing

1. Build the app: `.\gradlew.bat assembleDebug`
2. Install on device: `.\gradlew.bat installDebug`
3. Check the app icon on your device's home screen and app drawer

## Troubleshooting

**Icon not showing after build:**
- Clean build: `.\gradlew.bat clean`
- Uninstall old app from device
- Rebuild and reinstall

**Old icon still appears:**
- Clear launcher cache on device
- Restart the device
- Some launchers cache icons aggressively

**Build errors:**
- Make sure PNG files are valid (not corrupted)
- Check file names are exactly `ic_launcher.png` and `ic_launcher_round.png`
- Verify files exist in all mipmap-* folders

## Notes

- The current Mari Reader icon has a nice red background with a manga-reading character - perfect for a manga reader app!
- The icon is already optimized with rounded corners and good contrast
- For best results on modern Android (8.0+), consider creating an adaptive icon with separate foreground and background layers

