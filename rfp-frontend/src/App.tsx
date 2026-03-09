import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ErrorBoundary } from 'react-error-boundary';
import { Toaster } from 'sonner';
import { AppLayout } from '@/layouts/AppLayout';
import { UploadPage } from '@/pages/UploadPage';
import { JobStatusPage } from '@/pages/JobStatusPage';
import { JobsListPage } from '@/pages/JobsListPage';
import { ResultPage } from '@/pages/ResultPage';
import { AdminPage } from '@/pages/AdminPage';
import { LoginPage } from '@/pages/LoginPage';
import { SignupPage } from '@/pages/SignupPage';
import { AccessDeniedPage } from '@/pages/AccessDeniedPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { ServerErrorPage } from '@/pages/ServerErrorPage';
import { ProtectedRoute } from '@/components/ProtectedRoute';

export function App() {
    return (
        <ErrorBoundary fallback={<ServerErrorPage error={new Error('Application error')} resetErrorBoundary={() => {}} />}>
            <BrowserRouter>
                <Routes>
                    <Route path="/login" element={<LoginPage />} />
                    <Route path="/signup" element={<SignupPage />} />
                    <Route path="/access-denied" element={<AccessDeniedPage />} />

                    <Route element={<ProtectedRoute />}>
                        <Route element={<AppLayout />}>
                            <Route path="/" element={<Navigate to="/jobs" replace />} />
                            <Route path="/upload" element={<UploadPage />} />
                            <Route path="/jobs" element={<JobsListPage />} />
                            <Route path="/job/:jobId" element={<JobStatusPage />} />
                            <Route path="/result/:jobId" element={<ResultPage />} />
                        </Route>
                    </Route>

                    <Route element={<ProtectedRoute requiredRole="ADMIN" />}>
                        <Route element={<AppLayout />}>
                            <Route path="/admin" element={<AdminPage />} />
                        </Route>
                    </Route>

                    <Route path="*" element={<NotFoundPage />} />
                </Routes>
            </BrowserRouter>
            <Toaster />
        </ErrorBoundary>
    );
}

export default App;
