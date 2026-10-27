# 625B reproducer — BEFORE evidence (legal activity formatter + Home delegates)

Captured 2026-10-27 ~11:16–11:19 UTC on the e2e mock-auth stack (frontend :3001, backend :8081, all containers healthy, up 2 days) at main `285171749`. Logged in as Alice Owner (owner) via /mock-login. Read-only: no source or product data changed. The only extra calls were GETs to the two activity endpoints using a mock-IDP token.

## Surfaces and counts

| Surface | Rows | FALLBACK / raw | OK |
|---|---|---|---|
| Matter Activity tab, estate matter `9a27cd08-76fc-44b1-8c41-a8dd1aa1af22` "Estate Late Ndlovu 625A repro" (all rows, after one "Load more") | 28 | 23 | 5 |
| Home → Recent Activity widget (limit 15) | 15 | 15 | 0 |

- Matter feed: all 23 fallback rows read `<actor> performed <event_type> on <entity_type>`. They cover every trust (6), fee-note/`invoice.*` (6), `acceptance.*` (9), `customer.linked` (1) and `project.created_from_template` (1) row. The only OK rows are `time_entry.created` (2) and `document.generated` (3). No row says "fee note", and no row shows an amount, an INV number or a trust reference. `entityName` is `"unknown"` on all 23 fallback rows.
- Home: 13 rows read `<actor> performed <event_type>` and 2 read `<actor> created a <raw_entity>` (`created a invoice`, `created a time_entry`).
- Home and the matter feed render the same audit event differently. Home misses sentences the matter formatter already has: `time_entry.created`, `document.generated`, `statement.generated`, `matter_closure.closed`, `matter.closure.override_used`. Home also misattributes the portal-contact `acceptance.declined` to "System" (the matter feed shows "Sipho Ndlovu mvmzw78f31c9").

## Where the sentence is built (backend; the frontend renders it verbatim)

| Surface | Endpoint | JSON field | Builder | FE renderer |
|---|---|---|---|---|
| Matter feed | `GET /api/matters/{matterId}/activity?page&size` (Spring `Page<ActivityItem>`) | `content[].message` (also `entityName`, `entityType`, `eventType`) | `activity/ActivityMessageFormatter.formatMessage`; the default branch at line 153 gives `"%s performed %s on %s"` | `frontend/components/activity/activity-item.tsx:57` `{item.message}` (via `lib/actions/activity.ts` → `getMatterActivity`) |
| Home widget | `GET /api/dashboard/activity?limit=15` (list of `CrossMatterActivityItem`) | `[].description` (also `eventType`, `actorName`, `matterId`, `matterName`) | `dashboard/DashboardService.buildActivityDescription` (lines ~920–937). It only handles created/updated/deleted/uploaded, never reads `details`, and does not delegate to `ActivityMessageFormatter`. Actor comes from `COALESCE(m.name, details->>'actor_name','Unknown')` in `AuditEventRepository` lines 122/146, so portal contacts become "System". | `frontend/components/dashboard/recent-activity-widget.tsx` `{item.description}` (fetched in `lib/dashboard-home-fetch.ts:103`) |

Backend paths are relative to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/`.

## DB ground truth (db-audit.txt)

- Tenant schema: `tenant_7d218705360b` (`public.org_schema_mapping`, external_org_id `e2e-test-org`).
- The matter feed selects rows where `details->>'project_id' = matter` or the row is the matter's own `project` row. That gives 28 rows across 17 distinct event types. §1 lists the event_type/entity_type pairs, §2 the `details` keys per event type, §3 every matter row with full details, and §4 the 30 most recent tenant-wide events.
- The data for legal sentences is already in `details`:
  - trust rows: `amount`, `reference`, `customer_name`
  - `invoice.approved` / `sent` / `partially_paid`: `invoice_number`, plus `total`, `amount`, `balance_due`, `payment_method`, `payment_reference`
  - `acceptance.*`: `document_file_name`, `contact_name`, `acceptor_name`, `decline_reason`
  - `project.created_from_template`: `template_name`
  - Exception: `invoice.created` has no `invoice_number`, only `subtotal`, `currency` and `line_count`.
- Incidental: the latest 3 trust rows on this matter carry `customer_id` of Khumalo Property Holdings. That client was linked to the matter by `customer.linked` during 625A. This is expected and not a defect for 625B.

## Files

- `matter-activity.md`: all 28 matter-feed sentences verbatim, with FALLBACK/OK flags and reasons
- `home-recent-activity.md`: all 15 Home sentences verbatim, with flags and the same event's matter-feed sentence side by side
- `matter-activity-page1.png` (first 20 rows), `matter-activity-all.png` (all 28 rows), `home-recent-activity.png` (widget)
- `api-matter-activity.json`: raw `GET /api/matters/9a27cd08-.../activity?page=0&size=50` (totalElements 28)
- `api-matter-activity-closure-override-matter.json`: raw feed for matter `8ca3c73d-...` "625A observer closure override", for comparing Home rows 5–9
- `api-dashboard-activity.json`: raw `GET /api/dashboard/activity?limit=15`
- `db-audit.txt`: SQL ground truth

## Verdicts

- **G-D10 reproduced: YES.** The matter Activity feed is dominated by generic fallbacks (23 of 28) for legal events: trust, fee note, acceptance, client link and matter-from-template. Home uses a separate, weaker builder instead of delegating to the formatter.
- **G-D9 sentences reproduced: YES.** All 15 Home Recent Activity rows show raw dotted keys or raw entity tokens ("performed trust_payment.approved", "created a invoice"), and the portal-contact decline is attributed to "System".
