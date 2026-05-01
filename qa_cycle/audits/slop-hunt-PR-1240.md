# Slop hunt — PR #1240: fix(OBS-2104b): bulk billing customer discovery — CTE rewrite eliminates Cartesian product

**Batch**: D — SQL/billing
**Reviewed**: 2026-05-01
**Verdict**: NIT (CTE is row-set equivalent + aggregation correct; minor test-scope drift)

## PR description vs diff

PR description matches the diff. The single-file SQL rewrite in `BillingRunSelectionService.discoverCustomers()` replaces a flat 5-table LEFT-JOIN-then-GROUP-BY with three per-source aggregation CTEs (`time_agg`, `expense_agg`, `disbursement_agg`) hung off a `customer_filter` CTE. The new test file `BillingRunPreviewCardinalityTest` adds 3 regression tests covering the exact Cartesian scenarios that #1238 silently inflated.

The PR description's bug summary is accurate: "For a project with N tasks and 1 disbursement, the disbursement row appeared N times in the join product, so SUM(ld.amount + ld.vat_amount) returned `tasks_count × actual`. COUNT(DISTINCT) masked the bug for the count columns." The 419-line addition is heavy for the description's "rewrite the SQL" framing, but ~341 lines are the new test file, so the production-code delta is ~75 lines (~30 net after deletions).

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | LOW | Test-scope drift — targeted verify only | PR description test plan | Tests run as `./mvnw test -Dtest='*BillingRun*'` (72). No `./mvnw verify` evidence per PR. CLAUDE.md §1 requires it for any backend production change. The BillingRun-narrowed pattern would miss any class outside that prefix that imports BillingRunSelectionService or its DTOs. | Per cycle 23 the full verify was confirmed clean (5011 tests, 0F/0E). For new SQL rewrites, write the cardinality regression tests in the same PR (this PR did) AND run full verify before merge. |
| 2 | LOW | Comment over-narration | `BillingRunSelectionService.java:485-503` | 18-line block comment narrating the bug, the fix, and the OBS-2104 invariants. Useful as historical context but redundant with `OBS-2104b.md`. The "OBS-2104 invariants preserved" enumeration is accurate but reads like a defence-of-fix rather than code documentation. | Trim to 4-6 lines + link to fix-spec. The fix-spec is the authoritative narrative. |
| 3 | LOW | Test seeds bypass API factory pattern | `BillingRunPreviewCardinalityTest.java:411-466` | Helpers seed `Customer`, `Project`, `CustomerProject`, `Task`, `TimeEntry`, `LegalDisbursement` directly via JPA repos inside a transaction template. `backend/CLAUDE.md` (Anti-Patterns and "Shared Test Utilities") notes "Never define private `createProject()`/`createCustomer()`/`createTask()`/`createTag()` helpers in tests — use `TestEntityHelper.*`." Exception is granted for "domain-specific entity creation that includes lifecycle transitions or extra fields". | Borderline — `LegalDisbursement` needs `submitForApproval()` + `approve()`, which is the documented exception. `Task` and `TimeEntry` setup could use `TestEntityHelper`. Not blocking. |
| 4 | LOW | `MockMvc` request type qualified path | `BillingRunDisbursementSelectionTest.java:556` | Fully qualified `org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(...)` because the file already statically imports `post`, `put`, `get` from the same class — wait no, `get` is NOT in the imports. The author imported `post` and `put` but not `get`, so they reach for the FQN. Inconsistent. | Add `import static ... MockMvcRequestBuilders.get;` and use `get(...)` consistently. Cosmetic. (Cross-file finding — this is in #1241's test file, not #1240's, but flagged here because the pattern came up while reading the slop hunt — moved to #1241 audit.) |

## Test scope check

- Targeted: `./mvnw test -Dtest='*BillingRun*'` — 72 tests pass (69 pre-existing + 3 new cardinality tests). **Targeted, not full verify.**
- Did the test exercise the new behaviour? **Yes — well.** Three substantive integration tests:
  - Test 1: 9 tasks + 1 disbursement → asserts `unbilledExpenseAmount = 1250` (would have been 11250 pre-fix). This is the headline scenario.
  - Test 2: 5 tasks + 2 expenses → asserts `unbilledExpenseAmount = 300` (would have been 1500 pre-fix).
  - Test 3: 3 tasks + 4 time entries + 1 disbursement → asserts time = 6000 AND expense = 500 (the cross-source independence test).
- The tests use `@SpringBootTest + @AutoConfigureMockMvc + @Import(TestcontainersConfiguration.class)` against embedded Postgres. The SQL is exercised end-to-end via the wizard preview endpoint, not via a SQL-builder unit test. **Strong test design.**

## CTE row-set equivalence verdict

**Equivalent.** The OLD query started `FROM customers c JOIN customer_projects cp ON cp.customer_id = c.id JOIN projects p ON cp.project_id = p.id` (INNER JOINs) then LEFT JOINed tasks/time_entries/expenses/legal_disbursements, GROUP BY c.id, c.name, HAVING ≥1 of (te-count, e-count, ld-count) > 0.

The NEW query:
- `customer_filter` CTE = active customers (no project filter).
- `time_agg` = customers with ≥1 unbilled, in-period, billable time entry (currency-matching or NULL), via `customer_projects → tasks → time_entries`.
- `expense_agg` = customers with ≥1 unbilled, in-period, billable, currency-matching expense via `customer_projects → expenses`.
- `disbursement_agg` = customers with ≥1 UNBILLED + APPROVED, in-period legal_disbursement via `customer_projects → legal_disbursements`.
- Final SELECT LEFT JOINs the 3 aggs to `customer_filter` and WHERE-filters to ≥1 of the 3 counts > 0.

A customer with no `customer_projects` row: OLD excluded by INNER JOIN. NEW: in customer_filter, but each agg requires a `customer_projects` JOIN, so all aggs return NULL → COALESCE-to-0 → filtered by the WHERE. **Equivalent**.

A customer with `customer_projects` but zero in-period activity: OLD excluded by HAVING. NEW: aggs return null → filtered by WHERE. **Equivalent**.

A customer with mixed activity: OLD's GROUP BY collapsed the row-multiplied join to a single row and counted with COUNT(DISTINCT) (correct counts) but summed without DISTINCT (inflated SUMs). NEW computes per-source SUMs in isolation per customer (no row multiplication) and sums into the final projection (`unbilled_expense_amount = expense_amount + disbursement_amount`). **Mathematically correct**.

`customer_projects` has UNIQUE (customer_id, project_id) — see `V8__create_customer_projects.sql:14-16`. So no double-counting from the junction table itself. Confirmed.

One edge case worth noting (not introduced by #1240, present in OLD as well): if a project is linked to two customers via `customer_projects`, a time entry on that project's tasks contributes to **both** customers' totals. Both queries have this property; if it is wrong it is a pre-existing modelling issue, not a regression.

**Verdict: row-set equivalent, aggregation correct, no silent narrowing.** The CTE rewrite is a clean fix.

## Cross-reference to audit-04 suspects

`qa_cycle/audits/04-sql-cartesian.md` lists `ProjectRepository.java` and `AuditEventRepository.java` as remaining suspects.

- `ProjectRepository.java`: needs spot-check per audit-04. Not changed by this PR.
- `AuditEventRepository.java`: needs spot-check per audit-04. Not changed by this PR.

If those have the same flat-LEFT-JOIN + multi-source-aggregate pattern, the OBS-2104b CTE rewrite is the canonical template. Worth a future PR after eyeballing both.

## Notes

- The PR is well-scoped: 1 production file + 1 test file. Aligns with CLAUDE.md §7 ("One fix per PR").
- The test file uses `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` and `@BeforeAll` to set up the tenant once. Per-test the tests seed a fresh customer-with-project, so they don't pollute each other. Good design.
- The PR description's "(lucky no inflation here since disbursements_count = 1)" comment in Test 3 line 401 (now in test file) is a refreshingly honest acknowledgement that one of the time-side assertions doesn't actually distinguish the two queries; the disbursement-side assertion does. That's the kind of self-aware test-design comment that signals the dev understood the bug.
- Worth checking that the lifecycle gate `ld.approval_status = 'APPROVED'` matches the runtime invariants — Day 28 of the QA scenario must include a disbursement-approval step, otherwise APPROVED disbursements never exist in the test fixture (the fix-spec already flagged this scenario amendment requirement).
