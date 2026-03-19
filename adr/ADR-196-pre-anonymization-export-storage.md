# ADR-196: Pre-Anonymization Export Storage

**Status**: Accepted
**Date**: 2026-03-19
**Phase**: 50 (Data Protection Compliance)

## Context

When a customer's personal information is anonymized ([ADR-193](ADR-193-anonymization-vs-deletion.md)), the operation is irreversible by design — there is no "undo" button. Before executing anonymization, Phase 50 automatically generates a complete data export (the same ZIP produced by `DataExportService`) as a safety measure and compliance record.

This pre-anonymization export serves two purposes:
1. **Recovery path** — if the anonymization was triggered erroneously (e.g., wrong customer, miscommunication), the export is the only way to recover the original data. Re-entering data from the export would be manual, but possible.
2. **Compliance proof** — the export demonstrates that the firm had the data, received the deletion request, and fulfilled it. Regulators or auditors may request evidence that the firm actually processed the request.

The question is how long to retain this export and where to store it.

## Options Considered

### Option 1: Retain for Fixed Period (30 Days)

Store the export in S3 with a 30-day lifecycle policy. After 30 days, the export is automatically deleted.

- **Pros:**
  - Short retention minimizes the risk of the pre-anonymization export itself becoming a data protection liability
  - S3 lifecycle policies handle deletion automatically — no application code needed
  - 30 days is long enough for error correction (the firm would notice a mistake within a month)

- **Cons:**
  - If a regulator requests compliance evidence after 30 days, the proof is gone
  - 30 days may not be enough for audit purposes — financial audits can happen years after the fact
  - The export contains PII, but the customer was already requesting deletion — the retention window contradicts their intent
  - S3 lifecycle policies are global (per bucket prefix), not per-object — would need a dedicated prefix or bucket

### Option 2: Retain for Financial Retention Period (Selected)

Store the export in S3 for the same duration as `financialRetentionMonths` from OrgSettings (default 60 months / 5 years). The export's S3 key is recorded in the anonymization audit event details, not on the Customer entity.

- **Pros:**
  - Retention period matches the financial record retention requirement — if an auditor needs to verify the anonymization 3 years later, the export is available
  - Consistent with the overall retention framework — the same `financialRetentionMonths` value governs both financial records and compliance evidence
  - The export key is stored in the audit event (immutable, append-only) rather than on the customer record — it cannot be tampered with
  - If the tenant later shortens their financial retention period, exports created before the change are unaffected (S3 lifecycle is set at upload time)

- **Cons:**
  - 5 years of retaining an export that contains the very PII the data subject asked to delete
  - Storage cost for 5 years per anonymized customer (though exports are typically < 10MB, so this is negligible)
  - The export itself becomes a data protection asset that needs protection — access must be restricted

### Option 3: Retain Indefinitely (Never Delete)

Store the export permanently in S3 with no lifecycle policy.

- **Pros:**
  - Maximum audit trail — evidence is always available regardless of when a regulator asks
  - No cleanup logic needed — simplest implementation
  - Aligns with the immutability of audit events (which are never deleted)

- **Cons:**
  - Indefinite retention of PII contradicts POPIA Section 14(1) — "personal information must not be retained for longer than necessary"
  - Storage costs grow indefinitely (minor but principled objection)
  - The firm cannot claim they've fully deleted the data subject's PI if a full export exists permanently
  - No regulatory framework requires indefinite retention of compliance evidence

## Decision

**Option 2 — Retain for the financial retention period (`financialRetentionMonths`).**

## Rationale

The pre-anonymization export is a compliance record, not customer data. Its purpose is to prove that the firm: (1) had the data, (2) received a valid request, and (3) fulfilled it. This is analogous to keeping a receipt for a transaction — the receipt proves the transaction happened, even after the underlying relationship ends.

Aligning the export retention with `financialRetentionMonths` is the right choice because:

1. **Financial audits drive the longest retention need.** If SARS audits the firm, they need to verify that invoices and financial records are complete. The pre-anonymization export can demonstrate that a now-anonymized customer had invoices N, M, O — confirming that the remaining anonymized financial records are genuine.

2. **Consistency simplifies compliance.** One retention period (`financialRetentionMonths`) governs both "how long do we keep financial records?" and "how long do we keep proof that we anonymized someone?" The firm doesn't need to track separate retention schedules for different types of evidence.

3. **The export key lives in the audit event, not the customer entity.** This is a deliberate architectural choice. The customer entity is anonymized — attaching an export key to it would create a visible link between "Anonymized Customer REF-a1b2c3" and a ZIP full of their original PII. By storing the key in the audit event details (which are accessible only to owners and logged separately), we maintain the anonymization boundary. The export is retrievable only through the audit trail, not through the customer record.

**Storage implementation:** The export is uploaded to S3 at `org/{tenantId}/compliance-exports/{customerId}/pre-anonymization-{timestamp}.zip`. An S3 lifecycle rule on the `compliance-exports/` prefix deletes objects after `financialRetentionMonths` months. This is a per-bucket policy, not per-object — all compliance exports follow the same lifecycle.

**Access control:** Only OWNER role can access compliance exports. The presigned URL generation for these exports uses the same `StorageService` as other S3 operations but checks for OWNER role explicitly.

## Consequences

- **Positive:**
  - Compliance evidence is available for the full financial retention period
  - Consistent retention framework — one period to configure, one concept to understand
  - Audit event linkage maintains anonymization boundary
  - S3 lifecycle policy automates cleanup — no application-level purge job needed
  - Storage cost is negligible (< 10MB per customer, < 50GB for a large firm with 1000 anonymized customers over 5 years)

- **Negative:**
  - The data subject's PII exists in S3 for up to 5 years after they requested deletion — this is a known trade-off, justified by the legal obligation to retain financial records
  - The export must be protected as a high-sensitivity asset — S3 access controls and encryption at rest are essential (already provided by AWS af-south-1 defaults)
  - If a data subject or regulator specifically requests deletion of the pre-anonymization export, the firm faces a conflict between their audit obligation and the deletion request — this edge case must be handled manually

- **Neutral:**
  - The S3 lifecycle policy on `compliance-exports/` is a one-time infrastructure configuration
  - The audit event details field (`preAnonymizationExportKey`) follows the existing JSONB pattern — no schema change to audit_events
