# ADR-339: Navigation Built Around the Attorney's Week — Six Workflow Zones, One Nesting Level, No AI Zone — and One Diary Served by a Single Aggregated `GET /api/diary` Read

**Status**: Accepted

**Context**:

Phase 44 built the zone sidebar ([ADR-170](ADR-170-sidebar-zone-structure.md): fixed zones with capability-based visibility; [ADR-171](ADR-171-command-palette-scope.md): the palette indexes `NAV_GROUPS` + `UTILITY_ITEMS` + `SETTINGS_ITEMS`). Post-86 `frontend/lib/nav-items.ts` (475 lines) still carries the horizontal shape: **6 zones / 25 items** — `work` (Dashboard, My Work, Calendar, Court Calendar) · `projects` (Projects) · `clients` (Customers, Compliance, Conflict Check, Adverse Parties) · `finance` (Invoices, Billing Runs, Collections, Profitability, Reports, seven flat Trust items, Tariffs) · `team` (Team) · `ai` (AI Intake → `/projects/new`, AI Reviews) (`.arch-context-frontend.md` §2). Rendering is `components/desktop-sidebar.tsx` → `nav-zone.tsx` (filters by `hasCapability` and `isModuleEnabled`; one level deep, no sub-item type), `mobile-sidebar.tsx` (same data in a Sheet) and `command-palette-dialog.tsx`, which filters pages by capability only — **`requiredModule` is ignored for pages** (`command-palette-dialog.tsx:31`; settings items are module-filtered at `:38`), a known inconsistency with the sidebar.

The diary is two pages with no shared model (`.arch-context.md` §4; `/deadlines` died in 86): `/calendar` (`calendar-page-client.tsx`, `GET /api/calendar?from&to[&projectId&type&assigneeId&overdue]` → `CalendarItemDto{… itemType TASK|PROJECT, dueDate, assigneeId, projectId}`) and `/court-calendar` (`court-calendar-client.tsx`, 482 lines, `GET /api/court-dates?dateFrom&dateTo&dateType&status&customerId&projectId` paginated, `GET /api/court-dates/upcoming` → court dates + prescription warnings, `GET /api/prescription-trackers`). The only backend union read is per matter: `GET /api/projects/{id}/upcoming-deadlines` → `ProjectUpcomingDeadline(type COURT|REGULATORY, date, description, status)`. `components/dashboard/todays-agenda.tsx` is presentational (props `MyWorkTaskItem[]`, `MyWorkTimeEntryItem[]`, `PersonalDeadline[]` — tasks only) and mounted on `/my-work` only. **Court dates carry no assignee or fee-earner field**; tasks carry `assigneeId`; a matter's responsible fee earner is its `ProjectMember` with role LEAD. Roles are only `owner`/`admin`/`member`; capabilities come from `GET /api/me/capabilities` (§9). Schedule/recurring actions are gone (Epic 600); `lib/recurrence.ts` survives with no confirmed consumer.

The requirements set the target IA ("Today — Matters — Clients — Fees — Trust — Firm", AI has no zone, at most one nested level, Trust the only seven-item zone) and left "three reads behind one page vs one aggregated read" to this ADR. Phase 87 adds no migration.

**Options Considered**:

### A. Information architecture

1. **Keep the six generic zones**, relabel items (Projects→Matters, Customers→Clients, Invoices→Fee notes).
   - Pros:
     - Smallest change to `NAV_GROUPS`.
     - `nav-items.test.ts` and the Playwright sidebar spec need label edits only.
   - Cons:
     - The zones are organised by object type (`projects`, `clients`, `finance`), not by what an attorney does.
     - Trust is buried as seven flat siblings inside Finance.
     - AI has a zone although it is two links.
     - A managing partner evaluating a pilot still sees "an agency tool wearing legal labels" — the problem statement of the phase.
2. **Role-based zones** — Attorney / Bookkeeper / Managing partner top-level groups.
   - Pros:
     - Each persona sees "their" area first.
   - Cons:
     - Kazi has no legal role names in code or `legal-za.json`.
     - Roles are `owner`/`admin`/`member` with capability sets, and adding roles is out of scope.
     - A sole practitioner is all three personas.
     - The same page (Fee notes) would live in two groups or in none.
3. **Workflow zones around the attorney's week (CHOSEN)** — **Today** (Home, My Work, Diary) · **Matters** (Matters, New matter, Conflict check, Adverse parties, AI reviews) · **Clients** (Clients, FICA & compliance, Information requests) · **Fees** (Unbilled work, Fee notes, Fee runs, Debtors, Disbursements, Tariffs) · **Trust** (Overview, Transactions, Client ledgers, Reconciliation, Interest, Investments, Trust reports) · **Firm** (Team, Reports, Audit trail, Settings). One nesting level; no AI zone — AI intake is "New matter" (`/matters/new`), the MCP connection is a settings page, **AI reviews** (`/ai/reviews`, `AI_REVIEW`) is the fifth Matters item so the gate review queue stays reachable; **Profitability sits under Firm → Reports** (`/reports/profitability`, the single entry; `/profitability` redirects there), so the width rule holds — ≤ 6 items per zone, Trust the only seven-item zone.
   - Pros:
     - Reads as the attorney's day: what needs me today → my matters → my clients → what I bill → the trust position → the firm. Trust becomes a first-class zone, which is the one thing a South African firm is audited on.
     - Reuses `NAV_GROUPS` / `nav-zone.tsx` / mobile sidebar / palette unchanged in mechanics.
     - Only content is re-authored, so the Phase 44 tests keep their shape.
     - Capability and module gates survive per item (`VIEW_TRUST` + `trust_accounting` on the Trust zone, `court_calendar` on Diary's court events, `conflict_check`, `lssa_tariff`, `bulk_billing` on Fee runs).
   - Cons:
     - Fees is held at six items only by placing Profitability under Firm → Reports; a future Fees item must displace one or justify a second seven-item zone.
     - Every e2e nav assertion ("Matters, Clients, Fee Notes … Trust Accounting, Court Calendar, Conflict Check, Tariffs" in steps 0.23/0.24/0.59/90.3 of the 90-day scenario) changes ([ADR-344](ADR-344-qa-scenario-amendment-authorisation-phase87.md)).
     - `breadcrumbs.tsx` gains new segments (`diary`, `matters`, `clients`) and loses `calendar`/`court-calendar`.
     - `breadcrumbs-routes.test.ts` pins this against the filesystem.
4. **Flat list, no zones** — ≈30 items in one column.
   - Pros:
     - No taxonomy to argue about; nothing hidden behind a group header.
   - Cons:
     - Thirty items is unscannable on a laptop and impossible in the mobile Sheet.
     - ADR-170 rejected it for 25 items.

### B. One Diary

1. **Client-side merge of the existing reads** — the `/diary` page calls `/api/calendar`, `/api/court-dates`, `/api/court-dates/upcoming` and `/api/prescription-trackers` and normalises in TypeScript.
   - Pros:
     - No backend change; existing filters (`projectId`, `assigneeId`, `customerId`, `dateType`) already exist per read.
   - Cons:
     - Four round trips per view and three pagination models (`/api/court-dates` is a `Page`, `/api/calendar` is a list, `/upcoming` is a window).
     - The "fee earner" filter cannot be applied to court dates without a fifth read of matter members per matter.
     - `todays-agenda` and the home page would need the same merge code or a second copy.
     - The normalisation logic lives in the one place that cannot be unit-tested against the database.
2. **One aggregated read-model endpoint, `GET /api/diary` (CHOSEN)** — `GET /api/diary?from=&to=&types=&feeEarnerId=&matterId=` → `DiaryEventResponse[]` (`id, kind TASK_DUE|COURT_DATE|PRESCRIPTION|MATTER_DUE, date, time?, title, subtype?, matterId, matterName, matterReference?, clientId?, clientName?, feeEarnerId?, feeEarnerName?, status, overdue, href`), composed server-side in a new `diary/` package (`DiaryService` + `DiaryController`) from (a) task due dates, (b) matter due/target dates, (c) court dates plus prescription warnings from the `/upcoming` logic, (d) schedule/recurring items only if a live source is confirmed at build time (none is known). The service reads the existing repositories/services and owns no table; existing court-date and prescription CRUD endpoints stay for writes. `feeEarnerId` is nullable; the fee-earner filter matches tasks by assignee and court dates / matter due dates by the matter's LEAD member.
   - Pros:
     - One round trip, one DTO, one place for access rules (project-access via `ProjectAccessService`, `court_calendar` module gate for COURT_DATE/PRESCRIPTION types) and one unit-testable composer.
     - `todays-agenda` consumes the same DTO for one day.
     - The home page's "today" row is a `from=to=today` call — no second merge.
     - No migration: a read-only composition over `tasks`, `projects`, `court_dates`, `prescription_trackers`, `project_members`.
   - Cons:
     - A new controller + service + DTO + REST-Docs test in a phase that is otherwise rename/IA.
     - Batched queries are needed to avoid N+1 over matter leads (same pattern as `MatterListRow`).
     - Backend behaviour beyond "DTO/path renames" — permitted by the requirements' "(optionally) one aggregated diary read", but it is the one new read-model of the phase and must stay read-only.
3. **A materialised diary table** fed by domain events.
   - Pros:
     - Fast, sortable, paginated by the database.
   - Cons:
     - Requires a migration (`V4`) — forbidden in Phase 87 — plus event handlers for task, matter, court-date and prescription changes and a resync job.
     - A projection for a few hundred rows per firm is premature.
4. **Keep separate pages behind a shared `DiaryList` component**.
   - Pros:
     - Lowest risk to the 482-line court-calendar client.
   - Cons:
     - The user still sees two pages for one diary.
     - The requirements say "one page".
     - The fee-earner filter still cannot span both.

**Decision**: A3 + B2 — re-author `NAV_GROUPS` into the six workflow zones above with one nesting level and no AI zone, and serve `/diary` (and the home-page "today" row and `todays-agenda`) from a single new read-only `GET /api/diary` composed server-side in a `diary/` package; `/calendar` and `/court-calendar` are deleted and permanently redirected to `/diary`.

**Rationale**:

1. **The sidebar is the product's first sentence.** The founder's judgement is that a generic structure fitting everything fits nobody; zones named for an attorney's week say "law firm" before a single label does. Trust as a zone matches what the Legal Practice Council audits and what the design-partner GTM sells.
2. **Reuse Phase 44, re-author content.** `NavGroup`/`NavItem`, `nav-zone.tsx`, the mobile Sheet and the palette already do capability and module filtering; the only mechanics change is making the palette honour `requiredModule` for pages (one filter clause at `command-palette-dialog.tsx:31`), in scope because the palette is re-indexed anyway.
3. **One read because three clients would need the same merge.** `/diary`, the home page and `todays-agenda` all need "events in a window, by fee earner, by matter". Composing once in `DiaryService` gives one access-rule site (project access + `court_calendar` gate), one DTO (`DiaryEventResponse`) and one `@SpringBootTest`; composing in TypeScript gives three copies and no database-backed test.
4. **No assignee on court dates is a data fact, not a reason for a migration.** Falling back to the matter's LEAD member answers "which court dates are mine" for the common case and leaves `feeEarnerId` nullable for the rest; adding `court_dates.fee_earner_id` is a `V4` change for Phase 84+ if a design partner asks.
5. **Capabilities, not roles, drive the home rows.** `GET /api/me/capabilities` already exposes `VIEW_TRUST`, `FINANCIAL_VISIBILITY`, `INVOICING` and `isAdmin`; the home page keys its top row off those, keeps `KpiResponse.withFinancialsRedacted()` semantics and adds no role names.

**Consequences**:

- Positive:
  - `NAV_GROUPS` becomes six zones.
  - `UTILITY_ITEMS` shrinks to Notifications (Settings moves into Firm).
  - `command-palette-dialog.tsx` filters pages by `requiredModule`.
  - `mobile-sidebar.tsx` and the palette re-index from the same source.
  - `diary/DiaryController` + `DiaryService` + `DiaryEventResponse` land with REST-Docs + a composition test.
  - `/api/court-dates/**` and `/api/prescription-trackers/**` stay for writes.
  - `GET /api/calendar` and `calendar/CalendarService` are **retained for one release** with renamed fields, uncalled by the frontend after S6b, and deleted in the first phase after 87 (architecture §12.4.6, §12.14 #2).
  - `frontend/next.config.ts` gains permanent redirects `/org/:slug/calendar` → `/org/:slug/diary` and `/org/:slug/court-calendar` → `/org/:slug/diary`.
  - `calendar/**` and `court-calendar/**` page files are deleted.
  - The court-date dialogs under `components/legal/` are reused by `/diary` and the matter's Court Dates tab.
  - Home: `team-workload-widget` and `GET /api/dashboard/team-workload` are dropped (in S7, with the widget — S1 is a pure rename).
  - Delivered as two slices: **S6a** (nav IA, palette incl. the `requiredModule` fix, mobile footer, Unbilled work page, nav redirects; Firm → Settings links to the current settings routes and Today carries interim Calendar/Court calendar items) and **S6b** (Diary read + Diary page + `/calendar`/`/court-calendar` redirects).
  - New reads `GET /api/dashboard/unbilled-wip` (hours + value by fee earner, capability-gated), `GET /api/dashboard/fica-outstanding`, `GET /api/dashboard/trust-position` (three trust reads composed) feed legal-native widgets.
  - Debtors come from `GET /api/collections/debtors`.
  - `GET /api/reports/utilization` is reused for per-fee-earner value.
- Negative:
  - Profitability is one click further away (Firm → Reports) than a Fees item would be; the price of keeping Trust the only seven-item zone.
  - Prescription warnings lose their own tab.
  - They become `kind=PRESCRIPTION` events plus the existing prescription dialogs — a visible change the scenario amendments must cover.
  - Court dates with no LEAD member on the matter do not match any fee-earner filter (nullable `feeEarnerId`).
  - They still appear in the unfiltered diary.
  - `breadcrumbs.tsx` `SEGMENT_LABELS` / `NON_ROUTABLE_PREFIXES` and `breadcrumbs-routes.test.ts` change in the same PR.
  - `lib/__tests__/nav-items.test.ts` and the sidebar/palette tests are rewritten for the new content.
  - Matters carries five items (AI reviews is the fifth) so `ai/reviews` stays reachable; it is the only zone wider than the requirements' sketch besides Trust.
- Follow-ups:
  - `court_dates.fee_earner_id` (migration, Phase 84+) if partners want court dates assigned independently of the matter lead.
  - Diary pagination/virtualisation if a firm exceeds a few hundred events per month — `DiaryEventResponse[]` is unpaged by design for a 1–3 month window.
- Related:
  - [ADR-170](ADR-170-sidebar-zone-structure.md) / [ADR-171](ADR-171-command-palette-scope.md) (mechanics reused; content superseded), [ADR-252](ADR-252-portal-slim-left-rail-nav.md) (portal nav stays a slim rail — vocabulary only), [ADR-287](ADR-287-grouped-tabs-dense-navigation.md) (matter tab groups relabelled, not redesigned), [ADR-302](ADR-302-mcp-read-scope-model.md) (the same project-access regime `DiaryService` applies), [ADR-332](ADR-332-single-vertical-profile-mechanism-retained.md) (module gates still govern Diary's court events), [ADR-338](ADR-338-terminology-layer-removal-single-vocabulary.md) (zone labels are literal), [ADR-341](ADR-341-settings-taxonomy-five-families-controls-stay-pages-go.md) (Firm → Settings).
