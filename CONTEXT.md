# ScanCraft — System Context & Codebase Guide (CONTEXT.md)

This document serves as the primary technical context, architectural reference, and operational playbook for developers, AI assistants, and contributors working on **ScanCraft**.

---

## 1. Project Overview & Identity

* **Project Name:** ScanCraft
* **Package Identity:** `com.krinry.scancraft`
* **Target Platform:** Android 8.0 (API level 26) through Android 15+ (API level 35)
* **Core Philosophy:** 100% offline-first, private, ad-free, open-source document scanner with native C++ image processing, Jetpack Compose UI, on-device OCR, and professional PDF compilation.
* **Legal Policy:** Zero third-party branding, zero competitor names, zero proprietary binary assets. 100% clean-room algorithmic engineering.

---

## 2. Tech Stack & Engineering Environment

| Layer | Technologies / Libraries |
| :--- | :--- |
| **Language** | Kotlin 1.9.23, Native C++17 |
| **UI Framework** | Jetpack Compose (BOM 2024.04.01), Material 3 (`androidx.compose.material3`) |
| **Camera Engine** | CameraX 1.3.2 (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) |
| **Computer Vision (NDK)**| C++17 via CMake 3.22.1, Android NDK 25+, `jnigraphics`, `log` |
| **Local Database** | Room 2.6.1 (`room-runtime`, `room-ktx`, `room-compiler` via KSP) |
| **Asynchronous & State** | Kotlinx Coroutines 1.8.0 (`StateFlow`, `SharedFlow`, `viewModelScope`) |
| **Text Recognition (OCR)**| Google ML Kit Text Recognition 16.0.0 (On-device offline model) |
| **PDF Engine** | Apache PDFBox Android 2.0.27.0 with AES 128-bit encryption |
| **Image Loading** | Coil 2.6.0 (`coil-compose`) |
| **Build Tooling** | Android Gradle Plugin (AGP) 8.3.2, Gradle 8.4, JDK 17 |

---

## 3. Package & Module Map

```
com.krinry.scancraft/
├── ScanCraftApp.kt                 # Application class initializing Room & ThemeManager
│
├── camera/                           # CameraX Stream & Analysis
│   └── DocumentAnalyzer.kt           # Real-time frame luminance & contour stream
│
├── cv/                               # Computer Vision Bridge & Math
│   ├── DocumentQuadrilateral.kt      # 4-corner coordinate model, sorting, homography math
│   ├── NativeScannerEngine.kt        # JNI Bridge bindings to libscancraft.so
│   └── CollageGenerator.kt           # A4 canvas multi-page collage compositor
│
├── data/                             # Data Layer (Room & File System)
│   ├── local/
│   │   ├── AppDatabase.kt            # Room database definition (Document, Page, Folder)
│   │   └── Daos.kt                   # DocumentDao, PageDao, FolderDao
│   ├── model/
│   │   ├── Document.kt               # Document record entity
│   │   ├── Page.kt                   # Page record entity with crop coordinates
│   │   └── Folder.kt                 # Folder entity & FolderWithCount projection
│   └── repository/
│       └── ScannerRepository.kt      # Single source of truth abstracting DB & storage
│
├── ocr/                              # Machine Learning
│   └── TextRecognitionHelper.kt      # Google ML Kit offline text extraction
│
├── pdf/                              # Document Compilation
│   ├── PdfExportOptions.kt           # Page size, compression, watermark, encryption params
│   └── PdfGenerator.kt               # Streamed PDF compilation with PDFBox Android
│
└── ui/                               # Component-Driven Jetpack Compose UI
    ├── MainActivity.kt               # Root Activity with enableEdgeToEdge()
    ├── navigation/Screen.kt          # Typed navigation routes (Home, Camera, Crop, Filter, Detail, Viewer)
    ├── theme/
    │   ├── Theme.kt                  # Dynamic ScannerColors (AMOLED Dark & Paper Light)
    │   └── ThemeManager.kt           # SharedPreferences theme mode state holder
    │
    ├── home/                         # Home Dashboard & Folder System
    │   ├── HomeScreen.kt             # Lightweight Screen Coordinator (~330 lines)
    │   ├── HomeViewModel.kt          # Document list, sort, search, and batch state
    │   ├── components/               # Decoupled UI Components
    │   │   ├── HomeTopBar.kt         # Header with Avatar, Logo, Theme Switcher & Search
    │   │   ├── FolderSection.kt      # Folder Grid/List layout with "+ New Folder" action
    │   │   ├── FolderCards.kt        # ScannerFolderListItem & ScannerFolderGridCard
    │   │   ├── FolderGraphic.kt      # Compose Canvas Manila folder vector graphic
    │   │   ├── DocumentListItem.kt   # Document row with thumbnail & verified badge
    │   │   ├── DocumentGridCard.kt   # Document card with page counter badge
    │   │   ├── BatchSelectionBar.kt  # Multi-select action bottom bar (Move, PDF, Delete)
    │   │   ├── HomeBottomBar.kt      # Bottom tabs with emerald floating shutter FAB
    │   │   └── HomeToolsTab.kt       # Scanner Tools grid & Theme toggle card
    │   └── dialogs/                  # Focused Home Dialogs
    │       ├── CreateFolderDialog.kt # Folder creation dialog
    │       ├── RenameFolderDialog.kt # Folder rename dialog
    │       ├── RenameDocumentDialog.kt# Document title edit dialog
    │       └── MoveToFolderDialog.kt # Folder picker dialog
    │
    ├── detail/                       # Multi-Page Document View & Studio
    │   ├── DocumentDetailScreen.kt   # Lightweight Screen Coordinator (~390 lines)
    │   ├── DocumentDetailViewModel.kt# Page list, collage generation, PDF export
    │   ├── components/               # Decoupled Detail Components
    │   │   ├── DetailTopBar.kt       # Document title, back arrow & PDF export action
    │   │   ├── DetailBottomBar.kt    # 5 standard actions: Add, Collage, Share, Edit PDF, More
    │   │   └── PageGridCard.kt       # 2-column page thumbnail card with "01", "02" badges
    │   └── dialogs/
    │       └── CollageStudioDialog.kt# 2-in-1, 4-in-1, 6-in-1 A4 layout selector
    │
    ├── camera/                       # Live Viewfinder Screen & Laser Polygon Overlay
    │   ├── CameraScreen.kt
    │   └── ViewfinderOverlay.kt
    ├── crop/                         # 8-Point Crop Editor & Floating Magnifier Loupe
    │   ├── CropScreen.kt
    │   ├── DraggablePolygonOverlay.kt
    │   └── MagnifierLoupe.kt
    ├── filter/                       # Live Filter Previews & Tuning Sliders
    │   ├── FilterScreen.kt
    │   └── FilterViewModel.kt
    ├── viewer/                       # Fullscreen Pinch-to-Zoom Page & OCR Viewer
    ├── pdf/PdfExportDialog.kt        # PDF Export Settings Dialog
    └── signature/SignatureDialog.kt  # Transparent Ink E-Signature Dialog
```

---

## 4. Key Data Models & State Contracts

### 4.1 Document (`com.krinry.scancraft.data.model.Document`)
```kotlin
@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdTime: Long = System.currentTimeMillis(),
    val modifiedTime: Long = System.currentTimeMillis(),
    val folderId: Long = -1L,
    val pageCount: Int = 0,
    val tags: String = ""
)
```

### 4.2 Page (`com.krinry.scancraft.data.model.Page`)
```kotlin
@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(entity = Document::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)]
)
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val originalImagePath: String,
    val processedImagePath: String,
    val cropBorderJson: String,
    val enhanceModeId: Int = 1,
    val ocrText: String? = null
)
```

### 4.3 Folder & FolderWithCount (`com.krinry.scancraft.data.model.Folder`)
```kotlin
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdTime: Long = System.currentTimeMillis()
)

data class FolderWithCount(
    @Embedded val folder: Folder,
    val docCount: Int
)
```

---

## 5. Native Computer Vision Architecture (`cpp/`)

* **`edge_detector.cpp`:** Downsampled Sobel gradient magnitude calculator. Extracts convex hulls and finds 4-point quadrilateral document boundaries in < 15ms.
* **`perspective_warp.cpp`:** Calculates a 3x3 Homography Matrix mapping the arbitrary 4-point quad to a rectified rectangular bounding box with bilinear pixel interpolation.
* **`magic_color.cpp`:**
  * **Magic Color:** Local Contrast (CLAHE approximation) + Paper Illumination Whitening + 35% Saturation Boost in HSV color space.
  * **Bradley-Roth & Sauvola B&W:** Integral image-based adaptive thresholding for text extraction on uneven lighting.
  * **No-Shadow / Lighten:** Coarse-grid background luminance estimation and bilinear normalization.
  * **Deblur / Sharp Text:** 3x3 Laplacian edge difference with unsharp masking.

---

## 6. Theming & System Inset Strategy

* **AMOLED Dark Mode:** Pure `#000000` background, `#141414` graphite cards, `#00BA66` emerald primary, `#FFFFFF` crisp text.
* **Paper Light Mode:** Non-glare `#F8F9FA` paper background, `#FFFFFF` cards, `#DCF5E8` mint container, `#17202B` deep slate text.
* **Zero Startup Flicker:** `themes.xml` applies a neutral dark window background (`#121212`) before Compose renders.
* **Edge-to-Edge:** `enableEdgeToEdge()` in `MainActivity` with dynamic `WindowCompat.getInsetsController` status/nav bar icon adjustment eliminates gray/white navbar button artifacts.
* **Persistence:** `ThemeManager` synchronizes user selection (`LIGHT`, `DARK`, `SYSTEM`) via private `SharedPreferences`.

---

## 7. Memory & Hardware Safety Rules

* **Direct Buffer Mutating:** In-place pixel transformations using Android NDK `jnigraphics` (`AndroidBitmap_lockPixels`).
* **Preview Downsampling:** Viewport matching via `BitmapFactory.Options.inSampleSize` to cap preview RAM usage at < 8 MB per page.
* **Sequential PDF Streaming:** Multi-page PDF generation streams one page at a time with proactive `bitmap.recycle()` calls to maintain a bounded O(1) memory footprint.
* **Thread Pools:** CPU-bound processing operates on `Dispatchers.Default`; file/DB transactions on `Dispatchers.IO`; UI remains smooth at 60fps on `Dispatchers.Main`.
