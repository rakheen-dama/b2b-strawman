# 663B OBS-after — EmptyState variant sweep (task 663.10)

Captured 2026-11-26 on the same E2E mock-auth stack and data as `../before/`. The only change is the frontend container, rebuilt from the 663B worktree at PR head `e405f930a` with `compose/scripts/e2e-rebuild.sh frontend` (image created 08:47:12Z). Backend, data and personas are unchanged, so before and after differ only in code.

Command, run unedited from `../before/`:
`PHASE=after node scripts/capture.cjs && PHASE=after node scripts/assemble.cjs`

## Result: after = before
- **EmptyState boxes:** all 24 captures (6 surfaces × Alice/Carol × 1440 light/dark) match `../before/measurements.json` exactly on every `target` field: `variant` (`default`), `w`, `h`, `titleTag` (`h2`), title, description, actions, box and title computed styles, icon tile, className, viewport y, normalised outerHTML and its sha256. The comparison matched on key, persona and theme: **0 differences**.
- **Raw 1440×900 tiles** (`.tmp/663B-raw/{before,after}`, gitignored): 18 of 24 are byte-identical. The other 6:
  - 4 are the Activity tab, which shows a populated feed with relative timestamps (the Activity surface is not empty on any matter; see `../before/README.md`).
  - 1 is Carol's customer-rates capture. Rates is gated for members, so the tab falls back to Overview.
  - 1 is `time-summary` Carol dark. It is visually identical to before (checked side by side by the orchestrator), and its EmptyState hash is unchanged.
- **Console:** 24 captures, 0 errors, 0 warnings, 0 hydration warnings (`console.json`).

## Sheets
The same layout as `../before/`: `sheet-{activity,budget,documents,time-summary,customer-rates,matter-clients}.png`.
