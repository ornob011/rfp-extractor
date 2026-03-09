import type { FallbackProps } from 'react-error-boundary';
import { AuthLayout } from '@/layouts/AuthLayout';
import { Button } from '@/components/ui/button';

export function ServerErrorPage({ resetErrorBoundary }: FallbackProps) {
    return (
        <AuthLayout>
            <div className="text-center space-y-4">
                <h1 className="text-4xl font-bold text-foreground">500</h1>
                <p className="text-muted-foreground">
                    Something went wrong. Please try again.
                </p>
                <Button variant="outline" onClick={resetErrorBoundary}>
                    Retry
                </Button>
            </div>
        </AuthLayout>
    );
}
