# Vibemax CloudStream Extension

A CloudStream extension for [vibemax.to](https://vibemax.to) - Watch movies & TV shows online.

## Features

- Movies and TV Series support
- Multiple server options
- Download support
- Search functionality
- Main page with categories

## Installation

### Add Repository to CloudStream

1. Open CloudStream app
2. Go to Settings > Extensions > Add Repository
3. Enter this URL:
   ```
   https://raw.githubusercontent.com/crafteraadarsh/vibemax/main/repo.json
   ```
4. Click "Add"
5. Find and install "VibemaxProvider" from the extensions list

## Building Locally

### Prerequisites
- JDK 17
- Android Studio (recommended)

### Build Commands

**Windows:**
```bash
.\gradlew.bat VibemaxProvider:make
```

**Linux/Mac:**
```bash
./gradlew VibemaxProvider:make
```

### Deploy to Device (with ADB)

**Windows:**
```bash
.\gradlew.bat VibemaxProvider:deployWithAdb
```

**Linux/Mac:**
```bash
./gradlew VibemaxProvider:deployWithAdb
```

## Granting All Files Access (Android 11+)

For local plugin testing:

### Using ADB
```bash
adb shell appops set --uid com.lagradost.cloudstream3 MANAGE_EXTERNAL_STORAGE allow
```

### Manually
1. Open Settings > Apps > CloudStream
2. Permissions > Files and media > Allow management of all files

## Project Structure

```
vibemax/
├── VibemaxProvider/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/vibemax/
│       ├── VibemaxPlugin.kt
│       └── VibemaxProvider.kt
├── build.gradle.kts
├── settings.gradle.kts
├── repo.json
└── PRD.md
```

## License

This project is released into the public domain.

## Disclaimer

This extension is for educational purposes only. The developers are not responsible for how you use this extension.
