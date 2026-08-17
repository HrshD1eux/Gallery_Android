# Implementation Plan: Video Controls, Grid Settings, Search, Batch Delete, & Deep Scan

Fix video control layout overlap, implement full-featured Search, add Grid Aspect Ratio settings, optimize batch deletion to trigger a single OS prompt, hide trashed items from system files, and implement deep media scanning.

## User Review Required

> [!IMPORTANT]
> - **Video Player Controls**: The Media3 seek bar and time display will be shifted above the bottom gallery action bar to guarantee zero touch or visual overlap, with explicit current time / total duration (`56:45 / 2:21:52`) and a 90° image/video rotation button.
> - **Grid Aspect Ratio**: User can toggle between **Square 1:1 Grid** and **Natural Aspect Ratio Grid** via top bar / settings.
> - **Batch Deletion / Trashing**: Passing multiple selected items as a single array of `Uri`s to `MediaStore.createTrashRequest()` / `createDeleteRequest()` to trigger **one single system prompt** instead of prompting per item.
> - **System File App Bin Fix**: MediaStore system trash integration (`IS_TRASHED = 1`) ensures trashed files do not remain visible in public file managers.
> - **Deep Media Scan**: Scans all device storage paths including WhatsApp Media, Telegram, `.nomedia` folders, and external media subfolders.

---

## Proposed Changes

### 1. Video Player Controls, Time Display & Rotation
#### [MODIFY] [VideoPlayer.kt](file:///h:/Programming/Main%20Projects/Android/Gallery/app/src/main/java/com/HrshD1eux/Gallery/ui/viewer/VideoPlayer.kt)
#### [MODIFY] [PhotoViewerScreen.kt](file:///h:/Programming/Main%20Projects/Android/Gallery/app/src/main/java/com/HrshD1eux/Gallery/ui/viewer/PhotoViewerScreen.kt)

- Shift Media3 `PlayerView` bottom controls container (`exo_bottom_bar`) with 84dp bottom inset padding so seek bar and time text float cleanly above the gallery action bar.
- Add formatted video timestamp (`MM:SS / HH:MM:SS`) to viewer bar.
- Add 90° rotation button (`0° -> 90° -> 180° -> 270°`) in top bar / bottom controls to rotate images and videos dynamically.

---

### 2. Grid Aspect Ratio Settings (Square vs. Natural)
#### [MODIFY] [MainViewModel.kt](file:///h:/Programming/Main%20Projects/Android/Gallery/app/src/main/java/com/HrshD1eux/Gallery/ui/MainViewModel.kt)
#### [MODIFY] [TimelineScreen.kt](file:///h:/Programming/Main%20Projects/Android/Gallery/app/src/main/java/com/HrshD1eux/Gallery/ui/timeline/TimelineScreen.kt)

- Add `GridStyle` enum (`SQUARE`, `NATURAL`) to `MainViewModel`.
- Add toggle menu in app bar for switching grid layout styles.
- In `TimelineScreen.kt`, adapt `MediaGridCell` aspect ratio: 1.0f for Square mode, natural media aspect ratio for Natural mode.

---

### 3. Functional Search & UI Layout Cleanup
#### [MODIFY] [SearchScreen.kt](file:///h:/Programming/Main%20Projects/Android/Gallery/app/src/main/java/com/HrshD1eux/Gallery/ui/search/SearchScreen.kt)
#### [MODIFY] [MainActivity.kt](file:///h:/Programming/Main%20Projects/Android/Gallery/app/src/main/java/com/HrshD1eux/Gallery/ui/MainActivity.kt)

- Pass `viewModel` to `SearchScreen`.
- Connect query state to filter `visibleMediaItems` in real-time across display names, folder names, MIME types, dates, and suggested tags ("Sunset", "Food", "Receipts", etc.).
- Fix `TimelineScrubber` bleed-through on `SearchScreen` by applying `clipToBounds()` to timeline container pages in `HorizontalPager`.

---

### 4. Single Batch Delete Prompt & MediaStore Trash Integration
#### [MODIFY] [MainViewModel.kt](file:///h:/Programming/Main%20Projects/Android/Gallery/app/src/main/java/com/HrshD1eux/Gallery/ui/MainViewModel.kt)
#### [MODIFY] [MediaRepositoryImpl.kt](file:///h:/Programming/Main%20Projects/Android/Gallery/app/src/main/java/com/HrshD1eux/Gallery/data/repository/MediaRepositoryImpl.kt)

- Update `deleteSelectedMedia` and `trashSelectedMedia` to accumulate selected item `Uri`s into a single `listOf(uri1, uri2, ...)` call to `MediaStore.createTrashRequest` / `MediaStore.createDeleteRequest`.
- Ensures the OS displays a **single prompt** asking "Allow Gallery to move N items to trash?" instead of prompting N times.
- Ensure trashed items invoke `MediaStore.createTrashRequest(..., true)`, marking `IS_TRASHED = 1` so files disappear from public system file managers.

---

### 5. Deep Storage & WhatsApp / Hidden Folder Scanning
#### [MODIFY] [MediaStoreDataSource.kt](file:///h:/Programming/Main%20Projects/Android/Gallery/app/src/main/java/com/HrshD1eux/Gallery/data/media/MediaStoreDataSource.kt)

- Expand `MediaStore.Files` queries to include all external volumes (`external` volume uri).
- Include `MediaStore.MATCH_INCLUDE` for hidden / `.nomedia` media items.
- Perform deep directory scanner for WhatsApp Media (`/sdcard/Android/media/com.whatsapp/`), Telegram, Pictures, Downloads, and custom storage subdirectories.

---

## Verification Plan

### Automated Tests
- `./gradlew testDebugUnitTest`: Verify all unit tests, pagination, search filter logic, and repository tests pass cleanly.

### Manual Verification
- Verify video seek bar floats cleanly above bottom gallery action bar with zero overlap.
- Verify 90° rotation button rotates video/photo preview smoothly.
- Verify switching grid style between Square and Natural updates timeline grid.
- Verify Search tab filters photos in real-time and no background numbers bleed through.
- Verify batch deleting 4 items triggers **one single system prompt**.
- Verify trashed files are hidden from system file managers.
- Verify deep scan picks up WhatsApp and hidden media folders.
