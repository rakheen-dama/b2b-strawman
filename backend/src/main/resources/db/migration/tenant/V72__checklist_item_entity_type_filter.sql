ALTER TABLE checklist_template_items ADD COLUMN IF NOT EXISTS applicable_entity_types JSONB;
ALTER TABLE checklist_instance_items ADD COLUMN IF NOT EXISTS applicable_entity_types JSONB;
