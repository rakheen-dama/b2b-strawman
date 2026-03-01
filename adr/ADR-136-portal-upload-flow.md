# ADR-136: Portal Upload Flow — Presigned S3 URLs

**Status**: Accepted

**Context**:

Phase 34 enables portal contacts (clients) to upload files as responses to information request items. The platform already has a file upload infrastructure: the `Document` entity with a two-phase upload flow (create PENDING document, get presigned S3 URL, client uploads directly to S3, confirm upload to transition to UPLOADED status). This pattern is used throughout the firm-side application.

The question is whether the portal should reuse the same presigned URL approach (client uploads directly to S3) or route uploads through the portal backend as a proxy. The portal is a separate Next.js application that authenticates via portal JWTs, and the backend generates presigned URLs via the `DocumentService` / S3 integration layer.

**Options Considered**:

1. **Presigned URL (client uploads directly to S3)** -- Portal backend creates a Document entity and generates a presigned PUT URL. The client's browser uploads directly to S3. The portal then confirms the upload.
   - Pros:
     - Consistent with the existing upload pattern used throughout the platform
     - No additional load on the backend server during upload — S3 handles the data transfer
     - Scales naturally — large files don't consume backend memory or connection pool resources
     - Upload progress tracking is possible in the browser (XHR progress events against S3)
     - Presigned URLs have built-in expiry for security
   - Cons:
     - Requires S3 CORS configuration to allow uploads from the portal domain
     - Client browser must be able to reach the S3 endpoint directly
     - Slightly more complex client-side code (two-step: get URL, then PUT to S3, then confirm)

2. **Portal backend proxy** -- Client uploads the file to a portal backend endpoint, which streams it to S3 on behalf of the client.
   - Pros:
     - Simpler client-side code (single POST with file body)
     - No CORS configuration needed for S3
     - Backend can validate file before storing (MIME type, size)
   - Cons:
     - Backend becomes a bottleneck for large file uploads — memory and connection pool pressure
     - Doubles bandwidth: client -> backend -> S3 (vs. client -> S3 directly)
     - Inconsistent with existing upload pattern — creates a second upload pathway to maintain
     - Harder to show upload progress to the client (progress only to backend, not end-to-end)
     - Requires larger request body limits on the backend

3. **Chunked upload via portal backend** -- Client splits file into chunks, uploads each chunk to the portal backend, backend assembles and streams to S3 using multipart upload.
   - Pros:
     - Handles very large files (multi-GB)
     - Resumable uploads possible
     - Backend can validate chunks incrementally
   - Cons:
     - Significantly more complex implementation (chunking protocol, assembly, cleanup)
     - Over-engineered for the typical document sizes in professional services (PDFs, spreadsheets — rarely > 50MB)
     - Still doubles bandwidth per chunk
     - No existing infrastructure to build on — entirely new upload pathway
     - S3 multipart upload API adds complexity

**Decision**: Option 1 -- Presigned URL.

**Rationale**:

The presigned URL approach is the clear winner because it is consistent with the existing upload infrastructure. The `DocumentService` already generates presigned URLs and the two-phase upload flow (PENDING -> UPLOADED) is a proven pattern used by firm-side uploads. Reusing this pattern for portal uploads means no new upload infrastructure, no new failure modes, and the same S3 integration code path.

The performance characteristics are superior: file data flows directly from the client's browser to S3 without touching the application server. For a system that will handle document uploads from potentially many clients simultaneously, this is important. A backend proxy would create a scaling bottleneck — each concurrent upload ties up a connection and consumes memory proportional to the file size.

The CORS configuration is a one-time setup (already required for firm-side uploads from the browser) and presigned URL expiry provides built-in security. The slightly more complex client-side flow (get URL, upload, confirm) is a well-understood pattern with existing reference implementations in the firm-side frontend code.

Related: [ADR-018](ADR-018-document-scope-model.md) (document scope model).

**Consequences**:

- Portal upload follows the same three-step flow: (1) `POST /portal/api/requests/{id}/items/{itemId}/upload` creates Document + returns presigned URL, (2) client PUTs file to S3, (3) `POST .../submit` confirms upload
- Documents created via portal have `visibility = SHARED` and `scope = PROJECT` or `CUSTOMER` depending on request scoping
- S3 CORS configuration must allow the portal domain (already configured for firm-side uploads)
- `DocumentService.createDocumentAndPresignedUrl()` is reused — no new upload infrastructure
- Portal frontend needs a file upload component that handles the three-step flow (can reference firm-side upload implementation)
