import io
import logging

from pdf2image import convert_from_path
from PIL import Image

from sidecar_config import load_sidecar_config

logger = logging.getLogger(__name__)
CONFIG = load_sidecar_config()


def render_pdf_page_to_image(
    pdf_path: str,
    page_num: int,
    dpi: int = 300,
) -> bytes:
    """
    Render a single PDF page to PNG bytes.
    page_num is 0-based; pdf2image uses 1-based first_page/last_page.
    Raises FileNotFoundError if pdf_path does not exist.
    Raises IndexError if page_num >= document page count.
    """
    effective_dpi = min(dpi, CONFIG.render.max_dpi)

    if dpi > CONFIG.render.max_dpi:
        logger.warning(
            "event=dpi.capped component=image_utils status=WARN"
            " requested_dpi=%d effective_dpi=%d",
            dpi,
            effective_dpi,
        )

    pages = convert_from_path(
        pdf_path,
        dpi=effective_dpi,
        first_page=page_num + 1,
        last_page=page_num + 1,
    )

    if not pages:
        raise IndexError(f"Page {page_num} not found in {pdf_path}")

    buf = io.BytesIO()
    pages[0].save(buf, format="PNG")
    return buf.getvalue()


def bytes_to_pil(image_bytes: bytes) -> Image.Image:
    """
    Decode PNG/JPEG bytes to PIL Image in RGB mode.
    Raises ValueError if bytes are not a valid image.
    """
    return Image.open(io.BytesIO(image_bytes)).convert("RGB")
