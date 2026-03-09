import { Link } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { Button } from '@/components/ui/button';

export function AccessDeniedPage() {
    return (
        <div className="min-h-screen flex items-center justify-center bg-muted p-4">
            <div className="text-center space-y-4">
                <ShieldAlert className="mx-auto h-12 w-12 text-destructive" />
                <h1 className="text-2xl font-bold">Access Denied</h1>
                <p className="text-muted-foreground">
                    You do not have permission to view this page.
                </p>
                <Button asChild>
                    <Link to="/jobs">Go to Jobs</Link>
                </Button>
            </div>
        </div>
    );
}
