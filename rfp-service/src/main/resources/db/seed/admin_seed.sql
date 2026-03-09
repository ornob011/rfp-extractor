-- Seed default admin user.
-- BCrypt hash below matches the bootstrap password documented in Sprint 11.
-- Change the password immediately after first deployment.
INSERT INTO users (username, password_hash, role, enabled, created_at, updated_at)
VALUES (
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'ADMIN',
    true,
    NOW(),
    NOW()
)
ON CONFLICT (username) DO NOTHING;
