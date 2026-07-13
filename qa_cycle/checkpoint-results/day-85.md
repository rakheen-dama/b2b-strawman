# Day 85 — Firm final closure paperwork `[FIRM]` — cycle 2026-07-12 (run 2026-07-13)

**Actor**: Thandi Mathebula (firm :3000 — standing session still valid).

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 85.1 | PASS | Matter RAF-2026-001 (Closed) Work > Documents lists `matter-closure-letter-dlamini-v-road-accident-fund-2026-07-13.pdf` (2.2 KB) and `statement-of-account-dlamini-v-road-accident-fund-2026-07-13.pdf` (5.5 KB) — byte sizes match Day-60/61 records — plus FICA ×3 (fica-id/address/bank) and Day-46 medical evidence |
| 85.2 | SKIPPED (conditional not met — prior-cycle precedent) | "If tenant workflow requires it…" — no tenant workflow requires a final closing letter; the Day-60 closure letter is the closing correspondence. Product does expose **Generate Document** on the closed matter if a firm wanted one |
| 85.3 | PASS (cycle-local product shape, unchanged from prior cycle) | DB (read-only): `projects.retention_clock_started_at = 2026-07-13 01:15:27` persists on CLOSED matter; active **MATTER / 1825 days (5y) / MATTER_CLOSED / ARCHIVE** retention policy unchanged since closure (created at closure moment 01:15:27); CUSTOMER 1825d + AUDIT_EVENT 2555d policies also active. Scenario's "end_date ≈ today + 5y − 25d" doesn't apply literally in a compressed cycle, and the computed deletion date remains blank pending org `legal_matter_retention_years` (still NULL — Day-60 period-unconfigured banner shape) |
| 85.4 | PASS (matter activity feed = shipped audit surface, per precedent) | Matter Activity tab, lookback **90 days**: unfiltered feed renders full history (portal downloads → SoA/closure-letter generation → "Thandi Mathebula closed the matter" → Bob's task CANCELLED/DONE transitions → REQ-0003 accepts/submits → task IN_PROGRESS/DONE progression), friendly copy throughout — **LZKC-019 holds** ("Closed by Thandi Mathebula" in Closure history — LZKC-014 holds). **Filter by actor** combobox lists Bob Ndlovu / Sipho Dlamini / Thandi Mathebula — firm users AND portal contact. Actor=**Sipho Dlamini** (+ Load more ×2) → exactly his 13 portal actions: downloads ×2 (Day 61), REQ-0003 submits+uploads ×4 (Day 46), **"Sipho Dlamini paid fee note INV-0001"** (Day 30 — "fee note" wording, no LZKC-009 "invoice" leak here this cycle), REQ-0001 FICA submits+uploads ×6 (Day 4). Engagement-letter accept absent from the *matter* feed because proposal events are proposal/client-scoped, not matter-scoped — same shape as prior-cycle 85.4 PASS (and the acceptance IS correctly attributed elsewhere: portal trail Day 75 evidence + firm proposal audit Day 10) |
| 85.5 | PASS | 📸 `day-85-firm-audit-filtered.png` (actor=Sipho filtered 90-day feed) |

## Day 85 day-level checkpoints

- Matter retention row persists correctly: **PASS** (clock + active 5y MATTER policy; computed date pending org setting — known product shape)
- Audit log filters by actor for BOTH firm users AND portal contacts: **PASS**

## Console

0 firm-side JS errors during Day 85 flows.

## Gaps

None new.
