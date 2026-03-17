import io
from typing import Any

try:
    import fitparse
    _FITPARSE_AVAILABLE = True
except ImportError:
    _FITPARSE_AVAILABLE = False


class FitDecodeService:
    def decode(self, data: bytes) -> list[dict[str, Any]]:
        if not _FITPARSE_AVAILABLE:
            return []
        fit = fitparse.FitFile(io.BytesIO(data))
        records = []
        for msg in fit.get_messages("record"):
            row: dict[str, Any] = {}
            for field in msg:
                row[field.name] = field.value
            records.append(row)
        return records
