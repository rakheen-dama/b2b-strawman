# Phase 93 — decisions log

Defaults applied by builders under the standing protocol (no founder stop). The orchestrator may overrule any row before the family that uses it is built. Source = the 653A brief's "Spec contradictions" table (C-n) unless stated.

| # | Slice | Decision | Default applied | Source |
|---|---|---|---|---|
| D-1 | 653A | Tenant migration number (guide V30 / task file V31; V31 is taken by `V31__phase92_trust_idempotency_keys.sql`) | `V32__phase93_take_on_core.sql`, body exactly §17.7.2; schema-test name unchanged (`TakeOnCoreSchemaTest`) | C-1, P-1 |
| D-2 | 653A | Entity transitions throw `IllegalStateException`; there is no global ISE → 409 handler | Entities keep throwing ISE with X3-worded messages; Family 3 services wrap every transition into a 409 (`TAKE_ON_WRONG_STATE`); no global handler added | C-2 |
| D-3 | 653A | Audit group "Take-on" does not exist | `AuditEventGroup.DATA`, severity INFO (`takeon.file.downloaded` NOTICE) — Family 3 | C-3 |
| D-4 | 653A | settings-nav redirect-per-tab pin | exempt post-Phase-87 tabs (`matters/take-on`) in the test — Family 4 | C-4 |
| D-5 | 653A | Palette owner-without-modules pin 29 and processing-activity pin 6 | 29 → 30 (Family 4); 6 → 7 (Family 3) | C-4b, C-4c |
| D-6 | 653A | Next server-action body default is 1 MB | `experimental.serverActions.bodySizeLimit: "11mb"` — Family 4 | C-5 |
| D-7 | 653A | Preview needs headers that the schema does not store | preview re-reads the stored file | C-6 |
| D-8 | 653A | "POI usermodel" vs "streaming event API" | streaming event API only in `takeon.reader` main code; tests may use `XSSFWorkbook`/`SXSSFWorkbook` to generate fixtures | C-7 |
| D-9 | 653A | Hidden-sheet state and error cells | `sheetNames` returns `SheetName(name, hidden)`; error tokens stored as cell text, counted malformed, row kept; CSV structural malformation skipped with a warning | C-8 |
| D-10 | 653A | "10 MB workbook inside a bounded heap" | ArchUnit structural guard + functional read of a ~10 MB SXSSF workbook (no retained-heap assertion) | C-9 |
| D-11 | 653A | Size/rows/columns limits | service enforces all three → 422 (Family 3); the reader enforces rows/columns while reading | C-10 |
| D-12 | 653A | `SIGN_OFF_TRUST_TAKE_ON` in frontend `CAPABILITIES` | `CAPABILITY_META` only (Family 4) | C-11 |
| D-13 | 653A | Purge job pattern | ShedLock + cron 03:00 `KaziTime.ZONE_ID`, `forEachTenant`, no new job type — Family 3 | C-12 |
| D-14 | 653A | `markValidated` on a trust/debtor batch without a cutover date | `requireValidatable()` also checks `cutoverDate` ("Enter the cutover date before validating.") → 409 `TAKE_ON_CUTOVER_DATE_REQUIRED` in the service | C-14 |
| D-15 | 653A | Transitions unreachable through 653A endpoints | plain unit test `TakeOnBatchTest` covers every transition incl. `claimCommit`/`complete`/`returnToValidated` | C-15 |
| D-16 | 653A | `setCutoverDate` with the same date on a validated batch | treated as no change (stays `VALIDATED`); a different date moves `VALIDATED` → `MAPPED` | C-19 (builder reading) |
| D-17 | 653A | `claimCommit` with a revoked sign-off | refused (ISE) in addition to the hash checks of §17.8.3; `notRequired(hash)` is always live | §17.8.3 (builder reading) |
| D-18 | 653A | Purge test aging | backdate only the test's own batch `created_at` via `JdbcTemplate` | C-18 |
| D-19 | 653A | Dedup keys computable without lookups | CLIENTS `email:`, MATTERS `ref:`, DEBTOR_BALANCES `number:`; trust rows `null` — Family 3 | C-25 |
| D-20 | 653A | `eq1_holds`/`eq2_holds` in `takeon.batch.validated`; unread DTO fields | omitted in 653A (657B / 655A add them) | C-26, C-27, P-11 |
