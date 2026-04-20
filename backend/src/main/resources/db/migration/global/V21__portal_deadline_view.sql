-- V21__portal_deadline_view.sql
-- Epic 497A: Polymorphic portal deadline read-model (ADR-256). A single table aggregates four
-- firm-side deadline sources — FilingStatus / CourtDate / PrescriptionTracker / FieldDateApproaching
-- — so portal queries are trivial SELECTs. PK is (source_entity, id) because the firm-side ids
-- are unique per source but not across sources. Status is derived at sync time (ADR-253).

CREATE TABLE IF NOT EXISTS portal.portal_deadline_view (
    id                      UUID         NOT NULL,
    source_entity           VARCHAR(30)  NOT NULL
        CHECK (source_entity IN ('FILING_SCHEDULE','COURT_DATE','PRESCRIPTION_TRACKER','CUSTOM_FIELD_DATE')),
    customer_id             UUID         NOT NULL,
    matter_id               UUID,
    deadline_type           VARCHAR(20)  NOT NULL
        CHECK (deadline_type IN ('FILING','COURT_DATE','PRESCRIPTION','CUSTOM_DATE')),
    label                   VARCHAR(160) NOT NULL,
    due_date                DATE         NOT NULL,
    status                  VARCHAR(20)  NOT NULL
        CHECK (status IN ('UPCOMING','DUE_SOON','OVERDUE','COMPLETED','CANCELLED')),
    description_sanitised   VARCHAR(140),
    last_synced_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (source_entity, id)
);

-- Portal list queries scope to (customer_id, due_date) and order by due_date ASC.
CREATE INDEX IF NOT EXISTS idx_portal_deadline_customer_due
    ON portal.portal_deadline_view (customer_id, due_date ASC);
