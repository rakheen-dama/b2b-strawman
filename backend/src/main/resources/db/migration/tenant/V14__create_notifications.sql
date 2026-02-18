-- V16: Create notifications table
-- Epic 61B — In-app notification rows, one per recipient per event

CREATE TABLE IF NOT EXISTS notifications (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_member_id     UUID         NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    type                    VARCHAR(50)  NOT NULL,
    title                   VARCHAR(500) NOT NULL,
    body                    TEXT,
    reference_entity_type   VARCHAR(20),
    reference_entity_id     UUID,
    reference_project_id    UUID,
    is_read                 BOOLEAN      NOT NULL DEFAULT false,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Indexes per architecture doc Section 11.2.2
CREATE INDEX IF NOT EXISTS idx_notifications_unread
    ON notifications (recipient_member_id, is_read, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_list
    ON notifications (recipient_member_id, created_at DESC);
