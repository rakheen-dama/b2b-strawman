# Fix Spec: GAP-DI-01 — DRAFT invoices cannot be voided

## Problem

The data integrity test plan (T1.2.4) expected DRAFT -> VOID to be a valid transition. The system returns HTTP 409 with "Only approved or sent invoices can be voided". However, this is **intentional behavior**, not a bug.

## Root Cause (hypothesis)

Not a bug. The Invoice entity has two distinct void methods:
- `voidInvoice()` at line 226 of `Invoice.java` — only allows APPROVED or SENT invoices to be voided (exposed via `/api/invoices/{id}/void` endpoint)
- `voidDraft()` at line 235 of `Invoice.java` — only allows DRAFT invoices to be voided (used internally by `BillingRunService` when cancelling a billing run)

The official QA plan at `docs/qa/plans/02-invoicing-billing.md` test case INV-040 explicitly states: **"Void from DRAFT is rejected"** with expected HTTP 400. This confirms the system behaves as designed.

The rationale: DRAFT invoices can simply be deleted (they have no financial impact). Voiding is a lifecycle action for invoices that have already been approved/sent, preserving the audit trail.

## Fix

No code change needed. This is BY_DESIGN.

The data integrity test plan (T1.2.4) should be updated to expect DRAFT -> VOID to be REJECTED (HTTP 409), consistent with INV-040 in the official QA plan.

## Scope

N/A — no fix required.

## Verification

Re-run T1.2 with corrected test expectation. The existing behavior already passes INV-040.

## Estimated Effort

N/A (BY_DESIGN — no implementation work)
