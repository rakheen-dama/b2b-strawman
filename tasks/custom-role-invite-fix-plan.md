# Fix plan: a member invited into a custom role can never sign in

Status: PLAN ONLY (2026-11-24, base `origin/main` @ c8907fb7d). Nothing built, run or committed.
Legend: **[C]** confirmed by reading code/config (file:line). **[H]** hypothesis / not confirmed.
All paths are relative to the repo root unless they start with `../keycloak-saas`.

## 1. Root cause

**Kazi (same in both auth modes)**
- [C] Invite: `InvitationService.createInvitation` loads the role by id (`backend/.../invitation/InvitationService.java:80-83`), runs the PR #1856 guards (`:86-89`) and stores the role entity on the invitation (`:97`; FK `pending_invitations.org_role_id`, `backend/src/main/resources/db/migration/tenant/V1__baseline.sql:1061,1069`).
- [C] Sign-in: `MemberFilter.lazyCreateMember` keeps only the invitation role's **slug** (`member/MemberFilter.java:162`). It then looks that slug up again with `orgRoleService.findSystemRoleBySlug` (`:183-189`), which returns only system roles (`orgrole/OrgRoleService.java:400-403`). A custom slug such as `partner` → `IllegalStateException`. The exception is caught and logged as a warning in `resolveMember` (`MemberFilter.java:122-129`), which returns null. The request carries on with no member bound (`:87-88`). Every endpoint that calls `RequestScopes.requireMemberId()` (`multitenancy/RequestScopes.java:60-65`) answers 500 `"Member context not available"` (`exception/GlobalExceptionHandler.java:91-101`). Caffeine does not cache a loader that throws (`MemberFilter.java:121`), so every request fails again.
- [C] Once the invitation expires (7 days, `InvitationService.java:35`), `findPendingByEmail` marks it EXPIRED and returns nothing (`:182-186`). The next request then creates the user as a plain **Member** (`MemberFilter.java:154,172`). So the outcome is "broken for 7 days, then silently downgraded".
- **Mock-auth/e2e:** [C] the Team-page invite is a no-op in mock mode (`frontend/app/(app)/org/[slug]/team/invitation-actions.ts:164`, and list/revoke at `:172,188`). Through the UI the bug cannot happen on the mock stack. It reproduces only with a direct `POST /api/invitations` (which is presumably what the earlier agent did [H]) plus a mock-IdP token whose email matches (`compose/mock-idp/src/index.ts:54` makes `<userId>@unknown.local`).
- **Keycloak mode:** same Kazi failure. Keycloak's own steps all succeed (see §2), so the invitee becomes a Keycloak org member and then hits 500s inside Kazi.

**History: it has been broken since March, and probably never worked in the current design.**
- 2026-03-10, #652 (`12158255a`): the invite form gains the "Custom" group.
- 2026-03-14, #670/#671 (`08d3359e9`, `55b51e516`): invitations are stored in Kazi and sign-in reads the invitation slug. Custom slug → `findSystemRoleBySlug(..).ifPresent(..)` → member created with **no role** [C code; effect on capabilities H].
- 2026-03-15, #676 (`5baaa8cad`): switched to `orElseThrow` → the hard failure we see today.
- Before 03-14, invites went through the gateway BFF `/bff/admin/invite` carrying `orgRoleId` (`12158255a`). Whether that ever applied the role is unverified [H].
- This is a never-worked bug, not a regression from #1856.

**Why the tests missed it** [C]
- `MemberFilterInvitationTest` (`backend/src/test/.../member/MemberFilterInvitationTest.java:70,111,154,195`) covers only system roles: admin, member, expired, no invitation.
- `InvitationRoleEscalationIntegrationTest.theOwnerStillInvitesIntoTheAdminRoleAndAPartnerRole` (`invitation/InvitationRoleEscalationIntegrationTest.java:137-146`) signs in the *admin* invitee but stops at the 201 for the Partner invite. `anAdminStillInvitesIntoTheMemberRoleAndOrdinaryRoles` (`:120-134`) never signs in at all.
- `InvitationServiceTest` / `InvitationControllerTest` cover creation, listing and revoking only.
- The Keycloak E2E `frontend/e2e/tests/keycloak/member-invite-rbac.spec.ts:27-90` invites only Admin and Member.

## 2. What Keycloak does with roles

**2.1 Every place Kazi talks to Keycloak about roles** [C]

| Call site | Role value sent | Keycloak API | If the role is unknown to Keycloak |
|---|---|---|---|
| `InvitationService.java:124` → `KeycloakAdminClient.inviteMember` (`security/keycloak/KeycloakAdminClient.java:153-166`) | system **or custom slug**, but the `role` parameter is **never used**: the body is only `email` (+`redirectUrl`) | `POST /organizations/{id}/members/invite-user` | Nothing happens: Keycloak never sees a role. Any failure is caught and logged (`InvitationService.java:127-129`) |
| `OrgProvisioningService.java:84-85` (self-serve create-org) and `DemoProvisionService.java:177-178` → `updateMemberRole` (`KeycloakAdminClient.java:186-205`) | always the literal `"owner"` | user attribute `org_role` via `GET`+`PUT /users/{id}` (`:256-284`). Also `/organizations/{id}/roles` + `.../organization-roles/grant` (`:190-197, :286-324`) | Keycloak would create the org role on the fly (`:302-307`). The code expects the grant to fail on KC 26.x and logs it at debug (`:199-203`). Whether those endpoints exist in stock KC 26 is unconfirmed [H] |
| `KeycloakAdminClient.createOrganization` (`:78-104`) | no role; sets the org attribute `creatorUserId` | `POST /organizations` | n/a |
| `AccessRequestApprovalService.java:107-109` → `KeycloakProvisioningClient.inviteUser` / `setOrgCreator` (`accessrequest/KeycloakProvisioningClient.java:233-243, 274-289`) | no role; `creatorUserId` org attribute only | `invite-user`, `PUT /organizations/{id}` | n/a |
| `OrgRoleService.assignRole` (`orgrole/OrgRoleService.java:289-367`), role create/update/delete | **no Keycloak call at all** (only callers of `updateMemberRole` are the two above) | none | n/a |
| `KeycloakAdminClient.revokeInvitation` (`:177-184`) | n/a | `DELETE /organizations/{id}/invitations/{inv}` | **No callers.** Revoking in Kazi (`InvitationService.java:147-174`) leaves the Keycloak invite email link live (see §6) |

**2.2 How Keycloak stores roles** [C]
- No realm roles beyond the defaults, no client roles, and one group, `platform-admins`. That holds both locally (`compose/keycloak/realm-export.json:20,45`) and in production (`../keycloak-saas/realms/b2mash.json:17,42`).
- Organisations are enabled. Org membership carries no role.
- Only two protocol mappers exist, both on `gateway-bff`:
  - `groups` (`realm-export.json:76`; `b2mash.json:71`)
  - `org-role`: user attribute `org_role` → claim `org_role` (`realm-export.json:89-100`; `b2mash.json:84`; created by `compose/scripts/keycloak-bootstrap.sh:127-136` and `../keycloak-saas/scripts/bootstrap-realm.sh:191-214`, both commented "KC 26.x not including org roles in the built-in organization claim").
- The `organization` claim comes from the built-in scope that the gateway requests (`gateway/src/main/resources/application.yml:35`). It is a list of org aliases (`gateway/.../BffUserInfoExtractor.java:33-37`).
- The Clerk-style `o` claim (`id`/`rol`/`slg`) is **not emitted by any Keycloak realm**. Only the test JWTs (`TestJwtFactory`) and `JwtUtils.extractClerkClaim` (`security/JwtUtils.java:85-94`) know about it.
- `org_role` is a **per-user, realm-wide** attribute, not per-org (`KeycloakAdminClient.java:188`). It cannot represent a user who belongs to two firms.

**2.3 Is the token's role claim trusted anywhere? No.** [C]
- Backend:
  - `ClerkJwtAuthenticationConverter.java:23-25` grants no authorities.
  - `JwtUtils.extractOrgRole` (`JwtUtils.java:67-75`) has **no callers** (repo-wide grep).
  - `MemberFilter` takes the role from the DB only (`MemberFilter.java:131`). JWT-role use was removed in #671 (`55b51e516`) and the lazy-create fallback in `1c93cb66a`.
  - The guard tests `MemberFilterDbRoleTest.shouldNotElevateMemberToAdminEvenIfJwtSaysAdmin` / `shouldLazyCreateWithDefaultMemberRoleNotJwtRole` already exist.
  - The only claim used for authorisation is `groups` (platform-admin, not a firm role): `security/PlatformAdminFilter.java:37`.
- Gateway: `/bff/me` returns no role (`gateway/.../BffController.java:33-41, 75-97`).
- Next.js: the role comes from backend `/api/members` (`frontend/lib/auth/client/hooks.ts:52-62`) and `/api/me/capabilities` (`frontend/lib/api/capabilities.ts`). The mock provider reads only `sub`/`organization`/`groups`/`email`/`name` (`frontend/lib/auth/providers/mock/server.ts:50-70`).
- The mock IdP emits a `role` claim (`compose/mock-idp/src/index.ts:56`), which is ignored.
- Portal: `portal/lib/auth.ts` decodes portal-contact JWTs only, not firm roles.
- → No PR #1856 concern today. `extractOrgRole` is dead code that could tempt someone later; see §6.

**2.4 Sync and drift** [C]
- Kazi never updates Keycloak on assign or invite, so the two disagree:
  - Invitees (any role) have **no** `org_role`.
  - Founders via self-serve create-org have `org_role=owner`.
  - Founders via access-request approval have none.
  - The bootstrap backfill sets `owner` for the creator and **`member` for everyone else**, including admins and custom-role holders (`keycloak-bootstrap.sh:204-268`).
- Because nothing reads `org_role` (§2.3), the drift is invisible to users today. No admin UI is shown from the token.
- A custom-role invite leaves this in Keycloak: a user (created at registration [H: per stock invite-user flow, the same flow `member-invite-rbac.spec.ts` drives]), org membership, and no role or attribute. In Kazi: a PENDING invitation and no member row.

**2.5 Founding user / owner** [C]
- Kazi: the first member created in an empty tenant becomes Owner (`MemberFilter.java:171-176`). That check is DB-only (`memberRepository.count() == 0`) and applies only when the resolved role is the system Member.
- Keycloak keeps owner markers: the `creatorUserId` org attribute (`KeycloakAdminClient.java:88`, `KeycloakProvisioningClient.java:285`) and `org_role=owner`. Kazi **never reads either** (only the bootstrap backfill reads `creatorUserId`, `keycloak-bootstrap.sh:226`).
- A pending invitation always implies at least one member (`invited_by` FK NOT NULL, `V1__baseline.sql:1062,1068`), so an invitee can never trip the founder rule.

## 3. Fix options

**A: resolve the invitation's role by its stored id; Keycloak stays out of roles (RECOMMENDED)**
- In `lazyCreateMember`, carry `invitation.getOrgRole().getId()` instead of the slug.
- Load that exact `OrgRole` through a new `OrgRoleService.findRoleForInvitation(UUID)` (read-only `findById`) and build the `Member` with it.
- Keep `findSystemRoleBySlug` only for the "no invitation" path (Member, or Owner for a founder).
- Return `new MemberInfo(id, orgRole.getSlug())`. That matches what a custom-role member already gets after `assignRole` (`MemberFilter.java:138`).
- Keep the founder rule exactly as now: promote only when there is no invitation or the invitation role is the system Member.
- Security under #1856:
  - The role applied is exactly the role id that passed `requireSystemRoleGrantable` and `requireOwnerToGrant` at creation.
  - Nothing comes from the JWT except the email, which picks the invitation, as today.
  - Capabilities still come from the role's current definition. Editing a role to add an owner-only capability is itself owner-only (`OrgRoleService.java:250`), the same as for existing members on that role.
  - The callers' rules cannot be re-run at sign-in because the invitee is not an owner and `ORG_ROLE` is unbound.
  - Optional belt-and-braces: never grant the system **Owner** role from an invitation. That needs a founder ruling (Q1).
- Keycloak: no change. No realm or mapper change in `../keycloak-saas`.
- Cost: about 25 lines of production code, 1–2 files.

**B: create the user as Member, then call `assignRole` to switch to the custom role**
- `assignRole` re-runs the caller-scoped guards (`OrgRoleService.java:307, 342`). With no owner bound, a Partner role holding an owner-only capability is refused with 403. Making it work means bypassing #1856 → **rejected on security**.
- It is also two writes that are not atomic: if the second fails, the user is silently downgraded.
- It calls `memberFilter.evictFromCache` from inside that cache's own loader (`:354`) [H: Caffeine recursive-update error].
- It writes a misleading "role changed" audit entry and notification.

**C: teach Keycloak the custom roles and read the role from the token**
- Write `org_role=<custom slug>` or one group per role, then let sign-in or capabilities trust the claim.
- **Rejected:**
  - It reverses the "role from DB" decision (#671).
  - It trusts a value that drifts (§2.4) and is per-user rather than per-firm (§2.2).
  - It needs realm/mapper work in `../keycloak-saas`.
  - It gives no benefit, because Kazi does not consume the claim.

**D (stopgap only): refuse custom-role invites**
- Return 400 at creation and hide the "Custom" group.
- That is a product regression. Only worth it if A cannot ship soon.

## 4. Reproduce-first tests (write these first, see them fail, then fix)

1. **`MemberFilterInvitationTest.shouldAssignCustomInvitationRoleOnFirstLogin`** (extend the existing class)
   - Setup: in the tenant scope, save `new OrgRole("Partner Inv","partner-inv",…,false)` with `{VIEW_TRUST, SIGN_OFF_TRUST_TAKE_ON}`, plus a `PendingInvitation("invited_partner@test.com", partner, owner, +7d)`.
   - Act: `GET /api/me/capabilities` with a JWT carrying subject `user_invited_partner` and that email.
   - Today it fails with 500 and ProblemDetail `title="Member context not available"`.
   - Expect after the fix: 200, `$.role=="Partner Inv"`, `$.capabilities` = exactly those two, `$.isAdmin==false`, `$.isOwner==false`.
   - DB checks: `member.getOrgRoleId()==partner.getId()`, invitation `ACCEPTED`.
2. **`…shouldAssignCustomInvitationRoleWithKeycloakFormatJwt`**: same, but the JWT uses `claim("organization", List.of(ORG_ID))` and no `o` claim. That is the Keycloak path (`JwtUtils.java:99-109`, `TenantFilter.java:59`), so the Keycloak-mode sign-in is covered without a Keycloak server.
3. **`…customInviteeIgnoresTokenRoleClaims`**: custom invitee whose JWT also carries `role:"owner"`, `org_role:"owner"` and `o.rol:"owner"` → still Partner, `isOwner==false`.
4. **`InvitationRoleEscalationIntegrationTest`**:
   - extend `theOwnerStillInvitesIntoTheAdminRoleAndAPartnerRole` (`:137-146`) to `signIn("user_ies_owner_partner", …)` and assert `$.role=="Partner Invite B"` and that the capabilities include `SIGN_OFF_TRUST_TAKE_ON` and `VIEW_TRUST`;
   - extend `anAdminStillInvitesIntoTheMemberRoleAndOrdinaryRoles` to sign in the Bookkeeper and assert `INVOICING`, `isAdmin==false`.
   - This covers the whole POST→sign-in path with the #1856 guards live.
5. Leave unchanged and keep green: the 4 existing `MemberFilterInvitationTest` cases, `MemberFilterDbRoleTest`, `MemberFilterIntegrationTest`, the founder path, and `TenantScopeBindingTest`.

- Gate: full `./mvnw verify` (`backend/scripts/verify.sh`).
- **E2E/OBS (later, Keycloak dev stack, because the mock UI cannot invite):**
  1. Owner creates the custom role "Partner" (e.g. `VIEW_TRUST`, `SIGN_OFF_TRUST_TAKE_ON`).
  2. Team → Invite → Custom → Partner.
  3. Mailpit shows the Keycloak invitation.
  4. The invitee registers (`frontend/e2e/fixtures/keycloak-auth.ts:54` `registerFromInvite`) and lands on the dashboard with no 500.
  5. The Team page shows them as Partner. Trust take-on sign-off is reachable; owner-only settings are not.
  6. DB check: invitation ACCEPTED, `members.org_role_id` = Partner.
- Add this as a new step in `member-invite-rbac.spec.ts`.

## 5. Scope, size, migration, self-heal
- Files:
  - `member/MemberFilter.java` (~20 lines)
  - `orgrole/OrgRoleService.java` (+1 small read method)
  - two test classes (~120 lines)
- Size: about 2–3 h including the verify. No frontend change and no Keycloak or `../keycloak-saas` change.
- **No migration.** `pending_invitations.org_role_id` already holds the id.
- **Self-heal:** pending, unexpired custom-role invitations work on the invitee's next request after deploy. Failures were never cached, and no member row or Keycloak role needs fixing.
- Invitees whose invitation already **expired** were created as Member (§1). An admin must assign their role by hand. To find them: an `EXPIRED` invitation into a non-system role whose email now matches a member. Read-only SQL, run per tenant by ops.

## 6. Adjacent defects found (not in this fix; one fix per PR)
- **Revoke does not revoke in Keycloak** (`InvitationService.java:147-174`; `KeycloakAdminClient.revokeInvitation` has no callers). The invitee can still join through the email link and is created as Member. MEDIUM [C code; Keycloak link validity after revoke H].
- **Pending invitations created before #1856** into Owner/Admin by an admin still grant that role at sign-in. The #1856 guard runs only at creation (Q1).
- **"Customize for this user" overrides are silently dropped.** The UI sends them (`invite-member-form.tsx:352`, `:220-224`), the action ignores `_capabilityOverrides` (`invitation-actions.ts:80`) and the DTO has no field (`invitation/dto/InvitationDtos.java:15`) (Q2).
- The pending-invitations badge shows `org:partner-inv` for a custom role (`invitation-actions.ts:124` + `components/team/pending-invitations.tsx:72-75`). LOW, cosmetic.
- Deleting a custom role that is still referenced by any invitation row hits the FK (`V1__baseline.sql:1069`), because `deleteRole` checks members only (`OrgRoleService.java:371-393`). HTTP status unverified [H].
- `JwtUtils.extractOrgRole` is dead code, and the `org_role` mapper and backfill write stale values. Delete it, or leave a "never for authorisation" note. LOW.

## 7. Questions for the founder (plain English)
- **Q1.** Should signing in from an invitation ever make someone **Owner**? I suggest "never". If someone is on an invitation to Owner, they would join as Member instead. Also, should we cancel any invitations into Owner or Admin that an admin sent before last week's security fix? It's about 30 minutes extra if bundled; otherwise it goes in a separate small fix.
- **Q2.** The invite form lets you tick extra or fewer permissions "for this user", but they are thrown away. Should we hide that panel (about 1 h, frontend only) or make invitations honour it (about 4 h, with owner-only checks)?
- **Q3.** When an invitation is revoked in Kazi, should we also cancel the Keycloak invitation email link? I recommend yes, as its own fix (about 2 h).
