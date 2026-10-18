# ADR-337: Wire Rename — Exactly Two Nouns (`project→matter`, `customer→client`) on Paths, DTOs, JSON and TypeScript; JPA Entities, Tables and the `mcp/` Boundary Untouched

**Status**: Accepted

**Context**:

After Phase 86 Kazi serves one vertical, but its presentation contract is still the horizontal one. The backend has **121 `@RestController`s**, of which **32** carry `projects`/`customers` in their paths (`.arch-context.md` §3.1): `project/ProjectController` at `/api/projects`, `customer/CustomerController` at `/api/customers`, and nested resources for tasks, documents, time entries, expenses, members, budget, health, activity, comments, correspondence, information requests, checklists, anonymisation, data export and profitability. **45** DTO records are named `Project*`/`Customer*` (`ProjectResponse` nested in `ProjectController.java:238`, `CustomerResponse` in `customer/dto/CustomerDtos.java:81`, …); the DTO/controller bucket holds **251 `projectId` + 192 `customerId`** occurrences against 1,537 + 1,429 in entities, repositories and services (§3.3). JSON property names are record component names — no `@JsonNaming`, `@JsonProperty` in only two Anthropic files — so renaming a component renames the field. The frontend has **74** literal `/api/projects` and **57** `/api/customers` strings, `projectId` ×1,212 and `customerId` ×936, `lib/types/{project,customer}.ts`, and **463 hard-coded `/projects|/customers` hrefs in 163 files** (`.arch-context-frontend.md`). The vocabulary is already mixed: `/api/matters/{projectId}/closure`, `/api/matters/{projectId}/statements` and `/portal/trust/matters/{matterId}` are legal-native islands beside `/api/projects/{projectId}/tasks`.

The founder decision (`requirements/claude-code-prompt-phase87.md`, "Rename the API, keep the tables") fixes the scope: REST paths, DTO record names and fields, JSON property names, TypeScript types, `lib/api` modules, server actions, route segments and portal paths become legal-native; JPA entities, repositories, services, table and column names stay `project`/`customer` indefinitely; `invoice`, `expense`, `timeEntry`, `rateCard`, `budget` keep their wire names and change only at the label. The `mcp/` package and the `kazi-legal-za` plugin contract do not move ([ADR-333](ADR-333-mcp-sole-conversational-ai-surface.md)). Phase 87 adds no migration (tenant high-water `V3`; `V4` is reserved for whoever needs it next). The entity-leak check found none: no controller returns `Project`/`Customer` directly; `ProjectResponse.from(Project …)` and the `CustomerResponse` factory are the seam (§3.2).

**Options Considered**:

1. **Rename everything, entities and tables included** — `Project`→`Matter`, `Customer`→`Client`, `projects`→`matters`, `project_id`→`matter_id`, plus every repository, service, event and audit string.
   - Pros:
     - One vocabulary end to end; no "the table is called X but the API says Y" explanation for future readers.
     - The 1,788/1,621 identifier counts drop to zero.
     - No contract test needed to hold a boundary.
   - Cons:
     - A schema rename is a migration, and Phase 87 is migration-free.
     - The baseline becomes immutable the day the first tenant lands (`backend/db/BASELINE.md`, [ADR-331](ADR-331-migration-squash-at-zero-tenants.md)).
     - Touches 74 `Project*`/`Customer*` non-wire files and 283 further files, `ReportRepository` native SQL, `ViewFilterHelper`'s entity→table map and the `audit_events.details->>'project_id'` expression index ([ADR-035](ADR-035-activity-feed-direct-audit-query.md)).
     - Dozens of 751 s full verifies for no user-visible gain beyond option 2.
2. **Two nouns on the wire; entities, tables and the `mcp/` boundary stay (CHOSEN)** — `project→matter` and `customer→client` on paths (`/api/matters`, `/api/clients`, `/api/matters/{matterId}/…`, `/portal/matters`), record names and components (`MatterResponse`, `matterId`, `clientId`, `clientName`), TS types (`lib/types/{matter,client}.ts`), `lib/api` modules, route segments (`/org/[slug]/matters`, `/clients`) and the BFF handler `app/api/customers/route.ts`. Already-legal paths keep their path and rename only the variable (`/api/matters/{matterId}/closure`). The rename stops at `mcp/`.
   - Pros:
     - The two nouns are the ones in every URL, every join field and every type — renaming them is what makes the API read as legal.
     - The seam is the existing static factory; services and repositories are untouched; no migration; no `@JsonProperty` anywhere.
     - Gateway (`Path=/api/**`) and `SecurityConfig` (`/api/**`, `/portal/**`) match no noun literal and need no edit (§3.5).
     - `CustomerAuthFilter` checks `startsWith("/portal/")` only.
   - Cons:
     - A permanent `Matter`-on-the-wire / `Project`-in-the-table split that every backend contributor must learn.
     - Still ≈440 identifier edits across 88 Java files plus the 163-file frontend blast radius.
     - `trust-accounting/client-ledgers/[customerId]` is a route segment as well as an identifier.
     - "client" collides with OAuth `clientId` in `security/` (14 non-OAuth `clientId` today) — the contract test must exclude the OAuth package.
3. **Rename nothing on the wire; labels only at render** (the status quo with the terminology map replaced by literal copy).
   - Pros:
     - Zero backend change; no redirects; no contract test.
     - The Playwright specs' 133 `page.goto()` literals keep working.
   - Cons:
     - Contradicts the founder decision.
     - A design partner's developer reading `/api/projects/{projectId}/customers` sees an agency tool.
     - `/api/matters/{projectId}/closure`-style inconsistency stays forever.
     - Every new page keeps translating in its head — the exact cost the phase exists to remove.
4. **Rename every legal term on the wire** — also `invoice→feeNote`, `expense→disbursement`, `timeEntry→timeRecording`, `rateCard→tariff`, `budget→feeEstimate`.
   - Pros:
     - The most legal-native contract possible.
     - No label layer needed for any term.
   - Cons:
     - `invoice` is the statutory South African VAT term ("tax invoice" under the VAT Act) and the Xero adapter's object (`integration/accounting/sync/*` syncs invoices, payments, customers — [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md)).
     - `"invoice"` is the most-used audit `entityType` (22 sites, [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md)) and `InvoiceLineType` is persisted.
     - `expense` and `disbursement` are two entities, not one word (`$B/expense/Expense`, `verticals/legal/disbursement/LegalDisbursement`, [ADR-247](ADR-247-legal-disbursement-sibling-entity.md)).
     - Renaming `expense` on the wire would create a second `/api/…/disbursements`.
     - These terms are leaf labels on a handful of pages, not identifiers in every join.
     - The cost/benefit is inverted relative to the two nouns.

**Decision**: Option 2 — rename exactly `project→matter` and `customer→client` on REST paths, DTO records and components, JSON, TypeScript, `lib/api`, server actions, route segments and portal paths; keep JPA entities, repositories, services, tables, columns, audit/EntityTag/SavedView strings and the whole `mcp/` package unchanged; `invoice`, `expense`, `timeEntry`, `rateCard` and `budget` stay on the wire and change only at the label.

**Rationale**:

1. **The seam already exists.** `ProjectResponse.from(Project)` / `CustomerResponse` factories are the only place entity meets wire, and no controller leaks an entity (§3.2). Renaming the record and its components renames the JSON, so the DTO layer carries the whole change and the non-DTO occurrences never move. Schema-per-tenant tables keep their names; Phase 87 ships zero migrations and Phase 84 still lands on `V4`.
2. **Two nouns is where the value is.** `project`/`customer` appear in every URL, every join field and every TS type; `invoice`, `expense` and `budget` are leaf labels on a few pages. `invoice` in particular is a statutory term, the Xero object and the top audit `entityType`; renaming it buys a word on the wire at the cost of three integration surfaces.
3. **The MCP contract is a dependency, not a surface.** `kazi-legal-za` (seven skills) is built on `mcp/dto/*` shapes with `customerId` on the wire (`McpMatterDto`, `McpInvoiceDto`, `McpTrustBalanceDto`) and tool params `projectId`/`customerId` (39/54 occurrences). The rename stops at the `mcp/` package; [ADR-343](ADR-343-mcp-contract-pinning-test.md) pins it.
4. **Batch per bounded context, IDE rename for Java; each batch keeps `main` green.** Each batch is its own PR with a full `./mvnw verify` (CLAUDE.md §1, §5) and `./mvnw spotless:apply` before push; rename PRs never carry IA changes. Because the wire contract is one unit of change, batches 1–5 each also carry the **minimum frontend and portal edits** to stay green — the `/api` path literals and JSON field names for that bounded context only, no TypeScript identifier/type renames, no route changes — so there is no backend dual-path period and no red window; the frontend and portal gates run on every batch PR alongside the backend verify:
   1. matters core (+ both contract tests and the MCP pin, [ADR-343](ADR-343-mcp-contract-pinning-test.md));
   2. clients core;
   3. finance — time, invoices, billing runs, expenses, budgets, rates, profitability;
   4. legal verticals — court dates, disbursements, trust client ledgers, closure, statements, conflicts, FICA;
   5. portal API + portal read-model DTOs;
   6. frontend/portal TS type/module names + routes + redirects (S4), after the wire is fully renamed.

   Every backend batch PR (1–5) carries the frontend/portal JSON-field renames with the property accesses they force, and the external-caller edits for its context (`compose/seed/**`, `scripts/regression-test.sh`, API-calling Playwright fixtures/specs), so `main` is green at every merge; batch 6 (S4) is TS type/module names and routes only.

   Java symbols are renamed with IntelliJ `rename_refactoring`, one symbol per call, never text replace; path templates, `@PathVariable` names and TS are explicit edits guarded by the contract test.
5. **Contract test, not redirects, on the backend.** Two greenfield JUnit tests land in batch 1 (no springdoc exists and none is added):
   - `WireVocabularyContractTest` (in `project/`, annotated like `McpHandshakeIntegrationTest` so it reuses that context-cache key) enumerates `RequestMappingHandlerMapping` and asserts no pattern contains `/projects` or `/customers` outside `/mcp` — scoped to batch 1's packages in S1, widened to batch 2 in S2, and to everything outside `mcp/` in S3;
   - a reflection scan of every `record` under `**/dto/**` and controller packages (excluding `mcp/` and OAuth types in `security/`) asserts no component named `projectId`, `customerId`, `projectName`, `customerName`, `projectRole` or `customerType`.

   Old `/api/projects` paths return **404**: the only consumers are the frontend and portal in this repo (no production tenant, no third-party API client; the plugin uses `/mcp`), and a redirect layer would keep the old vocabulary alive and defeat the test. Frontend bookmarks get permanent redirects instead.

**Consequences**:

- Positive:
  - `WireVocabularyContractTest` + the DTO reflection scan make the rename permanent and catch any future `/api/projects` endpoint at PR time.
  - The test file enumerates every renamed path family, satisfying "every renamed path appears in the contract test".
  - No migration, no entity/repository/service edit, no gateway or `SecurityConfig` change (both stay on the slice checklist as "verify no path literal").
  - `frontend/next.config.ts` `redirects()` gains `/org/:slug/projects/:path*` → `/org/:slug/matters/:path*` and `/org/:slug/customers/:path*` → `/org/:slug/clients/:path*` (permanent; `:path*` keeps query strings; precedent `/settings/team → /team`).
  - `portal/next.config.ts` gains its first `redirects()` block for `/projects` → `/matters`.
- Negative:
  - A permanent two-vocabulary backend (`Matter` on the wire, `Project` in JPA/SQL) — `backend/CLAUDE.md` gets a "wire vs persistence vocabulary" paragraph.
  - Reviewers must reject PRs that rename an entity "for consistency".
  - ~163 frontend files change across batches 1–6: field renames and caller edits land with batches 1–5 (so `main` is green at every merge), and batch 6 (S4) carries only TS type/module names, route segments and redirects.
  - `breadcrumbs.tsx` `NON_ROUTABLE_PREFIXES` + `SEGMENT_LABELS` (pinned by `breadcrumbs-routes.test.ts`), `recent-items-provider.tsx` regex `^\/org\/[^/]+\/(projects|customers)\/`, `lib/api/setup-status.ts`, `components/audit/entity-cell.tsx` hrefs and the command palette all move in that PR.
  - `trust-accounting/client-ledgers/[customerId]` becomes `[clientId]`.
  - Old API paths 404 — Postman collections and the e2e `page.goto()` literals (`/projects` ×32, `/customers` ×12) are amended in the same PR ([ADR-344](ADR-344-qa-scenario-amendment-authorisation-phase87.md)).
  - `apiRequest` has no path registry, so a missed literal surfaces only at runtime or in Playwright.
  - `MatterListRow` / `ClientListRow` (architecture §12.3) are new DTOs born in the renamed vocabulary.
  - They must not be back-ported into `ProjectResponse`.
- Follow-ups:
  - `CustomerController.listCustomers` in-controller filtering (Controller-Discipline violation) is not fixed by the rename — record it, do not bundle it (CLAUDE.md §7).
- Related:
  - [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md) (strings that deliberately do not follow the wire), [ADR-343](ADR-343-mcp-contract-pinning-test.md) (the boundary the rename stops at), [ADR-338](ADR-338-terminology-layer-removal-single-vocabulary.md) (labels for everything else), [ADR-306](ADR-306-mcp-tool-contract-design.md) (why `mcp/dto` is its own layer), [ADR-331](ADR-331-migration-squash-at-zero-tenants.md) (why tables cannot move), [ADR-247](ADR-247-legal-disbursement-sibling-entity.md) (why `expense` cannot be renamed to `disbursement`), [ADR-340](ADR-340-disbursement-is-the-face-of-matter-costs.md).
