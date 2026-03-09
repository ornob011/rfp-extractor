import { Link } from 'react-router-dom';
import { AuthLayout } from '@/layouts/AuthLayout';

export function NotFoundPage() {
    return (
        <AuthLayout>
            <div className="text-center space-y-4">
                <h1 className="text-4xl font-bold text-foreground">404</h1>
                <p className="text-muted-foreground">
                    The page you are looking for does not exist.
                </p>
                <Link
                    to="/jobs"
                    className="inline-block text-sm text-primary hover:underline"
                >
                    Go to Jobs
                </Link>
            </div>
        </AuthLayout>
    );
}
