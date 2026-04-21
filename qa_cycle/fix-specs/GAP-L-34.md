# Fix Spec: GAP-L-34 — Auto-create portal contact on customer create (shortcut to unblock QA)

## Problem

Day 3 Checkpoint 3.8 halted on BLOCKER: Create Information Request dialog detects 0 portal contacts for Sipho Dlamini (the customer created on Day 2) and renders

> "No portal contacts found for this customer. Please add a portal contact first."

Both `Save as Draft` and `Send Now` buttons are **disabled**. DB confirms `SELECT count(*) FROM tenant_5039f2d497cf.portal_contacts` → 0 for the legal-za tenant even though Sipho's customer row has `email = 'sipho.portal@example.com'`.

Scenario 3.9 expects "portal contact auto-populated from client record" and the firm has no firm-side UI to add one. Cascades into every portal POV day (4/8/11/15/30/46/61/75) — without at least one portal contact the firm cannot dispatch a magic-link and the portal user cannot authenticate.

## Root Cause (confirmed)

**File:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` line 343–347

- `@GetMapping("/{id}/portal-contacts")` exists — **no** corresponding `@PostMapping`.
- `PortalContactService.createContact` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactService.java` line 29) is fully implemented (validates customer exists, enforces email uniqueness per customer, defaults role to GENERAL) but is invoked by **only one caller**: `DevPortalController` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/dev/DevPortalController.java` line 115), which is a `@Profile({"local","dev"})` Thymeleaf harness for generating test magic-links. No production path reaches the service.
- `Customer` entity already holds `email` (populated by Day 2 scenario: `sipho.portal@example.com`). `CustomerService.createCustomer` (line 259–261) already publishes a `CustomerCreatedEvent` carrying `customerId + name + email + orgId + tenantId` — an ideal hook point that requires no new plumbing.

**Frontend:** `frontend/components/information-requests/create-request-dialog.tsx` line 196–213 — dialog is purely read-only on portal contacts; no "Add Portal Contact" affordance in the empty-state branch, no call to a POST endpoint.

## Fix — Shortcut (chosen)

Auto-create a `PortalContact` the moment a customer is created, using `customer.email` and `customer.name`. This is the simplest path that unblocks every downstream portal POV day without shipping a full CRUD flow.

**Why shortcut over full dialog:**

- Scenario 3.9 literally reads "portal contact auto-populated from client record" — auto-create *is* the scenario-specified behaviour, not a workaround.
- Less than 50 LOC of backend code (one new event listener bean). No frontend work. No new API surface to lint/test.
- The GET endpoint already exists; firm-side QA can still verify the contact was created via `/api/customers/{id}/portal-contacts`.
- Proper "Add Portal Contact" dialog with named contacts / multiple contacts per customer / roles (PRIMARY / BILLING / GENERAL) is a real feature — tracked separately as **GAP-L-40** (see status-update notes below) so it is not lost.

### Implementation

**Step 1.** Create `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactAutoProvisioner.java`:

```java
package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerCreatedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-creates a default PortalContact whenever a Customer is created with an email
 * address. This unblocks the "send information request" firm-side flow for the typical
 * one-contact-per-customer case, with no additional UI required.
 *
 * <p>For customers that need multiple named contacts (e.g. billing vs. primary), a
 * future "Add Portal Contact" firm-side dialog (GAP-L-40) will layer on top — this
 * listener only creates the default GENERAL contact; any extras go through the normal
 * create endpoint once it ships.
 */
@Component
public class PortalContactAutoProvisioner {

  private static final Logger log = LoggerFactory.getLogger(PortalContactAutoProvisioner.class);

  private final PortalContactService portalContactService;

  public PortalContactAutoProvisioner(PortalContactService portalContactService) {
    this.portalContactService = portalContactService;
  }

  @EventListener
  @Transactional
  public void onCustomerCreated(CustomerCreatedEvent event) {
    String email = event.getEmail();
    if (email == null || email.isBlank()) {
      log.debug(
          "Customer {} created without email — skipping portal-contact auto-provision",
          event.getCustomerId());
      return;
    }
    String orgId = event.getOrgId();
    if (orgId == null) {
      orgId = RequestScopes.getOrgIdOrNull();
    }
    if (orgId == null) {
      log.warn(
          "Customer {} created but orgId unavailable — cannot auto-provision portal contact",
          event.getCustomerId());
      return;
    }
    try {
      var contact =
          portalContactService.createContact(
              orgId,
              event.getCustomerId(),
              email,
              event.getName(),
              PortalContact.ContactRole.GENERAL);
      log.info(
          "Auto-provisioned portal contact {} for customer {} (email={})",
          contact.getId(),
          event.getCustomerId(),
          email);
    } catch (ResourceConflictException e) {
      // Contact already exists for this email on this customer — benign
      log.debug(
          "Portal contact for customer {} already exists with email {}",
          event.getCustomerId(),
          email);
    }
  }
}
```

- Listener is synchronous (no `@Async`) and `@Transactional` so it runs in the same tenant transaction as the originating customer-create — `search_path` is already bound via `RequestScopes.TENANT_ID`.
- The `ResourceConflictException` catch makes the listener idempotent on retry / replay; the event re-publish path treats an existing contact as a no-op.
- `orgId` is carried on the event; fallback to `RequestScopes.getOrgIdOrNull()` handles future cases where the event might be published from a background job without request scope.

**Step 2.** Seed one-time backfill for the already-stuck Day-2 customer. Because Sipho Dlamini already exists in `tenant_5039f2d497cf.customers` with zero contacts, the new listener will not fire for him (it only hooks future create events). QA will re-create Sipho via the scenario Day-2 flow on the next cycle — OR dev can run a one-off SQL probe as part of the verification step to confirm the listener fires (see Verification §3 below). If QA does NOT want to re-run Day 2, dev should surface a manual probe in the infra shell:

```
docker exec b2b-postgres psql -U postgres -d docteams -c "
SET search_path TO tenant_5039f2d497cf, public;
INSERT INTO portal_contacts (id, org_id, customer_id, email, display_name, role, status, created_at, updated_at)
SELECT gen_random_uuid(),
       '6b404169-f142-436a-8e6c-354a66843a5a',
       id,
       email,
       name,
       'GENERAL',
       'ACTIVE',
       NOW(),
       NOW()
FROM customers
WHERE email = 'sipho.portal@example.com'
  AND NOT EXISTS (
    SELECT 1 FROM portal_contacts pc WHERE pc.customer_id = customers.id
  );
"
```

This is a QA-only stopgap (aligned with the user's "No SQL shortcuts in QA" rule — this is dev running the backfill as part of the fix rollout, not QA skipping an API path). The clean alternative is: QA reseeds from Day 0 which takes less time than the SQL probe.

**Step 3.** No frontend change required for the blocker. The existing `fetchPortalContacts` action (`frontend/lib/actions/acceptance-actions.ts` line 168) will now return the auto-created contact, the dialog will auto-select it (existing branch in `create-request-dialog.tsx` line 77–79 already auto-selects when `contacts.length === 1`), and the Send Now / Save as Draft buttons will enable.

## Scope

- Backend only
- Files to modify: none
- Files to create:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactAutoProvisioner.java`
- Files to create (test): `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactAutoProvisionerTest.java` — one integration test asserting that `POST /api/customers` with an email produces one row in `portal_contacts` with role=GENERAL, and that a customer without an email produces zero rows.
- Migration needed: no
- Env / config: no

## Verification

1. Backend restart completes cleanly. No startup errors.
2. Re-run Day 2 scenario from 2.1 (or reseed stack) — create a new customer `Sipho Dlamini / sipho.portal@example.com` via `POST /api/customers`. Expected:
   ```
   docker exec b2b-postgres psql -U postgres -d docteams -c \
     "SELECT email, display_name, role, status FROM tenant_5039f2d497cf.portal_contacts ORDER BY created_at DESC LIMIT 1;"
   ```
   → `sipho.portal@example.com | Sipho Dlamini | GENERAL | ACTIVE`.
3. Re-run Day 3 Checkpoint 3.8: Create Information Request dialog on matter RAF-2026-001 → Portal Contact select now auto-selects "Sipho Dlamini (sipho.portal@example.com)". Save as Draft and Send Now buttons are enabled. With GAP-L-33 also fixed, Template dropdown shows "FICA Onboarding Pack (3 items)" and clicking it pre-fills the three items.
4. Day 3 Checkpoint 3.12: click Send → `POST /api/information-requests/{id}/send` returns 200 and Mailpit receives a magic-link email addressed to `sipho.portal@example.com` (confirms 3.14 also passes).
5. Edge case: create a customer without `email` → no portal_contact row produced, no error log entry.
6. Edge case: create a customer with the same email twice (happens when QA re-seeds) → no duplicate contact, only the original row remains, log line at DEBUG "Portal contact for customer … already exists".
7. `./mvnw test -Dtest=PortalContactAutoProvisionerTest` passes.

## Estimated Effort

**S (< 30 min)** — one new `@Component` file (≤ 60 LOC) + one integration test + backend restart. No migration, no frontend edits, no API contract change.

## Follow-up tracked separately

- **GAP-L-40** (OPEN, MED) — Firm-side "Add Portal Contact" dialog on client detail. Proper CRUD with named contacts, roles (PRIMARY / BILLING / GENERAL), suspend / archive. Needed for multi-contact customers (e.g. Moroka Family Trust where 2–3 beneficial owners each need portal access). This fix-spec deliberately does **not** cover that feature; the auto-provisioner covers the common one-contact-per-customer case which unblocks the Day 3 scenario and all downstream portal POV days.
