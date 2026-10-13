#!/usr/bin/env python3
"""Render Graviton's launcher assets from the supplied 512px brand image.

Requires ImageMagick's `convert` command with WebP support. The source image is intentionally kept
as the single source of truth; this script only scales it and creates the legacy round-mask assets.
"""

from __future__ import annotations

import shutil
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/ic_launcher-playstore.png"
APP_RES = ROOT / "app/src/main/res"
DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def run_convert(arguments: list[str]) -> None:
    subprocess.run(["convert", *arguments], check=True)


def copy_brand_image() -> None:
    destinations = (
        APP_RES / "drawable-nodpi/ic_launcher_artwork.png",
        ROOT / "core/ui/src/main/res/drawable-nodpi/ic_graviton_logo.png",
        ROOT / "core/ui/src/main/ic_launcher-playstore.png",
        ROOT / "fastlane/metadata/android/en-US/images/icon.png",
    )
    for destination in destinations:
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(SOURCE, destination)
        print(f"copied {destination.relative_to(ROOT)}")


def render_legacy_mipmaps() -> None:
    for directory, size in DENSITIES.items():
        output_directory = APP_RES / directory
        output_directory.mkdir(parents=True, exist_ok=True)

        standard_icon = output_directory / "ic_launcher.webp"
        run_convert([
            str(SOURCE),
            "-filter", "Lanczos",
            "-resize", f"{size}x{size}",
            "-strip",
            "-define", "webp:lossless=true",
            "-quality", "100",
            str(standard_icon),
        ])
        print(f"rendered {standard_icon.relative_to(ROOT)} ({size}x{size})")

        center = size // 2
        round_icon = output_directory / "ic_launcher_round.webp"
        run_convert([
            str(SOURCE),
            "-filter", "Lanczos",
            "-resize", f"{size}x{size}",
            "-alpha", "set",
            "(",
            "-size", f"{size}x{size}",
            "xc:black",
            "-fill", "white",
            "-draw", f"circle {center},{center} {center},0",
            "-alpha", "copy",
            ")",
            "-compose", "CopyOpacity",
            "-composite",
            "-strip",
            "-define", "webp:lossless=true",
            "-quality", "100",
            str(round_icon),
        ])
        print(f"rendered {round_icon.relative_to(ROOT)} ({size}x{size})")


def main() -> None:
    if not SOURCE.is_file():
        raise SystemExit(f"Logo source not found: {SOURCE}")
    if shutil.which("convert") is None:
        raise SystemExit("ImageMagick `convert` is required to render launcher mipmaps.")

    copy_brand_image()
    render_legacy_mipmaps()


if __name__ == "__main__":
    main()
