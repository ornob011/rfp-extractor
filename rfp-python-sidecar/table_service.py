from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

import camelot


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
    grid: list[list[TableCell]]
    confidence: float
    method: str


class TableService:
    def extract_page(
        self,
        document_path: str,
        page_number: int,
        strategy: Literal["lattice", "stream"],
    ) -> list[ExtractedTable]:
        tables = camelot.read_pdf(
            filepath=document_path,
            pages=str(page_number),
            flavor=strategy,
        )

        return [self._to_extracted_table(table, strategy) for table in tables]

    def _to_extracted_table(
        self,
        table: camelot.core.Table,
        strategy: str,
    ) -> ExtractedTable:
        raw_grid = table.df.fillna("").values.tolist()
        headers = raw_grid[0] if raw_grid else []
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
            grid=mapped_grid,
            confidence=float(table.accuracy) / 100.0,
            method=strategy,
        )

