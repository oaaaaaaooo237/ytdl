from enum import Enum

from PySide6.QtCore import Qt, QUrl
from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer
from PySide6.QtWidgets import QHBoxLayout, QLabel, QPushButton, QSlider, QVBoxLayout, QWidget


class PreviewState(str, Enum):
    IDLE = "idle"
    LOADING = "loading"
    PLAYING = "playing"
    PAUSED = "paused"
    UNAVAILABLE = "unavailable"
    ERROR = "error"


def preview_failure_message(state: PreviewState) -> str:
    if state == PreviewState.UNAVAILABLE:
        return "预览不可用，下载仍可继续。"
    return "预览播放失败，下载仍可继续。"


class PreviewPlayer(QWidget):
    def __init__(self):
        super().__init__()
        self.player = QMediaPlayer(self)
        self.audio = QAudioOutput(self)
        self.player.setAudioOutput(self.audio)
        self.state = PreviewState.IDLE

        self.status = QLabel("预览未开始")
        self.play_button = QPushButton("播放")
        self.pause_button = QPushButton("暂停")
        self.volume = QSlider(Qt.Orientation.Horizontal)
        self.volume.setRange(0, 100)
        self.volume.setValue(60)
        self.audio.setVolume(0.6)

        self.play_button.clicked.connect(self.play)
        self.pause_button.clicked.connect(self.pause)
        self.volume.valueChanged.connect(self._set_volume)
        self.player.errorOccurred.connect(self._show_playback_error)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)
        layout.addWidget(self.status)
        controls = QHBoxLayout()
        controls.addWidget(self.play_button)
        controls.addWidget(self.pause_button)
        layout.addLayout(controls)
        layout.addWidget(self.volume)

    def load_url(self, url: str) -> None:
        self.show()
        if not url:
            self.show_unavailable()
            return
        self.state = PreviewState.LOADING
        self.status.setText("正在加载预览...")
        self.player.setSource(QUrl(url))

    def play(self) -> None:
        self.player.play()
        self.state = PreviewState.PLAYING
        self.status.setText("正在预览播放")

    def pause(self) -> None:
        self.player.pause()
        self.state = PreviewState.PAUSED
        self.status.setText("预览已暂停")

    def show_unavailable(self) -> None:
        self.show()
        self.player.stop()
        self.state = PreviewState.UNAVAILABLE
        self.status.setText(preview_failure_message(PreviewState.UNAVAILABLE))

    def _show_playback_error(self, *_args) -> None:
        self.state = PreviewState.ERROR
        self.status.setText(preview_failure_message(PreviewState.ERROR))

    def _set_volume(self, value: int) -> None:
        self.audio.setVolume(value / 100)
