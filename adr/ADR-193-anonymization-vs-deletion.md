# ADR-193: Anonymization vs. Deletion

**Status**: Accepted
**Date**: 2026-03-19
**Phase**: 50 (Data Protection Compliance)

## Context

When a data subject exercises their right to erasure (POPIA Section 24, GDPR Article 17), the responsible party must remove the personal information from their systems. However, SA tax law (Income Tax Act Section 29, VAT Act Section 55) requires financial records to be retained for 5 years from the end of the relevant tax year. DocTeams stores invoices, billable time entries, and payment records linked to customer records. A customer who has been invoiced cannot simply be deleted from the database without violating tax retention obligations.

The existing `Customer.anonymize(replacementName)` method replaces `name` and `email` and nulls `phone` and `idNumber`, but does not address `notes`, `customFields`, or the `LifecycleStatus` enum. The existing `DataAnonymizationService` transitions the customer to `OFFBOARDED`, which allows re-activation — an undesirable state for an anonymized customer. Additionally, the current anonymization does not update customer references on invoices (the anonymized customer name is visible but the invoice still shows the old reference in some contexts).

We need to decide how to handle the "delete personal data" operation given these conflicting legal requirements.

## Options Considered

### Option 1: Hard Deletion with Financial Record Orphaning

Delete the customer record entirely. Invoices and time entries that reference the customer are orphaned — their `customer_id` FK is set to null or points to a tombstone record.

- **Pros:**
  - Strongest interpretation of "deletion" — no personal data remains
  - Simple to implement — cascade deletes or nullify FKs
  - Data subject cannot be re-identified from remaining records

- **Cons:**
  - Orphaned invoices lose their customer context — auditors cannot trace invoices back to a customer relationship, violating the purpose of tax record retention
  - FK nullification breaks existing queries that join through customer_id
  - Cascade delete of invoices would directly violate SA tax law
  - No recovery path — if the deletion was triggered erroneously, data is permanently lost with no record of what existed

### Option 2: Anonymization with Financial Record Preservation (Selected)

Replace all personal information (PII) with anonymized reference values. Customer name becomes `"Anonymized Customer REF-{shortId}"`, email becomes `"anon-{id}@anonymized.invalid"`, and all other PII fields are nulled. Financial records (invoices, billable time entries) are preserved with the anonymized reference ID replacing the customer name. A new terminal `ANONYMIZED` lifecycle status prevents re-activation.

- **Pros:**
  - Satisfies POPIA Section 24 — personal information is no longer identifiable
  - Satisfies SA tax law — financial records are intact with a traceable (but anonymous) reference ID
  - Audit trail is preserved — audit events remain linked to the anonymized customer record
  - Reference ID (`REF-{shortId}`) allows auditors to verify financial record completeness without re-identifying the data subject
  - `ANONYMIZED` terminal status prevents accidental re-use of the record
  - Pre-anonymization export provides a recovery path and proof of compliance

- **Cons:**
  - The customer record still exists in the database (anonymized), which some data subjects may object to
  - The reference ID is deterministic from the customer UUID — if someone has both the UUID and the reference ID, they can link them (low risk in practice since UUIDs are internal)
  - More complex than hard deletion — requires field-by-field anonymization across multiple entities

### Option 3: Soft Deletion with Encrypted Tombstone

Mark the customer as deleted and encrypt all PII fields with a key that is destroyed after the financial retention period expires. During retention, auditors can request key recovery to access the original data.

- **Pros:**
  - Full data recovery is possible during the retention period
  - After key destruction, data is effectively deleted
  - Satisfies both deletion and retention requirements in theory

- **Cons:**
  - Key management adds significant complexity — secure storage, rotation, per-customer keys
  - "Encryption at rest" is already provided by AWS S3 and PostgreSQL — application-level encryption is redundant infrastructure
  - Key recovery for auditors requires a separate workflow
  - POPIA Section 24 requires deletion "as soon as reasonably possible" — keeping encrypted PII for 5 years may not satisfy this requirement
  - No existing encryption infrastructure in the codebase to build on

## Decision

**Option 2 — Anonymization with financial record preservation.**

## Rationale

The core tension is between POPIA's right to erasure and SA tax law's record retention requirements. Anonymization resolves this by transforming personal information into non-identifying reference data while keeping the financial record chain intact.

POPIA Section 24(1) requires that a responsible party "destroys or deletes a record of personal information [...] that is no longer required for the purpose for which it was collected." The key phrase is "no longer required" — financial records ARE still required by law. Anonymization satisfies the spirit of deletion by removing all identifying information while retaining the legally-required financial data in a form that cannot be linked back to the individual without additional information (which has been destroyed).

The `ANONYMIZED` lifecycle status is critical. The existing `OFFBOARDED` status allows re-activation (`OFFBOARDED -> ACTIVE` is a valid transition). An anonymized customer must never be re-activated — there is no personal information to restore. Making `ANONYMIZED` a terminal state with no outgoing transitions enforces this at the domain model level.

The pre-anonymization export (see [ADR-196](ADR-196-pre-anonymization-export-storage.md)) provides both a compliance record ("we had this data and we anonymized it on this date") and a last-resort recovery path. This is stored in S3 with the same retention period as financial records.

## Consequences

- **Positive:**
  - Compliant with both POPIA Section 24 and SA tax law (Income Tax Act Section 29, VAT Act Section 55)
  - Audit trail remains complete — regulators can verify the firm's compliance history
  - Reference ID enables financial record auditing without re-identification
  - Terminal `ANONYMIZED` status prevents accidental data exposure through re-activation
  - Pattern is extensible to other jurisdictions (GDPR Article 17 has the same tension with financial record retention)

- **Negative:**
  - Customer record persists in the database (anonymized) — increases storage marginally
  - More implementation complexity than hard deletion — each PII field must be explicitly handled
  - The `notes` and `customFields` clearing is new scope beyond the existing `anonymize()` method

- **Neutral:**
  - The approach aligns with the existing `Customer.anonymize()` method — Phase 50 extends it rather than replacing it
  - Other B2B SaaS platforms in regulated industries (accounting, legal) use the same anonymization pattern
