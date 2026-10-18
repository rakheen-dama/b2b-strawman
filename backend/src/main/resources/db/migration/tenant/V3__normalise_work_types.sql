-- Work type is free text, but work-type-scoped field groups match on it, so the application now
-- stores one canonical form (io.b2mash.b2b.b2bstrawman.worktype.WorkTypes.normalize): trimmed, each
-- whitespace run collapsed to a single '_', upper-cased; blank becomes NULL. Rewrite values stored
-- before that rule existed with the same rule (trim \s at both ends, \s+ -> '_', upper):
-- "Conveyancing" -> CONVEYANCING, " personal injury " -> PERSONAL_INJURY. Idempotent: only rows
-- whose value would change are updated, so a re-run (or an already-canonical tenant) is a no-op.
-- The Java and SQL rules are equivalent for ASCII input; Unicode whitespace and case mapping may
-- differ by database locale (the read-side match in FieldGroupService normalises again).

UPDATE projects
SET work_type = NULLIF(upper(regexp_replace(regexp_replace(work_type, '^\s+|\s+$', '', 'g'), '\s+', '_', 'g')), '')
WHERE work_type IS NOT NULL
  AND work_type IS DISTINCT FROM
      NULLIF(upper(regexp_replace(regexp_replace(work_type, '^\s+|\s+$', '', 'g'), '\s+', '_', 'g')), '');

UPDATE project_templates
SET work_type = NULLIF(upper(regexp_replace(regexp_replace(work_type, '^\s+|\s+$', '', 'g'), '\s+', '_', 'g')), '')
WHERE work_type IS NOT NULL
  AND work_type IS DISTINCT FROM
      NULLIF(upper(regexp_replace(regexp_replace(work_type, '^\s+|\s+$', '', 'g'), '\s+', '_', 'g')), '');

-- applicable_work_types is a jsonb array of work-type strings: normalise each entry (keeping
-- order) and drop entries that are blank. Only rows where the normalised array differs.
UPDATE field_groups fg
SET applicable_work_types = n.normalised
FROM (
    SELECT g.id,
           COALESCE(
               jsonb_agg(to_jsonb(e.canonical) ORDER BY e.ord)
                   FILTER (WHERE e.canonical IS NOT NULL),
               '[]'::jsonb) AS normalised
    FROM field_groups g
    CROSS JOIN LATERAL (
        SELECT ord,
               NULLIF(upper(regexp_replace(regexp_replace(value, '^\s+|\s+$', '', 'g'), '\s+', '_', 'g')), '')
                   AS canonical
        FROM jsonb_array_elements_text(
                 CASE WHEN jsonb_typeof(g.applicable_work_types) = 'array'
                      THEN g.applicable_work_types ELSE '[]'::jsonb END)
             WITH ORDINALITY AS t(value, ord)
    ) e
    GROUP BY g.id
) n
WHERE fg.id = n.id
  AND fg.applicable_work_types IS DISTINCT FROM n.normalised;
