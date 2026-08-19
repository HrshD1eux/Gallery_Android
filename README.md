# 🖼️ Android Gallery — Production-Grade Media & Vault App

A high-performance, privacy-focused, production-grade Android Gallery application built with **Jetpack Compose**, **Hilt**, **Room**, **Coil**, **Media3 ExoPlayer**, and **Hardware-Backed AES-256-GCM Cryptography**.

Designed to handle **100,000+ photo libraries** with fluid 60 FPS scrolling, zero OOM memory bottlenecks, native video playback, and an ultra-secure encrypted vault.

---

## 🌟 Key Features & Architecture Highlights

### 🚀 100k+ Photo Scalability & Performance Engine
- **Paginated Media Loading**: Built with offset-based paged loading (`PAGE_SIZE = 200`) and **Jetpack Paging 3** (`MediaPagingSource`) to prevent out-of-memory (OOM) crashes on large media libraries.
- **Batched Metadata Queries (N+1 Solved)**: Uses SQL `WHERE mediaId IN (:ids)` batch queries to fetch Room metadata overlay for entire pages at once, reducing SQL queries per page from 200 to **1**.
- **Pre-Aggregated Folder Discovery**: Queries `MediaStore` with SQL `GROUP BY (bucket_id)` and `COUNT(*)`, loading pre-counted album summaries directly from the database engine instead of scanning 100,000 rows in memory.
- **Dynamic Timeline Scrubber**: Fast vertical right scrubber with dynamic sampling (max 6 date headers evenly spaced via `Arrangement.SpaceBetween`), eliminating vertical label crowding (`WED THU SAT SUN TD`) and re-indexing automatically when switching sort orders.
- **Off-Main-Thread Timeline Computations**: Timeline date grouping runs entirely on `Dispatchers.Default` via reactive `StateFlow` streams.
- **Grid Thumbnail Downsampling**: Coil `AsyncImage` requests explicitly downsample thumbnails to `.size(320, 320)` for minimal GPU/memory footprint.

### 🔐 Hardware-Backed Encrypted Vault & Stealth Security
- **AES-256-GCM Encryption**: Files moved to the "Hidden Vault" are encrypted at rest using AES-256-GCM via `AndroidKeyStore` (`AES/GCM/NoPadding`, 12-byte random IV header, 128-bit authentication tag).
- **Multi-Factor Lock Options**: Support for **PIN (Numeric)**, custom 3x3 **Pattern Lock** (Canvas gesture with KeyStore hashing), and **Biometric (Fingerprint/Face)** authentication.
- **Stealth Mode & Secret Search Passphrase**: Hide the Vault album from the Albums tab and configure a secret passphrase (e.g. `#openvault`). Typing this exact word in the top search bar triggers the security prompt and unlocks the vault.
- **Isolated In-Vault Security Settings**: Vault privacy and lock settings are completely hidden from main app settings. They can only be accessed via the 3-dots top bar menu inside the unlocked vault (`VaultSecurityDialog`).
- **On-the-Fly Decrypted Cache & Purging**: Vault items are decrypted into temporary files (`cacheDir/vault_cache/`) on-the-fly while unlocked so Coil and PhotoViewer load full quality images/videos without corruption. Temporary cache files are immediately purged on lock (`clearVaultCache`) or app backgrounding.

### 🏷️ System-Wide Scoped Storage File Renaming
- **Atomic MediaStore & Physical Rename**: Updates `DISPLAY_NAME` and `TITLE` via `ContentResolver`, performs physical file rename (`java.io.File.renameTo`), and issues dual-path `MediaScannerConnection` scans to sync new filenames across the entire Android system.

### 🚀 In-App GitHub Releases Auto-Update Engine
- **GitHub Release Tracking**: Checks `https://api.github.com/repos/HrshD1eux/Gallery_Android/releases/latest` for the latest APK releases, comparing version tags (`v1.0.1` vs `v1.0.0`).
- **In-App Download & Installation**: Downloads release APKs with a live percentage progress bar dialog to `cacheDir/updates/update.apk` and launches the native Package Installer via `FileProvider`.

### 🎥 Native Media3 (ExoPlayer) Video Playback
- **Inline Video Player**: Powered by **AndroidX Media3 ExoPlayer** (`media3-exoplayer` & `media3-ui`), supporting inline video playback directly within the fullscreen photo viewer.
- **Lifecycle-Aware Playback**: Automatically starts playback when a video is the active page in `HorizontalPager` and pauses/releases decoders when swiped away or backgrounded.

### 🛡️ Privacy-Preserving Media Sharing
- **EXIF Metadata Stripping**: Integrates `androidx.exifinterface` to strip sensitive EXIF tags (GPS coordinates, camera details, photographer name) before sharing media files.
- **Scoped FileProvider**: Shared files are stored in a strictly scoped cache directory (`cache-path/shared_images`) with automated 30-minute cleanup.

---

## 🛠️ Technology Stack

| Category | Technology / Library | Version | Description |
|---|---|---|---|
| **Language** | Kotlin | `1.9.22` | Core programming language |
| **UI Framework** | Jetpack Compose | `2024.02.00 BOM` | Modern declarative UI |
| **Architecture** | ViewModel + StateFlow | Android Jetpack | Reactive state management with `SavedStateHandle` |
| **Dependency Injection** | Hilt | `2.51.1` | Compile-time dependency injection |
| **Database** | Room | `2.6.1` | Local SQLite database for metadata overlays |
| **Image Loading** | Coil | `2.6.0` | Image loading, downsampling, and video frame extraction |
| **Video Player** | AndroidX Media3 (ExoPlayer) | `1.3.1` | Native video playback engine |
| **Pagination** | Jetpack Paging 3 | `3.2.1` | Paged data pipeline for large datasets |
| **EXIF Handling** | AndroidX ExifInterface | `1.3.7` | Privacy EXIF metadata manipulation |
| **Cryptography** | AndroidKeyStore (AES-256-GCM) | Native (API 29+) | Hardware-backed vault encryption |
| **Build System** | Android Gradle Plugin (AGP) | `8.7.2` | Gradle build tools |

---

## 📁 Project Structure

```
com.HrshD1eux.Gallery/
├── core/
│   ├── di/                 # Hilt Dagger Modules (DatabaseModule, RepositoryModule)
│   └── util/               # Cryptography, Sharing & Updates (VaultCrypto, AppUpdateManager, SharingUtils)
├── data/
│   ├── database/           # Room Database, DAO (MetadataDao), & Entities (MediaMetadataEntity)
│   ├── media/              # MediaStoreDataSource (ContentResolver & ContentObserver)
│   ├── model/              # Immutable MediaItem domain models (Photo, Video, TimelineItem)
│   ├── paging/             # MediaPagingSource (Jetpack Paging 3 integration)
│   └── repository/         # MediaRepository interface & MediaRepositoryImpl
├── ui/
│   ├── albums/             # AlbumsScreen (Smart categories & Folder discovery)
│   ├── search/             # SearchScreen (Stealth Passphrase detection)
│   ├── selection/          # SelectionState manager
│   ├── settings/           # SettingsScreen & Theme selector
│   ├── theme/              # Color, Type, & Material3 Theme configuration
│   ├── timeline/           # TimelineScreen (LazyStaggeredGrid & TimelineScrubber)
│   ├── vault/              # PatternLockView, VaultUnlockDialog, VaultSecurityDialog
│   ├── viewer/             # PhotoViewerScreen, Zoomable modifier, & VideoPlayerContainer
│   ├── MainActivity.kt     # Main entry point & top app bar action router
│   └── MainViewModel.kt    # Central state manager & media stream handler
```

---

## 🔒 Cryptography & Vault Security Architecture

```
[ Public Media File ] ──► Vault Hide Action
                                │
                                ▼
               [ Generate 12-byte Random IV ]
                                │
                                ▼
       [ AES-256-GCM Cipher (AndroidKeyStore Alias) ]
                                │
                                ▼
          [ Write IV + Ciphertext to filesDir/vault/ ]
                                │
                                ▼
            [ Delete Original from Public MediaStore ]
                                │
                                ▼
[ Decrypt On-The-Fly to cacheDir/vault_cache/ On Unlock ]
                                │
                                ▼
    [ Purge Decrypted Cache On Lock or App Exit ]
```

- **Master Key**: Generated inside the `AndroidKeyStore` hardware enclave under the alias `GalleryVaultMasterKey`.
- **Cipher Specs**: `AES/GCM/NoPadding` with 256-bit key size and 128-bit authentication tag.
- **PIN & Pattern Hashing**: PINs and 3x3 pattern index vectors are hashed via `SHA-256(salt + input)` using a 16-byte cryptographically secure random salt (`SecureRandom`). Plaintext secrets are never stored on disk.

---

## 📦 ProGuard & Baseline Profiles Infrastructure

- **ProGuard / R8 Rules (`app/proguard-rules.pro`)**: Includes rules for Room, Hilt, Coil, Jetpack Compose runtime metadata, and Media3 ExoPlayer to ensure release builds (`isMinifyEnabled = true`) compile cleanly without stripping required reflection targets.
- **ART Baseline Profiles (`app/src/main/baseline-prof.txt`)**: Defines pre-compilation rules for critical user paths (`MainActivity.onCreate`, `MainViewModel.loadNextPage`, `TimelineScreen`, `PhotoViewerScreen`, `MediaRepositoryImpl.loadMediaPaged`) to reduce cold startup time and frame drops.

---

## 🧪 Testing & Verification

The codebase includes automated unit tests, performance benchmarks, and instrumentation tests:

```bash
# Run unit & benchmark test suite
./gradlew testDebugUnitTest

# Run instrumentation tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

### Included Test Suite:
1. **`MediaRepositoryImplTest.kt`**: Tests paginated media loading, orphan metadata cleanup, folder count deductions, vault cache purging, and self-healing.
2. **`MainViewModelTest.kt`**: Tests activity result handlers, deletion confirmation callbacks, and pagination resets.
3. **`SelectionStateTest.kt`**: Tests range selection and toggle operations.
4. **`TimelineBenchmarkTest.kt`**: Performance benchmark verifying 10,000 item timeline grouping completes in under 100ms.
5. **`RoomDatabaseTest.kt`** *(Instrumentation)*: Verifies Room in-memory database creation, indices, and batch operations.
6. **`VaultCryptoIntegrationTest.kt`** *(Instrumentation)*: Verifies AES-256-GCM encryption/decryption cycles and salted SHA-256 PIN/Pattern hashing.

---

## 📄 License Agreement

Copyright (c) 2026 **HrshD1eux**

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, and/or sublicense copies of the Software, subject to the following conditions:

1. **Non-Commercial Use**: You may NOT use this Software, or any modifications or derivatives of this Software, for commercial purposes. You may not sell, lease, or charge a fee for this Software or any part of it.

2. **Attribution**: If you modify, share, or distribute this Software in any form, you MUST provide clear and prominent credit to the original author (**HrshD1eux**) and include a link to the original repository.

THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
