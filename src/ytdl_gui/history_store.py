from __future__ import annotations

import json
import re
from dataclasses import asdict, dataclass, fields
from pathlib import Path


@dataclass
class HistoryRecord:
    title: str
    url: str
    output_path: str
    download_type: str
    format_summary: str
    subtitle_behavior: str
    status: str
    created_at: str


class HistoryStore:
    def __init__(self, data_dir: Path):
        self.path = data_dir / "history.json"

    def list(self) -> list[HistoryRecord]:
        if not self.path.exists():
            return []
        try:
            rows = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError, UnicodeDecodeError):
            return []
        if not isinstance(rows, list):
            return []

        records: list[HistoryRecord] = []
        for row in rows:
            record = _record_from_payload(row)
            if record is not None:
                records.append(record)
        return records

    def add(self, record: HistoryRecord) -> None:
        rows = self.list()
        rows.append(record)
        self._save_all(rows)

    def delete(self, index: int) -> None:
        rows = self.list()
        del rows[index]
        self._save_all(rows)

    def clear(self) -> None:
        self._save_all([])

    def _save_all(self, rows: list[HistoryRecord]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        safe_rows = [_sanitize_record(asdict(row)) for row in rows]
        self.path.write_text(json.dumps(safe_rows, ensure_ascii=False, indent=2), encoding="utf-8")


_SENSITIVE_PATTERNS = [
    re.compile(r"--cookies(?:\s+\"[^\"]*\"|\s+\S+)?", re.IGNORECASE),
    re.compile(r"\b(?:SID|HSID|SSID|token|access_token|auth|authorization)=\S+", re.IGNORECASE),
    re.compile(r"\b(?:authorization|cookie):[^\r\n]*", re.IGNORECASE),
]


def _sanitize_record(row: dict) -> dict:
    return {key: _sanitize_value(value) for key, value in row.items()}


def _sanitize_value(value: object) -> object:
    if not isinstance(value, str):
        return value

    sanitized = value
    for pattern in _SENSITIVE_PATTERNS:
        sanitized = pattern.sub("[已移除敏感内容]", sanitized)
    return sanitized


def _record_from_payload(row: object) -> HistoryRecord | None:
    if not isinstance(row, dict):
        return None

    allowed_fields = {field.name for field in fields(HistoryRecord)}
    clean_row = _sanitize_record({key: value for key, value in row.items() if key in allowed_fields})
    try:
        return HistoryRecord(**clean_row)
    except TypeError:
        return None
