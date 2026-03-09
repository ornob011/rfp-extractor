import { Navigate, Outlet } from 'react-router-dom';
import { hasRole, isAuthenticated } from '@/api/authClient';
import type { UserRole } from '@/types/userRole';

interface ProtectedRouteProps {
    requiredRole?: UserRole;
}

export function ProtectedRoute({ requiredRole }: ProtectedRouteProps) {
    if (!isAuthenticated()) {
        return <Navigate to="/login" replace />;
    }

    if (!requiredRole) {
        return <Outlet />;
    }

    if (hasRole(requiredRole)) {
        return <Outlet />;
    }

    return <Navigate to="/access-denied" replace />;
}
