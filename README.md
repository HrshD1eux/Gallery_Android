# 🖼️ Android Gallery — Production-Grade Media & Vault App

A high-performance, privacy-focused, production-grade Android Gallery application built with **Jetpack Compose**, **Hilt**, **Room**, **Coil**, **Media3 ExoPlayer**, and **Hardware-Backed AES-256-GCM Cryptography**.

Designed to seamlessly handle **100,000+ photo libraries** with fluid 60 FPS scrolling, zero OOM memory bottlenecks, real-time background sync, non-destructive photo editing, and an ultra-secure encrypted vault with zero plaintext disk exposure.

---

## 🌟 Key Features & Architecture Highlights

### 🚀 100k+ Photo Scalability & Performance Engine
- **Jetpack Paging 3 with Full Placeholders**: Integrated `MediaPagingSource` with `enablePlaceholders = true` (`pageSize = 60`, `prefetchDistance = 30`), allocating accurate library boundaries (`itemsBefore`, `itemsAfter`) to navigate massive libraries (100,000+ photos) without memory leaks.
- **Fast Timeline Scrubber with $O(1)$ Sampling**: Floating vertical scrubber mapped to equidistant date headers via `cursor.moveToPosition(pos)`. Eliminates startup full-cursor scans and allows instant jumps across decades of media.
- **Universal Multi-URI Reactive ContentObserver**: Listens concurrently on `MediaStore.Images`, `MediaStore.Video`, and `MediaStore.Files` external URIs, instantly refreshing the timeline grid when photos or screenshots are taken while the app is in the background.
- **Batched Metadata Queries (N+1 Solved)**: Uses SQL `WHERE mediaId IN (:ids)` batch queries to fetch Room metadata overlays for entire pages in a single query.
- **Pre-Aggregated Folder Discovery**: Queries `MediaStore` with SQL `GROUP BY (bucket_id)` and `COUNT(*)`, loading pre-counted album summaries directly from the database engine.
- **Optimized Thumbnail Downsampling**: Coil `AsyncImage` requests explicitly downsample grid cells to `.size(280, 280)` with `INEXACT` precision for minimal RAM/GPU overhead.

### 🔐 Zero-Disk-Leak Hardware-Backed Encrypted Vault
- **AES-256-GCM Hardware-Backed Encryption**: Files moved to the "Hidden Vault" are encrypted at rest using AES-256-GCM via `AndroidKeyStore` (`AES/GCM/NoPadding`, 12-byte random IV header, 128-bit authentication tag).
- **Zero Plaintext Flash Storage Leak**: Vault media is stream-decrypted directly into memory via custom Coil [`VaultFetcher`](app/src/main/java/com/HrshD1eux/Gallery/core/util/VaultFetcher.kt) (`CachePolicy.DISABLED`) and Media3 `ByteArrayDataSource`. Decrypted plaintext bytes **NEVER** touch physical flash storage or disk cache.
- **Lifecycle Auto-Lock on App Backgrounding**: Automatically locks vault sessions on `Lifecycle.Event.ON_STOP` and `ON_DESTROY`.
- **Dynamic `FLAG_SECURE` Window Protection**: Dynamically applies `WindowManager.LayoutParams.FLAG_SECURE` to the Activity window whenever viewing the Hidden Vault or secret media, blocking Android screenshots and Recent Apps switcher task preview leaks.
- **Multi-Factor Lock Options**: Support for **PIN (Numeric)**, custom 3x3 **Pattern Lock** (Canvas gesture with KeyStore hashing), and **Biometric (Fingerprint/Face)** authentication with setup flows on clean install.
- **Stealth Mode & Secret Search Passphrase**: Hide the Vault album from the Albums tab and configure a secret passphrase (e.g. `#openvault`). Typing this exact phrase in the search bar triggers the authentication dialog and opens the vault.

### 🧹 Real Duplicate Photo Finder
- **Dual Perceptual Hashing (`dHash` + `aHash`)**: Groups duplicate photos and near-duplicate screenshots across the entire library using 64-bit gradient difference hash and average luminance hash with calibrated Hamming distance thresholds ($\le 10$ bits).
- **Exact File Matching**: Instant $O(1)$ identification for exact file matches sharing identical byte size and pixel dimensions.
- **Smart Quality Retention**: Automatically analyzes resolution, file size, and timestamp to designate the best-quality photo with a `"Keep"` tag while auto-selecting duplicate copies for batch trashing.

### 🎨 Non-Destructive Photo Editor & Memory-Safe Compression
- **Non-Destructive Image Editor**: Interactive subsampled canvas preview for cropping, 90° rotation, horizontal/vertical flipping, and brightness adjustment. Exports full-resolution copies to `Pictures/Edited` while copying all original EXIF metadata.
- **Memory-Safe Target KB Image Compressor**: Uses `inJustDecodeBounds` and safe `inSampleSize` downsampling to compress photos to target sizes (e.g. 15 KB, 500 KB) without `OutOfMemoryError` on 50MP–200MP camera photos.

### 🏷️ System-Wide Scoped Storage File Operations
- **Single-Prompt Batch Trashing on Android 11+ (API 30+)**: Leverages `MediaStore.createTrashRequest` and `createDeleteRequest` to trash or permanently delete hundreds of items in a single system confirmation dialog.
- **Atomic MediaStore & Physical Rename**: Updates `DISPLAY_NAME` and `TITLE` via `ContentResolver`, performs physical file rename, and issues dual-path `MediaScannerConnection` scans to sync new filenames across the Android system.

### 🎥 Native Media3 (ExoPlayer) Video Playback
- **Inline Video Player**: Powered by **AndroidX Media3 ExoPlayer** (`media3-exoplayer` & `media3-ui`), supporting non-overlapping custom floating video controls directly inside the fullscreen photo viewer.
- **Lifecycle & Resource Management**: Seamlessly prepares and plays active videos in `HorizontalPager`, automatically pausing and releasing codec instances when swiped off-screen.

### 🛡️ Privacy-Preserving Media Sharing
- **EXIF Privacy Metadata Stripping**: Integrates `androidx.exifinterface` to strip sensitive EXIF tags (GPS coordinates, camera model, owner name, timestamps) before dispatching share intents.
- **Scoped FileProvider**: Private share files are generated in short-lived temporary directories (`cache-path/shared_images`) with automated 30-minute purging.

---

## 🛠️ Technology Stack

| Category | Technology / Library | Version | Description |
|---|---|---|---|
| **Language** | Kotlin | `1.9.22` | Core programming language |
| **UI Framework** | Jetpack Compose | `2024.02.00 BOM` | Modern declarative UI |
| **Architecture** | ViewModel + StateFlow | Android Jetpack | Reactive state management with `SavedStateHandle` |
| **Dependency Injection** | Hilt | `2.51.1` | Compile-time dependency injection |
| **Database** | Room | `2.6.1` | Local SQLite database for metadata overlays & indices |
| **Image Loading** | Coil | `2.6.0` | In-memory stream decoding, downsampling, and video frames |
| **Video Player** | AndroidX Media3 (ExoPlayer) | `1.3.1` | Native video playback engine |
| **Pagination** | Jetpack Paging 3 | `3.2.1` | Paged data pipeline with placeholder support |
| **EXIF Handling** | AndroidX ExifInterface | `1.3.7` | Privacy EXIF metadata stripping |
| **Cryptography** | AndroidKeyStore (AES-256-GCM) | Native (API 29+) | Hardware-backed vault encryption |
| **Build System** | Android Gradle Plugin (AGP) | `8.7.2` | Gradle build tools |

---

## 📁 Project Structure

```
com.HrshD1eux.Gallery/
├── core/
│   ├── di/                 # Hilt Dagger Modules (DatabaseModule, RepositoryModule)
│   └── util/               # Cryptography, Sharing & Utils (VaultCrypto, VaultFetcher, DuplicateFinder, SharingUtils)
├── data/
│   ├── database/           # Room Database, DAO (MetadataDao), & Entities (MediaMetadataEntity)
│   ├── media/              # MediaStoreDataSource (ContentResolver & Multi-URI ContentObserver)
│   ├── model/              # Immutable MediaItem domain models (Photo, Video, TimelineItem)
│   ├── paging/             # MediaPagingSource (Jetpack Paging 3 with placeholder support)
│   └── repository/         # MediaRepository interface & MediaRepositoryImpl
├── ui/
│   ├── albums/             # AlbumsScreen (Smart categories & Folder discovery)
│   ├── editor/             # PhotoEditorScreen & PhotoEditorUtils
│   ├── search/             # SearchScreen & DuplicateFinderScreen
│   ├── selection/          # SelectionState manager
│   ├── settings/           # SettingsScreen & Theme selector
│   ├── theme/              # Color, Type, & Material3 Theme configuration
│   ├── timeline/           # TimelineScreen (LazyVerticalStaggeredGrid & TimelineScrubber)
│   ├── vault/              # PatternLockView, VaultUnlockDialog, VaultSecurityDialog
│   ├── viewer/             # PhotoViewerScreen, Zoomable modifier, & VideoPlayerContainer
│   ├── MainActivity.kt     # Main entry point, lifecycle observer, & top bar router
│   └── MainViewModel.kt    # Central reactive state coordinator
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
       [ AES-256-GCM Cipher (AndroidKeyStore Master Key) ]
                                │
                                ▼
          [ Write IV + Ciphertext to filesDir/vault/ ]
                                │
                                ▼
            [ Delete Original from Public MediaStore ]
                                │
                                ▼
 [ On-The-Fly Decryption into In-Memory Buffer (VaultFetcher) ]
                                │
                                ▼
 [ Zero Plaintext Disk Exposure — Auto-Lock on ON_STOP + FLAG_SECURE ]
```

- **Master Key**: Generated inside the `AndroidKeyStore` hardware enclave under the alias `GalleryVaultMasterKey`.
- **Cipher Specs**: `AES/GCM/NoPadding` with 256-bit key size and 128-bit authentication tag.
- **PIN & Pattern Hashing**: PINs and 3x3 pattern index vectors are hashed via `SHA-256(salt + input)` using a 16-byte cryptographically secure random salt (`SecureRandom`) and encrypted with the KeyStore master key. Plaintext secrets are never stored on disk.

---

## 📦 ProGuard & Baseline Profiles Infrastructure

- **ProGuard / R8 Rules (`app/proguard-rules.pro`)**: Includes optimization rules for Room, Hilt, Coil, Jetpack Compose runtime metadata, and Media3 ExoPlayer to ensure release builds (`isMinifyEnabled = true`) compile cleanly without stripping required reflection targets.
- **ART Baseline Profiles (`app/src/main/baseline-prof.txt`)**: Defines pre-compilation rules for critical user paths (`MainActivity.onCreate`, `MainViewModel.loadNextPage`, `TimelineScreen`, `PhotoViewerScreen`, `MediaRepositoryImpl.loadMediaPaged`) to reduce cold startup time and frame drops.

---

## 🧪 Testing & Verification

The codebase includes comprehensive automated unit tests, performance benchmarks, and integration tests:

```bash
# Run unit & benchmark test suite
./gradlew testDebugUnitTest --rerun-tasks

# Run instrumentation tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

### Included Test Suite:
1. **`MediaRepositoryImplTest.kt`**: Tests paginated media loading, orphan metadata cleanup, zero plaintext disk leak assertions, in-memory vault decoding, and self-healing.
2. **`MainViewModelTest.kt`**: Tests activity result handlers, search debouncing, deletion confirmation callbacks, and pagination resets.
3. **`PhotoEditorUtilsTest.kt`**: Tests bitmap transformations, crop matrices, and perceptual duplicate hashing consistency.
4. **`SelectionStateTest.kt`**: Tests range selection and toggle operations.
5. **`TimelineBenchmarkTest.kt`**: Performance benchmark verifying 10,000 item timeline grouping completes in under 100ms.
6. **`RoomDatabaseTest.kt`** *(Instrumentation)*: Verifies Room in-memory database creation, indices, and batch operations.
7. **`VaultCryptoIntegrationTest.kt`** *(Instrumentation)*: Verifies AES-256-GCM encryption/decryption cycles and salted SHA-256 PIN/Pattern hashing.

---

## 📄 License Agreement

Copyright (c) 2026 **HrshD1eux**

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, and/or sublicense copies of the Software, subject to the following conditions:

1. **Non-Commercial Use**: You may NOT use this Software, or any modifications or derivatives of this Software, for commercial purposes. You may not sell, lease, or charge a fee for this Software or any part of it.

2. **Attribution**: If you modify, share, or distribute this Software in any form, you MUST provide clear and prominent credit to the original author (**HrshD1eux**) and include a link to the original repository.

THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
