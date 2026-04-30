# Fix Spec: GAP-L-60 — Invoice/Fee Note creation blocked by PROSPECT-lifecycle gate

## Problem

Day 28 Checkpoint 28.4 (FAIL / BLOCKER, per
`qa_cycle/checkpoint-results/day-28.md:37`) halts. After QA worked around
three orthogonal prerequisite gaps (feature flag, tax number, project rate
card) and reached **Validate & Create Draft** inside `/customers/<id>?tab=invoices`
→ New Fee Note → Generate Fee Note, the backend returns an in-dialog error:

> Cannot create invoice for customer in PROSPECT lifecycle status

Sipho Dlamini (`8fe5eea2-75fc-4df2-b4d0-267486df68bd`) has
`lifecycle_status = PROSPECT` (Day 2 seed) and is unreachable via the
scenario-scripted lifecycle transitions (`Start Onboarding` succeeds; `Activate`
is blocked on a 0-of-9 `Legal Individual Client Onboarding` checklist that the
scenario never scripts completion for). DB probe confirms: no new rows in
`invoices` or `fee_notes` after the attempt.

This is the **third occurrence of the same gate family** — GAP-L-35 on matter
custom fields (OPEN/deferred) and GAP-L-56 on time entries (VERIFIED via PR
#1111). The fix shape mirrors L-56: split the `CREATE_INVOICE` switch arm out
of its overly strict clause. Cascades to Day 28.4–28.8 + Day 30 (portal pays
fee note) + every later billing day.

Secondary copy issue: the error string hardcodes "invoice" regardless of
vertical profile. Legal-za tenants use "fee note" terminology in every other
surface (breadcrumb, page H1, dialog title, CTA). The error leaks the wrong
term. Fix below handles it with zero new infrastructure.

## Root Cause (confirmed, not hypothesised)

**File:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuard.java`
lines 36–40 (post-L-56 state):

```java
case CREATE_INVOICE -> {
  if (status != LifecycleStatus.ACTIVE && status != LifecycleStatus.DORMANT) {
    throwBlocked(action, status);
  }
}
```

Only `ACTIVE` and `DORMANT` are permitted; `PROSPECT` / `ONBOARDING` /
`OFFBOARDING` / `OFFBOARDED` all reject. The exception message template at
line 55 produces:

```java
"Cannot create " + action.label() + " for customer in " + status + " lifecycle status"
```

…where `LifecycleAction.CREATE_INVOICE.label() == "invoice"` (from
`LifecycleAction.java:6`). There is no vertical-aware terminology lookup —
the label string is hard-coded in the enum constructor.

**Single caller site:** `InvoiceCreationService.validateInvoicePrerequisites`
(`backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceCreationService.java:769`):

```java
customerLifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE);
```

Note: `validateInvoicePrerequisites` ALSO enforces `customer.getStatus() == "ACTIVE"`
at line 764 — but that is the `status` column (ACTIVE vs ARCHIVED), separate
from `lifecycle_status`. Sipho's `status = ACTIVE` already, so line 764 passes
and line 769 is the actual blocker.

Existing unit tests in `CustomerLifecycleGuardTest` (lines 17–49) assert the
strict gate:
- `createInvoiceBlockedForProspect` (line 17)
- `createInvoiceBlockedForOnboarding` (line 25)
- `createInvoiceBlockedForOffboarded` (line 32)
- `createInvoiceAllowedForActiveAndDormant` (line 38)

These will need to flip to match the relaxed gate.

## Fix

**Two-step change** in `CustomerLifecycleGuard.java` — relax the gate AND fix
the copy leak.

### Step 1 — Relax the `CREATE_INVOICE` switch arm

### File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuard.java`

Replace lines 36–40:

```java
case CREATE_INVOICE -> {
  if (status != LifecycleStatus.ACTIVE && status != LifecycleStatus.DORMANT) {
    throwBlocked(action, status);
  }
}
```

with:

```java
case CREATE_INVOICE -> {
  // Invoice / fee note generation is a billing record-keeping operation that
  // fires across the entire engagement. Billable time and disbursements can
  // accumulate on PROSPECT customers (initial consultation, sheriff-fee
  // deposits paid before formal activation) and need to be bill-able
  // immediately. ONBOARDING and OFFBOARDING must also permit billing
  // (engagement in progress / close-out in progress). Only OFFBOARDED
  // (terminal) blocks — after a customer is fully off-boarded, billing is
  // closed and final invoices have already been issued.
  if (status == LifecycleStatus.OFFBOARDED) {
    throwBlocked(action, status);
  }
}
```

**Rationale (Option A from status.md triage):** mirrors the GAP-L-56 fix —
invoice creation is analogous to time-entry creation (both are billing
record-keeping), so both gate at the same boundary (OFFBOARDED only). This
keeps the `CREATE_PROJECT` / `CREATE_TASK` gates strict (those still need
ACTIVE-ish customer before starting new work) and matches the de-facto firm
workflow where partners bill consultation hours on prospects as soon as they
retain.

Option B (keep strict on CREATE_INVOICE, auto-transition PROSPECT → ACTIVE on
first invoice) was rejected: the Activate transition already requires
completing the `Legal Individual Client Onboarding` checklist which the legal
scenario doesn't (and shouldn't) front-load. Relaxing the gate is simpler and
matches L-56's precedent.

### Step 2 — Fix the "invoice" copy leak (generic, low-risk)

Leave the `throwBlocked` template in `CustomerLifecycleGuard.java:52-56`
unchanged — the fix is a single-line edit in
`backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/LifecycleAction.java:6`.
Change `CREATE_INVOICE("invoice")` to use a generic term:

```java
CREATE_INVOICE("bill"),
```

The resulting error becomes `"Cannot create bill for customer in PROSPECT
lifecycle status"` — vertical-neutral, still informative. "Bill" is correct
under legal (fee note = bill), accounting (invoice = bill), and consulting
(invoice = bill). No new terminology-lookup infrastructure needed.

Alternative phrasing options — pick during PR review, all are acceptable:
- `"billing document"` (most formal; slightly verbose)
- `"bill"` (recommended — short, neutral)

NOTE: because Step 1 means this error will now only fire on OFFBOARDED
customers (terminal state), users will rarely see it. The copy fix is
defensive hygiene more than a UX-blocker.

### Test changes

#### 1. Unit tests in `CustomerLifecycleGuardTest.java`

**File:** `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuardTest.java`

Flip the three `createInvoiceBlockedFor*` tests (lines 17–36) to allow-asserts.
Replace them with (mirroring the `createTimeEntry*` pattern at lines 75–119):

```java
@Test
void createInvoiceAllowedForProspect() {
  // GAP-L-60: invoices must be permitted on PROSPECT customers so consultation
  // hours and up-front disbursements can be billed before formal activation.
  var customer = createCustomerWithStatus(LifecycleStatus.PROSPECT);
  assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
      .doesNotThrowAnyException();
}

@Test
void createInvoiceAllowedForOnboarding() {
  var customer = createCustomerWithStatus(LifecycleStatus.ONBOARDING);
  assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
      .doesNotThrowAnyException();
}

@Test
void createInvoiceAllowedForOffboarding() {
  // GAP-L-60: final bill must be issuable while close-out is in progress.
  var customer = createCustomerWithStatus(LifecycleStatus.OFFBOARDING);
  assertThatCode(() -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
      .doesNotThrowAnyException();
}

@Test
void createInvoiceBlockedForOffboarded() {
  // GAP-L-60: terminal state — billing is closed once off-boarding completes.
  var customer = createCustomerWithStatus(LifecycleStatus.OFFBOARDED);
  assertThatThrownBy(
          () -> guard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE))
      .isInstanceOf(InvalidStateException.class);
}
```

Keep `createInvoiceAllowedForActiveAndDormant` (lines 38–49) as-is — still
valid.

#### 2. Integration-level coverage

There is an existing `InvoiceCreationServiceTest` or `InvoiceControllerTest`
suite. Add one case mirroring `TimeEntryIntegrationTest.createTimeEntryAllowedForProspectCustomer`:

```java
@Test
void createInvoice_prospectCustomer_succeeds() throws Exception {
  // Provision tenant, sync owner, create PROSPECT customer via
  // TestEntityHelper.createCustomer (API default is PROSPECT), attach a
  // project + one billable time entry with a rate override, POST to
  // /api/invoices with { customerId, currency, timeEntryIds } — expect 201 Created.
}
```

If a closer harness exists (e.g. `InvoiceIntegrationTest.java`), place the test
there; otherwise, piggyback on the existing Prospect-path test in whichever
file already sets up a PROSPECT customer + billable time entry. The GAP-L-56
fix (PR #1111) already added the plumbing for time entries against PROSPECT,
so the fixtures are borrowable.

## Scope

- **Backend only.**
- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuard.java`
    (one switch case relaxed, ~6 lines changed)
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/LifecycleAction.java`
    (one enum label string changed, 1 line)
  - `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuardTest.java`
    (three block-asserts flipped to allow-asserts + one OFFBOARDED block, ~30 lines changed)
  - `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/*IntegrationTest.java`
    (add one PROSPECT-allowed case, ~25 lines — target file TBD by Dev, pick
    the closest existing suite with PROSPECT customer fixtures)
- Files to create: none
- Migration needed: **no** (pure logic change, no schema)
- Env / config: none

## Verification

1. **Backend restart required** (Java source change, no hot-reload):
   `bash compose/scripts/svc.sh restart backend`. NEEDS_REBUILD = true.

2. Run targeted unit+integration suite first:
   ```bash
   ./mvnw test -Dtest='CustomerLifecycleGuard*,Invoice*' -q
   ```
   Expect EXIT=0 including the new PROSPECT test. Three previously-passing
   `createInvoiceBlockedFor*` tests must be renamed/flipped (not just
   deleted) so the green count is preserved for audit.

3. QA re-runs **Day 28 Phase A**:
   - **28.4** (**BLOCKER** — primary assertion): re-open `/customers/<Sipho-id>?tab=invoices`
     → New Fee Note → fill From/To/Currency (already-worked-around
     prerequisites stay resolved: tax_number = `0123456789`, Bob's project
     rate override still active) → Fetch Unbilled Time → 5/5 selectable
     → Validate & Create Draft → Create Draft.
     Expect dialog to close cleanly and a new row in `invoices` (or
     `fee_notes` — whichever the legal-za vertical writes to).
     DB probe:
     ```sql
     SELECT count(*), status, customer_id
     FROM tenant_5039f2d497cf.invoices
     WHERE customer_id = '8fe5eea2-75fc-4df2-b4d0-267486df68bd'
     GROUP BY status, customer_id;
     ```
     Expect `(1, DRAFT, 8fe5eea2-…)`.
   - **28.5** — confirm draft preview page renders with 5 time lines + totals.
     (Disbursement inclusion is GAP-L-63, separate fix.)
   - **28.6–28.7** — swap to Thandi (Owner), Approve & Send → expect
     Mailpit delivery.

4. **Regression spot-check**:
   - Create an OFFBOARDED customer, attempt an invoice → expect
     `InvalidStateException` with `"Cannot create bill for customer in
     OFFBOARDED lifecycle status"` (note new generic label) still fires.
   - Create an ACTIVE customer, attempt an invoice → expect success (should
     already pass and not change).
   - Spot-check `CREATE_PROJECT` and `CREATE_TASK` on a PROSPECT customer
     — expect them to **still reject** (L-35 behaviour preserved — do NOT
     bundle that fix into this spec).

5. Copy-leak verification: trigger the OFFBOARDED path above and assert the
   error message reads `"Cannot create bill for customer in OFFBOARDED
   lifecycle status"` — not `"Cannot create invoice …"`.

## Estimated Effort

**S (< 30 min)** — one switch-case split + one enum label + three test flips
+ one integration test. Zero migration, zero API surface change, zero config,
zero frontend. Regression surface is narrow: `CREATE_INVOICE` has exactly one
caller (`InvoiceCreationService.java:769`) and three existing unit tests
whose assertions flip directly.

## Parallelisation Notes

**SAFE for parallel Dev execution alongside GAP-L-63.** No file overlap:

- L-60 touches only `backend/.../compliance/CustomerLifecycleGuard.java` +
  `backend/.../compliance/LifecycleAction.java` + one backend test file.
- L-63 touches only `frontend/components/invoices/invoice-generation-dialog.tsx`
  + `frontend/components/invoices/use-invoice-generation.ts` +
  `frontend/app/(app)/org/[slug]/customers/[id]/invoice-actions.ts` +
  `frontend/lib/types/invoice.ts` (frontend-only — backend already ships the
  data).

Two Dev agents can work in separate worktrees simultaneously without merge
conflicts. Both fixes can land independently; no cross-dependency at the code
level. QA will see the full Day 28 path unblock only once BOTH have shipped
(L-60 unblocks draft creation; L-63 makes disbursements appear in the
draft), but either can merge first.

## Status Triage

**SPEC_READY.** Minimal, surgical, on-path for Day 28 fee-note generation.
Root cause confirmed by direct code read
(`CustomerLifecycleGuard.java:36-40` + `InvoiceCreationService.java:769` +
`LifecycleAction.java:6`). Cousin gap GAP-L-35 (matter custom fields)
remains OPEN/deferred and is NOT bundled into this fix.
