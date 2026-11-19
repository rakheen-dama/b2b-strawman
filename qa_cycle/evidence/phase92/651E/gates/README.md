# 651E — gate evidence

Slice head observed: `3c5cebcd0` (PR #1812). Stack: `compose/scripts/e2e-up.sh` from the 651E worktree on 2026-11-19, fresh volumes for each suite. The e2e tenant `tenant_7d218705360b` ran 28 tenant migrations, including V28 "phase92 drop lpff rates" (`success = t`). Ports: mock-auth frontend :3001, backend :8081, mock IDP :8090, Postgres :5433.

Gate cadence (founder ruling 2026-11-18): 651E is the **stage-8 checkpoint**. On a stack built from the slice head the order is: E2E-LEGAL (legal-lifecycle and legal-depth-ii, each on a fresh stack) and OBS. BE-VERIFY runs last, on the final head.

## E2E-LEGAL — PASS (at baseline)

See [`e2e-legal.txt`](e2e-legal.txt).
- legal-lifecycle: **105 passed** (3.0m).
- legal-depth-ii: **40 passed, 4 skipped** (2.0m).

Both match the post-651C baseline (105 / 40 + 4).

## OBS — PASS (a–f)

- Script: [`obs.cjs`](obs.cjs), Playwright library, headless Chromium, Alice / owner.
- It ran on the lifecycle stack after the suite, so the Day-60 investments exist and check c has real rows.
- Result: [`obs/obs-result.json`](obs/obs-result.json). Screenshots: `obs/d*.png`. Register preview: `obs/c-register-preview.html`.

| # | Observation | Result |
|---|---|---|
| a | The two removed rate routes, owner JWT: `GET` and `POST /api/trust-accounts/{id}/lpff-rates` | both **404**, ProblemDetail detail "No static resource …" — PASS |
| b | Kept routes still served: `GET /api/trust-accounts`, `GET /api/trust-accounts/{id}`, `GET /api/trust-integrity`, `GET /api/report-definitions/investment-register` | all **200** — PASS |
| c | Investment register, executed for each of the 3 trust accounts | **4 rows** in total, **0** rows carry `applicableLpffRate`. Each row has the 18 keys in `obs-result.json` (basis, fund share and gross interest included). The preview HTML is 200 with no "LPFF Rate" header and no `applicableLpffRate`. The PDF export is 200, `%PDF`. — PASS |
| d | Browser: trust accounting, investments, other trust creditors, trust reports, and Settings › Fees & Trust › Trust accounting | all 200, no error page, no "LPFF rate" copy, 0 page errors, 0 console errors. The settings tab shows Trust Accounts, Approval Settings and Reminder Settings, with no LPFF rate section (`obs/d5-settings-trust-tab.png`). — PASS |
| e | Database, read-only ([`db-checks.txt`](db-checks.txt)) | `lpff_rates` and `interest_runs` give `to_regclass` NULL. `flyway_schema_history` has V28 "phase92 drop lpff rates" with `success = t`. 2 investments. 0 `lpff_rate` audit rows on this tenant, because the e2e seed records no rate. — PASS |
| f | Backend log ([`backend-log-excerpt.txt`](backend-log-excerpt.txt)) | `Started BackendApplication`. The only `lpff` mention is the Flyway line migrating to V28. **0** ERROR lines. The WARN lines are PDF fallback-font notices, startup bean notices and the seeded draft-invoice tax-number notice. — PASS |

Note on e: a fresh tenant creates `lpff_rates` in V1 and V28 drops it. `InterestRunRemovalTest` covers the data-bearing path in-test: a V27 scratch schema holding 2 rate rows migrates to V28, and a second run of the V28 body is a no-op.

## Environment note

The first `e2e-up.sh` failed because e2e-postgres hit `initdb: … No space left on device` in the Docker VM. I pruned unused Docker build cache to 5 GB (`docker builder prune --keep-storage 5GB`; it reported 0 cache entries in use) and rebuilt the stack fresh. No container, image or volume belonging to other work was touched.
