# Day 85 — Firm final closure paperwork `[FIRM]` — 2026-07-06

**Actor**: Thandi Mathebula (existing firm session).

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 85.1 | PASS | Matter RAF-2026-001 (Closed) Work > Documents still lists `matter-closure-letter-dlamini-v-road-accident-fund-2026-07-06.pdf` (1.6 KB) and `statement-of-account-…pdf` (5.0 KB), plus FICA ×3 and medical-evidence ×2 |
| 85.2 | SKIPPED (conditional not met) | "If tenant workflow requires it, generate a final closing letter" — no tenant workflow requirement exists; the Day 60 closure letter is the closing correspondence. Product does expose Generate Document (template picker) on the closed matter if needed |
| 85.3 | PASS (cycle-local product shape) | DB (read-only): `retention_clock_started_at = 2026-07-06 15:19:26` persists on CLOSED matter; active MATTER retention policy 1825 days (5y) / MATTER_CLOSED / ARCHIVE unchanged since closure. Scenario's "end_date ≈ today + 5y − 25d" doesn't apply literally in a single-day cycle, and computed deletion date remains blank pending org `legal_matter_retention_years` (Day 60 note) |
| 85.4 | PASS (matter activity feed = the shipped audit surface) | Matter Activity tab, lookback **90 days**: full history renders (matter created → info requests → time entries → tasks incl. automation follow-ups → court date → closure → doc generation → portal downloads). **Filter by actor** combobox lists Bob Ndlovu / Sipho Dlamini / Thandi Mathebula — i.e. firm users AND portal contact. Actor=**Sipho Dlamini** → exactly his portal actions: `portal.document.downloaded` ×2 (Day 61), `portal.request_item.submitted` + `portal.document.upload_initiated` (Days 46/4), `portal.invoice.paid` (Day 30). Note: raw event-key copy (LZKC-019, already logged) and `portal.invoice.paid` "invoice" wording (LZKC-009 family) |
| 85.5 | PASS | 📸 `day-85-firm-audit-filtered.png` (actor=Sipho filtered feed) |

## Day 85 day-level checkpoints

- Matter retention row persists correctly: **PASS** (clock + active 5y MATTER policy; computed date pending org setting — Day 60 product-shape note)
- Audit log filters by actor for BOTH firm users AND portal contacts: **PASS**

## Observations (not new gaps)

- "View audit" control in Closure history does not visibly navigate/open anything when clicked (tested real-coordinate click). The Activity tab supplies the audit view, so no functional loss — folded into LZKC-019's audit-surface polish rather than a new row.

## Gaps

None new.
