import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

from pydantic import BaseModel

from image_utils import render_pdf_page_to_image
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

    with ThreadPoolExecutor(max_workers=BATCH_WORKERS) as pool:
        futures = {
            pool.submit(
                _process_page,
                ocr_service,
                document_path,
                page_num,
                dpi,
            ): page_num
            for page_num in pages
        }

        results = []
        for future in as_completed(futures):
            results.append(future.result())

    results.sort(key=lambda r: r.page_number)

    elapsed = time.time() - start
    logger.info(
        "event=batch.done component=batch_processor"
        " pages=%d durationMs=%d",
        len(pages),
        int(elapsed * 1000),
    )

    return results


def _process_page(
    ocr_service: OcrService,
    document_path: str,
    page_number: int,
    dpi: int,
) -> BatchPageResult:
    image_bytes = render_pdf_page_to_image(
        document_path,
        page_number - 1,
        dpi,
    )

    ocr_result = ocr_service.extract_page(image_bytes)

    scanned_tables = ocr_service._extract_scanned_tables(
        document_path,
        page_number,
    )

    reading_order = resolve_reading_order(
        "",
        document_path,
        page_number,
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
