# Day 1 — Firm onboarding polish `[FIRM]` — 2026-07-06

Actor: Thandi (fresh Keycloak login as thandi@mathebula-test.local).

| Checkpoint | Result | Evidence |
|---|---|---|
| 1.1 upload logo + brand colour + Save | PASS | Settings > General > Branding: uploaded 241-byte 200×60 navy PNG (`qa_cycle/test-files/mathebula-logo-navy.png`); Brand Color set `#1B3358`; Save Settings clicked. Logo stored to S3/LocalStack: `org/tenant_5039f2d497cf/branding/logo.png` (presigned URL observed) |
| 1.2 refresh → colour + logo persist | PASS | After reload: Brand Color input = `#1B3358`; sidebar `<img>` src = LocalStack branding/logo.png (2 branding imgs on page). Cross-login persistence to be re-confirmed on Bob's Day 2 login |
| 1.3 Tariffs page pre-seeded | PASS | `/org/mathebula-partners/legal/tariffs` (Finance group nav): "LSSA 2024/2025 High Court Party-and-Party", effective 2024-04-01, 19 items |
| 1.4 tariff values | PASS | Section 4: 4(a) "Attendance at court (per day)" R 7800.00 AND 4(c) "Waiting time at court (per hour)" R 780.00 — both present, all values ZAR |
| 1.5 Add trust account | PASS | Settings > Trust Accounting > Add Account: Name "Mathebula Trust — Main", Standard Bank, branch 051001, acct 12345678, type Section 86 Trust Account, primary, **dual approval enabled** (required for Day 60 Section 86 flow). Saved without validation error |
| 1.6 account in list, R 0.00 | PASS | Settings list: "Mathebula Trust — Main / Primary / ACTIVE / Standard Bank · 051001 · 12345678 / SECTION_86 / Dual". Trust Accounting dashboard: "Trust Balance R 0,00 — Mathebula Trust — Main cashbook balance" |
| 1.7 screenshot | PASS | `qa_cycle/checkpoint-results/day-01-trust-account-created.png` |

Day 1 exit checkpoints: branding persisted (reload-verified; login-cycle check deferred to Day 2 Bob login), LSSA tariffs non-empty, SECTION_86 trust account created — PASS.

Console: 0 app errors (only Keycloak favicon 404, cosmetic, off-app origin).
