# VibePop — custom lifelike popups for Bluetooth earbuds

[![Version](https://img.shields.io/badge/Version-1.1.2-blue.svg)](https://github.com/daloyanf/vibepop/releases/tag/v1.1.2)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org)
[![TargetSDK](https://img.shields.io/badge/TargetSDK-34-orange.svg)](https://developer.android.com/about/versions/14)

VibePop is a lightweight, elegant and highly customisable Android app that shows an AirPods-style popup when your Bluetooth earbuds connect (Material dark theme). It supports a bottom sheet that slides up like iOS AirPods, media rendering (MP4/WebM video, frame-by-frame GIF, still images, Lottie vector animations), per-device animations, a real battery indicator in the top-right corner with a low-battery warning, waking the screen and showing the popup over the lock screen, plus elastic drag-to-dismiss and smart debouncing.

---

## 📥 Download

- **Lanzou cloud (Chinese host)**: [download the latest APK](https://wwbag.lanzouu.com/b0umfyvad) (password: `g3t5`)
- **GitHub Releases**: [Releases page](https://github.com/daloyanf/vibepop/releases)

---

## 🌟 Highlights

### 1. 🎨 Four-panel architecture
- **Dashboard**: one-tap animation preview, foreground service status, whitelist overview and live Bluetooth diagnostics.
- **Themes & Media**:
  - built-in classic AirPods vector animation and the *Great Victory* video theme;
  - a different animation for each paired earbud;
  - import MP4/WebM video, GIF (frame-by-frame), WebP/PNG/JPG images and Lottie JSON from the system gallery;
  - `VideoCropDetector` detects and crops black bars on import, and unsupported containers (MKV/AVI/MOV) are rejected with a clear message.
- **Devices**: paired earbud cards, Material pill switches, connection badges; whitelist-driven (nothing responds by default), per-device rename and one-tap "disable all".
- **Settings**: permission panel, Xiaomi/HyperOS lock-screen and background popup (AppOps) detection, forced speaker output, dismiss delay slider.

### 2. 🎧 Per-device customisation
- Each paired earbud (keyed by MAC address) can have its own theme, its own video/image and its own display name.
- A global default applies to every earbud you have not customised.

### 3. 🔋 Real battery indicator
- Reads the battery level reported by the earbuds hardware and the system Bluetooth broadcasts, shown in a translucent pill in the top-right corner.
- Colour graded by level (🟢 above 40% / 🟡 21-40% / 🔴 20% or less), with a lightning icon when the device reports charging.

### 4. ⚡ Wake-up and lock-screen support
- **WakeLock**: the screen turns on when the earbuds connect while the phone is asleep.
- **PopupActivity**: `setShowWhenLocked` and `setTurnScreenOn` let the transparent popup appear directly over the lock screen.

### 5. 🎬 Full-bleed video with automatic black-bar detection
- **`FillVideoView`**: a `TextureView` based player that computes its matrix during measurement, so the video fills the card 100% (center-crop) with no letterboxing.
- **`VideoCropDetector`**: samples the first frame on import, scans the black borders and produces a normalised crop rectangle.

### 6. 🖼️ Media import and rendering
- Uses the native full-screen media picker, avoiding the half-height PhotoPicker drawer.
- Detects MIME type and extension, keeps the real format; GIF is decoded frame by frame (API 28+ `ImageDecoder`, with a `Movie` fallback on API 26-27); images are sampled off the main thread and rotated according to EXIF, avoiding jank and OOM on large files.

### 7. 👆 Drag to dismiss
- The card follows your finger; released below the threshold it springs back (`OvershootInterpolator`), above the velocity threshold it slides away.

### 8. 🛡️ Do-not-disturb and hardening
- **Decoupled broadcasts**: periodic battery updates only refresh the badge, they never re-trigger the popup.
- **Hardening**: receivers are declared `android:exported="false"`, so third-party apps cannot forge a connection broadcast.
- **Throttling**: a 3-second window absorbs Bluetooth reconnection bursts.

---

## 📊 Media support matrix

| Type | Formats | Engine | Notes |
| :--- | :--- | :--- | :--- |
| **Video** | `.mp4`, `.webm` | `FillVideoView` (TextureView + MediaPlayer) | Center-crop full bleed, forced speaker output, auto-dismiss after playback |
| **Animated** | `.gif` | `ImageDecoder` / `GifMovieDrawable` | API 28+ system frame decoding, API 26-27 canvas fallback |
| **Still image** | `.png`, `.jpg`, `.jpeg`, `.webp`, `.bmp`, `.heic` | `BitmapDrawable` | Background sampling (capped at 1600 px), EXIF rotation |
| **Vector** | `.json` | `LottieAnimationView` | Lossless scaling, infinite loop |

---

## 📁 Architecture

```
com.vibepop
├── VibePopApp.kt                      # Application setup and notification channel
├── data/
│   ├── model/
│   │   ├── DeviceBatteryState.kt      # Earbud and real battery state
│   │   └── PopupConfig.kt             # Popup preferences and dismiss policy
│   └── repository/
│       └── PreferencesRepository.kt   # SharedPreferences persistence, per-device config
├── overlay/
│   ├── PopupWindowManager.kt          # WindowManager overlay lifecycle (singleton)
│   ├── FillVideoView.kt               # Center-crop full-bleed video player
│   ├── SlideDismissTouchListener.kt   # Drag tracking and spring back
│   ├── PopupAutoDismissManager.kt     # Countdown dismissal
│   ├── PopupInteractionController.kt  # Gesture and auto-dismiss controller (overlay and lock screen)
│   ├── PopupMetrics.kt                # Popup card sizing (40% of screen height)
│   ├── GifMovieDrawable.kt            # GIF frame rendering (API 26-27 Movie fallback)
│   └── PopupViewBinder.kt             # View binding, decoding, audio routing, battery badge
├── receiver/
│   ├── HeadsetBroadcastReceiver.kt    # Bluetooth connection and battery listener
│   ├── PopupTriggerLimiter.kt         # Throttling
│   └── BootReceiver.kt                # Start on boot
├── service/
│   └── HeadsetMonitorService.kt       # Foreground service (connectedDevice type)
├── ui/
│   ├── MainActivity.kt                # Four-tab UI
│   ├── MainViewModel.kt               # MVVM state and per-device media
│   └── PopupActivity.kt               # Transparent activity for lock screen and screen-off
└── util/
    ├── PermissionHelper.kt            # Overlay / Bluetooth / notification / OEM AppOps helpers
    └── VideoCropDetector.kt           # First-frame black-bar detection and crop ratio

app/src/test/java/com/vibepop
├── data/model/PopupConfigTest.kt      # Dismiss mode and delay calculation
├── receiver/PopupTriggerLimiterTest.kt# Throttling window
└── ui/MediaTypeDetectionTest.kt       # MIME/extension detection and rejected containers
```

---

## 🚀 Build and run

### 1. Requirements
- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK** 17
- **Gradle** 8.2+
- **Minimum Android**: 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)

### 2. Clone
```bash
git clone https://github.com/daloyanf/vibepop.git
cd vibepop
```

### 3. Signing (optional)
To build a signed release, copy `keystore.properties.example` to `keystore.properties` and fill in your signing details:
```bash
cp keystore.properties.example keystore.properties
```

### 4. Build and test
```bash
# Unit tests
./gradlew test

# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

---

## 📄 License

Released under the [MIT License](LICENSE).
