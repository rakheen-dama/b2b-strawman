# Slice 625D — BEFORE (reproduction on main b56371a2c)

Stack: e2e mock-auth. Frontend :3001 (image `docteams-e2e-frontend` built 2026-10-24 21:03Z; no commits to
`settings/firm/_tabs/`, `components/audit/`, `clients/[id]/audit-tab.tsx` or `lib/api/audit-events.ts` since, so the
FE under test equals main for these files). Backend :8081 rebuilt after 625C (labels + `/api/clients/{id}/activity` live).
Tenant schema `tenant_7d218705360b`. Captured 2026-10-27 by the reproducer. Read-only: logged in as Alice (owner), navigated
and filtered only. No DB writes, no record edits, no restarts.

Token recipe (token not saved):
`curl -s -X POST http://localhost:8090/token -H 'Content-Type: application/json' -d '{"userId":"user_e2e_alice","orgId":"org_e2e_test","orgSlug":"e2e-test-org","orgRole":"org:owner"}' | jq -r .access_token`

Screenshots were saved by Playwright MCP to `b2b-strawman/.playwright-mcp/625D-before/` (MCP file roots) and copied here.

## 1. Firm › Audit trail filters (G-D6, E.9) — REPRODUCED
URL: `/org/e2e-test-org/settings/firm?tab=audit-trail`
- Controls: Preset = Select; From/To = text; **Actor ID** = text, placeholder "UUID"; **Event type** = text, placeholder
  "e.g. security.login.failure"; **Entity type** = text, placeholder "e.g. customer"; **Entity ID** = text "UUID";
  Severity = toggle buttons. No member/actor picker, no entity-type or event-type Select. Unfiltered: "160 total", Page 1 of 4.
  → `625D-01-audit-trail-unfiltered-1440.png`
- Typed **"Matter"** into Entity type and pressed **Enter**: URL unchanged, still "160 total" — **Enter does not commit**.
  → `625D-03-entity-Matter-after-Enter-not-committed.png`
- Tab (blur) commits: URL `…&entityType=Matter` → **"0 total"**, "No audit events in this range…".
  → `625D-04-entity-Matter-on-blur-0-total.png` (API: `entityType=Matter` → totalElements 0)
- Raw key works: `?entityType=project&actorId=2e38845d-7fd5-4348-9b70-280c5d442df6` (Alice's UUID) → **19 total**: Matter created ×8,
  Matter created from template ×6, Matter closure closed ×4, Matter updated ×1. → `625D-05-actor-uuid-entity-project-19-total.png`,
  `api-audit-events-project-alice.json`
- Actor by name: `?actorId=Alice%20Owner` → whole settings page shows **"Something went wrong / Unable to load settings"**
  (Next 500; backend returns 400 for a non-UUID actorId). → `625D-06-actor-name-text-500.png`
- Actor display name on e2e: **"Alice Owner"** (owner, `2e38845d-…`). Others: "Bob Admin", "Carol Member", "Portal Contact", "System".
  (Scenario's "Thandi Mathebula" is the KC persona; not present on e2e.)

## 2. Labels (G-D10 audit labels) — PARTIAL (event labels already fixed by 625C backend; Entity column still raw)
- Event column (backend `label`, 625C): "Matter created from template", "Matter updated", "Matter created", "Client lifecycle transitioned",
  "Client created", "Checklist item completed", "Fee note partially paid" — legal sentence case. Remaining oddities:
  `invoice.created/approved/sent` all read just **"Fee note"** (prefix catalogue label, 628D follow-up); exact catalogue entries stay Title Case:
  "Matter Closure Override Used", "Security Event".
- **Entity column prints raw literals** (G-D6 last sentence): linked types render `{literal} {shortId}` — "project 8ca3c73d",
  "customer 31775a3a", "invoice 7b7e7dde", "trust_transaction a0e634c2", "matter_closure 013caa57"; unlinked types render
  `literal:shortId` — "checklist_instance_item:5831f9d6", "checklist_instance:e5278c50", "generated_document:5f69d320",
  "acceptance_request:85838bb1", "time_entry:5af9bad1", "security:a72a4ea0".
- Fee-note rows (`?entityType=invoice`, 11 total): Entity column "**invoice 7b7e7dde**" for INV-0003 (target: "Fee note INV-0003").
  → `625D-07-entity-invoice-raw-entity-column.png`, `api-audit-events-entity-invoice.json`
- Data note for the fix: `AuditEventResponse` has no entity display-name field. `details.invoice_number` is present on
  `invoice.approved/sent/partially_paid` rows but **absent on `invoice.created`** (draft has no number yet) — so "Fee note INV-0003"
  is derivable for 3 of 4 rows per fee note from details only.

## 3. Facet endpoints (exist, unused by the filter bar) — `api-facets-*.json` (default window now−30d)
- `GET /api/audit-events/facets/actors` → 4 entries: Alice Owner (USER, 116), Bob Admin (USER, 21), Carol Member (USER, 6),
  Portal Contact (PORTAL_CONTACT `65f788e3…`, 4). **No "System" entry** although 8 SYSTEM rows exist; also omitted: 3 WEBHOOK rows
  and 2 PORTAL_CONTACT rows with null actor_id (`sql-1-actor-types.txt`) — facet only lists non-null actor ids.
- `GET /api/audit-events/facets/entity-types` → 21 entries with legal labels from 625C (`project`→"Matter", `customer`→"Client",
  `invoice`→"Fee note", `legal_disbursement`→"Disbursement", `project_member`→"Matter team member"; rest sentence case e.g.
  "Checklist instance item", "Generated document"). Counts sum 160.
- `GET /api/audit-events/facets/event-types` → 50 entries `{eventType,label,severity,group,count}`; same labels as the Event column.

## 4. Client Activity tab (G-B2, E.9) — REPRODUCED
Fetcher: `clients/[id]/audit-tab.tsx` → `<AuditTimelineTab entityType="customer">` → server action `fetchEntityAuditPage`
→ backend `GET /api/audit-events/customer/{id}` (browser sees only a Next server-action POST to the page URL).
- **Acme Corp `b6dc030f-bb6f-45c6-844f-a673637c5da9`** (DORMANT): tab shows **4 rows** — Client lifecycle auto dormancy (System),
  Client lifecycle transitioned ×2, Client created; no from/to shown. → `625D-08-acme-client-activity-tab-4-rows.png`
  `GET /api/clients/{id}/activity` → **15**: the 4 + checklist.instance.created 1, checklist.item.skipped 8, checklist.item.completed 1,
  checklist.instance.completed 1; transition details carry `oldStatus`/`newStatus` (PROSPECT→ONBOARDING, **ONBOARDING→ACTIVE**).
  → `api-clients-activity-acme.json`, `api-audit-events-customer-acme.json`, `api-summary-client-feeds.txt`
- **Observer 625C Test Client `31775a3a-1ce2-4de0-8b26-a93202eda787`** (ONBOARDING): tab **2 rows** (Client lifecycle transitioned,
  Client created) vs activity endpoint **4** (+ checklist.instance.created, checklist.item.completed). → `625D-09-observer-client-activity-tab-2-rows.png`,
  `api-clients-activity-observer.json`
- Data reality (`sql-2-clients-checklists.txt`): **no client has nine completed FICA items**. Acme = 1 completed + 8 skipped (instance
  COMPLETED, lifecycle reached ACTIVE then auto-dormant); Observer = 1/9 completed; Sipho/Khumalo (mvmzw78f31c9) 0/9.

## 5. Overflow baseline (audit trail, unfiltered, 50 rows)
| viewport | doc scrollWidth / innerWidth | table scrollWidth / clientWidth | table wrapper (`overflow-x-auto`) sw/cw | `<main>` width |
|---|---|---|---|---|
| 1440 | 1440 / 1440 (no overflow) | 821 / 821 | 821 / 821 | 1215 |
| 1280 | **1406 / 1280 (+126 px page overflow)** | 821 / 821 | 821 / 821 | 1215 (does not shrink) |
- At 1280 the table fits its own wrapper; the page overflows because the shell column (`main.flex-1`, `div.flex.flex-1.flex-col`)
  keeps its 1215 px min-content (no `min-w-0`), so the audit panel ends at x=1366. Control: Firm › General at 1280 → 1280/1280 (no overflow).
  → `625D-02-audit-trail-1280-viewport-overflow.png`

Console: one pre-existing error on the audit-trail page (React #418 hydration text, also noted in 625C/before) — not investigated.
