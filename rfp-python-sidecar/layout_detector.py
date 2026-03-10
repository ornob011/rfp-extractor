from collections.abc import Iterable

from PIL import Image
from pydantic import BaseModel
from shapely import ops
from shapely.geometry import Polygon, box

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
    reader: object | None = None,
) -> LayoutDetectionResult:
    resolved_reader = reader or _create_reader()
    word_regions = _word_regions(
        resolved_reader.readtext(
            _to_array(image),
            detail=1,
        ),
        image.width,
        image.height,
    )
    table_regions = [
        _from_table_bbox(table.bbox)
        for table in scanned_tables
        if table.bbox is not None
    ]
    text_regions = _non_table_text_regions(
        word_regions,
        table_regions,
    )

    return LayoutDetectionResult(
        has_table=bool(table_regions),
        table_regions=table_regions,
        text_regions=text_regions,
    )


def _create_reader() -> object:
    import easyocr

    return easyocr.Reader(["en", "bn"], gpu=True)


def _to_array(image: Image.Image):
    import numpy as np

    return np.array(image)


def _word_regions(
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


def _non_table_text_regions(
    word_regions: list[BoundingBox],
    table_regions: list[BoundingBox],
) -> list[BoundingBox]:
    word_polygons = list(
        map(
            _to_polygon,
            word_regions,
        )
    )
    table_polygons = list(
        map(
            _to_polygon,
            table_regions,
        )
    )
    residual_words = [
        region
        for region, polygon in zip(
            word_regions,
            word_polygons,
            strict=False,
        )
        if not _intersects_any_table(
            polygon,
            table_polygons,
        )
    ]
    residual_polygons = list(
        map(
            _to_polygon,
            residual_words,
        )
    )

    return _to_regions(
        residual_polygons,
    )


def _intersects_any_table(
    polygon: Polygon,
    table_polygons: list[Polygon],
) -> bool:
    return any(
        polygon.intersects(table_polygon)
        for table_polygon in table_polygons
    )


def _to_regions(
    polygons: list[Polygon],
) -> list[BoundingBox]:
    if not polygons:
        return []

    merged = _merged_geometry(polygons)

    return list(
        map(
            _geometry_to_bbox,
            _geometries(merged),
        )
    )


def _merged_geometry(
    polygons: list[Polygon],
):
    return ops.unary_union(polygons)


def _geometries(
    geometry,
) -> Iterable:
    return getattr(
        geometry,
        "geoms",
        [geometry],
    )


def _geometry_to_bbox(
    geometry,
) -> BoundingBox:
    min_x, min_y, max_x, max_y = geometry.bounds

    return BoundingBox(
        x=min_x,
        y=min_y,
        width=max_x - min_x,
        height=max_y - min_y,
    )


def _to_polygon(
    bbox: BoundingBox,
) -> Polygon:
    return box(
        bbox.x,
        bbox.y,
        bbox.x + bbox.width,
        bbox.y + bbox.height,
    )
