# ADR-179: PendingInvitation for Role Assignment

**Status**: Accepted
**Date**: 2026-03-14

## Context

When an admin invites a new user to join the organization, the system needs to assign a specific role to that user *before they have authenticated for the first time*. The invited user does not yet exist as a `Member` entity in the tenant schema — they are created by `MemberFilter`'s lazy-create path when they first hit an `/api/**` endpoint after OIDC authentication.

Currently, the role assignment for invited users depends on Keycloak Organization Roles. The gateway's `AdminProxyController` calls `KeycloakAdminClient.inviteUser()` with a role parameter, Keycloak stores this as an org role attribute, and when the user authenticates, their JWT contains an `org_role` claim that `MemberFilter` reads to set the initial `Member.orgRole` value.

This approach has several problems. The `org_role` claim format is inconsistent across Keycloak versions (flat list vs. rich map). CVE-2026-1529 demonstrated that invitation JWTs could be forged. And with [ADR-178](ADR-178-db-authoritative-role-resolution.md) making the JWT role-free, there is no longer a transport mechanism for pre-authentication role assignment through the JWT.

The invitation flow needs a mechanism that is decoupled from the IDP, supports custom roles (which Keycloak knows nothing about), and provides an auditable record of the invitation lifecycle.

## Options Considered

### Option 1: Keycloak org role attributes (current)

Continue using Keycloak's Organization Roles to carry the invited role. The admin sets a role in Keycloak, Keycloak includes it in the JWT, and `MemberFilter` reads it during lazy-create.

- **Pros**:
  - Already implemented — no migration needed
  - Role travels with the JWT automatically
- **Cons**:
  - Contradicts ADR-178 (DB-only authorization) — JWT would still carry role for new users
  - Keycloak org role format instability (CVE-2026-1529, missing claims)
  - Cannot assign custom roles — Keycloak only knows system roles
  - No audit trail of invitation lifecycle
  - Gateway must retain `KeycloakAdminClient` for role assignment

### Option 2: PendingInvitation DB record (chosen)

Create a `pending_invitations` table in each tenant schema. When an admin invites a user, a record is created with the invited email and desired `org_role_id`. When `MemberFilter` lazy-creates a member and finds a matching `PendingInvitation`, it uses that role. Keycloak still handles the invitation email delivery (org membership invite), but role assignment is entirely DB-side.

- **Pros**:
  - Fully decoupled from IDP — works with any OIDC provider
  - Supports custom roles (references `org_roles.id` FK)
  - Full audit trail (status transitions: PENDING → ACCEPTED/EXPIRED/REVOKED)
  - Queryable by admins (list pending invitations, revoke)
  - Compatible with ADR-178 (no role in JWT)
- **Cons**:
  - New entity, migration, and ~8 new files
  - Slight race condition window: if user authenticates before invitation record is committed (mitigated by default "member" role fallback)
  - Invitation email delivery still depends on Keycloak (`KeycloakAdminClient.inviteUser()`)

### Option 3: Signed invitation URL with embedded role

Generate a cryptographically signed URL containing the role. When the user clicks the link, the role is extracted from the URL and used during member creation.

- **Pros**:
  - Self-contained — no DB lookup needed
  - Works offline (no DB query during member creation)
- **Cons**:
  - URL becomes a security token — must handle expiry, revocation, replay protection
  - Cannot revoke without a DB record (needs a blocklist)
  - Role is fixed at invitation time — admin cannot change it before acceptance
  - Complex key management for signing/verification
  - No audit trail without additional DB records (defeating the purpose)

### Option 4: Default role + admin post-assign

All new members get the default "member" role. Admins promote them after they join.

- **Pros**:
  - Simplest implementation — no new entities
  - Zero pre-authentication complexity
- **Cons**:
  - Poor UX — admin must watch for new members and manually assign roles
  - Window of incorrect permissions (user has "member" access before promotion)
  - No automation — every invitation requires a follow-up action
  - Contradicts admin intent — they invited for a specific role

## Decision

**Option 2: PendingInvitation DB record.** A `pending_invitations` table records the invitation intent with a FK to `org_roles`. `MemberFilter` checks for a matching pending invitation (by email) during lazy-create and uses the invited role.

## Rationale

The PendingInvitation approach aligns with ADR-178's principle that the product database is the sole authority for authorization. By storing the intended role in the DB before the user exists, the system maintains a clean separation: Keycloak handles identity and email delivery, the DB handles authorization (including pre-authentication role intent).

The partial unique index (`UNIQUE(email) WHERE (status = 'PENDING')`) ensures only one active invitation per email per tenant while preserving historical records. The status lifecycle (PENDING → ACCEPTED/EXPIRED/REVOKED) provides a complete audit trail that integrates with the existing `AuditEvent` infrastructure.

The race condition concern (user arrives before invitation record is committed) is theoretical: the admin creates the invitation, Keycloak sends an email, and the user clicks the link seconds-to-hours later. The invitation record is committed synchronously before the Keycloak API call. If the invitation is somehow missing or expired, the fallback to default "member" role is safe — the admin can promote afterwards.

## Consequences

### Positive
- Invitation lifecycle is fully auditable and queryable
- Custom roles supported from the invitation stage
- Admins can revoke pending invitations before acceptance
- Fully decoupled from IDP — switching from Keycloak to another provider requires only changing the email delivery mechanism
- `MemberFilter` lazy-create path gains a clear, deterministic role source

### Negative
- New entity, repository, service, controller, and migration (~8 new files)
- `MemberFilter` lazy-create path adds a DB query (`findPendingByEmail`) — only on first authentication, not on subsequent requests (cached after member creation)

### Neutral
- Keycloak still handles invitation email delivery — `KeycloakAdminClient.inviteUser()` moves to backend but retains the same functionality
- Expired invitations accumulate in the table — acceptable for audit purposes, can be archived later if needed
