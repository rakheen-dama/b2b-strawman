# Phase 68 — Portal Redesign & Vertical Parity

> Architecture doc: `architecture/phase68-portal-redesign-vertical-parity.md`
> ADRs: [ADR-252](../adr/ADR-252-portal-slim-left-rail-nav.md), [ADR-253](../adr/ADR-253-portal-surfaces-as-read-model-extensions.md), [ADR-254](../adr/ADR-254-portal-description-sanitisation.md), [ADR-255](../adr/ADR-255-portal-retainer-member-display.md), [ADR-256](../adr/ADR-256-polymorphic-portal-deadline-view.md), [ADR-257](../adr/ADR-257-custom-field-portal-visibility-opt-in.md), [ADR-258](../adr/ADR-258-portal-notification-no-double-send.md)
> Starting epic: 494 · Last completed: 493 (Phase 67)
> Migration high-water at phase start: tenant **V104** (`V104__add_disbursement_invoice_line_type.sql`); global portal read-model **V18** (`V18__subscription_billing_method.sql`). Phase 68 tenant migrations start at **V105**; portal (global) read-model migrations start at **V19**.

Phase 68 replaces the portal's three-entry top nav with a slim left rail that scales to ten entries, surfaces the trust ledger (`legal-za`), retainer hour-bank (`legal-za` + `consulting-za`), and upcoming deadlines (`accounting-za` + `legal-za`) that have been sitting firm-side since Phases 17, 51, 55, 60, and 61, and wires a weekly digest + per-event nudges so clients have a reason to come back between transactional emails.

Everything ships as **read-model extensions** — no new domain entities — and every new portal surface is module-gated so non-applicable verticals see neither the nav entry nor the route. The phase invests once in mobile-responsive polish across all portal pages (old + new) and establishes the first client-POV 90-day Keycloak QA lifecycle with screenshot baselines, so customer-interaction regression cycles can run from Phase 69 onwards.

Three small firm-side additions are in scope — `FieldDefinition.portalVisibleDeadline`, `OrgSettings.portalRetainerMemberDisplay`, and `OrgSettings.portalDigestCadence` — each with its settings UI. Nothing else firm-side moves.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 494 | Portal Session Context + Nav Shell | Both | -- | M | 494A, 494B | **Done** |
| 495 | Portal Trust Ledger View (`legal-za`) | Both | 494A | M | 495A, 495B | **Done** |
| 496 | Portal Retainer Usage View (`legal-za` + `consulting-za`) | Both | 494A | M | 496A, 496B | **Done** |
| 497 | Portal Deadline Visibility (`accounting-za` + `legal-za`) | Both | 494A | M | 497A, 497B | |
| 498 | Portal Notifications (digest + per-event + preferences) | Both | 495A or 496A or 497A (events) | L | 498A, 498B, 498C | |
| 499 | Mobile Polish & Responsive Pass | Frontend | 494B, 495B, 496B, 497B, 498B | M | 499A, 499B | |
| 500 | Client-POV 90-Day QA Capstone + Screenshots + Gap Report | E2E / Process | 494–499 | L | 500A, 500B | |

Slice count: **15 slices across 7 epics**. Backend and frontend are always split into separate slices. Migrations land first within each backend slice. Integration tests live in the same slice as the code they test.

---

## Dependency Graph

```
PHASES already complete:
  Phase 7  (portal read-model schema)
  Phase 17 (retainer agreements + period close)
  Phase 22 (portal frontend scaffolding)
  Phase 24 (email infra: PortalEmailService, EmailTemplateRenderingService,
            EmailDeliveryLog, SendGrid webhook, unsubscribe)
  Phase 28 (portal acceptance)
  Phase 32 (portal proposals)
  Phase 34 (portal information requests)
  Phase 43 (message catalogue)
  Phase 48 (firm terminology + FIELD_DATE_APPROACHING trigger)
  Phase 51 (accounting filing schedule + deadlines)
  Phase 55 (legal court calendar + prescription tracker)
  Phase 60 (legal trust accounting)
  Phase 61 (legal interest posting + §35 reports)
  Phase 67 (disbursements + closure + SoA)
                         │
                         ▼
              ┌──────────────────────┐
              │ [E494A  Portal session-context │
              │        endpoint + DTO +       │
              │        integration test]      │
              └──────────────────────┘
                         │
              ┌──────────┴──────────┐
              │                     │
        [E494B  Sidebar +           │
         topbar + nav-items +       │
         use-portal-context +       │
         layout.tsx + home page +   │
         component tests]           │
              │                     │
      ┌───────┼───────┬─────────────┼─────────────┐
      │       │       │             │             │
  [E495A][E496A][E497A         [E498A (early     │
   trust  retain deadline       scaffolding:     │
   read   read   polymorphic    template dir +    │
   model  model  read model +   OrgSettings      │
   +sync +sync  +sync +API +    portalDigest     │
   +API   +API  field flag +    Cadence +         │
   +tests +tests tests]          prefs table)]    │
      │       │       │             │             │
  [E495B][E496B][E497B         [E498B  scheduler  │
   pages  pages  page +         + per-event       │
   +cmps  +cmps  cmps +         channel +         │
   +API   +API   API cln +      templates +       │
   cln +  cln +  tests]         tests]            │
   tests] tests]                    │             │
      │       │       │             │             │
      └───┬───┴───────┴─────────────┤             │
          │                         │             │
          │             [E498C  prefs page +      │
          │              firm-side cadence UI +   │
          │              unsubscribe wiring +     │
          │              frontend tests]          │
          │                         │             │
          └────────────┬────────────┘             │
                       │                           │
              ┌────────┴────────┐                  │
              │                 │                  │
         [E499A mobile      [E499B new-page        │
          reflow existing    responsive audit +    │
          pages: login,      sm/md/lg shots of     │
          projects, inv,     trust, retainer,      │
          proposals, req,    deadlines, prefs,     │
          accept, profile]   home]                 │
                       │                           │
                       └────────────┬──────────────┘
                                    │
                         ┌──────────┴──────────┐
                         │                     │
                  [E500A  Client-POV 90-day  [E500B  Run + baseline
                   Keycloak lifecycle          capture + curated
                   script + Playwright         documentation/screenshots/
                   test infra scaffolding]     portal/ + gap report]
```

**Parallel opportunities:**
- After **494A** merges, **494B** starts and also **498A** (pure scaffolding — template dir + `portalDigestCadence` column + `portal_notification_preference` migration + empty preference service — does not need session-context wiring).
- After **494B** merges, **495A**, **496A**, **497A** run fully in parallel (independent vertical tracks).
- **495B / 496B / 497B** start once their respective `A` slice lands (three parallel frontend tracks).
- **498B** starts after at least one of 495A/496A/497A is in (events exist to subscribe to). Realistic earliest start: after 495A (trust events) since that's the richest new surface.
- **498C** waits on 498B (needs preference persistence).
- **499A** can partially overlap with 495B/496B/497B if the polish scope focuses on pre-existing pages (projects, invoices, proposals, requests, acceptance, profile); **499B** waits on 498C.
- **500A** scaffolds scripts while Epic 499 runs; **500B** blocks on everything green.

---

## Implementation Order

### Stage 0: Session-context foundation (blocks every vertical frontend)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 0a | 494 | 494A | `PortalContextController` (new `/api/portal/session/context`), `PortalSessionContextDto`, `PortalSessionContextService` resolving `tenantProfile` + `enabledModules` + `terminologyKey` + branding, integration tests. **Done** (PR #1082) |

### Stage 1: Portal shell + notifications scaffolding (parallel)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 1a | 494 | 494B | `portal-sidebar.tsx` (desktop + mobile drawer), `portal-topbar.tsx`, `nav-items.ts` registry, `use-portal-context.ts`, `layout.tsx` rewrite, `home/page.tsx`, middleware redirect `/` → `/home`, component tests + nav-filter unit test. **Done** (PR #1083) |
| 1b (parallel) | 498 | 498A | V105 tenant migration (`org_settings.portal_digest_cadence` enum + `field_definitions.portal_visible_deadline` column), V19 portal (global) migration (`portal_notification_preference` table), `OrgSettings` field + service method, `PortalNotificationPreference` entity + repo + service stub, unit tests. |

### Stage 2: Vertical backend fan-out (three parallel tracks after 494A)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 2a | 495 | 495A | V20 portal migration (`portal_trust_balance` + `portal_trust_transaction`), `TrustLedgerPortalSyncService` (3 event listeners + backfill), `PortalTrustController`, description-sanitisation helper, integration tests. **Done** (PR #1084) |
| 2b (parallel) | 496 | 496A | V21 portal migration (`portal_retainer_summary` + `portal_retainer_consumption_entry`), V106 tenant migration (`org_settings.portal_retainer_member_display` enum), `RetainerPortalSyncService` (3 event listeners + member-display resolver + backfill), `PortalRetainerController`, integration tests. **Done** (PR #1086) |
| 2c (parallel) | 497 | 497A | V22 portal migration (`portal_deadline_view` polymorphic), `DeadlinePortalSyncService` (4 source event listeners — filing, court date, prescription, custom-field-date with `portalVisibleDeadline` gate), `PortalDeadlineController`, integration tests. **Done** (PR #1088) |

### Stage 3: Vertical frontend fan-out + notification wiring

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 3a | 495 | 495B | `/trust` + `/trust/[matterId]` pages, trust API client, `balance-card`, `transaction-list`, `matter-selector` components, component tests. **Done** (PR #1085) |
| 3b (parallel) | 496 | 496B | `/retainer` + `/retainer/[id]` pages, retainer API client, `hour-bank-card`, `consumption-list` components, component tests. **Done** (PR #1087) |
| 3c (parallel) | 497 | 497B | `/deadlines` page, deadline API client, `deadline-list`, `deadline-detail-panel` components, firm-side `FieldDefinition.portalVisibleDeadline` settings toggle in existing field-definition editor, component tests. |
| 3d (after ≥1 of 2a/2b/2c) | 498 | 498B | `PortalDigestScheduler` (weekly cron), `PortalEmailNotificationChannel`, 7 Thymeleaf templates (`portal-weekly-digest`, `portal-trust-activity`, `portal-deadline-approaching`, `portal-retainer-period-closed`, + 3 audits of existing templates to confirm no double-send), event subscriptions, integration tests. |

### Stage 4: Notification preferences frontend + firm-side cadence UI

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 4a | 498 | 498C | `/settings/notifications/page.tsx`, preferences API client, unsubscribe landing wiring, firm-side `OrgSettings.portalDigestCadence` + `portalRetainerMemberDisplay` settings UI (small addition to existing portal-settings section), component tests. |

### Stage 5: Mobile polish

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 5a | 499 | 499A | Audit + fix mobile layouts on existing pages: login, projects (list + detail), invoices (list + detail + payment result), proposals (list + detail), requests, acceptance, profile. Empty/loading/error state audit. |
| 5b | 499 | 499B | Mobile audit on new pages (trust, retainer, deadlines, home, notifications), `e2e/screenshots/portal-v2/` baseline capture at sm/md/lg. |

### Stage 6: QA capstone

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 6a | 500 | 500A | `qa/testplan/demos/portal-client-90day-keycloak.md` drafted (11 checkpoints), Playwright test infra scaffolded under `frontend/e2e/tests/portal-client-90day/`, `/qa-cycle-kc` compatibility verified. |
| 6b | 500 | 500B | Full lifecycle run against a fresh legal-za / accounting-za / consulting-za tenant trio; curated screenshots to `documentation/screenshots/portal/`; `tasks/phase68-gap-report.md` authored. |

### Timeline

```
Stage 0:  [494A]                                            <- session context endpoint
Stage 1:  [494B] // [498A]                                  <- shell + notif scaffold
Stage 2:  [495A] // [496A] // [497A]                        <- vertical backends
Stage 3:  [495B] // [496B] // [497B] // [498B]              <- vertical frontends + scheduler
Stage 4:  [498C]                                            <- prefs page + firm cadence UI
Stage 5:  [499A] // [499B]                                  <- mobile polish
Stage 6:  [500A] -> [500B]                                  <- QA capstone
```

---

## Parallel Tracks

- **Shell track (494)** — 494A unblocks everything; 494B is the UI shell that consumes the new endpoint. Every other frontend slice waits on 494B's `use-portal-context` hook.
- **Three vertical tracks (495 / 496 / 497)** — fully independent after 494A. A-slice backends can land concurrently; B-slice frontends can land concurrently after their respective A.
- **Notification track (498)** — A-slice scaffolds (migrations + entity + template directory + empty service) and can run in parallel with Stage 1. B-slice depends on at least one vertical event stream (earliest realistic: 495A trust events). C-slice is pure frontend + tiny firm-side UI, depends on B.
- **Mobile polish (499)** — can start working on pre-existing pages as soon as Stage 1 lands; new-page audits wait on 495B/496B/497B/498C.
- **QA capstone (500)** — script drafting (500A) can begin once 498C is merged; execution (500B) blocks on everything including mobile polish.

A realistic day-by-day cadence: 494A day 1–2; 494B + 498A days 2–5 (parallel); 495A + 496A + 497A days 4–9 (parallel); 495B + 496B + 497B + 498B days 8–14 (parallel); 498C days 13–16; 499A days 15–19; 499B day 18–20; 500A days 19–21; 500B days 21–25.

---

## Epic 494: Portal Session Context + Nav Shell

**Goal**: Introduce a portal-only session-context endpoint that returns the tenant's vertical profile, enabled modules, terminology key, and branding in a single round-trip, then rebuild the portal layout shell around a slim left rail sidebar (desktop) / hamburger drawer (mobile) driven by a central nav-items registry filtered by that context. Adds a new Home landing page and retires the top-horizontal-nav `portal-header.tsx`.

**References**: Requirements §1.1–1.6, §1.7 tests; architecture §68.1.x; [ADR-252](../adr/ADR-252-portal-slim-left-rail-nav.md), [ADR-253](../adr/ADR-253-portal-surfaces-as-read-model-extensions.md).

**Dependencies**: None at the epic level. Inherits Phase 22 portal-frontend conventions, Phase 24 branding endpoint, Phase 48 terminology map.

**Scope**: Both (backend + frontend)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **494A** | 494.1–494.5 | `PortalContextController` at `/api/portal/session/context`, `PortalSessionContextDto` record, `PortalSessionContextService` resolving `tenantProfile` (via `VerticalProfileService`) + `enabledModules` (via `VerticalModuleRegistry`) + `terminologyKey` + branding (reusing `PortalBrandingController` internals), 2 integration tests (authed returns profile/module set; unauthed 401). Backend-only, 5 files. **Done** (PR #1082) |
| **494B** | 494.6–494.14 | `portal/lib/nav-items.ts` central registry (10 entries), `portal/hooks/use-portal-context.ts`, `portal/components/portal-sidebar.tsx` (desktop rail + mobile drawer variant), `portal/components/portal-topbar.tsx` (slim 48px top bar), `portal/app/(authenticated)/layout.tsx` rewrite, `portal/app/(authenticated)/home/page.tsx` (quick-actions summary), root `middleware.ts` redirect `/` → `/home`, nav-filter unit test + sidebar component test. Frontend-only, 9 files (budget verified: 7 new + 2 modified). **Done** (PR #1083) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 494.1 | Create `PortalSessionContextDto` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalSessionContextDto.java` | covered by 494.5 | `portal/PortalContactSummary.java` | Java record: `String tenantProfile`, `List<String> enabledModules`, `String terminologyKey`, `String brandColor`, `String orgName`, `String logoUrl`. |
| 494.2 | Create `PortalSessionContextService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalSessionContextService.java` | 494.5 | `portal/PortalBrandingController.java` (internals) | `@Service`. `resolve(UUID contactId)` → looks up `PortalContact` → resolves tenant → queries `VerticalProfileService.getProfileForTenant(tenantId)` + `VerticalModuleRegistry.listEnabledModules(tenantId)` + the existing branding resolution. Terminology key composed as `"en-ZA-{profile}"` (e.g. `"en-ZA-legal"`); empty when generic. Returns `PortalSessionContextDto`. |
| 494.3 | Create `PortalContextController` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContextController.java` | 494.5 | `portal/PortalBrandingController.java` | `@RestController @RequestMapping("/api/portal/session")`. `GET /context` authenticated via existing `CustomerAuthFilter`. Delegates to `PortalSessionContextService.resolve(currentContactId)`. Returns `PortalSessionContextDto`. No query params; tenant resolved from auth. |
| 494.4 | Decide and document relationship with `PortalBrandingController` | same files as 494.3 | n/a | ADR-252 | Keep `/api/portal/branding` byte-compatible for existing `use-branding.ts` callers. `PortalSessionContextService` internally calls the same branding resolver. No deprecation in this phase. |
| 494.5 | Integration tests for `/api/portal/session/context` | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/PortalContextControllerIntegrationTest.java` | ~3 | `portal/PortalAuthControllerIntegrationTest.java` | (1) legal-za portal contact → response includes `tenantProfile="legal-za"` and `enabledModules` containing `trust_accounting`, `retainer_agreements`, `document_acceptance`, (2) accounting-za portal contact → `tenantProfile="accounting-za"` + `enabledModules` contains `deadlines` but not `trust_accounting`, (3) no auth → 401. |
| 494.6 | Create `nav-items.ts` registry | `portal/lib/nav-items.ts` | covered by 494.13 | n/a (new pattern) | Exports `PortalNavItem` type (per requirements §1.2) and `PORTAL_NAV_ITEMS: PortalNavItem[]`. 10 entries: home, projects (labelKey `portal.nav.matters`), trust (profile `legal-za`, module `trust_accounting`), retainer (profiles `legal-za`+`consulting-za`, module `retainer_agreements`), deadlines (profiles `accounting-za`+`legal-za`, module `deadlines`), invoices, proposals, requests (module `information_requests`), acceptance (module `document_acceptance`), documents. Exports `filterNavItems(items, ctx)` pure function. |
| 494.7 | Create `use-portal-context.ts` hook | `portal/hooks/use-portal-context.ts` | covered by 494.14 | `portal/hooks/use-branding.ts` | React context + provider. Fetches `/api/portal/session/context` once on mount, caches in state, exposes `useProfile()`, `useModules()`, `useTerminologyKey()`, `usePortalContext()`. Loading state returns `null` — consumers handle. Exports a back-compat `useBranding()` that sources `brandColor`/`logoUrl`/`orgName` from the same state so existing pages continue to work. Legacy `portal/hooks/use-branding.ts` re-exports from this new module. |
| 494.8 | Create `portal-sidebar.tsx` component | `portal/components/portal-sidebar.tsx` | 494.14 | `frontend/components/sidebar/*` (firm app) | Desktop variant: fixed 240px left rail with icon+label per nav item, active-route indicator bar (brand colour), footer with Settings + Logout. Mobile variant: slides in from left triggered by hamburger in topbar — same list, same active state. Filters `PORTAL_NAV_ITEMS` via `filterNavItems(items, usePortalContext())`. Uses Lucide icons per requirements §1.2. Keyboard focus ring on rail items. |
| 494.9 | Create `portal-topbar.tsx` component | `portal/components/portal-topbar.tsx` | 494.14 | `portal/components/portal-header.tsx` (shape reference) | Slim 48px top bar. Left: hamburger button (only below `md`) + org logo/name. Right: user menu with display name + dropdown containing Profile link + Logout button. No nav entries. Height fixed 48px. |
| 494.10 | Rewrite `layout.tsx` | `portal/app/(authenticated)/layout.tsx` | covered by existing route tests | n/a | Wraps content in `PortalContextProvider` (from 494.7). Grid layout: `PortalTopbar` on top; `PortalSidebar` on left (desktop only, hidden below `md`); main content in a `max-w-4xl` container. On mobile, topbar + drawer state manages sidebar open/close. Remove `<PortalHeader>` import. Keep `<PortalFooter>`. |
| 494.11 | Delete (or comment-deprecate) `portal-header.tsx` | `portal/components/portal-header.tsx` | test file 494.13 must be updated/replaced | n/a | Replace file contents with a one-line deprecation export re-exporting `PortalTopbar` OR delete outright. The builder must also update `portal/components/__tests__/portal-header.test.tsx` — either retarget as `portal-topbar.test.tsx` or delete; recommended: delete old test, replace with the new sidebar/topbar tests in 494.14. |
| 494.12 | Create `home` page | `portal/app/(authenticated)/home/page.tsx` | covered by 500B end-to-end | `portal/app/(authenticated)/projects/page.tsx` (layout reference) | "use client". Quick-actions summary cards: pending info requests count, pending acceptances count, upcoming deadlines count (next 14 days, gated on `deadlines` module), recent invoices list (3), last trust movement (gated on `trust_accounting` module). Each card links to its section. Empty state when nothing to surface. |
| 494.13 | Middleware redirect `/` → `/home` | `portal/middleware.ts` (new or extend existing) | covered indirectly | `frontend/middleware.ts` for pattern | When `pathname === "/"`, redirect to `/home`. Existing auth middleware stays. |
| 494.14 | Component + unit tests | `portal/lib/__tests__/nav-items.test.ts`, `portal/components/__tests__/portal-sidebar.test.tsx` | ~6 | `portal/components/__tests__/portal-header.test.tsx` | `nav-items.test.ts` (~3): legal-za + trust_accounting → trust entry included; accounting-za → trust entry filtered out; no module → invoices still shown (no module gate). `portal-sidebar.test.tsx` (~3): desktop renders 10 items for legal-za with all modules; mobile drawer opens/closes on hamburger; active-route indicator appears on current route. Use `afterEach(() => cleanup())` per frontend/CLAUDE.md. |

### Key Files

**Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContextController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalSessionContextService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalSessionContextDto.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/PortalContextControllerIntegrationTest.java`
- `portal/lib/nav-items.ts`
- `portal/hooks/use-portal-context.ts`
- `portal/components/portal-sidebar.tsx`
- `portal/components/portal-topbar.tsx`
- `portal/app/(authenticated)/home/page.tsx`
- `portal/middleware.ts` (if not already present)
- `portal/lib/__tests__/nav-items.test.ts`
- `portal/components/__tests__/portal-sidebar.test.tsx`

**Modify:**
- `portal/app/(authenticated)/layout.tsx` — rewrite shell
- `portal/hooks/use-branding.ts` — back-compat re-export from `use-portal-context`
- `portal/components/portal-header.tsx` — delete or deprecate
- `portal/components/__tests__/portal-header.test.tsx` — delete / retarget

**Read for context:**
- `backend/.../portal/PortalBrandingController.java` — branding resolution internals
- `backend/.../verticals/VerticalProfileService.java` — profile lookup
- `backend/.../verticals/VerticalModuleRegistry.java` — module enablement
- `portal/hooks/use-branding.ts` — existing React-context pattern
- `portal/components/portal-header.tsx` — what's being replaced

### Architecture Decisions

- **Slim left rail, not mirror-of-firm-app** ([ADR-252](../adr/ADR-252-portal-slim-left-rail-nav.md)). No command palette, no zoned grouping, no dark mode. The `nav-items.ts` registry is the single source of truth for routes + gating.
- **Session context is one round-trip, not N** — profile + modules + terminology + branding in a single response to avoid waterfall fetches on cold load.
- **`use-branding` stays byte-compatible** via a re-export shim from `use-portal-context`, so existing pages (invoices, proposals, accept) don't churn.
- **Terminology key is derived server-side** (`"en-ZA-{profile}"`), not composed client-side, so the portal never needs to know profile→terminology mapping.

### Non-scope

- No new email / notification plumbing — 498 owns that.
- No trust / retainer / deadline content — 495 / 496 / 497 own those.
- No dark mode, command palette, or zoned sidebar ([ADR-252](../adr/ADR-252-portal-slim-left-rail-nav.md)).
- No firm-side changes.

---

## Epic 495: Portal Trust Ledger View (`legal-za`)

**Goal**: Expose the trust balance + transaction ledger that has been firm-side-only since Phase 60 to portal contacts whose tenant has `trust_accounting` enabled. Read-model extension + sync handlers on approved domain events + module-gated REST + frontend pages with matter-picker → detail flow.

**References**: Requirements §2.1–2.6; architecture §68.2; [ADR-253](../adr/ADR-253-portal-surfaces-as-read-model-extensions.md), [ADR-254](../adr/ADR-254-portal-description-sanitisation.md).

**Dependencies**: 494A (session context tells portal frontend whether to show the nav item). Phase 60 `TrustTransaction` + `ClientLedgerService` + `TrustTransactionApprovedEvent` + `InterestPostedEvent` + `ReconciliationCompletedEvent` all exist.

**Scope**: Both (backend + frontend)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **495A** | 495.1–495.7 | 9 backend files (2 migrations, 2 entities, 2 repos, 1 service, 1 controller, 1 helper, 1 test class) | V19 portal migration creates `portal_trust_balance` + `portal_trust_transaction`, `TrustLedgerPortalSyncService` with 3 `@EventListener` methods + backfill, `PortalTrustDescriptionSanitiser` helper, `PortalTrustController` at `/api/portal/trust/*` module-gated, integration tests. **Done** (PR #1084) |
| **495B** | 495.8–495.13 | 9 frontend files (2 pages, 3 components, 1 API client, 2 test files, 1 portal-nav wiring) | `/trust/page.tsx` (matter-picker + summary), `/trust/[matterId]/page.tsx` (detail + pagination), `portal/lib/api/trust.ts`, `balance-card.tsx`, `transaction-list.tsx`, `matter-selector.tsx`, component tests. **Done** (PR #1085) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 495.1 | V19 portal (global) migration: `portal_trust_balance` + `portal_trust_transaction` | `backend/src/main/resources/db/migration/global/V19__portal_trust_read_model.sql` | covered by 495.6 | `global/V11__portal_acceptance_requests.sql` | `portal_trust_balance` (`customer_id` UUID, `matter_id` UUID, `current_balance` DECIMAL(15,2), `last_transaction_at` TIMESTAMPTZ, `last_synced_at` TIMESTAMPTZ, PK `(customer_id, matter_id)`). `portal_trust_transaction` (`id` UUID PK mirror, `customer_id`, `matter_id`, `transaction_type` VARCHAR CHECK in set, `amount` DECIMAL, `running_balance` DECIMAL, `occurred_at` TIMESTAMPTZ, `description` VARCHAR(140), `reference` VARCHAR(100), `last_synced_at`). Index `(customer_id, matter_id, occurred_at DESC)`. Tenant isolation at row level — `customer_id` uniquely identifies the firm tenant within the global schema. |
| 495.2 | `PortalTrustBalanceView` + `PortalTrustTransactionView` JPA entities + repos | `backend/src/main/java/.../customerbackend/model/PortalTrustBalanceView.java`, `PortalTrustTransactionView.java`, `backend/src/main/java/.../customerbackend/repository/PortalTrustBalanceRepository.java`, `PortalTrustTransactionRepository.java` | 495.6 | `customerbackend/model/PortalInvoiceView.java`, `customerbackend/repository/PortalReadModelRepository.java` | Entities in portal read-model package. Composite-key entity for `PortalTrustBalanceView` via `@IdClass` or `@Embeddable`. Repos: `PortalTrustBalanceRepository.findByCustomerId(UUID)` returning list, `findByCustomerIdAndMatterId`. `PortalTrustTransactionRepository.findByCustomerIdAndMatterIdOrderByOccurredAtDesc(UUID, UUID, Pageable)`. |
| 495.3 | `PortalTrustDescriptionSanitiser` helper | `backend/src/main/java/.../customerbackend/service/PortalTrustDescriptionSanitiser.java` | covered by 495.6 | n/a (new — per ADR-254) | `@Component`. `sanitise(String raw, String fallbackTypeLabel, String matterRef)` returns sanitised string ≤140 chars. Strips entries whose source is tagged with leading `[internal]` → returns `""`, truncates at 140 with ellipsis, synthesises `"{typeLabel} — {matterRef}"` when sanitised result is empty. |
| 495.4 | `TrustLedgerPortalSyncService` with 3 event listeners + backfill | `backend/src/main/java/.../customerbackend/service/TrustLedgerPortalSyncService.java` | 495.6 | `customerbackend/handler/PortalEventHandler.java` (event-listener shape) | `@Service`. `@EventListener` on `TrustTransactionApprovedEvent` → upsert `PortalTrustTransactionView` + recompute + upsert `PortalTrustBalanceView` for `(customerId, matterId)` using running balance from `ClientLedgerService.getBalanceForMatter`. Same for `InterestPostedEvent` and `ReconciliationCompletedEvent`. Applies sanitiser. Exposes `backfillForTenant(UUID tenantId)` syncing current balance + last 50 transactions per matter — invoked on module activation via existing resync hook. |
| 495.5 | `PortalTrustController` | `backend/src/main/java/.../customerbackend/controller/PortalTrustController.java` | 495.7 | `customerbackend/controller/PortalInvoiceController.java` | `@RestController @RequestMapping("/api/portal/trust")`. Module-gated at controller level — returns 404 if `VerticalModuleRegistry.isEnabled("trust_accounting", tenantId)` is false (consult the PortalContact's tenant). Endpoints: `GET /summary` (balance + last transaction per matter), `GET /matters/{matterId}/transactions?page=&size=&from=&to=` (paginated), `GET /matters/{matterId}/statement-documents` (delegates to existing `PortalDocumentController`-style list filtering `GeneratedDocument` rows of `category=STATEMENT` scoped to matter). Authed via existing `CustomerAuthFilter`. |
| 495.6 | Sync-service integration tests | `backend/src/test/java/.../customerbackend/service/TrustLedgerPortalSyncServiceIntegrationTest.java` | ~5 | `customerbackend/service/PortalReadModelServiceTest.java` | (1) approved `TrustTransactionApprovedEvent` upserts portal_trust_transaction and updates portal_trust_balance, (2) `InterestPostedEvent` creates an `INTEREST_POSTED` row + updates balance, (3) sanitisation: `[internal] note` description → stored as synthesised fallback, (4) sanitisation: 200-char description truncated at 140, (5) `backfillForTenant` seeds 50 transactions per matter and correct balance. |
| 495.7 | Controller integration tests | `backend/src/test/java/.../customerbackend/controller/PortalTrustControllerIntegrationTest.java` | ~4 | `customerbackend/controller/PortalInvoiceControllerIntegrationTest.java` | (1) legal-za portal contact GET `/summary` returns their matters only, (2) accounting-za portal contact → 404 (module gate), (3) pagination `size=10` returns 10 rows + `hasNext`, (4) `from`/`to` date filter excludes out-of-range. |
| 495.8 | `portal/lib/api/trust.ts` API client | `portal/lib/api/trust.ts` | covered by 495.13 | `portal/lib/api/acceptance.ts` | Typed client. `getTrustSummary()`, `getMatterTransactions(matterId, {page, size, from, to})`, `getMatterStatementDocuments(matterId)`. Uses `portal/lib/api-client.ts`. |
| 495.9 | `balance-card.tsx` | `portal/components/trust/balance-card.tsx` | 495.13 | `portal/components/invoice-line-table.tsx` (design language) | Large balance figure in ZAR, "as of" date, matter name, link to detail page. Mobile-friendly — full width below `md`. |
| 495.10 | `matter-selector.tsx` | `portal/components/trust/matter-selector.tsx` | 495.13 | `portal/components/project-card.tsx` | List of matters with trust activity, each showing balance + last-activity date. Clickable card. Empty state "No trust activity on your matters". |
| 495.11 | `transaction-list.tsx` | `portal/components/trust/transaction-list.tsx` | 495.13 | `portal/components/invoice-line-table.tsx` | Paginated list, mobile card/row representation. Columns: date, type, description, amount, running balance. Sorts newest first. Pagination controls at bottom. |
| 495.12 | `/trust/page.tsx` + `/trust/[matterId]/page.tsx` | `portal/app/(authenticated)/trust/page.tsx`, `portal/app/(authenticated)/trust/[matterId]/page.tsx` | 495.13 | `portal/app/(authenticated)/invoices/page.tsx` | Index page: calls `getTrustSummary`, renders `<MatterSelector>`. If exactly one matter has activity, auto-redirects to its detail. Detail page: `<BalanceCard>` + `<TransactionList>` + statement-documents list linking existing `/api/portal/documents/{id}/download`. Both pages verify `trust_accounting` in `useModules()` and redirect to `/home` if disabled. |
| 495.13 | Component tests | `portal/components/trust/__tests__/balance-card.test.tsx`, `transaction-list.test.tsx`, `matter-selector.test.tsx` | ~5 | `portal/components/__tests__/invoice-line-table.test.tsx` | (1) `<BalanceCard>` renders balance + currency + as-of date; (2) `<TransactionList>` renders 10 rows + pagination advances page; (3) `<MatterSelector>` empty state visible with no matters; (4) matter selection navigates to detail route; (5) `<TransactionList>` sanitisation-fallback description renders synthesised text. Include `afterEach(() => cleanup())`. |

### Key Files

**Create:**
- `backend/src/main/resources/db/migration/global/V19__portal_trust_read_model.sql`
- `backend/src/main/java/.../customerbackend/model/PortalTrustBalanceView.java`
- `backend/src/main/java/.../customerbackend/model/PortalTrustTransactionView.java`
- `backend/src/main/java/.../customerbackend/repository/PortalTrustBalanceRepository.java`
- `backend/src/main/java/.../customerbackend/repository/PortalTrustTransactionRepository.java`
- `backend/src/main/java/.../customerbackend/service/PortalTrustDescriptionSanitiser.java`
- `backend/src/main/java/.../customerbackend/service/TrustLedgerPortalSyncService.java`
- `backend/src/main/java/.../customerbackend/controller/PortalTrustController.java`
- `backend/src/test/java/.../customerbackend/service/TrustLedgerPortalSyncServiceIntegrationTest.java`
- `backend/src/test/java/.../customerbackend/controller/PortalTrustControllerIntegrationTest.java`
- `portal/lib/api/trust.ts`
- `portal/components/trust/balance-card.tsx`
- `portal/components/trust/matter-selector.tsx`
- `portal/components/trust/transaction-list.tsx`
- `portal/app/(authenticated)/trust/page.tsx`
- `portal/app/(authenticated)/trust/[matterId]/page.tsx`
- `portal/components/trust/__tests__/balance-card.test.tsx`
- `portal/components/trust/__tests__/transaction-list.test.tsx`
- `portal/components/trust/__tests__/matter-selector.test.tsx`

**Read for context:**
- `backend/.../verticals/legal/trustaccounting/transaction/TrustTransaction.java` — firm-side entity
- `backend/.../verticals/legal/trustaccounting/event/TrustTransactionApprovedEvent.java` — event payload shape
- `backend/.../verticals/legal/trustaccounting/ledger/ClientLedgerService.java` — `getBalanceForMatter`
- `portal/lib/api/acceptance.ts` — portal API client shape

### Architecture Decisions

- **Read-model extension, not a new entity** ([ADR-253](../adr/ADR-253-portal-surfaces-as-read-model-extensions.md)). Firm-side `TrustTransaction` is authoritative; portal mirrors via sync service.
- **Description sanitisation at sync time** ([ADR-254](../adr/ADR-254-portal-description-sanitisation.md)) — strip `[internal]`, truncate to 140, synthesise from type+matter-ref when empty. Sanitiser is a reusable component used again by retainer consumption in 496.
- **Module gate at controller level** returns 404 (not 403) — matches the existing `PortalInvoiceController` pattern for opt-in modules.
- **Statement Documents are not duplicated** — the trust-matter detail page reads existing `GeneratedDocument` rows via the portal document API with a category filter; Phase 67's Statement of Account artifacts surface automatically.

### Non-scope

- No write path — portal is strictly read-only.
- No disbursement visibility (requirements §Constraints explicitly defers this to a later phase).
- No Section 86(5) trust-interest distinction — covered by firm-side Phase 61 reports.
- No email notifications on trust events — 498 owns those.

---

## Epic 496: Portal Retainer Usage View (`legal-za` + `consulting-za`)

**Goal**: Surface hour-bank consumption and renewal dates for active retainer agreements to portal contacts of retainer-backed customers. Adds `OrgSettings.portalRetainerMemberDisplay` for the privacy-toggle on "who logged this time".

**References**: Requirements §3.1–3.6; architecture §68.3; [ADR-253](../adr/ADR-253-portal-surfaces-as-read-model-extensions.md), [ADR-254](../adr/ADR-254-portal-description-sanitisation.md), [ADR-255](../adr/ADR-255-portal-retainer-member-display.md).

**Dependencies**: 494A (portal shell). Phase 17 `RetainerAgreement` + `RetainerPeriod` + `TimeEntryLogged` + `RetainerPeriodRolloverEvent` already exist (verified under `backend/.../retainer/`).

**Scope**: Both (backend + frontend)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **496A** | 496.1–496.8 | 10 backend files | V20 portal migration (`portal_retainer_summary` + `portal_retainer_consumption_entry`), V105 tenant migration (`org_settings.portal_retainer_member_display` enum), `OrgSettings` field + enum, `PortalRetainerMemberDisplayResolver` helper, `RetainerPortalSyncService` (3 listeners + backfill), `PortalRetainerController`, integration tests. **Note**: V105 is the phase's first tenant migration — any other slice needing a tenant migration (497A, 498A) lands after. Builder confirms V-number. **Done** (PR #1086) |
| **496B** | 496.9–496.14 | 8 frontend files | `/retainer/page.tsx` + `/retainer/[id]/page.tsx`, `portal/lib/api/retainer.ts`, `hour-bank-card.tsx`, `consumption-list.tsx`, component tests. **Done** (PR #1087) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 496.1 | V105 tenant migration: add `portal_retainer_member_display` to `org_settings` | `backend/src/main/resources/db/migration/tenant/V105__add_portal_retainer_member_display.sql` | covered by 496.7 | `tenant/V104__add_disbursement_invoice_line_type.sql` | `ALTER TABLE org_settings ADD COLUMN portal_retainer_member_display VARCHAR(20) DEFAULT 'FIRST_NAME_ROLE' CHECK (portal_retainer_member_display IN ('FULL_NAME','FIRST_NAME_ROLE','ROLE_ONLY','ANONYMISED'))`. Builder MUST verify `V105` is free (`ls backend/src/main/resources/db/migration/tenant/ \| tail -5`) — rename if another slice beat this one. |
| 496.2 | V20 portal migration: `portal_retainer_summary` + `portal_retainer_consumption_entry` | `backend/src/main/resources/db/migration/global/V20__portal_retainer_read_model.sql` | 496.7 | `global/V19__portal_trust_read_model.sql` (495A) | `portal_retainer_summary` (`id` UUID PK mirror, `customer_id`, `name`, `period_type` VARCHAR CHECK in {`MONTHLY`,`QUARTERLY`,`ANNUAL`}, `hours_allotted` NUMERIC(8,2), `hours_consumed`, `hours_remaining`, `period_start` DATE, `period_end` DATE, `rollover_hours` NUMERIC(8,2), `next_renewal_date` DATE, `status` VARCHAR CHECK in {`ACTIVE`,`EXPIRED`,`PAUSED`}, `last_synced_at`). `portal_retainer_consumption_entry` (`id` UUID PK mirror, `retainer_id`, `customer_id`, `occurred_at` DATE, `hours` NUMERIC(6,2), `description` VARCHAR(140), `member_display_name` VARCHAR(80), `last_synced_at`). Indexes `(customer_id, status)` on summary, `(retainer_id, occurred_at DESC)` on entries. |
| 496.3 | Add `portalRetainerMemberDisplay` field + enum to `OrgSettings` | `backend/src/main/java/.../settings/OrgSettings.java`, `backend/src/main/java/.../settings/PortalRetainerMemberDisplay.java` | 496.7 | `settings/OrgSettings.java` existing enum fields | Java enum `PortalRetainerMemberDisplay` with 4 values. `@Column(name="portal_retainer_member_display") private String portalRetainerMemberDisplay;` with getter returning `PortalRetainerMemberDisplay.valueOf(...)` defaulting to `FIRST_NAME_ROLE` when null. Update `OrgSettingsService.updatePortalRetainerMemberDisplay(...)`. |
| 496.4 | `PortalRetainerMemberDisplayResolver` helper | `backend/src/main/java/.../customerbackend/service/PortalRetainerMemberDisplayResolver.java` | 496.7 | n/a (new — per ADR-255) | `@Component`. `resolve(Member member, PortalRetainerMemberDisplay mode)`: `FULL_NAME` → "Alice Ndlovu"; `FIRST_NAME_ROLE` → "Alice (Attorney)"; `ROLE_ONLY` → "Attorney"; `ANONYMISED` → "Team member". Role lookup via existing `OrgRoleService`. |
| 496.5 | `RetainerPortalSyncService` | `backend/src/main/java/.../customerbackend/service/RetainerPortalSyncService.java` | 496.7 | `customerbackend/handler/PortalEventHandler.java` | `@Service`. `@EventListener` on `RetainerAgreementCreatedEvent` / `RetainerAgreementUpdatedEvent` / `RetainerPeriodClosedEvent` → upsert `portal_retainer_summary`. `@EventListener` on `TimeEntryLogged` → if project belongs to a retainer-backed customer (query retainer for the customer), upsert `portal_retainer_consumption_entry` + increment `hours_consumed` + decrement `hours_remaining` on summary. `@EventListener` on `RetainerPeriodRolloverEvent` → roll period boundaries + reset consumption + stamp `rollover_hours`. Applies `PortalTrustDescriptionSanitiser` (from 495A, reused) to entry description. Applies `PortalRetainerMemberDisplayResolver` using tenant's `OrgSettings.portalRetainerMemberDisplay`. Exposes `backfillForTenant(UUID)` — seeds current-period summary + current-period entries. |
| 496.6 | `PortalRetainerController` | `backend/src/main/java/.../customerbackend/controller/PortalRetainerController.java` | 496.8 | `customerbackend/controller/PortalInvoiceController.java` | `@RestController @RequestMapping("/api/portal/retainers")`. Module-gated (`retainer_agreements`). `GET /` returns list of active summaries for contact's customer. `GET /{id}/consumption?from=&to=` returns consumption entries for a retainer. Default range = current period bounds. |
| 496.7 | Sync-service integration tests | `backend/src/test/java/.../customerbackend/service/RetainerPortalSyncServiceIntegrationTest.java` | ~5 | `customerbackend/service/TrustLedgerPortalSyncServiceIntegrationTest.java` (495A) | (1) `RetainerAgreementCreatedEvent` upserts summary, (2) `TimeEntryLogged` on a retainer-backed matter creates entry + decrements hours_remaining, (3) `RetainerPeriodRolloverEvent` rolls period + zeros consumed, (4) `FIRST_NAME_ROLE` resolves to "Alice (Attorney)", (5) member_display config change for the tenant re-resolves on new events (existing entries unchanged). |
| 496.8 | Controller integration tests | `backend/src/test/java/.../customerbackend/controller/PortalRetainerControllerIntegrationTest.java` | ~4 | 495.7 | (1) `consulting-za` portal contact GET `/` returns their retainers, (2) accounting-za contact → 404 (module gate), (3) `legal-za` contact with a retainer-backed matter returns it, (4) `from`/`to` date range filters consumption entries. |
| 496.9 | `portal/lib/api/retainer.ts` | `portal/lib/api/retainer.ts` | covered by 496.14 | `portal/lib/api/acceptance.ts` | `listRetainers()`, `getConsumption(retainerId, {from, to})`. |
| 496.10 | `hour-bank-card.tsx` | `portal/components/retainer/hour-bank-card.tsx` | 496.14 | `portal/components/invoice-status-badge.tsx` + progress bar | Large remaining-hours number, progress bar (% consumed), renewal date, period label. Urgency tint when <20% remaining. |
| 496.11 | `consumption-list.tsx` | `portal/components/retainer/consumption-list.tsx` | 496.14 | `portal/components/task-list.tsx` | Grouped by date, each row: member display name, hours, description. Period selector dropdown (current, previous, custom range). |
| 496.12 | `/retainer/page.tsx` + `/retainer/[id]/page.tsx` | `portal/app/(authenticated)/retainer/page.tsx`, `portal/app/(authenticated)/retainer/[id]/page.tsx` | 496.14 | `portal/app/(authenticated)/projects/page.tsx` | Index: list of active retainers with `<HourBankCard>` per retainer. Detail: `<HourBankCard>` + `<ConsumptionList>`. Redirect to `/home` if `retainer_agreements` module disabled. |
| 496.13 | Empty-state + module-gate handling | same files as 496.12 | 496.14 | n/a | "No active retainer" empty state on index; 404 from controller surfaces as redirect. |
| 496.14 | Component tests | `portal/components/retainer/__tests__/hour-bank-card.test.tsx`, `consumption-list.test.tsx` | ~5 | 495.13 | (1) hour-bank renders remaining + progress bar + renewal date; (2) urgency tint at <20%; (3) consumption list groups by date; (4) period selector re-fetches; (5) empty period renders placeholder. `afterEach(() => cleanup())`. |

### Key Files

**Create:**
- `backend/src/main/resources/db/migration/tenant/V105__add_portal_retainer_member_display.sql`
- `backend/src/main/resources/db/migration/global/V20__portal_retainer_read_model.sql`
- `backend/src/main/java/.../settings/PortalRetainerMemberDisplay.java`
- `backend/src/main/java/.../customerbackend/service/PortalRetainerMemberDisplayResolver.java`
- `backend/src/main/java/.../customerbackend/service/RetainerPortalSyncService.java`
- `backend/src/main/java/.../customerbackend/controller/PortalRetainerController.java`
- `backend/src/main/java/.../customerbackend/model/PortalRetainerSummaryView.java`
- `backend/src/main/java/.../customerbackend/model/PortalRetainerConsumptionEntryView.java`
- `backend/src/main/java/.../customerbackend/repository/PortalRetainerSummaryRepository.java`
- `backend/src/main/java/.../customerbackend/repository/PortalRetainerConsumptionEntryRepository.java`
- `backend/src/test/java/.../customerbackend/service/RetainerPortalSyncServiceIntegrationTest.java`
- `backend/src/test/java/.../customerbackend/controller/PortalRetainerControllerIntegrationTest.java`
- `portal/lib/api/retainer.ts`
- `portal/components/retainer/hour-bank-card.tsx`
- `portal/components/retainer/consumption-list.tsx`
- `portal/app/(authenticated)/retainer/page.tsx`
- `portal/app/(authenticated)/retainer/[id]/page.tsx`
- `portal/components/retainer/__tests__/hour-bank-card.test.tsx`
- `portal/components/retainer/__tests__/consumption-list.test.tsx`

**Modify:**
- `backend/src/main/java/.../settings/OrgSettings.java` — add field + getter
- `backend/src/main/java/.../settings/OrgSettingsService.java` — add updater method

**Read for context:**
- `backend/.../retainer/RetainerAgreement.java`, `RetainerPeriod.java`, `RetainerConsumptionListener.java`
- `backend/.../customerbackend/service/TrustLedgerPortalSyncService.java` (495A) — sibling pattern

### Architecture Decisions

- **Member display is an org setting, not a per-contact toggle** ([ADR-255](../adr/ADR-255-portal-retainer-member-display.md)). Firms decide privacy posture uniformly for all their portal users. Default `FIRST_NAME_ROLE` balances transparency + privacy.
- **Sanitiser re-use** ([ADR-254](../adr/ADR-254-portal-description-sanitisation.md)) — same `PortalTrustDescriptionSanitiser` from 495A applies to consumption-entry descriptions. No duplicate helper.
- **Hours decrement at event time, not read time** — sync service keeps `hours_remaining` current, avoiding aggregation latency on the read path.

### Non-scope

- No retainer *creation* or *modification* on portal — firm-side only.
- No multi-retainer aggregation view (one card per retainer).
- No retainer consumption email notification on every time entry — 498 provides period-close notification only.

---

## Epic 497: Portal Deadline Visibility (`accounting-za` + `legal-za`)

**Goal**: Expose a unified upcoming-deadlines view to portal contacts whose tenant has `deadlines` module enabled, aggregating four firm-side sources into a single polymorphic read-model row and page. Includes a small firm-side opt-in flag (`FieldDefinition.portalVisibleDeadline`) for custom-date fields.

**References**: Requirements §4.1–4.5; architecture §68.4; [ADR-253](../adr/ADR-253-portal-surfaces-as-read-model-extensions.md), [ADR-254](../adr/ADR-254-portal-description-sanitisation.md), [ADR-256](../adr/ADR-256-polymorphic-portal-deadline-view.md), [ADR-257](../adr/ADR-257-custom-field-portal-visibility-opt-in.md).

**Dependencies**: 494A. Phase 51 filing schedule events exist. Phase 55 court-date + prescription events may not all be wired — sync handler must be generic enough to stay dormant until events fire. Phase 48 `FIELD_DATE_APPROACHING` trigger exists.

**Scope**: Both (backend + frontend + tiny firm-side UI change)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **497A** | 497.1–497.7 | 9 backend files | V106 tenant migration (`field_definitions.portal_visible_deadline` BOOLEAN), V21 portal migration (`portal_deadline_view`), `FieldDefinition` entity field, `DeadlinePortalSyncService` (4 listeners, gated by flag for custom dates), `PortalDeadlineController`, integration tests. Builder re-verifies V-numbers at build time. **Done** (PR #1088) |
| **497B** | 497.8–497.13 | 8 files (portal frontend + firm-side `FieldDefinition` settings toggle) | `/deadlines/page.tsx`, `portal/lib/api/deadlines.ts`, `deadline-list.tsx`, `deadline-detail-panel.tsx`, firm-side toggle in `FieldDefinition` settings UI (small extension to existing field-definition editor), component tests. Bundled into frontend slice per sizing rules — firm-side change is a single toggle on an existing form. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 497.1 | V106 tenant migration: `field_definitions.portal_visible_deadline` | `backend/src/main/resources/db/migration/tenant/V106__add_portal_visible_deadline_field_flag.sql` | 497.6 | `tenant/V104__add_disbursement_invoice_line_type.sql` | `ALTER TABLE field_definitions ADD COLUMN portal_visible_deadline BOOLEAN NOT NULL DEFAULT false`. Flyway verifies V-number at build. |
| 497.2 | V21 portal migration: `portal_deadline_view` (polymorphic) | `backend/src/main/resources/db/migration/global/V21__portal_deadline_view.sql` | 497.6 | `global/V20__portal_retainer_read_model.sql` (496A) | `portal_deadline_view` (`id` UUID PK mirror — compound with `source_entity` if id uniqueness across sources isn't guaranteed, else use `(source_entity, id)` composite), `customer_id`, `matter_id` UUID NULL, `deadline_type` VARCHAR CHECK in {`FILING`,`COURT_DATE`,`PRESCRIPTION`,`CUSTOM_DATE`}, `label` VARCHAR(160), `due_date` DATE, `status` VARCHAR CHECK in {`UPCOMING`,`DUE_SOON`,`OVERDUE`,`COMPLETED`,`CANCELLED`}, `description_sanitised` VARCHAR(140), `source_entity` VARCHAR CHECK in {`FILING_SCHEDULE`,`COURT_DATE`,`PRESCRIPTION_TRACKER`,`CUSTOM_FIELD_DATE`}, `last_synced_at`. PK `(source_entity, id)`. Index `(customer_id, due_date ASC)`. |
| 497.3 | Add `portalVisibleDeadline` to `FieldDefinition` entity | `backend/src/main/java/.../fielddefinition/FieldDefinition.java` | 497.6 | `fielddefinition/FieldDefinition.java` existing columns | `@Column(name="portal_visible_deadline") private boolean portalVisibleDeadline;` default false; getter. Extend `FieldDefinitionService` updater. |
| 497.4 | `DeadlinePortalSyncService` | `backend/src/main/java/.../customerbackend/service/DeadlinePortalSyncService.java` | 497.6 | 495.4 | `@Service`. `@EventListener` on (a) `FilingScheduleCreatedEvent` / `FilingStatusChangedEvent` → upsert row with `deadlineType=FILING`, `sourceEntity=FILING_SCHEDULE`; (b) `CourtDateScheduledEvent` / `CourtDateCancelledEvent` (check these event classes exist; handler is coded generically regardless) → `COURT_DATE`; (c) `PrescriptionDeadlineSetEvent` → `PRESCRIPTION`; (d) `FIELD_DATE_APPROACHING` trigger — only if `FieldDefinition.portalVisibleDeadline == true` for the field → `CUSTOM_DATE`. Status mapped by due-date proximity: ≤7 days → `DUE_SOON`, past → `OVERDUE`, else `UPCOMING`. Applies description sanitiser. Exposes `backfillForTenant(UUID)`. |
| 497.5 | `PortalDeadlineController` | `backend/src/main/java/.../customerbackend/controller/PortalDeadlineController.java` | 497.7 | 495.5 | `@RestController @RequestMapping("/api/portal/deadlines")`. Module-gated (`deadlines`). `GET /?from=&to=&status=` — list deadlines for contact's customer. Defaults: `from=today`, `to=today+60d`. Returns ordered by `due_date ASC`. `GET /{sourceEntity}/{id}` — detail. |
| 497.6 | Sync-service integration tests | `backend/src/test/java/.../customerbackend/service/DeadlinePortalSyncServiceIntegrationTest.java` | ~5 | 495.6 | (1) `FilingScheduleCreatedEvent` upserts with `FILING` type + `FILING_SCHEDULE` source, (2) re-upsert on status change updates existing row, (3) `FIELD_DATE_APPROACHING` with `portalVisibleDeadline=true` inserts; with flag=false → noop, (4) court-date event (synthesised event for test) → `COURT_DATE` row, (5) status auto-derives (mock clock, row 5 days out → `DUE_SOON`). |
| 497.7 | Controller integration tests | `backend/src/test/java/.../customerbackend/controller/PortalDeadlineControllerIntegrationTest.java` | ~4 | 495.7 | (1) default range returns next-60-day deadlines, (2) `status=OVERDUE` filter narrows to overdue only, (3) non-applicable tenant profile (consulting-za) → 404, (4) detail endpoint resolves `(sourceEntity, id)`. |
| 497.8 | `portal/lib/api/deadlines.ts` | `portal/lib/api/deadlines.ts` | covered by 497.13 | `portal/lib/api/acceptance.ts` | `listDeadlines({from, to, status})`, `getDeadline(sourceEntity, id)`. |
| 497.9 | `deadline-list.tsx` | `portal/components/deadlines/deadline-list.tsx` | 497.13 | `portal/components/task-list.tsx` | Grouped by week, urgency colour by time-to-due (>14d grey, ≤14d amber, ≤7d red, overdue red-solid, completed green). Row click opens detail panel. Filters above: status + type. |
| 497.10 | `deadline-detail-panel.tsx` | `portal/components/deadlines/deadline-detail-panel.tsx` | 497.13 | `portal/components/comment-section.tsx` | Slide-in right panel. Label + due date + status badge + sanitised description + source-entity read-only reference (e.g. "From: Filing schedule — VAT 201"). No actions. |
| 497.11 | `/deadlines/page.tsx` | `portal/app/(authenticated)/deadlines/page.tsx` | 497.13 | `portal/app/(authenticated)/projects/page.tsx` | `<DeadlineList>` + `<DeadlineDetailPanel>`. Defaults to upcoming. Redirects to `/home` if module disabled. |
| 497.12 | Firm-side `portalVisibleDeadline` toggle on Field Definition editor | `frontend/app/(app)/org/[slug]/settings/fields/[id]/page.tsx` (or the equivalent existing field-definition edit form — verify path) | covered by 497.13 | existing field-editor form | Single `<Checkbox>` "Surface this date on portal as a deadline" shown only when `fieldType === DATE`. Persists via existing `FieldDefinitionController` update endpoint (extend DTO to include the flag). |
| 497.13 | Component tests | `portal/components/deadlines/__tests__/deadline-list.test.tsx`, `deadline-detail-panel.test.tsx`, `frontend/__tests__/settings/field-definition-portal-deadline-toggle.test.tsx` | ~5 | 495.13 | (1) list groups by week, (2) urgency colour changes with due-date proximity, (3) detail panel opens on row click, (4) firm-side toggle visible only for DATE fields, (5) toggle save persists (via mocked API). `afterEach(() => cleanup())`. |

### Key Files

**Create:**
- `backend/src/main/resources/db/migration/tenant/V106__add_portal_visible_deadline_field_flag.sql`
- `backend/src/main/resources/db/migration/global/V21__portal_deadline_view.sql`
- `backend/src/main/java/.../customerbackend/service/DeadlinePortalSyncService.java`
- `backend/src/main/java/.../customerbackend/controller/PortalDeadlineController.java`
- `backend/src/main/java/.../customerbackend/model/PortalDeadlineView.java`
- `backend/src/main/java/.../customerbackend/repository/PortalDeadlineViewRepository.java`
- `backend/src/test/java/.../customerbackend/service/DeadlinePortalSyncServiceIntegrationTest.java`
- `backend/src/test/java/.../customerbackend/controller/PortalDeadlineControllerIntegrationTest.java`
- `portal/lib/api/deadlines.ts`
- `portal/components/deadlines/deadline-list.tsx`
- `portal/components/deadlines/deadline-detail-panel.tsx`
- `portal/app/(authenticated)/deadlines/page.tsx`
- `portal/components/deadlines/__tests__/deadline-list.test.tsx`
- `portal/components/deadlines/__tests__/deadline-detail-panel.test.tsx`
- `frontend/__tests__/settings/field-definition-portal-deadline-toggle.test.tsx`

**Modify:**
- `backend/src/main/java/.../fielddefinition/FieldDefinition.java` — add `portalVisibleDeadline`
- `backend/src/main/java/.../fielddefinition/FieldDefinitionService.java` — updater
- `backend/src/main/java/.../fielddefinition/dto/*` — DTO extension for the flag
- `frontend/app/(app)/org/[slug]/settings/fields/[id]/page.tsx` — add toggle

**Read for context:**
- `backend/.../deadline/DeadlineController.java`, `DeadlineCalculationService.java` — Phase 51 surface
- `backend/.../fielddefinition/FieldDefinition.java` — column addition pattern
- Phase 48 `FIELD_DATE_APPROACHING` trigger class (verify exact name)

### Architecture Decisions

- **Polymorphic `portal_deadline_view`** ([ADR-256](../adr/ADR-256-polymorphic-portal-deadline-view.md)) — one table, four sources, one controller. Portal UI is uniform regardless of origin, so polymorphism is the simpler shape.
- **Custom-field-date visibility is opt-in** ([ADR-257](../adr/ADR-257-custom-field-portal-visibility-opt-in.md)) — firms mark specific custom date fields as portal-deadline-visible. Default false keeps portal deadline feed signal-over-noise.
- **Sync service stays generic for Phase 55 events** — if court-date or prescription events aren't fully wired yet, handler methods exist but fire zero times until the events materialise; no additional work in this phase when they do.
- **Status is derived at sync time**, not read time — matches other portal read-model conventions.

### Non-scope

- No calendar view (list-only in this phase; toggle is a future polish).
- No in-app "mark completed" action from portal (status updates happen firm-side).
- No deadline-reminder email here — 498 owns notifications.

---

## Epic 498: Portal Notifications (digest + per-event + preferences)

**Goal**: Give portal contacts a reason to come back. Ship a weekly digest scheduler, per-event nudges for the three new verticals, a per-contact preferences page, and firm-level `OrgSettings.portalDigestCadence`. Reuses Phase 24 delivery infra; no new transport code.

**References**: Requirements §5.1–5.5; architecture §68.5; [ADR-253](../adr/ADR-253-portal-surfaces-as-read-model-extensions.md), [ADR-258](../adr/ADR-258-portal-notification-no-double-send.md).

**Dependencies**: 494A (session context for preferences API to know active modules); at least one of 495A/496A/497A for event streams. 498A (scaffolding) can start in parallel with Stage 1.

**Scope**: Both (backend + frontend)

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **498A** | 498.1–498.5 | 7 backend files | V107 tenant migration (`org_settings.portal_digest_cadence`), V22 portal migration (`portal_notification_preference`), `OrgSettings` field + enum, `PortalNotificationPreference` entity + repo + service stub, migration tests. Backend scaffolding only — no scheduler, no templates, no event channel yet. |
| **498B** | 498.6–498.14 | 10 backend files (scheduler, channel, 4 new templates, audit of 4 existing templates, integration tests) | `PortalDigestScheduler` (cron), `PortalEmailNotificationChannel` (per-event sibling to Phase 24 channel), 4 new Thymeleaf templates (`portal-weekly-digest`, `portal-trust-activity`, `portal-deadline-approaching`, `portal-retainer-period-closed`), audit of existing templates for no-double-send, integration tests. |
| **498C** | 498.15–498.20 | 8 files (portal frontend + firm-side cadence UI) | `/settings/notifications/page.tsx` + preferences API client + unsubscribe landing wiring, firm-side `OrgSettings.portalDigestCadence` + `portalRetainerMemberDisplay` settings UI (both on existing portal-settings section), component tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 498.1 | V107 tenant migration: `org_settings.portal_digest_cadence` | `backend/src/main/resources/db/migration/tenant/V107__add_portal_digest_cadence.sql` | covered by 498.5 | `tenant/V105__add_portal_retainer_member_display.sql` (496A) | `ALTER TABLE org_settings ADD COLUMN portal_digest_cadence VARCHAR(12) DEFAULT 'WEEKLY' CHECK (portal_digest_cadence IN ('WEEKLY','BIWEEKLY','OFF'))`. V-number depends on 496A/497A landing first — builder resolves. |
| 498.2 | V22 portal migration: `portal_notification_preference` | `backend/src/main/resources/db/migration/global/V22__portal_notification_preference.sql` | 498.5 | `global/V21__portal_deadline_view.sql` (497A) | `portal_notification_preference` (`portal_contact_id` UUID PK, `digest_enabled` BOOLEAN DEFAULT true, `trust_activity_enabled` BOOLEAN DEFAULT true, `retainer_updates_enabled` BOOLEAN DEFAULT true, `deadline_reminders_enabled` BOOLEAN DEFAULT true, `action_required_enabled` BOOLEAN DEFAULT true, `last_updated_at` TIMESTAMPTZ). |
| 498.3 | `OrgSettings.portalDigestCadence` + enum | `backend/src/main/java/.../settings/OrgSettings.java`, `backend/src/main/java/.../settings/PortalDigestCadence.java` | 498.5 | 496.3 | Enum `PortalDigestCadence {WEEKLY, BIWEEKLY, OFF}`. Field + getter + `OrgSettingsService` updater. |
| 498.4 | `PortalNotificationPreference` entity + repo + service stub | `backend/src/main/java/.../portal/notification/PortalNotificationPreference.java`, `PortalNotificationPreferenceRepository.java`, `PortalNotificationPreferenceService.java` | 498.5 | `notification/NotificationPreference.java` | Entity + repo. `PortalNotificationPreferenceService.getOrCreate(contactId)` returns default-true prefs if row missing. `update(contactId, dto)` persists. No scheduling/email yet. |
| 498.5 | Migration + preference-service tests | `backend/src/test/java/.../portal/notification/PortalNotificationPreferenceServiceIntegrationTest.java` | ~3 | 495.6 | (1) `getOrCreate` on first call creates row with all defaults true, (2) `update` persists, (3) `portal_digest_cadence` persists + defaults `WEEKLY`. |
| 498.6 | `PortalDigestScheduler` (weekly cron) | `backend/src/main/java/.../portal/notification/PortalDigestScheduler.java` | 498.14 | existing `MagicLinkCleanupService` (scheduler pattern) | `@Component @EnableScheduling`. `@Scheduled(cron = "0 0 8 ? * MON")` runs Monday 08:00. Per tenant with `orgSettings.portalDigestCadence != OFF`, iterate portal contacts with `digestEnabled=true`. For each: query portal read-model for 7-day activity (new invoices / proposals / acceptances / info requests / trust transactions / deadlines crossing into DUE_SOON / retainer period closes). If empty → skip (suppress). Else render `portal-weekly-digest` via existing `EmailTemplateRenderingService` + send via existing `PortalEmailService.sendDigest(...)`. For `BIWEEKLY`, skip on alternating Mondays (check `org_settings.digest_last_sent_at` or similar flag — add a small addition to `OrgSettings` if needed). |
| 498.7 | `PortalEmailNotificationChannel` (per-event) | `backend/src/main/java/.../portal/notification/PortalEmailNotificationChannel.java` | 498.14 | existing `notification/channel/EmailNotificationChannel.java` | `@Component`. Subscribes (via `@EventListener`) to events in requirements §5.2 that are not already wired. Specifically: `TrustTransactionApprovedEvent` → render `portal-trust-activity` + send via `PortalEmailService`. `FilingDeadlineApproachingEvent` (Phase 51) and/or `FIELD_DATE_APPROACHING` (Phase 48) → `portal-deadline-approaching`. `RetainerPeriodClosedEvent` (Phase 17) → `portal-retainer-period-closed`. Respects per-contact preferences via `PortalNotificationPreferenceService`. |
| 498.8 | Audit existing templates for no-double-send | no new code files — audit only; log findings in ADR-258 followup or slice notes | covered by 498.14 test | [ADR-258](../adr/ADR-258-portal-notification-no-double-send.md) | Verify `AcceptanceRequestCreatedEvent`, `InformationRequestCreatedEvent`, `ProposalSentEvent`, `InvoiceIssuedEvent` already email via their Phase 24/28/32/34 channels. Builder records in slice PR description which channel is authoritative. `PortalEmailNotificationChannel` MUST NOT subscribe to these. |
| 498.9 | `portal-weekly-digest.html` Thymeleaf template | `backend/src/main/resources/email-templates/portal-weekly-digest.html` | 498.14 | existing magic-link template under `email-templates/` | Thymeleaf with per-module conditional blocks: new invoices, pending acceptances, pending info requests, recent trust activity (if legal-za), upcoming deadlines (if deadlines module enabled), retainer period close (if retainer). CTA "Open portal →" links to `/home`. Variables bundle provided by scheduler. |
| 498.10 | `portal-trust-activity.html` template | `backend/src/main/resources/email-templates/portal-trust-activity.html` | 498.14 | 498.9 | Subject "{{orgName}}: Trust account activity". Body: one-transaction summary (date, type, amount, running balance), deep link to `/trust/{matterId}`. |
| 498.11 | `portal-deadline-approaching.html` template | `backend/src/main/resources/email-templates/portal-deadline-approaching.html` | 498.14 | 498.9 | Subject "Upcoming deadline: {{label}} on {{dueDate}}". Body: label + due date + days-away + deep link to `/deadlines`. |
| 498.12 | `portal-retainer-period-closed.html` template | `backend/src/main/resources/email-templates/portal-retainer-period-closed.html` | 498.14 | 498.9 | Subject "Your retainer period has closed". Body: period dates + hours used + rollover hours + deep link to `/retainer/{id}`. |
| 498.13 | Digest content-assembly service | `backend/src/main/java/.../portal/notification/PortalDigestContentAssembler.java` | 498.14 | existing context builders under `template/context/` | `@Component`. `assemble(portalContactId, lookbackDays)` → queries portal read-model repos (invoices / acceptances / requests / trust / retainer / deadlines), returns bundle consumed by the digest template. Returns `null` if all zero (scheduler suppresses). |
| 498.14 | Scheduler + channel integration tests | `backend/src/test/java/.../portal/notification/PortalDigestSchedulerIntegrationTest.java`, `PortalEmailNotificationChannelIntegrationTest.java` | ~7 | existing email-delivery tests under Phase 24 | (1) populated portal contact → digest email sent with expected subject + rendered body, (2) empty lookback → no email (suppressed), (3) contact with `digestEnabled=false` → no email, (4) `cadence=BIWEEKLY` skips alternate Mondays, (5) `TrustTransactionApprovedEvent` fires `portal-trust-activity` send, (6) `RetainerPeriodClosedEvent` fires `portal-retainer-period-closed`, (7) `InvoiceIssuedEvent` does NOT fire any Phase 68 portal send (no-double-send assertion). |
| 498.15 | `portal/lib/api/notification-preferences.ts` | `portal/lib/api/notification-preferences.ts` | 498.20 | `portal/lib/api/acceptance.ts` | `getPreferences()`, `updatePreferences(dto)`. |
| 498.16 | `PortalNotificationPreferenceController` | `backend/src/main/java/.../portal/notification/PortalNotificationPreferenceController.java` | 498.20 | `portal/PortalBrandingController.java` | `GET /api/portal/notification-preferences` + `PUT /api/portal/notification-preferences`. Authed via `CustomerAuthFilter`. |
| 498.17 | `/settings/notifications/page.tsx` | `portal/app/(authenticated)/settings/notifications/page.tsx` | 498.20 | `portal/app/(authenticated)/profile/page.tsx` | Toggle list per requirements §5.3. "Unsubscribe all" button → set all toggles false + save. Surfaces current firm-level cadence (read-only: "Your firm sends weekly digests"). |
| 498.18 | Unsubscribe landing wiring | `portal/app/(authenticated)/settings/notifications/page.tsx` (handler for `?unsubscribe=1` query param) | 498.20 | Phase 24 unsubscribe pattern | When page loads with `?unsubscribe=1`, auto-set digest toggle to false + save + show confirmation banner. Digest email footer links here. |
| 498.19 | Firm-side cadence + member-display settings UI | `frontend/app/(app)/org/[slug]/settings/portal/page.tsx` (existing portal settings or new — verify path) | 498.20 | existing org-settings form patterns | Two selects: `Portal digest cadence` (WEEKLY/BIWEEKLY/OFF) and `Portal retainer member display` (4 options from 496). Persist via existing `OrgSettingsController` update endpoint (extend DTO). Small addition — ~30 lines total. |
| 498.20 | Frontend + firm-side component tests | `portal/app/(authenticated)/settings/__tests__/notifications.test.tsx`, `frontend/__tests__/settings/portal-settings-cadence.test.tsx` | ~5 | 495.13 | (1) preferences toggles reflect server state, (2) "Unsubscribe all" sets all to false + calls PUT, (3) `?unsubscribe=1` query auto-unsubscribes, (4) firm-side cadence select persists, (5) firm-side member-display select persists. `afterEach(() => cleanup())`. |

### Key Files

**Create:**
- `backend/src/main/resources/db/migration/tenant/V107__add_portal_digest_cadence.sql`
- `backend/src/main/resources/db/migration/global/V22__portal_notification_preference.sql`
- `backend/src/main/java/.../settings/PortalDigestCadence.java`
- `backend/src/main/java/.../portal/notification/PortalNotificationPreference.java`
- `backend/src/main/java/.../portal/notification/PortalNotificationPreferenceRepository.java`
- `backend/src/main/java/.../portal/notification/PortalNotificationPreferenceService.java`
- `backend/src/main/java/.../portal/notification/PortalNotificationPreferenceController.java`
- `backend/src/main/java/.../portal/notification/PortalDigestScheduler.java`
- `backend/src/main/java/.../portal/notification/PortalDigestContentAssembler.java`
- `backend/src/main/java/.../portal/notification/PortalEmailNotificationChannel.java`
- `backend/src/main/resources/email-templates/portal-weekly-digest.html`
- `backend/src/main/resources/email-templates/portal-trust-activity.html`
- `backend/src/main/resources/email-templates/portal-deadline-approaching.html`
- `backend/src/main/resources/email-templates/portal-retainer-period-closed.html`
- 3 backend test classes
- `portal/lib/api/notification-preferences.ts`
- `portal/app/(authenticated)/settings/notifications/page.tsx`
- `portal/app/(authenticated)/settings/__tests__/notifications.test.tsx`
- `frontend/__tests__/settings/portal-settings-cadence.test.tsx`

**Modify:**
- `backend/src/main/java/.../settings/OrgSettings.java` — add `portalDigestCadence`
- `backend/src/main/java/.../settings/OrgSettingsService.java` — updater + possibly `digestLastSentAt` bookkeeping
- `frontend/app/(app)/org/[slug]/settings/portal/page.tsx` — cadence + member-display selects

**Read for context:**
- `backend/.../portal/PortalEmailService.java` — existing send pipeline
- `backend/.../notification/channel/EmailNotificationChannel.java` — per-event channel shape
- `backend/.../notification/template/` — Phase 24 template rendering
- `backend/.../portal/MagicLinkCleanupService.java` — existing scheduler example

### Architecture Decisions

- **No double-send** ([ADR-258](../adr/ADR-258-portal-notification-no-double-send.md)) — if an event already emails in a prior phase (24/28/32/34), this phase does not re-wire it. Phase 68 adds new channels only for trust, deadline, retainer.
- **Digest suppresses empty** — avoids noise for inactive portal contacts.
- **Cadence is firm-level; opt-out is contact-level** — avoids firm-micromanagement while giving clients an escape hatch.
- **Reuses Phase 24 delivery + log** — no new transport, no new bounce handling, unsubscribe mechanics inherit.

### Non-scope

- No SMS / push / webhook.
- No per-matter notification scoping — preferences are per-contact global.
- No A/B subject-line testing.
- No quiet hours / time-zone-aware sends (runs in system tz).

---

## Epic 499: Mobile Polish & Responsive Pass

**Goal**: Invest once in mobile-responsive polish across all portal pages — existing and new — so the portal renders cleanly at sm (375×667), md (768×1024), and lg (1280×800). Capture Playwright visual baselines.

**References**: Requirements §6.1–6.4; architecture §68.6.

**Dependencies**: 494B (shell), 495B/496B/497B/498C (new pages must exist before they can be audited).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **499A** | 499.1–499.7 | 10–12 existing pages + shared primitives | Audit + reflow pre-existing portal pages: login, projects (list + detail), invoices (list + detail + payment results), proposals (list + detail), requests, acceptance, profile. Empty/loading/error state audit. Sticky bottom-action bars where pages have one primary action. Tap targets ≥44px. |
| **499B** | 499.8–499.12 | 5–6 new pages + baseline capture | Audit new pages (home, trust, retainer, deadlines, settings/notifications). Design-token audit across all pages. Playwright visual baselines at sm/md/lg stored under `portal/e2e/screenshots/portal-v2/` (create if needed — verify portal has an e2e dir; otherwise reuse `frontend/e2e/` under a portal-specific spec directory). |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 499.1 | Mobile audit: login + profile | `portal/app/login/page.tsx`, `portal/app/(authenticated)/profile/page.tsx` | 499.7 | existing frontend mobile patterns | Form width collapses to full viewport below `md`. 44px input tap targets. |
| 499.2 | Mobile audit: projects list + detail | `portal/app/(authenticated)/projects/page.tsx`, `portal/app/(authenticated)/projects/[id]/page.tsx` | 499.7 | n/a | Table in detail page converts to card list below `md`. |
| 499.3 | Mobile audit: invoices list + detail + payment results | `portal/app/(authenticated)/invoices/page.tsx`, `portal/app/(authenticated)/invoices/[id]/page.tsx`, `invoices/[id]/payment-success/page.tsx`, `invoices/[id]/payment-cancelled/page.tsx` | 499.7 | n/a | Invoice line table either horizontally scrollable or card-list below `md`. Sticky "Pay invoice" bottom bar on detail page below `md`. |
| 499.4 | Mobile audit: proposals list + detail | `portal/app/(authenticated)/proposals/page.tsx`, `portal/app/(authenticated)/proposals/[id]/page.tsx` | 499.7 | n/a | Sticky "Accept / Decline" bottom bar where applicable. |
| 499.5 | Mobile audit: requests + acceptance flow | `portal/app/(authenticated)/requests/*` (verify path — Phase 34 portal info requests), `portal/app/(authenticated)/acceptance/*`, `portal/app/accept/[token]/page.tsx` | 499.7 | n/a | File-upload control works on mobile; acceptance dialog uses bottom sheet. |
| 499.6 | Empty / loading / error state pass — existing pages | touches pages from 499.1–499.5 + any shared skeleton primitives | 499.7 | existing skeleton patterns | Every existing page has dedicated empty state + loading skeleton matching content shape + error retry. |
| 499.7 | Existing-page component tests for responsive behaviour | `portal/components/__tests__/invoice-line-table-responsive.test.tsx`, etc. | ~4 | existing portal component tests | Render at sm and lg widths (via `window.innerWidth` mock or `matchMedia` mock) and assert layout differs (card vs table, bottom bar presence). `afterEach(() => cleanup())`. |
| 499.8 | Mobile audit: home | `portal/app/(authenticated)/home/page.tsx` | 499.12 | n/a | Quick-action cards stack vertically below `md`. |
| 499.9 | Mobile audit: trust, retainer, deadlines | `portal/app/(authenticated)/trust/**`, `portal/app/(authenticated)/retainer/**`, `portal/app/(authenticated)/deadlines/page.tsx` | 499.12 | n/a | Transaction list + consumption list + deadline list collapse to card view below `md`. Detail panel on deadlines slides as bottom sheet. |
| 499.10 | Mobile audit: settings/notifications | `portal/app/(authenticated)/settings/notifications/page.tsx` | 499.12 | n/a | Toggle list full-width below `md`. |
| 499.11 | Design-token audit + accessibility sanity | shared primitives across `portal/components/ui/` | 499.12 | n/a | No hardcoded `px` widths that break below `md`. Keyboard-only nav works on the new sidebar (focus ring + skip-to-content link on topbar). |
| 499.12 | Playwright baseline capture at sm / md / lg | `portal/e2e/tests/responsive/portal-pages.spec.ts` (new) + `portal/e2e/screenshots/portal-v2/` directory populated on first run + `portal/playwright.portal.config.ts` (new or extend existing) | baseline artifacts | `frontend/e2e/playwright.legal-lifecycle.config.ts` from Phase 67 | Spec iterates over the 10 portal pages × 3 breakpoints, invokes `toHaveScreenshot(...)` on each. Produces 30 baselines under `portal/e2e/screenshots/portal-v2/`. `maxDiffPixelRatio: 0.01`, `deviceScaleFactor: 2`. |

### Key Files

**Create:**
- `portal/e2e/tests/responsive/portal-pages.spec.ts`
- `portal/playwright.portal.config.ts` (if no existing portal playwright config)
- `portal/e2e/screenshots/portal-v2/` (baseline dir, populated on first run)
- `portal/components/__tests__/invoice-line-table-responsive.test.tsx` and ~3 others

**Modify:**
- Every page listed in requirements §6.1 — small layout touches; ~10–12 files
- Shared primitives under `portal/components/ui/` as needed

**Read for context:**
- `frontend/e2e/playwright.legal-lifecycle.config.ts` — config shape reference
- Existing portal component + page code

### Architecture Decisions

- **One mobile-polish slice, not incremental** — concentrated investment avoids re-visit churn.
- **Tables collapse to cards below `md`, not horizontal scroll** — except where meaningful (trust transaction list opts into scroll because running-balance needs column alignment).
- **Baselines live under `portal/e2e/screenshots/portal-v2/`** — namespace prefix `v2` reserves room for a future redesign without touching the old baselines.

### Non-scope

- No PWA / offline / native-app shell.
- No dark mode.
- No new components — only layout + responsive CSS adjustments + empty/loading/error states.

---

## Epic 500: Client-POV 90-Day QA Capstone + Screenshots + Gap Report

**Goal**: Produce and execute the first client-POV 90-day lifecycle script for the portal. Capture Playwright screenshot baselines + curated documentation screenshots. Produce `tasks/phase68-gap-report.md`.

**References**: Requirements §7.1–7.4; architecture §68.7.

**Dependencies**: Every other slice (494–499) merged.

**Scope**: E2E / Process

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **500A** | 500.1–500.5 | Script file + 3–4 Playwright spec files + config | Author `qa/testplan/demos/portal-client-90day-keycloak.md` (11 checkpoints from Day 0 → Day 90, mixed across the three vertical profiles). Scaffold Playwright specs under `portal/e2e/tests/portal-client-90day/`. Verify `/qa-cycle-kc` compatibility. |
| **500B** | 500.6–500.10 | Baseline artifacts + curated PNGs + gap-report markdown | Execute end-to-end against three fresh tenants (legal-za, accounting-za, consulting-za). Capture Playwright baselines to `portal/e2e/screenshots/portal-client-90day/`. Curated marketing/demo shots to `documentation/screenshots/portal/`. Produce `tasks/phase68-gap-report.md`. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 500.1 | Draft `portal-client-90day-keycloak.md` — Day 0–30 | `qa/testplan/demos/portal-client-90day-keycloak.md` | n/a | `qa/testplan/demos/legal-small-firm-90day-keycloak.md` | Day 0: magic-link → home, review info requests. Day 3: upload + submit info request. Day 7: receive first weekly digest, click into deadlines (accounting-za / legal-za). Day 14: review + accept proposal. Day 21: trust deposit nudge (legal-za). Day 30: view hour-bank (consulting-za / legal-za) + current consumption. Each checkpoint labelled by applicable profile per §7.1. |
| 500.2 | Draft script — Day 45–90 | same file as 500.1 | n/a | n/a | Day 45: pay invoice on portal. Day 60: download Phase 67 SoA or financial statement. Day 75: deadline-approaching nudge → mark read. Day 85: update digest cadence to biweekly. Day 90: final digest + review. |
| 500.3 | Scaffold Playwright specs | `portal/e2e/tests/portal-client-90day/day-00-07.spec.ts`, `day-14-30.spec.ts`, `day-45-75.spec.ts`, `day-85-90.spec.ts` | baseline artifacts | `frontend/e2e/tests/legal-depth-ii/day-05-disbursements.spec.ts` (Phase 67) | Each spec mirrors the script checkpoints. Uses Playwright + real Keycloak auth + `/qa-cycle-kc` skill convention. `toHaveScreenshot()` at each checkpoint. |
| 500.4 | Extend `portal/playwright.portal.config.ts` for the 90-day lifecycle | `portal/playwright.portal.config.ts` | n/a | 499.12 | Add a dedicated project pointing at `portal/e2e/tests/portal-client-90day/` with `workers: 1` (sequential — later days depend on earlier data). |
| 500.5 | `/qa-cycle-kc` compatibility check | slice notes only | n/a | existing `/qa-cycle-kc` skill | Verify the skill points at portal routes (port 3001 or whatever portal runs on) + portal auth headers. Document any shim needed. |
| 500.6 | Run Day 0–30 against 3 fresh tenants, capture baselines | populates `portal/e2e/screenshots/portal-client-90day/` | baselines | 493 capstone | Sequential execution. Fix or log any failures. |
| 500.7 | Run Day 45–90, capture baselines | same directory | baselines | n/a | Same as 500.6. |
| 500.8 | Curated marketing/demo screenshots | `documentation/screenshots/portal/` — desktop shell × 3 profiles, mobile drawer open × 3, home populated × 3, trust detail, retainer detail, deadlines list, settings/notifications, invoice-payment, acceptance flow on mobile, digest email | n/a | `documentation/screenshots/legal-vertical/phase67/` | ~16 curated PNGs per requirements §7.2. |
| 500.9 | Produce `tasks/phase68-gap-report.md` | `tasks/phase68-gap-report.md` | n/a | `tasks/phase67-gap-report.md` | Sections: Executive summary. Statistics (passed/failed/skipped per day). Gaps by severity (Blocker/Major/Minor). Recommended fix phase. Pre-log known gaps: audit-trail view (parked to Phase 69), multi-contact portal roles, two-way messaging, disbursement portal view, PWA, i18n, dark mode, portal search. |
| 500.10 | Add `test:e2e:portal-client-90day` npm script | `portal/package.json` | n/a | `frontend/package.json` (Phase 67 `test:e2e:legal-depth-ii`) | Single-line script invoking Playwright with the dedicated config project. |

### Key Files

**Create:**
- `qa/testplan/demos/portal-client-90day-keycloak.md`
- `portal/e2e/tests/portal-client-90day/day-00-07.spec.ts`
- `portal/e2e/tests/portal-client-90day/day-14-30.spec.ts`
- `portal/e2e/tests/portal-client-90day/day-45-75.spec.ts`
- `portal/e2e/tests/portal-client-90day/day-85-90.spec.ts`
- `portal/e2e/screenshots/portal-client-90day/` (baselines dir, populated on first run)
- `documentation/screenshots/portal/` (curated PNGs, ~16)
- `tasks/phase68-gap-report.md`

**Modify:**
- `portal/playwright.portal.config.ts` — add lifecycle project
- `portal/package.json` — add `test:e2e:portal-client-90day` script

**Read for context:**
- `tasks/phase67-legal-depth-ii.md` Epic 493 — capstone precedent
- `qa/testplan/demos/legal-small-firm-90day-keycloak.md` — firm-side lifecycle example
- `documentation/screenshots/legal-vertical/phase67/` — curated directory pattern

### Architecture Decisions

- **Client-POV is deliberately distinct from firm-side lifecycles** — does not reuse the legal-90-day spine. Clients interact with different surfaces and different affordances.
- **Three tenants exercised in one run** — to cover all three vertical gating branches without three separate lifecycle files.
- **Curated screenshots live under `documentation/screenshots/portal/`** — separate from regression baselines.
- **Sequential `workers: 1`** — later days read data created by earlier days (same constraint Phase 67 capstone honoured).

### Non-scope

- No new QA infrastructure — reuses `/qa-cycle-kc` and Phase 67 Playwright patterns.
- No gap fixes in this slice — gaps are documented, not resolved.
- No i18n / multi-currency test coverage.

---

## Risk Register

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|------------|--------|------------|
| 1 | **Migration number collisions across parallel slices**. 495A, 496A, 497A, 498A each introduce migrations (portal V19–V22 + tenant V105–V107). If they merge in unpredictable order, Flyway V-numbers will clash. | High | High — blocks every slice downstream of the offender. | Every migration task explicitly notes "verify next available V-number at build time and rename." Ordering in the phase plan serialises V-number reservations: 495A→V19, 496A→V20+V105, 497A→V21+V106, 498A→V22+V107. If one slice lands early, subsequent slices re-verify and rename. |
| 2 | **Event name drift** for Phase 55 court-date / prescription events. Architecture assumes `CourtDateScheduledEvent`, `PrescriptionDeadlineSetEvent`. If Phase 55 renamed or never shipped those, `DeadlinePortalSyncService` (497A) compiles against missing classes. | Medium | Medium — deadline feed for court dates / prescriptions silently empty. | Slice 497A's first action is to grep for the exact event class names under `backend/.../verticals/legal/` and adapt. If absent, wire the handler generically (on an imaginary supertype or empty listener) and leave a TODO for Phase 55-B to publish; the gap lands in the capstone report. |
| 3 | **Double-send on existing Phase 24/28/32/34 emails** if `PortalEmailNotificationChannel` (498B) accidentally subscribes to an already-wired event. Clients receive two emails per acceptance / proposal / invoice. | Medium | Medium — client annoyance, possible spam-filter hits. | Slice 498B task 498.8 is an explicit audit step — builder reads the four existing templates' channels and documents authoritative send path in the PR description. Integration test 498.14 (7) asserts `InvoiceIssuedEvent` does NOT fire any new channel. |
| 4 | **Capability / module gate race on first paint** — `<ModuleGate>` + `use-portal-context` both resolve asynchronously; sidebar may flash "all items" then filter. | Medium | Low (UX only) | `use-portal-context` returns `null` while loading — sidebar returns a compact skeleton until resolved. 494.14 test asserts loading state hides nav items rather than showing then hiding. |
| 5 | **Portal read-model freshness lag** — if sync services fall behind (backfill path slow), portal trust/retainer/deadline pages show stale balances. | Medium | Medium — client sees "your balance is Rx" when firm updated minutes ago. | Sync services are synchronous on-event. `backfillForTenant` runs at module-activation time only. Phase 50 resync path (`PortalResyncService`) is the escape hatch; `/api/portal/resync` remains available. Add `last_synced_at` to each read-model row so portal UI can surface "as of HH:MM" when data is stale. |
| 6 | **Terminology key mapping missing for new entities** — portal home shows "Your matter is ready" vs "Your project is ready" but the message catalogue (Phase 43) may not carry the keys the new pages use. | Low | Low (wrong copy) | Slice 494B seeds nav labels from the central registry's `labelKey`; new portal pages use plain English until catalogue keys land in a follow-up polish. Builder documents missing keys in the gap report. |
| 7 | **Digest scheduler cron collision across tenants** — running 08:00 Monday UTC for every tenant could hit PSP rate-limits on the EmailProvider. | Low | Low | Scheduler paginates tenants and spaces sends via existing `PortalEmailService` retry/backoff. If count grows large, a future phase introduces per-tenant staggering. |
| 8 | **Playwright visual baseline flakiness** from network-dependent elements (logos, avatars, live timestamps). | Medium | Low (CI noise) | Specs use `page.clock().install()` (Playwright ≥1.45) or freeze time via test utility. `waitForLoadState('networkidle')` before every `toHaveScreenshot`. |
| 9 | **Firm-side cadence UI collision** — 498C adds cadence + member-display selects to the firm portal-settings page; if 496A or 498A already added partial UI or if the portal-settings page does not exist, the builder may duplicate or misplace. | Low | Low | 498C task 498.19 explicitly checks for the existing portal-settings page first; if absent, adds the two controls to the most-natural existing org-settings section. |
| 10 | **Polymorphic `portal_deadline_view` PK ambiguity** — if `(source_entity, id)` composite is used and a firm-side id collides across source tables (unlikely with UUIDs but possible with test fixtures), upsert logic may overwrite unrelated rows. | Low | Medium | 497.2 uses `(source_entity, id)` composite PK. 497.6 integration test (2) explicitly re-upserts the same filing-schedule event twice and asserts no row count change — would catch accidental cross-source collisions too. |

---

### Critical Files for Implementation

- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContextController.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/portal/lib/nav-items.ts`
- `/Users/rakheendama/Projects/2026/b2b-strawman/portal/components/portal-sidebar.tsx`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/TrustLedgerPortalSyncService.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestScheduler.java`
