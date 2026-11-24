-- Phase 93 / 656A (architecture §17.2.6, §17.7.5): the firm's go-live acknowledgements. New table only.
-- Tenant V35 (guide V33; V34 = 655A client take-on FICA; next free at build time, P-1). No CASCADE.
CREATE TABLE IF NOT EXISTS take_on_go_live_items (
    item_key varchar(40) NOT NULL,
    acknowledged_by uuid NOT NULL,
    acknowledged_at timestamptz NOT NULL,
    note varchar(1000),
    CONSTRAINT take_on_go_live_items_pkey PRIMARY KEY (item_key),
    CONSTRAINT chk_go_live_item_key CHECK (item_key IN ('TRUST_RECORDS_RETAINED', 'FICA_RECORDS_RETAINED',
                                                        'ARCHIVE_HOSTING_CONSIDERED', 'AUDITOR_INFORMED')),
    CONSTRAINT take_on_go_live_items_member_fkey FOREIGN KEY (acknowledged_by) REFERENCES members(id)
);
