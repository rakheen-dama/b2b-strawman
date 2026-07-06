# Fix Spec: LZKC-008 — Send validation requires Tax Number for INDIVIDUAL client

## Problem
Day 28 / 28.6: sending INV-0001 for Sipho (INDIVIDUAL, RAF claimant) raised CRITICAL validation "Tax Number is required to send an invoice"; the owner had to use "Send Anyway". Inconsistent with OBS-2102, which exempts INDIVIDUAL customers from tax-number at activation. Non-admin members presumably cannot send at all.

## Root Cause (verified)
- Send-side: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceValidationService.java:105-118` — `checkCustomerTaxNumber(...)` calls `StructuralPrerequisiteCheck.isTaxNumberMissing(customer)` (line 109) with NO customer-type check; failure message at line 114. Invoked from `validateInvoiceSend` (lines 68-77) → `InvoiceTransitionService.java:214`.
- `StructuralPrerequisiteCheck.java:204` — `isTaxNumberMissing` ignores customer type.
- Activation-side precedent (OBS-2102): `StructuralPrerequisiteCheck.java:131-138` — explicit `continue` skipping `tax_number` when `context == LIFECYCLE_ACTIVATION && customerType == INDIVIDUAL`.

**Product-decision flag (orchestrator must confirm before dev starts):** inline Javadoc at `StructuralPrerequisiteCheck.java:59-66` and `131-134` documents the send-time tax-number hard-enforcement as *intentional* ("regardless of customer type", SARS rationale). The proposed fix contradicts that documented intent. QA's position (scenario + OBS-2102 consistency): an INDIVIDUAL RAF claimant has no VAT/tax number and a fee note must still be sendable without an owner override. Options:
- (a) Exempt INDIVIDUAL from the send-side CRITICAL check (proposed).
- (b) Downgrade to WARNING for INDIVIDUAL (sendable by any role, still visible).
- (c) Keep as-is and amend the scenario — requires explicit authorization per CLAUDE.md §6.

## Fix (option a, pending authorization; option b is the same file/line)
In `InvoiceValidationService.checkCustomerTaxNumber` (line 105): after loading the customer, return a passed check when `customerOpt.get().getCustomerType() == CustomerType.INDIVIDUAL`, mirroring the OBS-2102 activation exemption. Keep the CRITICAL check for COMPANY/TRUST. Update the Javadoc in `StructuralPrerequisiteCheck.java:59-66` to reflect the new policy.

## Scope
Backend only
Files to modify: `InvoiceValidationService.java` (+ Javadoc touch-up in `StructuralPrerequisiteCheck.java`)
Files to create: none
Migration needed: no

## Verification
Re-run Day 28 / 28.6 flow with a scratch invoice for an INDIVIDUAL client: Send proceeds with no Tax Number CRITICAL (or WARNING-only under option b). Regression: COMPANY client without tax number still blocked. Extend `InvoiceValidationService` tests for both customer types.

## Estimated Effort
S (< 30 min)

## AUTHORIZED DECISION (orchestrator/user, 2026-07-06)
**Option (b): downgrade to WARNING for INDIVIDUAL.** INDIVIDUAL without tax number → visible WARNING on send (any role can send, no owner override needed). COMPANY/TRUST keep the CRITICAL check. Update the Javadoc in StructuralPrerequisiteCheck to document the WARNING policy for INDIVIDUAL at send time.
