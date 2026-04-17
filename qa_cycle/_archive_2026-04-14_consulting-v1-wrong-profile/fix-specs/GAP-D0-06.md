# Fix Spec: GAP-D0-06 — Automation rules not pre-seeded; feature toggle disabled

## Problem
Settings > Automations page shows "Automation Rule Builder is not enabled" with a link to Settings > Features. Even after enabling the module, no accounting-za automation rules exist. The accounting-za profile references an automation pack (`automation-templates/accounting-za.json`) with 3+ rules (FICA reminder, budget alert, overdue reminder) but they are not seeded. Reported at Day 0 checkpoint 0.43.

## Root Cause (hypothesis)
The `automation_builder` module is a HORIZONTAL module in `VerticalModuleRegistry` (line 137-145) — it's manually toggled by org admins, not auto-enabled by the vertical profile. The accounting-za profile has `"enabledModules": []` (empty array), so no modules are auto-enabled at provisioning.

The automation template pack (`automation-templates/accounting-za.json`) exists with valid template definitions, but the automation seeder may only run when the module is enabled, OR the templates are seeded but require the module toggle to be visible in the UI.

**Two separate issues**:
1. The `automation_builder` module is not enabled by default — this is by design (horizontal modules are opt-in).
2. Automation templates may or may not be seeded regardless of module state.

## Fix
**WONT_FIX for this cycle.** The automation module is intentionally a horizontal (opt-in) feature per ADR-239. Accounting firms don't universally need custom automations, and the module toggle design is correct.

If desired in a future cycle:
1. The vertical profile could list `"automation_builder"` in its `enabledModules` array to auto-enable it for accounting-za tenants.
2. Alternatively, seed a "standard automations" set that runs without the module toggle (only the "builder" UI is gated, not the execution engine).

## Scope
N/A (WONT_FIX)

## Verification
N/A

## Estimated Effort
S (< 30 min) to add to enabledModules, but product decision needed first
