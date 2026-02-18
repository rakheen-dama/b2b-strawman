-- V19: Create org_settings, billing_rates, cost_rates, project_budgets tables
-- Phase 8 — Rate Cards, Budgets & Profitability

-- =============================================================================
-- org_settings
-- =============================================================================

CREATE TABLE IF NOT EXISTS org_settings (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    default_currency  VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_org_settings_currency_len CHECK (char_length(default_currency) = 3)
);

-- =============================================================================
-- billing_rates
-- =============================================================================

CREATE TABLE IF NOT EXISTS billing_rates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id       UUID NOT NULL REFERENCES members(id),
    project_id      UUID REFERENCES projects(id) ON DELETE CASCADE,
    customer_id     UUID REFERENCES customers(id) ON DELETE CASCADE,
    currency        VARCHAR(3) NOT NULL,
    hourly_rate     DECIMAL(12,2) NOT NULL,
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_billing_rate_positive CHECK (hourly_rate > 0),
    CONSTRAINT chk_billing_rate_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_billing_rate_scope CHECK (NOT (project_id IS NOT NULL AND customer_id IS NOT NULL)),
    CONSTRAINT chk_billing_rate_currency_len CHECK (char_length(currency) = 3)
);

CREATE INDEX IF NOT EXISTS idx_billing_rates_resolution
    ON billing_rates (member_id, project_id, customer_id, effective_from);

CREATE INDEX IF NOT EXISTS idx_billing_rates_project
    ON billing_rates (project_id) WHERE project_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_billing_rates_customer
    ON billing_rates (customer_id) WHERE customer_id IS NOT NULL;

-- =============================================================================
-- cost_rates
-- =============================================================================

CREATE TABLE IF NOT EXISTS cost_rates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id       UUID NOT NULL REFERENCES members(id),
    currency        VARCHAR(3) NOT NULL,
    hourly_cost     DECIMAL(12,2) NOT NULL,
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_cost_rate_positive CHECK (hourly_cost > 0),
    CONSTRAINT chk_cost_rate_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_cost_rate_currency_len CHECK (char_length(currency) = 3)
);

CREATE INDEX IF NOT EXISTS idx_cost_rates_resolution
    ON cost_rates (member_id, effective_from);

-- =============================================================================
-- project_budgets
-- =============================================================================

CREATE TABLE IF NOT EXISTS project_budgets (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    budget_hours        DECIMAL(10,2),
    budget_amount       DECIMAL(14,2),
    budget_currency     VARCHAR(3),
    alert_threshold_pct INTEGER NOT NULL DEFAULT 80,
    threshold_notified  BOOLEAN NOT NULL DEFAULT false,
    notes               TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_budget_at_least_one CHECK (budget_hours IS NOT NULL OR budget_amount IS NOT NULL),
    CONSTRAINT chk_budget_hours_positive CHECK (budget_hours IS NULL OR budget_hours > 0),
    CONSTRAINT chk_budget_amount_positive CHECK (budget_amount IS NULL OR budget_amount > 0),
    CONSTRAINT chk_budget_currency_required CHECK (budget_amount IS NULL OR budget_currency IS NOT NULL),
    CONSTRAINT chk_budget_currency_len CHECK (budget_currency IS NULL OR char_length(budget_currency) = 3),
    CONSTRAINT chk_budget_threshold_range CHECK (alert_threshold_pct >= 50 AND alert_threshold_pct <= 100)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_project_budgets_project
    ON project_budgets (project_id);
