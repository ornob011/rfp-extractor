import base64
from unittest.mock import MagicMock

import pytest
from fastapi import HTTPException
from pydantic import ValidationError

from layout_detector import LayoutDetectionResult
from main import OcrRequest, app, health, ocr_page, ocr_page_with_layout
from ocr_service import OcrResult, OcrService
from reading_order import ReadingOrderMethod, ReadingOrderResult
from table_service import ExtractedTable, TableBoundingBox


@pytest.fixture
def mock_ocr_service() -> OcrService:
    reader = MagicMock()
    reader.readtext.return_value = []
    return OcrService(reader, MagicMock())


@pytest.mark.anyio
async def test_health_returns_200():
    response = await health()

    assert response.status == "ok"
    assert response.version == "1.0.0"
    assert response.ocr_engine == "easyocr+tesseract"


def test_ocr_page_rejects_invalid_base64():
    with pytest.raises(ValidationError):
        OcrRequest(image_base64="!!!invalid!!!")


@pytest.mark.anyio
async def test_ocr_page_returns_503_when_service_not_ready():
    app.state.ocr_service = None

    with pytest.raises(HTTPException) as exception:
        await ocr_page(
            OcrRequest(image_base64=base64.b64encode(b"test").decode("utf-8"))
        )

    assert exception.value.status_code == 503
    assert exception.value.detail == "OCR service not available"


@pytest.mark.anyio
async def test_ocr_page_returns_result_for_valid_image(
    small_png_bytes,
    mock_ocr_service,
):
    mock_ocr_service._reader.readtext.return_value = [
        ([[10, 10], [100, 10], [100, 30], [10, 30]], "Hello", 0.9),
    ]
    app.state.ocr_service = mock_ocr_service

    response = await ocr_page(
        OcrRequest(
            image_base64=base64.b64encode(small_png_bytes).decode("utf-8")
        )
    )

    assert isinstance(response, OcrResult)
    assert response.text == "Hello"
    assert response.word_count == 1
    assert response.extraction_method == "easyocr"


@pytest.mark.anyio
async def test_ocr_page_with_layout_returns_reading_order_and_scanned_tables(
    small_png_bytes,
    mock_ocr_service,
):
    mock_ocr_service.extract_page_with_layout = MagicMock(
        return_value=(
            OcrResult(
                text="Hello",
                word_confidences=[],
                page_confidence=0.9,
                word_count=1,
                extraction_method="easyocr",
            ),
            LayoutDetectionResult(
                has_table=False,
                table_regions=[],
                text_regions=[],
            ),
            ReadingOrderResult(
                ordered_text="Ordered",
                method=ReadingOrderMethod.OCR_TEXT_FLOW,
            ),
            [
                ExtractedTable(
                    caption=None,
                    headers=["A", "B"],
                    rows=[["1", "2"]],
                    grid=[],
                    confidence=0.8,
                    method="lattice",
                    bbox=TableBoundingBox(0.1, 0.2, 0.4, 0.3),
                )
            ],
        )
    )
    app.state.ocr_service = mock_ocr_service

    response = await ocr_page_with_layout(
        OcrRequest(
            image_base64=base64.b64encode(small_png_bytes).decode("utf-8"),
            document_path="/tmp/sample.pdf",
            page_number=1,
        )
    )

    assert response.reading_order.ordered_text == "Ordered"
    assert response.reading_order.method == ReadingOrderMethod.OCR_TEXT_FLOW
    assert len(response.scanned_tables) == 1
