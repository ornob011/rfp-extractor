import io
from unittest.mock import MagicMock

import pytest
from PIL import Image


@pytest.fixture
def small_png_bytes() -> bytes:
    img = Image.new("RGB", (10, 10), color="white")
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


@pytest.fixture
def mock_easyocr_reader() -> MagicMock:
    reader = MagicMock()
    reader.readtext.return_value = []
    return reader
