from pathlib import Path

import pytest
from PySide6.QtGui import QColor, QImage

from scripts.render_qa_screenshots import assert_image_size
from scripts.render_qa_screenshots import visual_metadata_fixture


def test_assert_image_size_rejects_unexpected_dimensions(tmp_path: Path):
    image_path = tmp_path / "page.png"
    image = QImage(590, 900, QImage.Format.Format_RGBA8888)
    image.fill(QColor("#ffffff"))
    assert image.save(str(image_path))

    with pytest.raises(ValueError, match="截图尺寸不一致"):
        assert_image_size(image_path, 590, 883)


def test_visual_metadata_fixture_uses_reference_like_sample_content():
    metadata = visual_metadata_fixture({"formats": [{"format_id": "18"}]})

    assert metadata["title"].startswith("Rick Astley")
    assert metadata["duration"] == 213
    assert metadata["view_count"] == 1450123456
    assert metadata["formats"] == [{"format_id": "18"}]
