# Audit 02 — Flyway DEFAULT vs Pre-Existing Row Drift

## Hypothesis (from OBS-2107)
`ADD COLUMN ... DEFAULT X` populates new INSERTs with `X`. PostgreSQL DOES backfill `NOT NULL DEFAULT X` columns at ALTER time, but **plain nullable `DEFAULT X` columns are not guaranteed to backfill** — pre-existing rows can hold NULL even though new rows hold the default. Code that reads such columns and expects "DEFAULT was applied to all rows" silently misbehaves on tenants whose `org_settings` row pre-existed the migration.

## Method
```
grep -rn "ADD COLUMN.*DEFAULT\|ALTER COLUMN.*SET DEFAULT" backend/src/main/resources/db/migration/
```
52 matches across 25+ migrations. Triaged each by:
1. Is it `NOT NULL DEFAULT X`? → safe, PG backfills.
2. Is it nullable `DEFAULT X`? → check whether the consuming Java code handles NULL.
3. Is it a JSONB `DEFAULT '[]'::jsonb` or similar collection? → high-risk (OBS-2107 was this pattern).

## Findings

### Confirmed safe (NOT NULL DEFAULT, PG auto-backfill)

| Migration | Column | Default |
|---|---|---|
| V36 | `org_settings.accounting_enabled`, `ai_enabled`, `document_signing_enabled` | `FALSE` — NOT NULL DEFAULT |
| V47 | `projects.status` | `'ACTIVE'` — NOT NULL DEFAULT |
| V40 | `comments.source` | `'INTERNAL'` — NOT NULL DEFAULT |
| V103 | `projects.version` | `0` — NOT NULL DEFAULT |
| V106 | `org_settings.portal_visible_deadline` | `false` — NOT NULL DEFAULT |
| V101 | `customer.acceptance_eligible` | `false` — NOT NULL DEFAULT |
| V63 | `org_settings.billing_batch_async_threshold`, `billing_email_rate_limit` | NOT NULL DEFAULT integers |
| V50 | `org_settings.time_reminder_enabled`, `invoice_lines.line_type` | NOT NULL DEFAULT |
| V43 | `org_settings.tax_inclusive`, `invoice_lines.tax_exempt` | NOT NULL DEFAULT FALSE |
| V65 | `*.format`, `*.output_format` | NOT NULL DEFAULT |
| V10 | `documents.scope`, `documents.visibility` | NOT NULL DEFAULT |
| V53 | `field_definitions.required_for_contexts` | JSONB `'[]'::jsonb` NOT NULL DEFAULT |
| V54 | `project_templates.required_customer_field_ids` | JSONB `'[]'::jsonb` NOT NULL DEFAULT |
| V76 | `org_settings.retention_policy_enabled` | NOT NULL DEFAULT FALSE |
| V16 | `tasks.customer_visible` | NOT NULL DEFAULT FALSE |

PostgreSQL's `ADD COLUMN ... NOT NULL DEFAULT X` is atomic-with-backfill — the existing rows get rewritten with `X`. These are not at risk.

### Suspect — nullable DEFAULT, consuming code may assume value present

| Migration | Column | Default | Risk |
|---|---|---|---|
| V50 | `org_settings.time_reminder_days` | `'MON,TUE,WED,THU,FRI'` (no NOT NULL) | If pre-V50 tenant rows are NULL, the time-reminder dispatch reads NULL and may NPE or skip. |
| V50 | `org_settings.time_reminder_time` | `'17:00'` (no NOT NULL) | Same risk. |
| V50 | `org_settings.time_reminder_min_minutes` | `240` (no NOT NULL) | Same risk. |
| V56 | `org_settings.default_request_reminder_days` | `5` (no NOT NULL) | Same risk for info-request reminder scheduling. |
| V43 | `org_settings.tax_registration_label`, `tax_label` | string defaults (no NOT NULL) | Cosmetic — invoice rendering may show NULL/empty if pre-V43 tenants. |
| V76 | `org_settings.financial_retention_months` | `60` (no NOT NULL) | Retention sweep may misbehave on NULL. |

**Action**: per-migration spot-check is needed — for each, is there a tenant in production created before the migration that's now NULL? In practice the audit can be done with one `psql`:

```sql
SELECT count(*) FROM org_settings WHERE time_reminder_days IS NULL;
SELECT count(*) FROM org_settings WHERE default_request_reminder_days IS NULL;
-- etc.
```

If non-zero → write a V-N+1 backfill migration mirroring the OBS-2107 V118 pattern.

### Already-fixed instance — OBS-2107
- V117 added `org_settings.portal_notification_doc_types JSONB DEFAULT '...'::jsonb` (non-NOT-NULL JSONB).
- Pre-V117 tenant row had `[]` (empty), causing the closure-pack email skip.
- Fixed by V118 backfill.

## Action items

1. **Run the suspect-list `psql` queries against the dev tenant `tenant_5039f2d497cf`.** If any of the 6 suspect columns is NULL or empty, write a backfill migration in the next cycle.
2. **Add a Flyway-test convention**: any `ADD COLUMN` that's not `NOT NULL DEFAULT` should have an accompanying `UPDATE` to backfill, OR the consuming Java code must NULL-safe-handle.
3. **CLAUDE.md addition**: document the OBS-2107 lesson in `backend/CLAUDE.md` so future migrations follow the rule.
