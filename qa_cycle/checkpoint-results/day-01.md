# Day 1 — Firm onboarding polish — Cycle 2026-07-12

**Actor**: Thandi (Owner)

| Checkpoint | Result | Evidence |
|---|---|---|
| 1.1 upload logo + brand colour + Save | PASS | Logo `qa_cycle/mathebula-logo.png` (145 B PNG 50×50) uploaded → S3 presigned URL `localhost:4566/docteams-dev/org/tenant_5039f2d497cf/branding/logo.png`; brand colour set `#1B3358`; Save Settings clicked |
| 1.2 refresh → colour + logo applied | PASS | After reload: colour input persists `#1B3358`; sidebar renders logo img from S3; computed-style scan finds `rgb(27,51,88)` on sidebar accent elements (org-name span + active-item indicator bar) |
| 1.3 Tariffs via Finance nav | PASS | `/org/mathebula-partners/legal/tariffs` — "Tariff Schedules … 1 schedule: LSSA 2024/2025 High Court Party-and-Party, effective 2024-04-01, 19 items" |
| 1.4 LSSA tariff values | PASS | Expanded schedule shows "Waiting time at court (per hour) R 780.00" AND "Attendance at court (per day) R 7800.00" — all ZAR |
| 1.5 trust account form | PASS | Settings > Trust Accounting → Add Account dialog: Mathebula Trust — Main / Standard Bank / 051001 / 12345678 / SECTION_86 ("Section 86 Trust Account" option) → Create |
| 1.6 account saved, R 0.00 | PASS | No validation error; settings list shows "Mathebula Trust — Main, Primary, ACTIVE, Standard Bank · 051001 · 12345678, SECTION_86"; module page `/trust-accounting` shows "Trust Balance R 0,00 — Mathebula Trust — Main cashbook balance" |
| 1.7 screenshot | PASS | `qa_cycle/checkpoint-results/day-01-trust-account-created.png` |

## Day 1 exit checkpoints

- Firm branding (logo + colour) persists across logout/login: PASS — cookies cleared, fresh KC login as Bob: sidebar shows logo (S3 branding/logo.png) + `rgb(27,51,88)` accent, user "Bob Ndlovu"
- LSSA tariff table pre-populated, non-empty: PASS (19 items)
- Trust account created under Section 86 basis: PASS

## Observations (non-blocking)

- Same harness quirk as Day 0: trusted clicks dropped; synthetic events + `form.requestSubmit()` used, outcomes verified via UI state/DB-visible effects.
- `/org/{slug}/legal/trust` is 404 — QA route guess, not a scenario step; actual module route is `/org/{slug}/trust-accounting` (matches nav). Not a gap.
