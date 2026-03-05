import logging
from contextlib import asynccontextmanager
from typing import Literal

from fastapi import FastAPI
from pydantic import BaseModel

from ocr_service import OcrService
from table_service import TableService

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(
        "event=startup component=rfp-sidecar status=INFO"
        " jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA"
        " message=RFP sidecar starting up"
    )
    app.state.ocr_service = OcrService()
    app.state.table_service = TableService()
    yield
    logger.info(
        "event=shutdown component=rfp-sidecar status=INFO"
        " jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA"
        " message=RFP sidecar shutting down"
    )


app = FastAPI(title="RFP Python Sidecar", version="1.0.0", lifespan=lifespan)


class HealthResponse(BaseModel):
    status: str
    version: str


class TableExtractRequest(BaseModel):
    documentPath: str
    pageNumber: int
    strategy: Literal["lattice", "stream"]


class TableCellResponse(BaseModel):
    row: int
    col: int
    value: str
    rowspan: int
    colspan: int
    isHeader: bool


class TableResponse(BaseModel):
    caption: str | None
    headers: list[str]
    grid: list[list[TableCellResponse]]
    confidence: float
    method: str


class TableExtractResponse(BaseModel):
    tables: list[TableResponse]


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse(status="ok", version="1.0.0")


@app.post("/v1/table/extract", response_model=TableExtractResponse)
async def extract_table(request: TableExtractRequest) -> TableExtractResponse:
    extracted_tables = app.state.table_service.extract_page(
        document_path=request.documentPath,
        page_number=request.pageNumber,
        strategy=request.strategy,
    )

    response_tables = [
        TableResponse(
            caption=table.caption,
            headers=table.headers,
            grid=[
                [
                    TableCellResponse(
                        row=cell.row,
                        col=cell.col,
                        value=cell.value,
                        rowspan=cell.rowspan,
                        colspan=cell.colspan,
                        isHeader=cell.is_header,
                    )
                    for cell in row
                ]
                for row in table.grid
            ],
            confidence=table.confidence,
            method=table.method,
        )
        for table in extracted_tables
    ]

    return TableExtractResponse(tables=response_tables)
