# Fix Spec: GAP-L-33 — FICA Onboarding Pack request template missing for legal-za

## Problem

Day 3 Checkpoint 3.8 halted on BLOCKER: the Create Information Request dialog on matter `RAF-2026-001` lists 5 platform templates (Annual Audit Document Pack, Company Registration, Conveyancing Intake (SA), Monthly Bookkeeping, Tax Return Supporting Docs) plus Ad-hoc — **no "FICA Onboarding Pack"**. Scenario 3.8–3.12 hard-wires this template with three items (ID copy, Proof of residence ≤ 3 months, Bank statement ≤ 3 months) and 3.10 assumes pre-filled items. Cascades into Days 4, 5, 8, 11, 30, 46, 61, 75 (every portal POV day).

Evidence (from Day 3 QA turn):

- `SELECT name FROM tenant_5039f2d497cf.request_templates ORDER BY name;` → 5 rows, no FICA.
- `ls backend/src/main/resources/request-packs/` → 7 JSON files (`annual-audit`, `company-registration`, `consulting-za-creative-brief`, `conveyancing-intake-za`, `monthly-bookkeeping`, `tax-return`, `year-end-info-request-za`). No `fica-onboarding-pack.json`.

## Root Cause (confirmed)

**Missing asset.** No code bug. The `RequestPackSeeder` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestPackSeeder.java`) extends `AbstractPackSeeder` and auto-scans `classpath:request-packs/*.json` at tenant provisioning and at startup reconciliation (invoked from `TenantProvisioningService.provisionTenant` line 177 and `PackReconciliationRunner` line 110). The base seeder (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/AbstractPackSeeder.java` lines 130–141) filters each pack by its `verticalProfile` against the tenant's `org_settings.vertical_profile`. So:

- Dropping a new JSON in `backend/src/main/resources/request-packs/` with `"verticalProfile": "legal-za"` is sufficient — the seeder picks it up with no code change.
- Reconciliation runs for every existing tenant at backend startup, so the `tenant_5039f2d497cf` legal-za tenant will get the template seeded after a backend restart.
- Existing parallel (`conveyancing-intake-za.json`) confirms shape — record DTO is `RequestPackDefinition` with `packId / version / verticalProfile / name / description / items[]`; each item is `name / description / responseType (TEXT_RESPONSE | FILE_UPLOAD) / required / fileTypeHints / sortOrder`.

Vertical-profile registry file (`backend/src/main/resources/vertical-profiles/legal-za.json` line 13) currently lists `"request": ["conveyancing-intake-za"]`. That list is descriptive metadata for the vertical — the actual seeding decision is driven by each pack's own `verticalProfile` field. For completeness and future consumers of that list, add `"fica-onboarding-pack"` to it.

## Fix

**Step 1.** Create `backend/src/main/resources/request-packs/fica-onboarding-pack.json`:

```json
{
  "packId": "fica-onboarding-pack",
  "version": 1,
  "verticalProfile": "legal-za",
  "name": "FICA Onboarding Pack",
  "description": "FICA (Financial Intelligence Centre Act) onboarding pack for a new individual client of a South African law firm. Captures the three core FICA documents required to open a matter: certified ID copy, recent proof of residential address, and recent bank statement evidencing source of funds.",
  "items": [
    {
      "name": "ID copy",
      "description": "Certified copy of the client's South African ID document or passport bio page. Must be certified by a Commissioner of Oaths, SAPS, or other accepted certifier within the last 3 months.",
      "responseType": "FILE_UPLOAD",
      "required": true,
      "fileTypeHints": "PDF, JPG, PNG",
      "sortOrder": 1
    },
    {
      "name": "Proof of residence (≤ 3 months)",
      "description": "Recent utility bill, municipal rates account, bank statement, or similar document confirming the client's residential address. Document date must be within the last 3 months.",
      "responseType": "FILE_UPLOAD",
      "required": true,
      "fileTypeHints": "PDF, JPG, PNG",
      "sortOrder": 2
    },
    {
      "name": "Bank statement (≤ 3 months)",
      "description": "Most recent bank statement evidencing the client's source of funds. Statement must be dated within the last 3 months and show the client's name, account number, and at least one transaction.",
      "responseType": "FILE_UPLOAD",
      "required": true,
      "fileTypeHints": "PDF",
      "sortOrder": 3
    }
  ]
}
```

**Step 2.** Update `backend/src/main/resources/vertical-profiles/legal-za.json` line 13 to include the new pack in the metadata list:

```json
"request": ["conveyancing-intake-za", "fica-onboarding-pack"]
```

**Step 3.** Restart backend — `bash compose/scripts/svc.sh restart backend`. `PackReconciliationRunner` fires on startup and will seed the template into every legal-za tenant schema (including the live `tenant_5039f2d497cf`).

No Java code changes. No migration. No frontend changes.

## Scope

- Backend only (pure asset + metadata update)
- Files to modify:
  - `backend/src/main/resources/vertical-profiles/legal-za.json` (append to `packs.request`)
- Files to create:
  - `backend/src/main/resources/request-packs/fica-onboarding-pack.json`
- Migration needed: no

## Verification

1. Backend restart completes cleanly (no parse error on the new JSON). Tail `.svc/logs/backend.log` for line `Applied request pack fica-onboarding-pack v1 for tenant tenant_5039f2d497cf`.
2. DB read-only:
   ```bash
   docker exec b2b-postgres psql -U postgres -d docteams -c \
     "SELECT name, pack_id FROM tenant_5039f2d497cf.request_templates WHERE pack_id='fica-onboarding-pack';"
   ```
   Expect 1 row. Items:
   ```bash
   docker exec b2b-postgres psql -U postgres -d docteams -c \
     "SELECT name, response_type, sort_order FROM tenant_5039f2d497cf.request_template_items WHERE template_id=(SELECT id FROM tenant_5039f2d497cf.request_templates WHERE pack_id='fica-onboarding-pack') ORDER BY sort_order;"
   ```
   Expect 3 rows in sort order: "ID copy / FILE_UPLOAD / 1", "Proof of residence (≤ 3 months) / FILE_UPLOAD / 2", "Bank statement (≤ 3 months) / FILE_UPLOAD / 3".
3. Re-run Day 3 Checkpoint 3.8: Create Information Request dialog on matter RAF-2026-001 → Template dropdown now lists "FICA Onboarding Pack (3 items)" in the options.
4. Confirm other legal-za tenants (if any) also received the template via `SELECT schema_name FROM public.org_schema_mapping` then the same items probe per-schema.
5. Idempotency check: restart backend a second time — log line should become `Request pack fica-onboarding-pack already applied for tenant ..., reconciling settings` (no duplicate rows).

## Estimated Effort

**S (< 30 min)** — one JSON file + one metadata line + backend restart + QA re-verify. No tests need authoring (pack seeder is already covered by `RequestPackSeederTest` or equivalent; adding a new pack is covered by scanning logic, not per-pack unit tests).
