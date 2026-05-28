-- V25: Shard configuration table and org_schema_mapping extension
-- Phase 75 -- Scalability: Shard-Aware DB Resolver

CREATE TABLE IF NOT EXISTS public.shard_config (
    shard_id     VARCHAR(50)  PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    jdbc_url     VARCHAR(500),
    username     VARCHAR(100),
    pool_size    INT          NOT NULL DEFAULT 25,
    read_only    BOOLEAN      NOT NULL DEFAULT FALSE,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE public.shard_config IS
    'Shard metadata. Credentials come from env vars (KAZI_SHARD_{ID}_*), not this table.';

COMMENT ON COLUMN public.shard_config.jdbc_url IS
    'Metadata only for primary shard. Populated for secondary shards. Actual credentials from env vars.';

-- Seed the primary shard (always exists)
INSERT INTO public.shard_config (shard_id, display_name)
VALUES ('primary', 'Primary Database')
ON CONFLICT (shard_id) DO NOTHING;

-- Extend org_schema_mapping with shard assignment
ALTER TABLE public.org_schema_mapping
    ADD COLUMN IF NOT EXISTS shard_id VARCHAR(50) NOT NULL DEFAULT 'primary';

-- Foreign key from org_schema_mapping.shard_id to shard_config.shard_id
-- Using DO $$ block for idempotent constraint creation
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_org_schema_mapping_shard'
    ) THEN
        ALTER TABLE public.org_schema_mapping
            ADD CONSTRAINT fk_org_schema_mapping_shard
            FOREIGN KEY (shard_id) REFERENCES public.shard_config (shard_id);
    END IF;
END $$;

-- Index for shard-aware tenant discovery (used by TenantMigrationRunner)
CREATE INDEX IF NOT EXISTS idx_org_schema_mapping_shard
    ON public.org_schema_mapping (shard_id);

COMMENT ON COLUMN public.org_schema_mapping.shard_id IS
    'Database shard hosting this tenant schema. Defaults to primary.';
