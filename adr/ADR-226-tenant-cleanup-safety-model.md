# ADR-226: Tenant Cleanup Safety Model

**Status**: Accepted

**Context**:

Demo tenants accumulate over time. Every prospect meeting, every internal test, every sales demo creates a tenant with a dedicated Postgres schema, Keycloak organization, public-schema records, and potentially S3 documents. Without a cleanup mechanism, these orphaned tenants consume database resources (each schema has 84+ tables from tenant migrations), pollute admin views, and create operational noise.

Tenant deletion is irreversible. Dropping a schema with `CASCADE` destroys all tenant data in a single SQL statement — there is no undo. Deleting the Keycloak organization removes user associations. Removing public-schema records (subscriptions, organizations, members) erases the tenant's existence from the platform. If this operation is accidentally performed on a paying tenant, the business impact is catastrophic: customer data loss, potential legal liability, and trust destruction.

The question is what safety mechanism should prevent accidental deletion of paying tenants while allowing efficient cleanup of demo tenants.

**Options Considered**:

1. **Boolean `is_demo` flag on the organization** — Add a column `is_demo BOOLEAN DEFAULT false` to the organizations table. Cleanup is restricted to orgs where `is_demo = true`.
   - Pros:
     - Explicit: a tenant is either demo or not, with a clear flag.
     - Simple query: `WHERE is_demo = true` filters demo tenants.
     - Flag is set at creation time and can be cleared to "graduate" a demo to production.
   - Cons:
     - A separate flag creates a dimension that must be kept in sync with the billing arrangement. A demo tenant that graduates to paying should have `is_demo = false` AND `billing_method = DEBIT_ORDER` — two fields that must change together. If one is missed, the safety model has a hole.
     - The flag has no semantic meaning beyond "can be deleted." It doesn't capture why the tenant can be deleted — is it a pilot, a test, a strategic partner?
     - Adding a column to the `organizations` table (global schema) requires a global migration and adds a field that most code paths never read.
     - Conflates identity ("this was created as a demo") with policy ("this can be deleted"). A demo that becomes a paying customer should not carry a `was_demo = true` flag forever.

2. **Billing method as the safety classifier (chosen)** — Cleanup is restricted to tenants with `billing_method IN (PILOT, COMPLIMENTARY)`. No additional flag needed — the billing method already encodes the commercial relationship.
   - Pros:
     - No new fields: `billing_method` is already being added in this phase ([ADR-223](ADR-223-billing-method-separate-dimension.md)). The safety model is a natural consequence of the billing dimension.
     - Semantically correct: the billing method captures *why* a tenant can be deleted — they are not paying. PILOT = trial/demo partner, COMPLIMENTARY = free access. Both imply the tenant has no contractual payment relationship.
     - Automatic graduation: when a demo tenant subscribes via PayFast (billing method changes to PAYFAST) or starts paying via debit order (admin changes to DEBIT_ORDER), it is automatically protected from cleanup. No separate flag to remember to update.
     - Consistent with the billing override workflow: the admin panel shows billing method prominently. The admin can see at a glance which tenants are deletable (PILOT/COMPLIMENTARY badge) and which are protected.
     - Reduces the chance of human error: there is one field that determines both commercial arrangement and cleanup eligibility. No possibility of the two getting out of sync.
   - Cons:
     - Couples cleanup eligibility to billing method. If a future billing method should be cleanup-eligible (hypothetical), the enum method `isCleanupEligible()` must be updated.
     - MANUAL billing method tenants (the default) are NOT cleanup-eligible, even though they may be unpaying. This is intentional — MANUAL is the default for all real tenants until they subscribe. Making MANUAL cleanup-eligible would expose every new tenant to accidental deletion.

3. **Soft-delete with recovery period** — Instead of immediate destruction, mark tenants as "pending deletion" with a 30-day recovery window. After 30 days, a scheduled job performs the actual cleanup.
   - Pros:
     - Safety net: accidental deletions can be recovered within the window.
     - Gradual: the admin can see "pending deletion" tenants and reverse the decision.
     - Mirrors industry patterns (AWS account deletion, GCP project shutdown).
   - Cons:
     - Demo cleanup needs to be fast. The admin creates a demo for a 2pm meeting, uses it, and wants it gone by 3pm. A 30-day window is operationally useless for demo tenants.
     - Increases complexity: a scheduled job, a "pending deletion" status, a recovery mechanism, UI for viewing and cancelling pending deletions.
     - The tenant's Keycloak organization and schema continue consuming resources during the recovery period.
     - For demo/pilot tenants, the data is ephemeral by definition. There is nothing to recover — the data was created by the seeder and has no business value.
     - Does not address the core safety concern: preventing deletion of paying tenants. A paying tenant with a 30-day recovery window is still at risk — the recovery window just delays the impact.

**Decision**: Option 2 — Billing method as the safety classifier.

**Rationale**:

The billing method already captures the information needed for the safety decision: is this tenant paying? If `billing_method` is PAYFAST, DEBIT_ORDER, or MANUAL, the tenant has (or is expected to have) a commercial relationship. Deleting such a tenant is a business-critical error. If `billing_method` is PILOT or COMPLIMENTARY, the tenant exists for demonstration or strategic purposes — its data is either seeded or experimental, and deletion is a routine operational action.

Option 1 (boolean flag) creates a redundant dimension. The billing method already tells us everything the flag would: PILOT and COMPLIMENTARY tenants are demo/partner tenants. Adding a separate `is_demo` flag introduces a synchronization requirement — the flag and the billing method must agree — with no benefit beyond slightly simpler query syntax.

Option 3 (soft-delete) is over-engineered for the use case. Demo tenants contain seeded data with no business value. The platform admin who created the demo is the same person who deletes it, usually within hours. A 30-day recovery window adds infrastructure complexity (scheduled job, recovery UI, state management) for a scenario that provides no value: recovering data that was generated by a seeder and can be regenerated by clicking "Create Demo" again.

The chosen model has an important property: the safety classification is *self-maintaining*. When a demo tenant graduates to a real customer (admin changes billing method from PILOT to DEBIT_ORDER), it is automatically protected from cleanup. No "remember to also clear the is_demo flag" step. No "the cleanup job deleted a tenant that we forgot to mark as non-demo." The billing method is the single source of truth for both commercial arrangement and deletion eligibility.

Additional safety layers reinforce the model:
1. **Name confirmation**: The admin must type the exact organization name in a text input to confirm deletion. This prevents click-through accidents.
2. **Audit-first**: The audit event is logged *before* any destructive operation. Even if cleanup partially fails, there is a record of the attempt.
3. **Per-step reporting**: The cleanup response shows which steps succeeded and which failed, enabling the admin to retry or investigate partial failures.

**Consequences**:

- No additional schema changes needed. The `billing_method` column (from [ADR-223](ADR-223-billing-method-separate-dimension.md)) serves double duty: commercial classification and cleanup eligibility.
- The `BillingMethod.isCleanupEligible()` method centralizes the eligibility check. Adding a new cleanup-eligible billing method in the future requires only an enum change.
- MANUAL billing method is explicitly NOT cleanup-eligible. This protects real tenants that are provisioned through the normal access request flow (which defaults to MANUAL) from accidental deletion.
- The cleanup service enforces billing method validation at the service layer, not the controller layer. The platform admin is authenticated, but the service rejects the operation if the billing method is wrong.
- Demo tenant "graduation" (PILOT → DEBIT_ORDER or PILOT → PAYFAST) automatically removes cleanup eligibility. No separate admin action needed.
- The name confirmation dialog provides a UX-level safety net. The admin sees the org name, member count, and creation date before typing the name to confirm. This is the established pattern for irreversible operations (GitHub repository deletion, AWS stack deletion).
- Related: [ADR-223](ADR-223-billing-method-separate-dimension.md) (billing method dimension that enables this safety model), [ADR-224](ADR-224-demo-provisioning-bypass.md) (demo provisioning sets PILOT billing method).
