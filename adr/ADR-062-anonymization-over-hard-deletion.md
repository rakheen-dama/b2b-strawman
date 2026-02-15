# ADR-062: Anonymization Over Hard Deletion for Data Subject Requests

**Status**: Accepted

**Context**: When a data subject (customer) requests deletion of their personal information under data protection regulations (GDPR Article 17, POPIA Section 11), the platform must decide how to handle the request while balancing competing legal obligations. Financial records (invoices, time entries, billing rates) are subject to legal retention requirements — FICA Section 22 mandates 5-year retention of customer due diligence records, GDPR Article 17(3)(b) allows retention for establishment/exercise/defence of legal claims, and tax law in most jurisdictions requires multi-year retention of invoices and billing records.

The core tension: data protection law grants individuals the right to erasure, but accounting law prohibits deletion of financial records. The question is whether to hard-delete all customer records (breaking financial record integrity), anonymize PII while retaining record structure, soft-delete with PII stripping, or use encryption-based techniques to "forget" the data.

The decision affects audit trail integrity (can an auditor trace invoices to customers?), accounting system compliance (do ledgers balance after deletion?), and future customer re-onboarding (what if a deleted customer returns?).

**Options Considered**:

1. **Anonymization (replace PII with placeholder values, keep record structure)** — When a DELETION request is executed, customer PII fields are replaced with generic placeholders or nulled out, but the customer record and all linked financial records (invoices, time entries, billing rates) are retained with their amounts, dates, and structural relationships intact. Customer name becomes "Anonymized Customer [hash]", email/phone/ID number become null, custom fields are cleared, documents are deleted from S3, but invoice/time entry records remain with their financial values. The customer is marked `OFFBOARDED` and `offboarded_at` is set.
   - Pros:
     - Satisfies data protection obligations: PII is removed, making re-identification infeasible (GDPR Article 17 right to erasure is fulfilled when data can no longer identify the subject).
     - Satisfies financial retention obligations: invoice records, amounts, dates, and audit trails remain intact for tax and regulatory compliance.
     - Accounting integrity preserved: ledgers balance, invoices total correctly, time entry summaries match billing records.
     - Simple to implement: update PII columns to null or placeholders, execute S3 deletions. No cascade constraints to manage.
     - Reversible if needed: if an anonymization is mistakenly executed, the original data is gone but the record structure allows manual reconstruction from offline backups (if available) without breaking FK relationships.
     - Consistent with "soft deletion" philosophy used elsewhere in the platform (customers have `status = ARCHIVED`, not hard-deleted).
     - Clear audit trail: `DATA_DELETION_EXECUTED` event records what was anonymized. The customer record remains as evidence of the action.
   - Cons:
     - "Zombie records": anonymized customers appear in the system as "Anonymized Customer a1b2c3" in listings, reports, and historical invoices. This can clutter the UI if not filtered properly.
     - The anonymized customer name is not human-readable — auditors/staff can't immediately identify which customer a historical invoice belonged to without cross-referencing audit logs.
     - If a customer is re-onboarded after deletion, a new customer record must be created — the anonymized record cannot be "reactivated."
     - Some jurisdictions may interpret "deletion" more strictly (requiring full removal of records), though the consensus interpretation of GDPR/POPIA is that anonymization satisfies the right to erasure.

2. **Hard deletion (DELETE FROM for all customer-linked records)** — Execute `DELETE FROM` statements cascading through `customers`, `customer_projects`, `documents`, `invoices`, `invoice_lines`, `time_entries`, `comments`, `portal_contacts`, and any other customer-scoped data. The customer and all related records are permanently removed from the database.
   - Pros:
     - Strongest interpretation of "deletion" — data is fully gone, no residual traces.
     - No "zombie records" in the UI — the customer simply vanishes from all views.
     - Simplest mental model: deletion means deletion.
   - Cons:
     - **Violates financial record retention laws.** FICA Section 22 requires 5-year retention of customer due diligence. Tax law requires invoice retention. Deleting invoices, time entries, and billing records creates legal exposure.
     - Breaks accounting integrity: ledgers no longer balance if invoice records are deleted. Revenue totals are incorrect. Profitability reports are incomplete.
     - Audit trail is destroyed: no way to prove what the customer was billed, when, or why. If an ex-customer later disputes a payment, the org has no records to defend itself.
     - Cascade deletion is complex and error-prone: deleting a customer requires careful ordering of FK deletions (comments → documents → invoices → time entries → customer) and risks orphaning records if the cascade is incomplete.
     - Irreversible without offline backups: if a deletion is executed in error, the data is gone forever.
     - Inconsistent with platform patterns: the platform uses soft deletion (`status = ARCHIVED`) for projects and customers in normal operations. Hard deletion introduces a new, dangerous pattern.

3. **Soft deletion with PII stripping (mark records as deleted, strip PII fields, keep skeleton records)** — Add a `deleted` boolean to the customer table. When a DELETION request is executed, set `deleted = true`, null out PII fields (name, email, phone, ID), clear custom fields, and delete documents from S3. The customer record remains but is filtered from all queries via a `WHERE deleted = false` clause (or Hibernate `@Where` annotation). Invoice/time entry records remain linked via FKs but the customer is "soft deleted."
   - Pros:
     - PII is removed (satisfies data protection obligations).
     - Financial records are retained (satisfies retention obligations).
     - Soft deletion is a familiar pattern in the platform.
     - Reversible: setting `deleted = false` can "un-delete" the customer (though PII cannot be restored unless backed up separately).
   - Cons:
     - Filtering complexity: every query that touches customers must include `WHERE deleted = false`. Hibernate `@Where` filters can enforce this, but it's fragile — a single query that forgets the filter leaks deleted customers.
     - Ambiguous semantics: is a soft-deleted customer "deleted" or "archived"? How does `deleted = true` differ from `status = ARCHIVED`? The platform already has a soft-delete mechanism (`status`). Adding a second boolean creates confusion.
     - Listing bloat: soft-deleted customers remain in the database, growing the table size over time. Queries must always filter them out, adding overhead.
     - FK relationship ambiguity: invoices/time entries link to a "deleted" customer. What does that mean? Is the link valid? Should the customer name display as "Deleted Customer" or "Anonymized Customer"? The semantics are unclear.
     - Not significantly simpler than anonymization: both approaches null out PII and retain structure. Soft deletion adds a boolean flag but doesn't meaningfully reduce complexity.

4. **Encryption-based deletion (encrypt PII with per-customer key, destroy key to "delete")** — Store PII fields (name, email, phone, ID) encrypted with a per-customer encryption key. When a DELETION request is executed, destroy the encryption key. The encrypted data remains in the database but is unrecoverable without the key, effectively "deleting" it. Financial records remain intact and linked to the customer record.
   - Pros:
     - PII is cryptographically unrecoverable after key destruction — strongest technical guarantee of deletion.
     - Financial records and structure remain intact.
     - No "zombie records" with placeholder names — the encrypted values remain in the DB but are gibberish.
   - Cons:
     - Over-engineered for this use case: anonymization achieves the same legal outcome (PII is removed) with far less complexity.
     - Key management infrastructure is a major undertaking: secure key storage (HSM or KMS), key rotation, access controls, audit logging. This is a large engineering investment for a marginal benefit.
     - Performance overhead: every read/write of customer PII requires encryption/decryption. This adds latency and CPU cost.
     - Backup/restore complexity: encrypted data cannot be restored without the key. If keys are backed up, the backup system becomes a new attack surface. If keys are not backed up, accidental key loss is catastrophic.
     - Regulatory uncertainty: some jurisdictions may not recognize encrypted-data-without-key as "deletion" — the data still exists, just inaccessible. Anonymization is a clearer interpretation.
     - The platform has no other per-record encryption use case — building this infrastructure for data deletion alone is disproportionate.

**Decision**: Anonymization (Option 1).

**Rationale**: Anonymization satisfies both data protection obligations (PII is removed, re-identification is infeasible) and financial record retention obligations (invoice amounts, dates, and audit trails are preserved). The legal consensus under GDPR Article 17 and POPIA Section 11 is that anonymization — making data no longer identifiable — fulfills the right to erasure. Financial records must be retained for 5+ years in most jurisdictions, and hard deletion would create legal exposure under FICA Section 22 and tax law.

The anonymization approach is simple to implement, preserves accounting integrity, and maintains audit trail continuity. "Zombie record" clutter can be mitigated with UI filtering (e.g., exclude `lifecycle_status = OFFBOARDED` from default customer lists). The anonymized name format ("Anonymized Customer a1b2c3" with a 6-character hash) provides a stable identifier for cross-referencing without exposing PII.

Soft deletion with PII stripping (Option 3) was rejected because it adds a redundant `deleted` flag alongside the existing `status = ARCHIVED` mechanism. The semantics of "soft deleted but not archived" or "archived but not deleted" are confusing. Anonymization is clearer: the customer is `OFFBOARDED`, their PII is removed, and they remain in the system only as a structural record.

Encryption-based deletion (Option 4) was rejected as over-engineered. The platform has no other per-record encryption requirement, and the legal benefit of cryptographic irreversibility over anonymization is marginal. The engineering cost (key management, performance overhead, backup complexity) is not justified for a compliance feature that anonymization handles effectively.

Hard deletion (Option 2) was rejected outright because it violates financial record retention laws and destroys audit trail integrity. The platform is a professional services billing system — deleting invoices and time entries is non-negotiable.

**Consequences**:
- When a `DELETION` data subject request is executed, the `DataAnonymizationService` performs the following transformations:
  - Customer `name` → `"Anonymized Customer " + customerIdHash.substring(0, 6)` (6-char hash of customer ID for stable identifier).
  - Customer `email`, `phone`, `notes` → `null`.
  - Customer `custom_fields` → `{}` (empty JSONB map).
  - Documents with `scope = CUSTOMER` and `customer_id = <this customer>` → S3 objects deleted, Document records removed from DB.
  - Comments with `visibility = SHARED` authored by linked portal contacts → `content` replaced with `"[Removed]"`.
  - Portal contacts linked to this customer → `email` set to `null`, `name` set to `"Removed Contact"`.
  - Customer `lifecycle_status` set to `OFFBOARDED`, `offboarded_at` set to current timestamp.
  - **Retained unchanged**: Invoice records, invoice lines, time entries, billing rates, cost rates, project links (`customer_projects`), internal comments (`visibility = INTERNAL`).
- An `AuditEvent` with type `DATA_DELETION_EXECUTED` is recorded, capturing counts of affected records by type (e.g., `{documentsDeleted: 5, commentsAnonymized: 3, portalContactsRemoved: 2}`).
- The anonymization operation is **irreversible**. The API endpoint requires confirmation: the request body must include `{ confirmCustomerName: "<exact customer name>" }`, validated against the customer's current name before proceeding. The frontend requires the admin to type the customer's name to unlock the "Execute Deletion" button.
- Anonymized customers (`lifecycle_status = OFFBOARDED`) are excluded from default customer list views but can be included via a "Show Offboarded" filter toggle. Historical invoices and time entry reports show the anonymized name.
- If an anonymized customer needs to be re-onboarded (e.g., they return as a new client), a **new customer record** must be created. The anonymized record cannot be reactivated — its PII is gone.
- The retention policy system (ADR-064) can flag anonymized customers for purge after the configured retention period. At that point, an admin can hard-delete the anonymized customer record and all linked financial records (after confirming retention period compliance).
