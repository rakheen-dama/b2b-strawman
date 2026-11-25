# Invitation fixes: custom-role sign-in, FR-8 revoke, FR-7 invite form

Branch `fix/invitations-custom-role-revoke-and-no-invite-customisation`, base `origin/main` @ `522eb3413`.
Founder-authorised as one PR (related invitation bugs). Local commits only: no push, no PR, no full verify, no E2E.
Status: **CODE-COMPLETE, TARGETED-GREEN (not merged).** The full `bash scripts/verify.sh` and a Keycloak-stack
E2E/OBS run are still owed (see "Not done").

Logs sit next to this file as `.txt` copies (the original `*.log` files are gitignored):
`fix1-red.txt`, `fix1-green.txt`, `fix2-red.txt`, `fix2-green.txt`, `fix2b-red.txt`, `fix2b-green.txt`,
`fix3-red.txt`, `fix3-green.txt`, `targeted-backend.txt`, `frontend-gate-*.txt`.

## Commits (tests first per fix)

| # | SHA | What |
|---|-----|------|
| 1 | `2df9f9200` | test: custom-role invitee first sign-in + role delete while invited (red) |
| 2 | `f395c2636` | fix: apply a custom-role invitation's stored role at first sign-in |
| 3 | `20f086665` | test: revoking an invitation revokes the Keycloak invite (FR-8, red) |
| 4 | `4237aa195` | fix: revoking an invitation revokes the Keycloak invite (FR-8) |
| 5 | `f6eca73a2` | test: the invite form offers no capability customisation (FR-7, red) |
| 6 | `afbaf0cf4` | fix: remove capability customisation from the invite form (FR-7) |
| 7 | `8b1d6ce34` | test: exhaustive Keycloak invitation lookup on revoke (FR-8 review finding, red) |
| 8 | `46ff637f1` | fix: look the Keycloak invitation up exhaustively on revoke (FR-8 review finding) |

## Fix 1: a custom-role invitee gets 500s at first sign-in (plan option A)

**Red** (`fix1-red.txt`, on `522eb3413`): 6 failures in 15 tests.
- `MemberFilterInvitationTest.shouldAssignCustomInvitationRoleOnFirstLogin`, `…WithKeycloakFormatJwt` and
  `customInviteeIgnoresTokenRoleClaims`: `Status expected:<200> but was:<500>`. The body is ProblemDetail
  `"Member context not available"` (10 occurrences in the log).
- `InvitationRoleEscalationIntegrationTest.theOwnerStillInvitesIntoTheAdminRoleAndAPartnerRole` and
  `anAdminStillInvitesIntoTheMemberRoleAndOrdinaryRoles`, now extended to sign the invitee in: `200 expected, 500`.
- `aCustomRoleOfferedByAPendingInvitationCannotBeDeleted`: `409 expected, 500`. The 500 comes from the
  `pending_invitations_org_role_id_fkey` FK violation.

**Fix**
- `MemberFilter.lazyCreateMember` applies the role by the id the invitation stores, through the new
  `OrgRoleService.findRoleForInvitation(UUID)`. That is the exact role that passed the #1856 guards in
  `InvitationService.createInvitation`.
- The role is never re-resolved from a slug or from any token claim.
- No invitation → the system Member role.
- The founding-user → Owner rule is unchanged: it applies only when the resolved role is the system Member and the tenant has no members.
- `MemberInfo` carries `orgRole.getSlug()`.

**Decision: a custom role deleted after the invitation was sent**
- What already happens: `pending_invitations.org_role_id` is `NOT NULL` with an FK and no `ON DELETE`. `deleteRole` checked members only.
- So a role referenced by an invitation can never actually be deleted. The attempt answered **500**.
- Chosen behaviour: **refuse the deletion while a PENDING invitation offers the role.** The answer is a 409 ProblemDetail
  `"Role in use"` / `"Cannot delete role 'X' because it is offered in N pending invitation(s). Revoke the invitation(s) first."`
- Why this is the safest option: the invitee always lands on the exact role the inviter chose. There is no silent fallback to Member, and no surprise role.
- Not chosen: "fall back to Member", which would be a silent downgrade.

**Green** (`fix1-green.txt`): MemberFilterInvitationTest 9/9, InvitationRoleEscalationIntegrationTest 6/6, MemberFilterCacheEvictionTest 3/3.
New tests:
- custom role, Clerk-format JWT
- custom role, Keycloak-format JWT (`organization` claim, no `o`)
- token role claims (`o.rol`/`role`/`org_role` = owner) are ignored
- an expired custom-role invitation falls back to Member (unchanged)
- role delete while invited → 409, after which the invitee still lands on the role
- the escalation test now signs in the Bookkeeper (`INVOICING` only, not admin) and the owner-invited Partner role, which holds the owner-only
  `SIGN_OFF_TRUST_TAKE_ON` plus `VIEW_TRUST` (lands with exactly that role, isOwner/isAdmin false)

**Files**: `member/MemberFilter.java`, `orgrole/OrgRoleService.java`, `invitation/PendingInvitationRepository.java`;
tests `member/MemberFilterInvitationTest.java`, `invitation/InvitationRoleEscalationIntegrationTest.java`,
`member/MemberFilterCacheEvictionTest.java` (constructor arg only).

## Fix 2: FR-8, revoking an invitation must revoke the Keycloak invite

**What Keycloak does.** Read from the Keycloak **26.5.0** source, which is the version in `compose/docker-compose.yml` and `../keycloak-saas/docker/Dockerfile`.
- `POST /organizations/{id}/members/invite-user` (`OrganizationInvitationResource.inviteUser/sendInvitation`)
  **persists an invitation record** (id, lowercased email) and emails a link whose token id is that invitation id.
  It does **not** add org membership. It answers 204 with no body, so the id is not returned.
- Membership is added **only on acceptance**, in one of two places:
  - `RegistrationUserCreation.addOrganizationMember` for a new user
  - `InviteOrgActionTokenHandler.handleToken` for an existing user
- Both places then **delete** the invitation record.
- Both paths **re-check that the record still exists** before accepting. `invitationManager.getById(token.getId()) == null` → "invitation expired or no longer valid".
- So deleting the Keycloak invitation kills the emailed link.
- `GET /organizations/{id}/invitations` supports `email` (a SQL **LIKE** `%email%`), `status`, `first`, `max`.
  `DELETE /organizations/{id}/invitations/{invId}` removes the record.

**Does an old link still create a Member after revoke?** Before this fix, yes: the link stayed valid. After the person registered, Kazi found no
pending invitation, so `MemberFilter` created them as **Member**. After this fix, the Keycloak invitation is deleted, so the link is refused by Keycloak.
Membership is not created at invite time, so there is nothing else to undo. One residual path remains:
1. The person **already used the link**, so they are a Keycloak org member and Keycloak has deleted its invitation.
2. They have not yet opened Kazi, so the Kazi invitation is still PENDING.
3. Revoking only the Kazi row would let them in as Member.

This fix detects that case and refuses it (below).

**Red** (`fix2-red.txt`): 4 failures in 9 tests.
- `revokeInvitation(...)`: *Wanted but not invoked*.
- 3 cases: the expected refusal was not thrown (`Expecting actual not to be null`).

**Fix** (`InvitationService.revokeInKeycloak`)
- Runs in Keycloak mode only, inside the revoke transaction.
- Mock/e2e mode (no `KeycloakAdminClient` bean) and calls without an org context are unchanged.
- Steps:
  1. `findInvitationIdsByEmail(kcOrgId, email)` finds every Keycloak invitation for this exact email (see the review fix below).
  2. Each one is deleted with `revokeInvitation(kcOrgId, id)`.
  3. If none is found, check membership explicitly: `findUserByEmail` plus the paged `listOrgMemberIds`.
     - **Member** → **409** `"Invitation already accepted"`: "'x' has already accepted the invitation email and joins with the invited role on their first sign-in, so it can no longer be revoked. Remove them from the team once they appear." The Kazi invitation stays PENDING, so they join with the invited role, not as Member.
     - **Not a member** → Keycloak holds no invitation record, so no link can work. This happens, for example, when the best-effort invite-user call failed at creation. The Kazi revoke proceeds, with a WARN log.

**Decision: what happens when the Keycloak call fails.** **The revoke fails closed.**
- Any Keycloak error (lookup, membership check or delete) → **400** ProblemDetail `"Invitation not revoked"`:
  "The invitation email for 'x' could not be cancelled in Keycloak, so its link may still work. Nothing was changed. Please try again."
- The Kazi revoke rolls back, so the invitation stays PENDING.
- Why: a revoke never reports success while the link may still work, and the user is told plainly.
- Not chosen: "revoke locally + warning", because it leaves a live link behind a revoked invitation.
- 400 matches how `KeycloakAdminClient` already reports Keycloak errors (`InvalidStateException`).
- The frontend's `revokeInvitationBff` shows the ProblemDetail detail.
- Retrying is safe: on a second attempt, invitations already deleted are simply not found.

**Review finding (MEDIUM, from the coordinator's automated review) and its fix**
- Finding: the first version used `listInvitations(kcOrgId)`, one unpaged request filtered in memory. A Keycloak that pages could hide the target, and the
  empty result would then read as "nothing to revoke". That fails open.
- Keycloak 26.5.0 itself returns all rows when `first`/`max` are absent (`paginateQuery` with nulls), but that is not a contract to rely on.
- Fix: the new `KeycloakAdminClient.findInvitationIdsByEmail` uses the `email` filter, pages with `first`/`max` = 100 until a short page, and keeps
  exact (case-insensitive) email matches only, because the filter is a LIKE. Errors propagate and are never read as "none".
- An empty result is never taken as success on its own. The membership check above decides it, and any error there also fails closed.
- On the coordinator's point (b), "empty and not a member → not reported as success": I kept a Kazi-side revoke for that case. Once the exhaustive
  lookup is empty and the person is not an org member, Keycloak holds no invitation record and rejects every link (both acceptance paths re-check
  the record). There is therefore no live link.
- Refusing that case instead would make invitations whose Keycloak email never went out impossible to revoke. They would stay PENDING for 7 days
  and block a re-invite (Kazi 409 "Pending invitation exists"). **Flagged for the orchestrator to confirm.**
- Red (`fix2b-red.txt`): compilation fails, `cannot find symbol: method findInvitationIdsByEmail`.
- Green (`fix2b-green.txt`): KeycloakAdminClientInvitationLookupTest 3/3 (WireMock: target found on page 2 past 100 LIKE matches; no exact match → empty;
  503 → throws), InvitationServiceTest 10/10, InvitationControllerTest 10/10.

**Tests** (`InvitationServiceTest`, `KeycloakAdminClient` as `@MockitoBean`, as the class already did):
- revokes the Keycloak invitation for that email
- delete fails → 400, stays PENDING
- lookup unreachable → 400, stays PENDING
- already accepted (org member) → 409, stays PENDING, nothing deleted
- no invitation and not a member → decided explicitly (membership checked) → REVOKED
- membership check errors → 400, stays PENDING
- no org context → Keycloak skipped
- `InvitationControllerTest` (no Keycloak bean = mock mode): revoke still 204/400/404

**Files**: `invitation/InvitationService.java`, `security/keycloak/KeycloakAdminClient.java`; tests
`invitation/InvitationServiceTest.java`, `security/keycloak/KeycloakAdminClientInvitationLookupTest.java` (new).

## Fix 3: FR-7, hide "Customize for this user" in the invite form

**Red** (`fix3-red.txt`): 3 failures in 38 tests.
- `never offers 'Customize for this user' at invite time (owner|admin)`: found the collapsible trigger.
- `sends orgRoleId with custom role on invite`: `inviteMember` was called with a 4th (overrides) argument.

**Fix**
- `components/team/invite-member-form.tsx`: removed the overrides state, the toggle helpers and the collapsible checkbox panel,
  along with the imports they used (`Checkbox`, `Collapsible*`, `ChevronRight`, `OWNER_ONLY_CAPABILITIES`).
- Kept: the custom-role capability pills, the owner-only lock on the Admin option, and the owner-only custom-role lock from PR #1856.
- `app/(app)/org/[slug]/team/invitation-actions.ts`: `inviteMember` and `inviteMemberBff` dropped the unused overrides parameter. It was
  already ignored, and the gateway/BFF and the backend DTO never carried it, so no BFF change was needed.
- Tests:
  - `__tests__/invite-role-selection.test.tsx`: replaced "hides customization section when Admin is selected" with the FR-7 owner/admin test, and dropped the 4th argument from the submit expectation.
  - `__tests__/owner-only-capability-grants.test.tsx`: dropped the two tests that exercised the owner-only lock inside the removed panel. The role-lock tests stay.
  - `components/team/invite-member-form.test.tsx`: the submit expectation no longer has a 4th argument. This is a stale expectation of the removed parameter, fixed in the fix commit.

**Green** (`fix3-green.txt`): 3 files, 38 tests (invite-role-selection 5, owner-only-capability-grants 24, invite-member-form 9).

## Targeted backend sweep (`targeted-backend.txt`, HEAD `46ff637f1`)

**Scope.** 98 classes:
- every test class in `member/`, `invitation/` and `orgrole/`
- `architecture/` (`LayerDependencyRulesTest`, `TenantScopeBindingTest`, `TestConventionsTest` and the others), plus every class
  carrying `@AnalyzeClasses`/`ClassFileImporter`
- every test class that references `MemberFilter`, `InvitationService`, `PendingInvitation`, `KeycloakAdminClient`,
  `KeycloakProvisioningClient`, `OrgRoleService`, `/api/invitations`, `/api/org-roles` or `deleteRole`
- the new `KeycloakAdminClientInvitationLookupTest`

**Result.** Maven exit 0 at 20:49 SAST. Counted from the `<testcase>` elements in the surefire reports: **995 test cases, 0 failures, 0 errors,
14 skipped**. All 14 skipped tests are in `AcceptanceControllerIntegrationTest`, which is `@Disabled` on main and not touched by this branch.

## Frontend gate (once, no verify/E2E live)

| Step | Result | Log |
|---|---|---|
| `pnpm lint` | exit 0: 0 errors, 76 warnings | `frontend-gate-lint.log` |
| `pnpm format:check` | exit 0: all files Prettier-clean | `frontend-gate-format.log` |
| `timeout 900 pnpm build` | exit 0 | `frontend-gate-build.log` |
| `pnpm test` | exit 0: **504 files, 4949 passed, 2 skipped** | `frontend-gate-test.log` |

The warnings in the changed files all pre-date this branch: unused `ApiError`/`mapBffInvitation` in `invitation-actions.ts`, and the React Compiler
skip on `form.watch` in `invite-member-form.tsx`.

## Not done / owed before merge
- Full backend gate: `bash backend/scripts/verify.sh`.
- A Keycloak dev-stack E2E/OBS run, because the mock UI cannot invite (`invitation-actions.ts` is a no-op in mock mode). It needs to cover:
  - a custom-role invite → register from Mailpit → lands as Partner with no 500 (plan §4);
  - revoke → the old Mailpit link shows Keycloak's "invitation expired or no longer valid" page;
  - revoke after the link was used → 409.
- A review pass on the diff.

## Process notes (honesty)
- The Fix 1 green run (`fix1-green.txt`) **overlapped a live 658A Surefire run**. My guard printed the live process but did not stop the run.
- Two later guards were wrong in a different way: `pgrep … | cut` always exits 0, so they aborted every time.
- From the Fix 2 runs on, the guard is a correct `if pgrep -f '[s]urefirebooter|…'` check, and every later Maven/heavy pnpm run waited for a clear machine.
- Fix 1's result (18/18) was re-confirmed inside the targeted sweep, which ran with nothing else live.

## Open questions (plain English)
1. **Revoking when Keycloak has no invitation and the person never joined.** I let the Kazi revoke go through.
   - Why: Keycloak then has no invitation record, so no emailed link can work.
   - The coordinator suggested not reporting this as success. If we refuse it instead, invitations whose email never went out could not be cancelled for 7 days.
   - Please confirm which you want.
2. **Old revoked/accepted invitations still block deleting a role.** The database link means a role stays undeletable after it has ever been offered, and the attempt fails with an error page (500).
   - This fix only turns the *pending* case into a clear message.
   - A small follow-up could change the database rule. That would need a migration, so it is out of scope here.
3. **A person whose invitation is revoked after they already used the link** is now refused with "already accepted".
   - They will join with the invited role, and the firm must then remove them from the Team page.
   - A stricter option: Kazi refuses to create anyone whose latest invitation was revoked. That is about 1 h of work and changes sign-in behaviour, so it needs a ruling.
