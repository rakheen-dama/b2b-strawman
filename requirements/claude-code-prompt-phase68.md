# Phase 68 — Portal Redesign & Vertical Parity

## System Context

Kazi's customer-facing portal was last touched substantively in **Phase 22** (Portal frontend scaffolding, PRs #327–#332), with targeted extensions since:

- **Phase 25** — online payment flow + read-model extension (PRs #372–#375).
- **Phase 28** — portal document acceptance (PRs #408–#409).
- **Phase 32** — portal proposal pages (PRs #483–#484).
- **Phase 34** — portal information requests with upload & submit (PRs #551–#552).

What the portal ships today:

- Next.js app under `portal/`, separate from `frontend/`, deployed as its own container.
- Authenticated routes under `portal/app/(authenticated)/`: `projects/`, `projects/[id]/`, `invoices/`, `invoices/[id]/`, `invoices/[id]/payment-success/`, `invoices/[id]/payment-cancelled/`, `proposals/`, `proposals/[id]/`, `profile/`. Plus public routes: `login/`, `accept/[token]/`, `auth/exchange/`.
- Layout shell: `portal/app/(authenticated)/layout.tsx` → `PortalHeader` (top horizontal nav) + content + `PortalFooter`.
- Nav component: `portal/components/portal-header.tsx`. `NAV_LINKS` array contains three entries: Projects, Proposals, Invoices. Profile is a small icon on the right; Logout sits next to it. Mobile menu state already scaffolded via `mobileMenuOpen`.
- Branding: `portal/components/branding-provider.tsx` + `portal/hooks/use-branding.ts`, driven by the `PortalBrandingController` (`backend/.../portal/PortalBrandingController.java`).
- Auth: magic-link via `PortalAuthController` + `PortalAuthService`. Client hook: `portal/hooks/use-auth.ts`.
- Portal read-model (Phase 7, 22, 25, 28, 32, 34): separate schema, hydrated via `PortalReadModelService` (`backend/.../customerbackend/service/PortalReadModelService.java`) and per-entity sync services (e.g. `ProposalPortalSyncService`) listening to firm-side domain events. Portal controllers live under `backend/.../customerbackend/controller/` and `backend/.../portal/`.
- Portal email: `PortalEmailService` (`backend/.../portal/PortalEmailService.java`) uses the Phase 24 `EmailProvider` port for magic-link delivery. Template rendering via Phase 24 `EmailTemplateRenderingService`. `EmailDeliveryLog` tracks per-send history.

**What six months of vertical work has produced firm-side but left invisible to clients:**

| Vertical | Shipped firm-side | On portal today |
|---|---|---|
| Legal-ZA | Trust accounts, client ledger cards, trust transactions, interest posting, Section 35 reports (Phase 60 + 61) | No |
| Legal-ZA | Court calendar, prescription tracker, conflict check, tariff (Phase 55 — planned; some UI done) | No |
| Accounting-ZA | Deadline calculator + filing statuses + schedule actions (Phase 51) | No |
| Consulting-ZA / Legal-ZA | Retainer agreements, hour-bank consumption tracking (Phase 17) | No |

The client sees their invoices and proposals. They cannot see the trust balance held on their behalf, the hours remaining on their retainer, or their upcoming filing deadlines. Each vertical shipped a firm-facing tab and dashboard widget; none shipped a portal surface.

**Observed nav problem.** The current `NAV_LINKS` array has three entries. Adding trust, retainer, deadlines, information requests, acceptances, and documents-tab exposure would push top-nav to 8+ entries — horizontal breaks at ~5 on typical client laptops and looks crowded even sooner. Decision has been taken (Phase 68 ideation, 2026-04-18) to move to a slim left-rail sidebar optimised for client-first skim use rather than mirroring the firm app's zoned sidebar + command palette (Phase 44).

**Observed re-engagement problem.** Portal activity is driven only by magic-link emails at login time (Phase 24 170B) and transactional notifications for proposals (32) / acceptance (28) / information requests (34). There is no digest, no per-event push for the new vertical surfaces, and no portal-user preferences page. Clients who do not have an active information request or pending acceptance have no reason to come back.

**Observed QA gap.** Every firm-side vertical has a 90-day lifecycle script (Phase 47, 64, 66). No script has ever been written from the client's perspective. Agents running portal flows today improvise — no baselines, no screenshot regression, no reproducible "client received a trust statement and checked their balance" scenario.

## Objective

Ship a redesigned portal nav that scales beyond three items, surface the trust / retainer / deadline data that has been sitting firm-side, add notification glue so clients come back, invest once in a mobile-responsive polish pass across existing pages, and establish a client-POV 90-day QA lifecycle script with screenshot baselines — so that customer-interaction QA cycles can begin in earnest in later phases.

## Constraints & Assumptions

- **Nav shape is settled: slim left rail.** See Section 1 for details. No command palette, no zoned grouping, no mirror-of-firm-app. The portal intentionally feels simpler than the firm tool.
- **No new backend entities.** All new portal surfaces are **read-model extensions** of existing firm-side entities + portal sync handlers + portal controllers + frontend pages. This is the same pattern used since Phase 7 (portal read-model) and Phase 22 (portal frontend).
- **Vertical coverage — all three verticals in scope.** Trust ledger (`legal-za`), retainer usage (`consulting-za` + `legal-za`), deadlines (`accounting-za` + `legal-za`). Each surface is visible only to portal users whose firm tenant has the relevant profile + module enabled. Non-applicable surfaces do not appear in the portal nav for that tenant.
- **Profile / module gating is tenant-driven, not client-driven.** If a firm has `legal-za` profile enabled, all of that firm's portal users see the trust nav entry. There is no per-client toggle. This mirrors how firm-side module gates work (`VerticalModuleRegistry` + `<ModuleGate>`).
- **Notifications use existing Phase 24 infrastructure.** `PortalEmailService` + `EmailTemplateRenderingService` + `EmailDeliveryLog` already handle rendering, send, retry, unsubscribe, and bounce. Phase 68 adds new templates + new event subscriptions + a digest scheduler, not new infrastructure.
- **Portal notification preferences are per portal contact**, stored on (or adjacent to) `PortalContact`. No firm-side notification preference bleed-through.
- **Read-model contract is read-only for portal.** Portal frontend never mutates trust / retainer / deadline rows. Writes happen firm-side; portal consumes.
- **Statement of Account (Phase 67) is separate from trust ledger view.** Statement of Account is a *document* generated by the firm and delivered to the client (it appears as a `GeneratedDocument` on the matter). The trust ledger view on the portal is a *live balance + recent transactions* surface. They complement each other; they are not the same thing.
- **Disbursement portal view is out of scope.** Phase 67 is shipping disbursements firm-side now. Portal exposure of disbursements will land in a later phase once Phase 67 has stabilised.
- **Mobile polish scope is limited to existing pages + the six new pages added this phase.** No introduction of a native-app shell, no PWA work, no offline mode.
- **All new portal pages support portal terminology overrides** (Phase 48 364A established terminology wiring firm-side; portal extension of that terminology system happens here where needed — if it does not already extend through the branding endpoint).
- **No i18n / multi-language in this phase.** Portal stays `en-ZA` / English. Copy goes through the same message catalogue (Phase 43 327A) for future translation but no non-English rendering.
- **No multi-contact / roles enhancement to PortalContact.** One portal contact per customer continues. Role hierarchies on portal are a separate phase.
- **No two-way messaging thread / DM feature.** Existing `PortalCommentController` surface is sufficient for structured per-project comments.
- **Explicitly out of scope** (parked to later phases):
  - Firm-side audit log viewer and portal activity-trail view — **Phase 69**.
  - Multi-contact / per-client user roles on portal.
  - Two-way messaging thread / DM.
  - Portal i18n / multi-language rendering.
  - Disbursement visibility on portal (follows Phase 67 stabilisation).
  - Statement of Account scheduled auto-delivery to portal (manual generation + attach from Phase 67 is sufficient for now).
  - PWA / native app shell / offline mode.

---

## Section 1 — Nav Restructure & Layout Shell

### 1.1 Target Shape

Replace the current top-nav (`portal/components/portal-header.tsx`) with a two-column layout:

```
┌───────────────────────────────────────┐
│  Logo / Org Name   ·   Customer · v   │  ← slim top bar (48px): branding + user menu
├────────────┬──────────────────────────┤
│            │                          │
│  ● Home    │                          │
│  ● Projects│       page content       │
│  ● Matters │       (max-w-4xl)        │
│  ● Trust   │                          │
│  ● Retainer│                          │
│  ● Deadlines                          │
│  ● Invoices│                          │
│  ● Proposals                          │
│  ● Requests│                          │
│  ● Documents                          │
│            │                          │
│  Settings  │                          │
│  Logout    │                          │
└────────────┴──────────────────────────┘
```

- **Left rail**: 240px wide on desktop (≥`lg`), always visible. Icon + label per entry. Active route highlighted with brand colour indicator bar on the left edge of the entry. Grouping not required; ordering is deliberate (Home at top, settings + logout at bottom).
- **Top bar**: 48px tall, contains org logo/name on the left and user menu (name + dropdown with profile + logout) on the right. The top bar is slim intentionally — it is not a nav, it is branding + identity.
- **Content area**: `max-w-4xl` (narrower than today's `max-w-6xl` because the rail now takes 240px); centered within the remaining viewport.

### 1.2 Nav Item Registry

Centralise nav items into `portal/lib/nav-items.ts`:

```ts
type PortalNavItem = {
  href: string;
  label: string;       // defaults to English; can be replaced by terminology override
  labelKey?: string;   // for terminology override (e.g. "portal.nav.matters")
  icon: LucideIcon;
  // Which profile enables this item. If undefined, always shown.
  profiles?: Array<"legal-za" | "accounting-za" | "consulting-za" | "legal-generic" | "consulting-generic">;
  // Which backend module must be enabled for this item.
  module?: string;
  // Optional feature flag / branding flag.
  requiresFlag?: string;
};
```

Initial catalogue:

| href | label | icon | profile gate | module gate |
|---|---|---|---|---|
| `/home` (new) | Home | Home | — | — |
| `/projects` | Projects (→ Matters under legal-za terminology) | Folder | — | — |
| `/trust` (new) | Trust Account | Landmark | `legal-za` | `trust_accounting` |
| `/retainer` (new) | Retainer | Clock | `legal-za`, `consulting-za` | `retainer_agreements` (Phase 17 module or implicit) |
| `/deadlines` (new) | Deadlines | CalendarClock | `accounting-za`, `legal-za` | `deadlines` (Phase 51 module) |
| `/invoices` | Invoices | Receipt | — | — |
| `/proposals` | Proposals | FileText | — | — |
| `/requests` (new) | Information Requests | ClipboardList | — | `information_requests` |
| `/acceptance` (new) | Pending Acceptance | PenTool | — | `document_acceptance` |
| `/documents` (new) | Documents | Files | — | — |

A new `Home` landing page consolidates the "quick actions" surface (count of pending info requests, pending acceptances, upcoming deadlines, recent invoices, last trust movement) — replaces the current default-to-projects behaviour.

### 1.3 Profile & Module Resolution in Portal

Portal today does not know the firm tenant's vertical profile. This phase introduces a `/api/portal/session/context` endpoint (or extends the existing `/api/portal/summary` — builder decides) returning:

```json
{
  "tenantProfile": "legal-za",
  "enabledModules": ["trust_accounting", "retainer_agreements", "information_requests", "document_acceptance"],
  "terminologyKey": "en-ZA-legal",
  "brandColor": "#0f766e",
  "orgName": "Example Attorneys",
  "logoUrl": "..."
}
```

New hook `portal/hooks/use-portal-context.ts` fetches once on app mount, caches, and exposes `useProfile()` + `useModules()`. Nav rendering filters the `PortalNavItem` catalogue against this context. Branding context continues via `use-branding` (possibly merged into `use-portal-context` — builder decides).

### 1.4 Mobile Drawer

Below `md` breakpoint:
- Left rail collapses into a hamburger-triggered drawer over the content.
- Top bar stays visible; hamburger replaces the logo position shift (logo stays, hamburger is to the left of the logo).
- Drawer content is identical to the desktop rail — same icon + label list, same active-state treatment, same footer (settings + logout).
- Drawer state reuses the existing `mobileMenuOpen` pattern.

### 1.5 Terminology

Portal-side terminology works via `labelKey` lookup against the terminology key returned in session context. If `terminologyKey === "en-ZA-legal"`, `/projects` renders as "Matters" and its nav label, breadcrumbs, and page titles update accordingly. If no `labelKey` is defined on an item, the English `label` renders. The terminology map extends to portal-rendered entity references (e.g. "Your matter is ready" vs "Your project is ready") via the message catalogue (Phase 43 327A).

No new backend terminology infrastructure — portal consumes the existing map through the session context endpoint.

### 1.6 Deliverables

- Replace `portal/components/portal-header.tsx` with a slim top bar (`portal/components/portal-topbar.tsx`).
- New `portal/components/portal-sidebar.tsx` — desktop + mobile-drawer variant.
- New `portal/lib/nav-items.ts` — central nav registry.
- New `portal/hooks/use-portal-context.ts` — session context hook.
- Update `portal/app/(authenticated)/layout.tsx` to compose the new shell.
- New `backend/.../portal/PortalContextController.java` (or extend existing `PortalSummaryController`) — `/api/portal/session/context`.
- New `Home` page at `portal/app/(authenticated)/home/page.tsx`. Redirect `/` → `/home` in middleware.

### 1.7 Tests

- Snapshot / screenshot baseline of new desktop + mobile shell under each of the three profiles (`legal-za`, `accounting-za`, `consulting-za`) — 6 baselines.
- Playwright smoke: login → home → each visible nav item renders its page.
- Unit test: `nav-items` filter function against profile/module inputs.

---

## Section 2 — Portal Trust Ledger View (`legal-za`)

### 2.1 Read-Model Extension

New portal read-model tables (next available V-numbers; check latest before coding — Phase 67 will add V100+):

- `portal_trust_balance`
  - `customerId` (PK — one row per customer)
  - `matterId` (FK — one row per matter, so this is a composite PK: `customerId`, `matterId`)
  - `currentBalance` (DECIMAL, ZAR)
  - `lastTransactionAt` (TIMESTAMP)
  - `lastSyncedAt`
- `portal_trust_transaction`
  - `id` (UUID, mirror of firm-side `TrustTransaction.id`)
  - `customerId`, `matterId`
  - `transactionType` (enum string — `DEPOSIT`, `WITHDRAWAL`, `FEE_TRANSFER`, `PAYMENT`, `REFUND`, `INTEREST_POSTED`, etc.)
  - `amount`
  - `runningBalance`
  - `occurredAt`
  - `description` (sanitised firm-side description — see 2.4)
  - `reference` (firm-side reference number)
  - `lastSyncedAt`

Both tables live in the portal read-model schema, isolated from firm schemas.

### 2.2 Sync Handlers

New `TrustLedgerPortalSyncService` under `backend/.../customerbackend/service/` or `verticals/legal/trustaccounting/portal/`. Listens to:

- `TrustTransactionApprovedEvent` → upsert `portal_trust_transaction` + update `portal_trust_balance` for the matter.
- `InterestPostedEvent` → same.
- `ReconciliationCompletedEvent` (if any recalc of balance is needed) → refresh balance row.

Backfill: initial sync populates current balance + last N transactions (configurable, default 50) for each matter on module activation.

### 2.3 Portal REST API

New `PortalTrustController` under `backend/.../customerbackend/controller/` or `verticals/legal/trustaccounting/portal/`:

- `GET /api/portal/trust/summary` — balance + last transaction per matter for the authenticated portal contact's customer.
- `GET /api/portal/trust/matters/{matterId}/transactions?page=&size=&from=&to=` — paginated transaction list for one matter.
- `GET /api/portal/trust/matters/{matterId}/statement-documents` — list of previously generated Statement of Account `GeneratedDocument`s (Phase 67 artifacts) for this matter — links to existing portal document download.

Module-gated at controller level — returns 404 if tenant does not have `trust_accounting` module enabled.

### 2.4 Data Sanitisation

Trust transaction descriptions can contain firm-internal notes. Portal sync pipeline applies a **description sanitisation step**:
- Strip any description prefixed with `[internal]` (firm convention).
- Truncate to 140 characters.
- If empty after sanitisation, synthesise a description from transaction type + matter reference.

Sanitisation rules documented in an ADR; intended to avoid accidental internal-notes leakage.

### 2.5 Frontend

New pages:
- `portal/app/(authenticated)/trust/page.tsx` — matter-picker + current balance + last 5 transactions. If only one matter has trust activity, auto-select and show detail.
- `portal/app/(authenticated)/trust/[matterId]/page.tsx` — full balance + paginated transaction list + downloadable Statement of Account documents list.

Components:
- `portal/components/trust/balance-card.tsx`
- `portal/components/trust/transaction-list.tsx`
- `portal/components/trust/matter-selector.tsx`

Typed API client additions under `portal/lib/api/trust.ts`.

### 2.6 Tests

- ~4 backend integration tests: event → sync → read-model correctness; sanitisation; pagination; module-gate 404 for wrong profile.
- ~3 frontend tests: matter list, transaction list paging, empty state (no trust activity).

---

## Section 3 — Portal Retainer Usage View (`consulting-za` + `legal-za`)

### 3.1 Read-Model Extension

New portal read-model tables:

- `portal_retainer_summary`
  - `id` (mirror of firm-side `RetainerAgreement.id`)
  - `customerId`
  - `name` (agreement label)
  - `periodType` (enum — `MONTHLY`, `QUARTERLY`, `ANNUAL`, etc.)
  - `hoursAllotted`
  - `hoursConsumed` (current period)
  - `hoursRemaining`
  - `periodStart`, `periodEnd`
  - `rolloverHours` (if applicable)
  - `nextRenewalDate`
  - `status` (`ACTIVE`, `EXPIRED`, `PAUSED`)
- `portal_retainer_consumption_entry`
  - `id`, `retainerId`, `customerId`
  - `occurredAt` (date of the time entry)
  - `hours`
  - `description` (sanitised, same rules as 2.4)
  - `memberDisplayName` (first-name only or role-based display — decision below)
  - `lastSyncedAt`

### 3.2 Member Display Decision

Portal-facing representation of "who logged this time" is a legitimate privacy question. Default: **first name + role label** (e.g. "Alice (Attorney)"). Configurable via OrgSettings `portalRetainerMemberDisplay` enum with values `FULL_NAME`, `FIRST_NAME_ROLE` (default), `ROLE_ONLY`, `ANONYMISED`. Documented in ADR.

### 3.3 Sync Handlers

New `RetainerPortalSyncService`. Listens to:

- `RetainerAgreementCreated/Updated/PeriodClosed` (Phase 17 events) → upsert `portal_retainer_summary`.
- `TimeEntryLogged` when the entry's project belongs to a retainer-backed customer → upsert `portal_retainer_consumption_entry` + update `hoursConsumed`/`hoursRemaining` on the summary row.
- `RetainerPeriodRolloverEvent` → roll period boundaries, reset consumption, record rollover hours.

Backfill: initial sync populates current-period summary + current-period consumption entries.

### 3.4 Portal REST API

New `PortalRetainerController`:
- `GET /api/portal/retainers` — list active retainer summaries for authenticated contact's customer.
- `GET /api/portal/retainers/{id}/consumption?from=&to=` — consumption entries for a retainer within a date range; default = current period.

### 3.5 Frontend

New pages:
- `portal/app/(authenticated)/retainer/page.tsx` — retainer list + hour-bank card per active retainer (big number + progress bar + renewal date).
- `portal/app/(authenticated)/retainer/[id]/page.tsx` — consumption detail: period selector, entry list grouped by date, rollover display.

Components:
- `portal/components/retainer/hour-bank-card.tsx` (progress bar + remaining hours + renewal date)
- `portal/components/retainer/consumption-list.tsx`

### 3.6 Tests

- ~4 backend integration tests: time-entry sync, period close + rollover, member display config, multi-retainer aggregation.
- ~3 frontend tests: hour-bank rendering, period selector, empty period.

---

## Section 4 — Portal Deadline Visibility (`accounting-za` + `legal-za`)

### 4.1 Read-Model Extension

New portal read-model table:

- `portal_deadline_view`
  - `id` (mirror of firm-side deadline row — could be from accounting `FilingSchedule` or legal `CourtDate` / `PrescriptionTracker`)
  - `customerId`, `matterId` (nullable for customer-level deadlines)
  - `deadlineType` (polymorphic enum: `FILING`, `COURT_DATE`, `PRESCRIPTION`, `CUSTOM_DATE`)
  - `label`
  - `dueDate`
  - `status` (`UPCOMING`, `DUE_SOON`, `OVERDUE`, `COMPLETED`, `CANCELLED`)
  - `descriptionSanitised`
  - `sourceEntity` (`FILING_SCHEDULE` | `COURT_DATE` | `PRESCRIPTION_TRACKER` | `CUSTOM_FIELD_DATE`)
  - `lastSyncedAt`

Polymorphic: one table, many firm-side sources. Simpler for portal frontend than three tables + three controllers.

### 4.2 Sync Handlers

New `DeadlinePortalSyncService` listens to:

- Phase 51 filing schedule events (`FilingScheduleCreatedEvent`, `FilingStatusChangedEvent`) → upsert with `deadlineType=FILING`.
- Phase 55 court date events — when Phase 55 frontend ships; if not shipped by the time Phase 68 starts, the sync path exists but is dormant for absence of events. Builder should structure the handler generically.
- Phase 55 prescription tracker events — same.
- Phase 48 FIELD_DATE_APPROACHING trigger (Phase 48 360A) — for custom-field-date-based deadlines that have been marked as portal-visible. **Decision**: a custom-field can opt into portal visibility via a new `FieldDefinition.portalVisibleDeadline` flag. Only those sync to the portal deadline view.

### 4.3 Portal REST API

New `PortalDeadlineController`:
- `GET /api/portal/deadlines?from=&to=&status=` — list deadlines in a date range for the authenticated contact's customer. Default range: next 60 days.
- `GET /api/portal/deadlines/{id}` — detail (for the side-panel; no stand-alone detail page).

### 4.4 Frontend

New page:
- `portal/app/(authenticated)/deadlines/page.tsx` — upcoming list view (primary) + optional calendar view toggle. Filters: status, deadline type.

Components:
- `portal/components/deadlines/deadline-list.tsx` — grouped by week, visual urgency tier (colour by time-to-due).
- `portal/components/deadlines/deadline-detail-panel.tsx` — side panel with description + source entity reference (read-only).

### 4.5 Tests

- ~4 backend integration tests: each event source; polymorphic sync; field-flag gating for custom dates.
- ~3 frontend tests: list render, status filter, detail panel.

---

## Section 5 — Portal Notifications

### 5.1 Digest Scheduler

New `PortalDigestScheduler` under `backend/.../portal/notification/`. Runs weekly (default) via existing Spring scheduling infrastructure. For each portal contact with `digestEnabled=true`:

1. Query portal read-model for activity in the lookback window (7 days): new invoices, new proposals, new pending acceptances, new information requests, new trust transactions, new deadlines crossing into DUE_SOON, retainer period-close events.
2. Render digest email via new template `portal-weekly-digest.json` using Phase 24 rendering pipeline.
3. Send via `PortalEmailService.sendDigest(...)`.
4. Skip entirely if nothing to report (suppress empty digests).

Scheduler cadence configurable per-org via new `OrgSettings.portalDigestCadence` enum: `WEEKLY` (default), `BIWEEKLY`, `OFF`. Portal contacts retain per-contact override.

### 5.2 Per-Event Nudges

New notification channel `PortalEmailNotificationChannel` (sibling to existing `EmailNotificationChannel`) that fires on specific domain events for portal contacts only:

| Event | Template slug |
|---|---|
| `AcceptanceRequestCreatedEvent` (already emails; verify ride existing channel) | `portal-acceptance-pending` |
| `InformationRequestCreatedEvent` (already emails; verify) | `portal-info-request-new` |
| `ProposalSentEvent` (already emails; verify) | `portal-proposal-new` |
| `InvoiceIssuedEvent` (Phase 24 170A already emails; verify) | `portal-invoice-new` |
| `TrustTransactionApprovedEvent` **(new for portal)** | `portal-trust-activity` |
| `FilingDeadlineApproachingEvent` **(new for portal)** | `portal-deadline-approaching` |
| `RetainerPeriodClosedEvent` **(new for portal)** | `portal-retainer-period-closed` |

Events already wired to email in prior phases are left as-is — Phase 68 may re-use or reroute them, but MUST NOT introduce double-sends.

### 5.3 Portal Preferences Page

New page `portal/app/(authenticated)/settings/notifications/page.tsx`:

- Toggles: "Weekly digest", "Trust activity", "Retainer updates", "Deadline reminders", "Invoice / proposal / acceptance / info-request notifications" (grouped as "Action-required notifications").
- Unsubscribe-all link (reuses Phase 24 172 unsubscribe mechanism).
- Preferences persist to a new `portal_notification_preference` table (or column additions to `portal_contact` — builder decides based on shape).

Portal contact may also reach this page via unsubscribe-landing from digest emails.

### 5.4 Deliverability

- All emails go through Phase 24 delivery + log infrastructure — nothing new to build.
- Templates live in the same pack structure as existing email templates.
- Bounce handling reuses Phase 24 171 SendGrid webhook path (if configured for tenant).

### 5.5 Tests

- ~4 backend integration tests: digest composition (empty vs populated), preference gating, per-event nudge no double-send, unsubscribe.
- ~3 frontend tests: preferences save, unsubscribe-all, default-state rendering.

---

## Section 6 — Mobile Polish & Responsive Pass

A single phase investing once in mobile responsiveness across **all portal pages** — not just new ones.

### 6.1 Pages in Scope

Existing pages to audit + fix:
- `/login`
- `/home` (new — design mobile-first)
- `/projects`, `/projects/[id]`
- `/invoices`, `/invoices/[id]`, payment success / cancelled
- `/proposals`, `/proposals/[id]`
- `/requests` (existing Phase 34 portal information request pages — already under `portal/app/(authenticated)/`)
- `/acceptance` / `/accept/[token]` (existing Phase 28 portal pages)
- `/profile`
- `/trust` + `/trust/[matterId]` (new from Section 2)
- `/retainer` + `/retainer/[id]` (new from Section 3)
- `/deadlines` (new from Section 4)
- `/settings/notifications` (new from Section 5)

### 6.2 Checklist Per Page

- [ ] Layouts collapse gracefully below `md` — no horizontal scroll except where meaningful (e.g. transaction table may opt into sideways scroll).
- [ ] Tap targets ≥ 44px on primary actions.
- [ ] Tables either convert to card-list representation below `md` or retain a compact column set with horizontal scroll.
- [ ] Empty states have a dedicated mobile variant (illustrations scaled, CTA prominent).
- [ ] Loading skeletons match final content shape.
- [ ] Error states surface retry.
- [ ] Sticky bottom-action bars on mobile where a page has one primary action (e.g. "Pay invoice", "Accept document", "Submit request").

### 6.3 Screenshot Baselines

Playwright visual-regression baselines per page per breakpoint:
- `sm` (375×667) — iPhone SE baseline
- `md` (768×1024) — tablet baseline
- `lg` (1280×800) — desktop baseline

Stored under `e2e/screenshots/portal-v2/`.

### 6.4 Deliverables

- Visual regression pass on all pages listed in 6.1.
- Design-token audit: ensure no hardcoded widths that break below `md`.
- Empty / loading / error state pass: every page has all three.
- Accessibility sanity: keyboard-only navigation works on new nav shell (focus ring on rail items, skip-to-content link on top bar).

---

## Section 7 — Client-POV 90-Day Lifecycle QA Script + Screenshot Baselines

### 7.1 Script

New file `qa/testplan/demos/portal-client-90day-keycloak.md`. Written from the client's POV. One script that touches all three vertical variants (marked `[legal-za only]`, `[accounting-za only]`, `[consulting-za only]`, or `[all profiles]` per checkpoint).

Checkpoints (rough; builder may refine):

- **Day 0**: Client receives magic-link login email, logs in, lands on Home, reviews pending info requests.
- **Day 3**: Client uploads requested documents, submits info request.
- **Day 7**: Client receives first weekly digest email, clicks through to Deadlines page `[accounting-za / legal-za]`.
- **Day 14**: Firm sends proposal; client reviews proposal on portal, accepts.
- **Day 21**: First trust deposit recorded firm-side `[legal-za]`; client receives nudge, views balance on portal.
- **Day 30**: Client views hour-bank remaining on retainer `[consulting-za / legal-za]`; views current-period consumption.
- **Day 45**: Client pays first invoice via portal payment flow.
- **Day 60**: Client downloads generated document (from firm-generated Statement of Account `[legal-za]` or financial statement `[accounting-za]`).
- **Day 75**: Client receives deadline-approaching nudge; marks as read; downloads related document.
- **Day 85**: Client updates profile, changes digest cadence to biweekly.
- **Day 90**: Final digest; client reviews activity trail of the quarter.

### 7.2 Screenshot Baselines (curated)

Under `documentation/screenshots/portal/`:
- Desktop shell with rail — one shot per profile (3 shots)
- Mobile drawer open — one shot per profile (3 shots)
- Home page populated — one shot per profile (3 shots)
- Each new page: trust detail, retainer detail, deadlines list, settings/notifications, home (16 shots ± a few)
- Key moments: invoice payment, acceptance flow on mobile, digest email

### 7.3 Gap Report

Deliverable: `tasks/phase68-gap-report.md` documenting:
- UX rough edges encountered during run.
- Missing data or misaligned copy.
- Performance observations (load time, skeleton gaps).
- Proposals for Phase 68.5 polish slice or subsequent phases (audit trail Phase 69, disbursement portal view, multi-contact etc.).

### 7.4 Execution

Run via `/qa-cycle-kc qa/testplan/demos/portal-client-90day-keycloak.md` (or an adapted version of the existing skill pointed at portal routes + portal auth). Iterate to green before merging.

---

## Section 8 — Proposed Epic / Slice Breakdown

Rough shape for `/breakdown` — builder should sanity-check and resequence.

| Epic | Title | Scope | Slices |
|---|---|---|---|
| A | Portal session context + nav infra | Backend + Frontend | A1 (`/api/portal/session/context` + `use-portal-context` hook + `nav-items.ts`), A2 (sidebar + topbar components + layout.tsx rewrite + home page) |
| B | Portal trust ledger | Backend + Frontend | B1 (read-model tables + sync service + controller), B2 (frontend pages + components + API client) |
| C | Portal retainer usage | Backend + Frontend | C1 (read-model tables + sync service + controller + display config), C2 (frontend pages + components) |
| D | Portal deadline visibility | Backend + Frontend | D1 (read-model table + polymorphic sync service + controller + field flag), D2 (frontend page + components) |
| E | Portal notifications | Backend + Frontend | E1 (digest scheduler + per-event channel + templates + preferences table), E2 (preferences page + unsubscribe wiring) |
| F | Mobile polish & responsive pass | Frontend | F1 (existing pages reflow + empty/loading/error states), F2 (new-page responsive audit + screenshot baselines) |
| G | Client-POV 90-day QA script + screenshots + gap report | Process / E2E | G1 (script draft + test infrastructure), G2 (run + baselines + gap report) |

**~14 slices total.**

Epics B, C, D can execute in parallel after A1 lands. Epic E depends on at least one of B/C/D for events to subscribe to, but can start scaffolding ahead of them. Epic F is last before Epic G.

---

## Out of Scope

- Firm-side audit log viewer and portal activity-trail view — **Phase 69**.
- Multi-contact / per-client user roles on portal. `PortalContact` stays 1:1 with `Customer`.
- Two-way messaging thread / DM. Existing `PortalCommentController` is sufficient.
- Portal i18n / multi-language rendering. English (en-ZA) only.
- Portal PWA / offline / native-app shell.
- Disbursement portal view. Phase 67 ships disbursements firm-side; portal exposure is a later phase.
- Statement-of-Account scheduled auto-delivery to the portal.
- Command palette (⌘K) on portal. Intentionally client-first simple.
- Zoned / grouped sidebar (mirror of firm Phase 44 pattern). Portal uses flat rail.
- Per-portal-contact digest cadence overrides beyond a single "digest enabled" toggle — firm-side cadence setting is authoritative.
- Firm-side viewer of "what the portal showed this client" — future audit phase.
- Dark mode on portal. Light-only in this phase.
- Portal search across entities. Single-page list pages without global search.
- Multi-currency on portal views. ZAR only (inherited from firm).

---

## ADR Topics

- **ADR-??? — Portal nav shape: slim left rail, not mirror-of-firm-app.** Records rationale (client-first skim use, distinct visual identity from firm tool, scalability beyond three entries), what's parked (command palette, zoned grouping, dark mode), and the nav-item registry as the single source of truth for routes + profile/module gating.
- **ADR-??? — Portal vertical surfaces are read-model extensions, not new entities.** Re-affirms the pattern established in Phase 7 + 22. Documents why (tenant-isolation, firm authoritative, portal read-only), the sync handler pattern (domain event → upsert + sanitisation), and when a new portal-only entity is permissible (answer: it is not, in Phase 68).
- **ADR-??? — Description sanitisation for portal-visible domain text.** Strip `[internal]` prefix, truncate to 140 chars, fall back to synthesised text. Applied to trust transaction descriptions and retainer consumption descriptions. Extensible to other portal-visible text fields.
- **ADR-??? — Retainer member display on portal.** `OrgSettings.portalRetainerMemberDisplay` enum with default `FIRST_NAME_ROLE`. Records why not `FULL_NAME` by default (privacy + firm preference) and why not `ANONYMISED` by default (trust + transparency expectation).
- **ADR-??? — Polymorphic `portal_deadline_view` over per-source tables.** One row per deadline regardless of firm-side source. Trade-off: simpler portal code + one controller vs. slightly richer schema. Rule: polymorphic when portal UI is uniform regardless of source.
- **ADR-??? — Custom-field-date portal visibility opt-in via `FieldDefinition.portalVisibleDeadline`.** Most custom date fields are internal. Deliberate opt-in keeps portal deadline feed signal-over-noise. Surfaced on field-definition settings UI firm-side.
- **ADR-??? — Portal-visible events that already email.** Phase 68 must not double-send. Decision rule: if an event already sends an email in a prior phase (24, 28, 32, 34), Phase 68 does not re-wire it; the existing path covers portal contacts. Phase 68 adds new routes only for events not already wired (trust, deadline, retainer).

---

## Style & Boundaries

- Follow all conventions in `frontend/CLAUDE.md` and `backend/CLAUDE.md`. Portal follows `frontend/CLAUDE.md` except where its own `portal/` README or CLAUDE.md overrides.
- All new backend portal sync code lives alongside the relevant vertical (e.g. trust sync under `verticals/legal/trustaccounting/portal/`; retainer sync under `retainer/portal/`; deadline sync under `deadline/portal/` or similar). Controllers belong under `backend/.../customerbackend/controller/` (the established portal controller location) OR under the vertical package — builder decides based on tenant-isolation needs.
- All new portal controllers are module-gated. Reuse the existing `@PortalReadOnly` / module-guard pattern (see existing `PortalInvoiceController`, `PortalProposalController`). 404 when module not enabled for the authenticated contact's tenant.
- All new portal frontend pages wrap in the equivalent of `<ModuleGate>` at the layout/page level — if the client's tenant does not have the module enabled, the page is not reachable from the nav AND a direct URL hit returns a 404 / redirects to home.
- Portal controllers never touch firm-side entities directly — read from portal read-model only.
- All new domain-event → portal sync handlers live in their own class (`<Entity>PortalSyncService`) and attach via `@EventListener`. No inline sync logic in firm-side services.
- Migrations use the next available V-number sequence (check latest before coding).
- Tiptap / Thymeleaf: email templates continue the Phase 24 approach; new portal email templates are Thymeleaf.
- Variable placeholders in email templates follow Phase 24 resolver syntax.
- Branding continues via `use-branding` / `BrandingProvider`. If `use-portal-context` subsumes branding, preserve a back-compat alias to avoid mass-rename churn in existing pages.
- Mobile polish is not optional — every new page MUST ship with mobile, tablet, and desktop screenshots before merge.
- Multi-vertical coexistence: non-legal tenants must not see the trust nav entry; non-accounting/non-legal tenants must not see the deadlines nav entry; etc. Extend any existing portal multi-vertical coexistence test (or create one if absent) with assertions.
- No changes to firm-side `frontend/` in this phase **except**:
  - `FieldDefinition.portalVisibleDeadline` flag + its settings UI (small addition under custom-field settings).
  - `OrgSettings.portalRetainerMemberDisplay` + its settings UI (small addition under portal settings or retainer settings).
  - `OrgSettings.portalDigestCadence` + its settings UI.
  These three small firm-side additions are required and in-scope.
- Portal session context endpoint returns only what the portal needs — no accidental leakage of firm-side config unrelated to portal rendering.
