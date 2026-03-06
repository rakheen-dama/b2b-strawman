# ADR-156: Keycloak Invitation Role Gap Mitigation

**Status**: Proposed
**Date**: 2026-03-06
**Phase**: 36 (Keycloak + Gateway BFF Migration)

## Context

Keycloak 26.5 supports sending organization invitations via the Admin REST API (`OrganizationMembersResource.inviteUser()`), but **cannot attach roles to invitations** (upstream issue [#45238](https://github.com/keycloak/keycloak/issues/45238), filed Jan 2026, still open). When a user accepts an invitation and joins the organization, they become a member with no org-specific role.

In the admin-approved provisioning flow ([ADR-154](ADR-154-admin-approved-provisioning-flow.md)), the access request submitter must receive the **Owner** role upon joining their new organization. Without role-on-invitation, we need a mechanism to:
1. Store the intended role before the invitation is accepted
2. Assign the role when the user first logs in

Additionally, Keycloak's built-in `organization` scope mapper does not include org-specific roles in JWT tokens by default. A custom protocol mapper would be needed to include roles in JWTs, adding a maintenance burden.

## Options Considered

1. **Custom Keycloak protocol mapper** — Deploy a JAR that adds org roles to JWT claims
   - Pros: Role available in every JWT, no DB lookup needed at request time
   - Cons: Requires building, testing, and deploying a custom mapper JAR into Keycloak. Must be maintained across KC upgrades. Role changes require token refresh to take effect
   - Cons: Does not solve the invitation gap — still need a mechanism to assign the role after invite acceptance

2. **Keycloak Event Listener SPI for post-join role assignment** — Deploy a JAR that listens for "user joined org" events and calls Keycloak Admin API to assign the role
   - Pros: Role assigned immediately on join, fully server-side
   - Cons: Requires two custom Keycloak JARs (event listener + protocol mapper). Heavy operational burden for a gap that may be closed in a future KC release

3. **Application-level JIT role assignment (chosen)** — Store intended role in `access_requests` table. On first login, `MemberFilter` detects no existing member, looks up intended role from access request, creates member with correct role, and optionally assigns role in Keycloak via Admin API
   - Pros: Zero Keycloak customization, uses existing JIT member sync pattern, role source of truth is the application DB (already the case with Clerk)
   - Cons: First login has a small latency overhead (access request lookup + member creation + KC role assignment). Role is not in the JWT until a custom mapper is deployed (but the app already reads role from DB, not JWT)

4. **Phase Two extension** — Use the Phase Two managed Keycloak extension which supports role-on-invitation
   - Pros: Solves the gap with a single third-party extension
   - Cons: Adds a vendor dependency. Phase Two extensions may diverge from upstream Keycloak. Not compatible with vanilla Keycloak deployments

## Decision

Use **application-level JIT role assignment** (Option 3). The `access_requests` table stores the intended role for the initial requester. The `MemberFilter` performs JIT member creation on first login, reading the intended role from the access request record.

For subsequent invitations (org owner invites team members), a lightweight `pending_invitations` table can store `(keycloak_org_id, email, intended_role)` and be checked during JIT member creation. This is a future addition — the initial implementation only handles the access-request-to-owner path.

## Rationale

The application already treats the `Member.orgRole` field in the tenant database as the source of truth for authorization decisions. The `MemberFilter` binds `RequestScopes.ORG_ROLE` from the DB, not from the JWT. This means the JWT not containing org roles is not a functional problem — it's already the architecture.

Adding custom Keycloak JARs (Options 1, 2) introduces operational complexity disproportionate to the problem. The invitation role gap is a known upstream issue likely to be fixed in a future Keycloak release. Building a workaround at the application level is lower risk and easier to remove when KC adds native support.

The JIT approach also aligns with ADR-143's member sync pattern: the first request from a new member creates their record. Adding a role lookup to this existing flow is a small incremental change.

## Consequences

- **Positive**: Zero Keycloak customization — no custom JARs to deploy, test, or upgrade
- **Positive**: Consistent with existing architecture where `Member.orgRole` in DB is the role source of truth
- **Positive**: Easy to remove when Keycloak adds native role-on-invitation support (delete the access-request lookup, roles come from KC directly)
- **Positive**: Extends naturally to future invitation flows (org owner invites team members)
- **Negative**: First login has ~50-100ms additional latency (DB lookup + member creation)
- **Negative**: Role is assigned after the user's first request, not during invitation acceptance. There is a brief window where the user has no member record (between KC login redirect and first API call). This is handled by the JIT sync — the user sees a loading state, not an error
- **Neutral**: A `pending_invitations` table will be needed when the product supports owner-initiated invitations (not just admin-approved access requests)
