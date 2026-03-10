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


def render_pdf_pages_batch(
    pdf_path: str,
    page_nums: list[int],
    dpi: int = 300,
) -> dict[int, bytes]:
    """
    Render multiple PDF pages in a single poppler invocation.
    page_nums are 0-based.
    Returns dict mapping 0-based page_num -> PNG bytes.
    """
    if not page_nums:
        return {}

    effective_dpi = min(dpi, CONFIG.render.max_dpi)
    sorted_nums = sorted(set(page_nums))
    min_page = sorted_nums[0]
    max_page = sorted_nums[-1]

    logger.info(
        "event=batch.render component=image_utils"
        " pages=%d range=%d-%d dpi=%d",
        len(sorted_nums),
        min_page,
        max_page,
        effective_dpi,
    )

    rendered = convert_from_path(
        pdf_path,
        dpi=effective_dpi,
        first_page=min_page + 1,
        last_page=max_page + 1,
        thread_count=4,
    )

    page_set = set(page_nums)
    result: dict[int, bytes] = {}

    for i, page_idx in enumerate(range(min_page, max_page + 1)):
        if page_idx not in page_set:
            continue

        buf = io.BytesIO()
        rendered[i].save(buf, format="PNG")
        result[page_idx] = buf.getvalue()

    return result


def bytes_to_pil(image_bytes: bytes) -> Image.Image:
    """
    Decode PNG/JPEG bytes to PIL Image in RGB mode.
    Raises ValueError if bytes are not a valid image.
    """
    return Image.open(io.BytesIO(image_bytes)).convert("RGB")
