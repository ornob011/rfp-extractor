import { Badge } from '@/components/ui/badge';
import { ConfidenceBadge } from '@/components/ConfidenceBadge';
import type { TableCell, TableExtractionResult } from '@/types/table';

interface TableViewerProps {
    table: TableExtractionResult;
    highThreshold?: number;
    mediumThreshold?: number;
}

export function TableViewer({
    table,
    highThreshold,
    mediumThreshold,
}: TableViewerProps) {
    const sortedCells = sortCells(table.grid.flat());
    const columnCount = resolveColumnCount(table.headers.length, sortedCells);

    return (
        <div className="space-y-2 rounded-lg border bg-card p-4">
            <div className="flex flex-wrap items-center gap-2">
                {table.caption && (
                    <span className="text-sm font-semibold text-foreground">
                        {table.caption}
                    </span>
                )}
                <Badge variant="secondary" className="text-xs">
                    {table.type}
                </Badge>
                <Badge variant="outline" className="text-xs">
                    {table.confidence.method}
                </Badge>
                <ConfidenceBadge
                    score={table.confidence.score}
                    highThreshold={highThreshold}
                    mediumThreshold={mediumThreshold}
                />
                <span className="text-xs text-muted-foreground">
                    {formatPageRange(table.pageStart, table.pageEnd)}
                </span>
            </div>

            <div className="overflow-x-auto">
                <div
                    className="grid min-w-fit gap-px rounded border border-border bg-border text-sm"
                    style={{ gridTemplateColumns: `repeat(${columnCount}, minmax(9rem, 1fr))` }}
                >
                    {sortedCells.map((cell, index) => (
                        <div
                            key={resolveCellKey(cell, index)}
                            className={resolveCellClassName(cell)}
                            style={resolveCellPlacement(cell)}
                        >
                            {cell.value}
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

function formatPageRange(start: number, end: number): string {
    switch (start === end) {
        case true:
            return `Page ${start}`;
        default:
            return `Pages ${start}-${end}`;
    }
}

function resolveSpan(value: number): number | undefined {
    switch (value > 1) {
        case true:
            return value;
        default:
            return undefined;
    };
}

function resolveColumnCount(headerCount: number, cells: TableCell[]): number {
    const maxColumn = cells.reduce(
        (currentMax, cell) => Math.max(currentMax, cell.col + cell.colspan),
        0,
    );

    return Math.max(headerCount, maxColumn, 1);
}

function sortCells(cells: TableCell[]): TableCell[] {
    return cells
        .slice()
        .sort(
            (left: TableCell, right: TableCell) =>
                left.row - right.row || left.col - right.col,
        );
}

function resolveCellClassName(cell: TableCell): string {
    const baseClass = 'bg-background px-3 py-1.5 text-muted-foreground';
    const headerClass = 'bg-muted font-medium text-foreground';

    switch (cell.isHeader) {
        case true:
            return `${baseClass} ${headerClass}`;
        default:
            return baseClass;
    }
}

function resolveCellPlacement(cell: TableCell) {
    return {
        gridRow: `${cell.row + 1} / span ${resolveSpan(cell.rowspan) ?? 1}`,
        gridColumn: `${cell.col + 1} / span ${resolveSpan(cell.colspan) ?? 1}`,
    };
}

function resolveCellKey(cell: TableCell, index: number): string {
    return `${cell.row}-${cell.col}-${cell.value}-${index}`;
}
