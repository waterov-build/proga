from fastapi import APIRouter, Depends, UploadFile, File
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_db
from app.services.activity_ingest_service import ActivityIngestService

router = APIRouter()


@router.post("/upload")
async def upload_fit(file: UploadFile = File(...), db: AsyncSession = Depends(get_db)) -> dict:
    svc = ActivityIngestService(db)
    activity_id = await svc.ingest(await file.read(), filename=file.filename or "upload.fit")
    return {"activity_id": activity_id, "status": "queued"}
