-- V8__extend_portal_read_model.sql
-- Portal invoices (synced from tenant schema when status >= SENT)
CREATE TABLE IF NOT EXISTS portal.portal_invoices (
    id              UUID PRIMARY KEY,
    org_id          VARCHAR(255) NOT NULL,
    customer_id     UUID NOT NULL,
    invoice_number  VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    issue_date      DATE,
    due_date        DATE,
    subtotal        DECIMAL(14,2) NOT NULL DEFAULT 0,
    tax_amount      DECIMAL(14,2) NOT NULL DEFAULT 0,
    total           DECIMAL(14,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(3) NOT NULL,
    notes           TEXT,
    synced_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_portal_invoices_org_customer
    ON portal.portal_invoices(org_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_portal_invoices_customer_issue_date
    ON portal.portal_invoices(customer_id, issue_date DESC);

-- Portal invoice line items
CREATE TABLE IF NOT EXISTS portal.portal_invoice_lines (
    id                  UUID PRIMARY KEY,
    portal_invoice_id   UUID NOT NULL REFERENCES portal.portal_invoices(id) ON DELETE CASCADE,
    description         TEXT NOT NULL,
    quantity            DECIMAL(10,4) NOT NULL,
    unit_price          DECIMAL(12,2) NOT NULL,
    amount              DECIMAL(14,2) NOT NULL,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    synced_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_portal_invoice_lines_invoice
    ON portal.portal_invoice_lines(portal_invoice_id);

-- Portal tasks (synced from tenant schema, minimal data; used in Epic 154)
CREATE TABLE IF NOT EXISTS portal.portal_tasks (
    id                  UUID PRIMARY KEY,
    org_id              VARCHAR(255) NOT NULL,
    portal_project_id   UUID NOT NULL,
    name                VARCHAR(500) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    assignee_name       VARCHAR(255),
    sort_order          INTEGER NOT NULL DEFAULT 0,
    synced_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_portal_tasks_project
    ON portal.portal_tasks(portal_project_id);
CREATE INDEX IF NOT EXISTS idx_portal_tasks_org
    ON portal.portal_tasks(org_id);
