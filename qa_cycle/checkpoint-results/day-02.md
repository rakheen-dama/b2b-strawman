# Day 2 — Onboard Sipho as client, run conflict check + KYC — Cycle 6 Results

**Branch**: `main` (post-merge of PR #1164, commit `68c71cb8`)
**Date**: 2026-04-26 SAST (UTC 2026-04-26T20:13–20:21)
**Tenant**: `mathebula-partners` (tenant_5039f2d497cf)
**Actor**: Bob Ndlovu (Admin) — `bob@mathebula-test.local` / `SecureP@ss2`
**Stack**: Keycloak dev stack — frontend :3000, BFF gateway :8443, backend :8080, KC :8180

## Context swap

Closed prior browser context (Day 1 was Thandi). Fresh login as Bob via Keycloak OIDC at `http://localhost:8180/realms/docteams/...`. Form filled `bob@mathebula-test.local` + `SecureP@ss2`, redirected to `/org/mathebula-partners/dashboard`. Sidebar shows logo + "Mathebula & Partners" + "Bob Ndlovu / bob@mathebula-test.local". Brand-color rendering still navy in sidebar (GAP-L-90 fix holding for a different KC user — confirms fix is tenant-scoped, not user-scoped).

## Checkpoint Results

| ID | Description | Result | Evidence |
|----|-------------|--------|----------|
| 2.1 | Navigate to **Clients** → click **+ New Client** | PASS | Sidebar Clients group expanded → click Clients link → `/customers` page loaded with empty state, **+ New Client** button visible. Click opened "Create Client" modal (Step 1 of 2). |
| 2.2 | Verify dialog shows legal-specific promoted fields for INDIVIDUAL (ID number, preferred contact, matter_type hint) | PASS | Step 1 of 2 has core fields including ID Number (optional). Step 2 of 2 ("Additional Information") offers a collapsible **SA Legal — Client Details** field-pack panel with 4 fields: ID/Passport Number, Postal Address, Preferred Correspondence (Email/Post/Hand Delivery combobox), Referred By. matter_type hint not surfaced in client-create flow (per scenario it's a "hint" rather than required — likely on matter create instead, see Day 3). |
| 2.3 | Fill INDIVIDUAL / Sipho / Dlamini / sipho.portal@example.com / 8501015800088 / +27 82 555 0101 / 12 Loveday St JHB 2001 | PASS (with naming note) | Form has single **Name** field (not separate First/Last); filled `Sipho Dlamini`. Email/Phone/ID Number/Address Line 1=`12 Loveday St`/City=`Johannesburg`/Postal=`2001`/Country=`South Africa (ZA)` filled. SA Legal pack: ID/Passport=`8501015800088`, Preferred Correspondence=`Email`. Note: scenario says "First Name + Last Name" but UI uses single Name field — minor naming-only divergence, not a defect. Screenshot `day-02-2.3-legal-pack-form.png`. |
| 2.4 | Submit → client created, redirected to client detail | PASS | Create Client clicked → redirected to `/customers/c4f70d86-c292-4d02-9f6f-2e900099ba57`. Client header: "Sipho Dlamini / Active / Prospect / sipho.portal@example.com / +27 82 555 0101 · 8501015800088 · Created Apr 26, 2026". DB confirms: `customers` row with `customer_type=INDIVIDUAL, lifecycle_status=PROSPECT, custom_fields={"id_passport_number":"8501015800088","preferred_correspondence":"EMAIL"}`. Screenshot `day-02-2.4-client-detail.png`. |
| 2.5 | On client detail → click **Run Conflict Check** | **PARTIAL — BUG-CYCLE26-03** | **No "Run Conflict Check" button on client detail page.** Action bar has only: Change Status / Generate Document / Export Data / Edit / Archive. Conflict check is only reachable via the global `/conflict-check` page (sidebar Clients group → Conflict Check). Used the global page; selected Sipho from the Customer dropdown. Functionally available but UX miss vs scenario expectation of in-context launch. |
| 2.6 | Result = **CLEAR** (no pre-existing records) — green confirmation state renders | PASS | Conflict Check page result: green check icon, heading **"No Conflict"**, body "Checked 'Sipho Dlamini' at 26/04/2026, 22:16:31", green badge **"No Conflict"**. History tab now (1). UI label is "No Conflict" rather than "CLEAR" — semantically equivalent. DB: `conflict_checks` row `result=NO_CONFLICT, check_type=NEW_CLIENT, customer_id=c4f70d86-...` linked. |
| 2.7 | 📸 Screenshot: `day-02-conflict-check-clear.png` — CLEAR result badge | PASS | `qa_cycle/checkpoint-results/day-02-conflict-check-clear.png` — green No Conflict result card visible. |
| 2.8 | On client detail → click **Run KYC Verification** (if KYC adapter configured) | **PARTIAL — BUG-CYCLE26-04** | **No "Run KYC Verification" button on client detail.** Adapter is also functionally NOT configurable: Settings > Integrations > KYC Verification → Configure dialog persists `org_integrations` row (`domain=KYC_VERIFICATION, provider_slug=verifynow, enabled=false`) but provides **no UI toggle to enable it** — no "Enable" checkbox/switch in the Configure dialog or on the integrations card. After Save, status shows "Disabled" persistently. Test Connection returns "No KYC provider configured". The backend has KYC adapter classes (`CheckIdKycAdapter`, `VerifyNowKycAdapter`) but the firm UI cannot bring them online. |
| 2.9 | KYC adapter returns **Verified** — KYC badge renders green with verification timestamp | **SKIPPED** (gated by 2.8) | Cannot exercise — no run button + adapter cannot be enabled. Per scenario instruction "if KYC adapter configured; otherwise skip and note in gap report". Logged as BUG-CYCLE26-04. |
| 2.10 | 📸 Screenshot: `day-02-kyc-verified.png` — KYC verified status | **SKIPPED** (gated by 2.8) | Not capturable. |

## Day 2 wrap-up checks (per scenario)

- [x] Client created with INDIVIDUAL type and legal-specific fields (SA Legal pack data persisted in `customers.custom_fields`)
- [x] Conflict check CLEAR (no false positive hits) — `result=NO_CONFLICT`, history shows the run
- [ ] KYC verification badge visible on client detail — **NOT MET** (BUG-CYCLE26-04 — no UI affordance + adapter cannot be enabled). Per scenario, allowable to skip with gap-report entry; cycle continues.

## Bugs / observations opened this day

- **BUG-CYCLE26-03** — **Severity: BUG** (UX miss). Client detail page (`/customers/[id]`) has no in-context **Run Conflict Check** button. Conflict check is only reachable from sidebar > Clients > Conflict Check (global page). Demo wow-moment expectation is "on the client record, click Run Conflict Check" — currently the firm-side user has to leave the client and navigate elsewhere, then re-select the client from a dropdown. Recommend: add a "Run Conflict Check" action button (or section) to the client detail header, pre-populating the form with the client's name + ID. Functionally not blocking — Conflict Check page works correctly and links back via `customer_id` FK.

- **BUG-CYCLE26-04** — **Severity: BUG** (demo blocker for Day 2 wow moment #1). Two related defects:
  1. Client detail page has no **Run KYC Verification** button. KYC is not surfaced on client detail at all (no tab, no action button).
  2. Settings > Integrations > KYC Verification > Configure dialog persists the integration row but lands `enabled=false` and provides no UI mechanism to toggle it on. Status badge shows "Disabled" after save. Test Connection responds "No KYC provider configured". Backend adapters (`CheckIdKycAdapter`, `VerifyNowKycAdapter`) exist in code but cannot be brought online from the firm UI.

  Recommend: (a) add Enable toggle to KYC Configure dialog (or auto-enable on successful Test Connection), (b) add "Run KYC Verification" action on client detail (gated on the adapter being enabled), (c) render KYC status badge on client detail when verified. Functionally non-cascading — Day 2 says "skip if not configured" — but Day 2 demo wow moment #1 lists "Conflict CLEAR + KYC Verified" so this directly impacts the demo storyline.

## Pre-existing carry-over

- **GAP-L-90 fix holding** — Sidebar logo and brand-color (navy `#1B3358`) render correctly for Bob (different KC user, same tenant). Confirms the fix is tenant-scoped, not user-scoped.
- **BUG-CYCLE26-01/02 fix holding** — All form fills on this day worked through Playwright MCP `fill()` without native-setter fallback (Email, Phone, Name, ID, City, Postal, ID/Passport). Form-binding fix applies broadly. Only one Radix Select still required JS click on the Preferred Correspondence option (different control class from the team-invite Radix; not regressing the team-invite fix).

## Console errors observed

- 0 functional console errors during Day 2 navigation.
- Pre-existing KC favicon 404 not seen this session (no logout/relogin during Day 2).

## DB evidence

```
SELECT id, name, customer_type, email, phone, id_number, lifecycle_status, custom_fields
FROM tenant_5039f2d497cf.customers WHERE id='c4f70d86-c292-4d02-9f6f-2e900099ba57';

c4f70d86-c292-4d02-9f6f-2e900099ba57 | Sipho Dlamini | INDIVIDUAL | sipho.portal@example.com |
  +27 82 555 0101 | 8501015800088 | PROSPECT |
  {"id_passport_number": "8501015800088", "preferred_correspondence": "EMAIL"}

SELECT id, checked_name, checked_id_number, check_type, result, customer_id, checked_at
FROM tenant_5039f2d497cf.conflict_checks ORDER BY checked_at DESC;

f5775f7b-78bf-439c-b1f9-218c57a65c4a | Sipho Dlamini | 8501015800088 | NEW_CLIENT |
  NO_CONFLICT | c4f70d86-c292-4d02-9f6f-2e900099ba57 | 2026-04-26 20:16:31.870619+00

SELECT domain, provider_slug, enabled, key_suffix
FROM tenant_5039f2d497cf.org_integrations;

KYC_VERIFICATION | verifynow | f | ey-123    -- enabled=false; no UI to enable
```

## Wow-moment screenshots captured

- `day-02-2.3-legal-pack-form.png` — Step 2 dialog with SA Legal — Client Details panel expanded.
- `day-02-2.4-client-detail.png` — Sipho Dlamini client detail, full page.
- `day-02-conflict-check-clear.png` — **wow moment** — green No Conflict result on Conflict Check page.
- `day-02-sipho-client-detail.png` — Final view of Sipho's record, viewport screenshot.

KYC wow moment NOT capturable (BUG-CYCLE26-04).

## Summary

**5 PASS / 2 PARTIAL / 0 FAIL / 0 BLOCKER / 2 SKIPPED** for Day 2 (10 sub-checkpoints + 3 wrap-up checks).

PARTIALs:
- 2.5 (BUG-CYCLE26-03): No in-context Run Conflict Check button on client detail — workaround via global page works.
- 2.8 (BUG-CYCLE26-04): No in-context Run KYC button + KYC adapter cannot be enabled in UI — gates 2.9 + 2.10 (skipped).

Both PARTIALs are non-cascading — Day 3+ does not depend on KYC being run; conflict check is logged in DB and traceable via `customer_id`. Day 2 demo wow moment #1 (Conflict CLEAR) is captured; Day 2 wow moment #2 (KYC Verified) is gapped pending dev fix.

Cycle proceeds to Day 3 (Bob still logged in — same actor for Day 3).

## Cycle 6 verification 2026-04-26 SAST (UTC 20:25–20:34) — branch `bugfix_cycle_2026-04-26-day2`

Re-entered the cycle after user-directed release of the Day-2 hold. Confirmed Day 2 results above are still valid against `main` HEAD (commit `25e23125`):

| Check | Result | Evidence |
|-------|--------|----------|
| Sipho still in DB with full Day-2 fields | PASS | `tenant_5039f2d497cf.customers` id=`c4f70d86-…`, name=`Sipho Dlamini`, email=`sipho.portal@example.com`, phone=`+27 82 555 0101`, id_number=`8501015800088`, customer_type=`INDIVIDUAL`, lifecycle_status=`PROSPECT`, address=`12 Loveday St / Johannesburg / 2001 / ZA`, custom_fields=`{"id_passport_number":"8501015800088","preferred_correspondence":"EMAIL"}`. |
| Step-2 SA Legal — Client Details promoted fields still rendered in Create Client wizard | PASS | Probe-only New Client dialog → Step 2 → SA Legal — Client Details → Additional Information (4) expanded → 4 fields visible (ID/Passport Number, Postal Address, Preferred Correspondence, Referred By). Dialog cancelled. |
| Conflict-check workspace functional, returns NO_CONFLICT for Sipho | PASS | `/conflict-check` Run Check tab → fills Name=`Sipho Dlamini`, ID=`8501015800088`, Customer link → Sipho → Run. Result panel "No Conflict" badge + "Checked 'Sipho Dlamini' at 26/04/2026, 22:28:33". DB: 2nd `conflict_checks` row id=`a6529e36-…` at 20:28:33 UTC, both NO_CONFLICT, 0 false positives. History tab now shows (2). |
| BUG-CYCLE26-03 still reproducible | OPEN | Client detail action bar still: Change Status / Generate Document / Export Data / Edit / Archive. No Run-Conflict-Check shortcut. Confirmed unchanged. |
| BUG-CYCLE26-04 still reproducible | OPEN | Re-opened Settings > Integrations > KYC Verification > Configure → entered API key `sk-verifynow-demo-test-key-12345` → Save → DB `org_integrations` row updated `key_suffix='-12345'`, `enabled` STILL `false`. KYC card still "Disabled" badge. No Enable toggle exposed. Same root cause as the original cycle-6 finding. |
| Screenshot tooling | DEGRADED | `mcp__playwright__browser_take_screenshot` repeatedly times out today (`Timeout 5000ms exceeded after fonts loaded`) — DOM YAML used as substitute (see `day-02-conflict-check-clear.yml`). Note prior cycle-6 run produced `day-02-conflict-check-clear.png` successfully — tooling regression mid-session. Logging as `BUG-CYCLE26-05` (LOW, tooling-only). |

**Verification stop**: Day 2 is complete, all gaps observed in the prior cycle-6 run still reproduce on `main`. Advancing QA Position to Day 3 / 3.1.

