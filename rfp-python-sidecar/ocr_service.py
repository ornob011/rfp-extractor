import logging
from concurrent.futures import ThreadPoolExecutor

import numpy as np
import pytesseract
from PIL import Image
from pydantic import BaseModel
from pytesseract import Output

from image_utils import bytes_to_pil
from layout_detector import (
    BoundingBox,
    LayoutDetectionResult,
    detect_layout_from_regions,
)
from reading_order import (
    ReadingOrderMethod,
    ReadingOrderResult,
    resolve_reading_order,
)
from sidecar_config import load_sidecar_config
from table_service import ExtractedTable, TableService

logger = logging.getLogger(__name__)
CONFIG = load_sidecar_config()


class OcrWord(BaseModel):
    text: str
    confidence: float
    bbox: BoundingBox


class OcrResult(BaseModel):
    text: str
    word_confidences: list[OcrWord]
    page_confidence: float
    word_count: int
    extraction_method: str


class OcrService:

    def __init__(
        self,
        reader: object,
        table_service: TableService,
    ) -> None:
        self._reader = reader
        self._table_service = table_service

    def extract_page(
        self,
        image_bytes: bytes,
        lang: str = "eng+ben",
    ) -> OcrResult:
        image = bytes_to_pil(image_bytes)
        result = self._extract_with_easyocr(image)

        if result.page_confidence >= CONFIG.ocr.confidence_threshold:
            return result

        logger.info(
            "event=ocr.fallback component=OcrService status=INFO"
            " easyocr_confidence=%.2f threshold=%.2f"
            " message=Falling back to Tesseract",
            result.page_confidence,
            CONFIG.ocr.confidence_threshold,
        )

        return self._extract_with_tesseract(image, lang)

    def extract_page_with_layout(
        self,
        image_bytes: bytes,
        document_path: str | None,
        page_number: int | None,
    ) -> tuple[OcrResult, LayoutDetectionResult, ReadingOrderResult, list[ExtractedTable]]:
        with ThreadPoolExecutor(max_workers=3) as pool:
            ocr_future = pool.submit(
                self.extract_page, image_bytes,
            )
            tables_future = pool.submit(
                self._extract_scanned_tables,
                document_path,
                page_number,
            )
            reading_order_future = pool.submit(
                resolve_reading_order,
                "",
                document_path,
                page_number,
            )

            ocr_result = ocr_future.result()
            scanned_tables = tables_future.result()
            reading_order = reading_order_future.result()

        word_regions = [
            w.bbox for w in ocr_result.word_confidences
        ]
        layout = detect_layout_from_regions(
            word_regions,
            scanned_tables,
        )

        if reading_order.method == ReadingOrderMethod.OCR_TEXT_FLOW:
            reading_order = ReadingOrderResult(
                ordered_text=ocr_result.text,
                method=ReadingOrderMethod.OCR_TEXT_FLOW,
            )

        return ocr_result, layout, reading_order, scanned_tables

    def _extract_scanned_tables(
        self,
        document_path: str | None,
        page_number: int | None,
    ) -> list[ExtractedTable]:
        match (document_path, page_number):
            case (str(path), int(page)):
                return self._table_service.extract_page_with_strategies(
                    path,
                    page,
                    CONFIG.scanned_tables.strategies,
                )
            case _:
                return []

    def _extract_with_easyocr(
        self,
        image: Image.Image,
    ) -> OcrResult:
        img_array = np.array(image)
        results = self._reader.readtext(img_array, detail=1)
        height, width = img_array.shape[:2]

        words = [
            OcrWord(
                text=text,
                confidence=float(conf),
                bbox=BoundingBox(
                    x=bbox[0][0] / width,
                    y=bbox[0][1] / height,
                    width=(bbox[1][0] - bbox[0][0]) / width,
                    height=(bbox[2][1] - bbox[0][1]) / height,
                ),
            )
            for bbox, text, conf in results
        ]

        joined_text = " ".join(word.text for word in words)
        mean_conf = self._mean_confidence(words)

        return OcrResult(
            text=joined_text,
            word_confidences=words,
            page_confidence=mean_conf,
            word_count=len(words),
            extraction_method="easyocr",
        )

    def _extract_with_tesseract(
        self,
        image: Image.Image,
        lang: str,
    ) -> OcrResult:
        data = pytesseract.image_to_data(
            image,
            lang=lang,
            output_type=Output.DICT,
        )

        height, width = np.array(image).shape[:2]
        words: list[OcrWord] = []

        for i in range(len(data["text"])):
            conf = int(data["conf"][i])
            text = data["text"][i].strip()

            if conf < CONFIG.ocr.tesseract_min_confidence or not text:
                continue

            words.append(
                OcrWord(
                    text=text,
                    confidence=conf / 100.0,
                    bbox=BoundingBox(
                        x=data["left"][i] / width,
                        y=data["top"][i] / height,
                        width=data["width"][i] / width,
                        height=data["height"][i] / height,
                    ),
                )
            )

        joined_text = " ".join(word.text for word in words)
        mean_conf = self._mean_confidence(words)

        return OcrResult(
            text=joined_text,
            word_confidences=words,
            page_confidence=mean_conf,
            word_count=len(words),
            extraction_method="tesseract",
        )

    @staticmethod
    def _mean_confidence(words: list[OcrWord]) -> float:
        match len(words):
            case 0:
                return 0.0
            case _:
                return sum(w.confidence for w in words) / len(words)
