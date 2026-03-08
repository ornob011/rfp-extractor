from enum import Enum
from importlib import import_module
from pathlib import Path

from pydantic import BaseModel


class ReadingOrderMethod(str, Enum):
    PDFPLUMBER_LAYOUT = "pdfplumber_layout"
    OCR_TEXT_FLOW = "ocr_text_flow"


class ReadingOrderResult(BaseModel):
    ordered_text: str
    method: ReadingOrderMethod


def resolve_reading_order(
    ocr_text: str,
    document_path: str | None,
    page_number: int | None,
) -> ReadingOrderResult:
    pdf_text = _extract_pdf_text(
        document_path,
        page_number,
    )

    if pdf_text.strip():
        return ReadingOrderResult(
            ordered_text=pdf_text,
            method=ReadingOrderMethod.PDFPLUMBER_LAYOUT,
        )

    return ReadingOrderResult(
        ordered_text=ocr_text,
        method=ReadingOrderMethod.OCR_TEXT_FLOW,
    )


def _extract_pdf_text(
    document_path: str | None,
    page_number: int | None,
) -> str:
    if document_path is None:
        return ""

    if page_number is None:
        return ""

    pdfplumber = import_module("pdfplumber")

    with pdfplumber.open(Path(document_path)) as pdf:
        page = pdf.pages[page_number - 1]
        extracted_text = page.extract_text(layout=True)

    return extracted_text or ""
