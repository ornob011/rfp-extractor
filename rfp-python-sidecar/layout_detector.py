from PIL import Image
from pydantic import BaseModel

from table_service import ExtractedTable, TableBoundingBox


class BoundingBox(BaseModel):
    x: float
    y: float
    width: float
    height: float


class LayoutDetectionResult(BaseModel):
    has_table: bool
    table_regions: list[BoundingBox]
    text_regions: list[BoundingBox]


def detect_layout(
    image: Image.Image,
    scanned_tables: list[ExtractedTable],
    reader: object = None,
) -> LayoutDetectionResult:
    """
    Detect table and text regions in an image using sidecar table extraction
    for table regions and OCR word boxes for residual text regions.
    """
    resolved_reader = reader or _create_reader()
    normalized_boxes = _normalize_boxes(
        resolved_reader.readtext(_to_array(image), detail=1),
        image.width,
        image.height,
    )
    table_regions = [
        _from_table_bbox(table.bbox)
        for table in scanned_tables
        if table.bbox is not None
    ]
    text_regions = _text_regions(
        normalized_boxes,
        table_regions,
    )

    return LayoutDetectionResult(
        has_table=bool(table_regions),
        table_regions=table_regions,
        text_regions=text_regions,
    )


def _create_reader() -> object:
    import easyocr

    return easyocr.Reader(["en", "bn"], gpu=False)


def _to_array(image: Image.Image):
    import numpy as np

    return np.array(image)


def _normalize_boxes(
    results: list,
    width: int,
    height: int,
) -> list[BoundingBox]:
    return [
        BoundingBox(
            x=bbox[0][0] / width,
            y=bbox[0][1] / height,
            width=(bbox[1][0] - bbox[0][0]) / width,
            height=(bbox[2][1] - bbox[0][1]) / height,
        )
        for bbox, _text, _conf in results
    ]


def _from_table_bbox(
    bbox: TableBoundingBox,
) -> BoundingBox:
    return BoundingBox(
        x=bbox.x,
        y=bbox.y,
        width=bbox.width,
        height=bbox.height,
    )


def _text_regions(
    boxes: list[BoundingBox],
    table_regions: list[BoundingBox],
) -> list[BoundingBox]:
    if not boxes:
        return []

    if table_regions:
        return []

    return [_bounding_region(boxes)]


def _bounding_region(
    boxes: list[BoundingBox],
) -> BoundingBox:
    min_x = min(box.x for box in boxes)
    min_y = min(box.y for box in boxes)
    max_x = max(box.x + box.width for box in boxes)
    max_y = max(box.y + box.height for box in boxes)

    return BoundingBox(
        x=min_x,
        y=min_y,
        width=max_x - min_x,
        height=max_y - min_y,
    )
