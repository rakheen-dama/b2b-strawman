# Day 7 — First Real Work (Cycle 3 Re-test)

## Executed: 2026-03-16T03:00Z (cycle 3)
## Actor: Alice (Owner)

### Prerequisite Setup (Data Wiped — Full Rebuild)

E2E stack was rebuilt after GAP-030 fix (PR #691), wiping all data. The following was recreated:

1. **Kgosi Construction (Pty) Ltd** — Created as Company type, transitioned PROSPECT -> ONBOARDING. Customer ID: `c7eeb232-4063-4de1-92cf-dff43c84e85a`.
2. **Kgosi Construction — Monthly Bookkeeping 2026** — Project created and linked to Kgosi. Project ID: `1914ddfe-1e2d-429c-ad16-b4914e09e513`.
3. **Task: "January 2026 Bank Statement Capture"** — Created on Kgosi project. Task ID: `842137e9-30ff-4e4b-8834-8eea5cd00aa7`.

### GAP-030 Verification

- **Result**: REOPENED
- **Evidence**: Clicked "Log Time" button on the task row. The page immediately crashes with error boundary: "Something went wrong: Unable to load projects. Please try again." Console error: `TypeError: Cannot read properties of null (reading 'toLowerCase')`. This is a **different error** from the original `RangeError: Invalid currency code : null` but the behavior is identical — 100% reproducible crash on every click of "Log Time".
- **Root cause analysis**: The PR #691 fix added `currency || "USD"` fallback in `formatCurrency()` (`frontend/lib/format.ts` line 55). However, the crash now occurs earlier in the render pipeline. The `formatRateSource()` function in `frontend/components/tasks/log-time-dialog.tsx` line 37 calls `source.toLowerCase()` in the default case of a switch statement. If `source` is null, this throws TypeError. Additionally, there may be other null property accesses in the compiled chunks that are triggered during the Log Time dialog rendering.
- **Impact**: ALL time logging remains completely blocked. The fix was insufficient — it addressed one null path but missed others.

### Checkpoint 7.1 — Carol's My Work page loads
- **Result**: NOT TESTED (cycle 3)
- **Notes**: Not retested in cycle 3 — was PASS in cycle 2. Focus was on GAP-030 verification.

### Checkpoint 7.2 — Carol logged 3.0hr on Kgosi Construction at R450/hr = R1,350.00
- **Result**: FAIL (BLOCKER — GAP-030 REOPENED)
- **Evidence**: "Log Time" button crashes with `TypeError: Cannot read properties of null (reading 'toLowerCase')`. Error boundary: "Something went wrong: Unable to load projects." 100% reproducible.

### Checkpoint 7.3 — Carol logged 2.0hr on Vukani Tech at R450/hr = R900.00
- **Result**: FAIL (BLOCKED by GAP-030)

### Checkpoint 7.4 — Bob added comment on Kgosi project
- **Result**: PASS (partial — comment works, time entry blocked)
- **Evidence**: Opened task detail panel for "January 2026 Bank Statement Capture". Clicked Comments tab. Typed "Missing February bank statements — sent follow-up email to Thabo" and clicked "Post Comment". Comment appeared immediately with "Alice Owner" attribution and "now" timestamp. Edit and Delete buttons visible on the comment. Comment system is fully functional.

### Checkpoint 7.5 — Bob logged 1.0hr on Kgosi at R850/hr = R850.00
- **Result**: FAIL (BLOCKED by GAP-030)

### Checkpoint 7.6 — Document uploaded to Kgosi project
- **Result**: NOT TESTED

### Checkpoint 7.7 — Alice logged 0.5hr on Naledi at R1,500/hr = R750.00
- **Result**: FAIL (BLOCKED by GAP-030)

### Checkpoint 7.8 — Rate snapshots correct for all 3 members
- **Result**: FAIL (BLOCKED by GAP-030)

### Checkpoint 7.9 — Cross-project time summary accessible
- **Result**: NOT TESTED (BLOCKED by GAP-030)

---

## Summary

| Checkpoint | Result | Gap |
|-----------|--------|-----|
| GAP-030 verification | REOPENED | GAP-030 (different error, same crash) |
| 7.1 — Carol's My Work page loads | NOT TESTED (was PASS in c2) | — |
| 7.2 — Carol logged 3.0hr on Kgosi at R450/hr | FAIL (BLOCKER) | GAP-030 |
| 7.3 — Carol logged 2.0hr on Vukani at R450/hr | FAIL (BLOCKED) | GAP-030 |
| 7.4 — Comment on Kgosi project | PASS | — |
| 7.5 — Bob logged 1.0hr on Kgosi at R850/hr | FAIL (BLOCKED) | GAP-030 |
| 7.6 — Document uploaded to Kgosi | NOT TESTED | — |
| 7.7 — Alice logged 0.5hr on Naledi at R1,500/hr | FAIL (BLOCKED) | GAP-030 |
| 7.8 — Rate snapshots correct | FAIL (BLOCKED) | GAP-030 |
| 7.9 — Cross-project time summary | NOT TESTED | — |

**Totals**: 1 PASS, 5 FAIL, 4 NOT TESTED

## GAP-030 Update

### GAP-030 — Log Time crashes (REOPENED)
- **Previous error**: `RangeError: Invalid currency code : null` (cycle 2)
- **Current error**: `TypeError: Cannot read properties of null (reading 'toLowerCase')` (cycle 3)
- **Fix applied**: PR #691 added `currency || "USD"` fallback in `formatCurrency()` — this addressed the `Intl.NumberFormat` crash but the dialog still crashes from a different null access.
- **Likely cause**: `formatRateSource()` in `log-time-dialog.tsx:37` calls `source.toLowerCase()` on the default branch. If `resolvedRate.source` is null, this crashes. There may also be other null paths in the compiled chunks.
- **Suggested fix**: Add null guard in `formatRateSource()`: `return (source ?? "unknown").toLowerCase().replace(/_/g, " ");`. Also audit all properties of `resolvedRate` for null safety.
