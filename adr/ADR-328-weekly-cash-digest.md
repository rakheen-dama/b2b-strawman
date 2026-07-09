# ADR-328: Weekly Cash Digest — Deterministic Numbers + AI Narration, Numbers-Only Fallback, Existing Channels

**Status**: Accepted

**Context**:

The owner-facing surface of Phase 83 is a weekly cash digest: lockup at a glance — total outstanding and aging buckets, billed vs collected for the period, stale unbilled WIP, what the collections engine did (sent/pending/cancelled), and the top debtor risks. All inputs exist (`InvoiceAgingReportQuery` bucket logic, unbilled-time queries, the new `CollectionActivity` ledger, triage signals per ADR-327); the platform has a weekly digest precedent (`PortalDigestHandler` → scheduler with cadence checks) and standard delivery channels (in-app `NotificationService`, email pipeline with templates + delivery log). Questions: the AI's role in a document full of financial figures, what happens for AI-disabled tenants, cadence/recipients, and whether digests are persisted.

**Options Considered**:

### A. AI's role in the digest

1. **Deterministic numbers, AI narrates (CHOSEN)** — a `CashDigestData` record is assembled from queries; the `cash-digest` skill receives it and returns a short narrative + top-3 ranked risks; the email template prints the authoritative figures from `CashDigestData` regardless of what the prose says.
   - Pros: every figure in the digest's tables is query-derived — a hallucinated number in prose cannot change what the table shows; narration is where AI adds value (what matters, what changed, what to do); the same `CashDigestData` serves the fallback (B) for free.
   - Cons: prose and table could theoretically disagree (mitigated: the prompt forbids figures not present in the input, and the table always wins visually).
2. **AI generates the whole digest** from raw-ish data.
   - Pros: one generation step; flexible layout.
   - Cons: financial figures from a language model, mailed to the firm owner weekly — the single worst hallucination surface imaginable for this product; unverifiable without re-deriving the numbers anyway.
3. **No AI — a pure numbers email.**
   - Pros: zero cost/risk.
   - Cons: the founder chose the digest as the AI-visible "wow" surface; ranked risks and narrative ("Naidoo & Co has ignored two reminders — suggest a call") are the difference between a report and an advisor.

### B. AI-disabled tenants

1. **Numbers-only fallback (CHOSEN)** — the template renders without the narrative/risks block (one Thymeleaf conditional); triage *signals* still appear (they are deterministic).
   - Pros: near-zero marginal cost — the number assembly and template exist regardless; AI-disabled firms still get the lockup summary (the digest's core utility); no dead tenant-configuration state where enabling collections yields silence.
   - Cons: two rendering variants to test (one conditional block; covered by `CashDigestServiceTest`).
2. **Skip the digest entirely when AI is off.**
   - Pros: absolutely nothing extra to build.
   - Cons: couples a deterministic report to AI availability for no reason; a firm that disables AI after a provider hiccup silently loses its weekly cash visibility — a support ticket generator.
3. **Fall back to a different, simpler email template.**
   - Pros: tailored no-AI presentation.
   - Cons: a second template to keep in sync with the first; the conditional block achieves the same with one artifact.

### C. Cadence, recipients, persistence

1. **Weekly job, owner/admin recipients, no persistence (CHOSEN)** — `cash_digest` JobHandler (per-tenant fanout like `portal_digest`); in-app `CASH_DIGEST` notification via `createIfEnabled` (member-mutable in preferences) + one email per owner/admin through the standard pipeline (delivery-logged, `referenceType="CASH_DIGEST"`); digest content lives only in the email/notification.
   - Pros: matches the `PortalDigestHandler` precedent exactly; recipients match who can act on the information (policy edits and exclusions are admin/owner operations); `createIfEnabled` gives opt-out for free; no digest archive entity, table, or retention question — the email *is* the artifact, and the delivery log records that it went.
   - Cons: no in-app "past digests" page (explicitly out of scope; the underlying numbers are recomputable from the debtors page at any time).
2. **Configurable cadence (daily/weekly/monthly) per tenant.**
   - Pros: flexibility.
   - Cons: cadence config UI + validation for a v1 nobody has asked to tune; weekly is the practice-management rhythm (WIP meetings); a later settings field slots into `CollectionsSettings` without migration drama.
3. **Persist digests as entities** (queryable archive, in-app rendering).
   - Pros: history, deep links, trend comparisons.
   - Cons: a new table + retention policy + render-at-read machinery for content that is one query away from recomputation; "digest archive page is NOT in scope" is verbatim in the requirements.

**Decision**: Deterministic `CashDigestData` + AI narration with table-always-wins rendering (A1), numbers-only conditional fallback (B1), weekly owner/admin delivery over existing channels with no persistence (C1).

**Rationale**:

1. **Financial trust is non-negotiable.** The digest is the owner's weekly read on cash. The moment one figure is wrong, the feature is dead. Query-derived tables with AI confined to prose makes the trust-critical part boring and the interesting part safe to be imperfect.
2. **The fallback falls out of the design.** Because narration is additive (A1), removing it is a conditional, not a branch of the system. This is the "cheapest correct option" the requirements asked for — cheaper than skipping, because skipping creates a coupling bug surface (B2's silent loss).
3. **Precedent over invention.** `PortalDigestHandler` already answers scheduling, fanout, and cadence; `NotificationService.createIfEnabled` already answers opt-out; the email pipeline already answers delivery and logging. The digest adds content, not machinery.

**Consequences**:

- Positive: owners get a weekly lockup read whether or not AI is configured; the AI-enabled version adds ranked risks and narrative on top of identical numbers.
- Positive: one metered `AiExecution` per tenant per week — digest AI cost is negligible and predictable.
- Positive: opt-out via existing notification preferences; delivery evidenced in `EmailDeliveryLog` (QA "PASS means observed" has an artifact).
- Negative: no digest history in-app; a firm wanting last month's digest checks their inbox.
- Negative: prose/table disagreement is possible in principle; the prompt constraint and table-authoritative layout bound the damage to an awkward sentence, never a wrong figure.
- Negative: recipients are role-derived (owner/admin), not configurable; a firm wanting the bookkeeper cc'd waits for v2.
- Related: [ADR-327](ADR-327-ai-reminder-drafting-debtor-triage.md) (triage signals consumed here), [ADR-325](ADR-325-collections-domain-dunning-engine.md) (activity summary source; job-queue rails), [ADR-329](ADR-329-trust-aware-collections-extension-seam.md) (trust annotations in the risks section).
