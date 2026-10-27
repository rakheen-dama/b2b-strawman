# Home — Recent Activity widget — BEFORE (625B reproducer)

URL: http://localhost:3001/org/e2e-test-org/dashboard (Alice Owner, owner role → admin/owner branch `findCrossProjectActivity`), captured 2026-10-27 ~11:16 UTC
Rendered rows: 15 (widget limit=15; "View all activity →" button is a no-op). Rendered text == `description` field of `GET /api/dashboard/activity?limit=15` (api-dashboard-activity.json).
Screenshot: home-recent-activity.png

| # | occurred_at (UTC) | event_type | Matter | Home sentence (verbatim) | Flag | Same event in matter feed (verbatim) |
|---|---|---|---|---|---|---|
| 1 | 2026-10-25T00:45:38 | `trust_payment.approved` | Estate Late Ndlovu 625A repro | Bob Admin performed trust_payment.approved | FALLBACK | Bob Admin performed trust_payment.approved on trust_transaction |
| 2 | 2026-10-25T00:45:21 | `trust_payment.recorded` | Estate Late Ndlovu 625A repro | Alice Owner performed trust_payment.recorded | FALLBACK | Alice Owner performed trust_payment.recorded on trust_transaction |
| 3 | 2026-10-25T00:44:53 | `trust_deposit.recorded` | Estate Late Ndlovu 625A repro | Alice Owner performed trust_deposit.recorded | FALLBACK | Alice Owner performed trust_deposit.recorded on trust_transaction |
| 4 | 2026-10-25T00:44:05 | `customer.linked` | Estate Late Ndlovu 625A repro | Alice Owner performed customer.linked | FALLBACK | Alice Owner performed customer.linked on customer |
| 5 | 2026-10-25T00:30:26 | `statement.generated` | 625A observer closure override | Alice Owner performed statement.generated | FALLBACK | Alice Owner generated a statement of account "statement-of-account-625a-observer-closure-override-2026-10-25.pdf" |
| 6 | 2026-10-25T00:30:26 | `document.generated` | 625A observer closure override | Alice Owner performed document.generated | FALLBACK | Alice Owner generated document "matter-closure-letter-625a-observer-closure-override-2026-10-25.pdf" from template "Matter Closure Letter" |
| 7 | 2026-10-25T00:30:26 | `matter.closure.override_used` | 625A observer closure override | Alice Owner performed matter.closure.override_used | FALLBACK | Alice Owner used an override to close the matter |
| 8 | 2026-10-25T00:30:26 | `matter_closure.closed` | 625A observer closure override | Alice Owner performed matter_closure.closed | FALLBACK | Alice Owner closed the matter |
| 9 | 2026-10-25T00:30:05 | `project.created_from_template` | 625A observer closure override | Alice Owner performed project.created_from_template | FALLBACK | Alice Owner performed project.created_from_template on project |
| 10 | 2026-10-25T00:28:58 | `invoice.partially_paid` | Estate Late Ndlovu 625A repro | Alice Owner performed invoice.partially_paid | FALLBACK | Alice Owner performed invoice.partially_paid on invoice |
| 11 | 2026-10-25T00:28:39 | `invoice.sent` | Estate Late Ndlovu 625A repro | Alice Owner performed invoice.sent | FALLBACK | Alice Owner performed invoice.sent on invoice |
| 12 | 2026-10-25T00:28:28 | `invoice.approved` | Estate Late Ndlovu 625A repro | Alice Owner performed invoice.approved | FALLBACK | Alice Owner performed invoice.approved on invoice |
| 13 | 2026-10-25T00:28:11 | `invoice.created` | Estate Late Ndlovu 625A repro | Alice Owner created a invoice | FALLBACK | Alice Owner performed invoice.created on invoice |
| 14 | 2026-10-25T00:26:59 | `time_entry.created` | Estate Late Ndlovu 625A repro | Alice Owner created a time_entry | FALLBACK | Alice Owner logged 2h on task "Open estate bank account" |
| 15 | 2026-10-25T00:26:34 | `acceptance.declined` | Estate Late Ndlovu 625A repro | System performed acceptance.declined | FALLBACK | Sipho Ndlovu mvmzw78f31c9 performed acceptance.declined on acceptance_request |

**Totals: 15 rows — 15 FALLBACK/raw, 0 OK.** 13 read `<actor> performed <event_type>` (raw dotted key; one of them, `acceptance.declined`, also misattributes the actor as "System"); 2 read `<actor> created a <raw_entity>` (`created a invoice`, `created a time_entry` — raw entity token + broken article).

## Home vs matter feed — same event rendered differently
Home uses a separate, cruder builder: `DashboardService.buildActivityDescription` (backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardService.java:920-937) — it only knows created/updated/deleted/uploaded and never reads `details`. It does NOT delegate to `ActivityMessageFormatter`. Divergences observed on the same audit event id:
- `time_entry.created`: Home "Alice Owner created a time_entry" vs matter "Alice Owner logged 2h on task \"Open estate bank account\"".
- `statement.generated`, `document.generated`, `matter.closure.override_used`, `matter_closure.closed` (matter "625A observer closure override"): Home is raw `performed <key>`; the matter feed has proper sentences (e.g. "Alice Owner closed the matter").
- `acceptance.declined` (actor_type PORTAL_CONTACT): Home "System performed acceptance.declined" vs matter "Sipho Ndlovu mvmzw78f31c9 performed acceptance.declined on acceptance_request". Home actor comes from `COALESCE(m.name, ae.details->>'actor_name', 'Unknown')` (AuditEventRepository.java:122/146) — members join only, so a portal contact falls back to details.actor_name = "System" (actor misattribution on Home).
- Every other row: Home drops the ` on <entity_type>` suffix but is otherwise the same raw fallback.
Home rows carry no matter name in the sentence (matterName is in the JSON but not rendered).
