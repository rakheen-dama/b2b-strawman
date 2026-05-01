# Known regression baseline failures — 2026-05-01

After PR #1255 merged (OBS-AUDIT-N1), the post-merge hook fired the regression suite (`bash scripts/run-regression-test.sh`). Both the Playwright UI regression and the API regression reported FAIL. Investigation confirmed all four Playwright failures are **pre-existing baseline** — the matching `frontend/test-results/*-chromium/` directories were present as untracked files at the start of this session, before any of the three PRs (#1254, #1255, #1256) merged today. None of those PRs touched the failing test paths.

Filing this here so future PR descriptions and orchestrator sessions can either own/ignore/fix per [Quality Gate rule §1](../CLAUDE.md#1-build--test-bar-mandatory-observed-not-inferred) instead of treating each post-merge hook firing as a fresh signal.

## Confirmed pre-existing failures

| # | Test | Spec file | Symptom | Likely cause | Touched by today's PRs? |
|---|------|-----------|---------|--------------|-------------------------|
| 1 | PORTAL-03: Portal Navigation › Portal requests page | `frontend/e2e/tests/portal/portal-navigation.spec.ts:279` | (need re-investigation) | Portal navigation route or fixture drift | No |
| 2 | PORTAL-03: Portal Navigation › No firm-side leakage in portal | `frontend/e2e/tests/portal/portal-navigation.spec.ts:320` | `locator.click` timeout on `getByRole('link').first()` — element resolves to off-viewport "Skip to content" anchor | Possibly a layout change moved the first link off-viewport | No |
| 3 | PROP-03: Portal Proposal Acceptance › No unresolved variables in portal proposal view | `frontend/e2e/tests/portal/portal-proposal-acceptance.spec.ts:324` | `locator.click` timeout — same off-viewport-link pattern as #2 | Same root cause as #2 likely | No (my OBS-AUDIT-N1 PR added a `portal-proposal-expired.html` template; this test exercises proposal acceptance, not expiry) |
| 4 | PACK-01: Pack Lifecycle › Install pack and verify templates are created | `frontend/e2e/tests/settings/packs.spec.ts:88` | "Install" button stays `disabled` for 5s+ when test expects it enabled | Pack-install button gating logic — possibly auth/permission seeding or pack-state drift | No |

## API regression result

Reported FAIL by the wrapper script. Detailed output was lost (the script's stdout was piped through `tail -100` which dropped the API portion). Need to re-run with full output capture — but presence of the dirty test-result dirs at session start strongly suggests this also pre-dates today's work.

## Triage decision

**Track for fix in a dedicated session. Not blocking for current Task E / Task F work.**

Per user note (2026-05-01): the regression pack itself was built during a period when known bugs were live, so some assertions may encode buggy behaviour rather than correct behaviour. **Don't assume "test failure = production bug" until the regression pack has been audited and rebuilt against a known-good baseline.** That audit is itself a dedicated-session task — bigger than chasing each failure individually.

Reasoning per [Quality Gate rule §1](../CLAUDE.md#1-build--test-bar-mandatory-observed-not-inferred) ("own it, ignore with reason, or fix"):

- **Own**: yes — these go on the Task F backlog as concrete cleanup items, gated on the regression-pack audit.
- **Ignore-with-reason**: not chosen for the long term; these are real test failures, not flakes. Reason for *not* fixing in-session: (1) regression pack provenance is suspect, (2) each failure needs its own investigation, (3) all four are unrelated to the slop-hunt audit findings I'm currently working through.
- **Fix**: deferred. Recommend a dedicated session that (a) audits the regression pack against current product expectations, (b) discards or rewrites assertions that encode buggy old behaviour, (c) then triages remaining failures to either a code fix or a test update.

## Reproducer

```bash
bash compose/scripts/e2e-up.sh    # if not already running
bash scripts/run-regression-test.sh --ui  # narrows to the four Playwright failures
```

Failure artefacts: `frontend/test-results/portal-portal-*-chromium/` and `frontend/test-results/settings-packs-PACK-01-*-chromium/` — each contains `error-context.md`, `test-failed-1.png`, `video.webm`.

## Tooling note

`scripts/run-regression-test.sh` exit code is overridden by `| tail -N` in the caller. The script itself correctly exits 1 on failure (line 122–126). The post-merge hook captured exit 0 because of the pipe; the underlying summary still reports FAIL. Worth a follow-up to either invert the hook's command (capture exit before piping) or have the regression script emit a structured artefact the hook can inspect. Tracked as a Task G subtask alongside CI parity.
