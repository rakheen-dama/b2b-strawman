# F-657C-7 + FR-4 — reproduce-first evidence

Branch `fix/f657c7-fr4-take-on-revalidation-race-and-owner-only-grants`, base `origin/main` @ `aac4e50a0`.
Only targeted test runs (`./mvnw -q test -Dtest=…`). **No full `./mvnw verify` was run**; the merge bar is still open.

| Commit | What |
|---|---|
| `434818cac` | F-657C-7 guard test (not reproduced, no production change) |
| `56a5d13dd` | FR-4 reproduction tests (RED on unchanged code) |
| `58b972a99` | FR-4 fix in `OrgRoleService` + test probe tweak (GREEN) |

---

## Bug 1 — F-657C-7: re-validation vs a client anonymisation. **NOT REPRODUCED**

**Hypothesis** (657C found.md): `TakeOnValidationService.validate` reads the batch's rows without a lock and Hibernate writes each row back whole (`TakeOnRow` has no `@Version` / `@DynamicUpdate`). If an anonymisation commits between that read and the flush, the stale values would overwrite the redaction.

**Test:** `backend/src/test/java/io/b2mash/b2b/b2bstrawman/takeon/TakeOnRevalidationAnonymisationRaceIntegrationTest.java`, test `anAnonymisationCommittingDuringARevalidation_keepsItsRedaction`.
- It creates a validated trust batch whose client-ledger row names client R.
- A `@MockitoSpyBean TrustPositionDatasetHandler` pauses the re-validation (`POST …/validate`) at its first `mapAndValidate`. At that point every row has been read and none written.
- `POST /api/clients/{R}/anonymize` then runs and commits. The test checks that 1 redacted row is committed and then releases the re-validation.
- It asserts that afterwards no row names R, exactly 1 row is still `{"redacted": true}` in both value columns, and no raw values quote "R1". It also asserts that the re-validation answered 409 `TAKE_ON_CONCURRENT_CHANGE`.

**Attempts, in order:**
1. **Pause at `validateBatchLevel`** (after every row is mapped). The anonymisation could not finish in 30 s. Diagnostics showed `redactForClient` waiting on a `transactionid` lock held by the paused re-validation, which was "idle in transaction". The handler's queries had already autoflushed the rows, so the re-validation held their row locks. In that window the anonymisation simply waits. Output: `bug1-attempt1-late-pause-lock-wait.txt`.
2. **Pause at the first `mapAndValidate`** (the real read→flush window). The anonymisation committed, then the re-validation was released. **Redaction survived.** The re-validation got **409 TAKE_ON_CONCURRENT_CHANGE**: `validate` runs at `REPEATABLE_READ`, its snapshot predates the redaction, and Postgres refuses its UPDATE of the redacted row with 40001. `TakeOnBatchService.validate` maps that to 409 (C-21). Output: `bug1-not-reproduced.txt` (1 test, 0 failures).
3. **Sensitivity probe.** This was a temporary edit, reverted with `git checkout` and never committed. I changed `validate` to `READ_COMMITTED`. The test then fails with exactly the lost update the finding describes:
   ```
   [redacted take-on rows after the re-validation (HTTP 200)]
   expected: 1L
    but was: 0L
   ```
   Output: `bug1-mutation-read-committed.txt`.

**Why the hypothesis does not hold:** the `REPEATABLE_READ` isolation on `TakeOnValidationService.validate` turns the lost update into a serialization failure (409), and once the rows are flushed their row locks make the anonymisation wait. **No fix was applied.** The test is committed as a regression guard; if the isolation is ever weakened, it fails.

---

## Bug 2 — FR-4: admins could grant owner-only capabilities. **REPRODUCED, FIXED**

**Hypothesis:** `TEAM_OVERSIGHT` (which admins hold) opens role create/update and `PUT /api/members/{id}/role`, but `OrgRoleService` never checks `Capability.OWNER_ONLY` (`MANAGE_COMPLIANCE_DESTRUCTIVE`, `OVERRIDE_MATTER_CLOSURE`, `FINANCIAL_RECONCILE`, `SIGN_OFF_TRUST_TAKE_ON`).

**Tests:** `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/OwnerOnlyCapabilityGrantIntegrationTest.java` (HTTP path, `TestJwtFactory.adminJwt` caller). The effect probe is `GET /api/take-on/sign-off-queue` (`@RequiresCapability("SIGN_OFF_TRUST_TAKE_ON")` + `VIEW_TRUST`).

**RED on unchanged code** (`bug2-red.txt`: 7 run, 5 failures). All five routes were open:

| Route | Test | Red assertion |
|---|---|---|
| (a) create role with `SIGN_OFF_TRUST_TAKE_ON` | `anAdminCannotCreateARoleHoldingAnOwnerOnlyCapability` | `Status expected:<403> but was:<201>` |
| (b) update role adding it | `anAdminCannotAddAnOwnerOnlyCapabilityToARole` | `Status expected:<403> but was:<200>` |
| (c) assign owner-made Partner role to another member | `anAdminCannotAssignARoleHoldingAnOwnerOnlyCapabilityToAnotherMember` | the member then got the sign-off queue: `Status expected:<403> but was:<200>` |
| (d) assign it to themselves | `anAdminCannotAssignARoleHoldingAnOwnerOnlyCapabilityToThemselves` | the admin then got the sign-off queue: `Status expected:<403> but was:<200>` |
| (e) `+SIGN_OFF_TRUST_TAKE_ON` override | `anAdminCannotGrantAnOwnerOnlyCapabilityAsAnOverride` | the member then got the sign-off queue: `Status expected:<403> but was:<200>` |

`anAdminStillManagesAndAssignsOrdinaryRoles` and `anOwnerCanStillCreateUpdateAndAssignOwnerOnlyCapabilities` passed on the old code too.

**Fix** (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java`): a new private `requireOwnerToGrant(capabilities, action)`. When the caller's org role (`RequestScopes.getOrgRole()`) is not `owner` and any `OWNER_ONLY` capability is named, it throws `ForbiddenException` with title `Owner-only capability` and detail `Only the owner can <action>: <CAPS sorted>.`. It is called from:
- `createRole`, on the requested capabilities.
- `updateRole`, on the **added** capabilities only. Removing or keeping an owner-only capability stays open to admins.
- `assignRole`, on the target role's stored capabilities.
- `assignRole`, on `+` overrides only. `-CAP` stays open.

No data migration. Existing roles and assignments are untouched.

**GREEN:**
- `bug2-green-targeted.txt`: 80 classes, **873 tests, 1 failure, 14 skipped**. The 1 failure was a probe inside the new test (d): `GET /api/members/{id}/capabilities` answered **500** (`HttpMessageNotWritableException`) for a member on the system **Admin** role, *after* the fix had correctly refused the self-assignment. The probe now reads `/api/me/capabilities` instead (asserts role `Admin` and no `SIGN_OFF_TRUST_TAKE_ON`).
- `bug2-green-rerun.txt`: rerun of `OwnerOnlyCapabilityGrantIntegrationTest` (7/0), `RoleAssignmentTest` (11/0), `OrgRoleControllerTest` (13/0), `OrgRoleServiceTest` (20/0).
- `bug2-targeted-per-class.txt`: per-class counts for the 80 classes. 76 come from the batch run and the 4 orgrole classes from the rerun; all 0 failures / 0 errors. The class list is in `bug2-targeted-classes.txt`. It covers every class in `orgrole/` and `member/`, every test that drives `/api/org-roles`, `/role` or `capabilityOverrides`, and the capability-gated suites (`TakeOnSignOffAccessIntegrationTest`, `AccountingSyncControllerIntegrationTest`, `AnonymizationControllerTest`, MCP gates, trust report capability). It also includes the architecture scanners `LayerDependencyRulesTest`, `TenantScopeBindingTest`, `TestConventionsTest`, `TerminologyRemnantScanTest`, `PortalLinkBuilderArchTest`, `ZoneAwareTimeArchTest`, `TakeOnEntryPointsArchTest`, `TrustPostingRulesTest` and `DeadlineCopyRuleTest`.

**Separate finding (not fixed, out of scope):** `GET /api/members/{id}/capabilities` returns 500 for a member on the system Admin role. This was observed in this run. The suspected cause is not verified: `Member.capabilityOverrides` is a lazy `@ElementCollection`, and `resolveCapabilities` returns early for system admin/owner roles without touching it, so the response serialises an uninitialised collection after the read-only transaction closes.
