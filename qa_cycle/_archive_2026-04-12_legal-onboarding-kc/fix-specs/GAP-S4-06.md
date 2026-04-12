# Fix Spec: GAP-S4-06 — Project Trust tab errors for customers with no ledger card yet

## Priority
MEDIUM — affects **every** matter whose customer has not yet received a trust deposit. This is
the default state for any new matter, so the regression is broadly visible on every legal-za
customer journey through Session 4 Phase E.

## Problem
On a project/matter detail page, the "Trust" tab renders the `TrustBalanceCard` component for
matters whose customer has no ledger card yet. The component shows "Unable to load trust
balance" because the backend endpoint
`GET /api/trust-accounts/{accountId}/client-ledgers/{customerId}` throws
`ResourceNotFoundException("ClientLedgerCard", customerId)`, which renders as an error response
(HTTP 404 per ProblemDetail — QA's note of "HTTP 500" is a minor misread, but the end-user
symptom is identical: frontend shows an error state instead of "Trust balance: R0.00").

QA Cycle 5 evidence: encountered on Moroka Estate matter whose customer had a ledger card from
the subsequent R50,000 deposit recording flow — the error state was visible **before** the
deposit was recorded (i.e. during normal new-matter creation).

## Root Cause
File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerService.java`

Method `getClientLedger(UUID customerId, UUID trustAccountId)` (lines 86–96):

```java
@Transactional(readOnly = true)
public ClientLedgerCardResponse getClientLedger(UUID customerId, UUID trustAccountId) {
  moduleGuard.requireModule(MODULE_ID);

  var ledgerCard =
      ledgerCardRepository
          .findByTrustAccountIdAndCustomerId(trustAccountId, customerId)
          .orElseThrow(() -> new ResourceNotFoundException("ClientLedgerCard", customerId));

  return toResponse(ledgerCard);
}
```

The semantic issue: "no ledger card yet" is **not an error** for the project Trust tab — it is
the perfectly normal state of every new customer before their first trust deposit. The method
should return a zero-balance response instead of throwing.

The upstream callsite is `ClientLedgerController.getByCustomer` (line 34–39 of
`ClientLedgerController.java`), called from the frontend via
`frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions.ts::fetchClientLedger`
(line 82–87), which is consumed by `frontend/components/trust/TrustBalanceCard.tsx` for the
project/matter Trust tab.

## Fix
**Backend-only fix.** Return a synthetic zero-balance response when no ledger card exists yet.
This preserves the semantics for all callers (listClientLedgers, getStatement, getHistory all
still work against real ledger card rows), and only changes the single getter that is called
from the "view a client's trust balance" UI path.

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerService.java`

**Before** (lines 86–96):
```java
@Transactional(readOnly = true)
public ClientLedgerCardResponse getClientLedger(UUID customerId, UUID trustAccountId) {
  moduleGuard.requireModule(MODULE_ID);

  var ledgerCard =
      ledgerCardRepository
          .findByTrustAccountIdAndCustomerId(trustAccountId, customerId)
          .orElseThrow(() -> new ResourceNotFoundException("ClientLedgerCard", customerId));

  return toResponse(ledgerCard);
}
```

**After**:
```java
@Transactional(readOnly = true)
public ClientLedgerCardResponse getClientLedger(UUID customerId, UUID trustAccountId) {
  moduleGuard.requireModule(MODULE_ID);

  return ledgerCardRepository
      .findByTrustAccountIdAndCustomerId(trustAccountId, customerId)
      .map(this::toResponse)
      .orElseGet(() -> emptyLedgerCardResponse(customerId, trustAccountId));
}

/**
 * Synthetic zero-balance response for customers who have no ledger card yet (i.e. no trust
 * transactions have been recorded). The ledger card is lazily created on the first
 * deposit/payment via {@code TrustTransactionService.applyTransaction}. Returning a synthetic
 * empty response here lets the "Trust" tab on every new matter render a clean R0.00 state
 * instead of a 404 error.
 */
private ClientLedgerCardResponse emptyLedgerCardResponse(UUID customerId, UUID trustAccountId) {
  // Best-effort customer name lookup — tolerate missing customers gracefully.
  String customerName =
      customerRepository.findById(customerId).map(c -> c.getName()).orElse(null);
  return new ClientLedgerCardResponse(
      null, // no persistent card id yet
      trustAccountId,
      customerId,
      customerName,
      BigDecimal.ZERO,
      BigDecimal.ZERO,
      BigDecimal.ZERO,
      BigDecimal.ZERO,
      BigDecimal.ZERO,
      null, // lastTransactionDate
      null, // createdAt
      null); // updatedAt
}
```

Note: the existing `ClientLedgerCardResponse` record (lines 53–65) already permits `null` for
`id`, `lastTransactionDate`, `createdAt`, `updatedAt` — they are non-primitive types. If the
frontend `ClientLedgerCard` TS type requires non-null `id`, update it to `string | null` at
`frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions.ts` (or wherever
`ClientLedgerCard` is declared) — grep for `interface ClientLedgerCard` or `type ClientLedgerCard`
in `frontend/lib/types/` first.

### Frontend check (probably no change needed)
`frontend/components/trust/TrustBalanceCard.tsx` renders the balance. As long as it reads
`balance`, `totalDeposits`, etc. (not `id`), it will render R0.00 cleanly against the synthetic
response. Verify by reading the component's render path and confirming it does not `return null`
or fall through to an error on `!ledger.id`.

### Tests
Add to `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerServiceTest.java` (or equivalent):

- `getClientLedger_returnsZeroBalanceWhenNoCardExists` — asserts method returns a
  `ClientLedgerCardResponse` with `balance == 0`, `customerId == providedId`,
  `trustAccountId == providedId`, `id == null`.
- `getClientLedger_returnsRealBalanceWhenCardExists` — existing happy path.

## Scope
**Backend primary; frontend verification only.**

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerServiceTest.java` (if exists — otherwise add the test to the closest existing trust service test)

Files to create: none
Migration needed: no

## Verification
1. Restart backend (`svc.sh restart backend`).
2. Navigate to Moroka Estate matter (or any matter) → "Trust" tab BEFORE recording any deposit.
3. Expected: card renders "Trust Balance R0,00" with zero deposits / payments rows. No error
   state.
4. Then record a deposit via `/trust-accounting/transactions` → revisit the Trust tab → balance
   updates to the real amount.
5. Backend log: no `ResourceNotFoundException("ClientLedgerCard", ...)` on the "no card yet"
   path.

## Estimated Effort
**S** (< 30 min). Single service method change + 2 test cases.
