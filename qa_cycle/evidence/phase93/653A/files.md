# 653A — scout list (task 653.2)

Scouted at HEAD `cb803686c` (2026-11-23); adjusted by the Family 1 builder. Estimate ≈ 81–83 files (P-6 stop line ≈ 90).

## Migrations
- Tenant max at HEAD: `V31__phase92_trust_idempotency_keys.sql` → this slice creates **`V32__phase93_take_on_core.sql`** (confirmed next free at build time).
- Global max `V7__portal_trust_invested_balance.sql` — no global migration (V8 stays free).
- `BaselineSchemaParityTest` V1-pinned — no edit.

## Backend pins
| Pin | Where | Change | Family |
|---|---|---|---|
| Capability enum (27 → 29) | `orgrole/Capability.java` | `MANAGE_TAKE_ON`, `SIGN_OFF_TRUST_TAKE_ON` | 1 (done) |
| `OWNER_ONLY` (3 → 4) | `orgrole/Capability.java` | `SIGN_OFF_TRUST_TAKE_ON.name()` | 1 (done) |
| Capability tests | `CapabilityEndpointTest:158` (computed), `OrgRoleServiceTest:49` (`ALL_NAMES`), `CapabilityRegistrationTest` | no hard-coded count; `CapabilityRegistrationTest` +2 cases | 1 (done) |
| Audit registry count 83 → 89 | `AuditEventTypeRegistryTest:288,358,359` + ledger line | bump | 3 |
| Registry insertion | `AuditEventTypeRegistry.java` after `trust_interest_memo.saved` | 6 entries | 3 |
| Processing activities | `ProcessingActivityService.seedJurisdictionDefaults` | 7th `seedIfAbsent` | 3 |
| Processing-activity pin 6 → 7 | `ProcessingActivityControllerTest:193` | bump | 3 |

## Frontend pins (Family 4)
- `frontend/lib/capabilities.tsx` (`CAPABILITIES`, `CAPABILITY_META`, `OWNER_ONLY_CAPABILITIES`); `frontend/__tests__/capabilities.test.tsx` `OWNER_ONLY_NAMES` + drift pin (red from commit 1 until commit 4 — expected).
- `frontend/lib/settings-nav.ts` (31 → 32 tabs; matters 7 → 8); `frontend/lib/__tests__/settings-nav.test.ts` (counts, `TAB_GATES`, redirect-per-tab exemption C-4).
- `frontend/components/__tests__/command-palette-dialog.test.tsx` 29 → 30, 31 → 32.
- `frontend/__tests__/settings/matters-family.test.tsx` (loader + mock, eight tabs, unknown tab → `not-a-tab`).
- `frontend/app/(app)/org/[slug]/settings/matters/_tabs/index.ts`, `frontend/lib/types/index.ts`, `frontend/next.config.ts` (C-5).

## Files by family
| Family | Create | Modify |
|---|---|---|
| 1 | V32 SQL; `takeon/` 5 enums, 4 entities, 4 repositories; `TakeOnCoreSchemaTest`, `TakeOnBatchTest`; this file; `tasks/phase93-decisions.md` | `Capability.java`, `CapabilityRegistrationTest.java` |
| 2 | `takeon/reader/` 10 classes (the brief's 9 + package-private `TakeOnSheetAssembler`, the shared row-model rules); `CsvTakeOnReaderTest`, `XlsxTakeOnReaderTest`, `TakeOnValueParserTest`, `TakeOnTestFiles` | — |
| 3 | ≈ 15 main + ≈ 8 test + `wire/take-on-contract.json` | `AuditEventTypeRegistry`, `ProcessingActivityService`, `AuditEventTypeRegistryTest`, `ProcessingActivityControllerTest` |
| 4 | tab, actions, 7 components, types, schemas, 3 vitest | 9 files (see above) |
