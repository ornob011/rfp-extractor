import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getRfpResult } from '@/api/rfpClient';
import { SectionTree } from '@/components/SectionTree';
import { EntityTable } from '@/components/EntityTable';
import { Skeleton } from '@/components/ui/skeleton';
import { Alert, AlertDescription } from '@/components/ui/alert';

export function ResultPage() {
    const { jobId } = useParams<{ jobId: string }>();

    const { data: result, isLoading, error } = useQuery({
        queryKey: ['rfpResult', jobId],
        queryFn: () => getRfpResult(jobId!),
        enabled: !!jobId,
    });

    if (isLoading) {
        return (
            <div className="space-y-4 max-w-5xl">
                <Skeleton className="h-8 w-48" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-64 w-full" />
            </div>
        );
    }

    if (error) {
        return (
            <div className="max-w-5xl space-y-4">
                <Alert variant="destructive">
                    <AlertDescription>
                        {resolveErrorMessage(error)}
                    </AlertDescription>
                </Alert>
                <Link to="/jobs" className="text-sm text-primary hover:underline">
                    Back to Jobs
                </Link>
            </div>
        );
    }

    if (!result) {
        return <p className="text-muted-foreground">No result data found.</p>;
    }

    return (
        <div className="max-w-5xl space-y-6">
            <div className="flex items-center justify-between">
                <h1 className="text-xl font-bold text-foreground">
                    Result: Job #{result.jobId}
                </h1>
                <Link to="/jobs" className="text-sm text-primary hover:underline">
                    Back to Jobs
                </Link>
            </div>

            <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
                <section className="rounded-lg border bg-card p-4">
                    <h2 className="mb-3 text-sm font-semibold text-foreground">Sections</h2>
                    <SectionTree sections={result.sections} />
                </section>

                <section className="rounded-lg border bg-card p-4">
                    <h2 className="mb-3 text-sm font-semibold text-foreground">Entities</h2>
                    <EntityPanel
                        entities={result.entities}
                        confidenceMap={result.confidenceMap}
                        badgeThresholds={result.badgeThresholds}
                    />
                </section>
            </div>

            <section className="rounded-lg border bg-card p-4">
                <h2 className="mb-3 text-sm font-semibold text-foreground">Tables</h2>
                <p className="text-muted-foreground text-sm italic">
                    Table extraction available in Sprint 5.
                </p>
            </section>
        </div>
    );
}

function resolveErrorMessage(error: unknown): string {
    if (error instanceof Error) {
        return error.message;
    }

    return 'Failed to load results';
}

function EntityPanel(
    {
        entities,
        confidenceMap,
        badgeThresholds,
    }: {
        entities: Record<string, unknown> | undefined;
        confidenceMap: Record<string, number> | undefined;
        badgeThresholds?: { high: number; medium: number };
    },
) {
    if (!entities) {
        return (
            <p className="text-muted-foreground text-sm italic">
                No entities extracted yet.
            </p>
        );
    }

    return (
        <EntityTable
            entities={entities}
            confidenceMap={confidenceMap ?? {}}
            badgeThresholds={badgeThresholds}
        />
    );
}
