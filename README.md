# UNDER PROCESS

# 🖼️ Android Gallery — Production-Grade Media & Vault App

A high-performance, privacy-focused, production-grade Android Gallery application built with **Jetpack Compose**, **Hilt**, **Room**, **Coil**, **Media3 ExoPlayer**, and **Hardware-Backed AES-256-GCM Cryptography**.

Designed to handle **100,000+ photo libraries** with fluid 60 FPS scrolling, zero OOM memory bottlenecks, native video playback, and a secure encrypted vault.

---

## 🌟 Key Features & Architecture Highlights

### 🚀 100k+ Photo Scalability & Performance Engine
- **Paginated Media Loading**: Built with offset-based paged loading (`PAGE_SIZE = 200`) and **Jetpack Paging 3** (`MediaPagingSource`) to prevent out-of-memory (OOM) crashes on large media libraries.
- **Batched Metadata Queries (N+1 Solved)**: Uses SQL `WHERE mediaId IN (:ids)` batch queries to fetch Room metadata overlay for entire pages at once, reducing SQL queries per page from 200 to **1**.
- **Lightweight Active ID Projections**: Background orphan metadata cleanup queries only the `_ID` column from `MediaStore`, eliminating unnecessary `MediaItem` allocations.
- **Pre-Aggregated Folder Discovery**: Queries `MediaStore` with SQL `GROUP BY (bucket_id)` and `COUNT(*)`, loading pre-counted album summaries directly from the database engine instead of scanning 100,000 rows in memory.
- **Off-Main-Thread Timeline Computations**: Timeline date grouping runs entirely on `Dispatchers.Default` via reactive `StateFlow` streams.
- **Grid Thumbnail Downsampling**: Coil `AsyncImage` requests explicitly downsample thumbnails to `.size(320, 320)` for minimal GPU/memory footprint.

### 🔐 Hardware-Backed Encrypted Vault
- **AES-256-GCM Encryption**: Files moved to the "Hidden Vault" are encrypted at rest using AES-256-GCM via `AndroidKeyStore` (`AES/GCM/NoPadding`, 12-byte random IV header, 128-bit authentication tag).
- **Encrypted Metadata Sidecars**: Metadata sidecar files (`.meta`) store item properties in encrypted binary form.
- **Salted SHA-256 PIN Verification**: Vault access is secured with a 4-digit PIN hashed using SHA-256 and a 16-byte cryptographically secure random salt generated via `SecureRandom`.
- **Automatic Decrypted Cache Purging**: Temporary decrypted preview files in `cacheDir/vault_cache/` are immediately purged on app backgrounding (`Lifecycle.Event.ON_STOP`) or when locking the vault.

### 🎥 Native Media3 (ExoPlayer) Video Playback
- **Inline Video Player**: Powered by **AndroidX Media3 ExoPlayer** (`media3-exoplayer` & `media3-ui`), supporting inline video playback directly within the fullscreen photo viewer.
- **Lifecycle-Aware Playback**: Automatically starts playback when a video is the active page in `HorizontalPager` and pauses/releases hardware decoders when swiped away or backgrounded.
- **Encrypted Vault Video Support**: Seamlessly plays both public `MediaStore` videos and decrypted hidden vault videos.

### 🛡️ Privacy-Preserving Media Sharing
- **EXIF Metadata Stripping**: Integrates `androidx.exifinterface` to strip sensitive EXIF tags (GPS latitude/longitude/altitude, camera make & model, software version, photographer name) before sharing media files.
- **Scoped FileProvider**: Shared files are stored in a strictly scoped cache directory (`cache-path/shared_images`) with automated cleanup of temporary share files older than 30 minutes.

### 🎨 Modern Jetpack Compose UI & User Experience
- **Edge-to-Edge Design**: Full transparent system bars with dynamic window inset handling.
- **Adaptive Grid Density**: Pinch-to-zoom gesture support for dynamically adjusting column width in the main timeline grid.
- **Fluid Photo Viewer**: Built using `HorizontalPager` with pinch-to-zoom, bounds-constrained pan, double-tap zoom reset, and velocity-based vertical swipe-to-dismiss.
- **Selection Mode**: Multi-select media items with set-based ID tracking (`Set<Long>`) and batch share/delete actions.

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
│   └── util/               # Cryptography & Sharing utilities (VaultCrypto, SharingUtils)
├── data/
│   ├── database/           # Room Database, DAO (MetadataDao), & Entities (MediaMetadataEntity)
│   ├── media/              # MediaStoreDataSource (ContentResolver & ContentObserver)
│   ├── model/              # Immutable MediaItem domain models (Photo, Video, TimelineItem)
│   ├── paging/             # MediaPagingSource (Jetpack Paging 3 integration)
│   └── repository/         # MediaRepository interface & MediaRepositoryImpl
├── ui/
│   ├── albums/             # AlbumsScreen (Smart categories & Folder discovery)
│   ├── search/             # SearchScreen
│   ├── selection/          # SelectionState manager
│   ├── theme/              # Color, Type, & Material3 Theme configuration
│   ├── timeline/           # TimelineScreen (LazyStaggeredGrid & TimelineScrubber)
│   ├── viewer/             # PhotoViewerScreen, Zoomable modifier, & VideoPlayerContainer
│   ├── MainActivity.kt     # Main entry point & lifecycle observer
│   └── MainViewModel.kt    # Central state manager & media stream handler
```

---

## 🔒 Cryptography & Security Details

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
```

- **Master Key**: Generated inside the `AndroidKeyStore` hardware enclave under the alias `GalleryVaultMasterKey`.
- **Cipher Specs**: `AES/GCM/NoPadding` with 256-bit key size and 128-bit authentication tag.
- **PIN Security**: PIN is hashed via `SHA-256(salt + pin)` using a 16-byte random salt generated per device. The plaintext PIN is never stored on disk.

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
1. **`MediaRepositoryImplTest.kt`**: Tests paginated media loading, orphan metadata cleanup, folder count deductions, and vault self-healing.
2. **`MainViewModelTest.kt`**: Tests activity result handlers, deletion confirmation callbacks, and pagination resets.
3. **`SelectionStateTest.kt`**: Tests range selection and toggle operations.
4. **`TimelineBenchmarkTest.kt`**: Performance benchmark verifying 10,000 item timeline grouping completes in under 100ms.
5. **`RoomDatabaseTest.kt`** *(Instrumentation)*: Verifies Room in-memory database creation, indices, and batch operations.
6. **`VaultCryptoIntegrationTest.kt`** *(Instrumentation)*: Verifies AES-256-GCM encryption/decryption cycles and salted SHA-256 PIN hashing.

---
```📄
License Agreement

Copyright (c) 2026 HrshD1eux

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, and/or sublicense copies of the Software, subject to the following conditions:

1. **Non-Commercial Use**: You may NOT use this Software, or any modifications or derivatives of this Software, for commercial purposes. You may not sell, lease, or charge a fee for this Software or any part of it.

2. **Attribution**: If you modify, share, or distribute this Software in any form, you MUST provide clear and prominent credit to the original author (**HrshD1eux**) and include a link to the original repository.

THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
