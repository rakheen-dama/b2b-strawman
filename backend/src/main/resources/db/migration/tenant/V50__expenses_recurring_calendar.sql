-- ============================================================
-- V50: Expenses, Recurring Tasks & Calendar Support
-- Phase 30 -- Expense tracking, task recurrence, invoice line
--             type discriminator, and org settings extensions
-- ============================================================

-- ============================================================
-- 1. Expense entity
-- ============================================================
CREATE TABLE expenses (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id           UUID NOT NULL,
    task_id              UUID,
    member_id            UUID NOT NULL,
    date                 DATE NOT NULL,
    description          VARCHAR(500) NOT NULL,
    amount               NUMERIC(12,2) NOT NULL,
    currency             VARCHAR(3) NOT NULL,
    category             VARCHAR(30) NOT NULL,
    receipt_document_id  UUID,
    billable             BOOLEAN NOT NULL DEFAULT true,
    invoice_id           UUID,
    markup_percent       NUMERIC(5,2),
    notes                VARCHAR(1000),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT expenses_amount_positive CHECK (amount > 0),
    CONSTRAINT expenses_category_check
        CHECK (category IN ('FILING_FEE', 'TRAVEL', 'COURIER', 'SOFTWARE',
                            'SUBCONTRACTOR', 'PRINTING', 'COMMUNICATION', 'OTHER')),
    CONSTRAINT expenses_markup_non_negative CHECK (markup_percent IS NULL OR markup_percent >= 0)
);

-- Indexes: project_id for project-scoped queries (most common access pattern)
CREATE INDEX idx_expenses_project_id ON expenses(project_id);
-- member_id for "my expenses" cross-project query
CREATE INDEX idx_expenses_member_id ON expenses(member_id);
-- Composite index for unbilled expense queries (billing integration)
CREATE INDEX idx_expenses_billable_invoice ON expenses(billable, invoice_id)
    WHERE billable = true AND invoice_id IS NULL;
-- Date range queries within a project
CREATE INDEX idx_expenses_project_date ON expenses(project_id, date);

-- ============================================================
-- 2. Task: recurrence fields
-- ============================================================
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurrence_rule VARCHAR(100);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurrence_end_date DATE;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS parent_task_id UUID;

-- Index for finding all instances of a recurring task
CREATE INDEX IF NOT EXISTS idx_tasks_parent_task_id ON tasks(parent_task_id)
    WHERE parent_task_id IS NOT NULL;

-- ============================================================
-- 3. InvoiceLine: expense support + line type discriminator
-- ============================================================
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS expense_id UUID;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS line_type VARCHAR(20) NOT NULL DEFAULT 'TIME';

-- Backfill line_type for existing retainer lines
UPDATE invoice_lines SET line_type = 'RETAINER'
    WHERE retainer_period_id IS NOT NULL AND line_type = 'TIME';

-- Backfill line_type for manual lines (no FK populated)
UPDATE invoice_lines SET line_type = 'MANUAL'
    WHERE time_entry_id IS NULL
      AND retainer_period_id IS NULL
      AND line_type = 'TIME';

-- Constraint on line_type values (ADR-118: explicit discriminator over implicit FK inspection)
ALTER TABLE invoice_lines ADD CONSTRAINT invoice_lines_line_type_check
    CHECK (line_type IN ('TIME', 'RETAINER', 'EXPENSE', 'MANUAL'));

-- Index for expense-to-invoice-line lookup
CREATE INDEX IF NOT EXISTS idx_invoice_lines_expense_id ON invoice_lines(expense_id)
    WHERE expense_id IS NOT NULL;

-- ============================================================
-- 4. OrgSettings: time reminder + expense markup defaults
-- ============================================================
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_days VARCHAR(50) DEFAULT 'MON,TUE,WED,THU,FRI';
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_time TIME DEFAULT '17:00';
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_min_minutes INTEGER DEFAULT 240;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS default_expense_markup_percent NUMERIC(5,2);
