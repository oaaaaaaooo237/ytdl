from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class ValidationResult:
    ok: bool
    message: str


@dataclass
class Serializable:
    def to_dict(self) -> dict:
        return asdict(self)
