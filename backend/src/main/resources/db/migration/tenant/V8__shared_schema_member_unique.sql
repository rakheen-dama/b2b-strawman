-- V8: Widen members unique constraint for shared-schema multitenancy.
-- In tenant_shared, the same Clerk user can be a member of multiple orgs,
-- so UNIQUE(clerk_user_id) alone would collide. Replace with
-- UNIQUE(clerk_user_id, tenant_id). For dedicated schemas where tenant_id
-- is NULL, this still enforces one row per user (NULL is unique in Postgres).

ALTER TABLE members DROP CONSTRAINT IF EXISTS uq_members_clerk_user_id;
ALTER TABLE members ADD CONSTRAINT uq_members_clerk_user_tenant
    UNIQUE (clerk_user_id, tenant_id);
