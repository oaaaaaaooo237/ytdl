import subprocess


def hidden_window_kwargs() -> dict[str, int]:
    flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    return {"creationflags": flags} if flags else {}
