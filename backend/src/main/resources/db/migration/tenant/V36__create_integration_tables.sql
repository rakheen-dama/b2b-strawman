-- V36__create_integration_tables.sql
-- Phase 21: Integration Ports & BYOAK Infrastructure

CREATE TABLE org_secrets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_key      VARCHAR(200) NOT NULL,
    encrypted_value TEXT NOT NULL,
    iv              VARCHAR(24) NOT NULL,
    key_version     INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_org_secrets_key UNIQUE (secret_key)
);
