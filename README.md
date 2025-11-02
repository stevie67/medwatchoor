# Gymoor - Galaxy Watch Gym Tracker

A Wear OS app for tracking gym progress on Samsung Galaxy Watch Ultra.

## Features

- **Exercise List**: Display exercises with name, weight, and optional notes
- **Data Sync**: Loads exercise data from a remote JSON file
- **1-Minute Timer**: Quick access rest timer between sets
- **Weight Adjustment**: +/- buttons to adjust weights in 1 kg increments
- **Auto-Save**: Changes are automatically uploaded to the server after 3 seconds of inactivity

## JSON Data Format

The app fetches exercise data from: `https://www.radig.com/gymoor/stevie.json`

### Expected JSON Structure

```json
{
  "exercises": [
    {
      "id": 1,
      "name": "Leg Press",
      "weight": 70,
      "notes": "3 sets of 12 reps"
    },
    {
      "id": 2,
      "name": "Chest Press",
      "weight": 35,
      "notes": "Warmup with 25 kg first"
    },
    {
      "id": 3,
      "name": "Lat Pulldown",
      "weight": 55,
      "notes": null
    }
  ]
}
```

### Field Descriptions

- **id** (required, integer): Unique identifier for the exercise
- **name** (required, string): Display name of the exercise/machine
- **weight** (required, integer): Current weight in kilograms
- **notes** (optional, string): Additional notes or instructions

## Server Setup

### 1. Upload Server Files

Upload the files from the `server/` directory to your web server:
- `upload.php` → `https://www.radig.com/gymoor/upload.php`
- `.htaccess` → `https://www.radig.com/gymoor/.htaccess` (required for Apache to pass auth headers)
- Create or upload `stevie.json` → `https://www.radig.com/gymoor/stevie.json`

### 2. Configure Upload Token

**On the server** - Edit `upload.php` and set a secure token:
```php
define('UPLOAD_TOKEN', 'your-long-random-secret-token-here');
```

**In the app** - Edit `ExerciseApiService.kt` and set the same token:
```kotlin
private const val UPLOAD_TOKEN = "your-long-random-secret-token-here"
```

**IMPORTANT:** Both tokens must match exactly!

### 3. Set Permissions

Make sure the web server can write to the directory:
```bash
chmod 755 /path/to/gymoor/
chmod 644 /path/to/gymoor/stevie.json
```

## Building the App

### Prerequisites

- Android SDK 34
- Android NDK
- JDK 17
- Gradle 8.2+

### Build Instructions

The project uses signing credentials from the existing keystore at:
`/Users/stevie/test/logbuch/build/release-key.jks`

#### Build Debug APK
```bash
./gradlew assembleDebug
```

#### Build Release APK
```bash
./gradlew assembleRelease
```

The release build is automatically signed with the configured keystore.

### Install to Watch

1. Enable Developer Mode on your Galaxy Watch Ultra
2. Enable ADB debugging
3. Connect watch via WiFi or USB
4. Run: `adb install -r app/build/outputs/apk/release/app-release.apk`

## Project Structure

```
app/src/main/java/com/radig/gymoor/
├── MainActivity.kt              # Main entry point with navigation
├── data/
│   ├── Exercise.kt             # Data models
│   ├── ExerciseApiService.kt   # Retrofit API service
│   └── ExerciseRepository.kt   # Data repository
├── viewmodel/
│   └── ExerciseViewModel.kt    # UI state management
└── ui/
    ├── ExerciseListScreen.kt   # Main exercise list UI
    └── TimerScreen.kt          # Timer screen UI
```

## Future Enhancements

- **Weight Adjustment**: +/- buttons to modify exercise weights (structure already implemented in ViewModel)
- **Local Persistence**: Save modified weights locally
- **Sync Back**: Upload changes to server
- **Exercise History**: Track progress over time
- **Custom Timers**: Configurable rest periods

## App ID

`com.radig.gymoor`

## Permissions

- `INTERNET`: Download exercise data
- `WAKE_LOCK`: Keep screen on during timer
- `VIBRATE`: Vibrate when timer completes
