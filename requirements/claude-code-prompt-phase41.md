# Phase 41 — Organisation Roles & Capability-Based Permissions

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After Phase 40, the platform has:

- **Authentication (Phase 36)**: Keycloak 26.5 with Spring Cloud Gateway BFF. JWTs carry `org:owner`, `org:admin`, `org:member` as org-level roles. The Gateway's `BffUserInfoExtractor` extracts the role and passes it to the backend via `X-Org-Role` header.
- **Member sync (Phase 36)**: `MemberSyncFilter` resolves the authenticated user to a `Member` entity in the tenant schema. Binds `RequestScopes.TENANT_ID`, `RequestScopes.MEMBER_ID`, `RequestScopes.ORG_ROLE` as `ScopedValue`s.
- **Current authorization**: ~50+ `@PreAuthorize` annotations using Spring Security's `hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')`. Authorization is coarse — endpoints are either admin-only (`ORG_ADMIN`/`ORG_OWNER`) or open to all members. No fine-grained capability checks.
- **Team management (Phase 36)**: Invite flow with role dropdown (Member/Admin). Member list with role badges. Keycloak org role assignment via `KeycloakAdminClient`. No inline role editing or member removal.
- **Feature areas with access-control value**: Rate cards & cost rates (Phase 8), invoicing & billing runs (Phase 10/40), project CRUD & budgets (Phase 4/8), time approval (Phase 5), customer CRUD & lifecycle (Phase 4/14), automation rules (Phase 37), resource planning & capacity (Phase 38).

**Gap**: The three-role model (Owner/Admin/Member) is too coarse for 10–50 person firms. Members can't access anything sensitive. Admins get everything. There's no way to express "this person manages billing but shouldn't see profitability" or "this person manages projects but can't change org settings." Firms are forced to over-permission (make everyone admin) or under-permission (keep them as members and handle requests manually). Every competitor (Productive.io, Scoro, Accelo, Harvest) offers either granular permission toggles or custom roles.

## Objective

Build a **capability-based permission system** that allows firm administrators to:

1. **Define custom roles** — named presets (e.g., "Project Manager", "Billing Clerk", "Senior Associate") with a curated set of capability toggles, managed in Settings → Roles & Permissions.
2. **Assign roles at invite and post-invite** — the invite flow and member profile panel both support role selection from system + custom roles.
3. **Override per user** — optionally add or remove individual capabilities from a user's role preset, for edge cases that don't warrant a new role.
4. **Enforce capabilities at the API level** — backend checks `hasCapability("INVOICING")` instead of `role == "ADMIN"`. System roles (Owner/Admin) bypass capability checks. Members with no custom role have basic access only.
5. **Cascade preset edits** — changing a custom role's capabilities updates all users on that role. Per-user overrides are preserved.
6. **Adapt the frontend** — sidebar navigation, page visibility, and action buttons respect the user's effective capabilities. Features the user can't access are hidden (not greyed out).

## Constraints & Assumptions

- **No Keycloak changes.** Keycloak continues to issue JWTs with `org:owner`, `org:admin`, `org:member`. Custom roles and capabilities are resolved entirely in the application layer (tenant database). This is a hard constraint — Keycloak is still stabilizing.
- **No gateway changes.** The Gateway continues to pass `X-Org-Role` from the JWT. The backend resolves capabilities from the DB using the member's assigned role.
- **Capabilities are a fixed, curated set defined by the product.** Admins choose which capabilities a role gets — they do not define new capability types. This keeps the enforcement logic bounded and the UI simple.
- **System roles are immutable.** Owner and Admin always have all capabilities. Member always has basic access. These cannot be edited or deleted.
- **Custom roles are tenant-scoped.** Each firm defines their own vocabulary. No cross-tenant role sharing.
- **Per-user overrides are additive and subtractive.** Stored as a set of `+CAPABILITY` and `-CAPABILITY` entries on the Member entity. Effective capabilities = `role.capabilities ± overrides`.
- **Owner/Admin bypass.** Capability checks only apply to members with custom roles. `ORG_ADMIN` and `ORG_OWNER` from the JWT short-circuit to "all capabilities granted." This preserves backward compatibility — existing admin users lose no access.
- **No project-level roles.** This phase covers org-level roles only. Project-specific permissions (e.g., "project admin on this project but member everywhere else") are a future enhancement.
- **No hierarchy or inheritance between custom roles.** A "Senior Associate" is not "Project Manager + extras." Each role is a flat set of capabilities.

---

## Section 1 — Capability Definitions

### Fixed Capability Set

The product defines exactly 7 capabilities. This set is not user-configurable — it's part of the application code.

```
Capability Enum:
  FINANCIAL_VISIBILITY    — View billable rates, cost rates, profitability reports, budget financials
  INVOICING               — Create, edit, approve, send invoices. Access billing runs.
  PROJECT_MANAGEMENT      — Create projects, edit budgets, manage project members, project settings
  TEAM_OVERSIGHT          — View and approve others' time entries. See assigned team members' work.
  CUSTOMER_MANAGEMENT     — Customer CRUD, lifecycle management, compliance checklists, information requests
  AUTOMATIONS             — Create, edit, enable/disable automation rules. View execution logs.
  RESOURCE_PLANNING       — Manage allocations, capacity settings, leave. View utilization reports.
```

### Capability → Endpoint Mapping

Each capability gates a specific set of existing endpoints. The table below shows the mapping — not every endpoint, but the pattern:

| Capability | Gated Endpoints (examples) | Currently Guarded By |
|-----------|---------------------------|---------------------|
| `FINANCIAL_VISIBILITY` | `GET /api/rates/**`, `GET /api/profitability/**`, `GET /api/budgets/*/financials` | `hasAnyRole('ORG_ADMIN', 'ORG_OWNER')` |
| `INVOICING` | `POST /api/invoices/**`, `POST /api/billing-runs/**`, `PUT /api/invoices/*/approve` | `hasAnyRole('ORG_ADMIN', 'ORG_OWNER')` |
| `PROJECT_MANAGEMENT` | `POST /api/projects`, `PUT /api/projects/*`, `POST /api/projects/*/members`, `PUT /api/budgets/**` | `hasAnyRole('ORG_ADMIN', 'ORG_OWNER')` |
| `TEAM_OVERSIGHT` | `GET /api/time-entries?memberId=*` (others' time), `POST /api/time-entries/*/approve` | `hasAnyRole('ORG_ADMIN', 'ORG_OWNER')` |
| `CUSTOMER_MANAGEMENT` | `POST /api/customers`, `PUT /api/customers/*`, `POST /api/customers/*/checklists/**` | `hasAnyRole('ORG_ADMIN', 'ORG_OWNER')` |
| `AUTOMATIONS` | `POST /api/automations/**`, `PUT /api/automations/**`, `DELETE /api/automations/**` | `hasAnyRole('ORG_ADMIN', 'ORG_OWNER')` |
| `RESOURCE_PLANNING` | `POST /api/allocations/**`, `PUT /api/capacity/**`, `POST /api/leave/**` | `hasAnyRole('ORG_ADMIN', 'ORG_OWNER')` |

**Important**: Endpoints currently guarded by `hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')` (i.e., open to all members) remain open to all members. Capabilities only replace the admin-gated endpoints.

### Basic Member Access (no capabilities required)

All authenticated members retain access to:
- Track their own time (`POST /api/time-entries` for own member ID)
- View and work on assigned tasks
- View projects they're a member of
- Upload and view documents on their projects
- Comment on tasks and documents
- View their own notifications and preferences
- Use "My Work" view
- View their own profile

---

## Section 2 — OrgRole Data Model

### OrgRole Entity

A tenant-scoped entity representing a named role with a set of capabilities.

```
OrgRole:
  id              UUID (PK)
  name            String (not null, max 100) — display name, e.g., "Project Manager"
  slug            String (not null, max 100, unique per tenant) — URL-safe identifier
  description     String (nullable, max 500) — optional description
  capabilities    Set<String> (not null) — e.g., ["PROJECT_MANAGEMENT", "TEAM_OVERSIGHT"]
  is_system       boolean (not null, default false) — true for OWNER, ADMIN, MEMBER
  created_at      Instant
  updated_at      Instant
```

**Storage for capabilities**: Use a join table `org_role_capabilities` (org_role_id UUID FK, capability VARCHAR(50)), not JSONB. This enables straightforward queries like "find all roles with INVOICING capability."

**System roles**: Three system OrgRole rows are seeded per tenant schema via Flyway migration:
- `OWNER` (is_system=true, capabilities=all 7)
- `ADMIN` (is_system=true, capabilities=all 7)
- `MEMBER` (is_system=true, capabilities=empty set)

System roles cannot be edited or deleted. Their `is_system` flag is enforced in the service layer.

### Member Entity Extension

The existing `Member` entity gains:

```
Member (extended):
  ...existing fields...
  org_role_id             UUID (FK → OrgRole, nullable initially for migration)
  capability_overrides    Set<String> — e.g., ["+INVOICING", "-TEAM_OVERSIGHT"]
```

**Storage for overrides**: Join table `member_capability_overrides` (member_id UUID FK, override VARCHAR(60)). The `+`/`-` prefix encodes addition/removal.

**Migration strategy**: Existing members are mapped based on their current Keycloak role:
- `org:owner` → assigned to the OWNER system OrgRole
- `org:admin` → assigned to the ADMIN system OrgRole
- `org:member` → assigned to the MEMBER system OrgRole

After migration, `org_role_id` becomes NOT NULL.

### Effective Capability Resolution

```
resolveCapabilities(member):
  if member.orgRole.isSystem and member.orgRole.slug in ("owner", "admin"):
    return ALL_CAPABILITIES  // bypass — full access

  base = member.orgRole.capabilities  // from the role preset
  for override in member.capabilityOverrides:
    if override starts with "+":
      base.add(override.substring(1))
    else if override starts with "-":
      base.remove(override.substring(1))
  return base
```

This resolution runs once per request during `MemberSyncFilter` and is bound to a new `RequestScopes.CAPABILITIES` scoped value.

---

## Section 3 — Backend Authorization Changes

### New: CapabilityAuthorizationService

A service that checks whether the current request has a specific capability:

```java
@Service
public class CapabilityAuthorizationService {

  public boolean hasCapability(String capability) {
    Set<String> caps = RequestScopes.CAPABILITIES.get();
    return caps.contains(capability) || caps.contains("ALL");
  }

  public void requireCapability(String capability) {
    if (!hasCapability(capability)) {
      throw new AccessDeniedException("Missing capability: " + capability);
    }
  }
}
```

### New: @RequiresCapability Annotation (optional approach)

A custom method-security annotation as an alternative to inline service calls:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresCapability {
  String value();  // e.g., "INVOICING"
}
```

Implemented via a Spring Security `MethodSecurityExpressionHandler` or `AuthorizationManager`. This replaces `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")` with `@RequiresCapability("INVOICING")`.

### Migration of Existing @PreAuthorize Annotations

All existing `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")` annotations are replaced with the appropriate `@RequiresCapability`. Endpoints currently open to all members (`hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')`) are left unchanged — they just need an authenticated member.

**Migration mapping** (by controller/package):

| Package | Current Guard | New Guard |
|---------|--------------|-----------|
| `rate/` | `ORG_ADMIN, ORG_OWNER` | `@RequiresCapability("FINANCIAL_VISIBILITY")` for reads, `@RequiresCapability("FINANCIAL_VISIBILITY")` for writes |
| `budget/` | `ORG_ADMIN, ORG_OWNER` | `@RequiresCapability("PROJECT_MANAGEMENT")` for config, `@RequiresCapability("FINANCIAL_VISIBILITY")` for financial reads |
| `report/` (profitability) | `ORG_ADMIN, ORG_OWNER` | `@RequiresCapability("FINANCIAL_VISIBILITY")` |
| `invoice/` | `ORG_ADMIN, ORG_OWNER` | `@RequiresCapability("INVOICING")` |
| `billingrun/` | `ORG_ADMIN, ORG_OWNER` | `@RequiresCapability("INVOICING")` |
| `project/` (create, edit) | `ORG_ADMIN, ORG_OWNER` | `@RequiresCapability("PROJECT_MANAGEMENT")` |
| `customer/` | `ORG_ADMIN, ORG_OWNER` | `@RequiresCapability("CUSTOMER_MANAGEMENT")` |
| `automation/` | `ORG_ADMIN, ORG_OWNER` | `@RequiresCapability("AUTOMATIONS")` |
| `capacity/`, `allocation/`, `leave/` | `ORG_ADMIN, ORG_OWNER` | `@RequiresCapability("RESOURCE_PLANNING")` |
| `timeentry/` (approve others) | `ORG_ADMIN, ORG_OWNER` | `@RequiresCapability("TEAM_OVERSIGHT")` |
| `compliance/`, `retention/` | `ORG_ADMIN, ORG_OWNER` | `@RequiresCapability("CUSTOMER_MANAGEMENT")` |
| `orgsettings/` | `ORG_ADMIN, ORG_OWNER` | Stays `ORG_ADMIN, ORG_OWNER` — org settings remain admin-only |
| `template/` (doc templates) | `ORG_ADMIN, ORG_OWNER` | Stays `ORG_ADMIN, ORG_OWNER` — template management is admin |
| `notification/` (preferences) | All members | No change |

**Org-level settings, team management, subscription/billing, and template management remain admin/owner-only.** Capabilities gate feature-domain access, not platform administration.

### RequestScopes Extension

```
RequestScopes (extended):
  TENANT_ID       ScopedValue<String>    — existing
  MEMBER_ID       ScopedValue<UUID>      — existing
  ORG_ROLE        ScopedValue<String>    — existing (kept for backward compat)
  CAPABILITIES    ScopedValue<Set<String>> — NEW
```

Bound in `MemberSyncFilter` after member lookup. For `ORG_ADMIN`/`ORG_OWNER`, bind a singleton set containing `"ALL"`. For custom roles, resolve from DB.

---

## Section 4 — OrgRole CRUD API

### Endpoints

```
GET    /api/org-roles                    — List all roles (system + custom). Any member.
GET    /api/org-roles/{id}               — Get role detail with capabilities. Any member.
POST   /api/org-roles                    — Create custom role. Admin/Owner only.
PUT    /api/org-roles/{id}               — Update custom role name, description, capabilities. Admin/Owner only. System roles rejected.
DELETE /api/org-roles/{id}               — Delete custom role. Admin/Owner only. System roles rejected. Rejects if members are assigned.
```

### Request/Response Shapes

```json
// POST /api/org-roles
{
  "name": "Project Manager",
  "description": "Can manage projects and oversee team time",
  "capabilities": ["PROJECT_MANAGEMENT", "TEAM_OVERSIGHT"]
}

// Response (all endpoints)
{
  "id": "uuid",
  "name": "Project Manager",
  "slug": "project-manager",
  "description": "Can manage projects and oversee team time",
  "capabilities": ["PROJECT_MANAGEMENT", "TEAM_OVERSIGHT"],
  "isSystem": false,
  "memberCount": 3,
  "createdAt": "2026-03-08T10:00:00Z",
  "updatedAt": "2026-03-08T10:00:00Z"
}
```

### Validation Rules

- `name`: required, max 100 chars, unique per tenant (case-insensitive)
- `slug`: auto-generated from name (kebab-case), unique per tenant
- `capabilities`: must be a subset of the 7 defined capabilities. Empty set allowed (equivalent to Member).
- Cannot delete a role that has members assigned. Must reassign members first.
- Cannot edit or delete system roles (enforced by `is_system` check).

---

## Section 5 — Member Role Assignment API

### Endpoints

```
PUT    /api/members/{id}/role            — Assign role + optional overrides. Admin/Owner only.
GET    /api/members/{id}/capabilities    — Get effective capabilities for a member. Admin/Owner or self.
```

### Request/Response Shapes

```json
// PUT /api/members/{id}/role
{
  "orgRoleId": "uuid-of-project-manager-role",
  "capabilityOverrides": ["+INVOICING", "-TEAM_OVERSIGHT"]  // optional
}

// GET /api/members/{id}/capabilities
{
  "memberId": "uuid",
  "roleName": "Project Manager",
  "roleCapabilities": ["PROJECT_MANAGEMENT", "TEAM_OVERSIGHT"],
  "overrides": ["+INVOICING", "-TEAM_OVERSIGHT"],
  "effectiveCapabilities": ["PROJECT_MANAGEMENT", "INVOICING"]
}
```

### Validation Rules

- Cannot change the Owner's role.
- Cannot assign the Owner system role to another member.
- Admin/Owner system roles can only be assigned by an Owner.
- Overrides must reference valid capabilities from the enum.
- A `+CAPABILITY` override on a capability already in the role is a no-op (accepted but redundant).
- A `-CAPABILITY` override on a capability not in the role is a no-op.

---

## Section 6 — Invite Flow Changes

### Modified Invite API

The existing invite endpoint (`POST /bff/admin/invite`) gains an optional `orgRoleId` field:

```json
// POST /bff/admin/invite (extended)
{
  "email": "carol@firm.co.za",
  "role": "member",              // Keycloak role — always "member" for custom roles
  "orgRoleId": "uuid-of-custom-role",  // optional — if omitted, defaults to MEMBER system role
  "capabilityOverrides": []      // optional
}
```

**Flow**:
1. Gateway creates the Keycloak user and assigns `org:member` (or `org:admin` if selected).
2. On member sync (JIT provisioning), the backend assigns the specified `OrgRole` and overrides.
3. If `role` is `"admin"`, `orgRoleId` is ignored — admin system role is used.

### Modified Invite Frontend

The invite form role dropdown shows all roles:

```
System:
  Member — Basic access
  Admin — Full access

Custom:
  Project Manager — Projects, Team oversight
  Billing Clerk — Invoicing, Customers
  Senior Associate — Projects, Financials, Team
```

Selecting a custom role:
- Sets `role: "member"` for Keycloak (custom roles are always members in Keycloak)
- Sets `orgRoleId` to the selected custom role's ID
- Shows a read-only capability summary below
- Offers an expandable "Customize for this user" section with toggles

Selecting "Admin":
- Sets `role: "admin"` for Keycloak
- Hides capability toggles (admin has everything)

---

## Section 7 — Frontend Capability Gating

### Capabilities API

```
GET /api/me/capabilities    — Returns the current user's effective capabilities
```

Response:
```json
{
  "capabilities": ["PROJECT_MANAGEMENT", "TEAM_OVERSIGHT"],
  "role": "Project Manager",
  "isAdmin": false,
  "isOwner": false
}
```

Called once on app load, cached in a React context.

### CapabilityProvider Context

```typescript
// lib/capabilities.tsx
interface CapabilityContext {
  capabilities: Set<string>;
  hasCapability: (cap: string) => boolean;
  isAdmin: boolean;
  isOwner: boolean;
}
```

### Sidebar Navigation Gating

Navigation items are conditionally rendered based on capabilities:

| Nav Item | Required Capability | Currently Visible To |
|----------|-------------------|---------------------|
| Invoices | `INVOICING` | Admin/Owner |
| Customers | `CUSTOMER_MANAGEMENT` | Admin/Owner |
| Profitability | `FINANCIAL_VISIBILITY` | Admin/Owner |
| Resource Planning | `RESOURCE_PLANNING` | Admin/Owner |
| Automations | `AUTOMATIONS` | Admin/Owner |
| Settings | `isAdmin \|\| isOwner` | Admin/Owner (unchanged) |
| Projects, My Work, Dashboard | All members | All members (unchanged) |

Items the user lacks capability for are **hidden entirely**, not greyed out.

### Page-Level Protection

Each gated page checks capabilities in its server component. If the user lacks the required capability, return `notFound()` or redirect to dashboard — not a "403" page.

### Component-Level Gating

Action buttons and sections within pages respect capabilities:

- "Create Project" button on Projects page → requires `PROJECT_MANAGEMENT`
- "Generate Invoice" dropdown on Customer page → requires `INVOICING`
- Rate columns in time entry tables → requires `FINANCIAL_VISIBILITY`
- "Approve" button on time entries → requires `TEAM_OVERSIGHT`

Use a `<RequiresCapability cap="INVOICING">` wrapper component for clean conditional rendering.

---

## Section 8 — Settings UI: Roles & Permissions Page

### Location

New settings card: "Roles & Permissions" in the Settings hub. Admin/Owner only.

### Layout

```
Roles & Permissions
├── System Roles (read-only section)
│   ├── Owner — All capabilities · Cannot be assigned
│   ├── Admin — All capabilities
│   └── Member — Basic access
│
├── Custom Roles (editable section)
│   ├── Role card (name, capability pills, member count, edit/delete actions)
│   ├── Role card ...
│   └── [+ New Role] button
│
└── Capability Reference (collapsible)
    └── Table explaining what each capability grants
```

### Create/Edit Role Dialog

```
┌──────────────────────────────────────┐
│  Create Role  /  Edit Role           │
│                                      │
│  Name: [________________________]    │
│  Description: [_________________]    │
│                                      │
│  Capabilities                        │
│  ┌──────────────────────────────────┐│
│  │ ☐ Financial visibility          ││
│  │   Rates, costs, profitability   ││
│  │                                 ││
│  │ ☐ Invoicing                     ││
│  │   Create, edit, send invoices   ││
│  │                                 ││
│  │ ☐ Project management            ││
│  │   Create projects, budgets      ││
│  │                                 ││
│  │ ☐ Team oversight                ││
│  │   Approve time, view team work  ││
│  │                                 ││
│  │ ☐ Customer management           ││
│  │   Customer CRUD, lifecycle      ││
│  │                                 ││
│  │ ☐ Automations                   ││
│  │   Automation rules, execution   ││
│  │                                 ││
│  │ ☐ Resource planning             ││
│  │   Allocations, capacity, leave  ││
│  └──────────────────────────────────┘│
│                                      │
│  N members using this role           │
│                                      │
│  [ Save ]  [ Delete ]  [ Cancel ]    │
└──────────────────────────────────────┘
```

### Delete Role

- Only allowed if no members are assigned.
- Shows a confirmation dialog listing assigned member count.
- "Reassign N members to [role dropdown] before deleting."

---

## Section 9 — Team Page Changes

### Member List Enhancement

The member list table gains:
- **Role column** shows the role name badge (custom role name or system role name)
- **Override indicator** — if a member has overrides, show "+N" or "custom" next to the badge
- **Click-to-edit** — clicking a member row opens a side panel (admin/owner only)

### Member Detail Panel

```
┌──────────────────────────────────┐
│  Carol Williams                  │
│  carol@firm.co.za                │
│                                  │
│  Role: [ Project Manager ▾ ]     │
│                                  │
│  Capabilities                    │
│  ┌──────────────────────────────┐│
│  │ ✓ Project management        ││
│  │ ✓ Team oversight            ││
│  │ ☐ Financial visibility      ││
│  │ ☐ Invoicing                 ││
│  │ ☐ Customer management       ││
│  │ ☐ Automations               ││
│  │ ☐ Resource planning         ││
│  └──────────────────────────────┘│
│                                  │
│  Toggles reflect role defaults.  │
│  Changes create per-user         │
│  overrides.                      │
│                                  │
│  [ Save ]  [ Cancel ]            │
└──────────────────────────────────┘
```

When the user toggles a capability that differs from the role preset, it becomes an override. The UI should indicate which toggles match the preset and which are overrides (e.g., italic label, "overridden" tag).

---

## Section 10 — Audit & Notifications

### Audit Events

- `ROLE_CREATED` — custom role created, details: `{name, capabilities}`
- `ROLE_UPDATED` — custom role modified, details: `{name, addedCapabilities, removedCapabilities, affectedMemberCount}`
- `ROLE_DELETED` — custom role deleted, details: `{name}`
- `MEMBER_ROLE_CHANGED` — member's role assignment changed, details: `{memberId, memberName, previousRole, newRole, overrides}`

### Notifications

- When a member's effective capabilities change (role reassignment, role preset edit, override change), send an in-app notification: "Your permissions have been updated. You now have access to: [list]."
- No email notification for capability changes (low urgency, in-app is sufficient).

---

## Section 11 — Migration & Backward Compatibility

### Flyway Migration

Single migration script that:
1. Creates `org_roles` table
2. Creates `org_role_capabilities` join table
3. Creates `member_capability_overrides` join table
4. Adds `org_role_id` column to `members` (nullable initially)
5. Seeds three system roles (OWNER, ADMIN, MEMBER) with appropriate capabilities
6. Updates existing members: maps `org:owner` → OWNER role, `org:admin` → ADMIN role, `org:member` → MEMBER role
7. Sets `org_role_id` to NOT NULL after backfill

This migration runs per tenant schema (standard Flyway multitenancy pattern).

### Backward Compatibility

- Existing `RequestScopes.ORG_ROLE` is preserved (still contains "org:owner"/"org:admin"/"org:member" from JWT). Not removed.
- New `RequestScopes.CAPABILITIES` is added alongside.
- `@PreAuthorize` annotations are replaced incrementally. Both old and new patterns can coexist during migration.
- The `GET /api/me/capabilities` endpoint works immediately — existing admins/owners get all capabilities, existing members get none (matching current behavior exactly).

---

## Out of Scope

- **Keycloak changes** — no JWT mapper changes, no realm config, no gateway changes
- **Project-level roles** — org-level only in this phase
- **Custom capability definitions** — firms cannot define new capability types
- **Deny-based permissions** — capabilities are additive only (with override subtraction from preset)
- **Role hierarchy/inheritance** — each role is a flat capability set
- **API key / service account roles** — human users only
- **Plan-tier gating of custom roles** — all plans get custom roles (can be added later)

## ADR Topics

- **ADR: Application-level vs. IDP-level authorization** — why capabilities live in the app DB, not Keycloak. Trade-offs: query flexibility and no IDP coupling vs. token-based enforcement.
- **ADR: Fixed capability set vs. dynamic permissions** — why 7 curated capabilities instead of per-endpoint permissions. Trade-offs: simplicity and UX clarity vs. granularity.
- **ADR: Override storage model** — why `+`/`-` prefixed strings in a join table vs. a separate effective-capabilities materialized table. Trade-offs: simplicity vs. query performance.

## Style & Boundaries

- Follow existing Shadcn UI patterns for the settings page and dialogs.
- Capability checks should be **cheap** — resolved once per request, not per-endpoint DB queries.
- The `@RequiresCapability` annotation should be as ergonomic as `@PreAuthorize` — a single annotation per method.
- Frontend capability context should be loaded once on app init and cached — no per-navigation API calls.
- Audit events follow the existing `AuditEventBuilder` pattern.
- Notifications follow the existing `NotificationService.notify()` pattern.
