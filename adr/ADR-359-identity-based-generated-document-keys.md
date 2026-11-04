# ADR-359: Identity-based storage keys for generated documents

**Status**: Accepted

**Context**:

This ADR records a decision shipped in PR #1717 (merge `3501fa8998408470d5ceb93b6ce714bd7c9e51a6`, 2026-11-04) before Phase 89 started; it is written here so the decision is discoverable. Before the PR every generated document's S3 key was derived from names and dates: `StatementService.java:130-133` built `"org/" + tenantId + "/generated/" + buildFileName(slug, project, periodEnd)` where `:384-388` produced `{slug}-{matterSlug}-{periodEnd}.pdf`; `GeneratedDocumentService.java:245-246` and `PdfRenderingService.java:329-334` produced `{slug}-{entityName}-{LocalDate.now()}.pdf` for every Tiptap template, closure letter and fee-note PDF. Two documents with the same name therefore shared one object and the later upload overwrote the earlier bytes while both `generated_documents` rows (and the `documents` row of a shared one) kept pointing at it. The Phase 88 re-walk observed the worst case: a portal-shared Statement of Account and an internal closure SoA for the same matter and period end collided, and the client's portal Download served the internal document (G88-F33, HIGH). Generic templates collided on entity + generation day.

The fix had to keep the human-readable file name (attorneys and clients save PDFs under the name they see), keep legacy rows downloadable without a data migration, and stay inside the single presign whitelist that `S3StorageAdapter` and the in-memory test double share.

**Options Considered**:

1. **Keep name + date keys but add a disambiguating counter or timestamp suffix (`-2`, `-HHmmss`).**
   - Pros:
     - Smallest visible change; the key still "reads".
   - Cons:
     - A counter needs a read-before-write against S3 (list + pick next) that is racy under concurrent generation, and still collides across tenants' clock skew with a timestamp suffix.
     - The key would still encode the matter name, which is PII in an object path and changes when a matter is renamed.
2. **Content hash (SHA-256 of the PDF bytes) as the key segment.**
   - Pros:
     - Natural deduplication: byte-identical regenerations share one object.
   - Cons:
     - Deduplication is exactly the wrong property here: two rows must never share bytes they can independently delete (`RetentionService.java:384`, `DataAnonymizationService.java:147,269` purge by stored key). A hash key would make the legacy collided-row hazard permanent by design.
     - The key is unknown until rendering finishes, so a DOCX and its PDF sibling cannot be addressed together.
3. **Database identity as the key segment (`GeneratedDocument.id` or `Document.id`).**
   - Pros:
     - One-to-one with the row; readable in logs.
   - Cons:
     - The `GeneratedDocument` row is created after the upload (`generateDocument` uploads, then saves), so the id would have to be pre-allocated and threaded through `PdfRenderingService`; a shared SoA has two rows (`generated_documents` and `documents`) that by design point at one key, so neither id is "the" identity.
     - The bank-statement import (`TrustReconciliationService`) has no `GeneratedDocument` row at all.
4. **Fresh random UUID per upload as a path segment between the family and the file name; the file name stays the last segment (CHOSEN).**
   - Pros:
     - One builder, `S3PresignedUrlService.buildGeneratedKey(tenantId, token, fileName)` (`S3PresignedUrlService.java:36-38`) → `org/{tenant}/generated/{uuid}/{fileName}`; no read-before-write, no ordering dependency on row creation, same shape for a DOCX and its PDF sibling (`GeneratedDocumentService.java:633-657` shares one `storageToken`).
     - The browser's default save name is unchanged because there is no `Content-Disposition` on this path (`GeneratedDocumentController.java:53-64` redirects to a presigned URL; the last segment is the stored `GeneratedDocument.fileName`).
     - Legacy flat keys keep presigning: `StorageKeyPolicy.java:17-19` accepts both `generated/{fileName}` and `generated/{uuid}/{fileName}`; every reader resolves from the key stored on the row, so no backfill is needed.
   - Cons:
     - Objects are never overwritten, so storage grows with every generation (no dedup); regeneration of a document leaves the previous object behind until the row that owns it is purged.
     - The key no longer sorts or groups by matter in the bucket listing; operators must go through the row, not the bucket.

**Decision**: Option 4 — every generated-document upload (Tiptap templates, closure letter, fee-note PDF, SoA, DOCX + PDF sibling, PAIA manual, trust bank-statement import under `trust-statements/{schema}/{accountId}/{uuid}/{fileName}`) takes a fresh `UUID.randomUUID()` path segment built only by `S3PresignedUrlService.buildGeneratedKey`; the user-visible file name is unchanged and stays the last segment; `integration/storage/StorageKeyPolicy` is the single presign whitelist for the S3 adapter and `InMemoryStorageService`; legacy flat keys stay valid; no backfill and no data remediation.

**Rationale**:

1. **Identity, not description, is the invariant the storage layer needs.** The collision class is "two rows, one object"; the only property that closes it is a key that is unique per upload regardless of what the document is called or when it was made. A random UUID gives that without coupling the storage layer to row creation order, and the Phase 88 "next free" discipline applies here too: nothing is reserved, nothing is derived.
2. **Schema-per-tenant and the portal read model constrain remediation.** The overwritten bytes cannot be recovered, and a global migration must not read tenant schemas (BASELINE.md A8). The remedy for legacy collided rows is "regenerate", documented, not automated; the PR body carries a read-only `\gexec` report over every tenant schema's `documents.s3_key`, `generated_documents.s3_key`, `generated_documents.docx_s3_key`, `bank_statements.file_key` and `portal.portal_documents` with `HAVING count(*) > 1` (not run, not in the repo; do not join `documents` to `generated_documents` on `s3_key` — they share a key by design).
3. **Phase 88 lesson — tests on the real write path, observation on the real stack.** `GeneratedDocumentStorageKeyIntegrationTest` covers both collision shapes (SoA same matter + period end; SoA two matters same name; generic same entity same day; generic two matters same name); `GeneratedDocumentServiceDocxStorageKeyTest` proves DOCX + PDF share one token and two generations take two tokens (mutation-checked); `StorageKeyValidationTest` runs 46 cases against both storage implementations including legacy and malformed shapes; `TrustReconciliationControllerTest` proves two imports of the same file name keep both files and that a parse failure stores no file (upload now happens only after a successful parse, CodeRabbit fix `01f61bdb`). Browser + LocalStack observation: two SoAs for the same period end produced two objects with different SHA-256, and the portal projection held only the PORTAL key. Full verify on `01f61bdb`: 6360/0/0/26.

**Consequences**:

- Positive:
  - A shared SoA and an internal closure SoA on the same matter and period end are distinct objects; the portal Download serves the shared one (G88-F33 closed).
  - Phase 89's payment receipt ([ADR-364](ADR-364-payment-receipt-as-generated-document.md)) gets a unique key per receipt for free — one `GeneratedDocument` per payment event with no naming scheme to design.
  - The presign whitelist is one regex in one class; the duplicated regex in the in-memory double is gone.
- Negative:
  - Legacy collided rows remain shared: a retention or anonymisation purge of one row deletes bytes another legacy row still points to (follow-up; no live firms, so no remediation shipped).
  - `DisbursementService.java:480` writes `disbursement-receipts/{tenant}/{id}/{uuid}-{name}`, which does not match `StorageKeyPolicy`; if the receipt download ever presigns through `StorageService` it would throw (hypothesis, not run — needs an integration test before a fix, per the audit-hypothesis rule).
  - `DemoCleanupService.java:250` lists `org/{externalOrgId}/` while generated/export keys use the tenant schema — demo cleanup probably leaves generated documents behind (follow-up).
  - Dead `StatementResponse.pdfUrl` (`StatementService.java:264,329,436`) points at a non-existent `/api/documents/{id}/pdf` (follow-up).
- Follow-ups surfaced while walking (now in Phase 89): the SoA dialog defaults the next "Period start" after the default period end (R-E3d); `BankStatementParseException` → HTTP 500 (R-E1d).
- Explicitly not done: no `Content-Disposition` header (the file name is the key tail), no backfill of legacy keys, no deduplication, no storage-growth policy.

**Related**: [ADR-350](ADR-350-client-emails-explicit-intent.md) (generated documents are INTERNAL until shared — the shared/internal pair that collided), [ADR-353](ADR-353-firm-letterhead-settings.md) (the rendering pipeline that writes these keys), [ADR-356](ADR-356-pack-content-reconciliation.md) (SoA template reconciled in R-E3a renders through the same path), [ADR-364](ADR-364-payment-receipt-as-generated-document.md); `architecture/phase89-go-live-readiness.md` §14.3, §14.6, §14.11; `qa_cycle/evidence/phase88-rewalk/full/gaps.md` G88-F33.
