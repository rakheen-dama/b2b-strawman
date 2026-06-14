# Day 85 — Firm final closure paperwork + audit-log actor filters `[FIRM]`

- **Cycle**: 31 (Legal ZA Full Lifecycle, Keycloak, 2026-06-13)
- **Actor**: Thandi Mathebula (Owner) on firm app :3000 — session persisted (no KC login needed; `browser_navigate` to /dashboard carried a valid session)
- **Tooling**: **Playwright MCP exclusively**. No claude-in-chrome. No SingletonLock recurrence this session.
- **Matter**: RAF-2026-001 "Dlamini v Road Accident Fund" (`08ad56c4-ff5e-49c2-a034-cb5fa04b462c`), status **CLOSED**.
- **Result**: **4/4 step checkpoints PASS + 2/2 summary PASS; 0 new gaps; NOT blocked.**

## Step checkpoints

### 85.1 — Closure letter attached (Day 60) — PASS
- Matter detail header: **Closed** badge, ref `RAF-2026-001`, client Sipho Dlamini. "Closure history" card present: **13 Jun 2026 / Concluded / Closed by ca39e4b1-…** (Thandi), with a "View audit" affordance.
- DB `documents` for RAF project: `matter-closure-letter-dlamini-v-road-accident-fund-2026-06-13.pdf` **UPLOADED** (generated Day 60, downloaded Day 61 — 1644 B valid PDF). Also `matter_closure_log.closure_letter_document_id = 32d17e1d-f600-4f0a-8465-be692ba2f6cd`, `override_used=f` (clean path), `reopened_at=NULL`.
- SoA likewise present: `statement.generated` audit event for `generated_documents` id `d779f7b7` (downloaded Day 61, 5405 B).

### 85.2 — Final closing/thank-you correspondence — PASS (N/A by tenant workflow; conditional step)
- Scenario wording: "**If tenant workflow requires it**, generate a final closing letter…". The Mathebula tenant's closure workflow already generates the **matter closure letter** + **Statement of Account** as the closing-pack at Day 60 (verified 85.1 + Day 60/61). No additional thank-you template is required by this tenant's doc-pack workflow. No new document generated this cycle — closing paperwork complete from Day 60.

### 85.3 — Matter retention policy — PASS (with design note)
- Overview **Retention period** card (`retention-card.tsx`, state **`unconfigured`** = State B) renders: "Retention clock started on **13 Jun 2026**. Your firm's matter-retention period isn't configured yet, so the scheduled deletion date can't be computed." + "Configure retention period →" deep-link to `/settings/data-protection`.
- DB confirms the retention clock **is** stamped: `projects.retention_clock_started_at = 2026-06-13 17:33:09` (== `closed_at`, preserved-on-reopen anchor per ADR-249).
- **Why "unconfigured":** the per-matter card's end-date is computed from `OrgSettings.legalMatterRetentionYears` (`ProjectService.computeRetentionEndsOn` → `getRawLegalMatterRetentionYears()`), which is **NULL** in `org_settings` for this tenant (verified DB: `legal_matter_retention_years = NULL`, `retention_policy_enabled = f`). The code *deliberately* returns `null` rather than falling back to the 5-year default "so the UI can distinguish configured from unconfigured tenants" (ProjectService L110-114). This is **intentional design**, not a defect — the firm never set the data-protection retention-years value during Day 1 setup in this clean-slate cycle.
- **Separately**, the automated retention-sweep engine IS configured: `tenant.retention_policies` has an **active MATTER policy: 1825 days (5 years), trigger=MATTER_CLOSED, action=ARCHIVE**. This is a different subsystem (scheduled archival) from the per-matter display card. The 5-year period the scenario expects exists at the policy-engine layer; it's just not surfaced on the matter card because the org-settings display value is unset.
- Retention-end math sanity check: `retention_clock + 1825 days = 2031-06-12`, vs `today + 5 years = 2031-06-13` — within the scenario's "today + 5 years − 25 days" tolerance (1-day leap-year offset; the policy engine uses 1825-day arithmetic, the card would use `plusYears(5)` calendar arithmetic which lands exactly on 2031-06-13).
- **PASS rationale:** retention clock persists correctly across the closed matter, the period (5y) is configured at the policy engine, and the card correctly reflects the genuinely-unset org display setting with a clear remediation path. The "unconfigured" copy is correct, not a bug. No mutation of org settings performed (avoiding a scenario-amending state change).

### 85.4 — Audit Log filters by matter AND by actor (firm users AND portal contacts) — PASS
Firm-side **Audit log** at `/org/mathebula-partners/settings/audit-log` (TEAM_OVERSIGHT-gated, `AuditEventController`). Filter panel: date range, severity, **Actor ID**, Event type, Entity type, Entity ID, presets, Export.

- **Unfiltered feed** shows a healthy mix of actor types in the Actor column: **Thandi Mathebula** (statement.generated, document.generated, matter_closure.closed, trust_payment.approved), **Bob Ndlovu** (trust_payment.recorded, court_date.outcome_recorded, task.updated×3), **Portal Contact** (portal.document.downloaded×3 most recent) — firm + portal actors interleaved.
- **Filter by actor = Sipho (PORTAL_CONTACT, `793df2fa-6350-46af-b0c0-8b3ac0d7d855`)** → URL `?actorId=…` → **exactly 19 rows, ALL "Portal Contact", zero firm-user leak**. Event mix = `portal.document.downloaded` (SoA + closure letter, Day 61), `portal.request_item.submitted` + `portal.document.upload_initiated` (FICA + medical-evidence info-request uploads, Days 4/15/46), `portal.invoice.paid` (fee-note payment, Day 30). Matches DB count (19) exactly. Screenshot `day-85-firm-audit-filtered-sipho.png`. → **portal-contact actor filter works.**
- **Filter by actor = Thandi (USER, `ca39e4b1-…`)** → **32 rows** = DB count exactly. 31 display "Thandi Mathebula" (actor_type USER); 1 displays "System" — that row is `payment.session.created` carrying Thandi's actor_id but `actor_type=SYSTEM`, so it correctly resolves to the static "System" label per architecture §12.3.4 (`staticActorLabel`). Not a defect — the filter returns all rows for that actor_id; only the label differs because one event is system-typed. Screenshot `day-85-firm-audit-filtered-thandi.png`. → **firm-user actor filter works.**
- **Filter by matter (entity = project RAF `08ad56c4-…`)** → URL `?entityType=project&entityId=…` → **3 rows** (DB count for the `project` entity_type): `project.created_from_template` (Bob, Day 0), `project.updated` (Bob), `matter_closure.closed` (Thandi, Day 60). → **matter/entity filter works.**
  - **Note (scope nuance, not a defect):** the audit-log entity filter scopes to a single entity_type+id, so "filter by matter" surfaces only the direct `project`-entity events (3). The matter's **full 85-day cross-entity history** (trust_transaction, court_date, task, information_request, document, invoice events) lives across multiple entity types and is rendered as a unified timeline on the matter's **Activity tab** (the Day 88 wow-moment surface), not as a single audit-log entity filter. The audit-log filter mechanism itself is correct.
- Backend actor facet (`/api/audit-events/facets/actors`, `projectActorFacets`) includes ALL actor types (no actor_type WHERE clause) → portal contacts appear alongside firm users; PORTAL_CONTACT actors carry the static "Portal Contact" label (§12.3.4) — a design choice, the filter still operates by actorId.

## Day 85 summary checkpoints
- [x] **Matter retention row persists correctly** — PASS. `retention_clock_started_at` stamped at closure and preserved; active MATTER retention policy (5y/ARCHIVE) in `retention_policies`. Card State B is correct (org display setting unset); design-intentional, not a defect.
- [x] **Audit log filters by actor work for BOTH firm users AND portal contacts (Phase 50 + Phase 69 readiness)** — PASS. Sipho (PORTAL_CONTACT) → 19 portal-only rows; Thandi (USER) → 32 rows; matter entity filter → 3 rows. All three filter dimensions observed browser-driven.

## Console / backend
- **Audit-log page navigations: 0 JS errors** (per-navigation console logs = only HMR/Fast-Refresh + React DevTools info notice). Audit-log page renders clean.
- The `all:true` console dump's errors are session carry-over, NOT audit-log-origin: OBS-201 `/api/assistant/invocations 404` (exempt, fires on matter-detail), recharts `<path> d` SVG warning on /dashboard (known pre-existing cosmetic, below gap threshold), my own Day-75 portal isolation-probe 404s (Moroka `dc10e9ac`/`b72eaa77` → 404 = correct isolation), benign `:8443` favicon/logout + `DashboardRedirectPage` Performance.measure Next.js-16 dev-instrumentation artifact.

## OBS-6002 corroboration (cycle 31) — tooling, NOT re-filed
- The audit-log **Actor ID** textbox: a programmatic Playwright `fill()` set the value but did **not** apply the filter (table still showed all 50 rows) — the field commits via React `onCommit`/URL push that a synthetic `fill` doesn't trigger. Same Playwright/HMR pointer-and-input interaction class as OBS-6002 (firm + portal buttons + tablist tabs across Days 60/61/75). Worked around exactly as prior days: drove the filter via the URL searchParam (`?actorId=…` / `?entityType=…&entityId=…`), which is the page's own source-of-truth filter mechanism (server reads searchParams → backend filter). Backend + filter logic correct. **Not re-filed** — OBS-6002 already OPEN-CANDIDATE, deferred to quiescent-build repro at wrap-up.

## Carry-over exemptions observed (noted, not re-filed)
- OBS-201 assistant `/api/assistant/invocations` 404 in KC mode — WONT_FIX-EXEMPT.
- OBS-2101 non-tariff R0 time lines (visible in SoA reconciliation) — WONT_FIX prior cycle.
- KYC/FICA adapter unconfigured; Payments mock-only — per mandate.

## Evidence
- `day-85-firm-audit-filtered-sipho.png` — audit log filtered to Sipho (19 Portal Contact rows)
- `day-85-firm-audit-filtered-thandi.png` — audit log filtered to Thandi (32 USER rows)
- `day-85-firm-matter-retention-closed.png` — closed matter Overview: Retention card (State B), Closure history, RAF-2026-001 Closed
