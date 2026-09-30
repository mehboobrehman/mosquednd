#!/usr/bin/env python3
"""
Generate all app icons from a master source image.

Reads a single source image, downscales to all required mipmap densities,
and generates the adaptive-icon foreground drawable.

All output PNGs are RGBA mode (required for AAPT2/release builds).

Usage:
    python scripts/generate_icons.py
    python scripts/generate_icons.py --source path/to/source.png
    python scripts/generate_icons.py --source path/to/source.png --output-dir path/to/res
"""
import argparse
import os
import sys

from PIL import Image

# Default source path (relative to workspace root)
DEFAULT_SOURCE = "tools/icon_backups/ic_launcher_xxxhdpi_orig.png"

# Target definitions: (output_rel_path, target_size_px)
# Ordered by increasing size so the smallest image is generated from the
# source directly (LANCZOS downscale is always quality-preserving).
TARGETS = [
    ("mipmap-mdpi/ic_launcher.png", 48),
    ("mipmap-hdpi/ic_launcher.png", 72),
    ("mipmap-xhdpi/ic_launcher.png", 96),
    ("mipmap-xxhdpi/ic_launcher.png", 144),
    ("mipmap-xxxhdpi/ic_launcher.png", 192),
    # Adaptive-icon foreground — placed in drawable/ (no density qualifier)
    # because the adaptive-icon XML references @drawable/ic_launcher_foreground.
    # The system auto-scales the 192x192 PNG to fit the 108x108dp canvas.
    ("drawable/ic_launcher_foreground.png", 192),
]


def load_source(source_path: str) -> Image.Image:
    """Load and validate the source image."""
    if not os.path.isfile(source_path):
        print(f"ERROR: Source image not found: {source_path}", file=sys.stderr)
        sys.exit(1)

    img = Image.open(source_path)
    if img.mode != "RGBA":
        print(f"Converting source from {img.mode} to RGBA")
        img = img.convert("RGBA")
    else:
        img = img.copy()

    w, h = img.size
    print(f"Source: {source_path}  ({w}x{h}  {img.mode})")
    return img


def fits_within(img_size: tuple, canvas_size: int) -> bool:
    """Return True if img_size fits within a square canvas without upscaling."""
    return img_size[0] <= canvas_size and img_size[1] <= canvas_size


def resize_and_center(source: Image.Image, target_size: int) -> Image.Image:
    """
    Downscale (or upscale) source to fit within a square canvas of target_size,
    preserving aspect ratio and centering on a transparent background.
    """
    sw, sh = source.size

    # Compute the scale factor to fit inside the square canvas
    scale = min(target_size / sw, target_size / sh)
    nw = max(1, int(sw * scale))
    nh = max(1, int(sh * scale))

    resized = source.resize((nw, nh), Image.LANCZOS)

    canvas = Image.new("RGBA", (target_size, target_size), (0, 0, 0, 0))
    ox = (target_size - nw) // 2
    oy = (target_size - nh) // 2
    canvas.paste(resized, (ox, oy), resized)

    return canvas


def generate_all(source: Image.Image, res_dir: str) -> None:
    """Generate every target icon from the source image."""
    for rel_path, size in TARGETS:
        dst = os.path.join(res_dir, rel_path)
        os.makedirs(os.path.dirname(dst), exist_ok=True)

        # Downscale directly from the source for each target so every
        # output is independently derived (no cascading quality loss).
        icon = resize_and_center(source, size)
        icon.save(dst, "PNG")

        w_margin = max(0, size - icon.getbbox()[2] if icon.getbbox() else 0)
        print(f"  {size:>3}px  -> {dst}  ({icon.size[0]}x{icon.size[1]})")


def verify_targets(res_dir: str) -> bool:
    """Verify every target file exists and has correct dimensions."""
    all_ok = True
    for rel_path, expected_size in TARGETS:
        fp = os.path.join(res_dir, rel_path)
        if not os.path.isfile(fp):
            print(f"  MISSING: {fp}", file=sys.stderr)
            all_ok = False
            continue
        img = Image.open(fp)
        if img.size != (expected_size, expected_size):
            print(f"  SIZE MISMATCH: {fp}  expected {expected_size}x{expected_size} got {img.size[0]}x{img.size[1]}", file=sys.stderr)
            all_ok = False
            continue
        if img.mode != "RGBA":
            print(f"  MODE NOT RGBA: {fp}  mode={img.mode}", file=sys.stderr)
            all_ok = False
    return all_ok


def main():
    parser = argparse.ArgumentParser(
        description="Generate all app icons from a master source image."
    )
    parser.add_argument(
        "--source",
        default=DEFAULT_SOURCE,
        help=f"Path to the source image (default: {DEFAULT_SOURCE})",
    )
    parser.add_argument(
        "--output-dir",
        default="app/src/main/res",
        help="Android res/ directory (default: app/src/main/res)",
    )
    parser.add_argument(
        "--verify-only",
        action="store_true",
        help="Only verify existing icons, do not regenerate",
    )
    args = parser.parse_args()

    # Resolve paths relative to the script's parent directory (workspace root)
    script_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    source_path = args.source if os.path.isabs(args.source) else os.path.join(script_dir, args.source)
    res_dir = args.output_dir if os.path.isabs(args.output_dir) else os.path.join(script_dir, args.output_dir)

    if args.verify_only:
        print(f"Verifying icons in {res_dir} ...")
        ok = verify_targets(res_dir)
        print("All targets OK" if ok else "Some targets FAILED")
        sys.exit(0 if ok else 1)

    print(f"Generating icons from:\n  {source_path}\n  -> {res_dir}\n")
    source = load_source(source_path)
    generate_all(source, res_dir)

    print("\nVerifying generated files ...")
    ok = verify_targets(res_dir)
    print("Done — all targets OK" if ok else "Done — some targets FAILED")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
