from unittest.mock import MagicMock, patch

from ocr_service import OcrService
from reading_order import ReadingOrderMethod, ReadingOrderResult
from table_service import ExtractedTable, TableBoundingBox


def test_uses_easyocr_when_confidence_high(
    small_png_bytes,
    mock_easyocr_reader,
):
    mock_easyocr_reader.readtext.return_value = [
        ([[10, 10], [100, 10], [100, 30], [10, 30]], "Hello", 0.95),
        ([[10, 40], [100, 40], [100, 60], [10, 60]], "World", 0.90),
    ]

    service = OcrService(mock_easyocr_reader, MagicMock())
    result = service.extract_page(small_png_bytes)

    assert result.extraction_method == "easyocr"
    assert result.page_confidence > 0.5
    assert result.word_count == 2


def test_falls_back_to_tesseract_when_easyocr_confidence_low(
    small_png_bytes,
    mock_easyocr_reader,
):
    mock_easyocr_reader.readtext.return_value = [
        ([[10, 10], [100, 10], [100, 30], [10, 30]], "x", 0.2),
        ([[10, 40], [100, 40], [100, 60], [10, 60]], "y", 0.1),
    ]

    tesseract_data = {
        "text": ["Hello", "World", ""],
        "conf": [90, 85, -1],
        "left": [10, 10, 0],
        "top": [10, 40, 0],
        "width": [90, 90, 0],
        "height": [20, 20, 0],
    }

    service = OcrService(mock_easyocr_reader, MagicMock())

    with patch("ocr_service.pytesseract.image_to_data", return_value=tesseract_data):
        result = service.extract_page(small_png_bytes)

    assert result.extraction_method == "tesseract"
    assert result.word_count == 2


def test_extract_page_with_layout_returns_reading_order_and_tables(
    small_png_bytes,
    mock_easyocr_reader,
):
    table_service = MagicMock()
    table_service.extract_page_with_strategies.return_value = [
        ExtractedTable(
            caption=None,
            headers=["Col1", "Col2"],
            rows=[["A", "B"]],
            grid=[],
            confidence=0.8,
            method="lattice",
            bbox=TableBoundingBox(0.1, 0.2, 0.4, 0.3),
        )
    ]
    mock_easyocr_reader.readtext.return_value = [
        ([[10, 10], [100, 10], [100, 30], [10, 30]], "Test", 0.9),
    ]

    service = OcrService(mock_easyocr_reader, table_service)

    with patch("ocr_service.resolve_reading_order") as resolve_reading_order:
        resolve_reading_order.return_value = ReadingOrderResult(
            ordered_text="ordered text",
            method=ReadingOrderMethod.OCR_TEXT_FLOW,
        )

        ocr_result, layout, reading_order, scanned_tables = service.extract_page_with_layout(
            small_png_bytes,
            "/tmp/sample.pdf",
            1,
        )

    assert ocr_result is not None
    assert layout.has_table is True
    assert reading_order.ordered_text == "ordered text"
    assert len(scanned_tables) == 1
