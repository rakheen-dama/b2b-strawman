# Slice 625C — BEFORE (reproduction on main 2958399ba)

Stack: e2e mock-auth (frontend :3001, backend :8081, mock IDP :8090). Tenant schema `tenant_7d218705360b` (org `e2e-test-org`).
Captured 2026-10-27 by the reproducer. Read-only: no source edits, no DB writes, no UI mutations.

Token recipe (bearer token never saved in these files):
```
curl -s -X POST http://localhost:8090/token -H 'Content-Type: application/json' \
  -d '{"userId":"user_e2e_alice","orgId":"org_e2e_test","orgSlug":"e2e-test-org","orgRole":"org:owner"}' | jq -r .access_token
```
(Carol: `user_e2e_carol` / `org:member`; Bob: `user_e2e_bob` / `org:admin`. Same as `frontend/e2e/fixtures/auth.ts#getApiToken`.)

## 1. Registry labels — PASS-reproduced
- `GET /api/audit-events/metadata` (TEAM_OVERSIGHT) → 200, 33 entries. `invoice.*` ⇒ label **"Invoice"**. Only the exact
  keys `invoice.partially_paid` / `invoice.payment_excess` say "Fee note …". → `api-1-audit-metadata.json`
- Resolved labels on stored rows (`GET /api/audit-events?size=200`, `label` field = `AuditEventTypeRegistry.resolve`) → `api-2-resolved-labels.txt`:
  - `invoice.created/approved/sent` ⇒ "Invoice"
  - unregistered titleCase fallbacks: `project.created` ⇒ "Project Created", `project.created_from_template` ⇒ "Project Created From Template",
    `project.updated` ⇒ "Project Updated", `project_member.added/removed/role_changed` ⇒ "Project Member Added/Removed/Role Changed",
    `customer.created` ⇒ "Customer Created", `customer.linked` ⇒ "Customer Linked", `customer.lifecycle.transitioned` ⇒ "Customer Lifecycle Transitioned",
    `customer.lifecycle.auto_dormancy` ⇒ "Customer Lifecycle Auto Dormancy", `checklist.*` ⇒ "Checklist Instance/Item …".
  - No `legal_disbursement.*` or `project_budget.*` rows are stored in this tenant (`sql-1-event-type-counts.txt`, 50 types / 154 rows) — those two prefixes not observable here; by code they would titleCase to "Legal Disbursement …" / "Project Budget …".
- Browser: Settings › Firm › Audit trail shows "Invoice" for invoice.sent/approved/created and "Project Created From Template" with entity link text `project`/`invoice`
  → `625C-01-firm-audit-trail-labels-fullpage.png`; filtered `?eventType=project_member.added` → 3 rows "Project Member Added" → `625C-02-firm-audit-trail-project_member-added.png`.
  (Console shows a pre-existing React #418 hydration-text warning on this page — unrelated, not investigated.)

## 2. Client Activity tab misses checklist events (E.9) — PASS-reproduced
Client: **Acme Corp `b6dc030f-bb6f-45c6-844f-a673637c5da9`** (only client with a completed checklist: instance `ba78be61…` COMPLETED, 1 item COMPLETED + 8 SKIPPED; events are boot-seed rows written via product audit). Sipho/Khumalo instances are IN_PROGRESS with 0 completions (`sql-2a-clients-with-checklists.txt`). No UI mutation was needed.
- Tab shows **4 rows**: Customer Lifecycle Auto Dormancy, Customer Lifecycle Transitioned ×2, Customer Created → `625C-03-acme-client-activity-tab.png`
- Call chain: browser POSTs a Next server action to `/org/e2e-test-org/clients/{id}?tab=audit` → `fetchEntityAuditPage` (`frontend/lib/actions/audit-events.ts`) →
  backend `GET /api/audit-events/customer/{id}?page=0&size=20` (`@RequiresCapability("TEAM_OVERSIGHT")`). Replayed → totalElements 4 (`api-3-client-tab-audit-events-customer-acme.json`).
  As Carol (member) → 403 "Insufficient permissions" (`api-3b-…-carol-member.json`); the tab itself is hidden by `useAuditTabVisible()` for non-TEAM_OVERSIGHT.
- Expected per three-branch rule (`sql-2b-acme-expected-three-branch-counts.txt`):
  - A (entity_type='customer' AND entity_id=client): **4** — customer.created 1, customer.lifecycle.transitioned 2, customer.lifecycle.auto_dormancy 1
  - B (child entities): **11** — checklist_instance: checklist.instance.created 1, checklist.instance.completed 1; checklist_instance_item: checklist.item.completed 1, checklist.item.skipped 8; information_request / request_item / portal_contact: 0 (no such events exist tenant-wide)
  - C (details->>'customer_id' = client): **0**
  - Union distinct: **15** vs tab **4** → 11 checklist rows missing.

## 3. customer_id missing on checklist events — PASS-reproduced
`sql-3-checklist-events-details.txt` (12 most recent checklist rows): `details ? 'customer_id'` = false and `details ? 'project_id'` = false on **all 12**.
- `checklist.instance.*` carry camelCase `customerId` (not `customer_id`).
- `checklist.item.*` carry only `instanceId` / `itemName` (+ notes/reason/completedBy) — no customer reference at all.

## 4. `GET /api/clients/{id}/activity` — PASS-reproduced (absent)
→ 404 `"No static resource api/clients/b6dc…/activity."` (`api-4-clients-activity-acme.json`). `/api/customers/{id}/activity` also 404. `/api/clients/{id}` → 200 (route family exists).
