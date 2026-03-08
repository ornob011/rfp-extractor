import { Badge } from '@/components/ui/badge';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table';
import type { PageClassification, PageDetail } from '@/types/rfp';

interface PageSummaryTabProps {
    pageDetails: PageDetail[];
}

const CLASSIFICATION_STYLES: Record<PageClassification, string> = {
    DIGITAL: 'text-green-700 border-green-300',
    SCANNED: 'text-red-700 border-red-300',
    MIXED: 'text-yellow-700 border-yellow-300',
};

function resolveConfidenceColor(confidence: number): string {
    if (confidence >= 0.8) return 'bg-green-500';
    if (confidence >= 0.5) return 'bg-yellow-500';
    return 'bg-red-500';
}

function ClassificationBadge({ classification }: { classification: PageClassification }) {
    return (
        <Badge variant="outline" className={CLASSIFICATION_STYLES[classification]}>
            {classification}
        </Badge>
    );
}

function ConfidenceBar({ confidence }: { confidence: number }) {
    const percent = Math.round(confidence * 100);
    const color = resolveConfidenceColor(confidence);

    return (
        <div className="flex items-center gap-2">
            <div className="h-1.5 w-24 rounded-full bg-muted overflow-hidden">
                <div
                    className={`h-full rounded-full ${color}`}
                    style={{ width: `${percent}%` }}
                />
            </div>

            <span className="text-xs text-muted-foreground">{percent}%</span>
        </div>
    );
}

export function PageSummaryTab({ pageDetails }: PageSummaryTabProps) {
    if (pageDetails.length === 0) {
        return (
            <p className="text-sm text-muted-foreground py-4">
                No page details available.
            </p>
        );
    }

    return (
        <Table>
            <TableHeader>
                <TableRow>
                    <TableHead className="w-20">Page</TableHead>
                    <TableHead className="w-28">Type</TableHead>
                    <TableHead className="w-36">Method</TableHead>
                    <TableHead>Confidence</TableHead>
                </TableRow>
            </TableHeader>

            <TableBody>
                {pageDetails.map((page) => (
                    <TableRow key={page.pageNum}>
                        <TableCell className="font-medium">
                            {page.pageNum}
                        </TableCell>

                        <TableCell>
                            <ClassificationBadge classification={page.classification} />
                        </TableCell>

                        <TableCell className="text-xs text-muted-foreground">
                            {page.extractionMethod}
                        </TableCell>

                        <TableCell>
                            <ConfidenceBar confidence={page.confidence} />
                        </TableCell>
                    </TableRow>
                ))}
            </TableBody>
        </Table>
    );
}
