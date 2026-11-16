# 645A — defects found outside the slice's scope (reported, not fixed — re-spec required)

## F-1 — Interest-run wizard: Calculate step overflows the dialog at 1440×900 (LOW, UI)

- **Observed:** 2026-11-16, OBS run for 645A (stack built from `23a55222b`), `obs/wizard-{alice,bob,carol}.png`.
- **What:** at a 1440×900 viewport the wizard's "Step 2 of 2 — Calculate" allocations table is wider than the dialog: the "Client Share" column is clipped at the right edge and the Cancel button sits on the edge.
- **Scope:** 645A did not change the dialog width or the allocations table (it removed the approve/post step and controls only). **Not yet reproduced on `main`** — the same class of overflow is recorded for `PlaceInvestmentDialog` (memory: env-playwright-legal-configs-dirty-tree, 609E). Classify against `main` before fixing.
- **Lifetime:** the wizard is removed in 651C, so a fix may be moot; orchestrator/founder to decide whether it is worth a slice.
