from pathlib import Path

import pytest


@pytest.fixture
def app_data_dir(tmp_path: Path) -> Path:
    path = tmp_path / "YTDLGui"
    path.mkdir()
    return path
