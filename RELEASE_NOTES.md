## 🚀 What's New in Imava v1.1.28

### 🔒 Absolute Privacy for Biometric-Locked Albums
* **Photos Tab & Feed Isolation**: Media from biometric-locked albums is now completely hidden from the main Photos timeline, Videos tab, and general gallery searches. Even if an album was opened in a session, its contents never leak into general feeds.
* **System-Wide `.nomedia` Protection**: Locking an album automatically creates a `.nomedia` file inside its physical folder and triggers a media rescan. This hides locked photos from the Android system MediaStore and all other apps on your phone. Unlocking safely removes `.nomedia` and restores access.

---

### 📅 Lossless Move & Copy Date Preservation
* **No More "Today" Timestamp Resets**: When moving or copying photos/videos to other albums, their original capture dates are 100% preserved.
* **Full EXIF & Filesystem Synchronization**: Original EXIF datetime tags (`TAG_DATETIME_ORIGINAL`, `TAG_DATETIME`, `TAG_DATETIME_DIGITIZED`), physical filesystem last-modified timestamps, and MediaStore database rows are accurately updated.
* **Metadata Migration**: Favorites, custom tags, and custom locations migrate seamlessly to the new album.

---

### 📍 Free-Form Custom Location Descriptions & Geotagging
* **Arbitrary Place Names**: You can now attach any descriptive place name (e.g. *"delhi rohtak madina village raju printing press meham"*) directly to your photos without requiring GPS coordinates.
* **Dual Search (Names & Coordinates)**: The Map Explorer and EXIF Location Editor now support searching both place names and raw coordinates (e.g. `28.6139, 77.2090` or `28.6139° N, 77.2090° E`).
* **Gallery Search Integration**: Search your gallery using words from custom place descriptions.
* **Open in Maps**: Launch Google Maps / default maps app directly by coordinates or custom place name.

---

### 🎞️ Motion Photo Frame Extraction & GIF / MP4 Export
* **Micro-Video Frame Scrubber**: Play and scrub embedded live/motion photos frame-by-frame.
* **Best-Shot Extraction**: Pick any moment from a motion photo and save it as a pristine full-resolution still.
* **Looping GIF & MP4 Export**: Convert 2–3s motion photos into smooth looping GIFs or standalone video clips with 0 KB external bloat.

---

### 🔇 1-Second Lossless Video Muter (Audio Stripper)
* **Instant Background Noise Removal**: Strip audio tracks from videos in under 1 second.
* **Zero Re-Encoding**: Copies video streams losslessly via hardware-accelerated MediaMuxer and MediaExtractor, preserving 4K/60fps quality without battery drain.

---

### 🔍 On-Device OCR with Selective Copying
* **Streamlined Scanner**: Clean single-tap document scanner action in the photo viewer.
* **Selective Highlighting**: Double-tap words or drag selection handles to copy only the exact text snippet you want.
* **Smart Quick-Copy Chips**: Tap detected lines or phrases (phone numbers, addresses, totals) to copy them instantly.
* **Searchable Text**: Search for text visible inside photos directly in the main gallery search bar.

---

### 📸 Automated Documentation & Screenshot Sync
* **Continuous Sync**: Adding or updating pictures in `screenshots/` automatically synchronizes to `docs/screenshots/`, the GitHub Pages website, and `README.md` via pre-commit hooks, Gradle tasks, and CI.

---

### 📦 Downloads & Verification
* Download the signed APK below.
* Zero cloud dependencies, 100% offline, zero analytics.
