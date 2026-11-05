# 636A — default `test:e2e` (E2E-DEFAULT), Phase 89 exit gate

**Status:** PASS

| Field | Value |
|---|---|
| Command | `cd frontend && NODE_OPTIONS= E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 PLAYWRIGHT_JSON_OUTPUT_FILE=/tmp/636A/G1-default.json pnpm test:e2e --reporter=list,json` (no `KGOSI_EMAIL`, no `CI`) |
| Stack | G1: the same fresh stack as `lifecycle.md` (`1034b6af8`, tenant `tenant_7d218705360b`, Flyway 11/3, seed Complete!) |
| Baselines | n/a. The default suite has no screenshot assertions |
| Order on stack | lifecycle → depth-ii → **default** (the "same fresh stack" in the gate vocabulary) |
| Start / end (SAST) | 20:12:58 / 20:14:02 (1.0 min). Outside 00:00–02:00 ✓ |
| Result (JSON stats) | expected 165 / unexpected 0 / skipped 14 / flaky 0 (duration 62 s) |
| Result (list summary) | `14 skipped` / `165 passed (1.0m)` |
| Exit code | 0 |

## Baseline comparison

| | passed | failed | skipped | did not run |
|---|---|---|---|---|
| Plan floor | ≥ 160 | 0 | 17 | — |
| Current main (PR #1739 fresh stack / 634E run 8) | 165 | 0 | 14 | 0 |
| **This run (G1)** | **165** | **0** | **14** | **0** |

No test's outcome differs from current main. The totals are equal and the skip set matches the 14 known skips one for one (below).

## Named specs

| Spec | Result |
|---|---|
| `customers/customer-data-protection.spec.ts:47` "Download customer data export" | ✓ |
| `customers/customer-data-protection.spec.ts:77` "Anonymize customer preview shows entity counts" | ✓ |
| `customers/customer-data-protection.spec.ts:108` "Execute anonymization" | ✓ |
| `customers/customer-data-protection.spec.ts:150` "ANONYMIZED customer is read-only" (also green) | ✓ |
| `customers/customer-data-protection.spec.ts:165` "Cannot transition out of ANONYMIZED via API" (also green) | ✓ |
| `projects/project-tasks.spec.ts:277` "8. Comment notification deep-links to the comment in the task sheet (G88-M6)" | ✓ |
| `projects/project-tasks.spec.ts:322` "9. Matter comment notification deep-links to the highlighted comment on the Comments tab (B7)" | ✓ |

## Skips (14, exactly the known-skip list)

| Test (JSON file:line) | Count | Reason |
|---|---|---|
| `customers/customer-lifecycle.spec.ts:157` DORMANT -> OFFBOARDING, `:178` OFFBOARDING -> OFFBOARDED | 2 | `test.fixme(` at `:158` / `:179` (brief lines) |
| `settings/roles-capabilities.spec.ts:53` Create, edit and delete a custom role | 1 | `test.fixme(` at `:54` |
| `portal/portal-data-isolation.spec.ts:36`, `:48` | 2 | `KGOSI_EMAIL` not set (by design for the default run; scored separately on G2 with it set: 2 passed) |
| `auth/auth-change-password.spec.ts:35` keycloak: Account & Security … | 1 | KC-only |
| `admin-audit-30day/admin-audit-lifecycle.spec.ts:33, 52, 70, 88, 108, 125, 143, 159` | 8 | `test.skip(true, SKIP_REASON)` |

None of the 14 is a serial "did not run": the last result is `skipped` for each, not `notrun`.

## Post-run state check (#1739 regression guard)

Read-only SELECT on `tenant_7d218705360b.projects` after the default run:

```
Website Redesign|ACTIVE
E2E closure override 5rld|CLOSED
```

The seed matter `Website Redesign` is still ACTIVE. `matter-closure-audit-tab.spec.ts` created and closed its own run-unique `E2E closure override <RUN_ID>` matter, so the #1739 fix holds. These 2 rows are the whole table after all three G1 suites (`raw/stacks.txt` → "G1 projects after default").

## Failures

None.

## Raw output

`raw/G1-default.txt`, `raw/G1-default.tsv`, `raw/G1-default.json`

## Tree hygiene

See `README.md` § Tree hygiene. The default suite writes no tracked files.
