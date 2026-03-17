from sqlalchemy.ext.asyncio import AsyncSession

from app.services.fit_decode_service import FitDecodeService


class ActivityIngestService:
    def __init__(self, db: AsyncSession) -> None:
        self.db = db
        self._decoder = FitDecodeService()

    async def ingest(self, data: bytes, filename: str) -> str:
        records = self._decoder.decode(data)
        # TODO: persist activity & trigger attempt validation worker
        return "pending"
