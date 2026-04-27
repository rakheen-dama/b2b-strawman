# Fix Spec: BUG-CYCLE26-06 — PROPOSAL_SEND prereq honors portal_contact for INDIVIDUAL customers

## Problem

Sending a proposal to an INDIVIDUAL customer fails with `"2 required field(s) missing for Proposal Sending"` because the backend `PROPOSAL_SEND` structural prerequisite hard-requires `Customer.contact_name` + `Customer.contact_email` — but those columns are NULL by design for INDIVIDUAL customers in the legal-za vertical. Recipient identity for INDIVIDUALs is held in the `portal_contact` entity, which the firm explicitly selects in the Send Proposal dialog.

UX trap: the dialog asks the user to pick a Recipient (a portal_contact), allows Send, then fails server-side with no link to which fields/where to fix.

Evidence:
- `qa_cycle/checkpoint-results/day-07.md` Cycle 14 §7.8 (line 185) — Sipho Dlamini (`c4f70d86-…`, INDIVIDUAL, `sipho.portal@example.com`) → Send Proposal `0781c5ad-…/PROP-0001/DRAFT` → `2 required field(s) missing for Proposal Sending`. DB confirms `customers.contact_name = NULL, contact_email = NULL`; portal_contact `127d1c7d-…` ACTIVE/GENERAL with email + display name.
- `.playwright-mcp/day07-cycle14-7.9-after-send.yml` — paragraph e520.
- Day 2 cycle 6 (`day-02.md` line 20) — INDIVIDUAL Add-Client form does not surface `contact_name`/`contact_email` inputs; those fields are reserved for COMPANY customers where the natural-person liaison differs from the legal entity.

## Root Cause (verified)

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/StructuralPrerequisiteCheck.java:50-54`

```java
private static final List<FieldCheck> PROPOSAL_SEND_FIELDS =
    List.of(
        new FieldCheck("contact_name", "Contact Name", Customer::getContactName),
        new FieldCheck("contact_email", "Contact Email", Customer::getContactEmail),
        new FieldCheck("address_line1", "Address Line 1", Customer::getAddressLine1));
```

This list of three structural checks is invoked for every PROPOSAL_SEND, regardless of customer type and regardless of whether the customer has an ACTIVE portal_contact (which is the canonical recipient identity for proposal sends).

The check is invoked from `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java:198-220` (`checkStructural`, PROPOSAL_SEND branch):

```java
case PROPOSAL_SEND -> {
  if (entityType == EntityType.CUSTOMER) {
    var customer = loadCustomer(entityId);

    // Promoted field null-checks (contact name, contact email, address) ← line 203
    violations.addAll(StructuralPrerequisiteCheck.check(customer, context));

    // Portal contact check                                                ← line 206
    var contacts = portalContactRepository.findByCustomerId(entityId);
    boolean hasContactWithEmail =
        contacts.stream().anyMatch(c -> c.getEmail() != null && !c.getEmail().isBlank());
    if (!hasContactWithEmail) {
      violations.add( ... "Customer must have a portal contact with an email address ..." );
    }
  }
}
```

The portal_contact existence check (line 206-219) is the right invariant for "we have a recipient". The `contact_name`/`contact_email` structural fields are a stale duplicate from before the portal_contact entity existed as the source of truth for proposal-send identity. Confirmed downstream:

- `backend/.../proposal/ProposalVariableResolver.java:43` — proposal template variables use `contact.getDisplayName()` (portal contact), never `customer.getContactName()`.
- `backend/.../acceptance/AcceptanceService.java:228` — `notificationService.sendAcceptanceEmail(request, contact, doc, ...)` uses the PortalContact for the email recipient; `Customer.contact_email` is never read.
- `ProposalService.sendProposal(proposalId, portalContactId)` (line 601) takes the portal_contact ID as an explicit parameter — confirming that's the canonical recipient identity at the call site.

So `Customer.contact_name`/`contact_email` provide no value at PROPOSAL_SEND time when a portal_contact is already required and selected. They remain useful as descriptive metadata for COMPANY customers without a portal_contact, but should not block sends.

`address_line1` is a separate concern — it represents the **customer's** billing/correspondence address (used in the proposal letter body, not the email recipient), so it remains a legitimate prerequisite. This fix preserves the `address_line1` check.

## Fix

**Approach (Option 1, per QA recommendation): backend-side fallback.** When the customer has at least one ACTIVE portal_contact with a non-blank email, suppress the structural `contact_name`/`contact_email` violations — the portal_contact is the canonical recipient identity and is already required (and validated) for the send. Keep `address_line1` as a hard prerequisite. Keep the existing "must have a portal contact with email" check intact.

### Step 1 — `StructuralPrerequisiteCheck.java`

Add a context-aware overload that accepts a "portal contact identity is satisfied" boolean and skips the contact_name/contact_email checks when true. Keep the legacy two-arg `check(customer, context)` for backward compat (callers in LIFECYCLE_ACTIVATION + INVOICE_GENERATION branches use it unchanged).

In `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/StructuralPrerequisiteCheck.java`:

1. Add a new public static method below the existing `check(Customer, PrerequisiteContext)` (after line 117):

   ```java
   /**
    * Like {@link #check(Customer, PrerequisiteContext)}, but allows the caller to declare
    * that an alternative source of recipient identity exists (e.g. an ACTIVE portal_contact
    * with email) so that the {@code contact_name}/{@code contact_email} structural checks
    * for PROPOSAL_SEND can be skipped. Other field checks (address, etc.) are unaffected.
    *
    * <p>Rationale: for legal-za INDIVIDUAL customers, recipient identity is held in
    * {@code portal_contact}, not on the {@code Customer} entity columns. Hard-requiring
    * those columns is a stale invariant from before {@code portal_contact} became the
    * canonical proposal recipient. See BUG-CYCLE26-06.
    */
   public static List<PrerequisiteViolation> check(
       Customer customer,
       PrerequisiteContext context,
       boolean portalContactIdentitySatisfied) {
     List<FieldCheck> requiredFields = CONTEXT_FIELDS.get(context);
     if (requiredFields == null) {
       return List.of();
     }
     Map<String, Object> customFields = customer.getCustomFields();
     List<PrerequisiteViolation> violations = new ArrayList<>();
     for (FieldCheck field : requiredFields) {
       if (portalContactIdentitySatisfied
           && context == PrerequisiteContext.PROPOSAL_SEND
           && (field.fieldSlug().equals("contact_name")
               || field.fieldSlug().equals("contact_email"))) {
         continue;
       }
       if (!hasValue(field, customer, customFields)) {
         violations.add(
             new PrerequisiteViolation(
                 "STRUCTURAL",
                 field.displayName() + " is required for " + context.getDisplayLabel(),
                 "CUSTOMER",
                 customer.getId(),
                 field.fieldSlug(),
                 null,
                 "Fill the " + field.displayName() + " field on the customer profile"));
       }
     }
     return violations;
   }
   ```

2. Refactor the existing two-arg `check(Customer, PrerequisiteContext)` (lines 94–117) to delegate:

   ```java
   public static List<PrerequisiteViolation> check(Customer customer, PrerequisiteContext context) {
     return check(customer, context, false);
   }
   ```

### Step 2 — `PrerequisiteService.java` (PROPOSAL_SEND branch)

In `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java`, replace the PROPOSAL_SEND case body (lines 198–220) so the portal_contact lookup happens FIRST and feeds into the structural check:

```java
case PROPOSAL_SEND -> {
  if (entityType == EntityType.CUSTOMER) {
    var customer = loadCustomer(entityId);

    // Resolve the portal-contact recipient identity FIRST. If at least one ACTIVE
    // portal_contact has a non-blank email, the contact_name/contact_email
    // structural checks are satisfied via that contact (BUG-CYCLE26-06). The
    // customer's own contact_name/contact_email columns are not the canonical
    // identity at proposal-send time — the portal_contact is.
    var contacts = portalContactRepository.findByCustomerId(entityId);
    boolean hasActiveContactWithEmail =
        contacts.stream()
            .anyMatch(
                c ->
                    c.getStatus() == PortalContact.ContactStatus.ACTIVE
                        && c.getEmail() != null
                        && !c.getEmail().isBlank());

    // Promoted field null-checks. address_line1 always required; contact_name +
    // contact_email skipped when a portal_contact already provides recipient identity.
    violations.addAll(
        StructuralPrerequisiteCheck.check(customer, context, hasActiveContactWithEmail));

    // Existence check stays — even with the structural fallback, we still need
    // at least one ACTIVE portal_contact with email to actually send the proposal.
    if (!hasActiveContactWithEmail) {
      violations.add(
          new PrerequisiteViolation(
              "STRUCTURAL",
              "Customer must have a portal contact with an email address to send a proposal",
              entityType.name(),
              entityId,
              null,
              null,
              "Add a portal contact with email on the customer detail page"));
    }
  }
}
```

Notes:
- The new check filters on `ContactStatus.ACTIVE` (the existing implementation only filtered on email non-blank — this is a tightening, not a loosening: SUSPENDED/ARCHIVED contacts shouldn't satisfy the prereq).
- Add the import for `io.b2mash.b2b.b2bstrawman.portal.PortalContact` (top of file) since the enum constant is now referenced.

### Step 3 — Tests

#### `StructuralPrerequisiteCheckTest.java`

In `backend/src/test/java/io/b2mash/b2b/b2bstrawman/prerequisite/StructuralPrerequisiteCheckTest.java`, add three new tests:

```java
@Test
void proposalSend_individualCustomer_withPortalContact_skipsContactFields() {
  // INDIVIDUAL customer with portal_contact identity satisfied — contact_name/contact_email
  // structural checks must be suppressed. address_line1 still required.
  var customer =
      TestCustomerFactory.createActiveCustomer("Sipho Dlamini", "sipho@test.com", MEMBER_ID);
  customer.setCustomerType(CustomerType.INDIVIDUAL);
  customer.setAddressLine1("12 Loveday St");
  // contactName + contactEmail intentionally null

  var violations =
      StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.PROPOSAL_SEND, true);

  assertThat(violations).isEmpty();
}

@Test
void proposalSend_individualCustomer_withPortalContact_butMissingAddress_returnsAddressViolation() {
  var customer =
      TestCustomerFactory.createActiveCustomer("Sipho Dlamini", "sipho@test.com", MEMBER_ID);
  customer.setCustomerType(CustomerType.INDIVIDUAL);
  // address_line1 + contact_name + contact_email all null

  var violations =
      StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.PROPOSAL_SEND, true);

  // Only address_line1 should violate; contact_name/contact_email suppressed by portal_contact.
  assertThat(violations).hasSize(1);
  assertThat(violations.getFirst().fieldSlug()).isEqualTo("address_line1");
}

@Test
void proposalSend_companyCustomer_noPortalContact_stillRequiresContactFields() {
  // No portal_contact identity → legacy behaviour preserved (3 violations on the Sipho
  // setup) and consumers like the existing dialog UX still see the same error shape.
  var customer =
      TestCustomerFactory.createActiveCustomer("Acme Pty Ltd", "billing@acme.com", MEMBER_ID);
  customer.setCustomerType(CustomerType.COMPANY);
  // address_line1 + contact_name + contact_email all null

  var violations =
      StructuralPrerequisiteCheck.check(customer, PrerequisiteContext.PROPOSAL_SEND, false);

  assertThat(violations).hasSize(3);
  assertThat(violations)
      .extracting(PrerequisiteViolation::fieldSlug)
      .containsExactlyInAnyOrder("contact_name", "contact_email", "address_line1");
}
```

(Add `import io.b2mash.b2b.b2bstrawman.customer.CustomerType;` if not present.)

The existing `proposalSend_missingContactEmail_returnsViolation` and `proposalSend_allFieldsPresent_returnsEmptyViolations` tests stay green because they call the two-arg overload which routes to `portalContactIdentitySatisfied=false`.

#### `ProposalSendTest.java` (integration)

In `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalSendTest.java`, add an integration test mirroring the cycle-14 scenario:

```java
@Test
void sendProposal_individualCustomer_withPortalContact_succeeds_evenWhenContactColumnsNull()
    throws Exception {
  // Mirror BUG-CYCLE26-06: INDIVIDUAL customer with NULL contact_name/contact_email but
  // an ACTIVE portal_contact bearing the recipient identity. Send must succeed (200/204
  // with status flipping DRAFT → SENT and an acceptance request row created).
  // ...set up customer (INDIVIDUAL + address_line1, null contact fields)...
  // ...insert ACTIVE portal_contact via the same SQL helper used in the existing test...
  // ...create DRAFT proposal via API...
  // ...POST /api/proposals/{id}/send with portalContactId → expect 200 and SENT status...
}
```

Reuse the existing portal_contacts insert helper at line 447 of that file. The test should fail before the fix (`PrerequisiteNotMetException`) and pass after.

## Scope

- **Backend only.** Frontend Send-Proposal dialog already wires the portal_contact recipient correctly — no changes there.
- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/StructuralPrerequisiteCheck.java` (add 3-arg overload + refactor 2-arg to delegate)
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java` (PROPOSAL_SEND branch reorders portal_contact lookup, threads `hasActiveContactWithEmail` into structural check; tightens to `ContactStatus.ACTIVE`)
  - `backend/src/test/java/io/b2mash/b2b/b2bstrawman/prerequisite/StructuralPrerequisiteCheckTest.java` (3 new unit tests)
  - `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalSendTest.java` (1 new integration test for the BUG-CYCLE26-06 scenario)
- Files to create: none.
- Migration needed: **no.** No schema or data changes — `Customer.contact_name`/`contact_email` columns remain in place for COMPANY customers that legitimately use them.
- Frontend changes: **none.** The existing `"X required field(s) missing"` error rendering in the Send Proposal dialog will simply stop firing for the INDIVIDUAL+portal_contact case once the backend stops producing the violation.

## Verification

After backend rebuild + restart (`bash compose/scripts/svc.sh restart backend`):

1. **Day 7 §7.8** — Re-walk Send Proposal as Thandi (Owner) on RAF matter:
   - Open `/matters/{e788a51b-…}` → click **Send Proposal** on `PROP-0001 / DRAFT`.
   - Recipient combobox: select `Sipho Dlamini (sipho.portal@example.com)`.
   - Click **Send**. Expected: dialog closes, proposal flips to **SENT**, no "required fields missing" error.
   - DB: `proposals.status='SENT'`, `acceptance_requests` row created with `portal_contact_id=127d1c7d-…`.
2. **Day 7 §7.9** — Acceptance URL surfaces in proposal detail.
3. **Day 7 §7.10** — Mailpit receives the proposal-send email; recipient = `sipho.portal@example.com` (portal_contact email); recipient name = portal_contact display name.
4. **Day 7 §7.11** — Sipho clicks portal href → portal authenticates via token.
5. **Regression — COMPANY customer without portal_contact still blocks correctly**: pick (or seed) a COMPANY customer with no portal_contact and null contact fields. Attempt Send. Expected: still fails with the original "Contact Name / Contact Email / portal contact required" violations. Verifies the relaxation is scoped, not blanket.
6. **Regression — `address_line1` still required**: temp-null an INDIVIDUAL customer's `address_line1` while keeping the portal_contact in place. Attempt Send. Expected: fails with `Address Line 1 is required for Proposal Sending` and nothing else.

Backend tests:
```bash
./mvnw -pl . test -Dtest=StructuralPrerequisiteCheckTest,ProposalSendTest
```

Both classes must remain green.

## Estimated Effort

**S (< 30 min)** — single backend file overload + one branch reorder in `PrerequisiteService` + 4 tests. No schema changes, no frontend changes. Mirrors the existing dual-source pattern (entity column + JSONB) by adding a third source (portal_contact identity) gated to a single context.
