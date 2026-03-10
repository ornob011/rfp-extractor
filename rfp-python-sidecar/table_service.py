from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from importlib import import_module
from typing import Literal

import camelot


@dataclass(frozen=True)
class TableBoundingBox:
    x: float
    y: float
    width: float
    height: float


@dataclass(frozen=True)
class TableCell:
    row: int
    col: int
    value: str
    rowspan: int
    colspan: int
    is_header: bool


@dataclass(frozen=True)
class ExtractedTable:
    caption: str | None
    headers: list[str]
    rows: list[list[str]]
    grid: list[list[TableCell]]
    confidence: float
    method: str
    bbox: TableBoundingBox | None


class TableService:
    def extract_page(
        self,
        document_path: str,
        page_number: int,
        strategy: Literal["lattice", "stream"],
        pdfplumber_page=None,
    ) -> list[ExtractedTable]:
        tables = camelot.read_pdf(
            filepath=document_path,
            pages=str(page_number),
            flavor=strategy,
        )

        return [
            self._to_extracted_table(
                table,
                strategy,
                document_path,
                page_number,
                pdfplumber_page,
            )
            for table in tables
        ]

    def extract_page_with_strategies(
        self,
        document_path: str,
        page_number: int,
        strategies: list[str],
        pdfplumber_page=None,
    ) -> list[ExtractedTable]:
        with ThreadPoolExecutor(max_workers=len(strategies)) as pool:
            futures = [
                pool.submit(
                    self.extract_page,
                    document_path,
                    page_number,
                    strategy,
                    pdfplumber_page,
                )
                for strategy in strategies
            ]
            results = [f.result() for f in futures]

        return next(
            (tables for tables in results if tables),
            [],
        )

    def _to_extracted_table(
        self,
        table: camelot.core.Table,
        strategy: str,
        document_path: str,
        page_number: int,
        pdfplumber_page=None,
    ) -> ExtractedTable:
        raw_grid = table.df.fillna("").values.tolist()
        headers = raw_grid[0] if raw_grid else []
        rows = raw_grid[1:] if raw_grid else []
        mapped_grid = [
            [
                TableCell(
                    row=row_index,
                    col=column_index,
                    value=str(cell_value).strip(),
                    rowspan=1,
                    colspan=1,
                    is_header=row_index == 0,
                )
                for column_index, cell_value in enumerate(row)
            ]
            for row_index, row in enumerate(raw_grid)
        ]

        return ExtractedTable(
            caption=None,
            headers=[str(value).strip() for value in headers],
            rows=[[str(value).strip() for value in row] for row in rows],
            grid=mapped_grid,
            confidence=float(table.accuracy) / 100.0,
            method=strategy,
            bbox=self._resolve_bbox(
                table,
                document_path,
                page_number,
                pdfplumber_page,
            ),
        )

    def _resolve_bbox(
        self,
        table: camelot.core.Table,
        document_path: str,
        page_number: int,
        pdfplumber_page=None,
    ) -> TableBoundingBox | None:
        bbox = getattr(table, "_bbox", None)

        if bbox is None:
            return None

        if pdfplumber_page is not None:
            page_width = float(pdfplumber_page.width)
            page_height = float(pdfplumber_page.height)
        else:
            pdfplumber = import_module("pdfplumber")
            with pdfplumber.open(document_path) as pdf:
                page = pdf.pages[page_number - 1]
                page_width = float(page.width)
                page_height = float(page.height)

        x0, y0, x1, y1 = bbox

        return TableBoundingBox(
            x=x0 / page_width,
            y=(page_height - y1) / page_height,
            width=(x1 - x0) / page_width,
            height=(y1 - y0) / page_height,
        )
