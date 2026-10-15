# Phase 87 — Legal-Native Shell: Matter/Client Vocabulary on the Wire, Attorney's-Week Navigation, Settings Consolidation

> **Status: READY for `/architecture` — after Phase 86 has merged.** Scope agreed in `/ideate` (2026-10-15). Depends on Phase 86 (subtraction + migration squash) having landed; it must not start on the pre-86 tree. Phase 84 (take-on) is built on the shell this phase produces.
>
> **Why this phase exists.** Kazi's UI is a generic practice-management skeleton — Projects, Customers, Tasks, Invoices, a six-zone sidebar, 49 settings pages — with a terminology layer that paints "Matter", "Client", "Fee Note" over the top at render time. The founder's judgement: *"we can design a better UI without having a generic structure that fits everything."* After Phase 86 there is one vertical. The generic structure is now pure cost: every new page translates in its head, the three calendars (calendar, court calendar, deadlines) exist because three verticals needed three things, and a managing partner evaluating a pilot sees an agency tool wearing legal labels.
>
> **Founder decisions (2026-10-15):** (1) **Rename the API, keep the tables.** REST paths, DTOs, JSON fields, TypeScript types and client modules become legal-native; JPA entities, table and column names stay `project`/`customer` indefinitely. (2) **Re-shell, don't rewrite.** New navigation, home, list pages and settings structure; reuse the matter detail (Phase 73) and client detail (Phase 77) pages, the trust pages, and the `@b2mash/ui` design system. (3) **Delete the terminology layer.** Legal words are written directly into copy; no runtime substitution remains. (4) **The MCP tool contract does not move** (`kazi-legal-za` plugin depends on it).
>
> **Scouting note (verified against current code, 2026-10-15 — do NOT re-derive):**
> - **Wire vocabulary today is mixed.** Backend has 142 `@RestController`s; ≈40 DTO records named `Project*`/`Customer*`. Paths are mostly `/api/projects/...`, `/api/customers/...` but legal-era endpoints already use `/api/matters/{projectId}/closure` and `/api/matters/{projectId}/statements` — the inconsistency is the symptom. Frontend+portal carry ≈924 `projectId`, ≈801 `customerId`, ≈48 `matterId` identifiers. TS types live in `frontend/lib/types/{project,customer,proposal,task,invoice,expense,...}.ts` (proposal.ts dies in 86); API clients in `frontend/lib/api/*.ts` (37 files pre-86). Portal paths: `/portal/projects`, `/portal/retainers` (dies in 86), `/portal/trust/summary`, `/portal/invoices`, etc.
> - **Terminology layer:** `packages/shared/src/terminology-map.ts` → `TERMINOLOGY_BASE["legal-za"]` maps Project→Matter, Task→Action Item, Customer→Client, Proposal→Engagement Letter, Time Entry→Time Recording, Rate Card→Tariff Schedule, Invoice→Fee Note, Expense→Disbursement, Budget→Fee Estimate, Retainer→Mandate, plus placeholders and `audit.tab`. Consumed by `frontend/lib/terminology.tsx` (`TerminologyProvider`/`useTerminology`/`t()`), `terminology-heading.tsx`, `terminology-text.tsx`, `withArticle` helper (LZKC-003), and the portal's `PORTAL_TERMINOLOGY`. Call-site census: `t("Project")` 24, `t("Customer")` 17, `t("project")` 16, `t("projects")` 13, `t("customer")` 8, `t("customers")` 7, `t("Invoice")` 5, ... — roughly 120 call sites, concentrated in headings, empty states and dialogs. `lib/messages/en/help.json` carries generic copy.
> - **Navigation:** `frontend/lib/nav-items.ts` `NAV_GROUPS` zones post-86 ≈ work (Dashboard, My Work, Calendar, Court Calendar) / projects (Projects) / clients (Customers, Compliance, Deadlines, Conflict Check, Adverse Parties) / finance (Invoices, Billing Runs, Collections, Profitability, Reports, Trust Accounting ×7 sub-items, Tariffs) / team (Team) / ai (AI Intake → `/projects/new`, AI Reviews). Rendered by `components/desktop-sidebar.tsx` → `nav-zone.tsx`; `mobile-sidebar.tsx`; command palette (`command-palette-dialog.tsx`) indexes the same items; `UTILITY_ITEMS` for settings/notifications. Phase 44 built the zone system — reuse it, re-author the content.
> - **Three calendars:** `/calendar` (month/list views, `calendar-actions.ts`), `/court-calendar`, `/deadlines` — separate pages, separate actions, overlapping purpose for a law firm's diary. Dashboard widgets (`components/dashboard/`): `todays-agenda`, `my-schedule-widget`, `deadline-widget`, `project-health-widget`, `information-requests-widget`, `incomplete-profiles-widget`, `recent-activity-widget`, `sensitive-events-widget`, `team-workload-widget`, `team-capacity-widget` + `team-utilization-widget` + `pipeline-summary-widget` (die in 86), KPI/chart primitives.
> - **Matter detail** (`/projects/[id]`, Phase 73 sidebar + grouped tabs; `lib/constants/project-tab-groups.ts`): Details/Fields · Overview · Work (Tasks, Documents, Correspondence, Generated Docs, Staffing) · Finance (Time, Expenses, Disbursements, Budget, Rates, Financials, Statements, Trust) · Client (Customers, Requests, Client Comments, Adverse Parties) · Schedule (Court Dates) · Activity (Activity, Audit). Note **Expenses and Disbursements are two tabs** for one legal concept; `expense` pkg is generic, `verticals/legal/disbursement` is the legal one — one must become the face of the other.
> - **Client detail** (`/customers/[id]`, Phase 77 header card + grouped tabs). Lifecycle PROSPECT→ACTIVE→OFFBOARDED, FICA checklists, information requests, correspondence, profitability, data export/anonymise.
> - **Settings** (49 pages pre-86; ≈43 after): general, features, roles, audit-log, data-protection(+requests), compliance, checklists(+CRUD), custom-fields, tags, templates(+CRUD), clauses, acceptance, request-templates(+CRUD), request-settings, project-naming, project-templates(+[id]), rates, tax, time-tracking, trust-accounting, collections, batch-billing, billing, email, notifications, integrations(+xero, +xero/sync-log, +mcp), ai(+history), automations(+executions; builder removed in 86), packs, (capacity, pipeline removed in 86). Layout from Phase 44 (sidebar settings nav).
> - **Audit `entityType` strings** are free lowercase strings (`"project"` ×10 sites, `"invoice"` ×22, `"task"`, `"trust_transaction"`, ...) — stored in `audit_events`; EntityTag/SavedView use UPPERCASE `"PROJECT"`. Memory `backend_audit_entitytype_casing` warns mixing them silently breaks the activity tab. Post-squash these are free to change but the catalogue-count tests pin them; recommendation below is to leave them.
> - **Portal** (`portal/`): nav `home, projects, trust, deadlines, invoices, requests, acceptance, activity` (+retainer, proposals removed in 86); own `nav-items.ts` with `labelKey` i18n; read-model in the global `portal` schema (squashed into global `V1` by 86).
> - **Design system:** `packages/ui` (`@b2mash/ui`, extracted PR #1421), Tailwind v4, Shadcn. `frontend/CLAUDE.md` and the `vercel:nextjs` / `vercel:shadcn` / `frontend-design` skills govern UI work (memory: UI questions go to those skills, not first principles).
> - **QA:** `qa/testplan/demos/legal-za-90day-keycloak.md`, `legal-za-full-lifecycle-keycloak.md`, Playwright `legal-lifecycle` + `legal-depth-ii` configs drive the current IA by URL and label. They will need amendment — **authorised by this phase** because the IA change *is* the product decision (CLAUDE.md §6).

## System Context

After Phase 86 Kazi is a legal-only product: matters, clients, time, fee notes, disbursements, trust accounting, court diary and deadlines, conflict checks, FICA/POPIA compliance, document templates with acceptance, Xero, collections, an AI skills layer and an MCP server. The data model is sound and stays. What remains generic is the **presentation contract**: the REST/JSON/TypeScript vocabulary, the navigation information architecture, the home page, the list pages, and the settings tree — all shaped by the need to serve three verticals from one skeleton, and all now translated at runtime by a terminology layer.

## Objective

Make Kazi read and navigate like software built for an attorney. Concretely: (1) the API, JSON and TypeScript speak Matter/Client natively with no runtime translation; (2) the sidebar is organised around an attorney's week — diary, matters, clients, fees, trust, firm — in five or six zones with no sub-menu deeper than one level; (3) one diary replaces three calendars; (4) the home page answers "what needs me today, what is unbilled, who owes us, is the trust position clean, what FICA is outstanding"; (5) settings shrink to a tree a bookkeeper can learn in an afternoon; (6) the portal uses the same vocabulary. Delivered without rewriting pages that already work: the matter and client detail pages, trust pages, fee-note pages, and the design system are reused.

## Constraints & Assumptions

- **Rename scope — exactly two nouns on the wire, everything else at the label.** `project`→`matter` and `customer`→`client` are renamed in REST paths (`/api/matters`, `/api/clients`, `/api/matters/{matterId}/...`, `/api/clients/{clientId}/...`), DTO record names and fields (`projectId`→`matterId`, `customerId`→`clientId`, `ProjectResponse`→`MatterResponse`, ...), JSON property names, TypeScript types, `lib/api` modules, server actions, route segments (`/org/[slug]/matters`, `/org/[slug]/clients`), and portal paths (`/portal/matters`). The remaining terms (fee note, disbursement, fee estimate, time recording, tariff) are **copy-level only**: `/api/invoices` stays `/api/invoices`, `Invoice` TS type stays, the page title says "Fee Notes". Rationale: the two nouns appear in every URL, every join and every type; the rest are leaf labels, and "invoice" is also the statutory VAT term.
- **Tasks are "Tasks".** Drop the `Task → Action Item` override; legal PM tools (Clio, LEAP, AJS) all say Tasks. Retainer/Mandate wording attaches to the engagement-letter acceptance flow where it belongs.
- **JPA entities, repositories, services, table and column names are NOT renamed.** `Project`, `Customer`, `project_id`, `customer_id` remain. DTO mapping is the seam. If `/architecture` finds a DTO that is the entity itself (leaking), introduce a DTO; do not rename the entity.
- **IDE-assisted rename.** Use IntelliJ `rename_refactoring` for Java DTO/record/field renames (reference-aware, one symbol per call) and validate with IDE build + full verify; wire-level renames (paths, `@JsonProperty`, TS types) are explicit edits with a contract test. Each rename batch is its own PR with the full backend verify. Never rely on text replace for Java symbols.
- **Audit `entityType` strings stay as they are** (`"project"`, `"customer"`) — they are internal, pinned by catalogue tests, and changing them buys nothing visible. Document this in an ADR so nobody "fixes" it later. EntityTag/SavedView `"PROJECT"`/`"CUSTOMER"` likewise.
- **MCP tools, schemas and the `kazi-legal-za` plugin contract are untouched.** Add a contract test if none exists that pins tool names and input schemas.
- **Terminology layer deleted entirely:** `terminology-map.ts`, `terminology.tsx`, `terminology-heading.tsx`, `terminology-text.tsx`, `withArticle`, `PORTAL_TERMINOLOGY`, every `t("...")` call site rewritten to the literal legal word, `TerminologyProvider` removed from layouts, portal equivalents removed, `verticalProfile` prop threads removed where they only fed terminology. Tests that assert "renders Engagements for accounting-za" die in 86; tests asserting legal words become plain assertions.
- **Navigation IA (target; final labels at `/architecture`, informed by the `frontend-design` skill):**
  - **Today** — Home (dashboard), My Work, Diary.
  - **Matters** — Matters, New matter (intake), Conflict check, Adverse parties.
  - **Clients** — Clients, FICA & compliance, Information requests.
  - **Fees** — Unbilled work, Fee notes, Fee runs, Debtors & collections, Disbursements, Tariffs, Profitability.
  - **Trust** — Overview, Transactions, Client ledgers, Reconciliation, Interest, Investments, Trust reports.
  - **Firm** — Team, Reports, Audit trail, Settings.
  AI has no zone: AI intake lives on "New matter", AI reviews live on the matter and under Fees/Matters as appropriate, the MCP connection lives in Settings. Command palette and mobile sidebar re-index from the same source. At most one nested level; Trust is the only zone allowed seven items.
- **One Diary.** Merge `/calendar`, `/court-calendar`, `/deadlines` into `/diary` with a unified event model on the client (court dates, deadlines, tasks due, matter due dates, schedule actions) and filters by type, fee earner and matter. Backend endpoints may stay separate (three reads behind one page) or gain one aggregated read — `/architecture` decides; the user sees one page. Dashboard "today's agenda" is the same model, one day.
- **Home page** is legal-native: today's diary, unbilled WIP (hours and value, by fee earner), debtors aging summary, trust position (bank vs client-ledger total, last reconciliation date and status), FICA outstanding, my open tasks, recent activity. Reuse widget primitives; delete the agency widgets that survived 86 (`team-workload` stays only if staffing is a legal need — founder lean: drop). Role-aware: fee earner vs admin/bookkeeper vs managing partner see different top rows; reuse the existing capability model, no new roles.
- **Matters list** gets legal columns and filters: reference, client, work type, responsible fee earner, status, opened date, unbilled value, trust balance, next diary date. Saved views (Phase 11) remain the mechanism; ship 3–4 default views (My matters, Open matters, Awaiting fee note, Dormant). Same treatment for the Clients list (FICA status, outstanding fee notes, trust balance).
- **Matter detail (Phase 73) and Client detail (Phase 77) are reused**, with tab groups re-labelled, the Expenses/Disbursements split resolved (one "Disbursements" tab; decide at `/architecture` whether the legal `disbursement` entity fully replaces generic `expense` on matters or whether `expense` is hidden), and tabs for removed features gone.
- **Settings consolidation target** (names at `/architecture`): **Firm** (general, team & roles, audit trail, data protection, features) · **Matters** (matter templates, naming, custom fields, tags, checklists, request templates) · **Documents** (templates, clauses, acceptance, email) · **Fees & Trust** (tariffs/rates, tax, time recording, fee runs, collections, trust accounting) · **Connections** (Xero, MCP, AI, notifications, packs). Collapse single-purpose pages into tabs of their group where that removes a page without hiding a control; target ≤ 25 routes from ≈43. Keep every control; lose pages.
- **Portal:** vocabulary and nav only (Matters, Fee notes, Trust statement, Diary dates, Documents & requests, Engagement letters, Activity). No visual redesign — Phase 68 just did one.
- **Design discipline:** load `frontend-design` and `vercel:shadcn` skills before any shell work; keep `@b2mash/ui` as the only primitive source; density tuned for practitioners (tables over cards for lists); no new colour tokens without a reason; a11y pass as per Phase 36 standards. This is an information-architecture change with visual polish, not a brand refresh.
- **Test strategy.** Each rename PR: full `./mvnw verify`; frontend + portal full vitest + `format:check` + `build`. IA PRs: Playwright legal configs updated in the same PR as the IA change they break (never merged red, never skipped). Capstone: the full legal-za 90-day lifecycle re-run through the new shell on the Keycloak stack, observed, with amended scenarios committed; the amendments are the product decision this phase records.

---

## Section 1 — Wire Rename: Backend DTOs, Paths, JSON

- Enumerate every controller path, DTO record and JSON field carrying `project`/`customer` (≈40 records, ≈60 paths incl. nested resources: tasks, documents, time entries, expenses, members, budget, health, activity, comments, correspondence, information requests, profitability, closure, statements, trust client ledgers).
- Rename in batches by bounded context (matters core → clients core → finance → legal verticals → portal API → MCP-adjacent read-models untouched). IDE rename for Java symbols; explicit path edits; `@JsonProperty` only where a record field cannot be renamed (prefer renaming the field).
- Contract test: an OpenAPI/route snapshot (or a simple test enumerating `RequestMappingHandlerMapping`) asserting no surviving `/api/projects` or `/api/customers` path and no `projectId`/`customerId` JSON field outside the DB-facing layer.
- Gateway: route allow-lists / CSRF exclusions updated if path-based.

## Section 2 — Wire Rename: Frontend & Portal Types, Clients, Routes

- `lib/types/{project,customer}.ts` → `{matter,client}.ts`; `lib/api/*` renamed and re-pathed; server actions; route segments `/matters`, `/clients` with permanent redirects from `/projects`, `/customers` for bookmarks; `recent-items-provider`, command palette, breadcrumbs, setup-status links updated.
- Portal: `/portal/matters`, types, nav.
- Delete the terminology layer (Section 3 can be the same slice group).

## Section 3 — Terminology Layer Removal

- Delete the map, provider, hook, components; rewrite ≈120 call sites with legal copy; remove `verticalProfile` plumbing that only served it; update `help.json` copy; portal equivalent.

## Section 4 — Navigation, Diary & Home

- New `NAV_GROUPS` per the IA target; mobile + palette re-index; `/diary` page merging the three calendars; legal-native home with role-aware rows; delete surviving agency widgets.

## Section 5 — Lists, Detail Tab Groups, Disbursements Resolution

- Matters and Clients lists with legal columns, filters and default saved views; tab-group relabel on matter and client detail; one Disbursements tab.

## Section 6 — Settings Consolidation

- Re-group into the five families; collapse pages into tabs; ≤ 25 routes; redirects from old settings URLs; settings nav and command palette updated.

## Section 7 — Portal Vocabulary & Nav

- Labels, nav, route segments, types; no visual changes.

## Section 8 — QA Capstone & Scenario Amendments

- Amend the two legal-za scenario files and both Playwright configs for the new IA (authorised here); run the 90-day lifecycle end-to-end on the Keycloak stack; record evidence paths; closure report with route-count and settings-page-count deltas and a screenshot set of the new shell for the exec deck (`marketing/decks/exec-deck.html` references screens — refresh them).

## Out of Scope

- Renaming JPA entities, repositories, services, tables, columns, audit `entityType` strings, EntityTag/SavedView entity-type strings.
- Any change to MCP tool names/schemas or the `kazi-legal-za` plugin.
- Portal visual redesign; marketing site (`/legal` is Phase 85); brand refresh; new colour system.
- New features (take-on is Phase 84; anything a design partner asks for is its own phase).
- Backend behaviour changes beyond DTO/path renames and (optionally) one aggregated diary read.
- Removing the gateway or changing auth.

## ADR Topics to Address

1. Wire-rename scope: two nouns on the wire, labels for the rest; why entities/tables stay; why `invoice` stays on the wire.
2. Terminology layer removal and the single-vocabulary rule for future code (lint rule banning `t(`? a `frontend/CLAUDE.md` rule at minimum).
3. Navigation IA for a law firm (zones, depth limit, where AI lives) and the one-Diary decision (client-side merge vs aggregated endpoint).
4. Disbursements vs expenses on matters: which entity is the face, what happens to the other.
5. Settings taxonomy (five families) and the "controls stay, pages go" rule; redirect policy for old URLs.
6. Audit/EntityTag string stability under a renamed wire.
7. MCP contract pinning test.
8. Scenario-amendment authorisation scope for the QA capstone.

## Style & Boundaries

- One bounded context per rename PR; one IA surface per IA PR; no mixing rename and IA in a PR.
- CLAUDE.md quality gates unchanged. Prettier `format:check` is part of green.
- Reuse Phase 73/77 page structure; if a detail page needs more than tab relabelling, stop and re-spec.
- Every deleted route gets a redirect for one release; every renamed path appears in the contract test.
- UI decisions go through the `frontend-design` / `vercel:*` skills, not first principles.
- Do not touch `mcp`, `integration/ai` skills, trust-accounting domain logic, or any migration (the squashed baseline is `V1`; this phase adds none).

## Next step

After Phase 86 merges: run `/architecture requirements/claude-code-prompt-phase87.md`, then `/breakdown 87`. Phase 84 (take-on) is re-based on the Phase 87 shell before its own `/architecture` run (its spec's Settings → Take-On page lands under the **Firm** or **Matters** settings family — decide then).
