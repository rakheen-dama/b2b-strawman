# Phase 93 Ideation — Competitor review (AJS) → Take-On rewrite (2026-11-19 → 2026-11-20)

Builds on `gtm-design-partner-plan-2026-07-11.md` (take-on = the pilot close). Output: `requirements/claude-code-prompt-phase93.md`; Phase 84 marked SUPERSEDED.

## Competitor review — AJS (ajs.co.za), read 2026-11-19
- Products: Practice Mgmt & Trust Accounting (Startup 2 users / Express 2–20 / Pro ≤50 / Enterprise 50+; no prices published), FLOW (workflow), XpressDox (doc automation), Firefly (conveyancing cost calculator app), C3 (collections call centre), SBA (outsourced bookkeeping — their real moat is people).
- AJS has, Kazi lacks: business accounting/GL/creditors in one system; auditor portal (all tiers); e-payment requisitions; auto bank feeds (Pro only); mobile app; E4/Lexis/nCino integrations; bulk import (**Enterprise 50+ only**).
- Kazi ahead: AI/MCP (AJS AI = blog thought leadership only, no product feature); POPIA tooling (AJS POPIA = advice blog + KYC doc storage + encrypted backups; Kazi has DSAR/export/anonymisation, processing register, PAIA manual, retention); trust depth (AJS site never mentions LPC/s86/LPFF/investments); real FICA verification adapters; price transparency opportunity.
- Positioning line: "their blog tells you what to do; Kazi does it." Honest-comparison guardrails still apply (no bashing).
- Stance to make explicit: business books live in Xero; Kazi does not build a GL (legal-advisor confirmation still wanted).

## Decision rationale
- The gap that decides switching is take-on, not module parity. 84 was specced July, never built, and its scouting is void after Phases 86/87/92 (control total ≠ EQ-2, registry replaced CREDIT_TYPES, ledger card holds no balance, opening credit would double-count as outstanding deposit, Xero import lands PROSPECT, SENT path fires email/payment link/Xero/automation, dormancy job). → New Phase 93, keep 84's product decisions.
- Founder choices: trust scope = all creditor groups (+ outstanding reconciling items); clients ACTIVE with FICA attestation (not re-onboarding); generic CSV **+ XLSX**, no presets (no real AJS export in hand).
- Sequencing decided 2026-11-20: **92 → 93 → 91** (amends Phase 92 decision #1). Phase 91 trust scope needs a refresh for Phase 92 surfaces before its /architecture.

## Legal rulings (Fable advisor 2026-11-20) baked in
- Same-firm CDD reliance OK on software change; attestation must point to retained CDD records (s22/s23/s24); risk-driven review date; UNRATED → review queue.
- Trust records 7 yrs (Rule 54.9.1); debit creditor = hard block + 54.14.11 duty shown; month-end cutover default; partner/director sign-off; s86(4) needs instruction ref + 5% Fund status; unidentified deposits keep original date (s87(4) clock).
- Sign-off still wanted: attestation wording/who (A1), 54.9.2 / s24(3) archive question (A2), mid-month cutover (A3), override never (A4), accrued s86(4) interest (A5).

## Future-phase candidates surfaced
1. **Beneficiary bank-details register + changed-details hold** (payment-instruction fraud; Kazi captures no beneficiary bank details; dual approval defaults off). Strong auditor pitch.
2. **Auditor read-only access** (AJS parity + GTM auditor channel).
3. Source-system presets once a real export exists. 4. POPIA s22 breach register. 5. Bank feeds (later).
- Standalone bug to file: `trust_account.updated` audit omits old/new account number (`TrustAccountService.java:310-325`).
- Marketing idea: transfer-cost calculator on kazi.africa/legal (Firefly-style SEO lead magnet).
