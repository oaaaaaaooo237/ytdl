from pathlib import Path

import pytest
from PySide6.QtCore import QRect
from PySide6.QtGui import QColor, QImage

from scripts.visual_compare import REFERENCE_CROPS, compare_page


def test_visual_compare_reports_exact_match_for_cropped_identical_image(tmp_path: Path):
    reference = tmp_path / "reference.png"
    actual = tmp_path / "actual.png"
    page = "download"
    crop = REFERENCE_CROPS[page]
    image = QImage(700, 920, QImage.Format.Format_RGBA8888)
    image.fill(QColor("#123456"))
    for y in range(crop.y(), crop.y() + crop.height()):
        for x in range(crop.x(), crop.x() + crop.width()):
            image.setPixelColor(x, y, QColor("#abcdef"))
    assert image.save(str(reference))

    actual_image = image.copy(QRect(crop.x(), crop.y(), crop.width(), crop.height()))
    assert actual_image.save(str(actual))

    diff = compare_page(reference, actual, page)

    assert diff.exact_pixel_match_percent == 100
    assert diff.mean_abs_channel_delta == 0
    assert diff.max_channel_delta == 0


def test_visual_compare_rejects_size_mismatch_instead_of_scaling(tmp_path: Path):
    reference = tmp_path / "reference.png"
    actual = tmp_path / "actual.png"
    page = "download"
    crop = REFERENCE_CROPS[page]
    reference_image = QImage(700, 920, QImage.Format.Format_RGBA8888)
    reference_image.fill(QColor("#ffffff"))
    assert reference_image.save(str(reference))
    actual_image = QImage(crop.width(), crop.height() + 10, QImage.Format.Format_RGBA8888)
    actual_image.fill(QColor("#ffffff"))
    assert actual_image.save(str(actual))

    with pytest.raises(ValueError, match="尺寸不一致"):
        compare_page(reference, actual, page)
