# Slop hunt — PR #1241: fix(OBS-2104c): bulk billing step 3 — surface legal disbursements

**Batch**: D — SQL/billing
**Reviewed**: 2026-05-01
**Verdict**: NIT (well-scoped despite the file count; one misleading comment + one minor test-style nit)

## PR description vs diff

PR claims: "Wizard step 3 (cherry-pick) now renders a Disbursements section alongside Time Entries and Expenses... Adds `LEGAL_DISBURSEMENT` to `EntryType`, extends `createEntrySelections()` and `recalculateItemTotals()`, and threads `selectionService.resolveSelectedDisbursementIds()` into the billing-run draft generator." Diff confirms exactly that.

Despite touching 12 files, scope is tightly aligned to one feature: surfacing approved-and-unbilled legal disbursements as cherry-pickable lines parallel to expenses. The 12 files are: 1 controller (new endpoint), 1 service-facade method, 1 selection-service (new methods + extended persistence), 1 generation-service wiring, 1 enum value, 1 DTO record, 1 backend integration test, 1 frontend cherry-pick step component, 1 frontend customer-detail component, 1 frontend types/api-client, 1 frontend server-action, 1 frontend test. **No scope creep** — every file participates in the single feature thread.

The "12 files" headline is large but legitimate. The change traces cleanly from controller → service → SQL → DTO → frontend types → server action → component, with one test on each side.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | LOW | Misleading comment | `BillingRunSelectionService.java:728-731` | Comment says: "The query is harmless (returns empty) for non-legal tenants because the legal_disbursements table only exists in tenant schemas with the legal vertical module enabled (Flyway V100 in the legal-vertical migration set)." This is **wrong**. `V100__create_legal_disbursements.sql` is an unconditional Flyway tenant migration — it runs against EVERY tenant schema, not just legal ones. The table exists everywhere. The query is "harmless for non-legal tenants" only because no rows will exist (no legal-disbursement create endpoint is wired for non-legal tenants), not because the table is conditional. | Fix the comment to reflect actual behaviour. The table is global; the row population is module-gated. Avoid future readers concluding wrong things about Flyway conditionality. |
| 2 | LOW | Inconsistent static-import in test | `BillingRunDisbursementSelectionTest.java:556` | Author imports `post`, `put` (static, line 332-333) but reaches for fully qualified `org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(...)` on line 556 because `get` was not imported. The same file imports `get` already on line 330. Wait — confirmed: lines 330-332 import `get`, `post`, `put` statically. So line 556's FQN is dead code that the IDE could shorten. | Replace with `get(...)` for consistency. Cosmetic. |
| 3 | LOW | Comment placeholder kept in test | `BillingRunDisbursementSelectionTest.java:563-564` | `// (itemResult kept for readability; not asserted further here.) org.junit.jupiter.api.Assertions.assertNotNull(itemResult);` — the assertion is a no-op since `mockMvc.perform(...).andReturn()` would have thrown if the request failed. Reads like a placeholder the author meant to remove. | Remove the line or convert the preceding `andExpect` chain into a `.andDo(MockMvcResultHandlers.print())` if the goal is just observability. Not blocking. |
| 4 | LOW | DTO duplicates fields the entity already exposes | `BillingRunDtos.java:163-189 (DisbursementResponse)` | The new `DisbursementResponse` record exposes 11 fields with a `from(LegalDisbursement)` static. The existing `ExpenseResponse` follows the same pattern, so this is internally consistent — but it duplicates the per-getter accessor pattern instead of using a projection or interface. Acceptable for record-DTO style; no action. | None. Listed for transparency. |
| 5 | LOW | Test only covers happy path | `BillingRunDisbursementSelectionTest.java` | Two tests: getUnbilledDisbursements returns the approved row, and toggling to `included=false` recalculates. No tests cover: DRAFT-status disbursement excluded, REJECTED disbursement excluded, out-of-period disbursement excluded, multi-disbursement-multi-task cardinality (already covered by #1240's `BillingRunPreviewCardinalityTest`). | The cardinality test covers the SQL aggregation. The lifecycle-gate behaviour is implicit in the SQL but untested at the integration level. Adding one test seeding a DRAFT disbursement would close it. Not blocking. |

## Test scope check

- Backend: `./mvnw test -Dtest='*BillingRun*,*Disbursement*'` (131 tests). **Targeted, not `./mvnw verify`** — same test-scope-drift class as #1238 and #1240. Per CLAUDE.md §1 the merge bar is full verify.
- Frontend: `pnpm run lint && pnpm run build && pnpm test` (340 test files). **Frontend full bar met.**
- Did the tests exercise the new behaviour? **Yes** — the backend test seeds a real legal_disbursement, runs the wizard preview, hits the new GET endpoint, and verifies persistence + toggle recalculation. The frontend test renders `CherryPickStep` with `mockGetUnbilledDisbursements` returning a fixture and asserts the Disbursements section + checkbox + subtotal. End-to-end coverage of the new code path.

## Notes

- This PR is the third in a fast cluster (#1238 → #1240 → #1241) addressing OBS-2104, OBS-2104b, OBS-2104c on the same component. The spec-driven decomposition is reasonable: discover → de-duplicate → surface-in-step-3 are three different bugs with three different fixes. The cluster pattern is clean, not scope-creep.
- The `recalculateItemTotals()` method now sums disbursements into `unbilledExpenseAmount` (not its own column). This is consistent with the discoverCustomers projection in #1240 (`expense_amount + disbursement_amount → unbilled_expense_amount`). Symmetry preserved across the three layers.
- The frontend `CherryPickCustomerDetail` component renders a third table for Disbursements with legal-vertical-tuned columns (incurredDate / category / supplierName) instead of cloning the Expense table. Right call — disbursements have a different shape than expenses.
- The `EntryType` enum gains `LEGAL_DISBURSEMENT`. Per CLAUDE.md "Anti-Patterns" the class uses `@Enumerated(STRING)` (so no migration needed) — consistent with the design.
- `getUnbilledDisbursements` (service layer) calls `validateItemBelongsToRun(...)` first. Good defensive pattern, mirrors `getUnbilledExpenses`. No new attack surface.
- `BillingRunGenerationService.java:120-126` adds the disbursement IDs into the `CreateInvoiceRequest`. Comment notes "The invoice creation pipeline already supports this via CreateInvoiceRequest.disbursementIds (slice 487A)" — this is a load-bearing claim. Worth verifying that the invoice creation pipeline DOES correctly apply disbursementIds to the generated invoice (out of this PR's scope, but if it's broken, the cherry-pick selection would be silently dropped at generation time — same class of bug as the OBS-2104c "modal workaround" the PR aims to remove).
