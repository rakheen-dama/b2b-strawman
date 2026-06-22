# Phase 81 — Inbound Correspondence & the First Gated MCP Write-Back (Email → Kazi)

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` (2026-06-21). This phase opens the **"Phase D — gated write-back over MCP"** chapter reserved in the MCP plugin strategy (`.claude/ideas/mcp-plugin-strategy-2026-06-14.md`), using **email-filing as the lighthouse use case**. It adds two things: a **net-new inbound-correspondence domain** in Kazi, and the **first write tools on the Kazi MCP server** (which is read-only today).
>
> **Scouting note (why this phase exists, and what it must NOT duplicate):** ideation verified the codebase before writing this spec (the Phase 80 lesson: two of three front-runners were already built). Verified state:
> - The **Kazi MCP server is built** (Phase 78 — `backend/.../mcp/`, ~10–12 **read-only** tools: `kazi_ping`, `list_matters`, `get_matter`, `search_documents`, `get_document_url`, `list_clients`, `list_invoices`, `get_invoice`, `get_trust_balance`, `list_compliance_gaps`, `get_matter_activity`, `get_audit_events`), with enablement + POPIA egress consent (`McpEnablementService`, `V129__create_mcp_egress_consents.sql`) and OAuth (ADR-303). **Do not rebuild it — extend it.**
> - **Inbound correspondence / stored email is ABSENT.** Only **outbound** notification email exists (`integration/email/` — `EmailMessage` record, `EmailNotificationChannel`, SMTP/SendGrid providers, Mailpit). There is no entity for received/filed correspondence. This is the net-new domain.
> - **`AiExecutionGate` is built** (`integration/ai/gate/` — `AiExecutionGate` PENDING→APPROVED|REJECTED|EXPIRED, `AiExecutionGateService`, `AiExecutionGateController` with `/api/ai/gates/{id}/approve|reject` gated by `AI_REVIEW`) but **gate-creation is NOT exposed over MCP** — only the in-product AI skills create gates today.
> - **Reusable downstream:** `DocumentService` (`initiateCustomerUpload`/`confirmUpload`, scopes PROJECT/CUSTOMER/ORG, source `MANUAL`/`AI_GENERATED`), `CustomerRepository.findByEmail`, `TaskService.createTask`, audit/activity registries.
> - **Latest tenant migration is `V129`.** Resolve the actual next free `V` at build time (do not hardcode `V130`).

## System Context

Kazi is a mature multi-tenant B2B practice-management platform (Next.js 16 frontend + portal, Spring Boot 4 / Java 25 backend, Keycloak OIDC, schema-per-tenant via Hibernate + Flyway). 80 phases have shipped. Phase 78 shipped a **read-only Kazi MCP server** so a firm's *own* Claude (Claude Code / Desktop / claude.ai) can be grounded in the firm's live data — the "bring your own Claude; Kazi provides the grounded context" play. That server has read tools only; the strategy note reserved a later **gated write-back path** (`AiExecutionGate` creation over MCP, approved in-product) but it has never been built.

A SA law firm's lawyers already reach for Claude with a **Gmail connector** attached. Today, when an email arrives that belongs to a matter, filing it into Kazi is fully manual: download, re-upload, retype the deadline as a task, copy the body into a note. Claude can *read* the email (Gmail connector) and *read* Kazi (MCP), but it cannot **write** anything back — so the lawyer is the copy-paste bridge, slowly and with POPIA exposure.

- **MCP server** (`backend/.../mcp/`) — Phase 78. Per-user-authenticated (OAuth, ADR-303), per-tenant, enablement- and POPIA-consent-gated (`McpEnablementService.effectiveState()`), every tool call resolved through the existing tenant/member/capability pipeline, every read audited. **Read-only by construction today.**
- **Inbound email** — **does not exist.** Outbound notification email (`integration/email/`) is unrelated and must not be conflated.
- **`AiExecutionGate`** (`integration/ai/gate/`) — the existing write-safety machinery: an AI-proposed action is stored as a JSON blob in a PENDING gate; an authorised member approves/rejects **inside Kazi** (`AI_REVIEW` capability); on approval a `GateActionExecutor` performs the action. 72h expiry via scheduler.
- **Documents** (`document/`) — `DocumentService` files documents into a matter (PROJECT scope) or against a customer (CUSTOMER scope) via presigned S3 upload + confirm, tracking `source` (`MANUAL`/`AI_GENERATED`/…) and visibility (`INTERNAL`/`SHARED`/`PORTAL`).
- **Customers / Tasks** — `CustomerRepository.findByEmail(email)` resolves a customer by email; `TaskService.createTask(projectId, title, …, dueDate, actor)` creates a task with a due date (deadlines are `task.dueDate`, not a separate entity).

### Founder decisions that constrain this phase (2026-06-21 ideation)

- **BYOC ingestion — Kazi does NOT integrate Gmail.** The firm's own Claude holds the Gmail connector and the Kazi MCP server, reads the email, and performs the extraction. Kazi only **receives** the filed result through MCP write tools and persists it. **No Gmail OAuth, no polling, no inbound webhook, no email sync, and no LLM/extraction call inside the Kazi backend.** This preserves the "the firm pays the tokens" cost model and keeps the Kazi surface small. (A server-side Gmail integration with in-product extraction was considered and **rejected**.)
- **Tiered write safety.**
  - **Tier-1 (direct, audited writes):** filing the email as a **correspondence record** and **attachments as documents** are *recording* actions, not legal *acting* — they persist immediately, audited, **ungated**.
  - **Tier-2 (gated writes):** creating matter **tasks/deadlines** (and, in v2, contacts) is *acting* — it goes through `AiExecutionGate` (PENDING → attorney approves inside Kazi → executed).
- **Build the gated seam + one proof this phase.** v1 ships the Tier-1 capture path **and** exposes `AiExecutionGate`-creation over MCP, validated end-to-end by **exactly one** Tier-2 proof tool (`propose_task`). This proves the Claude→propose→approve-in-Kazi→execute loop now; bulk task/deadline extraction is v2. (Contract-only was the alternative; the founder chose to wire and prove the seam.)
- **v1 extract scope = correspondence record + attachments-as-documents** (Tier-1) + the single `propose_task` proof (Tier-2). **New contacts/parties, bulk deadline/task extraction, thread reconstruction beyond a thread id are v2.**
- **Linking is Claude's job, helped by a read tool.** A `resolve_matter_by_email` read tool (reusing `findByEmail` + matter listing) returns candidate matters/clients; the firm's Claude disambiguates and passes an **explicit** `matterId`/`customerId` to the write tools. Kazi does not auto-file on a guess.
- **Repo split.** This spec covers the **Kazi backend** (correspondence domain + MCP write tools + gate-over-MCP + migration). The **consumer skill** (orchestration: read Gmail → reason → call Kazi tools) is a **follow-on in `../claude-for-legal-sa`**, matching the `kazi-legal-za` skillpack precedent — **out of scope here**, noted as the next step.

## Objective

Let a firm's own Claude file an email into the right Kazi matter without copy-paste, safely. Specifically: (1) add an **inbound-correspondence** domain that stores a filed email (headers, body, thread, direction) against a matter/customer; (2) add the **first write tools** to the Kazi MCP server — `file_correspondence` and `attach_document` (Tier-1, direct) — reusing the existing enablement/consent/auth/audit pipeline; (3) add a **resolve-by-email** read tool so Claude can pick the right matter; (4) **expose `AiExecutionGate` creation over MCP**, proven by one Tier-2 `propose_task` tool whose approval/execution happens inside Kazi unchanged. One new bounded context (`correspondence`), maximal reuse of documents/gates/audit/MCP infrastructure.

## Constraints & Assumptions

- **Reuse, do not duplicate.** Attachments file through the **existing** `DocumentService` (a correspondence-scoped variant if needed, but not a new storage path). Gated writes create an **existing** `AiExecutionGate` and execute through the **existing** `GateActionExecutor`/approval flow — **no parallel gate, no second approval UI, no new safety machinery**. MCP auth, enablement, POPIA egress consent, and per-call audit are the **existing** Phase 78 pipeline, extended (not forked) to cover writes.
- **No Gmail in the backend.** Verify at review: no Gmail/Google API dependency, no IMAP/POP, no inbound HTTP webhook receiver, no scheduled mail poll, and **no Anthropic/LLM call** added to the Kazi backend by this phase. Extraction happens entirely in the firm's Claude client; Kazi receives structured input.
- **Idempotent ingestion.** A `file_correspondence` call carries a client-supplied stable key (e.g. the RFC-822 `Message-ID`, or a provided `externalId`). Re-filing the same email (Claude retries, or the lawyer re-runs) must **not** create duplicates — upsert/no-op on the key, scoped to the tenant + matter. Document the dedupe rule.
- **Tiered safety is enforced server-side, not by trust.** The MCP write tools themselves classify the action: `file_correspondence`/`attach_document` persist directly; `propose_task` **only** creates a PENDING gate and **never** creates a `Task` directly. A Claude client cannot bypass the gate by calling a "direct" path for a Tier-2 action — there is no such path.
- **Write capability gating.** Read tools today resolve member capabilities; **write tools need a distinct write authorization.** Introduce an MCP **write capability/scope** (e.g. `MCP_WRITE` or a correspondence-specific capability) that a member must hold for `file_correspondence`/`attach_document`, and the existing `AI_REVIEW` capability still governs gate **approval**. A read-only MCP user must not be able to write. Decide the exact capability shape at `/architecture` and reuse `@RequiresCapability`.
- **POPIA / egress symmetry.** Phase 78 gates *reads* behind egress consent. Writes move client data *into* Kazi (lower-risk direction) but still must be enablement-gated and **audited as writes** (`mcp.write.*` audit family, distinct from `mcp.read.*`). Confirm whether write actions require their own consent flag or inherit MCP enablement; document the decision.
- **Tenant isolation.** All new tables live in the per-tenant schema (`db/migration/tenant/`); every tool resolves tenant → member → capability exactly like an inbound API request; a correspondence/document/gate created via MCP is visible only within its tenant. **Mandatory tenant-isolation test.**
- **Migration numbering.** Add `db/migration/tenant/V{next}__create_correspondence_tables.sql` — resolve the actual next free `V` at build time (latest is `V129__create_mcp_egress_consents.sql`; do **not** hardcode `V130` without confirming).
- **Package convention.** New bounded context mirrors a recent domain package (`correspondence/` recommended): root entity `Correspondence`, `CorrespondenceRepository`, `CorrespondenceService`, `dto/`, `event/`, enums in-package. MCP write tools live alongside the existing read tools in `mcp/tool/` following their registration pattern.
- **Test strategy.** Backend: full `./mvnw verify` clean (not narrowed). Unit + integration tests for: correspondence create + idempotent re-file; attachment-as-document via `DocumentService`; `resolve_matter_by_email`; the MCP write-capability gate (read-only user rejected); `propose_task` creates a PENDING gate and **does not** create a task until approved; approval executes the task creation; tenant-isolation. **"PASS means observed"** — exercise the MCP tools against a running server (Claude or an MCP test client) → backend log → DB row / S3 object / gate record; reproduce-before-fix for any bug. No testcontainers (in-memory/mock per project convention).

---

## Section 1 — Data Model

### 1.1 `Correspondence` (the filed email record)
- Fields (guidance, finalise at `/architecture`): `id` (UUID), **linkage** — `customerId` (UUID, nullable FK) and `matterId`/`projectId` (UUID, nullable FK) with the rule that **at least one** is non-null (a correspondence must attach to a matter and/or a client); `direction` (enum `INBOUND` | `OUTBOUND`, default `INBOUND` for v1 — model the enum now, only inbound is written); `subject`; `bodyText` (and optional `bodyHtml`); `fromAddress`, `toAddresses`, `ccAddresses` (stored as appropriate collections/JSON); `sentAt` / `receivedAt` (instants from the email headers, supplied by Claude); `threadKey` (provider thread id, nullable) and `externalId`/`messageId` (the **idempotency key**, unique per tenant); `filedByMemberId` (the member whose MCP credentials filed it); `source` (e.g. `MCP_GMAIL` / `MCP`); audit columns.
- A `Correspondence` **owns zero or more attachment documents** — model the link to `Document` (FK on `Document` to `correspondenceId`, or a join; pick the lower-impact side at `/architecture`).
- Surfacing on the matter/customer activity feed + audit via the existing registries (register the new entity type; do not bespoke-build).

### 1.2 Idempotency
- A unique constraint on `(tenant schema) + externalId/messageId` (+ optionally matter) makes re-filing a no-op that returns the existing record id. Define and document the exact key and collision behaviour.

### 1.3 Reuse — no new document storage
- Attachments are **`Document` rows** filed through `DocumentService`, not bytes on `Correspondence`. Add a CORRESPONDENCE-aware path (or reuse PROJECT/CUSTOMER scope with a `correspondenceId` link) so an attachment is both a first-class document *and* tied to the correspondence.

---

## Section 2 — MCP Write Tools (the first writes on the server)

> All tools extend the **existing** Phase 78 MCP pipeline: enablement + POPIA gate (`McpEnablementService.effectiveState()`), OAuth per-user auth → tenant/member/capability resolution, and per-call audit. New for writes: the **write capability check** and the **`mcp.write.*` audit family**.

### 2.1 `resolve_matter_by_email` (read helper)
- Input: an email address (+ optional subject/reference hint). Output: candidate `{customer, matters[]}` the address maps to (reuse `CustomerRepository.findByEmail` + matter listing), so Claude can disambiguate and choose an explicit target. **Read-only** (audited as `mcp.read.*`). If zero/many matches, return them all — Kazi does not guess.

### 2.2 `file_correspondence` (Tier-1, direct write)
- Input (from Claude, post-extraction): explicit `matterId` and/or `customerId`, the email fields (from/to/cc/subject/body/timestamps/threadKey), and the idempotency key (`messageId`/`externalId`).
- Behaviour: upsert a `Correspondence` (no-op on duplicate key), write audit + activity, return the correspondence id. **No gate.**

### 2.3 `attach_document` (Tier-1, direct write)
- Input: a `correspondenceId` (and/or matter/customer), attachment metadata, and the bytes-delivery contract. Decide the **byte-transfer mechanism** at `/architecture`: either (a) the tool returns a **presigned upload URL** (reusing `DocumentService.initiate*Upload` + `confirmUpload`) that the Claude client PUTs to — preferred, mirrors the existing pattern — or (b) inline base64 for small attachments. Files a `Document` (`source = MCP`/`AI_GENERATED`-style, visibility `INTERNAL` by default) linked to the correspondence + matter. **No gate.**

### 2.4 `propose_task` (Tier-2, gated write — the one proof)
- Input: target `matterId`/`projectId`, proposed task fields (title, description, `dueDate`, optional assignee), and a reference to the originating `correspondenceId`.
- Behaviour: create an **`AiExecutionGate`** (PENDING) whose JSON payload describes "create task X on matter Y from correspondence Z" — **do not create the `Task`**. Return the gate id. Audit as `mcp.write.*` (proposed). An authorised member (`AI_REVIEW`) approves/rejects **inside Kazi** via the existing `AiExecutionGateController`; on approval the existing `GateActionExecutor` calls `TaskService.createTask`. This is the **end-to-end seam proof** — exactly one Tier-2 tool ships; bulk extraction is v2.

### 2.5 Write authorization & audit
- Gate `file_correspondence`/`attach_document`/`propose_task` behind the new MCP **write capability**; keep `resolve_matter_by_email` on read capability. Every write tool emits a distinct `mcp.write.*` audit event (member, tenant, target entity, tool, outcome) so the firm has a POPIA-defensible trail of what AI *wrote* (separate from what it read).

---

## Section 3 — Gate-over-MCP (reuse, do not rebuild)

### 3.1 Expose gate creation, not approval
- The MCP server may **create** gates (`propose_task`); it must **never** approve them. Approval/rejection stays exclusively inside Kazi (`AiExecutionGateController` + existing UI, `AI_REVIEW` capability). Confirm the existing gate payload/`GateActionExecutor` can represent and execute a "create task from correspondence" action; add the action type if missing, following the existing executor pattern.

### 3.2 No second gate, no second executor
- Reuse `AiExecutionGate`, its lifecycle, expiry scheduler, and approval UI verbatim. The only new code is the MCP tool that *creates* the gate and (if absent) the executor branch for the task-creation action.

### 3.3 Make the loop observable
- The approving member sees, on the gate, the originating correspondence (link), so approval is informed. The executed task links back to the correspondence. Audit records "AI-proposed (via MCP) → member-approved → executed" with actors + timestamps.

---

## Section 4 — Surfacing in the App (lean)

- **Matter / customer correspondence visibility.** Filed correspondence appears on the matter (and customer) via the **existing activity feed / audit timeline** — register the new entity type with those registries. A minimal **"Correspondence" list** (read-only) on the matter detail (subject, from, date, attachment count, link to documents) is in scope; a full inbox/threaded-conversation UI is **not**.
- **Attachments** show in the matter's existing documents list (they are `Document`s) — no new document UI.
- **Gated proposals** appear in the **existing** AI-gate review surface unchanged; no new approval screen.
- Frontend conventions: pages under `frontend/app/(app)/org/[slug]/...`, Next.js 16 (params are Promises), Shadcn UI, reuse existing detail-tab + timeline components. Route any UI design question through Shadcn/Next.js conventions.

---

## Out of Scope

- **Any Gmail / email-provider integration inside Kazi** — OAuth, IMAP/POP, inbound webhooks, polling, sync. The firm's Claude is the only thing that touches the mailbox.
- **Any LLM / extraction call in the Kazi backend** — extraction happens in the firm's Claude client; Kazi receives structured input.
- **Tier-2 beyond the one proof:** bulk task extraction, deadline/calendar entities, auto-creating multiple tasks, and **new contact/party detection & creation** — all **v2**.
- **Outbound correspondence writing / "send email from Kazi"** — the `OUTBOUND` enum value is modelled but no send path ships.
- **Threaded conversation / full inbox UI**, reply tracking, read receipts.
- **Portal exposure of correspondence** — correspondence is firm-internal; the client portal does not show it.
- **The consumer Claude skill** (Gmail-read → reason → call Kazi tools) — ships in `../claude-for-legal-sa`, not this repo.
- **MCP write tools other than the three named** (`file_correspondence`, `attach_document`, `propose_task`) plus the `resolve_matter_by_email` read helper.

---

## ADR Topics to Address

- **ADR-319**: Inbound-correspondence domain — the `Correspondence` entity, its matter/customer linkage rule (≥1 non-null), attachments-as-`Document`s (no new storage), and the idempotency key/dedupe contract.
- **ADR-320**: BYOC ingestion boundary — why Kazi does **not** integrate Gmail or run extraction; the trust boundary (Claude extracts, Kazi receives structured input), and what this buys (cost model, small surface, POPIA posture).
- **ADR-321**: MCP write-tool category & capability — introducing the first writes on the read-only Phase 78 server; the new MCP **write capability/scope** vs read capability; `mcp.write.*` audit family; enablement/POPIA symmetry for writes.
- **ADR-322**: Tiered write safety & gate-over-MCP — the direct (Tier-1: correspondence/attachments) vs gated (Tier-2: tasks) split enforced server-side; exposing `AiExecutionGate` **creation** over MCP while approval stays in-product; reuse of the existing executor; why exactly one Tier-2 proof tool ships now.
- **ADR-323**: Email→matter linking — `resolve_matter_by_email` (reusing `findByEmail`), Claude-disambiguates-with-explicit-target rather than server-side auto-filing; zero/many-match behaviour.

---

## Style & Boundaries

- Follow `backend/CLAUDE.md` (Spring Boot 4, Hibernate 7, multitenancy, `TenantFilter`/`MemberFilter`/`RequestScopes`, delete-guard, audit/activity patterns, `@RequiresCapability`) and `frontend/CLAUDE.md` (Next.js 16, Keycloak, Shadcn).
- **Reuse over rebuild:** the Phase 78 MCP pipeline (auth/enablement/consent/audit), `AiExecutionGate` + `GateActionExecutor` + approval UI, `DocumentService`, `CustomerRepository.findByEmail`, `TaskService.createTask`, activity/audit registries.
- **One bounded context** (`correspondence`) + new MCP write tools in `mcp/tool/`. New per-tenant tables only; the only changes outside the new context are the thin `Document↔correspondence` link, the new write capability, and (if absent) one `GateActionExecutor` action branch.
- **Hard boundaries to verify at review:** no Gmail/Google/IMAP dependency, no inbound webhook/poll, no LLM call added to the backend, no Tier-2 action that bypasses the gate, no read-only MCP user able to write, tenant-isolation test present.
- Honour the project quality gates: backend → `./mvnw verify` clean (full suite); frontend/portal (if touched) → `pnpm lint && pnpm build && pnpm test` + prettier `format:check`; tenant-isolation test mandatory; **"PASS means observed"** (MCP tool call → backend log → DB/S3/gate record), reproduce-before-fix.

---

## Next step

`/architecture requirements/claude-code-prompt-phase81.md` — generates the architecture section + ADRs (319–323). Then `/breakdown 81` for epics/slices (target ~6 epics / ~12 slices). The **consumer skill** that drives Gmail → these tools is a separate follow-on in `../claude-for-legal-sa` (its plugin/marketplace flow, not `/architecture`). Reproduce-before-fix applies to any bug surfaced during build.
