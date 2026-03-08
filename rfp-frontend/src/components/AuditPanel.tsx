import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table';
import type { RepairEvent } from '@/types/rfp';
import { ChevronDown, ChevronRight } from 'lucide-react';

interface AuditPanelProps {
    repairEvents: RepairEvent[];
    totalRepairIterations: number;
}

function ResultBadge({ result }: { result: RepairEvent['result'] }) {
    switch (result) {
        case 'IMPROVED':
            return (
                <Badge variant="outline" className="text-green-700 border-green-300">
                    IMPROVED
                </Badge>
            );
        case 'NOT_IMPROVED':
            return (
                <Badge variant="outline" className="text-orange-700 border-orange-300">
                    NOT IMPROVED
                </Badge>
            );
        case 'MAX_RETRIES':
            return <Badge variant="destructive">MAX RETRIES</Badge>;
    }
}

export function AuditPanel({ repairEvents, totalRepairIterations }: AuditPanelProps) {
    const [open, setOpen] = useState(false);

    if (repairEvents.length === 0 && totalRepairIterations === 0) {
        return null;
    }

    return (
        <div className="space-y-2">
            <Button
                variant="ghost"
                size="sm"
                onClick={() => setOpen(!open)}
                className="w-full justify-start"
            >
                {open ? (
                    <ChevronDown className="mr-2 h-4 w-4" />
                ) : (
                    <ChevronRight className="mr-2 h-4 w-4" />
                )}
                Show Repair Audit ({repairEvents.length} events)
            </Button>

            {open && (
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHead>Component</TableHead>
                            <TableHead>Attempt</TableHead>
                            <TableHead>Strategy</TableHead>
                            <TableHead>Result</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {repairEvents.map((event, index) => (
                            <TableRow key={`${event.componentId}-${index}`}>
                                <TableCell className="font-mono text-xs">
                                    {event.componentId}
                                </TableCell>
                                <TableCell>{event.attempt}</TableCell>
                                <TableCell className="text-xs">
                                    {event.strategy}
                                </TableCell>
                                <TableCell>
                                    <ResultBadge result={event.result} />
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            )}
        </div>
    );
}
