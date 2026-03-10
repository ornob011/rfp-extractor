import logging
import tempfile
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Literal

import torch
import easyocr
import pdfplumber
from fastapi import FastAPI, HTTPException
from pydantic import Base64Bytes, BaseModel

from batch_processor import BatchPageResult, process_batch
from layout_detector import LayoutDetectionResult
from ocr_service import OcrResult, OcrService
from reading_order import ReadingOrderResult
from table_service import ExtractedTable, TableService

logger = logging.getLogger(__name__)
logging.getLogger("pdfminer").setLevel(logging.ERROR)


def _unwrap_data_parallel(reader: easyocr.Reader) -> None:
    for model_attr in ("recognizer", "detector"):
        model = getattr(reader, model_attr, None)

        if not isinstance(model, torch.nn.DataParallel):
            continue

        unwrapped = model.module
        setattr(reader, model_attr, unwrapped)

        for module in unwrapped.modules():
            if isinstance(module, (torch.nn.LSTM, torch.nn.GRU, torch.nn.RNN)):
                module.flatten_parameters()

    logger.info(
        "event=rnn.unwrap component=rfp-sidecar"
        " message=Unwrapped DataParallel and flattened RNN parameters"
    )


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(
        "event=startup component=rfp-sidecar status=INFO"
        " jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA"
        " message=RFP sidecar starting up"
    )

    app.state.table_service = getattr(app.state, "table_service", None) or TableService()
    reader = easyocr.Reader(["en", "bn"], gpu=True)
    _unwrap_data_parallel(reader)
    app.state.ocr_service = getattr(app.state, "ocr_service", None) or OcrService(
        reader,
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
    documentPath: str | None = None
    documentBase64: Base64Bytes | None = None
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


class BatchTableRequest(BaseModel):
    document_base64: Base64Bytes
    pages: list[int]


class BatchTablePageResponse(BaseModel):
    page_number: int
    tables: list[TableResponse]


class BatchTableResponse(BaseModel):
    results: list[BatchTablePageResponse]


class OcrRequest(BaseModel):
    image_base64: Base64Bytes
    lang: str = "eng+ben"
    dpi: int = 300
    document_path: str | None = None
    document_base64: Base64Bytes | None = None
    page_number: int | None = None


class OcrPageWithLayoutResponse(BaseModel):
    ocr_result: OcrResult
    layout: LayoutDetectionResult
    reading_order: ReadingOrderResult
    scanned_tables: list[ScannedTableResponse]


class BatchOcrRequest(BaseModel):
    document_base64: Base64Bytes
    pages: list[int]
    dpi: int = 300
    lang: str = "eng+ben"


class BatchPageResponse(BaseModel):
    page_number: int
    ocr_result: OcrResult
    layout: LayoutDetectionResult
    reading_order: ReadingOrderResult
    scanned_tables: list[ScannedTableResponse]


class BatchOcrResponse(BaseModel):
    results: list[BatchPageResponse]


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        version="1.0.0",
        ocr_engine="easyocr+tesseract",
    )


@app.post("/v1/table/extract", response_model=TableExtractResponse)
def extract_table(request: TableExtractRequest) -> TableExtractResponse:
    document_path = _resolve_table_document_path(request)

    with pdfplumber.open(document_path) as pdf:
        page = pdf.pages[request.pageNumber - 1]
        extracted_tables = app.state.table_service.extract_page(
            document_path=document_path,
            page_number=request.pageNumber,
            strategy=request.strategy,
            pdfplumber_page=page,
        )

    response_tables = [
        _table_response(table)
        for table in extracted_tables
    ]

    return TableExtractResponse(tables=response_tables)


@app.post("/v1/table/batch", response_model=BatchTableResponse)
def extract_table_batch(request: BatchTableRequest) -> BatchTableResponse:
    document_path = _write_temp_pdf(bytes(request.document_base64))

    try:
        table_service: TableService = app.state.table_service
        batch_results = table_service.extract_document_batch(
            document_path,
            request.pages,
        )

        page_responses = [
            BatchTablePageResponse(
                page_number=page_num,
                tables=[
                    _table_response(table)
                    for table in tables
                ],
            )
            for page_num, tables in sorted(batch_results.items())
        ]

        logger.info(
            "event=table.batch component=rfp-sidecar"
            " pages=%d totalTables=%d",
            len(request.pages),
            sum(len(r.tables) for r in page_responses),
        )

        return BatchTableResponse(results=page_responses)
    finally:
        Path(document_path).unlink(missing_ok=True)


@app.post("/ocr/page", response_model=OcrResult)
def ocr_page(request: OcrRequest) -> OcrResult:
    ocr_service: OcrService | None = getattr(app.state, "ocr_service", None)

    if ocr_service is None:
        raise HTTPException(
            status_code=503,
            detail="OCR service not available",
        )

    return ocr_service.extract_page(bytes(request.image_base64), request.lang)


@app.post("/ocr/page-with-layout", response_model=OcrPageWithLayoutResponse)
def ocr_page_with_layout(
    request: OcrRequest,
) -> OcrPageWithLayoutResponse:
    ocr_service: OcrService | None = getattr(app.state, "ocr_service", None)

    if ocr_service is None:
        raise HTTPException(
            status_code=503,
            detail="OCR service not available",
        )

    document_path = _resolve_document_path(request)
    pdf, pdfplumber_page = _open_pdfplumber_page(
        document_path,
        request.page_number,
    )

    try:
        ocr_result, layout, reading_order, scanned_tables = ocr_service.extract_page_with_layout(
            bytes(request.image_base64),
            document_path,
            request.page_number,
            pdfplumber_page,
        )
    finally:
        if pdf is not None:
            pdf.close()

    return OcrPageWithLayoutResponse(
        ocr_result=ocr_result,
        layout=layout,
        reading_order=reading_order,
        scanned_tables=[
            _scanned_table_response(table)
            for table in scanned_tables
        ],
    )


@app.post("/ocr/batch", response_model=BatchOcrResponse)
def ocr_batch(request: BatchOcrRequest) -> BatchOcrResponse:
    ocr_service: OcrService | None = getattr(app.state, "ocr_service", None)

    if ocr_service is None:
        raise HTTPException(
            status_code=503,
            detail="OCR service not available",
        )

    document_path = _write_temp_pdf(bytes(request.document_base64))

    try:
        results = process_batch(
            ocr_service,
            document_path,
            request.pages,
            request.dpi,
        )

        return BatchOcrResponse(
            results=[
                _batch_page_response(r)
                for r in results
            ],
        )
    finally:
        Path(document_path).unlink(missing_ok=True)


def _batch_page_response(
    result: BatchPageResult,
) -> BatchPageResponse:
    return BatchPageResponse(
        page_number=result.page_number,
        ocr_result=result.ocr_result,
        layout=result.layout,
        reading_order=result.reading_order,
        scanned_tables=[
            ScannedTableResponse(
                headers=t.headers,
                rows=t.rows,
                confidence=t.confidence,
                method=t.method,
            )
            for t in result.scanned_tables
        ],
    )


def _write_temp_pdf(pdf_bytes: bytes) -> str:
    tmp = tempfile.NamedTemporaryFile(
        suffix=".pdf",
        delete=False,
    )
    tmp.write(pdf_bytes)
    tmp.close()
    return tmp.name


def _resolve_document_path(request: OcrRequest) -> str | None:
    if request.document_base64 is not None:
        return _write_temp_pdf(bytes(request.document_base64))

    if request.document_path and Path(request.document_path).exists():
        return request.document_path

    return None


def _open_pdfplumber_page(
    document_path: str | None,
    page_number: int | None,
) -> tuple[pdfplumber.PDF | None, object | None]:
    if document_path is None or page_number is None:
        return None, None

    pdf = pdfplumber.open(document_path)
    return pdf, pdf.pages[page_number - 1]


def _resolve_table_document_path(request: TableExtractRequest) -> str:
    if request.documentBase64 is not None:
        return _write_temp_pdf(bytes(request.documentBase64))

    if request.documentPath and Path(request.documentPath).exists():
        return request.documentPath

    raise HTTPException(
        status_code=400,
        detail="Either documentBase64 or a valid documentPath is required",
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
