import { rfpClient } from './rfpClient';
import type { UserRole } from '@/types/userRole';

interface LoginResponse {
    token: string;
    expiresAt: string;
    roles: UserRole[];
}

interface SignupResponse {
    userId: number;
    username: string;
    role: UserRole;
    createdAt: string;
}

const TOKEN_KEY = 'rfp_token';
const ROLES_KEY = 'rfp_roles';
const USERNAME_KEY = 'rfp_username';
const EXPIRES_AT_KEY = 'rfp_expires_at';

export async function login(
    username: string,
    password: string,
): Promise<LoginResponse> {
    const response = await rfpClient.post<LoginResponse>(
        '/api/v1/auth/login',
        { username, password },
    );

    persistSession(response.data, username);

    return response.data;
}

export async function signup(
    username: string,
    password: string,
): Promise<SignupResponse> {
    const response = await rfpClient.post<SignupResponse>(
        '/api/v1/auth/signup',
        { username, password },
    );

    return response.data;
}

export function logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLES_KEY);
    localStorage.removeItem(USERNAME_KEY);
    localStorage.removeItem(EXPIRES_AT_KEY);
}

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}

export function getRoles(): UserRole[] {
    const raw = localStorage.getItem(ROLES_KEY);

    if (!raw) {
        return [];
    }

    return JSON.parse(raw) as UserRole[];
}

export function getUsername(): string | null {
    return localStorage.getItem(USERNAME_KEY);
}

export function getExpiry(): string | null {
    return localStorage.getItem(EXPIRES_AT_KEY);
}

export function hasRole(role: UserRole): boolean {
    return getRoles().includes(role);
}

export function isAuthenticated(): boolean {
    const token = getToken();
    const expiresAt = getExpiry();

    if (!token || !expiresAt) {
        return false;
    }

    return new Date(expiresAt).getTime() > Date.now();
}

function persistSession(
    data: LoginResponse,
    username: string,
): void {
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(ROLES_KEY, JSON.stringify(data.roles));
    localStorage.setItem(USERNAME_KEY, username);
    localStorage.setItem(EXPIRES_AT_KEY, data.expiresAt);
}
