# ADR-284: Document Reading via S3 + Vision, No Vector Store

**Status**: Accepted

**Context**:

The FICA verification skill needs to "read" uploaded documents (ID cards, proof of address, company registrations, trust deeds) to verify them against the compliance checklist. Documents are stored in S3 (LocalStack locally) via the Phase 4 document infrastructure. They can be PDFs (digitally generated or scanned), images (JPG, PNG — photographed IDs), or occasionally Word documents.

The skill must extract the document content and include it in the AI prompt alongside the checklist items. The question is how to read documents and whether to use a vector store for semantic retrieval.

**Options Considered**:

1. **S3 fetch + PDF text extraction + Claude vision for images (CHOSEN)** — Fetch documents from S3 using the existing `StorageAdapter`. For PDFs, extract text using Apache PDFBox. If the text layer is thin (< 100 characters — indicates a scanned PDF), fall back to Claude's vision capability by encoding the PDF pages as images. For image files, encode as base64 and send via `completeWithVision()`. Enforce size limits: 10 MB per document, 50 MB total per invocation.
   - Pros:
     - **No new infrastructure.** S3 and the `StorageAdapter` already exist. PDFBox is a well-maintained Java library with no external dependencies. Claude's vision API is part of the same Anthropic Messages API used for text completions — no separate vendor, no separate API key.
     - **Covers all document types.** Digital PDFs (text extraction), scanned PDFs (vision fallback), and images (direct vision) are all handled. The skill gracefully degrades across the spectrum.
     - **Uses the tenant's BYOAK key.** Vision calls go through the same `AnthropicAiProvider` and the same API key. No separate OCR vendor key, no additional integration to manage, no additional cost line item. This aligns with ADR-268 (Phase 70: OCR via Claude vision, BYOAK, no separate vendor).
     - **Size limits prevent abuse.** 10 MB per document and 50 MB per invocation are generous for typical FICA documents (ID scans are 1-5 MB, utility bills are 0.5-2 MB) while preventing denial-of-service via oversized uploads.
     - **Document content stays in the user prompt.** The content is included in the skill's user prompt (not the system prompt), so it is not cached by Anthropic's prompt cache. This is correct — document content is per-invocation and should not leak between invocations.
   - Cons:
     - **Token-heavy.** A multi-page trust deed could consume 10,000+ tokens. At Claude's token pricing, this adds cost. Mitigated: the skill includes a token budget and truncates large documents with a note ("Document truncated — first 50 pages included").
     - **Vision is slower and more expensive.** Vision calls process images alongside text, increasing latency and cost compared to text-only completions. A fully scanned FICA submission (6 documents, all images) could take 15-30 seconds and cost 2-3x a text-only invocation.
     - **PDFBox dependency.** Apache PDFBox adds ~10 MB to the backend JAR. It is a well-maintained library but adds a dependency. Acceptable — it is already a transitive dependency of the document template pipeline (Phase 42).
     - **No semantic search.** The skill includes all documents in the prompt — it cannot "find the ID document" without reading all documents first. For a typical FICA submission (3-6 documents), this is fine. For a customer with 50+ documents, the skill would need document filtering (by type, by upload date) before inclusion.

2. **Vector store + RAG** — Embed all tenant documents in a vector store (pgvector or external). When a skill runs, semantically retrieve the most relevant documents based on the skill's query.
   - Pros:
     - Scalable to large document sets. A customer with 100 documents could have the most relevant 5 retrieved efficiently.
     - Reduces token usage — only relevant documents are included in the prompt.
     - Enables future document search features (semantic search across all firm documents).
   - Cons:
     - **Massive over-engineering for v1.** A typical FICA submission has 3-6 documents. Including all of them in the prompt (with text extraction and vision) is straightforward and complete. RAG adds an embedding pipeline, a vector store, an indexing job, a retrieval service, and a reranking step — weeks of implementation for a problem that does not exist at v1 scale.
     - **New infrastructure dependency.** pgvector requires PostgreSQL extension activation and index management. External vector stores (Pinecone, Weaviate) add operational complexity and cost. Phase 72 explicitly excludes vector databases.
     - **Embedding quality risk.** Embedding a scanned ID card produces low-quality embeddings (OCR noise). The retrieval step might rank a blurry ID scan lower than a clear utility bill, even though the ID is more relevant for FICA verification. Relevance for FICA is structural (document type), not semantic (content similarity).
     - **Breaks the "include all documents" contract.** The FICA skill needs to review ALL uploaded documents against the checklist — not a subset. If the RAG retrieval misses a document, the skill cannot flag it as missing. False negatives (missed documents) are worse than false positives (included irrelevant documents).
     - **Embedding cost.** Every document upload triggers an embedding call, adding latency and cost to the upload flow — even if the document is never used by an AI skill.

3. **External OCR service (Google Document AI, AWS Textract)** — Use a dedicated OCR service for document text extraction instead of Claude's vision.
   - Pros:
     - Purpose-built OCR: higher accuracy for structured documents (IDs, forms, tables).
     - Faster than vision for text extraction — OCR services are optimised for speed.
     - Separates OCR cost from LLM cost — easier to attribute spend.
   - Cons:
     - **Additional vendor dependency.** A separate API key, separate billing, separate failure mode, separate integration. This contradicts the Phase 70 decision (ADR-268) to consolidate OCR under the BYOAK Anthropic key.
     - **Additional cost for the tenant.** The tenant already pays for Anthropic. Adding Google Cloud or AWS OCR is a second cost line item with separate billing, separate budgets.
     - **Integration complexity.** The `IntegrationRegistry` would need a new domain (`OCR`) with its own port, adapter, and no-op fallback. More infrastructure for a capability that Claude vision handles adequately.
     - **Claude vision is good enough for FICA.** FICA documents are relatively simple: ID cards, utility bills, company registration certificates. These are not complex tables or handwritten forms. Claude's vision handles them accurately.
     - **Two-step process adds latency.** OCR → text → prompt is two network calls instead of one (vision = image → prompt). The total latency is higher.

**Decision**: Option 1 — S3 fetch + PDFBox text extraction + Claude vision for scanned documents and images. No vector store, no external OCR.

**Rationale**:

The FICA skill operates on a small, bounded set of documents per customer (typically 3-6). Including all documents in the prompt is both feasible and correct — the skill needs to verify ALL uploaded documents against the checklist, not a semantic subset. RAG's selective retrieval would be a liability, not an advantage, because missing a document means missing a FICA requirement.

Claude's vision capability (ADR-268) covers the OCR use case without a separate vendor. The quality is sufficient for FICA documents (structured government-issued IDs, utility bills, company registration certificates). A dedicated OCR service would add vendor complexity, cost, and integration effort for marginal accuracy improvement on document types that Claude handles well.

PDFBox for text extraction is a proven, zero-dependency approach (already a transitive dependency via Phase 42's document template pipeline). The text-layer detection heuristic (< 100 characters = scanned, fall back to vision) is a pragmatic threshold that avoids unnecessary vision calls for digital PDFs while ensuring scanned PDFs are properly handled.

Size limits (10 MB per document, 50 MB per invocation) are enforced at the skill level, not the upload level. Documents uploaded for other purposes (non-AI) are not restricted. The limits prevent the AI skill from consuming excessive tokens and incurring unexpected costs.

**Consequences**:

- Positive:
  - No new infrastructure: S3, PDFBox, and Claude vision are all available or already in use.
  - BYOAK alignment: all AI costs (text completion and vision) go through the tenant's Anthropic key.
  - Complete document coverage: all documents are read and included in the prompt. No retrieval gaps.
  - Graceful degradation: digital PDF → text extraction (fast, cheap); scanned PDF → vision (slower, more expensive); image → vision (standard).

- Negative:
  - Token-heavy for large documents. A 30-page trust deed consumes significant tokens. Mitigated by document truncation with a note.
  - Vision calls are 2-3x more expensive than text-only. A fully scanned FICA submission costs more than a digital one. This is visible in the per-invocation cost and the tenant's budget.
  - No semantic document search. The skill reads all documents, not "the most relevant." For v1 with 3-6 documents per customer, this is fine. For future skills that operate on larger document sets, RAG or document filtering may be needed.

- Neutral:
  - PDFBox adds no new dependency (already transitive via Phase 42).
  - The `FicaDocumentReader` class encapsulates the S3 fetch + text extraction + vision fallback logic. It is skill-specific (not a shared service) because the reading strategy (all documents, with text-layer detection) is specific to FICA verification. Future skills may need different reading strategies.
  - Size limits are configured in `application.yml` (`kazi.ai.max-document-size-bytes`, `kazi.ai.max-total-document-size-bytes`), not hard-coded.

- Related: [ADR-268](ADR-268-ocr-via-claude-vision-byoak-no-separate-vendor.md) (Phase 70: OCR via Claude vision — same principle), [ADR-280](ADR-280-evolve-ai-provider-port-for-skills.md) (AiProvider with `completeWithVision()` method), [ADR-283](ADR-283-prompt-architecture-firm-profile-cache.md) (prompt architecture — documents go in user prompt, not system prompt), [ADR-282](ADR-282-per-invocation-cost-metering-byoak.md) (cost metering — vision calls are more expensive)
