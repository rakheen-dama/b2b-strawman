# Phase 92 ideation — 2026-11-16 (trust accounting correctness)

Builds on `phase89-ideation-2026-11-03.md` (trust defects are go-live-blocking) and `phase91-ui-modernization-ii-ideation-2026-11-12.md`. Input: `Kazi trust accounting findings and proposal.md` (D1–D12), a code trace, a Fable legal-advisor report, a Perplexity cross-check. Output: `requirements/claude-code-prompt-phase92.md`.

## Lighthouse domain
SA small/medium law firms, design-partner take-on (Phase 84) next. Bar: a firm's trust auditor finds nothing.

## Key insight
The interest bug survived Phases 88, 89 and the G89 batches because nothing asserts the books balance. Fixing interest alone leaves the class open. Framing chosen: "trust books provably right" — one equation, asserted in tests and shown to the firm.

## Decisions (founder, 2026-11-16)
- Sequence: Phase 92 → 91 → 84. Trust first; Phase 91 redesigns trust pages on the corrected model.
- Posting guard = slice 1 of the phase (not a standalone PR).
- All eight epics in one phase; investments not split out.
- Legal basis: Fable advisor + Perplexity with founder sign-off; open auditor points carried as an assumptions register, not a gate.
- No real tenant has posted a run → no remediation tooling, reset environments.
- Calculated interest run removed outright (no estimator).
- Invariant: tests + visible integrity check; no hard block.
- Exit bar: an observed balanced month in the browser on a fresh stack + three suites. No 90-day re-walk.
- Fund ledger transient debit: allow and flag. The founder pointed to the LPFF June 2020 notice; it treats recoverable charges as a set-off against interest (net paid to the Fund) and is silent on timing, so it supports mirroring the bank and gives no basis for blocking or a float.

## Considered and rejected
- Interest-only fix (no systemic check). Full trust audit before any fix (slower start). Hard-block invariant (a latent defect would stop all trust posting). Keeping the run as an estimate or repurposing it for s86(4).

## Design preferences that emerged
- Kazi mirrors the bank statement; no rule may stop a firm recording what the bank did.
- Statutory figures are constants, never firm settings.
- Prefer removal over keeping dormant code.
- Exit gates are browser-observed scenarios committed as regression specs.

## Domain notes
- s86(5): general (s86(2)) and firm-initiative (s86(3)) interest is 100% the Fund's; only s86(4) pays the client (95/5). Bank sweeps monthly (compulsory since 1 Sep 2025).
- Statement layout of interest/sweep is unpublished and differs by bank → design must work for gross-then-swept, net, and not-shown.
- Invested money stays a trust creditor (Rule 54.14.8, 54.15.1); dropping it at placement is a known inspection finding (De Rebus Sept 2024).
- Unidentified deposits: itemised, aged, payable to the Fund after the second annual closing (s87(4)); no fees from them.
- Rule 54.15 list is monthly, by name. Rule 54.28 wants gross interest and gross charges per account even with no claim.
- Recoverable bank charges = LPFF list; VAT vendors deduct net of VAT; shortfall funded from business.
- IT3(b): a SAICA FAQ says the practitioner submits for s86(4) interest (the findings doc said the bank) — unconfirmed, keep the per-investment figures.
- Still for a trust auditor: transient Fund ledger debit vs Rule 54.14.9; each bank's s86(4) presentation; who files IT3(b).
- Not reached in primary text: IRBA guide and letters (403), lpc.org.za consolidation, any bank statement specimen.

## Tooling notes
- Perplexity `perplexity_research` timed out twice; `perplexity_ask` with high context worked. PDFs: no poppler/pypdf locally; a PDFKit Swift script extracts text.

## Roadmap
Phase 92 (this) → Phase 91 UI modernization II → Phase 84 take-on. Later candidates: IT3(b) export, s87(4) Fund submission, LPFF charge-refund claim, bank feeds.
