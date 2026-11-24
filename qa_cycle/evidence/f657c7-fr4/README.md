# F-657C-7 + FR-4 — reproduce-first evidence

Branch `fix/f657c7-fr4-take-on-revalidation-race-and-owner-only-grants`, base `origin/main` @ `aac4e50a0`.
Only targeted test runs (`./mvnw -q test -Dtest=…`). **No full `./mvnw verify` was run**; the merge bar is still open.

| Commit | What |
|---|---|
| `434818cac` | F-657C-7 guard test (not reproduced, no production change) |
| `56a5d13dd` | FR-4 reproduction tests (RED on unchanged code) |
| `58b972a99` | FR-4 fix in `OrgRoleService` + test probe tweak (GREEN) |
| `22ef8d0d7` | this evidence (first round) |
| `7cd6c63d0` | Task B reproduction test (RED) |
| `99192c0ba` | Task B fix (GREEN) |
| `c87c87eb3` | Task A frontend tests (RED) |
| `35246e379` | Task A frontend fix (GREEN, full frontend gate) |

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

**Follow-up (fixed in Task B below):** `GET /api/members/{id}/capabilities` answered 500 for a member on the system Admin role. The Owner role was affected too. The root cause is now confirmed; see Task B.

---

## Task A — frontend: owner-only capabilities in every grant UI (FR-4 follow-through). **REPRODUCED, FIXED**

**Places a non-owner can grant capabilities** (found with grep on `capabilityOverrides`, `orgRoleId` and `CAPABILITY_META`):
- `frontend/components/roles/role-dialog.tsx`: create and edit a role.
- `frontend/components/team/member-detail-panel.tsx`: assign a member's role, plus the `+CAP`/`-CAP` override checkboxes.
- `frontend/components/team/invite-member-form.tsx`: the custom role for an invitation, plus "Customize for this user". Its overrides are not sent to the backend today (`inviteMemberBff` ignores them), but the rule was applied for consistency.

Only `SIGN_OFF_TRUST_TAKE_ON` among the owner-only capabilities appears in `CAPABILITY_META`. The other three are not shown in any of these UIs. The rule is still keyed on `OWNER_ONLY_CAPABILITIES`, so all four are covered.

**Behaviour, non-owner:**
- Every owner-only capability checkbox is `disabled` and keeps its current checked state. Editing an owner-made Partner role therefore re-sends `SIGN_OFF_TRUST_TAKE_ON` unchanged.
- Each such checkbox shows the visible hint "Only the owner can grant this".
- Every role holding an owner-only capability is a disabled select option with the same hint.

**Owner:** unchanged. `useCanGrantOwnerOnly()` reads `isOwner` from the capability context and answers false outside a provider, so it is never wider than the backend.

**Test ids:**

| Where | Test id |
|---|---|
| Checkbox in the role dialog | `role-capability-<CAP>` |
| Checkbox in the member panel | `member-capability-<CAP>` |
| Checkbox in the invite form | `invite-capability-<CAP>` |
| Hint on a capability | `owner-only-hint-<CAP>` |
| Role option | `role-option-<roleId>` |
| Hint on a role option | `owner-only-role-hint-<roleId>` |

**403 detail:**
- Before: `createRoleAction`, `updateRoleAction` and `deleteRoleAction` (`app/(app)/org/[slug]/settings/firm/_tabs/roles-actions.ts`) and `assignMemberRole` (`app/(app)/org/[slug]/team/member-actions.ts`) replaced every 403 with a generic permission message.
- Now: they return the ProblemDetail `detail` when there is one, and keep the generic message otherwise.

**Files:** `lib/capabilities.tsx` (`holdsOwnerOnlyCapability`, `OWNER_ONLY_GRANT_HINT`, `useCanGrantOwnerOnly`), new `components/roles/owner-only-hint.tsx` and `components/roles/role-option.tsx`, plus the three components and two action files above.

**Tests:** `frontend/__tests__/owner-only-capability-grants.test.tsx` (9 tests: owner vs non-owner per place) and `frontend/__tests__/owner-only-grant-actions.test.ts` (4 tests).

**RED** (`taskA-red.txt`; component and action changes stashed, new tests run alone): 13 tests, **11 failed, 2 passed**. The two that passed are the owner creating a role in the dialog and a detail-less 403 keeping the generic message. Excerpts:
- `expect(element).toBeDisabled() — Received element is not disabled: <button aria-checked="false" aria-label="Sign off trust take-on"`
- `Unable to find an element by: [data-testid="role-option-role-partner"]`
- `AssertionError: expected { success: false, …(1) } to deeply equal { success: false, …(1) }` (generic message instead of the detail)

**GREEN, full frontend gate** from `frontend/` (nvm node 20.11.1, `/opt/homebrew/bin/pnpm`):

| Step | Result | Output |
|---|---|---|
| `pnpm run lint` | exit 0: 0 errors, 77 warnings | `taskA-gate-lint.txt` |
| `pnpm run format:check` | exit 0, "All matched files use Prettier code style!" | `taskA-gate-format.txt` (after `prettier --write` on 4 touched files) |
| `pnpm run build` | exit 0 | `taskA-gate-build.txt` |
| `pnpm test` | exit 0: **503 files, 4912 passed, 2 skipped** (4914) | `taskA-gate-test.txt` |

The lint warning reported on a touched file (`invite-member-form.tsx` `form.watch`, react-hooks/incompatible-library) is on a pre-existing line. I did not compare the warning count against the base.

---

## Task B — backend: `GET /api/members/{id}/capabilities` 500 for system-role members. **REPRODUCED, FIXED**

**Test:** `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgrole/MemberCapabilitiesEndpointIntegrationTest.java`. It is an owner caller on the HTTP path. Targets: an admin member, the owner, and a custom-role member with a `+VIEW_TRUST` override. The helper prints the resolved exception's cause chain.

**RED** (`taskB-red.txt`): 3 tests, 2 failures.
- `aMemberOnTheSystemAdminRole: Status expected:<200> but was:<500>`
- `theOwner: Status expected:<200> but was:<500>`
- The custom-role member passed.

The root cause comes from the stack trace, not a guess:
```
HttpMessageNotWritableException: Could not write JSON: Cannot lazily initialize collection of role
'io.b2mash.b2b.b2bstrawman.member.Member.capabilityOverrides' with key '…' (no session)
Caused by: tools.jackson.databind.DatabindException: …
Caused by: org.hibernate.LazyInitializationException: …
```
`OrgRoleService.getMemberCapabilities` put the member's lazy `@ElementCollection` (`Member.java:47`) into the response. For the system owner/admin roles, `resolveCapabilities` returns before reading the overrides, so the collection was still uninitialised when Jackson wrote it after the read-only transaction (`spring.jpa.open-in-view: false`). Custom-role members worked only because `resolveCapabilities` iterates the overrides.

**Fix** (small, local; `OrgRoleService.getMemberCapabilities`): return `Set.copyOf(member.getCapabilityOverrides())`, which is read inside the transaction.

**GREEN:**
- `taskB-green.txt`: 3/3.
- `taskB-green-targeted.txt` + `taskB-targeted-per-class.txt`: every class in `orgrole/` and `member/`, **19 classes / 174 tests, 0 failures, 0 errors**.
- `OwnerOnlyCapabilityGrantIntegrationTest` was rerun green after its comment about the 500 was updated.

**Machine-sharing note:** my first Task B red run (one class, about 18 s) started while the 657D full verify was live. My `pgrep` check printed it, but the command did not stop on it. The red run recorded here is a clean rerun made after that verify finished. Every later run waited for a clear machine. The wait now uses a self-excluding pattern, because `pgrep -f 'surefire'` also matches other agents' polling shells and the waiter itself.

**Unverified hypothesis (not touched):** `POST /api/invitations` (`InvitationService.java:78-81`, `TEAM_OVERSIGHT`) accepts any `orgRoleId`, with no owner-only check. On acceptance, `MemberFilter.lazyCreateMember` resolves the role by slug among system roles only. So an admin might be able to invite someone straight into the system **Owner** role, and a custom-role invitation may fail to create the member at all. I did not reproduce either; each needs its own ticket and reproduce-first test.
