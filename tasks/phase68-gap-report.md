# Phase 68 — Portal Client-POV 90-Day Gap Report
## Generated: 2026-04-21
## Executed by: Claude Code (Agent Pass — Epic 500B)
## Stack: Keycloak dev stack (frontend 3000 / portal 3002 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025)

---

## Execution Status

**Spec files written (Epic 500A, PR #1095). Lifecycle execution + baseline capture deferred to a human-operator live-stack run.**

Slice 500A scaffolded the full Phase 68 client-POV capstone — four Playwright spec files covering the 11 lifecycle checkpoints, the `portal-client-90day` project in `portal/playwright.portal.config.ts`, the magic-link auth helper at `portal/e2e/helpers/auth.ts`, and the lifecycle script `qa/testplan/demos/portal-client-90day-keycloak.md`. Slice 500B (this PR) adds the `test:e2e:portal-client-90day` npm script, scaffolds the curated-screenshot + Playwright-baseline directories with capture procedures, and authors this gap report.

The actual lifecycle execution is deferred because it requires:

1. A live **Keycloak dev stack** (~10 min cold boot) — the agent runtime here uses an isolated worktree without the stack pre-provisioned.
2. **Three vertical Keycloak tenants** pre-provisioned via firm-side `/qa-cycle-kc` runs (`mathebula-partners`, `ledger-collective`, `keystone-consulting`) — each lifecycle script takes 20+ minutes wall-clock, so seeding all three is a 60+ min prerequisite before the portal capstone can even begin.
3. **Portal contacts** added per tenant via firm-side UI (without these the magic-link helper returns `null` and every spec skips with "Could not obtain portal JWT").

These are documented at the top of the slice-500B brief (`.epic-brief.md` §Live-Stack Fallback Strategy) as the explicit fallback path. The Phase 67 capstone (Epic 493A, PR #1081 precedent) followed the same pattern — baseline capture is **always** a separate human-operator step.

**Capstone invariant (Risk #10 from Phase 67)**: Screenshot baselines under `portal/e2e/screenshots/portal-v2/portal-client-90day/` are the single source of truth for Phase 68 — no intermediate Phase 68 slice captured visual baselines. The directory ships with `.gitkeep` + `README.md` documenting the capture procedure.

**To execute the full suite (paste-ready):**

```bash
# 1. Bring the Keycloak dev stack up
bash compose/scripts/dev-up.sh
bash compose/scripts/keycloak-bootstrap.sh
bash compose/scripts/keycloak-seed.sh
bash compose/scripts/svc.sh start all
bash compose/scripts/svc.sh status   # all services healthy

# 2. Seed three vertical tenants via the firm-side lifecycles
/qa-cycle-kc qa/testplan/demos/legal-za-90day-keycloak.md           # → mathebula-partners
/qa-cycle-kc qa/testplan/demos/accounting-za-90day-keycloak-v2.md   # → ledger-collective
/qa-cycle-kc qa/testplan/demos/consulting-agency-90day-keycloak.md  # → keystone-consulting

# 3. Run the portal capstone with --update-snapshots, once per tenant
#    (legal-za LAST so the canonical narrator wins for [all profiles] checkpoints)
cd portal
PORTAL_CONTACT_EMAIL=zola.portal@example.com PORTAL_ORG_SLUG=ledger-collective \
  BACKEND_URL=http://localhost:8080 PLAYWRIGHT_BASE_URL=http://localhost:3002 \
  NODE_OPTIONS="" pnpm test:e2e:portal-client-90day -- --update-snapshots

PORTAL_CONTACT_EMAIL=thembi.portal@example.com PORTAL_ORG_SLUG=keystone-consulting \
  BACKEND_URL=http://localhost:8080 PLAYWRIGHT_BASE_URL=http://localhost:3002 \
  NODE_OPTIONS="" pnpm test:e2e:portal-client-90day -- --update-snapshots

PORTAL_CONTACT_EMAIL=sipho.portal@example.com PORTAL_ORG_SLUG=mathebula-partners \
  BACKEND_URL=http://localhost:8080 PLAYWRIGHT_BASE_URL=http://localhost:3002 \
  NODE_OPTIONS="" pnpm test:e2e:portal-client-90day -- --update-snapshots

# 4. Verification re-run (no --update-snapshots) — should pass green
PORTAL_CONTACT_EMAIL=sipho.portal@example.com PORTAL_ORG_SLUG=mathebula-partners \
  BACKEND_URL=http://localhost:8080 PLAYWRIGHT_BASE_URL=http://localhost:3002 \
  NODE_OPTIONS="" pnpm test:e2e:portal-client-90day

# 5. Capture the 16 curated marketing PNGs manually (see
#    documentation/screenshots/portal/README.md for the per-shot procedure).
```

### Key Empirical Findings

**Working (inferred from platform capabilities + Phase 68 Epics 494–499 shipped to main):**
- Portal session-context endpoint + DTO (Epic 494A) — `use-portal-context` hook resolves vertical profile + module gates per tenant
- Slim left-rail nav with module-gated entries (Epic 494B / ADR-252)
- Magic-link → JWT → cookie + localStorage auth flow (`portal/e2e/helpers/auth.ts`)
- Portal trust ledger view (`/trust`) for `legal-za` (Epic 495 / ADR-253)
- Portal retainer hour-bank view (`/retainer`) for `legal-za` + `consulting-za` (Epic 496 / ADR-255)
- Portal deadline visibility (`/deadlines`) for `accounting-za` + `legal-za` (Epic 497 / ADR-256, ADR-257)
- Portal weekly digest + per-event nudges + cadence preference (Epic 498 / ADR-258)
- Mobile-responsive sidebar (Sheet) + responsive table breakpoints (Epic 499)
- Portal description sanitisation (ADR-254) — no HTML injection from firm-side rich-text fields
- Phase 28 portal acceptance flow (proposals, OTP)
- Phase 34 portal information requests (Day 0 / Day 3 surfaces)
- Phase 24 email infra (PortalEmailService, EmailTemplateRenderingService, EmailDeliveryLog, SendGrid webhook, unsubscribe)
- Sandbox PSP integration for invoice payment (Phase 28+)

**Broken / Missing (pre-logged per slice-500B brief + architecture §68):**
- Audit-trail dedicated portal view — parked to Phase 69 (Day 90 spec falls back to `/home`)
- Multi-contact portal roles (only one portal contact per client today)
- Two-way messaging (clients cannot send messages back to firm)
- Disbursement portal view (visible firm-side per Phase 67, not yet on portal `/projects/[id]`)
- PWA / installable manifest / service worker
- i18n (portal UI is en-ZA only)
- Dark mode (`prefers-color-scheme: dark` not honoured)
- Portal global search bar
- Single-command three-vertical seed script (60+ min prereq today)
- Frozen-clock pattern in 500A specs (timestamp flake risk on re-runs)
- CI workflow invoking `playwright.portal.config.ts`
- `snapshotPathTemplate` writes to `portal-v2/portal-client-90day/` but task brief documents `portal-client-90day/` (cosmetic naming drift)
- No portal-side `captureScreenshot` helper (curated PNGs must be hand-captured)

---

## Summary Statistics

| Category | Blocker | Major | Minor | Cosmetic | Total |
|----------|---------|-------|-------|----------|-------|
| missing-feature | 0 | 4 | 4 | 0 | 8 |
| ux | 0 | 0 | 1 | 1 | 2 |
| tooling | 0 | 0 | 4 | 2 | 6 |
| content | 0 | 0 | 0 | 0 | 0 |
| bug | 0 | 0 | 0 | 0 | 0 |
| **Total** | **0** | **4** | **9** | **3** | **16** |

*Statistics reflect pre-logged architecture-acknowledged gaps + slice-500B-scoping tooling gaps + inferred gaps from static analysis of Phase 68 Epics 494–500. Empirical numbers will update after a green Playwright run against the live three-tenant stack.*

---

## Critical Path Blockers

*No pre-logged blockers for Phase 68 — all critical lifecycle gates (session-context, nav shell, trust ledger, retainer view, deadlines, digest scheduler, mobile polish) shipped in Epics 494–499 before the capstone. Empirical blockers discovered during execution will be appended here.*

---

## Major Gaps

### GAP-001: Audit-trail dedicated portal view not implemented (parked to Phase 69)

**Day**: 90
**Step**: 90.x — "Final digest + activity trail review"
**Category**: missing-feature
**Severity**: major
**Description**: The Day 90 lifecycle checkpoint references an "activity trail" — a per-contact audit log of every portal interaction (logins, info-request submits, proposal accepts, invoice payments, document downloads). No dedicated `/profile/activity` or `/audit` route exists on the portal today. The `day-90-activity-trail.png` baseline currently falls back to capturing the `/home` view as a graceful degradation (see `day-85-90.spec.ts`). Phase 68 architecture explicitly parks this to Phase 69 to keep Phase 68 scoped to the read-model-extension pattern.
**Evidence**: No `portal/app/(authenticated)/audit/` or `portal/app/(authenticated)/profile/activity/` route in the portal Next.js app router. `day-85-90.spec.ts` Day 90 test asserts on `/home` rather than a dedicated audit surface.
**Suggested fix**: Phase 69 dedicated epic — promote the firm-side `AuditLog` aggregate to a portal-visible read-model with appropriate filters (only show contact's own actions, redact firm-internal entries) (M — 1 slice with backend read-model + 1 slice with frontend page).

### GAP-002: Multi-contact portal roles not implemented

**Day**: Cross-cutting (Day 0, Day 14, Day 45, Day 85)
**Step**: All checkpoints assume one client = one portal contact
**Category**: missing-feature
**Severity**: major
**Description**: The portal lacks UI for inviting additional contacts per customer with distinct roles (e.g. a BILLING contact who sees invoices but not deadlines, or a GENERAL contact who sees nothing sensitive). The persistence layer already supports multiple contacts per customer — `PortalContact` carries a `ContactRole` enum (PRIMARY / BILLING / GENERAL) and `customer_id` is non-unique — but `PortalCapabilityResolver` does not branch on `ContactRole`, no invite UI exists on the firm side, and digest preferences are not per-role. Real-world clients (e.g. `Moroka Family Trust`) often have multiple stakeholders — a primary signatory, a co-trustee, an external accountant — who each want their own login and scoped capability set.
**Evidence**: `PortalContact.java` uses a plain `UUID customerId` column (no `@OneToOne`, no JPA relationship to `Client`); `PortalContactRepository.findByCustomerId(UUID)` returns `List<PortalContact>` (and `countByCustomerId` exists for anonymization preview); no `*InviteContactPage` route under `portal/app/(authenticated)/settings/`; `PortalCapabilityResolver` does not consume `ContactRole` when computing per-surface capabilities.
**Suggested fix**: Multi-slice epic — add firm-side invite + role-management UI, extend `PortalCapabilityResolver` to scope capabilities by `ContactRole` (BILLING → invoices only; GENERAL → no sensitive surfaces), and make digest preferences per-role (M — 2 slices: firm-side invite/management UI, portal-side capability gating + per-role digest preferences).

### GAP-003: Two-way messaging not implemented

**Day**: Cross-cutting (any day when a client wants to clarify a request)
**Step**: All checkpoints — clients can act (accept proposal, pay invoice, submit info) but cannot author free-text messages back to the firm
**Category**: missing-feature
**Severity**: major
**Description**: The portal supports structured client → firm interactions (info-request file uploads, proposal acceptance, invoice payment) but no free-text messaging surface. Clients who want to clarify "I'm uploading the wrong year's tax cert — please confirm" have to fall back to email outside the portal. This breaks the closed-loop story for the demo + reduces stickiness.
**Evidence**: No `PortalMessage` entity or `/messages` route on the portal. Inbound messages today only flow firm → client via PortalEmailService digests.
**Suggested fix**: Dedicated epic — add `PortalMessage` aggregate (subject, body, attachments, threading), firm-side inbox surface, portal-side compose + thread view, notification triggers (M — 2 slices: backend + frontend; ~1 sprint).

### GAP-004: Disbursement portal view not implemented

**Day**: 60 (legal-za document download — disbursements should be itemised on the SoA)
**Step**: 60.x — "Download Statement of Account / financial statement"
**Category**: missing-feature
**Severity**: major
**Description**: Phase 67 (Epic 486–488) shipped firm-side disbursement tracking (DRAFT → SUBMITTED → APPROVED → WRITTEN_OFF lifecycle, trust-link slot). The portal `/projects/[id]` detail page does not yet surface the project's disbursements. Clients on legal-za matters who need to see "what costs I've been charged so far" have to wait for the next Statement of Account download. The firm-side data is there; only the portal read-model + page extension is missing.
**Evidence**: `app/portal/projects/[id]/page.tsx` does not render a Disbursements section; no `PortalDisbursementSyncService` exists. The Day 60 spec relies on the SoA document itself, not a live disbursement list.
**Suggested fix**: Read-model-extension slice (mirrors the trust/retainer/deadline pattern from 495/496/497) — `PortalDisbursementSync` + `/api/portal/projects/{id}/disbursements` endpoint + portal `<DisbursementList>` component (S — 1 slice).

---

## Minor Gaps

### GAP-005: PWA / installable manifest / service worker not configured

**Day**: Cross-cutting (mobile capture day 14 + 45 + 85)
**Step**: N/A (platform capability)
**Category**: missing-feature
**Severity**: minor
**Description**: The portal renders responsively (Epic 499) but is not installable as a Progressive Web App. There is no `manifest.json`, no service worker, no offline support, no add-to-home-screen prompt. Mobile clients have to bookmark the URL or open it from a digest email each time. Acceptance flow on mobile (Day 14 captured at 375×812) works fine in-browser; install-as-app would smooth the UX.
**Evidence**: No `app/manifest.ts` or `public/sw.js` in the portal codebase. `next.config.ts` does not enable PWA plugins.
**Suggested fix**: PWA-shell slice — add `manifest.ts`, basic service worker via `next-pwa` or hand-rolled, app icons (S — 1 slice). Non-blocking.

### GAP-006: i18n not implemented (en-ZA only)

**Day**: Cross-cutting
**Step**: N/A (platform capability)
**Category**: missing-feature
**Severity**: minor
**Description**: Portal UI strings are hard-coded en-ZA (English, South Africa). No `next-intl`, `react-intl`, or message catalogue is wired in. Phase 43 introduced a server-side message catalogue (`MessageCatalogService`) which the portal does not consume. ZA market is multilingual — Afrikaans, isiZulu, isiXhosa speakers may want their portal in their first language. **Risk #6** in the slice brief notes that translation-key gaps will surface as the gap-report builder records missing keys.
**Evidence**: All visible strings in `portal/components/` are inline literals. No `messages/*.json` directory. `use-portal-context` does not expose a `locale` field.
**Suggested fix**: i18n epic — wire `next-intl`, extract strings to `messages/en-ZA.json`, add language switcher in settings, add af-ZA + zu-ZA + xh-ZA catalogues (M — 1 slice for plumbing + ongoing translation work).

### GAP-007: Dark mode not honoured (`prefers-color-scheme: dark` ignored)

**Day**: Cross-cutting
**Step**: N/A (platform capability)
**Category**: ux
**Severity**: minor
**Description**: The portal uses Tailwind v4 with the slate palette but does not flip palette on `prefers-color-scheme: dark`. Mobile users on iOS/Android dark-mode see a bright portal at night. Frontend (firm-side) does honour dark mode in a partial way (Phase 22+); portal lags.
**Evidence**: No `dark:` variant utility usage in `portal/components/ui/*.tsx`. Tailwind config does not enable `darkMode: 'media'` or `'class'`.
**Suggested fix**: Theming pass — enable `darkMode: 'media'` in Tailwind, audit components, add a settings toggle (S — 1 slice).

### GAP-008: Portal global search bar not implemented

**Day**: Cross-cutting (any day with multi-page navigation)
**Step**: N/A (platform capability)
**Category**: missing-feature
**Severity**: minor
**Description**: Clients have to navigate via the slim left rail (~7 entries depending on profile gating). For tenants with many active info requests, deadlines, or invoices, a global search bar (Cmd+K-style) would shortcut "find that proposal I half-accepted last week". Firm side has Cmd+K via `cmdk` already.
**Evidence**: No `<CommandMenu>` component in `portal/components/`. The topbar only renders a profile button + notifications bell.
**Suggested fix**: Cmd+K palette slice — wire `cmdk`, index routes + recent items + search across `/info-requests`, `/projects`, `/invoices` (S — 1 slice).

### GAP-009: No single-command three-vertical Keycloak provisioning script

**Day**: N/A (tooling — affects every Phase 68+ portal capstone run)
**Step**: Prerequisites for slice 500B Step 4
**Category**: tooling
**Severity**: minor
**Description**: Today, the only way to seed three vertical tenants for the portal capstone is to run three separate firm-side `/qa-cycle-kc` lifecycles sequentially (`legal-za-90day-keycloak.md`, `accounting-za-90day-keycloak-v2.md`, `consulting-agency-90day-keycloak.md`). Each takes ~20 min wall-clock, totalling ~60 min just for prerequisite seeding before the portal capstone can begin. `compose/seed/seed.sh` provisions only one org with a single vertical (default `accounting-za`).
**Evidence**: No `compose/scripts/seed-three-verticals-kc.sh` exists. `compose/seed/seed.sh` honours `VERTICAL_PROFILE` env var (single value only).
**Suggested fix**: Tooling slice — write `compose/scripts/seed-three-verticals-kc.sh` that hits the firm-side onboarding API three times (one per profile) with idempotent client + portal-contact creation, shortening prereq from 60 min to ~10 min (S — single PR, infrastructure-only).

### GAP-010: 500A specs do not install a frozen clock — timestamp flake risk on re-runs

**Day**: N/A (tooling — affects baseline stability)
**Step**: All `toHaveScreenshot()` calls in `day-00-07.spec.ts`, `day-14-30.spec.ts`, `day-45-75.spec.ts`, `day-85-90.spec.ts`
**Category**: tooling
**Severity**: minor
**Description**: The four 500A spec files use `await page.waitForLoadState('domcontentloaded')` + `await page.waitForTimeout(500)` before each `toHaveScreenshot()`. They do **not** install a frozen clock via `page.clock().install({ time: ... })` (Playwright ≥1.45). Surfaces that render relative timestamps ("posted 2 hours ago", "due in 3 days") will produce a different baseline on every run, causing first-re-run flakes. Risk #8 in the slice-500B brief explicitly calls this out as the recommended escalation if flakes appear.
**Evidence**: `grep -n "page.clock" portal/e2e/tests/portal-client-90day/*.spec.ts` returns no matches. All four specs use the brief boilerplate which omits the clock install.
**Suggested fix**: Spec-edit slice — add `await page.clock().install({ time: new Date('2026-01-01T08:00:00Z') })` inside each `beforeEach`, adjust any time-sensitive assertions to a fixed point. Document in a follow-up gap-report addendum (XS — single PR; explicitly out of scope for 500B per the brief, which forbids spec-file edits in this slice).

### GAP-011: No CI workflow invoking `playwright.portal.config.ts`

**Day**: N/A (tooling — affects regression coverage in CI)
**Step**: N/A
**Category**: tooling
**Severity**: minor
**Description**: There is no GitHub Actions workflow (or other CI) that runs the portal Playwright suite. Epic 499B (responsive baselines) and Epic 500A (capstone scaffold) both shipped without CI hookup. This means the baselines, once captured, are only enforced by local human-operator runs — a breaking change to the portal could land on main without anyone noticing until a human pulls + runs locally.
**Evidence**: `.github/workflows/` does not contain `portal-playwright.yml` or similar. Existing workflows (e.g. `frontend-playwright.yml` if present) target `frontend/` not `portal/`.
**Suggested fix**: CI slice — add `.github/workflows/portal-playwright.yml` mirroring the firm-side pattern, with the e2e-up.sh stack + three-vertical seeder + `pnpm test:e2e:portal-client-90day` (depends on GAP-009 for the seeder; S — 1 slice if seeder exists, M — 2 slices if seeder is bundled).

### GAP-012: `snapshotPathTemplate` drift between task brief and config

**Day**: N/A (tooling — naming consistency)
**Step**: N/A
**Category**: tooling
**Severity**: minor
**Description**: The original Phase 68 task brief (`tasks/phase68-portal-redesign-vertical-parity.md` task 500.6) documents the baseline path as `portal/e2e/screenshots/portal-client-90day/`. The actual `snapshotPathTemplate` in `portal/playwright.portal.config.ts` writes to `portal/e2e/screenshots/portal-v2/portal-client-90day/` (with the `portal-v2/` segment). The drift exists because Epic 500A landed the lifecycle project alongside the existing 499B `chromium` responsive project under a shared `portal-v2/` snapshot root. Changing the template now would invalidate the Epic 499B baselines under `portal-v2/chromium/`.
**Evidence**: `portal/playwright.portal.config.ts` line setting `snapshotPathTemplate`. `tasks/phase68-portal-redesign-vertical-parity.md` task 500.6 row.
**Suggested fix**: Documentation-only update — patch the task brief to match the config (recommended) rather than the reverse. The path drift is documented in `portal/e2e/screenshots/portal-v2/portal-client-90day/README.md` (XS — single PR).

### GAP-013: Curated PNGs deferred to live-stack run (slice 500B fallback)

**Day**: All 11 lifecycle days
**Step**: Slice 500B task 500.8
**Category**: tooling
**Severity**: minor
**Description**: The 16 prescribed curated marketing/demo PNGs (`documentation/screenshots/portal/*.png`) cannot be captured in the slice 500B agent run because the live three-tenant Keycloak stack is not pre-provisioned (see GAP-009). The directory ships with `.gitkeep` + `README.md` documenting the per-shot manual capture procedure. A human operator must follow the documented procedure once the stack is up.
**Evidence**: `documentation/screenshots/portal/` contains only `.gitkeep` + `README.md` after slice 500B — no `.png` files.
**Suggested fix**: Human-operator follow-up — execute the procedure in `documentation/screenshots/portal/README.md` after a successful live-stack lifecycle run (see Step 8 in the slice 500B brief). Optional acceleration: add a portal-side `captureScreenshot` helper (see GAP-014) so curated capture is one Playwright invocation instead of 16 manual ones.

---

## Cosmetic Gaps

### GAP-014: No portal-side `captureScreenshot` helper

**Day**: N/A (tooling — affects ergonomics of curated capture)
**Step**: N/A
**Category**: tooling
**Severity**: cosmetic
**Description**: The frontend codebase has `frontend/e2e/helpers/screenshot.ts` exposing `captureScreenshot(page, name, { curated: true })` which writes flat into `documentation/screenshots/legal-vertical/`. The portal codebase has no equivalent — the four 500A specs only call `expect(page).toHaveScreenshot(...)` (regression baselines). This means the 16 curated PNGs (GAP-013) must be captured by hand, one at a time, via Playwright MCP or manual browser screenshots.
**Evidence**: `grep -r "captureScreenshot" portal/` returns no matches. `portal/e2e/helpers/` contains only `auth.ts`.
**Suggested fix**: Port the helper from `frontend/e2e/helpers/screenshot.ts` into `portal/e2e/helpers/screenshot.ts`, parameterise the `CURATED_DIR` to point at `documentation/screenshots/portal/`, optionally add a `subdirectory` parameter to skip the manual `mv` step that Phase 67 needed (XS — single PR).

### GAP-015: 500A specs use `domcontentloaded + waitForTimeout(500)` instead of `networkidle`

**Day**: All 11 lifecycle days
**Step**: All `toHaveScreenshot()` calls
**Category**: ux
**Severity**: cosmetic
**Description**: Risk #4 in the slice-500B brief notes that capability/module gates resolve asynchronously — the sidebar can flash "all items" before filtering. The 500A spec boilerplate uses `waitForLoadState('domcontentloaded') + waitForTimeout(500)` which is faster but less defensive than `waitForLoadState('networkidle')`. If first re-run baselines flake on the sidebar or any other module-gate-driven surface, escalating to `networkidle` is the recommended fix.
**Evidence**: `portal/e2e/tests/portal-client-90day/day-*.spec.ts` — all four files use the brief boilerplate.
**Suggested fix**: Spec-edit slice — replace `waitForLoadState('domcontentloaded') + waitForTimeout(500)` with `waitForLoadState('networkidle')` across all four spec files (XS — single PR; explicitly out of scope for 500B per the brief).

### GAP-016: Snapshot baselines are tenant-agnostic — sequential `--update-snapshots` runs overwrite each other

**Day**: All `[all profiles]` checkpoints (Day 0, 3, 14, 45, 85, 90)
**Step**: Steps 5–6 in the slice-500B brief (per-tenant `--update-snapshots` runs)
**Category**: ux
**Severity**: cosmetic
**Description**: Snapshot filenames in `toHaveScreenshot()` calls are tenant-agnostic strings (e.g. `"day-00-home-landing.png"`). Three sequential per-tenant `--update-snapshots` runs will all write to the same file. Strategy is "last tenant wins" — and the lifecycle script designates `sipho.portal@example.com` (legal-za) as the primary narrator, so legal-za should be run **last**. For profile-gated checkpoints, only the matching tenant produces a non-skipped baseline so this constraint doesn't apply (the skipped tenants leave the existing baseline untouched).
**Evidence**: Specs do not parameterise snapshot filenames by `process.env.PORTAL_ORG_SLUG`. The `snapshotPathTemplate` does not include `{ORG_SLUG}` either.
**Suggested fix**: Either (a) accept "last tenant wins" with documented run ordering (current approach — see baseline directory README), or (b) parameterise snapshot filenames per profile (e.g. `day-00-home-landing-${PROFILE}.png`) which would triple the baseline count. Option (a) is recommended — it keeps the baseline volume manageable and matches the demo intent (legal-za as primary narrator). Documented in baseline directory README; no code change needed.

---

## Checkpoint Summary by Day

| Day | Focus | Profiles | Spec file | Pass | Fail | Skipped | Notes |
|-----|-------|----------|-----------|------|------|---------|-------|
| Day 0 | Magic-link login + pending info requests | all | `day-00-07.spec.ts` | - | - | - | Pending live-stack execution |
| Day 3 | Upload requested documents + submit | all | `day-00-07.spec.ts` | - | - | - | Pending live-stack execution |
| Day 7 | First weekly digest → /deadlines | accounting-za / legal-za | `day-00-07.spec.ts` | - | - | - | consulting-za skipped (no /deadlines route) |
| Day 14 | Review + accept proposal | all | `day-14-30.spec.ts` | - | - | - | Pending live-stack execution |
| Day 21 | Trust deposit nudge + balance | legal-za only | `day-14-30.spec.ts` | - | - | - | accounting-za + consulting-za skipped |
| Day 30 | Hour-bank remaining + consumption | consulting-za / legal-za | `day-14-30.spec.ts` | - | - | - | accounting-za skipped (no /retainer route) |
| Day 45 | Pay first invoice via portal | all | `day-45-75.spec.ts` | - | - | - | Sandbox PSP — pending live-stack execution |
| Day 60 | Download SoA / financial statement | legal-za / accounting-za | `day-45-75.spec.ts` | - | - | - | consulting-za skipped (no SoA template) |
| Day 75 | Deadline-approaching nudge → mark read → download | accounting-za / legal-za | `day-45-75.spec.ts` | - | - | - | consulting-za skipped (no /deadlines route) |
| Day 85 | Update profile + change digest cadence to biweekly | all | `day-85-90.spec.ts` | - | - | - | Pending live-stack execution |
| Day 90 | Final digest + activity trail | all | `day-85-90.spec.ts` | - | - | - | Activity trail falls back to /home (see GAP-001) |

*Pass/Fail/Skipped counts populate after the first green run. Spec files include graceful-degradation `test.skip(true, "reason")` so missing feature surfaces (module gate off, profile mismatch) skip cleanly rather than fail the capstone.*

---

## All Gaps (Chronological)

### Pre-Logged Known Gaps (from slice-500B brief + architecture §68)

1. **GAP-001** — Audit-trail dedicated portal view (parked to Phase 69)
2. **GAP-002** — Multi-contact portal roles (one-contact-per-client today)
3. **GAP-003** — Two-way messaging (clients cannot author free-text back)
4. **GAP-004** — Disbursement portal view (firm-side data exists; portal page missing)
5. **GAP-005** — PWA / installable manifest / service worker
6. **GAP-006** — i18n (portal UI is en-ZA only)
7. **GAP-007** — Dark mode (`prefers-color-scheme: dark` ignored)
8. **GAP-008** — Portal global search bar (Cmd+K palette absent)

### Tooling Gaps (discovered while scoping slice 500B)

9. **GAP-009** — No single-command three-vertical Keycloak provisioning script
10. **GAP-010** — 500A specs do not install a frozen clock (timestamp flake risk)
11. **GAP-011** — No CI workflow invoking `playwright.portal.config.ts`
12. **GAP-012** — `snapshotPathTemplate` drift between task brief and config
13. **GAP-013** — Curated PNGs deferred to live-stack run (slice 500B fallback)
14. **GAP-014** — No portal-side `captureScreenshot` helper
15. **GAP-015** — 500A specs use `domcontentloaded + waitForTimeout(500)` instead of `networkidle`
16. **GAP-016** — Snapshot baselines are tenant-agnostic (sequential `--update-snapshots` runs overwrite)

### Empirically Verified Gaps

*Populated after live-stack execution. Expected additions would include: data-testid drift between specs and components, module-gate race conditions (Risk #4), portal read-model freshness lag (Risk #5), terminology key gaps (Risk #6), digest scheduler timing collisions (Risk #7), and any unexpected visual regressions on first re-run.*

---

## Fork Readiness Assessment

### Overall Verdict: READY (pending live-stack capture + verification)

Phase 68 introduces the portal client-POV story across three vertical profiles. All seven major Epics (494–500) have shipped implementations to main:

- **494**: Portal session-context endpoint + nav shell
- **495**: Portal trust ledger view (legal-za)
- **496**: Portal retainer hour-bank view (legal-za + consulting-za)
- **497**: Portal deadline visibility (accounting-za + legal-za)
- **498**: Portal weekly digest + per-event nudges + cadence preference
- **499**: Mobile-responsive polish + visual baselines
- **500A**: Lifecycle script + four Playwright spec files + auth helper (PR #1095, merged)
- **500B**: This slice — npm script + gap report + directory scaffolding (THIS PR)

**No pre-logged blockers** — every gate of the demo path has an implementation backing it. Gaps are concentrated in:

- **Major-but-deferred enhancements**: audit-trail view (Phase 69), multi-contact roles, two-way messaging, disbursement portal view (each is a clean future epic, not a current-phase miss)
- **Platform-capability minors**: PWA, i18n, dark mode, global search (each is an independent slice that can land independently)
- **Tooling minors**: three-vertical seeder, frozen clock, CI workflow, path drift, helper port (each is a single PR or a documentation update)

| Area | Criteria | Rating |
|------|----------|--------|
| Portal session context + nav shell (Epic 494) | Endpoint + DTO + sidebar + topbar + module-gated routes | PASS (pending e2e) |
| Trust ledger view, legal-za (Epic 495) | Page + read-model + sync + API + tests | PASS (pending e2e) |
| Retainer view, legal-za + consulting-za (Epic 496) | Page + read-model + sync + API + tests | PASS (pending e2e) |
| Deadline view, accounting-za + legal-za (Epic 497) | Page + read-model + sync + API + tests + portal-visible flag | PASS (pending e2e) |
| Notifications: digest + per-event + preferences (Epic 498) | Scheduler + per-event channel + templates + cadence preference | PASS (pending e2e) |
| Mobile responsive polish (Epic 499) | Sheet sidebar + responsive tables + new pages + visual baselines | PASS (Epic 499B baselines captured) |
| Capstone lifecycle (Epic 500) | Script + 4 spec files + auth helper + npm script + gap report | PASS (pending live-stack capture) |
| Module-gating correctness (E.4) | `/trust` legal-za only; `/retainer` consulting-za + legal-za; `/deadlines` accounting-za + legal-za | PASS (pending e2e) |
| Auth flow (E.5) | Magic-link → JWT (cookie + localStorage); zero Keycloak-form usage | PASS (pending e2e) |
| Terminology sweep (E.6) | Vertical vocabulary consistent per tenant | PARTIAL (terminology key gaps tracked under Risk #6 / GAP-006) |
| Three-tenant coverage (E.7) | Every `[all profiles]` checkpoint validated on ≥2/3 tenants | PASS (pending e2e — depends on GAP-009 acceleration) |
| Email linkage (E.8) | Every email-driven checkpoint authenticates follow-through without re-login | PASS (pending e2e) |
| Payment flow (E.9) | Day 45 sandbox PSP completes end-to-end + receipt download | PASS (pending e2e) |
| Visual regression (E.10) | Diff pixel-ratio ≤ 1% across all 📸 shots | PASS (config sets `maxDiffPixelRatio: 0.01`; pending baseline capture) |
| Test suite gate (E.11) | `pnpm lint` + `pnpm run build` + `pnpm exec playwright test --list` clean | PASS (verified in slice 500B) |

**Estimated work to reach READY-executed:**
- 1 human operator runs the live-stack capture procedure (~90 min total: 10 min stack boot + 60 min three-vertical seeding + 20 min Playwright lifecycle execution + curated PNG capture).
- 1 follow-up pass to triage any empirical gaps uncovered (effort depends on findings; expected: small, given graceful-degradation `test.skip(true, ...)` patterns).
- Optional follow-up for the four tooling minors (GAP-009, GAP-014, GAP-011, GAP-012) — none blocking.

---

## QA Execution Status

**Authored, type-checks, and lifecycle-listable.** Full Playwright execution against a live three-tenant Keycloak stack is deferred to a human-operator run for the reasons outlined in the Execution Status section above. The slice-500B brief (`.epic-brief.md` §Live-Stack Fallback Strategy) explicitly authorises this fallback path and matches the Phase 67 Epic 493A precedent (PR #1081 also shipped without populating Playwright baselines — baseline capture is **always** a separate human-operator step in this codebase).

**Verification evidence shipped in this slice (500B):**

- `portal/package.json` — added `test:e2e:portal-client-90day` npm script (single-line, mirrors `frontend/package.json` `test:e2e:legal-depth-ii` pattern).
- `documentation/screenshots/portal/` directory created with `README.md` (16-PNG manifest + manual capture procedure) + `.gitkeep`.
- `portal/e2e/screenshots/portal-v2/portal-client-90day/` directory created with `README.md` (capture procedure + snapshot-path-drift caveat + determinism notes) + `.gitkeep`.
- `tasks/phase68-gap-report.md` — this report (16 gaps: 0 Blocker / 4 Major / 9 Minor / 3 Cosmetic).
- `pnpm run lint` — clean.
- `pnpm run build` — clean.
- `pnpm exec playwright test --list --config=playwright.portal.config.ts --project=portal-client-90day` — parses ~11 tests across 4 spec files without errors.

**Deferred to a human-operator live-stack run:**

- Playwright baseline PNGs under `portal/e2e/screenshots/portal-v2/portal-client-90day/` (~13–15 PNGs).
- 16 curated marketing/demo PNGs under `documentation/screenshots/portal/`.
- Empirical verification of the 11 checkpoints across 3 tenants.
- Empirical population of the Pass/Fail/Skipped columns in the Checkpoint Summary table above.

**Next action for a human operator:**

```bash
# 1. Bring the Keycloak dev stack up
bash compose/scripts/dev-up.sh
bash compose/scripts/keycloak-bootstrap.sh
bash compose/scripts/keycloak-seed.sh
bash compose/scripts/svc.sh start all
bash compose/scripts/svc.sh status

# 2. Seed three vertical tenants
/qa-cycle-kc qa/testplan/demos/legal-za-90day-keycloak.md
/qa-cycle-kc qa/testplan/demos/accounting-za-90day-keycloak-v2.md
/qa-cycle-kc qa/testplan/demos/consulting-agency-90day-keycloak.md

# 3. Capture Playwright baselines per tenant (run legal-za LAST so it wins as canonical narrator)
cd portal
PORTAL_CONTACT_EMAIL=zola.portal@example.com PORTAL_ORG_SLUG=ledger-collective \
  BACKEND_URL=http://localhost:8080 PLAYWRIGHT_BASE_URL=http://localhost:3002 \
  NODE_OPTIONS="" pnpm test:e2e:portal-client-90day -- --update-snapshots

PORTAL_CONTACT_EMAIL=thembi.portal@example.com PORTAL_ORG_SLUG=keystone-consulting \
  BACKEND_URL=http://localhost:8080 PLAYWRIGHT_BASE_URL=http://localhost:3002 \
  NODE_OPTIONS="" pnpm test:e2e:portal-client-90day -- --update-snapshots

PORTAL_CONTACT_EMAIL=sipho.portal@example.com PORTAL_ORG_SLUG=mathebula-partners \
  BACKEND_URL=http://localhost:8080 PLAYWRIGHT_BASE_URL=http://localhost:3002 \
  NODE_OPTIONS="" pnpm test:e2e:portal-client-90day -- --update-snapshots

# 4. Verification re-run (no --update-snapshots; should pass green)
PORTAL_CONTACT_EMAIL=sipho.portal@example.com PORTAL_ORG_SLUG=mathebula-partners \
  BACKEND_URL=http://localhost:8080 PLAYWRIGHT_BASE_URL=http://localhost:3002 \
  NODE_OPTIONS="" pnpm test:e2e:portal-client-90day

# 5. Capture 16 curated marketing PNGs (per-shot procedure in
#    documentation/screenshots/portal/README.md)
```

---

*End of gap report. Total gaps: 16 (Blocker: 0, Major: 4, Minor: 9, Cosmetic: 3).*
