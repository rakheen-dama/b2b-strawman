ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS logo_s3_key VARCHAR(500);
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS brand_color VARCHAR(7);
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS document_footer_text TEXT;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS template_pack_status JSONB;

-- brand_color must be a valid hex color if provided
ALTER TABLE org_settings ADD CONSTRAINT chk_brand_color_hex
    CHECK (brand_color IS NULL OR brand_color ~ '^#[0-9a-fA-F]{6}$');
