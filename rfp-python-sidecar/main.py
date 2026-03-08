import logging
from contextlib import asynccontextmanager
from typing import Literal

import easyocr
from fastapi import FastAPI, HTTPException
from pydantic import Base64Bytes, BaseModel

from layout_detector import LayoutDetectionResult
from ocr_service import OcrResult, OcrService
from reading_order import ReadingOrderResult
from table_service import ExtractedTable, TableService

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(
        "event=startup component=rfp-sidecar status=INFO"
        " jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA"
        " message=RFP sidecar starting up"
    )

    app.state.table_service = getattr(app.state, "table_service", None) or TableService()
    app.state.ocr_service = getattr(app.state, "ocr_service", None) or OcrService(
        easyocr.Reader(["en", "bn"], gpu=False),
        app.state.table_service,
    )

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
    ocr_engine: str


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


class ScannedTableResponse(BaseModel):
    headers: list[str]
    rows: list[list[str]]
    confidence: float
    method: str


class TableExtractResponse(BaseModel):
    tables: list[TableResponse]


class OcrRequest(BaseModel):
    image_base64: Base64Bytes
    lang: str = "eng+ben"
    dpi: int = 300
    document_path: str | None = None
    page_number: int | None = None


class OcrPageWithLayoutResponse(BaseModel):
    ocr_result: OcrResult
    layout: LayoutDetectionResult
    reading_order: ReadingOrderResult
    scanned_tables: list[ScannedTableResponse]


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        version="1.0.0",
        ocr_engine="easyocr+tesseract",
    )


@app.post("/v1/table/extract", response_model=TableExtractResponse)
async def extract_table(request: TableExtractRequest) -> TableExtractResponse:
    extracted_tables = app.state.table_service.extract_page(
        document_path=request.documentPath,
        page_number=request.pageNumber,
        strategy=request.strategy,
    )

    response_tables = [
        _table_response(table)
        for table in extracted_tables
    ]

    return TableExtractResponse(tables=response_tables)


@app.post("/ocr/page", response_model=OcrResult)
async def ocr_page(request: OcrRequest) -> OcrResult:
    ocr_service: OcrService | None = getattr(app.state, "ocr_service", None)

    if ocr_service is None:
        raise HTTPException(
            status_code=503,
            detail="OCR service not available",
        )

    return ocr_service.extract_page(bytes(request.image_base64), request.lang)


@app.post("/ocr/page-with-layout", response_model=OcrPageWithLayoutResponse)
async def ocr_page_with_layout(
    request: OcrRequest,
) -> OcrPageWithLayoutResponse:
    ocr_service: OcrService | None = getattr(app.state, "ocr_service", None)

    if ocr_service is None:
        raise HTTPException(
            status_code=503,
            detail="OCR service not available",
        )

    ocr_result, layout, reading_order, scanned_tables = ocr_service.extract_page_with_layout(
        bytes(request.image_base64),
        request.document_path,
        request.page_number,
    )

    return OcrPageWithLayoutResponse(
        ocr_result=ocr_result,
        layout=layout,
        reading_order=reading_order,
        scanned_tables=[
            _scanned_table_response(table)
            for table in scanned_tables
        ],
    )


def _table_response(
    table: ExtractedTable,
) -> TableResponse:
    return TableResponse(
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


def _scanned_table_response(
    table: ExtractedTable,
) -> ScannedTableResponse:
    return ScannedTableResponse(
        headers=table.headers,
        rows=table.rows,
        confidence=table.confidence,
        method=table.method,
    )
