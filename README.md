# xPDF Viewer

[![Android Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9335)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![ML Kit](https://img.shields.io/badge/AI%2FML-Google%20ML%20Kit-FF6F00?logo=google&logoColor=white)](https://developers.google.com/ml-kit)
[![Offline Privacy](https://img.shields.io/badge/Privacy-100%25%20On--Device%20%26%20Offline-success)](README.md)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**xPDF Viewer** is a modern, high-performance, edge-to-edge Android PDF reader and offline document utility suite. Built entirely with Jetpack Compose, Material 3, PdfBox-Android, CameraX, and Google ML Kit, it provides viewing, editing, signing, scanning, merging, and compression tools—without internet access, third-party analytics, or cloud uploads.

---

## Key Highlights

- **100% Offline & Private**: All rendering, text extraction, OCR, signature creation, and document manipulations run locally on-device. Zero telemetry, zero cloud dependencies.
- **Edge-to-Edge Material 3 Design**: Fully aligned with Android 15 edge-to-edge layout standards, dynamic theming (Dark & Light modes), and fluid motion transitions.
- **Modern Storage Standard**: Built on Android's Storage Access Framework (SAF) and scoped storage. Requires no legacy external storage permissions and works with file managers and cloud providers.
- **Comprehensive Utility Suite**: Includes on-device OCR, multi-page batch merging, multi-level image re-sampling compression, a CameraX perspective-warping document scanner, and an encrypted E-Signature studio.

---

## Feature Tour

### 1. High-Fidelity PDF Viewer
- **Native Hardware Rendering**: Asynchronous background page rendering powered by native Android `PdfRenderer` and Kotlin Coroutines.
- **Pinch & Zoom Gestures**: Fluid multi-touch zooming and panning with per-page zoom memory.
- **Edge-to-Edge Display**: Built-in support for system display cutouts, status bars, and navigation bar insets.
- **Recents & Reading Progress**: Tracks recently opened files, last-read page positions, file sizes, and exact timestamps (`Oct 29, 2026 • 7:15 PM`).
- **Interactive Gestures**: Swipe-to-dismiss items from recents with an animated trash reveal, or use bulk "Clear All".
- **Print & Share**: Direct integration with Android's `PrintManager` and native document share sheets.

### 2. Context-Sensitive Long-Press Actions
Long-pressing any page inside the viewer brings up a contextual action sheet:
- **Select Text**: Activates on-screen text selection and clipboard copying for the active page.
- **Add Page**: Inserts pages from another PDF directly after the active page via SAF.
- **Extract Page**: Isolates the active page into a single-page PDF, stripping all other pages while preserving dimensions and embedded fonts.
- **Delete Page**: Deletes the active page (safeguarded against single-page documents).
- **Add E-Signature**: Opens the full-screen E-Signature studio pre-loaded with the current document navigated to the selected page.

### 3. E-Signature & Contract Studio
- **3 Signature Modes**:
  - **Draw**: Freehand stylus and touch canvas with quadratic Bézier stroke smoothing, ink colors (Black, Navy, Blue), stroke thickness controls, and undo/clear actions.
  - **Type**: Calligraphic cursive engine converting typed names into flowing handwritten signatures.
  - **Image / File Import**: Import signatures from device storage (PNG, JPG, WEBP) with automatic luminance background removal, anti-aliased edges, and ink-bounds auto-cropping.
- **Saved Signatures Vault**: Secure local caching for recurring 1-tap signature placement.
- **Interactive Drag & Resize Overlay**: Drag signatures anywhere on the document with proportional corner scaling handles and instant deletion chips.
- **128-Bit AES Encryption**: Password-protect signed documents with user and owner passwords via PdfBox `StandardProtectionPolicy`.
- **Safe Discard Architecture**: Back gestures trigger confirmation dialogs to prevent accidental loss of edits.

### 4. Custom Camera Document Scanner
- **CameraX 3:4 Viewfinder**: Native 3:4 aspect ratio matching physical camera sensors, preventing vertical stretching.
- **ML Kit Edge Detection & Auto-Crop**: Real-time bounding box tracking with animated reticles; automatically crops the document on shutter release.
- **Interactive 4-Corner Perspective Warp**: Four independent draggable corner handles allow precision adjustment on skewed, rotated, or angled documents. Rectifies quadrilaterals into flat, 90-degree rectangular pages via homography transformations (`Matrix.setPolyToPoly`).
- **Image Enhancement Suite**: Instant Enhance, Grayscale, Black & White, Color filters, rotation (90° clockwise), and paper background whitening/shadow cleanup.
- **Multi-Page Compilation**: Append multiple camera captures or gallery photos into a unified PDF with custom naming prompts.

### 5. On-Device OCR Scanner
- **Offline Machine Learning**: Powered by Google ML Kit Text Recognition (`16.0.1`), executing neural OCR pipelines entirely offline.
- **Camera & Gallery Inputs**: Live capture or photo selection without requiring dangerous runtime permissions.
- **Live Metrics & Editing**: Instant word and character counts, thumbnail previews, and an editable `OutlinedTextField` with one-tap copy and share capabilities.

### 6. PDF Merge & Utilities
- **Batch Document Merge**: Select multiple PDFs via SAF, reorder pages with Move Up/Down controls, view combined file sizes and page counts, and export to any directory without intermediate temporary files.
- **Multi-Tier Compression**:
  - **Extreme Compression**: ~65–85% reduction (JPEG quality 0.50, downscaled to ~1100px max dimension).
  - **Recommended Compression**: ~45–65% reduction (JPEG quality 0.72, downscaled to ~1600px max dimension).
  - **Less Compression**: ~25–40% reduction (JPEG quality 0.86, downscaled to ~2200px max dimension).
  - Recursively traverses `PDPage` resources and form XObjects to re-encode high-resolution bitmaps while keeping text and vectors intact.
- **Safe Save Modes**: Choose between "Overwrite" or "Save As Copy" before writing modifications to storage.

---

## Architecture & Tech Stack

```
com.xpdfviewer
├── data/            # Local repositories, SharedPreferences, Recents cache
├── ocr/             # Google ML Kit text recognition engine & abstractions
├── pdf/             # PdfBox-Android & Android PdfRenderer engines, text extractors, compressors
├── scanner/         # CameraX integration, perspective warp matrix processing, PDF generation
├── signature/       # Digital ink capture, cursive typography, luminance filter, encryption
├── ui/
│   ├── components/  # Navigation bars, speed dials, top bars, page items, swipeable cards
│   ├── dialogs/     # Material 3 discard warnings, filename prompts, export sheets, save pickers
│   ├── screens/     # Compose screens (Home, Viewer, Scanner, E-Sign, Tools, Merge, Compress, OCR)
│   └── theme/       # Dynamic color schemes, typography, and edge-to-edge system insets
└── viewmodel/       # StateFlow ViewModels managing asynchronous coroutine workflows
```

### Core Technologies

| Technology | Purpose |
|---|---|
| **Kotlin 2.0** | Core programming language with coroutines and Flow |
| **Jetpack Compose & Material 3** | Declarative edge-to-edge UI and fluid animations |
| **Android PdfRenderer** | High-performance native page rendering |
| **PdfBox-Android** (`2.0.27.0`) | In-place PDF mutation, page extraction, insertion, merging, encryption |
| **Google ML Kit** (`16.0.1`) | On-device text recognition and document detection |
| **CameraX** (`1.4.1`) | Camera preview, analysis, and photo capture |
| **Storage Access Framework (SAF)** | Secure file picker and content URI streaming |
| **AndroidX Core & Lifecycle** | ViewModel lifecycle integration and Coroutine scopes |

---

## Getting Started

### Prerequisites

- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: Version 17
- **Android SDK**:
  - `compileSdk`: 35 (Android 15)
  - `minSdk`: 26 (Android 8.0 Oreo)
  - `targetSdk`: 35

### Building from Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/iamlexxx/xPDFViewer.git
   cd xPDFViewer
   ```

2. **Open in Android Studio** or build using the Gradle wrapper:

   - **Debug APK:**
     ```bash
     ./gradlew assembleDebug
     ```

   - **Release APK:**
     ```bash
     ./gradlew assembleRelease
     ```

   Generated APKs will be located at:
   `app/build/outputs/apk/{debug|release}/`

---

## Permissions & Privacy

xPDF Viewer is built with a zero-tracking philosophy:

- **`android.permission.CAMERA`**: Used strictly within `CustomDocumentScannerScreen` to stream viewfinder frames and capture document images. Declared with `android.hardware.camera required="false"`.
- **No External Storage Permissions**: Does not use `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`. Access to user files is granted on demand via Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`).
- **No Internet Access**: The application does not declare the `android.permission.INTERNET` permission. Documents, signatures, and camera captures never leave the physical device.

---

## Contributing

Contributions are welcome! Please feel free to open issues or submit pull requests:

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## Developer & Maintainer

Developed and maintained by **Lex**:
- **Repository**: [https://github.com/iamlexxx/xPDFViewer](https://github.com/iamlexxx/xPDFViewer)

---

## License

Distributed under the Apache License, Version 2.0. See `LICENSE` for more information.

