# Verify Markers — Quality Gate Contract

Per `CLAUDE.md` §1 and §10, every PR landing on `main` must have a recent successful full-suite verify.

The pre-merge hook (`.claude/hooks/pre-pr-merge-gate.sh`) blocks `gh pr merge` against `main` unless the appropriate marker file in this directory shows a clean, recent run.

## Required markers (per touched area)

| File | Required when PR touches | Run command |
|---|---|---|
| `verify-backend.json` | `backend/` | `cd backend && ./mvnw verify` |
| `verify-frontend.json` | `frontend/` | `cd frontend && pnpm install && pnpm run lint && pnpm run build && pnpm test` |
| `verify-portal.json` | `portal/` | `cd portal && pnpm install && pnpm run lint && pnpm run build && pnpm test` |

A PR touching multiple areas requires all matching markers.

A PR touching ONLY `qa_cycle/`, `qa/testplan/`, `architecture/`, `.claude/`, `tasks/`, `requirements/` is documentation-only and the gate is satisfied automatically.

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
