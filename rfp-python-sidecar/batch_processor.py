import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import pdfplumber
from pydantic import BaseModel

from image_utils import render_pdf_pages_batch
from layout_detector import LayoutDetectionResult, detect_layout_from_regions
from ocr_service import OcrResult, OcrService
from reading_order import (
    ReadingOrderMethod,
    ReadingOrderResult,
    resolve_reading_order,
)
from table_service import ExtractedTable

logger = logging.getLogger(__name__)

BATCH_WORKERS = 4


class BatchPageResult(BaseModel):
    page_number: int
    ocr_result: OcrResult
    layout: LayoutDetectionResult
    reading_order: ReadingOrderResult
    scanned_tables: list[ExtractedTable]


def process_batch(
    ocr_service: OcrService,
    document_path: str,
    pages: list[int],
    dpi: int,
) -> list[BatchPageResult]:
    start = time.time()
    logger.info(
        "event=batch.start component=batch_processor"
        " pages=%d",
        len(pages),
    )

    zero_based = [p - 1 for p in pages]
    rendered_images = render_pdf_pages_batch(
        document_path,
        zero_based,
        dpi,
    )

    render_elapsed = time.time() - start
    logger.info(
        "event=batch.rendered component=batch_processor"
        " pages=%d durationMs=%d",
        len(pages),
        int(render_elapsed * 1000),
    )

    pdf = pdfplumber.open(document_path)

    try:
        results = _process_all_pages(
            ocr_service,
            document_path,
            pages,
            rendered_images,
            pdf,
        )
    finally:
        pdf.close()

    results.sort(key=lambda r: r.page_number)

    elapsed = time.time() - start
    logger.info(
        "event=batch.done component=batch_processor"
        " pages=%d durationMs=%d",
        len(pages),
        int(elapsed * 1000),
    )

    return results


def _process_all_pages(
    ocr_service: OcrService,
    document_path: str,
    pages: list[int],
    rendered_images: dict[int, bytes],
    pdf: pdfplumber.PDF,
) -> list[BatchPageResult]:
    with ThreadPoolExecutor(max_workers=BATCH_WORKERS) as pool:
        futures = {
            pool.submit(
                _process_page,
                ocr_service,
                document_path,
                page_num,
                rendered_images[page_num - 1],
                pdf.pages[page_num - 1],
            ): page_num
            for page_num in pages
        }

        results = []
        for future in as_completed(futures):
            results.append(future.result())

    return results


def _process_page(
    ocr_service: OcrService,
    document_path: str,
    page_number: int,
    image_bytes: bytes,
    pdfplumber_page,
) -> BatchPageResult:
    ocr_result = ocr_service.extract_page(image_bytes)

    scanned_tables = ocr_service._extract_scanned_tables(
        document_path,
        page_number,
        pdfplumber_page,
    )

    reading_order = resolve_reading_order(
        "",
        document_path,
        page_number,
        pdfplumber_page,
    )

    word_regions = [w.bbox for w in ocr_result.word_confidences]
    layout = detect_layout_from_regions(word_regions, scanned_tables)

    if reading_order.method == ReadingOrderMethod.OCR_TEXT_FLOW:
        reading_order = ReadingOrderResult(
            ordered_text=ocr_result.text,
            method=ReadingOrderMethod.OCR_TEXT_FLOW,
        )

    logger.info(
        "event=batch.page component=batch_processor"
        " page=%d confidence=%.2f words=%d",
        page_number,
        ocr_result.page_confidence,
        ocr_result.word_count,
    )

    return BatchPageResult(
        page_number=page_number,
        ocr_result=ocr_result,
        layout=layout,
        reading_order=reading_order,
        scanned_tables=scanned_tables,
    )
