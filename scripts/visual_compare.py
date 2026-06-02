from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path

from PySide6.QtCore import QRect
from PySide6.QtGui import QImage


REFERENCE_CROPS = {
    "download": QRect(20, 22, 590, 883),
    "formats": QRect(620, 22, 488, 883),
    "queue": QRect(1117, 22, 535, 883),
}


@dataclass(frozen=True)
class VisualDiff:
    page: str
    reference_size: str
    actual_size: str
    compared_size: str
    mean_abs_channel_delta: float
    exact_pixel_match_percent: float
    max_channel_delta: int


def compare_page(reference: Path, actual: Path, page: str) -> VisualDiff:
    reference_image = QImage(str(reference))
    actual_image = QImage(str(actual))
    if reference_image.isNull():
        raise ValueError(f"无法读取参考图：{reference}")
    if actual_image.isNull():
        raise ValueError(f"无法读取截图：{actual}")
    if page not in REFERENCE_CROPS:
        raise ValueError(f"未知页面：{page}")

    crop = reference_image.copy(REFERENCE_CROPS[page]).convertToFormat(QImage.Format.Format_RGBA8888)
    if actual_image.size() != crop.size():
        raise ValueError(
            "截图尺寸不一致："
            f"{page} reference={crop.width()}x{crop.height()} "
            f"actual={actual_image.width()}x{actual_image.height()}"
        )
    compared_actual = actual_image.convertToFormat(QImage.Format.Format_RGBA8888)

    total_delta = 0
    max_delta = 0
    exact_pixels = 0
    width = crop.width()
    height = crop.height()
    pixels = width * height

    for y in range(height):
        for x in range(width):
            reference_color = crop.pixelColor(x, y)
            actual_color = compared_actual.pixelColor(x, y)
            deltas = (
                abs(reference_color.red() - actual_color.red()),
                abs(reference_color.green() - actual_color.green()),
                abs(reference_color.blue() - actual_color.blue()),
                abs(reference_color.alpha() - actual_color.alpha()),
            )
            if deltas == (0, 0, 0, 0):
                exact_pixels += 1
            total_delta += sum(deltas)
            max_delta = max(max_delta, *deltas)

    return VisualDiff(
        page=page,
        reference_size=f"{crop.width()}x{crop.height()}",
        actual_size=f"{actual_image.width()}x{actual_image.height()}",
        compared_size=f"{width}x{height}",
        mean_abs_channel_delta=round(total_delta / (pixels * 4), 4),
        exact_pixel_match_percent=round(exact_pixels * 100 / pixels, 4),
        max_channel_delta=max_delta,
    )


def compare_all(reference: Path, screenshots_dir: Path) -> list[VisualDiff]:
    pages = {
        "download": screenshots_dir / "1-download.png",
        "formats": screenshots_dir / "2-formats.png",
        "queue": screenshots_dir / "3-queue.png",
    }
    return [compare_page(reference, actual, page) for page, actual in pages.items()]


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare GUI QA screenshots against cropped reference windows.")
    parser.add_argument("--reference", type=Path, default=Path("docs/gui-reference.png"))
    parser.add_argument("--screenshots", type=Path, default=Path("docs/qa/screenshots"))
    parser.add_argument("--output", type=Path, default=Path("docs/qa/visual-diff.json"))
    args = parser.parse_args()

    diffs = compare_all(args.reference, args.screenshots)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps([asdict(diff) for diff in diffs], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    for diff in diffs:
        print(
            f"{diff.page}: exact={diff.exact_pixel_match_percent}% "
            f"mean_delta={diff.mean_abs_channel_delta} max_delta={diff.max_channel_delta}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
