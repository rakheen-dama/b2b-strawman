# Phase 83 — Collections & Cash Intelligence (Dunning Engine + AI-Drafted Reminders + Weekly Cash Digest)

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` (2026-07-09, log: `.claude/ideas/phase83-ideation-2026-07-09.md`). The revenue engine today ends at `SENT` — nothing in the product chases cash. This phase adds the missing **collections loop**: a deterministic dunning engine, an AI layer that drafts house-style reminders through the existing execution-gate machinery, and a weekly owner-facing cash digest. Fork-neutral core (every vertical bills); legal-za gets a trust-aware extension point, not core logic.
>
> **Scouting note (verified against the codebase 2026-07-09 — build on these, do not rebuild):**
> - **No dunning/collections machinery exists.** All "reminder" hits are unrelated (`schedule/TimeReminderHandler`, `informationrequest/RequestReminderHandler`, `verticals/legal/courtcalendar/CourtDateReminderHandler`, proposal/acceptance expiry). No `dunning`/`debtor` classes anywhere. This is the net-new domain.
> - **`InvoiceStatus` is `DRAFT, APPROVED, SENT, PAID, VOID` — there is NO `OVERDUE` status.** Overdue is derived at query time (see `reporting/InvoiceAgingReportQuery`, slug `invoice-aging`, aging buckets by `asOfDate`). **Keep it derived — do not add an OVERDUE status** (it would cross-cut every invoice flow for no benefit).
> - **Invoice payment is all-or-nothing.** `Invoice` has `status` + `paidAt` + `getPaymentUrl()` but **no `amountPaid`/`amountDue`** — `InvoiceService.recordPayment` flips SENT→PAID; amounts live on `PaymentEvent` only. Collections logic keys off `status = SENT && dueDate < today`; partial-payment modeling is out of scope.
> - **AI infra** (`integration/ai/`): `AiProvider` (+ `anthropic/AnthropicAiProvider`, `NoOpAiProvider`); skills implement `skill/AiSkill` (`skillId()`, `assembleSystemPrompt(AiFirmProfile)`, `assembleUserPrompt(SkillContext)`, `createGates(...)`, `requiresVision()`) and **register by simply being a Spring bean** (`AiSkillExecutionService` collects `List<AiSkill>`); gates are `gate/AiExecutionGate` with a sealed `gate/GateAction` parsed from `gateType` + `proposed_action` JSONB, executed by `gate/GateActionExecutor` (existing types incl. `CREATE_TASK_FROM_CORRESPONDENCE` — add new variants the same way); cost metering via `execution/AiExecution` + `cost/AiCostService`; firm style via `profile/AiFirmProfile`.
> - **Job queue** (`infrastructure/jobqueue/`): implement `JobHandler` (`jobType()`, `execute(payload)`, tenant scope pre-bound) as a `@Component` — `JobHandlerRegistry` auto-registers, fail-fast on duplicate jobType. Example: `integration/accounting/sync/AccountingPaymentPollHandler` (`accounting_payment_poll`).
> - **Email**: `integration/email/` port (`EmailProvider`: SendGrid/SMTP/NoOp) + `EmailDeliveryLog`. Invoice delivery precedent: `invoice/InvoiceEmailService.sendInvoiceEmail(...)`, template `templates/email/invoice-delivery.html`, **already injects `paymentUrl` (from `invoice.getPaymentUrl()`) + `portalUrl`** — reminder emails follow this exact pattern.
> - **Payment ground truth**: gateway webhooks → `invoice/PaymentReconciliationService` → `InvoiceService.recordPayment`; Xero pull → `integration/accounting/sync/AccountingPaymentPollWorker` (jobType `accounting_payment_poll`). `invoice/PaymentEvent` holds provider/amount/status.
> - **Digest precedent**: `portal/notification/PortalDigestHandler` (jobType `portal_digest`, weekly) → `PortalDigestScheduler.processTenant(...)`. The cash digest follows this shape.
> - **Trust extension point (legal-za)**: `integration/accounting/sync/TrustBoundaryGuard.evaluate(...)` → `TrustBoundaryDecision` (permit/refused; tolerant of non-legal tenants with no trust tables); available trust balance via `verticals/legal/trustaccounting/ledger/ClientLedgerService` (backs the `get_trust_balance` MCP tool).
> - **OrgSettings** (`settings/OrgSettings.java`) is one row of ~10 `@Embedded` value objects — a new collections-policy embeddable follows `settings/TimeReminderSettings` (closest analogue) + `OrgSettingsService`/`OrgSettingsController` exposure.
> - **Latest tenant migration is `V132__create_correspondence_tables.sql`.** Resolve the actual next free `V` at build time (do not hardcode `V133`).

## System Context

Kazi is a mature multi-tenant B2B practice-management platform (Next.js 16 frontend + portal, Spring Boot 4 / Java 25 backend, Keycloak OIDC, schema-per-tenant via Hibernate + Flyway). 82 phases have shipped. The money layer is deep: time → rates → invoicing (Phase 10) → tax (26) → online payments with portal payment links (25) → retainers (17) → disbursements + statements of account (67) → invoice-aging reporting (19) → one-way Xero sync with payment pull (71). The AI foundation is live (72/74): provider abstraction, firm profile, execution gates with in-product approval, cost metering, and five shipped skills. Scheduled work runs on a durable job queue (75).

What is missing is everything **after** an invoice is sent: no reminder ever goes out, no one watches the debtor book, and the owner has no periodic read on lockup. For SA professional-services firms, lockup (WIP days + debtor days) is the primary cash killer — and every ingredient to fix it already exists in the platform.

### Founder decisions that constrain this phase (2026-07-09 ideation)

- **Scope bundle = dunning engine + AI layer + cash digest.** All three ship in this phase; the digest is the owner-facing surface that makes the collections work visible.
- **Gated always, policy later.** Every AI-drafted reminder goes through an `AiExecutionGate` and is approved by a human before sending (batch-approve UX required). **No auto-send code path ships in v1** — auto-send for early stages is a possible later org-policy toggle, and the design should not preclude it, but this phase must not build it. A wrongly-worded auto-email to a client is a trust-destroying failure mode for a professional firm.
- **The month-end WIP-to-bill run is explicitly OUT.** It overlaps the Phase 70 Billing Assistant and the MCP-side `fee-note-run` consumer skill; it is the natural Phase 84, not a rider here.
- **Fork-neutral core, trust-aware edge.** The dunning engine, AI skills, and digest must work identically for legal-za, consulting-za, and accounting-za. The only vertical-aware behaviour is an **extension point**: when a customer has available trust funds (legal-za), triage suggests a fee transfer instead of / alongside a payment reminder. Core code must not import legal packages — mirror the `TrustBoundaryGuard` tolerance pattern.
- **Reminders ride the existing invoice-email pattern** (recipient resolution, payment link, portal link, delivery log) — no new email path, no new channel.

## Objective

Close the cash loop. Specifically: (1) add a **collections domain** — a graduated reminder policy on org settings, per-customer overrides, and a `CollectionActivity` log tying every chase action to an invoice/customer; (2) run a **scheduled debtor scan** on the job queue that identifies overdue `SENT` invoices per policy stage and produces work; (3) add an **AI reminder-drafting skill** that turns each due reminder into a house-style, context-aware email draft delivered as a PENDING gate, with **batch approval** and send-on-approve through the existing email infrastructure; (4) add an **AI debtor-triage capability** that ranks the debtor book (who is drifting, who always pays late, who needs a call, where trust funds could cover fees); and (5) ship a **weekly cash digest** — an AI-narrated summary (lockup, debtor days, billed vs collected, stale WIP, top risks) delivered in-app and by email to owners/admins, following the portal-digest scheduling pattern.

## Constraints & Assumptions

- **Reuse, do not duplicate.** Scan + digest are `JobHandler`s on the Phase 75 queue (no new scheduler). Drafts are `AiSkill` beans through `AiSkillExecutionService` with costs metered on `AiExecution` (no bespoke AI call path). Sends go through the invoice-email pattern (`EmailProvider` + `EmailDeliveryLog` + Thymeleaf template with `paymentUrl`/`portalUrl`). Approval reuses `AiExecutionGate` + `GateActionExecutor` + the existing gate review surface — **new `GateAction` variant(s), no parallel gate machinery**. Aging math reuses/extends the `InvoiceAgingReportQuery` logic rather than re-deriving buckets in new SQL.
- **No `OVERDUE` invoice status.** Overdue remains derived (`status = SENT && dueDate < asOf`). The collections domain observes invoices; it does not mutate the invoice lifecycle.
- **No partial-payment modeling.** `Invoice` keeps its all-or-nothing `paidAt` semantics. Collections treats an invoice as outstanding until `PAID`/`VOID`.
- **Server-side send safety.** The only way a reminder email leaves the system is `GateActionExecutor` executing an approved gate. There is no direct "send reminder" endpoint, and the scan job never sends — it only creates gates (via the drafting skill). Enforce by construction, verify at review.
- **Payment cancels pending work.** When an invoice becomes `PAID` or `VOID` (webhook, Xero pull, or manual `recordPayment`), any PENDING reminder gates for that invoice must be cancelled/expired and the cancellation recorded on `CollectionActivity`. Chasing a paid client is the second trust-destroying failure mode; make this path first-class and tested.
- **Idempotency.** One reminder per (invoice × policy stage). Re-runs of the scan (job retries, overlapping schedules) must not create duplicate gates or duplicate sends. Define the uniqueness rule on `CollectionActivity` and enforce it in the DB.
- **Per-customer control.** A customer can be excluded from collections entirely (e.g. "never chase this client") — a flag/override resolved before any gate is created. Decide the exact shape (Customer field vs. settings-side list) at `/architecture`; keep it minimal.
- **Recipient resolution mirrors invoice delivery.** Reminders go to the same recipients the invoice-delivery email used; no new recipient model. If an invoice has no resolvable recipient, the scan records a `SKIPPED` activity with reason rather than failing the batch.
- **Digest is AI-narrated but data-grounded.** The digest skill assembles numbers deterministically (aging query, billed-vs-collected, stale WIP from unbilled-time data) and asks the AI only to narrate/rank — the numbers in the email must come from queries, not from the model. Digest generation is metered like any skill; a firm with AI disabled gets no digest (or a plain-numbers fallback — decide at `/architecture`, prefer the cheapest correct option).
- **Trust extension point, not trust logic.** Core triage exposes a seam (interface/SPI in the collections domain) that legal-za implements using `ClientLedgerService` to annotate "client has R X available in trust — consider fee transfer". Non-legal tenants and non-legal builds see no trust language anywhere. Mirror `TrustBoundaryGuard`'s tolerant-of-absent-tables approach.
- **StubAiProvider parity.** Extend the stub provider so the drafting + triage + digest skills are fully exercisable in tests and the E2E stack without live Anthropic calls (Phase 74 precedent).
- **Tenant isolation.** New tables in `db/migration/tenant/` (resolve next free `V`; latest is `V132`). Scan and digest jobs are per-tenant via the job queue's ScopedValue binding. **Mandatory tenant-isolation test** (one tenant's reminders/gates/digest never visible to another).
- **Audit + activity.** Register new entity types with the existing audit/activity registries (lowercase entityType strings on the audit plane, per project convention). Gate lifecycle events (proposed → approved → sent / cancelled-on-payment) must be reconstructable from audit.
- **Test strategy.** Backend: full `./mvnw verify` clean. Integration tests for: scan produces the right stage-work from seeded overdue invoices; idempotent re-scan; gate approval sends email (Mailpit/GreenMail observation) + writes `CollectionActivity` + delivery log; payment cancels pending gates; excluded customer produces nothing; digest job produces the notification/email; tenant isolation. **"PASS means observed"** — run the scan job → see the gate → approve in the UI → see the email in Mailpit → see the `CollectionActivity` row. Reproduce-before-fix for any bug. No testcontainers.

---

## Section 1 — Data Model

### 1.1 Collections policy (org settings embeddable)
- A new `@Embeddable` on `OrgSettings` (follow `TimeReminderSettings`): enabled flag + graduated stages. v1 stages are fixed in shape, configurable in timing — e.g. `stage1DaysOverdue` (default 7), `stage2DaysOverdue` (default 21), `stage3DaysOverdue` (default 45), `escalateDaysOverdue` (default 60, produces a "flag for partner call" triage item, not an email). Expose via `OrgSettingsService`/`OrgSettingsController` + settings UI. Keep it flat — no per-stage template editor, no arbitrary stage lists in v1.

### 1.2 `CollectionActivity` (the chase log — new entity, new table)
- Guidance (finalise at `/architecture`): `id`, `invoiceId` (FK), `customerId` (FK), `stage` (enum incl. escalation/digest-agnostic values), `status` (e.g. `PROPOSED` → `APPROVED`/`REJECTED` → `SENT` | `CANCELLED_PAYMENT` | `SKIPPED`), `gateId` (nullable link to `AiExecutionGate`), `emailDeliveryLogId` (nullable), `reason` (for SKIPPED/CANCELLED), timestamps, actor. **Unique constraint enforcing one activity per (invoiceId, stage)** — this is the idempotency backbone.
- Surfaces on the customer detail (collections history) and feeds triage context ("two reminders ignored").

### 1.3 Per-customer override
- Minimal exclusion control ("do not chase") resolvable in the scan. Shape decided at `/architecture` (prefer a column on `Customer` or a small settings-side structure over a new entity).

### 1.4 What this phase does NOT model
- No changes to `Invoice` columns or `InvoiceStatus`. No `amountPaid`. No interest/late-fee fields. No new recipient/contact model.

---

## Section 2 — Dunning Engine (deterministic core)

### 2.1 Debtor scan job
- A `JobHandler` (e.g. jobType `collections_scan`, daily) that, per tenant: loads policy (skip if disabled), finds `SENT` invoices past due per stage thresholds, filters excluded customers and already-actioned (invoice, stage) pairs, resolves recipients, and **invokes the reminder-drafting skill per due reminder** (Section 3) — producing PENDING gates, never sends. Records `SKIPPED` activities with reasons for unresolvable cases.

### 2.2 Payment-cancellation path
- On invoice `PAID`/`VOID` (all three routes: gateway webhook reconciliation, Xero payment pull, manual record-payment), cancel that invoice's PENDING reminder gates and mark the linked activities `CANCELLED_PAYMENT`. Prefer listening to the existing invoice payment/void domain events; decide exact wiring at `/architecture`.

### 2.3 Send-on-approve
- New `GateAction` variant (e.g. `SEND_COLLECTION_REMINDER`) whose payload carries the drafted subject/body + invoice/customer refs. `GateActionExecutor` branch renders through a new `collection-reminder` email template (modelled on `invoice-delivery.html`, includes `paymentUrl` + `portalUrl`), sends via the existing email path, records delivery log + `CollectionActivity` transition to `SENT`. Rejection marks the activity `REJECTED` (stage may be re-proposed later — define the rule).

---

## Section 3 — AI Layer

### 3.1 Reminder-drafting skill
- An `AiSkill` bean (own subpackage under `integration/ai/skill/`, e.g. `collections/`). Context assembly (deterministic): invoice facts (number, amount, due date, days overdue), customer relationship (age, lifetime billed, payment history from `PaymentEvent`/prior invoices), prior chase history (`CollectionActivity`), current stage tone (gentle nudge / firm reminder / final demand). House style from `AiFirmProfile`. Output: subject + body for the gate payload. Escalation stage (`escalateDaysOverdue`) produces a triage/task-style gate ("suggest a call"), not an email draft — decide its exact gate shape at `/architecture` (reuse the existing task-creation executor if it fits).
- Cost metered per execution (`AiExecution`); batch of N reminders = N metered executions (acceptable at expected volumes; note the volume assumption in the ADR).

### 3.2 Debtor triage
- Ranks the outstanding book: who is drifting vs. their own payment pattern, who reliably pays late (suppress noise), who has gone quiet after multiple reminders, where the trust extension point reports available funds. v1 delivery: triage output feeds the **digest** (top risks section) and annotates the collections/debtors frontend surface — it does **not** create its own gates. Decide at `/architecture` whether triage is its own skill or a section of the digest skill (prefer whichever is smaller).

### 3.3 Trust extension seam (legal-za only)
- A collections-domain SPI (e.g. `CollectionsAdvisor` / contribution interface) with a no-op default; legal-za implementation consults `ClientLedgerService` and contributes "R X available in trust — consider fee transfer" annotations to triage/digest. Core never references trust concepts.

---

## Section 4 — Weekly Cash Digest

- A `JobHandler` (e.g. `cash_digest`, weekly) following `PortalDigestHandler`'s shape, per tenant: assemble numbers deterministically (total outstanding + aging buckets via the aging query logic; billed vs collected for the period; stale unbilled WIP; reminder activity summary; triage top-3 risks), have the digest skill narrate, deliver as an in-app notification + email to owner/admin members through the existing notification/email channels.
- If AI is disabled/unconfigured for the tenant: decide at `/architecture` between skipping the digest or sending a numbers-only fallback — prefer the cheaper-to-build option, document it.

---

## Section 5 — Frontend (firm app only; no portal changes)

- **Collections/Debtors surface** — a page (placement decided at `/architecture`: likely under the existing financial/reports area) showing the aging overview, per-customer outstanding drill-in with chase history, and the **pending reminder queue with batch approve/reject** (approve-all-checked; per-item preview of the drafted email before approval). Batch approval may need a small batch endpoint on the gate controller — extend, don't fork, the existing gate API.
- **Settings card** for the collections policy (enable + stage day-offsets) on the existing settings surface, plus the per-customer exclusion control on the customer detail.
- **Digest visibility** — the digest arrives via existing notification UI + email; a dedicated digest archive page is NOT in scope.
- Conventions: Next.js 16 (params are Promises), Shadcn UI, pages under `frontend/app/(app)/org/[slug]/...`; route design questions through the Shadcn/Next.js expert skills per project convention.

---

## Out of Scope

- **Auto-send** of any reminder at any stage (later org-policy toggle; design must not preclude, must not build).
- **Month-end WIP-to-bill run** (natural Phase 84 — overlaps Billing Assistant + `fee-note-run`).
- **Partial payments / `amountPaid` on Invoice**, interest on overdue, late fees, payment plans.
- **New channels** (SMS/WhatsApp), external debt-collection or attorney-handoff integrations.
- **Portal changes** — the client-side experience remains the existing payment link + portal invoice pages.
- **Per-stage template editors / arbitrary custom stages** — v1 policy is fixed-shape, configurable timing.
- **Statement-of-account regeneration or bundling** into reminders (statements exist from Phase 67; a link is fine if trivial, generation is not in scope).
- **MCP exposure** of collections data/tools (candidate for a later phase alongside the consumer skill pack).

---

## ADR Topics to Address

- **ADR-325**: Collections domain & dunning engine — derived-overdue (no `OVERDUE` status), `CollectionActivity` + (invoice, stage) idempotency, policy-as-embeddable, scan-on-job-queue, payment-cancellation semantics across all three payment routes.
- **ADR-326**: Gated-send safety model — every send through `AiExecutionGate` (no direct send path by construction), the `SEND_COLLECTION_REMINDER` executor, batch approval, rejection/re-proposal rules, why auto-send is deferred.
- **ADR-327**: AI reminder drafting & debtor triage — context assembly, stage-tone control via prompts + `AiFirmProfile`, per-reminder cost metering and volume assumptions, triage-feeds-digest (no triage gates in v1), StubAiProvider parity.
- **ADR-328**: Weekly cash digest — deterministic numbers + AI narration split, data sources, cadence/delivery via existing channels, AI-disabled fallback behaviour.
- **ADR-329**: Vertical extension seam for trust-aware collections — the no-op-default SPI, legal-za implementation over `ClientLedgerService`, why core stays fork-neutral (mirrors `TrustBoundaryGuard` tolerance).

---

## Style & Boundaries

- Follow `backend/CLAUDE.md` (Spring Boot 4, Hibernate 7, multitenancy, `TenantFilter`/`MemberFilter`/`RequestScopes`, delete-guard, audit/activity registries, `@RequiresCapability`) and `frontend/CLAUDE.md` (Next.js 16, Keycloak, Shadcn).
- **Reuse over rebuild:** job queue (`JobHandler`), `AiSkill`/`AiSkillExecutionService`/`AiExecution`, `AiExecutionGate` + `GateActionExecutor` + gate review UI, `EmailProvider`/`EmailDeliveryLog`/invoice-email template pattern, `InvoiceAgingReportQuery` logic, `PortalDigestHandler` shape, OrgSettings embeddable pattern, audit/activity registries.
- **One bounded context** (`collections/` recommended) + the AI skill subpackage + one settings embeddable + one/two `GateAction` variants + two job handlers. Changes outside the new context stay thin: gate-executor branch, payment-event listeners, settings wiring, frontend pages.
- **Hard boundaries to verify at review:** no send path outside gate execution; no `OVERDUE` added to `InvoiceStatus`; no auto-send code; no trust/legal imports in core collections; payment-cancellation covered for webhook + Xero-pull + manual routes; (invoice, stage) uniqueness enforced in the DB; tenant-isolation test present.
- Honour the project quality gates: backend → `./mvnw verify` clean (full suite); frontend → `pnpm lint && pnpm build && pnpm test` + prettier `format:check`; **"PASS means observed"** (scan job → gate in UI → approve → Mailpit email → `CollectionActivity`/delivery-log rows); reproduce-before-fix; no testcontainers.

---

## Next step

`/architecture requirements/claude-code-prompt-phase83.md` — generates the architecture section + ADRs (325–329). Then `/breakdown 83` for epics/slices (target ~7–8 epics / ~14–16 slices: foundation/migration + policy settings; scan job + cancellation; drafting skill + gate executor; batch approval + collections frontend; triage + trust seam; digest; QA capstone). The month-end WIP-to-bill run is the natural Phase 84 follow-on.
