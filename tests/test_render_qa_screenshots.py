from pathlib import Path

import pytest
from PySide6.QtGui import QColor, QImage

from scripts.render_qa_screenshots import assert_image_size
from scripts.render_qa_screenshots import apply_visual_download_state
from scripts.render_qa_screenshots import VISUAL_SAMPLE_URL
from scripts.render_qa_screenshots import VISUAL_SAVE_DIR
from scripts.render_qa_screenshots import VISUAL_HISTORY_ROWS
from scripts.render_qa_screenshots import visual_metadata_fixture
from ytdl_gui.ui.main_window import MainWindow


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


def test_visual_history_fixture_matches_reference_row_count():
    assert len(VISUAL_HISTORY_ROWS) == 5


def test_apply_visual_download_state_sets_reference_input_and_save_path(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    apply_visual_download_state(window)

    assert window.download_page.url_input.toPlainText() == VISUAL_SAMPLE_URL
    assert window.download_page.save_folder_label.text() == VISUAL_SAVE_DIR
    assert window.download_page.free_space_label.text() == "剩余空间：126.8 GB"
