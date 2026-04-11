# Fix Spec: GAP-PE-009 — Proposal acceptance crashes on null currency (invoice creation)

## Status
SPEC_READY

## Problem
Accepting a FIXED-fee proposal via the portal UI triggers a `DataIntegrityViolationException`:
```
null value in column "currency" of relation "invoices" violates not-null constraint
```

The entire acceptance transaction rolls back -- the proposal remains in SENT state, no project is created.

## Evidence
Discovered during Cycle 3 fix verification (see `qa_cycle/checkpoint-results/portal-cycle3.md`, Observations section).

## Root Cause (confirmed via code review)

The crash occurs in `ProposalOrchestrationService.createFixedFeeInvoices()` (line 372/398), which passes `proposal.getFixedFeeCurrency()` to `createDraftInvoice()` as the `currency` parameter. This value is passed directly to the `Invoice` constructor (line 434):

```java
var invoice = new Invoice(customerId, currency, customerName, customerEmail, null, orgName, createdBy);
```

The `Invoice` entity has `@Column(name = "currency", nullable = false, length = 3)` -- the DB enforces NOT NULL.

The `fixedFeeCurrency` field on the Proposal entity is **nullable** (`@Column(name = "fixed_fee_currency", length = 3)`) and is only set during proposal creation/update if explicitly provided by the client:

```java
if (fixedFeeCurrency != null) proposal.setFixedFeeCurrency(fixedFeeCurrency);
```

### How proposals get created without currency
The API allows creating a FIXED-fee proposal with `fixedFeeAmount` but without `fixedFeeCurrency`. The send-validation (`ProposalService.sendProposal()`) does not check for currency presence -- it only validates that `fixedFeeAmount > 0` for FIXED fee models.

This means a proposal can be DRAFTED, SENT, and reach ACCEPTANCE with `fixedFeeCurrency = null`.

### Why this is a code bug, not a data issue
1. No validation prevents sending a FIXED proposal without currency
2. The orchestration service does not fall back to org default currency
3. The DB constraint is correct (invoices must have a currency) -- the problem is upstream

## Fix

### Two-part fix (defense in depth)

#### Part 1: Currency fallback in `ProposalOrchestrationService.createFixedFeeInvoices()`
If `proposal.getFixedFeeCurrency()` is null, fall back to the org's `defaultCurrency` from `OrgSettings`. This is the defensive fix that prevents the crash.

In `ProposalOrchestrationService.java`, inject `OrgSettingsRepository` (or use the existing `OrgSettingsService`) and modify `createFixedFeeInvoices()`:

```java
private List<UUID> createFixedFeeInvoices(Proposal proposal, UUID projectId) {
    // ... existing code ...

    // Resolve currency: proposal field first, then org default
    String currency = proposal.getFixedFeeCurrency();
    if (currency == null || currency.isBlank()) {
        var orgSettings = orgSettingsRepository.findFirst();
        currency = orgSettings.map(OrgSettings::getDefaultCurrency).orElse("USD");
        log.info("Proposal {} has no fixedFeeCurrency, falling back to org default: {}",
            proposal.getId(), currency);
    }

    // Use resolved currency in createDraftInvoice calls below
    // (replace proposal.getFixedFeeCurrency() with currency variable)
    ...
}
```

The `OrgSettingsService` already has `DEFAULT_CURRENCY = "USD"` as a constant, so the fallback chain is: `proposal.fixedFeeCurrency` -> `orgSettings.defaultCurrency` -> `"USD"`.

#### Part 2: Send-time validation in `ProposalService.sendProposal()`
Add a validation check that rejects sending a FIXED-fee proposal without a currency. This prevents the bad state from reaching acceptance.

In `ProposalService.sendProposal()`, add after the existing fee-model validation:

```java
if (proposal.getFeeModel() == FeeModel.FIXED) {
    if (proposal.getFixedFeeAmount() == null || proposal.getFixedFeeAmount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidStateException("Missing fee amount", "Fixed-fee proposals require a fee amount > 0");
    }
    if (proposal.getFixedFeeCurrency() == null || proposal.getFixedFeeCurrency().isBlank()) {
        throw new InvalidStateException("Missing fee currency", "Fixed-fee proposals require a currency code");
    }
}
```

Similarly for RETAINER fee model, validate `retainerCurrency`.

## Scope

### Files to modify
1. `backend/src/main/java/.../proposal/ProposalOrchestrationService.java` — add `OrgSettingsRepository` dependency, currency fallback logic in `createFixedFeeInvoices()`
2. `backend/src/main/java/.../proposal/ProposalService.java` — add currency validation in `sendProposal()` (and equivalent for retainer currency)

### Migration needed
No.

## Verification
1. Create a FIXED-fee proposal via API without `fixedFeeCurrency` field
2. Attempt to send it -- should be rejected with 400 "Missing fee currency" (Part 2)
3. Create a FIXED-fee proposal WITH `fixedFeeCurrency: "ZAR"`
4. Send + accept via portal -- invoice should be created with currency ZAR (baseline)
5. Manually null out `fixedFeeCurrency` in DB for a SENT proposal, then accept -- should fall back to org default currency (Part 1 defense)

## Estimated Effort
S (< 1 hour). Both changes are small and isolated. No test infrastructure changes needed.

## Risk
LOW. Part 1 is a defensive null-check with sensible fallback. Part 2 is a validation tightening that only affects new proposals (existing proposals in SENT state with null currency are protected by Part 1).
