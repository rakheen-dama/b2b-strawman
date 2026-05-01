# Slop hunt — PR #1248: qa(cycle 2026-04-30 continuation): days 46/60/61/75/85/88/90 + 3 fixes

**Batch**: E — bookkeeping/test-fix
**Reviewed**: 2026-05-01
**Verdict**: NIT (one self-introduced state amendment carry-over; otherwise clean)

## PR description vs diff

Description claims continuation of cycle from #1244 with 3 underlying code fixes (#1245–#1247) merged via separate PRs. Diff matches: 1 scenario amendment (2-line, Day 46), 8 day-NN.md checkpoint files, ~30 evidence files (PNG, JSON, MD, PDF), 5 fix-specs (OBS-2105/2106/2107), and a status.md update. No production code touched. Scope honest.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | LOW | Carry-over scenario amendment | `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md:747-750` (Day 46.4-46.5) | Trust balance R 70k → R 71k carry-over (mirror of PR #1244 Day 45 amendment). Same Finding #1 as PR #1244 — a test-state contamination (Day 14 OBS-1101 verify R 1,000 deposit) is rolled forward into the assertion. Consistent with PR #1244 but inherits the same arithmetic-amendment-not-product-fact pattern. | Same recommendation: roll back the OBS-1101 verify deposit between cycles, or add a fixture-reset step to the scenario. |
| 2 | LOW | E.15 was claimed PASS without backend verify | `qa_cycle/checkpoint-results/day-90.md` (PR 1248 Day 90) — "E.15: Test suite gate" implicit in cycle 21 frontend/portal claims | Day 90 PASS-WITH-NOTES rolls up exit gates including E.15 by reference (cycle 21). Cycle 21's report claimed E.15 PASS based on frontend + portal lint/test/build only — backend `./mvnw verify` was deferred to cycle 22. PR #1249 explicitly carries E.15 as "PASS for all sub-gates run; backend verify in flight at writeup time" — and PR #1250 is the corrective for the regression that fresh verify caught. Honest in retrospect. | Already addressed by PR #1250 + the new merge-gate hook. No further action. |

## (Bookkeeping PRs only) Scenario amendment audit

Only 1 scenario file edit in this PR:

| Checkpoint | Amendment | Classification |
|---|---|---|
| 46.4-46.5 | Trust balance R 70k → R 71k + 3 deposits not 2 | LEGITIMATE_CARRY_OVER (mirrors Day 45.4-45.5 amendment from PR #1244; same OBS-1101 test-state cascade) |

No other scenario edits. Day 60/61/75/85/88/90 expectations are NOT amended — they are walked end-to-end and reported as PASS / PASS-WITH-NOTES with explicit defect filings (OBS-2105/2106/2107).

**No DISPOSED_BUG amendments found.**

## (Bookkeeping PRs only) Evidence audit

Sampled 5 PASS claims for evidence backing.

| Day | Claim | Evidence backing | Verdict |
|---|---|---|---|
| Day 60 | Closure happy-path: Active → Closed, all 9 gates green, SoA + closure-letter generated | Backend log lines with PID 99738 + request UUID `4db54991-...` + 12 PNG screenshots + actual SoA PDF saved as `qa_cycle/evidence/day-60/statement-of-account-firmside.pdf` (5489 bytes, MD5 `52b1a3227eca8a6ee8228cfe8f1d9060`) | Strong — full back-end + S3 + UI evidence |
| Day 60 | OBS-2106 portal email FAIL claim | Mailpit total = 13, latest is `Trust account activity 19:21:36`; closure committed at `19:31:42`; backend log filtered for `template=portal-document-ready` returns ZERO entries | Strong — observed absence with timestamps |
| Day 75 | Weekly digest isolation symmetry | Mailpit IDs `825imn2X9feksRFZX2GoRr` (Sipho) + `azizA3PXjsKnEFQLfTTAj7` (Moroka), per-message body content quoted with regex isolation checks | Strong — concrete message IDs + content excerpts |
| Day 85 | OBS-2106 VERIFIED on structural-fix-live criterion | Backend log line `PortalDocumentNotificationHandler.process entered: tenant=tenant_5039f2d497cf, template=statement-of-account, ...` from `.svc/logs/backend.log` at 20:29:53 | Strong — log line is the structural-fix proof; PR criterion documented up-front |
| Day 88 | Activity feed wow moment | Captured 88+ events to `firm-activity-feed.json` (179 lines), 3 portal feed JSONs; specific event highlights cited from each | Strong — JSON artefacts on disk |

Evidence quality is consistently high. Mailpit IDs, backend log lines with timestamps and PIDs, S3 PDF byte-counts and MD5 hashes, DB UUIDs, JSON evidence dumps — these are not "visually confirmed" claims.

One soft spot: PNG screenshot capture was reportedly broken in the Playwright MCP environment for some of these days (filed as ENV-001 sister); DOM/JSON evidence substitutes for PNG. The substitution is honest and documented.

## Notes

This PR carries 3 NEW defects discovered during the continuation walk (OBS-2105 cosmetic header layout, OBS-2106 closure-pack email pipeline, OBS-2107 Flyway DEFAULT drift on existing tenants). Each is filed with severity, root cause, and a concrete fix path. OBS-2105/2106 fixed via PRs #1245/#1246, OBS-2107 via #1247. OBS-2106 even has a labelled "structural-fix-live" verification criterion that allows the verify to pass without an end-to-end Mailpit observation when the email is gated by a separate (newly-discovered) issue (OBS-2107). That layered verification is honest about what was proven.

The cycle continuation correctly defers the backend `./mvnw verify` to a later cycle (gating PR #1249/1250). This is the same root cause as the Quality Gate rule #5 ("test scoping") that the new hook + lockdown were built to enforce — this PR landed before that lockdown.

The status.md change correctly flips from in-progress to `ALL_DAYS_COMPLETE`. The handoff document tracks one open-medium gap (OBS-2107) which was subsequently closed via PR #1247.
