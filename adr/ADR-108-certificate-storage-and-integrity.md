# ADR-108: Certificate Storage and Integrity

**Status**: Accepted

**Context**:

When a document is accepted, the system generates a Certificate of Acceptance PDF as proof. This one-page certificate contains the acceptor's name, timestamp, IP address, user agent, and a SHA-256 hash of the original document PDF. Firms file this certificate alongside the engagement letter for compliance and audit purposes.

The certificate must be stored, retrievable, and downloadable. The original document's integrity must be verifiable — the certificate includes a hash of the original, which means the original PDF must remain immutable after generation. The question is where and how to store the certificate and what its relationship to the original document should be.

The platform has two relevant storage patterns: (1) S3 objects referenced by entity fields (e.g., `OrgSettings.logoS3Key`, document uploads), and (2) the `GeneratedDocument` entity which tracks template-generated PDFs with metadata like `templateId`, `primaryEntityType`, `s3Key`, and `contextSnapshot`.

**Options Considered**:

1. **Separate S3 object linked via AcceptanceRequest (chosen)** -- The certificate is stored as its own S3 object with a key pattern like `{tenantSchema}/certificates/{requestId}/certificate.pdf`. The `AcceptanceRequest` entity stores `certificateS3Key` and `certificateFileName` directly.
   - Pros:
     - Simple and direct: the certificate is an artifact of the acceptance request, stored on the acceptance request. One entity, one S3 object, one clear relationship.
     - Original document is completely untouched. Its S3 object, hash, and metadata remain exactly as generated.
     - Certificate lifecycle is tied to the acceptance request — if a request is archived or cleaned up, the certificate follows naturally.
     - Clear S3 key pattern: `{tenantSchema}/certificates/{requestId}/certificate.pdf` is unambiguous and easy to locate.
     - Certificate download endpoint lives on `AcceptanceController`, keeping the acceptance domain self-contained.
     - Easy to delete or archive independently of other document infrastructure.
   - Cons:
     - Two S3 objects to manage per accepted document (the original PDF + the certificate). S3 lifecycle and cleanup must account for both.
     - No unified "document list" view that includes certificates alongside generated documents. Certificates are accessed through the acceptance request, not the document management system.
     - Certificate metadata (S3 key, filename) is stored on `AcceptanceRequest` rather than in a dedicated document tracking entity.

2. **Store as a GeneratedDocument entry** -- Create a new `GeneratedDocument` record with a special type (e.g., `CERTIFICATE`) linked to the original document. Reuses the existing document tracking infrastructure.
   - Pros:
     - Reuses `GeneratedDocument` listing and download infrastructure. Certificates appear in document lists alongside other generated documents.
     - Consistent storage model: all PDFs tracked by the same entity type.
     - Document download endpoints work for both generated documents and certificates.
   - Cons:
     - Conceptual mismatch: `GeneratedDocument` is designed for user-triggered, template-based document generation. It has fields like `templateId`, `generatedBy`, and `contextSnapshot` that do not apply to system-generated certificates. `templateId` would need to be nullable or reference a fake "certificate template."
     - Pollutes `GeneratedDocument` queries: every document list query must filter out certificates (or include them with different display treatment). Existing code that queries `GeneratedDocument` by `primaryEntityType` would return certificates mixed in with engagement letters.
     - The certificate is semantically proof metadata about an acceptance event, not a generated business document. Treating it as one creates confusion about what `GeneratedDocument` represents.
     - Requires modifying the existing `GeneratedDocument` entity (adding a type/category discriminator), which risks regressions in document generation and listing code.

3. **Append certificate as a page to the original PDF** -- Modify the original PDF in S3 to add the certificate as a final page, creating a single combined document.
   - Pros:
     - Single file: the engagement letter and its acceptance proof are one document. Simple to download, email, or archive.
     - No additional S3 objects to manage.
     - The combined document is self-contained — no cross-referencing needed.
   - Cons:
     - Mutates the original document. This directly invalidates the SHA-256 hash embedded in the certificate — the certificate references the hash of the original, but the original no longer has that hash because it has been modified. This is a fundamental contradiction.
     - Breaks the immutability guarantee of generated documents. Other systems that reference the original document's S3 key (audit events, `GeneratedDocument` metadata, portal read-model) now point to a different file than what was originally generated.
     - Complicates the acceptance flow: must download the original from S3, append the certificate page using a PDF library, re-upload the modified file, and update all references. This is error-prone and slow.
     - Cannot separately access the original document without the certificate or the certificate without the original.
     - If the request is revoked after acceptance, the certificate page is permanently embedded in the document — there is no clean way to "un-append" it.

**Decision**: Option 1 -- Separate S3 object linked via AcceptanceRequest.

**Rationale**:

The certificate is conceptually a companion artifact to the acceptance request, not a variant of the original document. Storing it on `AcceptanceRequest` keeps the relationship clear: one acceptance leads to one certificate. The certificate references the original document via a SHA-256 hash, which means the original must remain byte-for-byte identical after generation — Option 3 is disqualified by this constraint alone, as appending to the PDF changes its hash.

Option 2 is technically workable but creates a conceptual mismatch. `GeneratedDocument` tracks user-initiated, template-based document generation with fields like `templateId` and `contextSnapshot` that have no meaning for a system-generated certificate. Forcing certificates into this model requires nullable fields, type discriminators, and query filters — all signs that the abstraction does not fit. The certificate is not a "generated document" in the domain sense; it is a cryptographic proof artifact attached to an acceptance event.

Storing the certificate as a pair of fields on `AcceptanceRequest` (`certificateS3Key`, `certificateFileName`) is the simplest model. The acceptance controller provides a download endpoint that streams or redirects to the certificate. No new entities, no changes to existing entities, no query filter changes.

**Consequences**:

- Positive:
  - `AcceptanceRequest.certificateS3Key` and `AcceptanceRequest.certificateFileName` store the certificate location directly. No join or secondary lookup needed.
  - Original document PDF is never modified after generation. Its SHA-256 hash is stable and can be independently verified by downloading the original and recomputing.
  - Certificate download endpoint is on `AcceptanceController` (`GET /api/acceptance-requests/{id}/certificate`), keeping the acceptance domain self-contained.
  - No changes to `GeneratedDocument` entity, queries, or listing UI. Existing document management code is unaffected.

- Negative:
  - Two S3 objects per accepted document. S3 cleanup/lifecycle policies must be aware of both the `generated-documents/` and `certificates/` key prefixes.
  - Certificates are not visible in the generated documents list. Firms access certificates through the acceptance request detail, not through document management. If a future requirement is to list all PDFs (generated documents + certificates) in one view, additional queries are needed.

- Neutral:
  - Certificate rendering uses `PdfRenderingService` (same `OpenHTMLToPDF` pipeline as document generation) with a dedicated Thymeleaf template at `classpath:templates/certificates/certificate-of-acceptance.html`. This template is system-managed, not user-editable.
  - S3 key pattern: `{tenantSchema}/certificates/{requestId}/certificate.pdf`. This keeps certificates in a separate key namespace from generated documents (`{tenantSchema}/generated-documents/...`).
  - The SHA-256 hash of the original document is computed at acceptance time by downloading the original from S3 and hashing its bytes. This adds a small latency to the accept flow (typically <100ms for a multi-page PDF).
  - S3 lifecycle/cleanup: certificates follow the same retention policy as the tenant's other documents. No special lifecycle rules needed.

- Related: [ADR-107](ADR-107-acceptance-token-strategy.md) (acceptance token authentication), [ADR-109](ADR-109-portal-read-model-sync-granularity.md) (portal read-model sync), [ADR-056](ADR-056-pdf-engine.md) (OpenHTMLToPDF rendering engine), [ADR-057](ADR-057-template-storage.md) (template storage in database).
