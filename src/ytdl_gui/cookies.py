from pathlib import Path

from ytdl_gui.models import ValidationResult


def validate_netscape_cookies(path: Path) -> ValidationResult:
    if not path.exists() or not path.is_file():
        return ValidationResult(False, "未找到 cookies.txt 文件")

    text = path.read_text(encoding="utf-8", errors="replace")
    lines = [line for line in text.splitlines() if line.strip()]
    if not lines or "Netscape HTTP Cookie File" not in lines[0]:
        return ValidationResult(False, "请选择 Netscape 格式的 cookies.txt 文件")

    cookie_rows = [line for line in lines if not line.startswith("#")]
    if not cookie_rows:
        return ValidationResult(False, "cookies.txt 中没有可用的 cookie 记录")

    for row in cookie_rows:
        if len(row.split("\t")) < 7:
            return ValidationResult(False, "cookies.txt 行格式不完整，请重新导出 Netscape 格式文件")

    return ValidationResult(True, "cookies.txt 格式看起来有效")


def cookie_help_text() -> str:
    return (
        "cookies.txt 是敏感登录数据。建议只导出目标站点的 cookies，避免包含无关账号信息。"
        "导出前，请先在浏览器中登录目标站点，并打开目标站点页面。"
        "可以使用浏览器扩展或可信工具导出 Netscape 格式 cookies，然后在设置页选择该文件。"
        "本程序只保存 cookies.txt 文件路径，不显示、不复制、不写入日志或历史中的 cookies 内容。"
    )
