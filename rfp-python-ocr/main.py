import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel

from ocr_service import OcrService

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(
        "event=startup component=ocr-sidecar status=INFO"
        " jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA"
        " message=OCR sidecar starting up"
    )
    app.state.ocr_service = OcrService()
    yield
    logger.info(
        "event=shutdown component=ocr-sidecar status=INFO"
        " jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA"
        " message=OCR sidecar shutting down"
    )


app = FastAPI(title="RFP OCR Sidecar", version="1.0.0", lifespan=lifespan)


class HealthResponse(BaseModel):
    status: str
    version: str


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse(status="ok", version="1.0.0")
