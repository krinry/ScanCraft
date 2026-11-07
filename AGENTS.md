# ScanCraft — Agent & Engineering Guidelines (AGENTS.md)

Welcome to **ScanCraft**. This document defines the mandatory engineering, legal, architectural, and quality standards for all developers, AI agents, and contributors working on this codebase.

---

## 1. Absolute Legal & Branding Rules (Zero Tolerance)

### 1.1 Zero Proprietary Assets
* **Never** commit, extract, copy, or embed proprietary binary assets, copyrighted PNGs/JPGs/SVGs, proprietary fonts, or media from closed-source or commercial applications.
* All user interface graphics must be generated dynamically using Jetpack Compose `Canvas`, vector graphics (`ImageVector`), or standard Material 3 icons from `androidx.compose.material.icons.*`.
* All algorithms must be independent, clean-room implementations using open standards (OpenCV, Google ML Kit, Android Jetpack).

### 1.2 Zero Trademarked / Proprietary Branding
* **Never** use third-party trademarked names or competitor brand identities in:
  - App titles, headers, logos, or user-facing strings
  - Package names, class names, function names, or variable identifiers
  - Theme constants (e.g., use `ScannerGreen`, `ScannerFolderYellow`, `ScanCraftDarkTheme`, etc.)
  - Documentation, commit messages, or metadata
* The official project identity is exclusively **ScanCraft**.

---

## 2. Engineering Excellence (No AI Slop)

### 2.1 Professional Modular Architecture
* Adhere strictly to **Modern Android Architecture (MVVM)**:
  - **UI Layer (`com.krinry.scancraft.ui.*`)**: Jetpack Compose screens, stateless composables, unidirectional data flow (UDF).
  - **ViewModel Layer (`*ViewModel.kt`)**: Exposes state via `StateFlow`, handles UI events, manages coroutine scopes (`viewModelScope`).
  - **Repository Layer (`com.krinry.scancraft.data.repository.*`)**: Single source of truth abstracting local Room database and file storage.
  - **Data Layer (`com.krinry.scancraft.data.local.*`, `com.krinry.scancraft.data.model.*`)**: Room Entities, DAOs, migrations, and strongly-typed data structures.
  - **Engine / CV Layer (`com.krinry.scancraft.cv.*`, native C++)**: High-performance computer vision algorithms, perspective warp, edge detection, and document color enhancements.
* **No AI Slop**: Every line of code must serve a concrete purpose. No placeholder `TODO()` comments in production paths, no duplicated files, and no unmaintained dead code.

### 2.2 Global Theming & Reusable Components
* Centralize colors, shapes, and typography in `Theme.kt`.
* Build reusable, decoupled UI components (cards, dialogs, bottom sheets, sliders) rather than copy-pasting code blocks across screens.
* Support high-contrast AMOLED Dark Theme (`#000000`) and modern Edge-to-Edge window insets.

---

## 3. Privacy, Security & Stability

### 3.1 Strict Local Privacy
* ScanCraft is an **offline-first**, privacy-focused tool.
* No analytics SDKs, advertising libraries, tracking beacons, or unsolicited external network calls.
* Files and temporary scan buffers must remain in the app-private sandbox (`context.filesDir`, `context.cacheDir`).

### 3.2 Memory Safety & Hardware Optimization
* High-resolution camera bitmaps must be processed safely to prevent `OutOfMemoryError` (OOM):
  - Downsample large bitmaps during preview rendering.
  - Recycle native buffers immediately when done.
  - Execute computationally intensive operations (perspective warp, filters, collage generation, PDF compilation) on background coroutine dispatchers (`Dispatchers.IO` / `Dispatchers.Default`).

---

## 4. Git Hygiene & Release Standards

* Branching strategy:
  - `main`: Production-ready, stable releases.
  - `dev`: Primary integration branch for verified features.
  - `feature/*`: Short-lived branches for isolated development.
* Clear, conventional commit messages (`feat:`, `fix:`, `refactor:`, `docs:`, `chore:`).
* Never track `.apk`, `.so`, `.aab`, `.class`, or build caches in Git. Maintain a strict `.gitignore`.
