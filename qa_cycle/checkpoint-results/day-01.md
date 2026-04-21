# Day 1 — Firm onboarding polish
Cycle: 1 | Date: 2026-04-21 | Auth: Keycloak | Frontend: :3000 | Portal: n/a (firm-only day)

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 1 (checkpoints 1.1–1.7).

Result summary: **2/7 checkpoints executed. 0 PASS, 1 BLOCKED (1.1), 1 PARTIAL (setup path discovery only). Remaining (1.2–1.7) halted per BLOCKER rule.** New BLOCKER gap: `GAP-L-23` (HIGH — all server actions on `/settings/general` throw `ReferenceError: PortalDigestCadence is not defined` at module evaluation, causing every Save Settings / logo upload / brand colour / portal digest mutation to return 500). Additional non-blocking gaps: `GAP-L-24` (MED — `/settings/general` shows "Failed to load profiles" card, Vertical Profile Section cannot fetch profile list), `GAP-L-25` (LOW — no `SECTION_86` option in Trust Account type dropdown; only `General` / `Investment` offered — Checkpoint 1.5 cannot be completed even with 1.1 fixed).

## Session prep — Thandi sign-in (post-Day-0 workaround for GAP-L-22)

Per `day-00.md` Checkpoint 0.21/0.32 notes and `status.md` GAP-L-22, opened a fresh browser context. Explicit KC logout (`http://localhost:8180/realms/docteams/protocol/openid-connect/logout` → Logout button). Fresh OIDC via `http://localhost:8443/oauth2/authorization/keycloak` → filled `thandi@mathebula-test.local` / `SecureP@ss1` → landed cleanly on `http://localhost:3000/org/mathebula-partners/dashboard`. Sidebar correctly shows "TM / Thandi Mathebula / thandi@mathebula-test.local". No session-handoff leak this turn — GAP-L-22 workaround held.

## Checkpoint 1.1 — Upload firm logo + set brand colour `#1B3358`, Save
- Result: **BLOCKED**
- Scenario path: "Settings > Organization" — actual path is Settings > **General** (`/settings/general`). "Organization" submenu does not exist; General covers branding. Path naming is a copy-only discrepancy, not the blocker.
- Evidence — behaviour walk-through:
  - Clicked sidebar **Settings** → landed on `/org/mathebula-partners/settings/general`.
  - Page GET renders OK (200): shows "Vertical Profile" card, Currency = ZAR (pre-seeded), Tax config, Branding (Logo + Brand Color + Document Footer), Client Portal (digest cadence + retainer display), Org Documents.
  - Vertical Profile card shows **"Failed to load profiles."** with Apply Profile disabled → logged as `GAP-L-24`.
  - Typed `#1B3358` into Brand Color text input → swatch preview updated to navy.
  - Clicked **Save Settings** → **500 Internal Server Error**.
  - Reloaded page → Brand Color reverted to `#000000`. Nothing persisted.
  - Read-only DB check (diagnostics only): `SELECT brand_color, logo_s3_key, document_footer_text FROM tenant_5039f2d497cf.org_settings;` returns empty `brand_color`, null `logo_s3_key`, null `document_footer_text`. Confirms save really did fail end-to-end.
  - Console (browser): 11 errors, all `Failed to load resource: 500 @ /org/mathebula-partners/settings/general`.
  - Network (browser devtools): **17 × POST `/org/mathebula-partners/settings/general` → 500 Internal Server Error** over the turn (each server-action invocation that the page attempts — page load fires `fetchProfiles`, clicking Save fires the general-settings-form action, etc.).
  - Frontend server log (`.svc/logs/frontend.log`): every failing POST prints:
    ```
    ⨯ ReferenceError: PortalDigestCadence is not defined
        at module evaluation (.next/dev/server/chunks/ssr/…frontend_9b4c4642._.js:401:5)
        at module evaluation (.next-internal/server/app/(app)/org/[slug]/settings/general/page/actions.js (server actions loader):15:1)
        at module evaluation (…frontend_9b4c4642._.js:570:1598)
      13 | export {fetchProfiles ... } from 'ACTIONS_MODULE3'
      14 | export {updateVerticalProfile ... } from 'ACTIONS_MODULE3'
    > 15 | export {updatePortalDigestCadence ... } from 'ACTIONS_MODULE4'
      16 | export {updatePortalRetainerMemberDisplay ... } from 'ACTIONS_MODULE4'
      ...
    ```
  - Root cause (suspected, not fixed): `frontend/app/(app)/org/[slug]/settings/general/portal-actions.ts` line 10 does `export type { PortalDigestCadence, PortalRetainerMemberDisplay };`. The Next.js 16 turbopack server-actions loader bundle appears to elide the `import type { PortalDigestCadence } from '@/lib/types/settings'` at module-bundle time but leaves the re-export referencing an undefined identifier, so the **entire** server-actions loader module for `settings/general/page/actions.js` fails on first invocation — which means **every** action on this page (including `updateGeneralSettings`, `initiateOrgUpload`, `confirmOrgUpload`, `fetchProfiles`, `updateVerticalProfile`) crashes before the action body runs. Not just the portal digest cadence action.
  - Screenshot: `qa_cycle/checkpoint-results/day-01-1.1-save-settings-500.png` (shows page with Brand Color reverted to `#000000` and "Failed to load profiles" card after reload).
- Gap: **GAP-L-23** (HIGH / BLOCKER — firm branding, vertical profile UI, portal digest cadence settings, org doc upload all broken by the same loader-module error).

## Checkpoint 1.2 — Refresh → brand colour applied to sidebar, logo renders
- Result: **BLOCKED** (cascade of 1.1)
- Evidence: Nothing saved in 1.1, so nothing to verify in 1.2. Sidebar currently renders default slate-950 accent; no logo in header.
- Gap: blocked on GAP-L-23.

## Checkpoint 1.3 — Settings > Rate Cards, verify LSSA tariff pre-seeded
- Result: **NOT EXECUTED** (halted on BLOCKER)
- Scenario path "Settings > Rate Cards" does not exist. Settings sidebar has **Finance > Rates & Currency** at `/settings/rates`, but quick inspection shows this is the **per-member billing-rate** page (rows of firm users with "Add Rate" actions) — NOT the LSSA tariff schedule. The tariff schedule IS seeded in DB (`LegalTariffSeeder` "LSSA 2024/2025 High Court Party-and-Party" with 19 items for `tenant_5039f2d497cf` per backend log 2026-04-21T18:08:48Z). No firm-side UI route surfaces it yet — no "Tariffs" link anywhere in the live sidebar (full nav-link dump captured, zero matches for "tariff"). Carry-forward OBS-L-05 (tariff naming) cannot be verified in UI this cycle.
- Gap: not re-logged as a separate GAP because Day 1 halted on 1.1; will surface naturally once 1.1 unblocks and 1.3 is re-tried. Reference in follow-up.

## Checkpoint 1.4 — Verify specific tariff entry (High Court — attending at court, per hour) with 2026 rate
- Result: **NOT EXECUTED**

## Checkpoint 1.5 — Create Trust Account (SECTION_86, Mathebula Trust — Main)
- Result: **NOT EXECUTED, but dialog discoverability probed** — logged separate gap.
- Scenario path "Settings > Trust Accounts" does not exist. The flow is at top-level **`/trust-accounting`** (under Finance → not yet collapsed open in sidebar at turn start, reachable via direct URL or via `Finance` button). Page renders "No Trust Accounts / Add Account" empty state; "Add Account" dialog opens cleanly (not affected by the GAP-L-23 bundle error because it lives under a different page route).
- Dialog fields captured: Account Name, Bank Name, Branch Code, Account Number, **Account Type = combobox [General | Investment]**, Opened Date (prefilled 2026-04-21), Set as primary trust account (checked by default), Require dual approval (toggle), Payment Approval Threshold (ZAR), Notes.
- **Account Type dropdown does NOT include `SECTION_86`** — only `General` and `Investment`. Scenario 1.5 asks for Type: **SECTION_86 (Legal Practice Act)**. Even with GAP-L-23 fixed, 1.5 cannot be completed as written.
- Gap: **GAP-L-25** (LOW — Trust Account type dropdown missing SECTION_86 / LSSA / Section 86 option. The legal-za tenant should offer the SA Legal Practice Act §86 trust-account classification; current options are generic accounting verticals.)

## Checkpoint 1.6 — Trust account saves, balance = R 0.00, appears in list
- Result: **NOT EXECUTED**

## Checkpoint 1.7 — Screenshot day-01-trust-account-created.png (optional)
- Result: **NOT EXECUTED**

## Day 1 summary checks
- Firm branding (logo + colour) persists across logout/login: **FAIL — branding never saves** (GAP-L-23)
- LSSA tariff table pre-populated, non-empty: **DEFERRED — no UI route found for firm-side tariff viewer; DB confirms 19 LSSA items seeded**
- Trust account created under Section 86 basis: **NOT POSSIBLE — SECTION_86 classification absent from dropdown** (GAP-L-25)

## Carry-Forward watch-list verifications this turn

| Prior gap | Re-observed? | Notes |
|---|---|---|
| GAP-L-22 (post-registration session handoff) | Workaround verified | Explicit KC logout + fresh OIDC login landed Thandi on `/org/mathebula-partners/dashboard` with her own identity in sidebar — no padmin / Carol leak this turn. Still OPEN (needs fix); workaround still reliable. |
| OBS-L-05 (tariff naming) | Not verifiable | No firm-side tariff UI route to inspect. DB-only seed confirmed (legal-za pack v5 + 19 LSSA items present on tenant schema). |

## New gaps

| GAP-ID | Severity | Summary |
|---|---|---|
| GAP-L-23 | **HIGH / BLOCKER** | `/settings/general` server-actions loader module crashes with `ReferenceError: PortalDigestCadence is not defined` at module evaluation (`export {updatePortalDigestCadence} from 'ACTIONS_MODULE4'` line 15 of the loader). Every POST to the page — Save Settings, Upload Logo confirm, Vertical Profile apply, Portal Digest Cadence update, Portal Retainer Member Display update, org doc upload initiate/confirm — returns 500. Root cause is likely `portal-actions.ts` line 10 `export type { PortalDigestCadence, PortalRetainerMemberDisplay };` interacting badly with the Next.js 16 turbopack server-actions loader: the type-only re-export is not being elided in the loader bundle, leaving a dangling value reference. This single bug blocks **all** of Day 1 Phase A (logo + brand colour + document footer), blocks Day 1 Phase C if tariff settings live on another page that shares the actions loader, blocks all Portal-settings admin config, and will block Day 8/Day 11 portal-facing UX that depends on `portalDigestCadence` / `portalRetainerMemberDisplay` fields being configurable firm-side. Owner: frontend (fix is probably either (a) replace the `export type { ... }` re-export in `portal-actions.ts` with a pure import-only consumption, or (b) split the portal-actions.ts file so the loader sees inline `export async function ...` without re-exports). |
| GAP-L-24 | MED | `/settings/general` "Vertical Profile" card renders "Failed to load profiles." with Apply Profile button disabled. The `fetchProfiles` server action also lives on the same broken actions loader (GAP-L-23), so this is almost certainly a downstream symptom. If it persists after GAP-L-23 is fixed it becomes its own gap. Flagged separately so the triage can verify after the loader fix. |
| GAP-L-25 | LOW | Trust Account create dialog `/trust-accounting` Account Type dropdown offers only `General` and `Investment`. Scenario Day 1.5 expects `SECTION_86` (Legal Practice Act §86 trust-account type) for the legal-za vertical. Currently Mathebula & Partners cannot tag their trust account under SA-specific statutory classification — they must use the generic `General` option. This is pre-existing scenario-vs-feature gap; not a cascade of GAP-L-23. Suggest legal-za vertical profile should extend the enum set at pack-install time. |

## Halt reason

Per QA protocol: "On BLOCKER: stop, log it, return." Checkpoint 1.1 is BLOCKED on GAP-L-23. Checkpoint 1.2 depends on 1.1 persisting, so it is cascade-BLOCKED. The fix for GAP-L-23 is a frontend code change that must ship via the dev-fix loop before Day 1 Phase A can be re-tried.

Checkpoints 1.3 / 1.5 were lightly probed for discoverability only (no state mutation) so the product / dev triage has the full picture — specifically that (a) LSSA tariff has no firm-side UI route, and (b) Trust Account type dropdown lacks SECTION_86 even when the form is reachable. Recommend dev triage addresses GAP-L-23 first; GAP-L-24 will likely resolve automatically; GAP-L-25 can be triaged in parallel as it is orthogonal to GAP-L-23 and is a pre-existing feature gap rather than a regression.

## QA Position on exit

`Day 1 — 1.1 (blocked pending GAP-L-23)`. Next QA turn: after GAP-L-23 fix PR merges, re-run KC login + Checkpoint 1.1 from scratch; if PASS, continue 1.2 → 1.7. GAP-L-25 will gate 1.5 regardless.
