#!/usr/bin/env python3
"""
sync_screenshots.py
Automatically synchronizes screenshots from 'screenshots/' to:
1. docs/screenshots/ (copies and syncs image files for the website)
2. README.md (generates responsive screenshot grid with names based on filenames)
3. docs/index.html (generates phone cards in .screenshots-grid for the website)

Usage:
  python scripts/sync_screenshots.py          # Run once
  python scripts/sync_screenshots.py --watch  # Run in watch mode (auto-syncs on file changes)
"""

import os
import sys
import shutil
import re
import time
from pathlib import Path

# Ensure UTF-8 output on Windows consoles
if hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

# Supported image extensions
IMAGE_EXTS = {'.png', '.jpg', '.jpeg', '.webp', '.gif'}

# Preferred ordering for key gallery screens
PRIORITY_ORDER = {
    'photo': 1,
    'photos': 1,
    'timeline': 1,
    'album': 2,
    'albums': 2,
    'vault': 3,
    'hidden': 3,
    'setting': 4,
    'settings': 4,
    'map': 5,
    'map_explorer': 5,
    'viewer': 6,
    'photo_viewer': 6,
    'editor': 7,
    'ocr': 8,
    'ocr_scanner': 8,
    'collage': 9,
    'video': 10,
    'video_player': 10,
    'doctor': 11,
    'storage_doctor': 11,
}

ACRONYMS = {'ocr': 'OCR', 'gps': 'GPS', 'exif': 'EXIF', 'ai': 'AI', 'ui': 'UI', 'apk': 'APK', 'gif': 'GIF', 'mp4': 'MP4'}
TITLE_ALIASES = {
    'photo': 'Photos',
    'photos': 'Photos',
    'album': 'Albums',
    'albums': 'Albums',
    'vault': 'Vault',
    'setting': 'Settings',
    'settings': 'Settings',
}

def get_repo_root() -> Path:
    current = Path(__file__).resolve().parent
    if (current.parent / "README.md").exists() and (current.parent / "screenshots").exists():
        return current.parent
    return Path.cwd()

def format_title(filename: str) -> str:
    stem = Path(filename).stem.strip()
    lower_stem = stem.lower()
    if lower_stem in TITLE_ALIASES:
        return TITLE_ALIASES[lower_stem]

    words = re.split(r'[-_\s]+', stem)
    result = []
    for w in words:
        if not w:
            continue
        low = w.lower()
        if low in ACRONYMS:
            result.append(ACRONYMS[low])
        elif low in TITLE_ALIASES:
            result.append(TITLE_ALIASES[low])
        else:
            result.append(w.capitalize())
    return " ".join(result) if result else stem

def get_sort_key(file_path: Path):
    stem = file_path.stem.lower()
    for key, priority in PRIORITY_ORDER.items():
        if stem == key or stem.startswith(key + '_') or stem.startswith(key + '-'):
            return (priority, stem)
    return (100, stem)

def sync_images(src_dir: Path, dest_dir: Path) -> list[str]:
    dest_dir.mkdir(parents=True, exist_ok=True)
    
    src_files = [f for f in src_dir.iterdir() if f.is_file() and f.suffix.lower() in IMAGE_EXTS]
    src_files.sort(key=get_sort_key)
    
    src_names = {f.name for f in src_files}
    
    # Clean up files in dest that no longer exist in src
    for dest_file in dest_dir.iterdir():
        if dest_file.is_file() and dest_file.suffix.lower() in IMAGE_EXTS:
            if dest_file.name not in src_names:
                print(f"  [-] Removing old screenshot: docs/screenshots/{dest_file.name}")
                dest_file.unlink()

    # Copy / update from src to dest
    for src_file in src_files:
        dest_file = dest_dir / src_file.name
        needs_copy = False
        if not dest_file.exists():
            needs_copy = True
        elif src_file.stat().st_size != dest_file.stat().st_size or src_file.stat().st_mtime > dest_file.stat().st_mtime:
            needs_copy = True
        
        if needs_copy:
            print(f"  [+] Copying screenshot: screenshots/{src_file.name} -> docs/screenshots/{src_file.name}")
            shutil.copy2(src_file, dest_file)

    return [f.name for f in src_files]

def generate_readme_screenshots_html(image_names: list[str]) -> str:
    if not image_names:
        return "<!-- SCREENSHOTS_START -->\n## Screenshots\n\n*No screenshots added yet.*\n<!-- SCREENSHOTS_END -->"

    lines = [
        "<!-- SCREENSHOTS_START -->",
        "## Screenshots",
        "",
        '<div align="center">',
        '  <table>'
    ]

    # Chunk into rows of 4
    chunk_size = 4
    for i in range(0, len(image_names), chunk_size):
        chunk = image_names[i:i + chunk_size]
        lines.append('    <tr>')
        for name in chunk:
            title = format_title(name)
            lines.append('      <td align="center" width="25%">')
            lines.append(f'        <b>{title}</b><br><br>')
            lines.append(f'        <img src="screenshots/{name}" width="100%" alt="{title}" />')
            lines.append('      </td>')
        lines.append('    </tr>')

    lines.append('  </table>')
    lines.append('</div>')
    lines.append('<!-- SCREENSHOTS_END -->')
    return "\n".join(lines)

def generate_website_grid_html(image_names: list[str]) -> str:
    lines = [
        '      <!-- SCREENSHOTS_START -->',
        '      <div class="screenshots-grid">'
    ]
    for name in image_names:
        title = format_title(name)
        lines.append('        <div class="phone-card">')
        lines.append(f'          <img src="screenshots/{name}" alt="{title}" loading="lazy">')
        lines.append(f'          <div class="phone-caption">{title}</div>')
        lines.append('        </div>')
    lines.append('      </div>')
    lines.append('      <!-- SCREENSHOTS_END -->')
    return "\n".join(lines)

def update_readme(readme_path: Path, new_content: str) -> bool:
    if not readme_path.exists():
        return False
    text = readme_path.read_text(encoding="utf-8")

    # Check for markers first
    marker_pattern = re.compile(r'<!--\s*SCREENSHOTS_START\s*-->.*?<!--\s*SCREENSHOTS_END\s*-->', re.DOTALL)
    if marker_pattern.search(text):
        updated = marker_pattern.sub(new_content, text)
    else:
        # Fallback: look for ## Screenshots up to next section or separator
        header_pattern = re.compile(r'##\s+Screenshots\s*\n\s*(?:<p align="center">.*?</p>|.*?\n(?=---|\n##\s))', re.DOTALL)
        if header_pattern.search(text):
            updated = header_pattern.sub(new_content, text)
        else:
            # Append if not found
            updated = text + "\n\n" + new_content + "\n"

    if updated != text:
        readme_path.write_text(updated, encoding="utf-8")
        return True
    return False

def update_website_html(html_path: Path, new_grid_html: str) -> bool:
    if not html_path.exists():
        return False
    text = html_path.read_text(encoding="utf-8")

    marker_pattern = re.compile(r'^[ \t]*<!--\s*SCREENSHOTS_START\s*-->.*?<!--\s*SCREENSHOTS_END\s*-->', re.DOTALL | re.MULTILINE)
    if marker_pattern.search(text):
        updated = marker_pattern.sub(new_grid_html, text)
    else:
        grid_pattern = re.compile(r'<div class="screenshots-grid">.*?</div>', re.DOTALL)
        if grid_pattern.search(text):
            updated = grid_pattern.sub(new_grid_html, text)
        else:
            return False

    if updated != text:
        html_path.write_text(updated, encoding="utf-8")
        return True
    return False

def run_sync() -> bool:
    repo_root = get_repo_root()
    src_dir = repo_root / "screenshots"
    dest_dir = repo_root / "docs" / "screenshots"
    readme_file = repo_root / "README.md"
    html_file = repo_root / "docs" / "index.html"

    if not src_dir.exists():
        print(f"Error: Screenshots directory '{src_dir}' not found.")
        return False

    print(f"[*] Synchronizing screenshots from '{src_dir.name}'...")
    image_names = sync_images(src_dir, dest_dir)
    print(f"  Found {len(image_names)} screenshot(s): {', '.join(image_names)}")

    readme_html = generate_readme_screenshots_html(image_names)
    readme_changed = update_readme(readme_file, readme_html)
    if readme_changed:
        print(f"  [OK] Updated {readme_file.name}")
    else:
        print(f"  [--] {readme_file.name} is already up to date")

    website_grid = generate_website_grid_html(image_names)
    html_changed = update_website_html(html_file, website_grid)
    if html_changed:
        print(f"  [OK] Updated {html_file.relative_to(repo_root)}")
    else:
        print(f"  [--] {html_file.relative_to(repo_root)} is already up to date")

    print("[SUCCESS] Screenshots sync complete!\n")
    return readme_changed or html_changed

def watch_mode():
    repo_root = get_repo_root()
    src_dir = repo_root / "screenshots"
    print(f"[*] Watching '{src_dir}' for changes (Press Ctrl+C to stop)...")
    run_sync()

    last_snapshot = {}
    def get_snapshot():
        snap = {}
        if src_dir.exists():
            for f in src_dir.iterdir():
                if f.is_file() and f.suffix.lower() in IMAGE_EXTS:
                    snap[f.name] = (f.stat().st_size, f.stat().st_mtime)
        return snap

    last_snapshot = get_snapshot()
    try:
        while True:
            time.sleep(2)
            current_snapshot = get_snapshot()
            if current_snapshot != last_snapshot:
                print("[CHANGE] Detected screenshot file change!")
                run_sync()
                last_snapshot = current_snapshot
    except KeyboardInterrupt:
        print("\nStopped watch mode.")

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--watch":
        watch_mode()
    else:
        run_sync()
