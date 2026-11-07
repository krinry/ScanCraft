# ScanCraft — Feature & Milestone Tracker (TRACKER.md)

**Current Version:** `v1.2.0-modular`  
**Active Development Branch:** `feature/modular-components-and-theming`  
**Architecture Base:** Android MVVM + Jetpack Compose + Room + C++ NDK (CMake)

---

## 1. Feature Status Matrix

| Module | Feature / Capability | Status | Verified |
| :--- | :--- | :---: | :---: |
| **Camera & Capture** | Real-time 60fps Sobel contour detection overlay | ✅ Completed | Yes |
| | Low-pass EMA temporal tracking & anti-jitter smoothing | ✅ Completed | Yes |
| | Document Stability Detector & Auto-Capture Countdown | ✅ Completed | Yes |
| | Emerald green laser polygon with L-brackets | ✅ Completed | Yes |
| | Single Capture Mode (Auto-stabilized shutter) | ✅ Completed | Yes |
| | Batch Capture Mode (Rapid multi-page continuous scanning) | ✅ Completed | Yes |
| | ID Card Dual-Sided Capture (Front + Back on A4) | ✅ Completed | Yes |
| | Book Curvature Split Mode | ✅ Completed | Yes |
| | QR & Barcode Scanner Integration | ✅ Completed | Yes |
| | Viewfinder Controls (Auto/Manual Shutter, Flash, Grid) | ✅ Completed | Yes |
| **Crop & Geometry** | 8-Point Draggable Polygon (4 corners + 4 midpoints) | ✅ Completed | Yes |
| | Magnetic Snap Crop (Image boundary & 90° orthogonal snap) | ✅ Completed | Yes |
| | Tactile Haptic Snap Feedback & Cyan Alignment Guides | ✅ Completed | Yes |
| | Floating Magnifier Loupe (2.2x zoom with snap reticle) | ✅ Completed | Yes |
| | Auto-crop quadrilateral calculation | ✅ Completed | Yes |
| | 3x3 Homography Bilinear Perspective Warp (C++) | ✅ Completed | Yes |
| **Image Enhancement**| Magic Color (CLAHE + Whitening + Saturation Boost) | ✅ Completed | Yes |
| | B&W 1 & B&W 2 (Bradley-Roth & Sauvola adaptive binarization) | ✅ Completed | Yes |
| | Grayscale (Contrast-stretched 8-bit monochromatic) | ✅ Completed | Yes |
| | Lighten / No-Shadow background illumination correction | ✅ Completed | Yes |
| | Blueprint Inverted (High contrast white-on-dark document) | ✅ Completed | Yes |
| | Eco Text (Ultra-clean ink-saver binarization) | ✅ Completed | Yes |
| | Stamp & Seal (Vivid red/blue ink booster) | ✅ Completed | Yes |
| | Warm Paper (Soft eye-care parchment tone) | ✅ Completed | Yes |
| | Manual Tuning Sliders (Brightness, Contrast, Sharpness) | ✅ Completed | Yes |
| **Folder & Organization**| Folder Hierarchy Entity & Room DAO migrations | ✅ Completed | Yes |
| | Custom Manila Folder Canvas Vector Graphic | ✅ Completed | Yes |
| | Document count badges per folder | ✅ Completed | Yes |
| | Filter Dropdown ("All docs (N) ▾") & Folder Navigation | ✅ Completed | Yes |
| | Sort Menu (Date Newest/Oldest, Name A-Z, Pages) | ✅ Completed | Yes |
| | Layout Toggle: 2-Column Responsive Grid ⊞ / Row List ☰ | ✅ Completed | Yes |
| | Batch Selection Mode (Move to folder, Delete, PDF) | ✅ Completed | Yes |
| | Real-time Search across titles and OCR text | ✅ Completed | Yes |
| **Document Detail** | 2-Column Page Grid with two-digit badges ("01", "02") | ✅ Completed | Yes |
| | 5-Action Bottom Navigation: Add, Collage, Share, Edit PDF, More | ✅ Completed | Yes |
| | Fullscreen Collage Studio: Question Book, ID Card, Passport, 2x1, 1x2, 4-grid, 6-grid | ✅ Completed | Yes |
| | Multi-Action Share & Export Sheet (PDF, JPG, Long image, OCR text, Split, Gallery, Email) | ✅ Completed | Yes |
| | Document "More" Settings (Sort toggle, Page Numbering, Direction, Paper Size Grid) | ✅ Completed | Yes |
| | Fullscreen Pinch-to-Zoom (1x–5x) Page Viewer with Save & Page Badges ("1/9") | ✅ Completed | Yes |
| | Quick Edit Toolbar: Anti-theft watermark, Signature, PDF Password, Compression | ✅ Completed | Yes |
| | Digital E-Signature Studio with ink extraction | ✅ Completed | Yes |
| **PDF Engine** | Custom Paper Dimensions: A4, US Letter, US Legal, Fit | ✅ Completed | Yes |
| | Quality Compression Presets (High, Medium, Small) | ✅ Completed | Yes |
| | Custom Diagonal Watermark Stamps | ✅ Completed | Yes |
| | AES 128-bit PDF Password Encryption | ✅ Completed | Yes |
| | Android Share Sheet Integration | ✅ Completed | Yes |
| **OCR (Text Extraction)**| On-device Google ML Kit Text Recognition | ✅ Completed | Yes |
| | One-tap Copy to Clipboard | ✅ Completed | Yes |
| **Theming & System** | AMOLED High-Contrast Dark Mode (`#000000`) | ✅ Completed | Yes |
| | Clean Paper Light Mode (`#F8F9FA`) | ✅ Completed | Yes |
| | Dynamic ThemeManager with SharedPreferences persistence | ✅ Completed | Yes |
| | Quick-toggle ☀️/🌙 Sun/Moon button on TopBar & Tools | ✅ Completed | Yes |
| | Edge-to-Edge System Bars (Zero white/gray navbar glitch) | ✅ Completed | Yes |
| | Neutral Dark Startup Theme (Zero white startup flash) | ✅ Completed | Yes |
| **Code Architecture** | Component-Driven Modular UI Architecture | ✅ Completed | Yes |
| | Monolithic HomeScreen decomposed (1500+ lines -> ~330 lines) | ✅ Completed | Yes |
| | Monolithic DocumentDetail decomposed (755 lines -> ~390 lines)| ✅ Completed | Yes |
| | Dialogs extracted into dedicated modules | ✅ Completed | Yes |
| **Legal & Compliance** | 100% Original ScanCraft Branding | ✅ Completed | Yes |
| | Zero third-party trademarks or competitor names in code/branches | ✅ Completed | Yes |
| | Zero proprietary binary assets or closed-source media | ✅ Completed | Yes |
| | Complete Documentation Suite (`PRD`, `ARCHITECTURE`, `DESIGN`, `CONTEXT`, `RULES`, `TRACKER`) | ✅ Completed | Yes |

---

## 2. Recent Milestones & Release Notes

### Milestone: `v1.2.0-modular` (Current Release)
* **Dual-Theme Engine:** Added seamless Light & Dark theme architecture with `ThemeManager`, instant `HomeTopBar` ☀️/🌙 toggle, and edge-to-edge system navigation bar contrast synchronization.
* **Component-Driven Modularization:** Refactored monolithic screen files into decoupled components under `com.krinry.scancraft.ui.home.components`, `com.krinry.scancraft.ui.home.dialogs`, `com.krinry.scancraft.ui.detail.components`, and `com.krinry.scancraft.ui.detail.dialogs`.
* **Zero-Branding Audit & Legal Compliance:** Completely eliminated all third-party brand names and competitor references across source code, comments, theme identifiers, and git branch names. Deleted all legacy branches with third-party names.
* **Full Documentation Suite:** Created `PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`, `CONTEXT.md`, `RULES.md`, and `TRACKER.md`.

---

## 3. Upcoming Roadmap (Next Milestones)

* [ ] **v1.3.0:** Local Wi-Fi Direct document transfer between Android devices (zero cloud).
* [ ] **v1.3.1:** Batch auto-crop with background homography warp queue.
* [ ] **v1.4.0:** Advanced PDF annotation (handwritten markup with stylus/S-Pen support).
