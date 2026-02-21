CREATE TABLE IF NOT EXISTS template_task_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_task_id UUID NOT NULL REFERENCES template_tasks(id) ON DELETE CASCADE,
    title            VARCHAR(500) NOT NULL,
    sort_order       INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_template_task_items_task_sort
    ON template_task_items (template_task_id, sort_order);
