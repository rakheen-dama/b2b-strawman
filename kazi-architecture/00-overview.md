# 00 — Kazi in One Page

**Last updated:** 2026-05-10

## What Kazi is

Kazi is a multi-tenant B2B SaaS for **professional-services firms** that bill clients for work — initially built generic, then verticalised toward legal, accounting, and consulting practices. It is the product surface name; **b2mash** is the company; package namespaces use `io.b2mash.b2b.*` and `@b2mash/*` (see `feedback_product_name_kazi.md` in user memory).

The shape of the product is a practice-management workspace per firm:

- **Customers / Clients / Matters** — who you serve and what you do for them.
- **Projects / Engagements / Matters** — units of delivery, with tasks and recurring schedules.
- **Tasks, Time, Expenses, Disbursements** — what gets done and what gets billed.
- **Documents, Templates, Clauses, Acceptances, Proposals** — the paper trail and the sales pipeline.
- **Invoicing, Retainers, Bulk Billing, Tax** — turning work into money. Legal practices add **trust accounting** (Section 86 / LPFF) on top.
- **Customer Portal** — a separate app where end-customers see their projects, invoices, proposals, deadlines, and (legal) trust ledger.
- **Automation, Notifications, Audit, AI Assistants, Reporting** — the operational fabric.

A firm signs up as an **Org** (admin-gated, no self-serve), picks a **Vertical Profile** (legal-za / accounting-za / consulting-za / base), and the org is provisioned with a tenant schema, role definitions, terminology overrides, and the seeded packs (field packs, compliance packs, template packs, clause packs, request templates, project templates) appropriate to that vertical.

## Who uses it

Three audience layers:

1. **Firm staff** — owners, admins, members. Use the main staff frontend (`frontend/`, port 3000 / via gateway 8443 in production). Authenticated via Keycloak. Capability-gated RBAC over a small set of org roles.
2. **End customers / clients** — receive magic-link emails, log into the portal (`portal/`, port 3002) to view what the firm has shared with them.
3. **Platform admins (b2mash)** — approve org access requests, manage tenant subscriptions. Use the staff frontend with a `platform-admins` Keycloak group claim.

## Headline capabilities (current, not aspirational)

- Multi-tenant SaaS with **schema-per-tenant** isolation (one Postgres schema per Org).
- **Vertical profile system** that lets the same codebase serve multiple verticals via JSON profiles + packs + terminology overrides + module gates. New verticals are added by adding a JSON file plus pack content; the core code does not branch on vertical.
- Full practice lifecycle: customer onboarding → matter/proposal → engagement → tasks/time/expenses → invoice → payment → retention/anonymisation.
- **Trust accounting** for legal practices (general, investment, Section-86 accounts) with hard guards preventing trust-related invoices from leaking through to accounting integrations.
- **Document pipeline** — Tiptap-based templates, DOCX merge, e-acceptance with audit certificates.
- **Workflow automations** — rules over domain events with conditions, actions, scheduled triggers, AI-specialist actions.
- **AI assistants** — BYOAK (bring-your-own-API-key) LLM provider, tool framework, SSE streaming chat, specialist personas, human-approval queue for write actions.
- **Customer portal** with magic-link auth, document acceptance, proposal accept/decline, info-request fulfilment, trust-ledger view.
- **Audit log** that is immutable at both the JPA and Postgres-trigger layers; AuditDeltaBuilder for field-level diffs; DSAR/PAIA flows.

## The four deployable units

| Unit | Tech | Port | Role |
|---|---|---|---|
| **`backend/`** | Spring Boot 4, Java 25, Hibernate 7, Postgres 16, Maven | 8080 | The core: ~40 feature packages, ~60 entities, ~280 REST endpoints, sealed `DomainEvent` bus, schema-per-tenant via Java 25 ScopedValue. |
| **`frontend/`** | Next.js 16 App Router, React 19, TypeScript 5, Tailwind v4, Shadcn UI | 3000 | Staff workspace. Single fetch client (`lib/api/client.ts`), runtime vertical branching via `OrgProfileProvider` + `TerminologyProvider` + `ModuleGate`. |
| **`gateway/`** | Spring Boot 4 + Spring Cloud Gateway Server WebMVC | 8443 | Thin BFF: Keycloak OIDC login → SESSION cookie → TokenRelay to backend. One YAML route. No custom filters yet. |
| **`portal/`** | Next.js 16, separate from staff frontend | 3002 | End-customer app. Magic-link auth, JWT in localStorage, calls backend `/portal/**` directly (bypasses gateway). |

## The architectural seams worth knowing on day one

- **Multi-tenancy is everywhere.** Every backend service runs inside a `RequestScopes.TENANT_ID` ScopedValue binding; every JPA call goes through Hibernate's tenant-aware connection provider; every scheduled job iterates tenants via `TenantScopedRunner`. See `20-cross-cutting/multitenancy.md`.
- **Multi-vertical is a first-class concern.** Vertical profiles, packs, terminology, and module gates are the seams that let one codebase serve N verticals. This is the *load-bearing* piece — see `20-cross-cutting/multi-vertical.md`.
- **Domain event bus wires the modules.** A sealed `DomainEvent` interface with ~35 record permits. Automation engine subscribes to all events; notifications, portal read-model sync, and audit are secondary consumers. See `30-modules/domain-events.md`.
- **Audit is non-bus.** Audit emission is in-transaction (not via the bus) so it can never lie about a rollback. Every other secondary effect uses `@TransactionalEventListener(AFTER_COMMIT)` because the side effects (email, read-model, integration push) are irreversible.
- **Capability-based RBAC, not role-based.** `RequiresCapability` annotation + `Capability` enum. Org roles are templates that set capabilities. Same model on the frontend (`CapabilityProvider` + `RequiresCapability` UI wrapper).
- **The portal does not transit the gateway.** Portal traffic goes directly to the backend with a portal-specific JWT. Staff traffic goes through the gateway with an OAuth2 session cookie. Two trust boundaries, two auth stacks.

## Where current trajectory points

Current epic stream (per `TASKS.md`) is in the AI-automation-and-vertical-depth phase: AI-specialist personas, automation queue UI, scheduled trigger reapers, Xero accounting integration (Phase 71), legal-vertical depth-II, firm audit view. The next architectural decisions worth attention live in `90-adr-index.md` Phase-71 cluster (ADRs 272–279).

## Cross-references

- Module map: `10-bounded-contexts.md`
- Vocabulary: `glossary.md`
- ADR status: `90-adr-index.md`
- Cross-cutting concerns: `20-cross-cutting/`
- Per-vertical overlays: `60-verticals/`
- Per-repo entrypoints: `70-repos/`
- Headline flows: `50-flows/`
- Maintenance discipline: `99-conventions.md`
