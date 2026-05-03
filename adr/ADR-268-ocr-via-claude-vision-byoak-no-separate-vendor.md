# ADR-268: OCR via Claude Vision over BYOAK; No Separate OCR Vendor

**Status**: Accepted

**Context**:

The Intake specialist (Phase 70 Section 3) extracts structured fields from documents uploaded to a customer / matter / information-request. Two extraction paths exist by document type: text-layer PDFs (the common case — a CIPC certificate, an LSSA-generated form, a typed bank statement) yield clean text via a server-side library (`pdfbox`) and the LLM extracts from the text. Image-only PDFs and scans (a smartphone photo of a client's ID, a faxed offer-to-purchase) have no text layer; the bytes are pixels.

Pixel-only documents need OCR. Three candidate paths exist: (a) introduce a dedicated OCR vendor (AWS Textract, Tesseract, Google Document AI) with its own SDK, key, billing relationship, and integration test surface; (b) send the rasterised pages or the PDF directly to Claude as a vision input over the tenant's already-provisioned BYOAK key; (c) skip vision in v1 and fail with a "please upload a text PDF" error. Phase 70 ships in 4 weeks; the founder's 2026-04-22 call was explicit that intake-from-scans is the single biggest drudgery moment for SA paralegals (certified ID copies arrive as scans). Option (c) defers the demo's strongest moment.

**Options Considered**:

1. **Dedicated OCR vendor (AWS Textract or Google Document AI).** Add an `OCRProvider` port + an adapter; tenants configure a separate key (or platform absorbs cost on AWS); image-only PDFs route through OCR → text → extraction.
   - Pros:
     - Best-in-class OCR accuracy on dense or low-quality documents (multi-column legal forms, handwritten annotations). Textract's structured-form extraction is genuinely better than vanilla LLM vision on tables.
     - Separation of concerns — OCR is a deterministic transform, extraction is an LLM call, each component testable in isolation.
     - At very high page volumes (tens of thousands per day), Textract is cheaper per page than vision API calls.
   - Cons:
     - New cost-centre. Either platform-paid (breaks the BYOAK invariant — Phase 52 explicitly defers any platform-paid AI to a future phase) or tenant-paid via a second key (now tenants configure two AI providers, doubling support surface).
     - New dependency: SDK, JAR, AWS-region selection, presigned-URL flow to push the document, OCR job polling for async runs (Textract async jobs typically 5-30s for non-trivial PDFs). Doubles the integration footprint.
     - Schema-per-tenant means OCR results either flow back through the platform or get stored per-tenant; either way more plumbing.
     - Quality-vs-volume case is weak at firm-pilot scale — a 10-attorney firm uploads a few dozen intake pages per week, not tens of thousands. The Textract advantage flattens at that scale; the vision API call cost is in the cents per intake.

2. **Claude vision via BYOAK key (CHOSEN).** When `pdfbox` reports `hasTextLayer=false` or `characterCount < threshold`, the Intake specialist sends the PDF (or rasterised JPEG pages, depending on what the Anthropic API accepts at implementation time) as a vision content block on the same BYOAK chat call. Same key, same provider, same billing relationship as Phase 52. Tenant absorbs cost.
   - Pros:
     - Zero new infrastructure. Zero new vendor. Zero new key. The tenant's already-configured Anthropic key is reused.
     - Cost stays in the BYOAK envelope — tenant absorbs the call cost the same way it absorbs every other Phase 52 / Phase 70 call. No platform cost-centre, no fairness questions about who pays.
     - Quality is competitive for the document types that matter to SA intake: certified ID copies (clean printed text on a clear page), offer-to-purchase forms (typed legal templates), trust-deed pages (clean prose), proof of address (clean utility-bill print). These are the 95% case, and Claude vision handles them well in 2026.
     - Architecturally simple — extends the existing `LlmChatProvider.chat()` call to include a vision content block. No new service, no new port, no new test scaffold beyond mocking the vision response in WireMock the same way Phase 52 mocks chat responses.
     - Implementation latency is bounded — the Intake specialist itself is the bounded scope; the vision fallback is a content-block variant on the same call. Ships in the same epic.
   - Cons:
     - Vision API calls are more expensive per call than text. Intake on a scanned multi-page document can cost 2-5× a text-path extraction. At firm-pilot volumes this is noticeable but not a blocker; surfaced in the Phase 70 gap report.
     - Latency on vision calls is higher (~5-10s for a multi-page PDF vs ~1-3s for text). The Intake UX shows a "Reading scanned document…" indicator and surfaces extraction-path in the review-queue entry (`extractionPath: VISION` vs `TEXT`).
     - Quality on dense or noisy scans (faxed forms with smudges, bad photo angles) is weaker than dedicated OCR. Acceptable: the user reviews per-field in the proposed-vs-current diff anyway; misreads are caught at the human-approval step.
     - Locks intake-vision capability to Anthropic. If the tenant configured a different LLM provider (not in scope today, but a future possibility), they'd have no vision path. Acceptable: Phase 52 already locks chat to Anthropic; this is the same lock, not a new one.

3. **Defer vision in v1; only support text-layer PDFs.** Image-only PDFs return an error: "Please upload a PDF with a text layer to use AI extraction."
   - Pros:
     - Simplest possible v1. Lowest risk on accuracy or cost.
     - Forces the question of OCR vendor to a later phase where it can be designed properly.
   - Cons:
     - Removes the demo's strongest moment. SA conveyancing intake is mostly scans (certified ID copies are scans by regulatory definition; clients photograph offers-to-purchase on phones). A "AI intake assistant that doesn't read scans" is half the product.
     - Pushes the user back to manual data entry on exactly the documents the assistant was meant to help with. Drudgery removal not achieved.
     - The demo for the founder's 2026-04-22 priority list has visible gaps.

**Decision**: Option 2 — vision fallback via Claude over the tenant's BYOAK key. No `OCRProvider` port. `pdfbox` extracts text first; below the configurable character threshold, the Intake specialist sends the PDF or rasterised images as a vision content block to the same Anthropic adapter that Phase 52 already uses. The `AiSpecialistInvocation.proposedOutput` records `extractionPath` as `TEXT` or `VISION` for diagnostics.

**Rationale**:

The cost-vs-quality decision is sensitive to scale. At Anthropic's published vision pricing and a typical SA firm's intake volume (low double-digits of pages per week), the vision premium is in the cents-to-low-rand range per intake — well inside the BYOAK envelope and invisible against the value of "paralegal saved 20 minutes." A dedicated OCR vendor (Option 1) would only pay for itself at volumes Kazi's firm pilots will not hit in Phase 70's evaluation window. Building it speculatively conflicts with `backend/CLAUDE.md`'s YAGNI guidance ("Avoid premature abstractions — do not create provider/adapter patterns until there are two concrete implementations").

The BYOAK invariant is decisive. Phase 52 explicitly chose tenant-pays over platform-pays for AI. Reversing that for OCR alone — making OCR a platform cost while LLM stays tenant — would be a quiet violation of the policy with no design rationale to back it up. Vision-via-BYOAK preserves the policy uniformly: every AI call, including OCR-shaped calls, comes out of the tenant's envelope.

The quality cap on dense / noisy documents is a real cost but is mitigated by the human-approval step. Intake never auto-applies — every extracted field appears in a per-field diff for the user to accept, edit, or reject. A misread RSA ID checksum is flagged in the proposal; a transcription error in an address line is corrected by the reviewer. The OCR-quality gap matters most for fully unattended OCR; at the human-reviewed scale Kazi operates, the gap is absorbed by the review step.

The deferral option (Option 3) was rejected as too costly to the demo. The Intake specialist is the most concrete drudgery-removal moment in Phase 70's three; gutting its main path leaves the demo without a strong intake story.

**Consequences**:

- Positive:
  - No new OCR vendor, no new SDK, no new key, no new cost-centre, no new audit / observability surface. Phase 70's surface area shrinks accordingly.
  - The Intake flow has a clean fallback: text-first (fast, cheap), vision-only-when-needed (slower, more expensive). The branching logic is internal to the Intake specialist's two new tools (`ExtractTextFromDocument` returns `hasTextLayer=false` → caller switches to vision content block).
  - The `AiSpecialistInvocation.proposedOutput` records `extractionPath`, so the Phase 70 gap report can quantify how often vision was used and what its quality + cost looked like in real firm runs. This data informs whether a Phase 71+ dedicated OCR vendor is justified.
  - Dev/test scaffolding stays small — vision responses are mocked via WireMock the same way Phase 52 mocks chat responses, and the existing `TestcontainersConfiguration` (embedded Postgres + `InMemoryStorageService`) needs no extension.

- Negative:
  - Vision calls cost more per page than text. A firm running Intake on a high-volume client (e.g. a property attorney with daily scanned ID-and-FICA bundles) will see a noticeable BYOAK cost line. The Phase 70 gap report flags this; Phase 71 may revisit.
  - Quality on dense forms (multi-column tables, handwritten annotations) is weaker than dedicated OCR. The per-field diff catches errors; users may reject more fields per intake than a Textract path would yield. Surfaced as a metric in the gap report.
  - No structured-form extraction (Textract's table-detection equivalent). If Phase 71+ adds tax-return / financial-statement intake, dedicated OCR may become more attractive.

- Neutral:
  - PDF rasterisation (if the Anthropic API at implementation time does not accept native PDF vision input) uses a Java PDF library (likely `pdfbox` already in dependencies for text-layer extraction). 150 DPI JPEGs are the rasterisation target — chosen because vision API token costs are roughly proportional to image-pixel area, and 150 DPI balances readability with cost.
  - Threshold for switching from text to vision (`characterCount < threshold`) is configurable per-tenant via `OrgSettings.intakeVisionThreshold`, defaulting to 200 characters per page average. Surfaced for tuning in the gap report.
  - The Phase 21 `SecretStore` flow is unchanged — the same `"ai:anthropic:api_key"` secret is used for both text and vision calls. No `"ai:vision:..."` secret variant is introduced.

- Related: [ADR-200](ADR-200-llm-chat-provider-interface.md) (Phase 52 `LlmChatProvider` — extended to carry vision content blocks, no new interface), [ADR-201](ADR-201-secret-store-reuse-for-ai-keys.md) (Phase 52 BYOAK key storage — reused unchanged), [ADR-265](ADR-265-specialist-as-prompt-tools-launcher-metadata.md) (the Intake specialist's tool subset includes the `ExtractTextFromDocument` and `ProposeCustomerFieldExtraction` tools), [ADR-270](ADR-270-ai-specialist-invocation-jsonb-output.md) (`extractionPath` lives in `proposedOutput` JSONB).
