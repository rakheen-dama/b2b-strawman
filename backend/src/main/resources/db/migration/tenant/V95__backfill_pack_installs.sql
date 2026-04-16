-- V95__backfill_pack_installs.sql
--
-- Phase 65: Kazi Packs Catalog & Install Pipeline — Backfill Migration
--
-- Creates synthetic PackInstall rows from the existing OrgSettings JSONB
-- columns (template_pack_status and automation_pack_status) so that
-- pre-existing pack-installed content is tracked in the new pack_install
-- table and linked via source_pack_install_id.
--
-- PART 1 — Document Template Packs (high confidence)
-- Attribution uses document_templates.pack_id, which was set by
-- TemplatePackSeeder on every seeded template. Linkage is exact.
--
-- PART 2 — Automation Template Packs (best-effort)
-- AutomationPackSeeder did NOT stamp a pack_id on automation_rules.
-- Attribution uses a 60-second timestamp heuristic: rules created
-- within 60 seconds of the pack's appliedAt time with source = 'TEMPLATE'
-- are attributed. Unmatched rules remain with NULL source_pack_install_id
-- and cannot be uninstalled via the pack pipeline (safe default).

-- ============================================================
-- PART 1: Document Template Packs
-- ============================================================
DO $$
DECLARE
  pack_record RECORD;
  new_pack_install_id UUID;
  template_count INT;
BEGIN
  -- Iterate over each entry in org_settings.template_pack_status JSONB array
  FOR pack_record IN
    SELECT
      elem ->> 'packId'    AS pack_id,
      elem ->> 'version'   AS pack_version,
      elem ->> 'appliedAt' AS applied_at
    FROM org_settings,
         jsonb_array_elements(template_pack_status) AS elem
    WHERE template_pack_status IS NOT NULL
  LOOP
    -- Skip if already backfilled (idempotent)
    IF EXISTS (SELECT 1 FROM pack_install WHERE pack_id = pack_record.pack_id) THEN
      CONTINUE;
    END IF;

    -- Count templates linked to this pack_id
    SELECT COUNT(*) INTO template_count
    FROM document_templates
    WHERE pack_id = pack_record.pack_id;

    -- Insert synthetic PackInstall row
    INSERT INTO pack_install (id, pack_id, pack_type, pack_version, pack_name, installed_at, installed_by_member_id, item_count)
    VALUES (
      gen_random_uuid(),
      pack_record.pack_id,
      'DOCUMENT_TEMPLATE',
      COALESCE(pack_record.pack_version, '1'),
      pack_record.pack_id,  -- Use pack_id as name (best we have)
      COALESCE(pack_record.applied_at::timestamptz, now()),
      NULL,
      template_count
    )
    RETURNING id INTO new_pack_install_id;

    -- Link document_templates to the new PackInstall via source_pack_install_id
    UPDATE document_templates
    SET source_pack_install_id = new_pack_install_id
    WHERE pack_id = pack_record.pack_id
      AND source_pack_install_id IS NULL;
  END LOOP;
END $$;

-- ============================================================
-- PART 2: Automation Template Packs
-- ============================================================
DO $$
DECLARE
  pack_record RECORD;
  new_pack_install_id UUID;
  rule_count INT;
  pack_applied_at TIMESTAMPTZ;
BEGIN
  -- Iterate over each entry in org_settings.automation_pack_status JSONB array
  FOR pack_record IN
    SELECT
      elem ->> 'packId'    AS pack_id,
      elem ->> 'version'   AS pack_version,
      elem ->> 'appliedAt' AS applied_at
    FROM org_settings,
         jsonb_array_elements(automation_pack_status) AS elem
    WHERE automation_pack_status IS NOT NULL
  LOOP
    -- Skip if already backfilled (idempotent) — pack_id must be unique
    -- Automation packs use a different pack_id namespace, but the UNIQUE
    -- constraint is on pack_install.pack_id. Prefix to avoid collision
    -- with document template pack_ids.
    DECLARE
      prefixed_pack_id VARCHAR(128) := 'automation:' || pack_record.pack_id;
    BEGIN
      IF EXISTS (SELECT 1 FROM pack_install WHERE pack_id = prefixed_pack_id) THEN
        CONTINUE;
      END IF;

      pack_applied_at := COALESCE(pack_record.applied_at::timestamptz, now());

      -- Count rules created within 60 seconds of pack application with TEMPLATE source
      SELECT COUNT(*) INTO rule_count
      FROM automation_rules
      WHERE source = 'TEMPLATE'
        AND created_at BETWEEN pack_applied_at - INTERVAL '60 seconds'
                           AND pack_applied_at + INTERVAL '60 seconds';

      -- Insert synthetic PackInstall row
      INSERT INTO pack_install (id, pack_id, pack_type, pack_version, pack_name, installed_at, installed_by_member_id, item_count)
      VALUES (
        gen_random_uuid(),
        prefixed_pack_id,
        'AUTOMATION_TEMPLATE',
        COALESCE(pack_record.pack_version, '1'),
        pack_record.pack_id,  -- Use original pack_id as display name
        pack_applied_at,
        NULL,
        rule_count
      )
      RETURNING id INTO new_pack_install_id;

      -- Link automation_rules within the timestamp window
      UPDATE automation_rules
      SET source_pack_install_id = new_pack_install_id
      WHERE source = 'TEMPLATE'
        AND created_at BETWEEN pack_applied_at - INTERVAL '60 seconds'
                           AND pack_applied_at + INTERVAL '60 seconds'
        AND source_pack_install_id IS NULL;
    END;
  END LOOP;
END $$;
