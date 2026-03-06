-- V59: Add request_template_id to project_templates for auto-draft information requests
-- Phase 34: Client Information Requests (Epic 256A)

ALTER TABLE project_templates ADD COLUMN IF NOT EXISTS request_template_id UUID REFERENCES request_templates(id);
