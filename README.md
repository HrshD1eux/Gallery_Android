# 🖼️ Android Gallery

A high-performance, privacy-focused Android Gallery application built with **Jetpack Compose**, **Dagger Hilt**, **Room (KSP)**, **Coil**, **Media3 ExoPlayer**, and **Hardware-Backed AES-256-GCM Cryptography**.

Engineered for large photo libraries with efficient memory management, streaming video playback, non-destructive photo editing, and an encrypted vault.

---

## 🌟 Key Features & Architecture

### 🚀 Scalable Architecture & Performance
- **Jetpack Paging 3 Grid**: Incremental SQL pagination (`pageSize = 60`) loads media in small chunks with minimal UI memory footprint ($O(1)$ rendering relative to total storage).
- **Fast Timeline Scrubber**: Equidistant date sampling with $O(1)$ cursor seeks across media collections.
- **Multi-URI Reactive ContentObserver**: Concurrent observation on `MediaStore.Images`, `MediaStore.Video`, and `MediaStore.Files` for immediate timeline sync when media changes externally.
- **Batched Metadata Queries**: Room queries use chunked `WHERE mediaId IN (:ids)` batch queries (500 items/chunk) to eliminate N+1 query overhead.
- **Downsampled Thumbnail Grid**: Coil `AsyncImage` decodes downsampled grid cells to `280x280` px for minimal RAM and GPU overhead.

### 🔐 Hardware-Backed Encrypted Vault
- **AES-256-GCM Hardware Encryption**: Media moved to the Hidden Vault is encrypted at rest using AES-256-GCM via `AndroidKeyStore` (`AES/GCM/NoPadding`, 12-byte random IV header, 128-bit authentication tag).
- **Brute-Force Resistant PIN Hashing**: PIN authentication uses `PBKDF2WithHmacSHA256` with 100,000 iterations and a 16-byte cryptographically secure salt.
- **Persistent Lockout Rate Limiting**: Anti-tampering failed attempt tracking and lockout timestamps are persisted to prevent rate-limit bypass.
- **Zero Plaintext Flash Storage Leak**: Stream-decrypted in-memory decoding via custom Coil `VaultFetcher` and Media3 `EncryptedVaultDataSource` (`CipherInputStream`). Decrypted bytes never touch disk cache.
- **Dynamic `FLAG_SECURE` Protection**: Applies window security flags when viewing vault media to prevent screenshots and task preview leaks.
- **Multi-Factor Auth**: Supports PIN, 3x3 Pattern lock, and AndroidX Biometrics (Fingerprint/Face unlock).
- **Stealth Mode**: Configurable secret search trigger (e.g. `#vault`) to access the vault when the album is hidden.

### 🧹 Duplicate Photo Finder
- **Dual Perceptual Hashing (`dHash` + `aHash`)**: Groups duplicate and near-duplicate photos using gradient difference and luminance hashes with Hamming distance metrics ($\le 10$ bits).
- **Exact File Matching**: Fast detection for identical byte size and pixel dimensions.
- **Quality Retention**: Highlights the best-resolution photo with a "Keep" badge while allowing one-tap cleanup of duplicates.

### 🎨 Photo Editor & Image Compressor
- **Non-Destructive Image Editor**: Interactive canvas for cropping (Free, 1:1, 4:3, 16:9), 90° rotation, horizontal/vertical flipping, and brightness adjustment. Exports high-res copies to `Pictures/Edited` with EXIF preservation.
- **Target KB Compressor**: Memory-safe bitmap subsampling (`inSampleSize`) to compress photos to target sizes (e.g. 50 KB, 500 KB) without heap exhaustion.

### 🎥 Native Media3 Video Player & Trimmer
- **ExoPlayer Video Engine**: Gesture seeking ($\pm 10\text{s}$), speed selector (0.5x – 2.0x), aspect ratio cycling (Fit, Zoom, Fill), loop toggle, and Picture-in-Picture (PiP) support.
- **Lossless Video Trimmer**: Keyframe-aligned track copying with `MediaExtractor` + `MediaMuxer` for instant trimming without re-encoding.
- **Animated GIF Generator**: Extracts video frames and encodes standard GIF89a animations.

### 📸 Motion Photos & Live Photos
- **Micro-Video Detection**: Binary XMP scanner detecting Google Pixel (`MicroVideoOffset`) and Samsung SEFH trailer markers.
- **Inline Playback**: Floating "Motion Photo 🎞️" badge in viewer for immediate micro-video playback.

### 🛡️ Privacy Tools & Sharing
- **EXIF Metadata Stripper**: Strips GPS coordinates, camera model, and private timestamps prior to sharing.
- **Date & Time Editor**: Update timestamps across Room, MediaStore, and EXIF `TAG_DATETIME`.
- **Multi-Photo PDF Export**: Converts selected photos into high-resolution multi-page A4 PDF documents.

---

## 🛠️ Technology Stack

| Category | Technology / Library | Version | Description |
|---|---|---|---|
| **Language** | Kotlin | `1.9.22` | Core programming language |
| **Annotation Processing** | KSP (Kotlin Symbol Processing) | `1.9.22-1.0.17` | High-speed compile-time codegen |
| **UI Framework** | Jetpack Compose | `2024.02.00 BOM` | Modern declarative UI |
| **Architecture** | ViewModel + StateFlow | Android Jetpack | Reactive state management |
| **Dependency Injection** | Dagger Hilt | `2.51.1` | Compile-time dependency injection |
| **Database** | Room | `2.6.1` (KSP) | Local SQLite metadata database with exported schemas |
| **Image Loading** | Coil | `2.6.0` | In-memory stream decoding and video frames |
| **Video Player** | AndroidX Media3 (ExoPlayer) | `1.3.1` | Native video playback & progressive cipher streaming |
| **Pagination** | Jetpack Paging 3 | `3.2.1` | Database & MediaStore query pagination |
| **Biometrics** | AndroidX Biometric | `1.1.0` | Standardized biometric prompt API |
| **EXIF Handling** | AndroidX ExifInterface | `1.3.7` | Privacy EXIF stripping and editing |
| **Cryptography** | AndroidKeyStore (AES-256-GCM) | API 29+ | Hardware-backed vault encryption & PBKDF2 hashing |

---

## 🚀 CI/CD Automation Pipeline

The repository includes automated GitHub Actions workflows located in `.github/workflows/`:

```
Push to 'main'
   │
   ├──► [lint.yml] ────────► Code Quality & Unit Test Suite
   │
   └──► [android.yml]
          │
          ├── 1. Setup Environment (JDK 17, Gradle Cache)
          ├── 2. Extract Version from build.gradle.kts
          ├── 3. Decode Keystore & Configure Signing
          ├── 4. Run Unit Tests & Scale Benchmarks
          ├── 5. Compile & Assemble Signed Release APK (assembleRelease)
          ├── 6. Upload APK Artifact to GitHub Actions
          ├── 7. Create Git Tag (e.g. v1.1.3) & Publish GitHub Release
          └── 8. Auto-Bump Version (1.1.3 -> 1.1.4) & Commit [skip ci]
```

### GitHub Repository Secrets Setup
To enable automated APK signing and GitHub Releases, configure the following secrets in **Settings > Secrets and variables > Actions**:

| Secret Name | Description | Example / Notes |
|---|---|---|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded string of your `.jks` release keystore file | Output of `[Convert]::ToBase64String([IO.File]::ReadAllBytes("gallery-release.jks"))` |
| `RELEASE_KEYSTORE_PASSWORD` | Password for the release keystore | `gallery123456` |
| `RELEASE_KEY_ALIAS` | Key alias inside the keystore | `gallery` |
| `RELEASE_KEY_PASSWORD` | Password for the key alias | `gallery123456` |

*Note: If keystore secrets are not configured (e.g. on external fork pull requests), the workflow automatically falls back to building an unsigned debug APK.*

---

## 🧪 Testing & Verification

Run the automated test suite locally:

```bash
# Run unit tests and memory/scale benchmarks
./gradlew testDebugUnitTest

# Assemble signed release APK
./gradlew assembleRelease
```

---

## 📄 License

Copyright (c) 2026 **HrshD1eux**

Licensed under standard non-commercial terms. See [LICENSE](LICENSE) for full details.
