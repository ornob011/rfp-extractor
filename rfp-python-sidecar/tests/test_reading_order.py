from unittest.mock import MagicMock, patch

from reading_order import ReadingOrderMethod, resolve_reading_order


def test_uses_pdfplumber_layout_when_document_context_exists():
    mock_page = MagicMock()
    mock_page.extract_text.return_value = "Left column\nRight column"
    mock_pdf = MagicMock()
    mock_pdf.pages = [mock_page]
    mock_context = MagicMock()
    mock_context.__enter__.return_value = mock_pdf
    mock_context.__exit__.return_value = False

    pdfplumber_module = MagicMock()
    pdfplumber_module.open.return_value = mock_context

    with patch("reading_order.import_module", return_value=pdfplumber_module):
        result = resolve_reading_order(
            "ocr text",
            "/tmp/sample.pdf",
            1,
        )

    assert result.ordered_text == "Left column\nRight column"
    assert result.method == ReadingOrderMethod.PDFPLUMBER_LAYOUT


def test_falls_back_to_ocr_text_without_document_context():
    result = resolve_reading_order(
        "ocr text",
        None,
        None,
    )

    assert result.ordered_text == "ocr text"
    assert result.method == ReadingOrderMethod.OCR_TEXT_FLOW
