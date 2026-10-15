# Phase 84 Ideation — Client & Trust Take-On (2026-07-09 → 2026-07-11)

First ideation log in this directory; no prior file to build on.

## Lighthouse domain
SA small-to-medium law firms (legal-za). Founder has 1–2 reachable pilot firms plus domain-expert
contacts who can source more. Target firms run GhostPractice, AJS/Lexpro, or Excel/bookkeeper setups.

## Decision rationale
- Session opened as an **honest product assessment**: 83 phases, feature surface past incumbents,
  zero users. Verdict: building by momentum, not pull; marginal feature value ≈ 0, marginal value of
  one design-partner firm enormous.
- **Run-vs-sell resolved as a sequence, not a fork**: run as SaaS first; a strategic sale
  (LexisNexis/AJS/Lexpro) only becomes valuable with 10–30 retained paying firms. Pre-revenue
  acquisition ≈ acqui-hire. GTM plan: `.claude/ideas/gtm-design-partner-plan-2026-07-11.md`.
- **Feature freeze after Phase 83** except Phase 84 (take-on/migration) — the one build that
  unblocks pilots ("we move your data for you" is a lie without it).
- Take-on chosen over alternatives because adoption, not capability, is the bottleneck; conspicuous
  absence across 83 phases (only Xero API customer pull + bank-statement CSVs exist).

## Key design preferences (founder-confirmed)
- Generic CSV wizard + column mapping; source-system presets ONLY after real export files arrive
  from design partners (no parsers against imagined formats).
- Opening balances + master data only; no history, no WIP (deferred, likely own phase).
- Trust take-on auditor-defensible: new OPENING_BALANCE credit type, control-total hard block,
  negative balances rejected, exportable report.
- Debtor balances = opening-balance invoices (no AR ledger entity), no send side effects.
- Capability-gated (MANAGE_TAKE_ON, admin not owner-only), not module-gated; re-runnable batches.

## Phase roadmap (emerging)
1. Finish Phase 83 (collections, in flight) → 2. Phase 84 take-on → 3. design-partner-driven fixes
   only. Parked until a paying firm: template extraction, standalone blog monetization, new
   horizontal phases. Future candidates from partner feedback: WIP take-on, Outlook/Gmail +
   calendar sync (identified as #1 practical churn driver), XLSX, source-system presets, batch undo.

## Domain notes
- No opening-balance concept existed anywhere in trust domain pre-84; chk_trust_txn_type CHECK
  constraint is where transaction types live (V85), migration V136 next (verify — Phase 83 landing).
- Trust credits record pre-approved (RECORDED); only debits gate on APPROVE_TRUST_PAYMENT.
- Debtor balance = sum of unpaid invoices; Statement of Account computes, never stores.
- Non-code GA gates: attorney/auditor sign-off on trust module (top), pilot letter template,
  firm-level pricing (~R2.5–5k/month), continuity one-pager. Auditors = sleeper sales channel.
