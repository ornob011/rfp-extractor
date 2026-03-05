export interface TableCell {
    row: number;
    col: number;
    value: string;
    rowspan: number;
    colspan: number;
    isHeader: boolean;
}

export interface ExtractionConfidence {
    score: number;
    method: string;
}

export type TableProvenance = 'DIGITAL' | 'SCANNED' | 'MIXED';

export type TableTypeName =
    | 'DELIVERABLES'
    | 'EVALUATION'
    | 'PAYMENT'
    | 'STAFFING'
    | 'SCHEDULE'
    | 'OTHER';

export interface TableExtractionResult {
    tableId: string;
    sectionId?: string;
    clauseId?: string;
    pageStart: number;
    pageEnd: number;
    provenance: TableProvenance;
    caption?: string;
    type: TableTypeName;
    headers: string[];
    grid: TableCell[][];
    confidence: ExtractionConfidence;
}
