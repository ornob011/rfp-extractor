class PageOcrResult:
    def __init__(self, page_number: int, text: str, confidence: float) -> None:
        self.page_number = page_number
        self.text = text
        self.confidence = confidence


class OcrService:
    def extract_page(self, page_image_bytes: bytes, page_number: int) -> PageOcrResult:
        raise NotImplementedError("OCR not yet implemented, coming Sprint 6")

    def extract_pages_batch(self, pdf_bytes: bytes) -> list[PageOcrResult]:
        raise NotImplementedError("OCR not yet implemented, coming Sprint 6")
