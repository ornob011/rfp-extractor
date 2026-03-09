import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { AuthLayout } from '@/layouts/AuthLayout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Alert } from '@/components/ui/alert';
import { login } from '@/api/authClient';

export function LoginPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const successMessage = (location.state as { message?: string })?.message;

    async function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            await login(username, password);
            navigate('/');
        } catch (err: unknown) {
            const message = (err as { response?: { status?: number } })?.response?.status === 401
                ? 'Invalid username or password'
                : 'Login failed. Please try again.';
            setError(message);
        } finally {
            setLoading(false);
        }
    }

    return (
        <AuthLayout>
            <div className="space-y-4">
                <div className="text-center">
                    <h2 className="text-lg font-bold">RFP Extractor</h2>
                    <p className="text-sm text-muted-foreground">Sign in to your account</p>
                </div>

                {successMessage && (
                    <Alert className="bg-green-50 text-green-800 border-green-200">
                        {successMessage}
                    </Alert>
                )}

                {error && (
                    <Alert variant="destructive">{error}</Alert>
                )}

                <form onSubmit={handleSubmit} className="space-y-3">
                    <div className="space-y-1">
                        <Label htmlFor="username">Username</Label>
                        <Input
                            id="username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            required
                            autoFocus
                        />
                    </div>

                    <div className="space-y-1">
                        <Label htmlFor="password">Password</Label>
                        <Input
                            id="password"
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                        />
                    </div>

                    <Button type="submit" className="w-full" disabled={loading}>
                        {loading ? 'Signing in...' : 'Sign In'}
                    </Button>
                </form>

                <p className="text-center text-sm text-muted-foreground">
                    Don&apos;t have an account?{' '}
                    <Link to="/signup" className="text-primary hover:underline">
                        Sign up
                    </Link>
                </p>
            </div>
        </AuthLayout>
    );
}
