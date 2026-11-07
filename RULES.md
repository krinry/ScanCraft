# ScanCraft — Engineering & Compliance Rules (RULES.md)

This document establishes the mandatory architectural, legal, engineering, and quality standards for all developers, AI agents, and contributors working on the **ScanCraft** codebase.

---

## 1. Absolute Legal & Branding Rules (Zero Tolerance)

### Rule 1.1: 100% Original ScanCraft Branding
* The official and exclusive project identity is **ScanCraft**.
* **Never** use competitor brand names, proprietary trademarks, or third-party scanner identities in:
  * Application titles, headers, logos, dialogs, or user-facing strings.
  * Package names, class names, function names, or variable identifiers.
  * Theme constants (use `ScannerGreen`, `ScannerFolderYellow`, `ScanCraftDarkTheme`, etc.).
  * Git branch names, commit messages, PR titles, or documentation.

### Rule 1.2: Zero Proprietary Binary Assets
* **Never** extract, commit, copy, or embed proprietary binary assets, copyrighted PNGs/JPGs/SVGs, closed-source fonts, or media from commercial apps.
* All visual interface elements (folders, loupes, laser polygons, badges) must be generated programmatically using Jetpack Compose `Canvas` or standard Google Material 3 vector drawables (`androidx.compose.material.icons.*`).

### Rule 1.3: Clean-Room Algorithmic Standards
* All algorithms must be independent, clean-room implementations utilizing standard open computer vision mathematical formulations (Sobel gradients, Homography perspective warp, Bradley-Roth binarization, CLAHE contrast enhancement).

---

## 2. Engineering Architecture & Code Quality (No AI Slop)

### Rule 2.1: Component-Driven Modular UI Architecture
* **No Monolithic Files:** Screens must not exceed 450 lines. Large screens must be decomposed into focused, reusable components housed in dedicated subpackages (`components/` and `dialogs/`).
* **Unidirectional Data Flow (UDF):** State flows down from ViewModels via `StateFlow`; events flow up via callbacks.
* **Stateless Composables:** UI components must be stateless wherever practical, hoisting state to the ViewModel or screen coordinator.
* **No AI Slop:** Every line of code must serve a concrete purpose. No placeholder `TODO()` comments in production paths, no duplicate code blocks, and no unmaintained dead code.

### Rule 2.2: Global Theming & Dynamic Tokens
* Centralize color tokens, typography, and shapes in `Theme.kt` and `ThemeManager.kt`.
* Always use `ScannerTheme.colors` rather than hardcoding static color values in composables to ensure seamless compatibility with both **AMOLED Dark Mode** and **Paper Light Mode**.
* Respect modern Android Edge-to-Edge window insets and dynamically synchronize status and navigation bar icons.

---

## 3. Privacy & Security Assurance

### Rule 3.1: Strict Offline-First Operation
* ScanCraft is an **offline-first, zero-telemetry** tool.
* Do not include the `android.permission.INTERNET` permission in `AndroidManifest.xml`.
* No analytics SDKs, advertising libraries, tracking beacons, or crash reporters that transmit data outside the device.
* All scans, thumbnails, and cache files must remain strictly within the app's internal sandbox (`context.filesDir`, `context.cacheDir`).

---

## 4. Hardware Optimization & Memory Safety

### Rule 4.1: Bounded Memory Footprint
* High-resolution camera bitmaps must be downsampled using `inSampleSize` before rendering on UI preview screens.
* Proactively recycle native buffers and intermediate bitmaps when warping or filtering is complete.
* Always execute CPU-heavy image processing on `Dispatchers.Default` and I/O file operations on `Dispatchers.IO`.

---

## 5. Git Hygiene & Branch Management

### Rule 5.1: Clean Branching & Conventional Commits
* **Branch Strategy:**
  * `main`: Production-ready, verified releases.
  * `dev`: Primary integration branch.
  * `feature/*`: Short-lived feature branches named after functional features (e.g., `feature/modular-components-and-theming`). **Never** include competitor names in branch names.
* **Conventional Commits:** Write clear messages prefixed with `feat:`, `fix:`, `refactor:`, `docs:`, or `chore:`.
* **Zero Build Artifacts in Git:** Maintain strict `.gitignore` rules prohibiting `.apk`, `.so`, `.aab`, `.dex`, `.class`, and `.externalNativeBuild` files from being committed.
