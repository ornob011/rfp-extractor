import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { listRulePacks, reloadRulePacks } from '@/api/rfpClient';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Alert, AlertDescription } from '@/components/ui/alert';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table';
import { RefreshCw } from 'lucide-react';
import { toast } from 'sonner';
import type { RfpType } from '@/types/rfpType';

const RFP_TYPE_VARIANTS: Record<RfpType, 'default' | 'secondary' | 'outline'> = {
    ICT: 'default',
    WORKS: 'secondary',
    CONSULTANCY: 'outline',
    GOODS: 'secondary',
    UNKNOWN: 'outline',
};

const RELOAD_ICON_STATE_CLASS: Record<'true' | 'false', string> = {
    true: 'animate-spin',
    false: '',
};

export function AdminPage() {
    const queryClient = useQueryClient();

    const { data: packs, isLoading, error } = useQuery({
        queryKey: ['rulePacks'],
        queryFn: listRulePacks,
    });

    const reloadMutation = useMutation({
        mutationFn: reloadRulePacks,
        onSuccess: (result) => {
            queryClient.invalidateQueries({ queryKey: ['rulePacks'] });
            toast.success(`Reloaded ${result.reloadedPacks.length} rule packs`);
        },
        onError: () => {
            toast.error('Failed to reload rule packs');
        },
    });
    const iconClassName = reloadIconClassName(reloadMutation.isPending);

    if (isLoading) {
        return (
            <div className="space-y-4 max-w-4xl">
                <Skeleton className="h-8 w-48" />
                <Skeleton className="h-64 w-full" />
            </div>
        );
    }

    if (error) {
        return (
            <div className="max-w-4xl">
                <Alert variant="destructive">
                    <AlertDescription>
                        {resolveErrorMessage(error)}
                    </AlertDescription>
                </Alert>
            </div>
        );
    }

    return (
        <div className="max-w-4xl space-y-6">
            <div className="flex items-center justify-between">
                <h1 className="text-xl font-bold text-foreground">Rule Pack Management</h1>
                <Button
                    onClick={() => reloadMutation.mutate()}
                    disabled={reloadMutation.isPending}
                    variant="outline"
                    size="sm"
                >
                    <RefreshCw className={iconClassName} />
                    Reload All
                </Button>
            </div>

            <div className="rounded-lg border bg-card">
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHead>Pack ID</TableHead>
                            <TableHead>Version</TableHead>
                            <TableHead>RFP Type</TableHead>
                            <TableHead className="text-right">Rules</TableHead>
                            <TableHead>Last Loaded</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        <RulePackTableRows packs={packs} />
                    </TableBody>
                </Table>
            </div>
        </div>
    );
}

function RulePackTableRows(
    { packs }: { packs: Awaited<ReturnType<typeof listRulePacks>> | undefined },
) {
    if (!packs || packs.length === 0) {
        return (
            <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground italic">
                    No rule packs loaded.
                </TableCell>
            </TableRow>
        );
    }

    return packs.map((pack) => (
        <TableRow key={pack.packId}>
            <TableCell className="font-mono text-sm">{pack.packId}</TableCell>
            <TableCell>{pack.version}</TableCell>
            <TableCell>
                <RfpTypeBadge rfpType={pack.rfpType} />
            </TableCell>
            <TableCell className="text-right">{pack.ruleCount}</TableCell>
            <TableCell className="text-sm text-muted-foreground">
                {formatTimestamp(pack.lastLoadedAt)}
            </TableCell>
        </TableRow>
    ));
}

function RfpTypeBadge({ rfpType }: { rfpType: RfpType }) {
    const variant = RFP_TYPE_VARIANTS[rfpType];

    return <Badge variant={variant}>{rfpType}</Badge>;
}

function formatTimestamp(iso: string): string {
    return new Date(iso).toLocaleString();
}

function resolveErrorMessage(error: unknown): string {
    if (error instanceof Error) {
        return error.message;
    }

    return 'Failed to load rule packs';
}

function reloadIconClassName(isPending: boolean): string {
    return [
        'h-4',
        'w-4',
        'mr-2',
        RELOAD_ICON_STATE_CLASS[String(isPending) as 'true' | 'false'],
    ].join(' ').trim();
}
