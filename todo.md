# Gallery Product Roadmap & To-Do List

This document lists the feature roadmap for our high-performance, private, local-first gallery app. 

---

## 🚀 Version 1.0 (Core Engine & Performance Prototype)
*Focus: Extreme scrolling speed, gesture fluidness, edge-to-edge aesthetics, and architectural privacy.*

### 1. Photos & Timeline Grid
- [x] **Unified Media Feed**: Combine images and videos from MediaStore in one chronological stream.
- [x] **Date Headers**: Group items by date taken (e.g. *Today*, *Yesterday*, *Month Year*) with a single-flat-hierarchy LazyVerticalGrid layout to maintain maximum FPS.
- [x] **Dynamic Grid & Densities**: Let users pinch-to-zoom between Compact (5), Normal (3), and Large (2) column densities, with an adaptive cinematic layout (like Google Photos) where landscape and favorited photos dynamically span 2 columns.
- [x] **Timeline Scrubber**: Drag-based fast-scroll date rail for immediate navigation through thousands of images.

### 2. Photo Viewer & Gestures
- [x] **Edge-to-Edge Fluidity**: Full-bleed rendering with translucent, auto-fading app bars and navigation controls.
- [x] **Swipe-to-Dismiss**: Drag-down gesture with smooth scaling, transition, and alpha fading to return to the grid.
- [x] **Zoom Gestures**: Multi-pointer pinch-to-zoom and pan, plus double-tap to zoom.
- [x] **Gesture Conflict Resolution**: Geolocation drag/dismiss is automatically bypassed when scale > 1f.
- [x] **Interactive Info Sheet**: Draggable bottom sheet with EXIF parameters (camera details, file size, dimensions, location coordinates).

### 3. Media Actions & Albums
- [x] **Dynamic Folders**: List local media folders (Camera, Screenshots, downloads) dynamically from the MediaStore.
- [x] **Smart Lists**: Filter items into virtual categories for Favorites, Trashed, and Hidden files.
- [x] **Selection Mode**: Long-press to activate multi-select with a contextual M3 action bar for batch editing (share, delete, hide).

### 4. Architectural Privacy
- [x] **Metadata Stripping**: Dialog to strip camera make/model and GPS tags from shared copies using the secure cache directory.
- [x] **Vault Simulation**: Secure UI pattern for locked vault access.
- [ ] **Basic Editor**: Basic non-destructive crop, rotate, flip, and brightness/contrast adjustments.
- [ ] **Simulated App Lock**: Require passcode/biometrics to open hidden albums.

---

## 🌀 Version 2.0 (Smart Utilities & Basic Automation)
*Focus: Native on-device indexing, utility management, and basic file modifications.*

### 1. Smart Utilities
- [ ] **Duplicate Finder**: Identify exact duplicate files based on hash check and file parameters.
- [ ] **Similar-Photo Finder**: Run localized low-resource clustering to group visually near-identical bursts/photos.
- [ ] **Screenshot Intelligence**: Categorize screenshots into buckets (e.g., chats, tickets, receipts, memes) using simple local heuristics.

### 2. User Experience Extensions
- [ ] **Interactive Maps View**: Plot photo location markers on a local map.
- [ ] **Automatic Moments**: Cluster photos automatically into events (e.g. *Weekend Trip*, *Family Dinner*) based on location coordinates and time density.
- [ ] **Full Local Editor**: Implement non-destructive cropping, rotation, saturation, highlights, temperature, and basic brush/drawing tools.
- [ ] **Integrated Video Player**: Add frame scrubbing, trimming, and instant mute tools inside the photo viewer instead of launching intents.

---

## 🧠 Version 3.0 (On-Device AI & Extensions)
*Focus: Fully local AI search, face grouping, and plugin architecture.*

### 1. Local Intelligent Search
- [ ] **On-Device Semantic Search**: Process images with a local CLIP or MobileNet model to search via natural language queries (e.g. *"photos of dogs in snow"*).
- [ ] **Face Grouping**: Local clustering of faces to index and filter media by recognized individuals.
- [ ] **OCR & Document Detection**: Index document text locally, allowing users to search via text content.

### 2. Extensions & Scale
- [ ] **Dynamic Rules Engine**: Allow users to create custom smart albums (e.g. *"Screenshots from last week containing 'invoice'"*).
- [ ] **Plugin System**: Allow developers to build custom editors, backup endpoints, or metadata exporters.
