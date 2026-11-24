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
| `26161f9fb` | evidence for A and B |
| `8ac0c3a5e` | Task C backend reproduction tests (RED) |
| `70748dfd5` | Task C backend fix (GREEN) |
| `f0ebb2749` | Task C frontend test (RED) |
| `b7dd7e51f` | Task C frontend fix (GREEN, full frontend gate) |

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

> **Superseded in review round 1 (section E).** `assignRole` now compares the member's effective owner-only capabilities before and after. The two `assignRole` bullets above were too broad: a role holding an owner-only capability blocked even a no-gain edit, which is a regression. They also missed one case: dropping a `-CAP` that kept an owner-only capability away.

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

**Hypothesis (later taken up as Task C below):** `POST /api/invitations` (`InvitationService.java:78-81`, `TEAM_OVERSIGHT`) accepts any `orgRoleId`, with no owner-only check. On acceptance, `MemberFilter.lazyCreateMember` resolves the role by slug among system roles only. So an admin might be able to invite someone straight into the system **Owner** role, and a custom-role invitation may fail to create the member at all. I did not reproduce either; each needs its own ticket and reproduce-first test.

---

## Task C — invitation role escalation. **REPRODUCED (Owner and Admin routes), FIXED**

**How an invitation is accepted** (traced before testing):
- `POST /api/invitations` (`InvitationController`, `@RequiresCapability("TEAM_OVERSIGHT")`) stores a `PendingInvitation` with any `orgRoleId`.
- On the invitee's first authenticated request, `MemberFilter.lazyCreateMember` (`member/MemberFilter.java:155-191`) finds the pending invitation by the JWT email and creates the member with `findSystemRoleBySlug(invitation role slug)`. The invited system role becomes the member's org role.
- `CreateInvitationRequest` is only `(email, orgRoleId)`. There are no capability overrides, so route (4) does not exist.

**Test:** `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationRoleEscalationIntegrationTest.java`. Each case invites as an ADMIN (`TestJwtFactory.adminJwt`) over HTTP, signs the invitee in (a JWT carrying the invited email, `GET /api/me/capabilities`), and only then checks the invitation answer.

**RED** (`taskC-red.txt`: 6 tests, 3 failures):

| Route | Result on the old code |
|---|---|
| (1) admin invites into the **Owner** role | **Open.** 201, and the invitee signs in as an owner: `[invitee is an owner: {"capabilities":["FINANCIAL_RECONCILE", … "SIGN_OFF_TRUST_TAKE_ON" …]} …] Expecting value to be false but was true` |
| (1b) the owner invites into the Owner role | **Open.** `[invitation answer {… "roleSlug":"owner" …}] expected: 403 but was: 201` (assignRole forbids this for everyone) |
| (2) admin invites into the **Admin** role | **Open.** 201, and the invitee signs in as an admin: `[invitee is an admin: …] Expecting value to be false but was true` |
| (3) admin invites into an owner-made custom role holding `SIGN_OFF_TRUST_TAKE_ON` | **Taken (201) but no escalation on this path.** `lazyCreateMember` finds no system role for a custom slug (`IllegalStateException "System role not found for slug: partner-invite-a"`, swallowed in `resolveMember`), so the invitee is never created and their request answers 500 (`MemberContextNotBoundException`). The committed test asserts no member is created. |
| (4) `+CAP` override | **Does not exist** (no overrides on invitations). |

**Fix** (proven routes only):
- `OrgRoleService.requireSystemRoleGrantable(OrgRole)` is extracted from `assignRole` (unchanged rules and messages). The Owner system role is never granted: 403 "Owner role assignment forbidden" / "Cannot assign the Owner system role to a member". The Admin system role is granted only by an owner: 403 "Admin role assignment restricted" / "Only owners can assign the Admin system role".
- `InvitationService.createInvitation` now calls it.
- Route (3) is left unchanged (not proven).

**Existing tests aligned** (they encoded the escalation):
- `InvitationControllerTest`: the admin caller now invites into the Member role instead of Admin.
- `InvitationServiceTest`: the owner-run cases now bind `ORG_ROLE=owner`.

**GREEN backend** (`taskC-green-targeted.txt`, `taskC-targeted-per-class.txt`, classes in `taskC-targeted-classes.txt`): every class in `orgrole/`, `member/` and `invitation/`, every class referencing invitations, plus `LayerDependencyRulesTest`, `TerminologyRemnantScanTest`, `TenantScopeBindingTest` and `TestConventionsTest`. **26 classes / 210 tests, 0 failures, 0 errors.** `InvitationRoleEscalationIntegrationTest` is 6/6.

**Frontend:**
- **Problem:** the invite form offered **Admin** to everyone. **Owner** is not offered today, and the tests keep it absent.
- **Change:** for a non-owner the Admin option is now disabled, with "Only the owner can grant this" (`role-option-system-admin`, `owner-only-role-hint-system-admin`).
- **RED** (`taskC-frontend-red.txt`): 11 tests, 2 failed — `Unable to find an element by: [data-testid="role-option-system-admin"]`.
- **Existing test updated:** `__tests__/invite-role-selection.test.tsx` now renders as the owner. Two of its tests picked Admin without a capability provider, which is now the non-owner view.
- **Full frontend gate** (node 20.11.1):

| Step | Result | Output |
|---|---|---|
| lint | exit 0: 0 errors, 77 warnings | `taskC-gate-lint.txt` |
| format:check | exit 0, clean | `taskC-gate-format.txt` |
| build | exit 0 on the rerun | `taskC-gate-build.txt` |
| vitest | exit 0: **503 files, 4914 passed, 2 skipped** (4916) | `taskC-gate-test.txt` |

The first build attempt printed its complete output, including the route table, and then sat idle in `next build` teardown for about 10 minutes (0% CPU, a turbopack postcss pool worker still alive). It was killed (`taskC-gate-build-hung-attempt1.txt`). The rerun exited 0.

**Not covered:** route (3) above. Custom-role invitations never complete on the JIT sign-in path, which is a separate defect (`MemberFilter.java:183-190`). The Keycloak-side path, where `InvitationService` sends the role slug to Keycloak and `MemberSyncService.resolveOrgRole` resolves any slug including custom ones, was not exercised. If that path grants a custom role, route (3) becomes a real escalation and needs the FR-4 owner-only check on invitations. In the member detail panel, the system **Admin** option is still selectable by a non-owner; the backend refuses it with a 403 whose detail is now shown.

## D. Member panel: the system Admin role is locked for non-owners (orchestrator, follow-through of C)

- **Gap:** after C, the backend refuses a non-owner assigning the system Admin role (`requireSystemRoleGrantable`), but the member detail panel still offered it as selectable. The Admin role holds no owner-only capability, so FR-4's lock did not cover it.
- **Fix:** `member-detail-panel.tsx` `isLockedRole` also locks the system `admin` and `owner` roles for a non-owner, with the same hint.
- **Tests:** two new tests in `owner-only-capability-grants.test.tsx`.
  - Red on the C head: 1 failed / 12 passed (`member-panel-admin-red.txt`; the owner-positive test already passed).
  - Green: 17/17 (`member-panel-admin-green.txt`).
  - With `team/member-role-management.test.tsx`: 18/18.

## Not fixed here (logged for their own reproduce-first items)

- **Inviting into a custom role:** the invitation is accepted (201), but at sign-in no system role matches the custom slug, the invitee is never created, and the invitee gets a 500 (mock-auth path; see C).
  - This is a functional defect, not an escalation.
  - The Keycloak sign-in path needs checking too: if it resolves custom slugs, an owner-only custom role invited by an admin would become a real escalation. C did not block that route; **review round 1 (E) does**: `createInvitation` refuses a non-owner inviting into a role holding an owner-only capability. The functional defect (custom-role invitees never created on the JIT path) is still open.
- ~~`updateRole` concurrent-update race~~: fixed in review round 2 (section F) with row locks, no migration.

---

## E. Review round 1 (PR #1856)

| # | Finding | Fix | Tests |
|---|---|---|---|
| 1 | **MEDIUM** (quality): `assignRole` refused a non-owner whenever the target role held an owner-only capability. An admin could neither edit the ordinary overrides of a member already on an owner-made Partner role nor take `SIGN_OFF_TRUST_TAKE_ON` away. The removal test was a no-op (Bookkeeper never held the capability). | Per the ruling ("only an owner may put an owner-only capability into a member's **effective** capabilities"), `assignRole` computes the member's effective capabilities before and after (role + overrides, via the `effectiveCapabilities(role, overrides)` now shared with `resolveCapabilities`). It refuses a non-owner only for an owner-only capability **gained**. With a role change, the role-sourced part reads "assign a role holding …" and override-sourced parts "grant …". With the role unchanged, a dropped `-CAP` reads "grant an owner-only capability: …". (c), (d) and (e) are still refused, with unchanged messages. | Added to `OwnerOnlyCapabilityGrantIntegrationTest`: admin adds `+INVOICING` to a Partner member → 200 (red: `expected:<200> but was:<403>`); admin applies `-SIGN_OFF_TRUST_TAKE_ON` → 200 and the capability is gone from effective (red: same); admin drops an existing `-SIGN_OFF_TRUST_TAKE_ON` → 403 "Only the owner can grant an owner-only capability: SIGN_OFF_TRUST_TAKE_ON." (red: already 403, but with the role-assignment detail). The no-op removal is replaced by `-INVOICING` on the Bookkeeper role. |
| 2 | **LOW** (security, defence in depth): `createInvitation` checked only the system roles. | Also `OrgRoleService.requireOwnerToGrant(role capabilities, "invite into a role holding an owner-only capability")`, now `public static`. | Route (3) in `InvitationRoleEscalationIntegrationTest` now expects 403 with title/detail (red: `expected: 403 but was: 201`). The owner→Partner invite stays 201. The class Javadoc states exactly what is enforced. |
| 3 | **LOW** (visible defect from this PR): the owner-only hint sat inside `SelectItem`'s `ItemText`, so Radix copied it into the trigger ("AdminOnly the owner can grant this"). | `components/ui/select.tsx` `SelectItem` takes an optional `hint`, rendered after `ItemText` (never mirrored into the trigger). `role-option.tsx` and the invite form's Admin item pass the hint through it. | Trigger text equals the role name exactly for a selected locked Partner role and for the system Admin role, seen by a non-owner (red: `expected 'PartnerOnly the owner can grant this' to be 'Partner'`, and the same for Admin). |
| 4 | **LOW** (a11y): the hint was not tied to its disabled checkbox. | `OwnerOnlyHint` takes an `id`. The checkbox gets `aria-describedby`, with a per-component prefix: `role-dialog-` / `member-panel-` / `invite-owner-only-hint-<CAP>`. | `toHaveAccessibleDescription("Only the owner can grant this")` in the role dialog, member panel and invite form (red: empty description). An ordinary capability has no `aria-describedby`. |

**Red outputs:** `review1-backend-red.txt` (16 tests, 4 failures) and `review1-frontend-red.txt` (19 tests, 5 failed).

**Green backend** (`review1-backend-green.txt`, `review1-targeted-per-class.txt`, list in `review1-targeted-classes.txt`): every class in `orgrole/`, `member/`, `invitation/` and `architecture/`, every class referencing invitations, and `TakeOnRevalidationAnonymisationRaceIntegrationTest`. **30 classes / 224 tests, 0 failures, 0 errors.**

**Full frontend gate** (node 20.11.1, `/opt/homebrew/bin/pnpm`):

| Step | Result | Output |
|---|---|---|
| lint | exit 0: 0 errors, 77 warnings | `review1-gate-lint.txt` |
| format:check | exit 0, clean | `review1-gate-format.txt` |
| build (`timeout 900`) | exit 0 | `review1-gate-build.txt` |
| vitest | exit 0: **503 files, 4922 passed, 2 skipped** (4924) | `review1-gate-test.txt` |

---

## F. Review round 2 (CodeRabbit first review of PR #1856)

| # | Finding | Fix | Tests |
|---|---|---|---|
| 1 | **MAJOR** (security): `updateRole` computed the "added" capabilities from a plain read and wrote the whole list back. An owner's removal between the admin's read and save was therefore undone. `assignRole` had the same window. | No migration. `OrgRoleRepository.findByIdForUpdate` and `MemberRepository.findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`, `SELECT … FOR UPDATE`) are now taken first in `updateRole` and `assignRole`, in the same transaction. A concurrent change waits and lands after. | New `OwnerOnlyGrantRaceIntegrationTest` (spy on `AuditService.log` pauses the admin after its read and entity change, before its flush; the owner runs; then release). **Red** (`review2-race-red.txt`, 2/2 failed): updateRole → `["INVOICING", "SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST"]`, the owner's removal undone; assignRole (admin moves a Partner member to a second SIGN_OFF role while the owner moves them to Bookkeeper) → effective `["SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST"]`. **Green** (`review2-race-green.txt`): 2/2, each about 3 s (the owner's request waited on the lock). |
| 2 | Minor: the role dialog would not let an admin uncheck an existing owner-only capability. | For a non-owner an owner-only box is disabled only while **unchecked**. Unchecking is allowed; checking (granting) is not. The hint and `aria-describedby` stay on every owner-only box for a non-owner. | Admin unchecks SIGN_OFF on Partner → payload `["VIEW_TRUST"]`, and the box is then disabled (red: "Received element is not enabled"). Admin cannot check an unchecked owner-only box. The round-1 "keeps unchanged" test is replaced. |
| 3 | Minor: the member panel overrides had the same problem. | Same rule against the effective state: a non-owner may drop a `+X` or add a `-X` for an inherited X (box checked → enabled). They may not add a `+X` or drop an existing `-X` (box unchecked → disabled). | Adds `-SIGN_OFF` for the inherited capability → saved overrides `["-SIGN_OFF_TRUST_TAKE_ON"]` (red). Drops `+SIGN_OFF` → `[]` (red). Cannot add `+X` (pass). Cannot drop `-X` (pass). |
| 4 | Minor (test): the take-on race test skipped its joins when an assertion failed inside `try`. | Both worker threads are declared outside the `try`, and both are joined (with timeouts) in `finally`, before `@AfterEach` resets the spy. | Green, 1/1. |
| 5 | Minor (test): the 409 assertion checked only the code. | It now parses the ProblemDetail: status 409, code `TAKE_ON_CONCURRENT_CHANGE`, title "Take-on batch changed", detail "This take-on batch was changed at the same time. Try again." | Green, 1/1. |

**Observed while building test 1:** an override-only edit with the role unchanged is **not** a race. Hibernate writes no `members` row (the entity is not dirty), and it writes the overrides collection as a diff, so the owner's concurrent `-SIGN_OFF` survived. The member race needs a role change, which is what the committed test uses.

**Unit stubs:** `OrgRoleServiceTest` and `MemberFilterCacheEvictionTest` were updated for the new finders.

**Red frontend:** `review2-frontend-red.txt` (24 tests, 3 failed).

**Green backend** (`review2-backend-green.txt`, `review2-targeted-per-class.txt`, `review2-targeted-classes.txt`): every class in `orgrole/`, `member/`, `invitation/` and `architecture/`, every class referencing invitations, and `TakeOnRevalidationAnonymisationRaceIntegrationTest`. **31 classes / 226 tests, 0 failures, 0 errors.**

**Full frontend gate** (node 20.11.1, `/opt/homebrew/bin/pnpm`):

| Step | Result | Output |
|---|---|---|
| lint | exit 0: 0 errors, 77 warnings | `review2-gate-lint.txt` |
| format:check | exit 0, clean | `review2-gate-format.txt` |
| build (`timeout 900`) | exit 0 | `review2-gate-build.txt` |
| vitest | exit 0: **503 files, 4927 passed, 2 skipped** (4929) | `review2-gate-test.txt` |

---

## G. Review round 3 (security delta review on 664d22a9b, HIGH)

**Finding:** `assignRole` accepted an override set holding both `+X` and `-X`. `effectiveCapabilities` applied overrides in set-iteration order (last wins). The round-1 gain check evaluated the **request's** set, while the member's capabilities resolve from the set **reloaded** from `member_capability_overrides`. If the two iterate differently, the check could see "nothing gained" while the stored overrides grant X. Round 1 introduced this: before it, any `+OWNER_ONLY` from a non-owner was refused outright.

**Resolution sites:** a grep across `backend/src/main` for `resolveCapabilities`, `effectiveCapabilities`, `capabilityOverrides` and `startsWith("+")` found **one** implementation, `OrgRoleService.effectiveCapabilities(role, overrides)`. It sits behind `resolveCapabilities` (MemberFilter, `GET …/capabilities`) and the gain check. `TakeOnValueParser`'s `startsWith("+")` is unrelated.

**Fix** (`backend/.../orgrole/OrgRoleService.java`):
1. **Validation:** `assignRole` refuses an override set naming the same capability with both signs. The response is 400 `InvalidStateException` with title "Contradictory capability override" and detail "An override cannot both add and remove the same capability: X." It applies to owners and non-owners alike, after the format checks and before the owner-only gain check.
2. **Order-independent resolution:** `effectiveCapabilities` adds every `+` override, then removes every `-` override (`overrideCapabilities(overrides, sign)`), so `-` wins whatever the iteration order. Stored data that already holds both therefore resolves without X. This matches the frontend's `isEffectivelyEnabled`, which checks `-` before `+`.

**Tests:**

| Test | Red (`review3-backend-red.txt`, on 664d22a9b) | Green |
|---|---|---|
| (a) `ContradictoryOverrideIntegrationTest.anAdminCannotSendAContradictoryOverridePair`: admin, Bookkeeper member, `["+MANAGE_COMPLIANCE_DESTRUCTIVE","-MANAGE_COMPLIANCE_DESTRUCTIVE", +11 fillers]` (13 elements: the request HashSet resizes to 32 buckets, so `-` iterates last and the old gain check saw nothing gained) | `expected: 400 but was: 200`, the pair was accepted. The member did **not** end up holding the capability in this run: the stored set resolved the same way. Making the two sets iterate differently was not shown here. | 400 with ProblemDetail status, title and detail |
| (c) `theOwnerCannotSendAContradictoryOverridePairEither`: owner, `["+INVOICING","-INVOICING"]` | `expected: 400 but was: 200` | 400 |
| (b1) `storedOverridesHoldingBothSignsResolveWithTheMinusWinning`: both `±SIGN_OFF_TRUST_TAKE_ON` rows inserted with JDBC; read through `/api/me/capabilities` (what MemberFilter binds) and the owner's view | `["INVOICING", "SIGN_OFF_TRUST_TAKE_ON"]`: granted | SIGN_OFF absent in both views |
| (b2) `OrgRoleServiceTest.resolveCapabilities_contradictoryPair_minusWinsInEitherIterationOrder`: a `LinkedHashSet` in both orders | one order resolved INVOICING (last wins) | neither order holds SIGN_OFF or INVOICING |

The new class has its own tenant because the FR-4 test class's tenant is at `max-members: 10`.

**Green backend** (`review3-backend-green.txt`, `review3-targeted-per-class.txt`, list in `review3-targeted-classes.txt`): every class in `orgrole/`, `member/`, `invitation/` and `architecture/`; every class referencing invitations, `capabilityOverrides`, `setCapabilityOverrides`, `resolveCapabilities`, `MemberFilter` or `/api/me/capabilities`; and the take-on race test. **65 classes / 594 tests, 0 failures, 0 errors.**

**Frontend:**
- **The bug:** unchecking a capability that the role holds and that also carries a stored, redundant `+X` sent `["+X","-X"]`.
- **The fix:** `member-detail-panel.tsx` `toggleCapability` flips the effective state and keeps at most one override per capability: none when the new state is the role default, else `+X` or `-X`.
- **Vitest:** two tests (`review3-frontend-red.txt`: 26 tests, 2 failed).
  - Uncheck now sends `["-INVOICING"]`. It was `["+INVOICING","-INVOICING"]`.
  - Re-check restores `[]`. It was `["+INVOICING"]`.
- **Invite form:** its overrides start empty and the role's capabilities are fixed there, so it cannot reach that state. It also does not send overrides at all.

**Full frontend gate** (node 20.11.1, `/opt/homebrew/bin/pnpm`):

| Step | Result | Output |
|---|---|---|
| lint | exit 0: 0 errors, 77 warnings | `review3-gate-lint.txt` |
| format:check | exit 0, clean | `review3-gate-format.txt` |
| build (`timeout 900`) | exit 0 | `review3-gate-build.txt` |
| vitest | exit 0: **503 files, 4929 passed, 2 skipped** (4931) | `review3-gate-test.txt` |
