# Phase 83 Ideation — Collections & Cash Intelligence (2026-07-09)

First ideation log in this directory (earlier strategy notes: `mcp-plugin-strategy-2026-06-14.md` referenced from memory; prior phases were ideated without logs here).

## Lighthouse domain
SA small-to-medium law firms; fork-neutral foundation bias. Lockup (WIP days + debtor days) is the #1 cash pain for SA professional-services firms — collections is revenue-protecting and universal across legal/consulting/accounting forks.

## Prompt & interpretation
Founder invoked `/ideate accounting ai`. Three readings offered:
1. **AI on the money layer** (fork-neutral collections/cash intelligence) — **CHOSEN**
2. accounting-za vertical depth (SARS/tax-season packs + accountant AI skills) — deferred, still the smallest fork gap per vertical strategy
3. AI-assisted Xero reconciliation — deferred, too narrow (only Xero-connected firms)

## Decision rationale
- The revenue engine ends at `SENT`: no dunning, no debtor watching, no owner cash narrative. Verified by scout: zero dunning code, no OVERDUE status (derived in `InvoiceAgingReportQuery`), Invoice has no amountPaid (all-or-nothing PAID).
- Every ingredient exists: aging report (P19), email + delivery log + payment-link-in-email (P24/25), Xero payment pull (P71), AI skills/gates/metering (P72/74), job queue (P75), weekly-digest precedent (`PortalDigestHandler`, P68).
- Month-end WIP-to-bill run explicitly excluded — overlaps Phase 70 Billing Assistant + MCP `fee-note-run`; earmarked as **Phase 84**.

## Founder decisions (constrain the build)
- **Scope bundle**: dunning engine + AI layer + weekly cash digest (all three; digest is the owner-facing "wow" surface).
- **Gated always**: every reminder through `AiExecutionGate` with batch-approve UX; NO auto-send code in v1 (later policy toggle; don't preclude, don't build). Rationale: wrongly-worded auto-email to a client = trust-destroying failure mode.
- Fork-neutral core; legal-za trust awareness only via no-op-default SPI (suggest fee transfer when trust funds available, via `ClientLedgerService`; mirrors `TrustBoundaryGuard` tolerance).
- Reminders reuse invoice-email recipient/template/payment-link pattern; no new channels, no portal changes.

## Key design preferences (recurring founder patterns)
- Scout the codebase before speccing (Phase 80 lesson — spec embeds a verified scouting note).
- Gates over autonomy for anything client-facing; batch-approve to keep it usable.
- Deterministic core + AI on top (digest numbers from queries, AI narrates only).
- Smallest correct version: fixed-shape policy stages (configurable timing only), no template editors, no partial payments.

## Phase roadmap (emerging)
- **83**: Collections & Cash Intelligence (this spec)
- **84** (natural next): month-end WIP-to-bill run (AI billing sweep across projects)
- Later candidates: auto-send policy toggle; accounting-za vertical depth (option 2 above); MCP exposure of collections + consumer skill; Xero reconciliation intelligence (option 3).

## Domain notes
- SA firms: "lockup" is the operative partner-level metric; final-demand tone is regulated territory for attorneys (letters of demand have legal weight) — one more reason human-gated sending is right for legal-za.
- Payment ground truth is triple-sourced: gateway webhooks, Xero pull, manual record-payment — cancellation-on-payment must cover all three routes.

## Output
`requirements/claude-code-prompt-phase83.md` (ADRs 325–329 reserved; latest migration V132 — resolve next V at build time).
