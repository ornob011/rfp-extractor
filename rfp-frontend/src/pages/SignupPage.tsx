import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthLayout } from '@/layouts/AuthLayout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Alert } from '@/components/ui/alert';
import { signup } from '@/api/authClient';

export function SignupPage() {
    const navigate = useNavigate();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    async function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        setError('');

        if (password.length < 8) {
            setError('Password must be at least 8 characters');
            return;
        }

        setLoading(true);

        try {
            await signup(username, password);
            navigate('/login', {
                state: { message: 'Account created successfully. Please sign in.' },
            });
        } catch (err: unknown) {
            const status = (err as { response?: { status?: number } })?.response?.status;
            const message = status === 409
                ? 'Username already taken'
                : 'Signup failed. Please try again.';
            setError(message);
        } finally {
            setLoading(false);
        }
    }

    return (
        <AuthLayout>
            <div className="space-y-4">
                <div className="text-center">
                    <h2 className="text-lg font-bold">Create Account</h2>
                    <p className="text-sm text-muted-foreground">Sign up for RFP Extractor</p>
                </div>

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
                            minLength={8}
                        />
                        <p className="text-xs text-muted-foreground">Minimum 8 characters</p>
                    </div>

                    <Button type="submit" className="w-full" disabled={loading}>
                        {loading ? 'Creating account...' : 'Create Account'}
                    </Button>
                </form>

                <p className="text-center text-sm text-muted-foreground">
                    Already have an account?{' '}
                    <Link to="/login" className="text-primary hover:underline">
                        Sign in
                    </Link>
                </p>
            </div>
        </AuthLayout>
    );
}
