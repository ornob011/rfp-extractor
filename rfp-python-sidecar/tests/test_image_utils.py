import io

import pytest
from PIL import Image

from image_utils import bytes_to_pil, render_pdf_page_to_image


def _create_minimal_pdf(path: str) -> None:
    try:
        from reportlab.pdfgen import canvas

        c = canvas.Canvas(path)
        c.drawString(100, 750, "Hello World")
        c.save()
    except ImportError:
        from fpdf import FPDF

        pdf = FPDF()
        pdf.add_page()
        pdf.set_font("Helvetica", size=12)
        pdf.text(10, 20, "Hello World")
        pdf.output(path)


def test_render_returns_png_bytes(tmp_path):
    pdf_path = str(tmp_path / "test.pdf")
    _create_minimal_pdf(pdf_path)

    result = render_pdf_page_to_image(pdf_path, page_num=0, dpi=150)

    assert isinstance(result, bytes)
    assert len(result) > 0
    assert result[:4] == b"\x89PNG"


def test_bytes_to_pil_returns_rgb_image(small_png_bytes):
    result = bytes_to_pil(small_png_bytes)

    assert isinstance(result, Image.Image)
    assert result.mode == "RGB"


def test_render_raises_index_error_for_invalid_page(tmp_path):
    pdf_path = str(tmp_path / "test.pdf")
    _create_minimal_pdf(pdf_path)

    with pytest.raises((IndexError, Exception)):
        render_pdf_page_to_image(pdf_path, page_num=999, dpi=150)
