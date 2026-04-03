# ADR-224: Demo Provisioning Bypass

**Status**: Accepted

**Context**:

The platform's org provisioning pipeline (Phase 39) follows a multi-step flow: potential client submits an access request with email → OTP verification → platform admin reviews and approves → `OrgProvisioningService` creates Keycloak org, calls `TenantProvisioningService` for schema/packs, creates subscription. This flow is designed for real prospects where identity verification and admin judgment are necessary.

For demo tenants — created by the platform admin to showcase the product during a prospect meeting — this flow introduces unnecessary friction. The admin is already authenticated. The "prospect" email may be the admin's own address for a self-demo. OTP verification for a demo tenant serves no purpose. The admin would be approving their own request. The entire access request → approval pipeline adds 5+ minutes and multiple page navigations to what should be a 30-second operation.

The question is how to enable fast demo provisioning: should it auto-approve a normal access request, bypass the access request entirely, or use a completely separate provisioning path?

**Options Considered**:

1. **Auto-approve access requests for demo tenants** — The admin creates an access request with a "demo" flag, and the system auto-approves it, skipping OTP verification and the admin review queue.
   - Pros:
     - Reuses the entire access request pipeline end-to-end.
     - Provisioning logic stays in one place (`AccessRequestApprovalService.approve()`).
     - Audit trail follows the same pattern as real provisioning.
   - Cons:
     - Still creates an `AccessRequest` record that pollutes the approval queue (even if immediately auto-approved, it shows up in history).
     - The access request flow validates fields that don't apply to demos (industry selection, company size, intended use case) — either these become optional or the demo must provide fake values.
     - The OTP verification step must be conditionally skipped — adding a bypass path to a security-sensitive flow is a code smell.
     - The admin must still fill out the access request form, even with reduced fields. More clicks than necessary.
     - Testing the bypass path requires testing the full access request pipeline with a new conditional branch.

2. **Bypass the access request pipeline, call TenantProvisioningService directly (chosen)** — A new `DemoProvisionService` calls `KeycloakAdminClient` for org/user setup and `TenantProvisioningService.provisionTenant()` for schema/packs, then overrides the subscription to ACTIVE/PILOT.
   - Pros:
     - Minimal new code: the Keycloak setup is 4 API calls (create org, find/create user, add member, set role), and `TenantProvisioningService.provisionTenant()` is a single method call that handles schema, migrations, and 11 pack seeders.
     - No access request pollution — demo tenants don't appear in the approval queue.
     - The provisioning logic (`TenantProvisioningService`) is reused without modification. Any future pack seeder added to provisioning automatically applies to demo tenants.
     - The form is minimal: org name, vertical profile, admin email, seed toggle. Four fields, one click.
     - Clean separation: `AccessRequestApprovalService` handles real prospects, `DemoProvisionService` handles demos.
   - Cons:
     - Two code paths that create tenants. If `TenantProvisioningService` changes its interface, `DemoProvisionService` must adapt. However, the interface is stable (one method: `provisionTenant(slug, name, verticalProfile)`).
     - Keycloak user setup is partially duplicated between `OrgProvisioningService` and `DemoProvisionService`. This is acceptable because the Keycloak operations differ (demo needs find-or-create user semantics; normal provisioning always links the authenticated user).

3. **Separate provisioning path with its own schema creation** — Build a standalone `DemoProvisioningPipeline` that handles everything: schema creation, migrations, pack seeding, subscription, and demo data seeding.
   - Pros:
     - Complete independence from the main provisioning pipeline — changes to one don't affect the other.
     - Could optimize for demo speed (e.g., skip certain pack seeders that aren't relevant for demos).
   - Cons:
     - Massive code duplication. `TenantProvisioningService` is 100+ lines with 11 injected seeders. Duplicating this means maintaining two parallel provisioning pipelines.
     - Every new pack seeder, migration, or provisioning step must be added in two places. This is the textbook maintenance nightmare that leads to "the demo tenant doesn't have feature X because we forgot to add the seeder."
     - The provisioning pipeline's retry logic (`@Retryable`) and idempotency checks would need to be reimplemented.
     - Violates DRY to no benefit — the demo provisioning is identical to real provisioning except for the access request bypass and post-provisioning overrides.

**Decision**: Option 2 — Bypass the access request pipeline, call `TenantProvisioningService` directly.

**Rationale**:

The access request pipeline exists to solve a specific problem: verifying the identity of unknown prospects and giving the platform admin judgment over who gets access. For demo tenants, this problem does not exist — the platform admin is the actor, not the subject. Forcing demo provisioning through the access request pipeline would be using a tool for a purpose it was not designed for.

Option 2 reuses the most important component (`TenantProvisioningService`) while bypassing the components that don't apply (`AccessRequest`, OTP verification, admin approval queue). The `TenantProvisioningService.provisionTenant()` method is a single, well-tested entrypoint that encapsulates schema creation, Flyway migrations, 11 pack seeders, and subscription creation. Calling it directly from `DemoProvisionService` ensures that demo tenants are identical to real tenants at the infrastructure level — same schema, same packs, same migrations.

Option 1 pollutes the access request pipeline with conditional bypass logic. The OTP verification step — a security-sensitive component — would need a "skip this for demos" flag, which is exactly the kind of shortcut that erodes trust in the security model over time.

Option 3 duplicates the provisioning pipeline unnecessarily. The provisioning steps are identical — the only difference is the entry point (admin panel form vs. access request approval). Duplicating 100+ lines of pipeline code to save one method call is not a trade-off this project makes.

**Consequences**:

- `DemoProvisionService` depends on `TenantProvisioningService` and `KeycloakAdminClient`. If `TenantProvisioningService` gains new parameters, `DemoProvisionService` must adapt.
- Demo tenants are indistinguishable from real tenants at the schema level. Same tables, same pack data, same migrations. The only difference is the subscription: ACTIVE status with PILOT billing method.
- The access request approval queue is not polluted with demo records. Admin can view the queue knowing every entry represents a real prospect.
- `KeycloakAdminClient` gains two new methods: `findUserByEmail()` and `createUser()`. These are small additions to an existing 380-line class.
- The demo provisioning form collects only 4 fields (org name, profile, email, seed toggle) — minimal friction for the admin.
- Future provisioning enhancements (e.g., new pack seeders, additional migration steps) automatically apply to demo tenants because `DemoProvisionService` delegates to the same `TenantProvisioningService`.
- Related: [ADR-223](ADR-223-billing-method-separate-dimension.md) (PILOT billing method set on demo subscriptions).
