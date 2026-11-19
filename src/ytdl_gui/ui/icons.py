from PySide6.QtCore import QPoint, QSize, Qt
from PySide6.QtGui import QIcon, QPainter, QPen, QPixmap, QPolygon

from ytdl_gui.paths import resource_root


def line_icon(key, color):
    if key == "emule":
        return QIcon(str(resource_root() / "assets" / "emule.ico")).pixmap(QSize(24, 24))
    image = QPixmap(24, 24)
    image.fill(Qt.GlobalColor.transparent)
    with QPainter(image) as painter:
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        pen = QPen(color)
        pen.setWidthF(1.6)
        pen.setCapStyle(Qt.PenCapStyle.RoundCap)
        pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
        painter.setPen(pen)

        def path(*points):
            painter.drawPolyline(QPolygon([QPoint(x, y) for x, y in points]))

        if key == "appearance":
            painter.drawRoundedRect(4, 5, 16, 11, 2, 2)
            painter.drawLine(12, 16, 12, 20)
            painter.drawLine(8, 20, 16, 20)
        elif key == "behavior":
            path((5, 15), (5, 19), (19, 19), (19, 15))
            path((8, 10), (12, 14), (16, 10))
            painter.drawLine(12, 5, 12, 14)
        elif key == "storage":
            path((4, 19), (4, 6), (10, 6), (12, 9), (20, 9), (20, 19), (4, 19))
        elif key == "file":
            path((6, 4), (14, 4), (18, 8), (18, 20), (6, 20), (6, 4))
            path((14, 4), (14, 8), (18, 8))
        elif key == "search":
            painter.drawEllipse(4, 4, 11, 11)
            painter.drawLine(13, 13, 20, 20)
        elif key == "notice":
            painter.drawArc(7, 5, 10, 14, 0, 180 * 16)
            path((7, 12), (5, 17), (19, 17), (17, 12))
            painter.drawLine(11, 20, 13, 20)
        elif key == "login":
            painter.drawRoundedRect(6, 10, 12, 10, 2, 2)
            painter.drawArc(8, 4, 8, 12, 0, 180 * 16)
            painter.drawLine(12, 14, 12, 16)
        elif key == "components":
            painter.drawRoundedRect(5, 5, 14, 14, 2, 2)
            for y in (9, 15):
                painter.drawLine(8, y, 16, y)
        elif key == "about":
            painter.drawEllipse(4, 4, 16, 16)
            painter.drawPoint(12, 8)
            painter.drawLine(12, 11, 12, 16)
        elif key == "tasks":
            for y in (6, 12, 18):
                painter.drawPoint(5, y)
                painter.drawLine(9, y, 19, y)
        elif key == "settings":
            for y, x in ((6, 9), (12, 16), (18, 11)):
                painter.drawLine(4, y, x - 2, y)
                painter.drawLine(x + 2, y, 20, y)
                painter.drawEllipse(x - 2, y - 2, 4, 4)
        elif key == "pause":
            painter.drawLine(9, 6, 9, 18)
            painter.drawLine(15, 6, 15, 18)
        elif key == "cancel":
            painter.drawLine(7, 7, 17, 17)
            painter.drawLine(17, 7, 7, 17)
        elif key == "retry":
            painter.drawArc(5, 5, 14, 14, 40 * 16, 305 * 16)
            path((19, 4), (19, 9), (14, 9))
        elif key == "media":
            painter.drawRoundedRect(4, 7, 12, 10, 2, 2)
            path((16, 10), (20, 7), (20, 17), (16, 14))
        elif key == "audio":
            path((8, 17), (8, 7), (18, 5), (18, 15))
            painter.drawEllipse(4, 15, 4, 4)
            painter.drawEllipse(14, 13, 4, 4)
        elif key == "play":
            path((8, 5), (18, 12), (8, 19), (8, 5))
        else:
            path((10, 7), (15, 12), (10, 17))
    return image
