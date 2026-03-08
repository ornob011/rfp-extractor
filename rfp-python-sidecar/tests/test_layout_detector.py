from unittest.mock import MagicMock

from PIL import Image

from layout_detector import detect_layout
from table_service import ExtractedTable, TableBoundingBox


def test_detects_table_from_sidecar_results():
    reader = MagicMock()
    reader.readtext.return_value = [
        ([[10, 10], [50, 10], [50, 30], [10, 30]], "A", 0.9),
    ]
    scanned_tables = [
        ExtractedTable(
            caption=None,
            headers=["A", "B"],
            rows=[["1", "2"]],
            grid=[],
            confidence=0.9,
            method="lattice",
            bbox=TableBoundingBox(0.1, 0.2, 0.4, 0.3),
        )
    ]

    image = Image.new("RGB", (300, 200), color="white")
    result = detect_layout(
        image,
        scanned_tables,
        reader,
    )

    assert result.has_table is True
    assert len(result.table_regions) == 1
    assert len(result.text_regions) == 1


def test_no_table_for_prose_text_without_sidecar_tables():
    reader = MagicMock()
    reader.readtext.return_value = [
        ([[10, 10], [290, 10], [290, 30], [10, 30]], "This is a paragraph", 0.9),
        ([[10, 40], [290, 40], [290, 60], [10, 60]], "Another line of text", 0.9),
    ]

    image = Image.new("RGB", (300, 200), color="white")
    result = detect_layout(
        image,
        [],
        reader,
    )

    assert result.has_table is False
    assert len(result.table_regions) == 0
    assert len(result.text_regions) == 2
