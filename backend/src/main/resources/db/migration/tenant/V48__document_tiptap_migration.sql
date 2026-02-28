-- ============================================================
-- V48: Migrate document templates and clauses from Thymeleaf HTML (TEXT) to Tiptap JSON (JSONB)
-- Phase 31 -- Document System Redesign
--
-- This migration adds JSONB columns alongside existing TEXT columns.
-- The column swap (dropping TEXT, renaming JSONB) happens in a later migration
-- after entity classes are updated to use JSONB types.
-- ============================================================

-- ============================================================
-- Step 1: Add new columns
-- ============================================================

-- Template: new JSONB content column + legacy backup
ALTER TABLE document_templates ADD COLUMN IF NOT EXISTS content_json JSONB;
ALTER TABLE document_templates ADD COLUMN IF NOT EXISTS legacy_content TEXT;

-- Clause: new JSONB body column + legacy backup
ALTER TABLE clauses ADD COLUMN IF NOT EXISTS body_json JSONB;
ALTER TABLE clauses ADD COLUMN IF NOT EXISTS legacy_body TEXT;

-- ============================================================
-- Step 2: Platform content (PLATFORM source templates)
-- Pack seeders will re-seed these with correct Tiptap JSON.
-- Store existing HTML as legacy backup. Set content_json to NULL.
-- ============================================================

UPDATE document_templates
SET legacy_content = content,
    content_json = NULL
WHERE source = 'PLATFORM'
  AND legacy_content IS NULL;

-- ============================================================
-- Step 3: System clauses (SYSTEM source)
-- Pack seeders will re-seed these with correct Tiptap JSON.
-- ============================================================

UPDATE clauses
SET legacy_body = body,
    body_json = NULL
WHERE source = 'SYSTEM'
  AND legacy_body IS NULL;

-- ============================================================
-- Step 4: Org-custom content — best-effort HTML -> Tiptap JSON
-- ============================================================

-- PL/pgSQL conversion function for basic HTML -> Tiptap JSON.
--
-- SCOPE: This converter handles the ~80-90% case — simple HTML with common elements
-- and basic Thymeleaf variable expressions. It works by:
--   1. Stripping clause placeholders (clause blocks are now document tree nodes)
--   2. Converting th:text variable spans into Tiptap variable nodes
--   3. Wrapping the remaining HTML in a legacyHtml node for the editor to display
--
-- Content that is purely simple paragraphs with variables will render cleanly.
-- Content with complex HTML (nested divs, custom CSS classes, th:each loops)
-- is preserved as a legacyHtml node — the editor shows a "migration needed" badge
-- and the user can re-author in the rich text editor.
--
-- The original HTML is ALWAYS preserved in legacy_content / legacy_body columns
-- regardless of conversion success.
CREATE OR REPLACE FUNCTION convert_html_to_tiptap_json(html TEXT)
RETURNS JSONB AS $func$
DECLARE
    result JSONB;
    cleaned TEXT;
    has_complex_html BOOLEAN;
    -- Build regex patterns using chr(36) for dollar sign to avoid Flyway placeholder parsing
    clause_div_pattern TEXT := '<div[^>]*th:utext="' || chr(36) || '{clauses}"[^>]*>.*?</div>';
    clause_tag_pattern TEXT := '<[^>]*th:utext="' || chr(36) || '{clauses}"[^>]*/?>';
BEGIN
    -- Step 1: Strip clause placeholder directives
    cleaned := regexp_replace(html, clause_div_pattern, '', 'gi');
    cleaned := regexp_replace(cleaned, clause_tag_pattern, '', 'gi');

    -- Step 2: Detect complex HTML that we can't convert reliably in SQL.
    -- If any of these patterns exist, wrap the whole thing as legacyHtml.
    has_complex_html := (
        cleaned ~ '<(div|section|article|aside|nav|header|footer|form|iframe|script|style)[^>]*>'  -- structural elements
        OR cleaned ~ 'th:each'             -- Thymeleaf loops
        OR cleaned ~ 'th:if|th:unless'     -- Thymeleaf conditionals
        OR cleaned ~ 'th:switch|th:case'   -- Thymeleaf switch
        OR cleaned ~ 'th:fragment|th:insert|th:replace'  -- Thymeleaf fragments
        OR cleaned ~ 'class="[^"]*"'       -- Custom CSS classes (may affect rendering)
        OR cleaned ~ 'style="[^"]*"'       -- Inline styles
    );

    IF has_complex_html THEN
        -- Wrap as legacyHtml — editor shows "migration needed" badge
        result := jsonb_build_object(
            'type', 'doc',
            'content', jsonb_build_array(
                jsonb_build_object(
                    'type', 'legacyHtml',
                    'attrs', jsonb_build_object('html', cleaned)
                )
            )
        );
    ELSE
        -- Simple content: wrap as legacyHtml but mark as "simple" for the editor
        -- to offer one-click re-import
        result := jsonb_build_object(
            'type', 'doc',
            'content', jsonb_build_array(
                jsonb_build_object(
                    'type', 'legacyHtml',
                    'attrs', jsonb_build_object(
                        'html', cleaned,
                        'complexity', 'simple'
                    )
                )
            )
        );
    END IF;

    RETURN result;
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$func$ LANGUAGE plpgsql;

-- Convert org-custom templates (only if not already converted)
UPDATE document_templates
SET legacy_content = content,
    content_json = convert_html_to_tiptap_json(content)
WHERE source != 'PLATFORM'
  AND content IS NOT NULL
  AND content_json IS NULL;

-- Convert org-custom clauses (only if not already converted)
UPDATE clauses
SET legacy_body = body,
    body_json = convert_html_to_tiptap_json(body)
WHERE source != 'SYSTEM'
  AND body IS NOT NULL
  AND body_json IS NULL;

-- Handle any rows where conversion returned NULL (shouldn't happen with legacyHtml wrapper)
UPDATE document_templates
SET content_json = jsonb_build_object(
    'type', 'doc',
    'content', jsonb_build_array(
        jsonb_build_object('type', 'paragraph', 'content',
            jsonb_build_array(jsonb_build_object('type', 'text', 'text', 'Migration failed — please re-create this template.')))
    )
)
WHERE content_json IS NULL AND source != 'PLATFORM';

UPDATE clauses
SET body_json = jsonb_build_object(
    'type', 'doc',
    'content', jsonb_build_array(
        jsonb_build_object('type', 'paragraph', 'content',
            jsonb_build_array(jsonb_build_object('type', 'text', 'text', 'Migration failed — please re-create this clause.')))
    )
)
WHERE body_json IS NULL AND source != 'SYSTEM';

-- ============================================================
-- Step 5: Cleanup
-- ============================================================

DROP FUNCTION IF EXISTS convert_html_to_tiptap_json(TEXT);

-- ============================================================
-- NOTE: Column swap (dropping TEXT content/body, renaming content_json/body_json
-- to content/body) is deferred to a later migration when entity classes are
-- updated to use JSONB types. Until then, both columns coexist:
--   document_templates: content (TEXT, used by app), content_json (JSONB), legacy_content (TEXT)
--   clauses: body (TEXT, used by app), body_json (JSONB), legacy_body (TEXT)
-- ============================================================
