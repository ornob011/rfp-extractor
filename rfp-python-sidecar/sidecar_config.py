from functools import lru_cache
from pathlib import Path

import yaml
from pydantic import BaseModel


class RenderConfigModel(BaseModel):
    max_dpi: int


class OcrConfigModel(BaseModel):
    confidence_threshold: float
    tesseract_min_confidence: int


class LayoutConfigModel(BaseModel):
    detector: str


class ReadingOrderConfigModel(BaseModel):
    pdf_layout: bool


class ScannedTablesConfigModel(BaseModel):
    strategies: list[str]


class SidecarConfigModel(BaseModel):
    version: int
    render: RenderConfigModel
    ocr: OcrConfigModel
    layout: LayoutConfigModel
    reading_order: ReadingOrderConfigModel
    scanned_tables: ScannedTablesConfigModel


@lru_cache
def load_sidecar_config() -> SidecarConfigModel:
    config_path = Path(__file__).with_name("ocr-config-v1.yml")
    payload = yaml.safe_load(config_path.read_text(encoding="utf-8"))

    return SidecarConfigModel.model_validate(payload)
