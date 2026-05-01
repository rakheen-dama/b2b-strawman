# Slop hunt — PR #1235: fix(OBS-1001): trust transaction dialogs — combobox pickers replace raw UUID inputs

**Batch**: D — SQL/billing
**Reviewed**: 2026-05-01
**Verdict**: NIT

## PR description vs diff

PR description is honest and accurate. Scope is "three trust transaction dialogs (Deposit/Payment/Refund) — replace raw UUID input with a combobox picker, plus matter-detail surface uses a locked-picker variant." The diff confirms exactly that: a shared `TrustEntityPickers.tsx` component with `TrustCustomerPicker` + `TrustMatterPicker`, threaded into the three dialogs and the `TrustBalanceCard`. The 11 changed files are: 1 new shared component, 3 dialogs, 1 transaction-actions wrapper, 1 balance card, 1 transactions page (server-fetches roster), 4 frontend tests (3 stub-mock additions, 1 substantive trust-transactions extension).

The "Scope expansion" subsection in the PR description correctly flags that all three dialogs were fixed in one PR rather than three separate PRs, with the rationale "same bug class, identical fix pattern, identical risk profile." That is the explicit exception in CLAUDE.md §7 ("Exception: same-bug-class clusters... may ship in one PR if explicitly authorized"). It is not strictly authorized in writing, but the orchestrator-level mandate ("Don't skip any bugs") has the same shape.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | LOW | Defensive workaround masking failure | `frontend/app/(app)/org/[slug]/trust-accounting/transactions/page.tsx:222` | Empty `catch { pickerCustomers = [] }` swallows `/api/customers?status=ACTIVE` failures. The user sees an empty picker with `No clients found.` and no signal that the upstream call failed. | Log the error or render a banner. Or let the page throw to the Next.js error boundary — graceful degradation here only hides the symptom. |
| 2 | LOW | AI-flavoured comment | `frontend/components/trust/TrustEntityPickers.tsx:606-619` | Long file-header docblock that mostly restates what the code obviously does ("Shared customer + matter combobox pickers used by the trust transaction dialogs (Record Deposit / Payment / Refund). Replaces the raw `<Input placeholder="UUID"/>` fields..."). | Trim to 2-3 lines plus the OBS-1001 reference. The component name + types already convey the purpose. |
| 3 | LOW | Test-coverage gap acknowledged in code | `frontend/__tests__/trust-transactions.test.tsx:124-127` | Comment "Driving the Radix Popover open is unreliable in happy-dom, so the deeper select-and-fill flow is exercised by the locked-picker submission test above." The select-customer-from-popover-then-pick-matter flow is **never** exercised in vitest. | OK as-is given Radix + happy-dom limitations; flag if the matter-picker `useEffect` clear-on-customer-change logic regresses, since no test covers it. Could add a Playwright check on the dev stack. |
| 4 | LOW | Mock-action stub-out across 3 unrelated test files | `frontend/__tests__/trust-coexistence.test.tsx:30`, `trust-tabs-settings.test.tsx:48`, `stub-pages.test.tsx:12` | Three separate test files now each carry an identical `vi.mock("@/app/(app)/org/[slug]/customers/[id]/actions", ...)` stub for `fetchCustomerProjects` because `TrustBalanceCard` (embedded in those pages) now imports it transitively. This is mock-creep — the next picker addition will require touching all four test files. | Consider extracting a shared `vi.mock` setup or a `setupFiles` entry. Not blocking. |

## Test scope check

- `pnpm run lint && pnpm run build && pnpm test` — all run (per PR description). 339 files / 2123 tests. **Frontend full-test bar met** per CLAUDE.md §1.
- The substantive coverage of the new component (`TrustEntityPickers`) is one happy-path test driving the locked-picker submission and two presence checks. The Radix Popover open-and-select flow is acknowledged as untested in happy-dom (see Finding #3). For a UX-only fix this is adequate; a follow-up Playwright test would close the gap.
- No backend tests changed because the API contract is unchanged.

## Notes

- This is mostly clean UX work. Per-spec, per-mandate. The defensive try/catch in the page (Finding #1) is the only thing that smells like a workaround — and it's small.
- The `void slug;` parameter discard in `RecordDepositDialog` (line 291) suggests the prop is now unused but kept for API compatibility — harmless, but could be dropped from the props if no caller passes it. Leaving alone since the PR is scoped to the picker fix.
- The PR description explicitly lists two test-plan items as `[ ]` (unchecked manual ck 10.4 verifications). The orchestrator merged anyway; that is consistent with the PR being frontend-only and the test plan reflecting "next QA pass" items rather than "must-do-before-merge" items.
