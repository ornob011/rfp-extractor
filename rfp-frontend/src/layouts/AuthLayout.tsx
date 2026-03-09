import type { ReactNode } from 'react';
import { Card, CardContent } from '@/components/ui/card';

interface AuthLayoutProps {
    children: ReactNode;
}

export function AuthLayout({ children }: AuthLayoutProps) {
    return (
        <div className="min-h-screen bg-muted flex items-center justify-center p-4">
            <Card className="w-full max-w-sm">
                <CardContent className="pt-6">
                    {children}
                </CardContent>
            </Card>
        </div>
    );
}
