# Verify Markers — Quality Gate Contract

Per `CLAUDE.md` §1 and §10, every PR landing on `main` must have a recent successful full-suite verify for each code area it touches (backend / frontend / portal; `packages/` counts as frontend + portal). Documentation-only PRs need no area marker — see the exception below — but every PR still needs green CI and the review audit trail.

The pre-merge hook (`.claude/hooks/pre-pr-merge-gate.sh`) blocks `gh pr merge` against `main` unless the appropriate marker file in this directory shows a clean, recent run.

## Required markers (per touched area)

| File | Required when PR touches | Run command |
|---|---|---|
| `verify-backend.json` | `backend/` | `cd backend && ./mvnw verify` |
| `verify-frontend.json` | `frontend/` | `cd frontend && pnpm install && pnpm run lint && pnpm run build && pnpm test` |
| `verify-portal.json` | `portal/` | `cd portal && pnpm install && pnpm run lint && pnpm run build && pnpm test` |

A PR touching multiple areas requires all matching markers.

A PR touching ONLY `qa_cycle/`, `qa/testplan/`, `architecture/`, `.claude/`, `tasks/`, `requirements/` is documentation-only and needs no area verify marker. The merge gate still requires green GitHub checks, the review audit trail in the PR body (two `## Verdict: APPROVE @<sha>` lines bound to the current head + a `## CodeRabbit:` line) and a single plain `gh pr merge <N> … --match-head-commit <full-sha>` command with a literal SHA (no `$(…)` substitution — the hook reads the command text) — there is no automatic allow.

## Marker format

JSON, written by the agent that ran the verify:

```json
{
  "commit": "<short SHA at time of run>",
  "command": "<exact command run>",
  "exit": 0,
  "ts": "<ISO 8601 timestamp>",
  "summary": "5011 tests / 0 failures / 0 errors / 26 skipped — 13:35 min"
}
```

`exit` MUST be `0`. The hook checks `mtime` (max 24 hours old) and the `exit` field.

## How to write a marker

After your verify run completes successfully:

```bash
cat > .claude/markers/verify-backend.json <<EOF
{
  "commit": "$(git rev-parse --short HEAD)",
  "command": "./mvnw verify",
  "exit": 0,
  "ts": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "summary": "<test count summary from the verify output>"
}
EOF
```

If the verify failed (exit != 0), do **not** write a marker. Fix the failure, re-run, then write.

## Marker-commit check (post-merge, before the bookkeeping commit) — `scripts/check-verify-markers.sh`

The hook reads the marker files in the working tree at merge time; it cannot tell whether they were ever committed (G88-F35: five Epic-624 PRs merged with a valid marker that no commit recorded). `scripts/check-verify-markers.sh <pr-number>` closes that gap: for a merged PR it lists the areas touched (`backend/` → backend, `frontend/` → frontend, `portal/` → portal, `packages/` → both frontend and portal — the same mapping as the hook) and, per area, requires a `verify-<area>.json` either committed since the PR's merge commit or staged in the current index, whose `commit` is the PR's merged head or a PR-branch ancestor of it (`git merge-base --is-ancestor` to the head, and not already an ancestor of the merge commit's parent on `main`). Exit 0 = every area covered (docs-only PRs pass with a note), 1 = the named areas have no marker, 2 = usage/environment error. `/epic_v2` Step 5 runs it after `git add` and before the bookkeeping commit and fails the step on non-zero — write the missing marker from the gate that actually ran; never bypass. The same script audits any merged PR after the fact (Phase 89 exit gate, 636B).

## What the gate does NOT check

- Whether the verify was run on the current branch's HEAD (you can run on any commit; the marker captures which).
- Whether tests are flaky or slow.
- Whether targeted tests would have been sufficient (they're not — see CLAUDE.md §5).

The 24-hour window is a balance: fresh enough to be relevant, lenient enough to not require re-verifying for every cosmetic edit. If you've changed code substantively after the marker, re-run.

## Loopholes (forbidden)

- Don't write markers without running the command.
- Don't run a partial test suite and write a "verify" marker.
- Don't backdate a marker.
- Don't bypass the hook with `--admin` flags or by editing the hook out of `settings.json`.

If you genuinely cannot run the full verify (e.g. broken local stack), report up and ask. Don't ship.

See `CLAUDE.md` §1, §3, §9, §10 for the full quality gate contract.
