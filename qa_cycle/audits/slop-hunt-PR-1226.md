# Slop hunt — PR #1226: fix(OBS-201): client wizard ID Number dedupe + intake accordion UX

**Batch**: C — customer UX
**Reviewed**: 2026-05-01
**Verdict**: NIT

## PR description vs diff

Description honest. Two narrowly-scoped changes, both frontend-only (`create-customer-dialog.tsx`, `intake-fields-section.tsx`). Verification block lists lint/build/full vitest counts (339 files / 2119 passed), which matches what the diff would exercise. No "verified end-to-end" overstatement — the description correctly notes "QA cycle will re-run Day 2 ck 2.2 to verify before Day 3", which is honest staging.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | LOW | AI smell — verbose comment | `frontend/components/customers/create-customer-dialog.tsx:188-198` | The mirror logic is preceded by an 8-line OBS-201 comment that re-explains the entire fix rationale. The variable name (`mirroredIdNumber`) plus a one-line `// OBS-201: mirror id_passport_number → entity idNumber` would carry the same information. The code itself is short (3 lines) so the comment outweighs the logic 3:1. | Trim to 1–2 lines on next touch. Not worth a follow-up PR. |
| 2 | LOW | AI smell — verbose comment | `frontend/components/customers/intake-fields-section.tsx:55-60, 67-72` | Same pattern. Two 5-line "OBS-201 Part B" comments around what is essentially `useState(visibleFields.length > 0)` and a single boolean derivation. | Same — trim opportunistically. |
| 3 | LOW | Test scope gap | `frontend/__tests__/components/customers/create-customer-dialog.test.tsx` | No vitest test was added for the OBS-201 mirror behaviour (idNumber falls back to `customFields.id_passport_number`). The visible Step-1 input was removed, so any prior test that asserted the "ID Number" label still appears would fail; absence of churn in this file suggests no such test existed. The new mirror logic is therefore covered only by the broad "vitest 2119 passed" claim, not by a behavioural assertion. | On next touch add a small test that asserts the createCustomer payload's `idNumber` reflects the SA Legal `id_passport_number` when the user typed nothing in Step 1. |
| 4 | LOW | Test scope gap | `frontend/__tests__/components/customers/intake-fields-section.test.tsx` | Existing tests assert "Group with required fields starts expanded". They will continue to pass under the new "any visible fields → expanded" rule. There is no assertion that an all-optional group now auto-opens (the actual OBS-201 Part B behaviour change). | On next touch add a vitest case for an all-optional group rendering its fields directly with no inner accordion. |

## Test scope check

- PR description claims: `pnpm lint` (0 errors / 96 pre-existing warnings), `pnpm build` green, `pnpm test` (339 files / 2119 passed / 2 skipped). This is the correct full-suite frontend gate per CLAUDE.md §1.
- Frontend-only diff with no backend touch — no `./mvnw verify` needed.
- The only weakness is that the new behaviours (`mirroredIdNumber`, all-optional group auto-open) lack dedicated assertions; the green vitest run covers them only by absence of regression.

## Notes

- No backend or schema changes — none of the cross-package risks that bit OBS-2102 apply here.
- `mirroredIdNumber` logic has a sensible precedence (`values.idNumber` wins if user typed it). Trim guard avoids whitespace-only fallback. Logic itself is clean.
- No scope creep — both changes are scoped to OBS-201 Part A (dedupe) and Part B (accordion UX), explicitly named.
- No swallowed exceptions, no defensive null-checks beyond the `typeof` guard which is appropriate for a `Record<string, unknown>` value.
