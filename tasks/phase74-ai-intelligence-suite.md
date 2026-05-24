# Phase 74 — AI Intelligence Suite: Contract Review, Drafting & Compliance Audit

> **Architecture**: [`architecture/phase74-ai-intelligence-suite.md`](../architecture/phase74-ai-intelligence-suite.md)
> **Requirements**: [`requirements/claude-code-prompt-phase74.md`](../requirements/claude-code-prompt-phase74.md)
> **ADRs**: [ADR-288](../adr/ADR-288-contract-review-document-as-report.md), [ADR-289](../adr/ADR-289-template-guided-drafting-over-freeform.md), [ADR-290](../adr/ADR-290-on-demand-compliance-audit-over-scheduled.md), [ADR-291](../adr/ADR-291-compliance-findings-persistent-lifecycle.md), [ADR-292](../adr/ADR-292-ai-generated-document-provenance.md)
> **Predecessors**: Phase 72 (AI Foundation -- `AiProvider`, `AnthropicAiProvider`, `AiFirmProfile`, `AiExecution`, `AiExecutionGate`, `AiSkill`, `AiSkillExecutionService`, `StubAiProvider`, `AiCostService`), Phase 12/31/42 (Document system -- `Document`, `DocumentTemplate`, `Clause`, `DocumentService`), Phase 14 (Customer Compliance -- `ComplianceChecklist`, `ComplianceChecklistItem`), Phase 50 (POPIA -- `DataProtectionController`, `RetentionService`), Phase 55 (Legal Foundations -- `PrescriptionTrackerService`), Phase 60 (Trust Accounting -- `TrustAccountService`)
> **Starting epic**: 538 . Last completed: 537 (Phase 73)
> **Migration high-water at phase start**: tenant **V126**. Phase 74 ships **one** tenant migration (V127).

Phase 74 delivers three new AI skills -- contract review, template-guided drafting, and on-demand compliance audit -- that plug into the Phase 72 skill infrastructure. Each skill reads from the system of record, produces structured output, and routes proposed actions through execution gates for attorney approval. Two new persistence entities (`ComplianceAuditReport`, `ComplianceAuditFinding`) and provenance columns on `Document` (`source`, `ai_execution_id`) are added. New supporting services include `DocumentTextExtractorService`, `ComplianceDataCollectorService`, `AiReviewReportGenerator`, `AiDraftDocumentGenerator`, and `ComplianceAuditReportService`.

---

## Open Questions

- **PDFBox dependency.** Phase 72 added PDFBox for `FicaDocumentReader`. Verify at implementation time that `org.apache.pdfbox:pdfbox` is already in the dependency tree. If not, add it in slice 538A.
- **Apache POI for DOCX.** `DocumentTextExtractorService` needs DOCX text extraction. Verify whether `poi-ooxml` is already a transitive dependency from the existing DOCX merge pipeline (Phase 42). If not, add it in slice 538A.
- **Template variable metadata structure.** The drafting skill needs to read variable definitions from `DocumentTemplate.variables`. Verify the exact field name and JSONB structure at implementation time in slice 540A.
- **Compliance service method signatures.** `ComplianceDataCollectorService` queries 6+ services. Verify exact method names on `ComplianceChecklistService`, `PrescriptionTrackerService`, `RetentionService`, `TrustAccountService` at implementation time in slice 541A. Some services may need new read-only query methods added.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 538 | V127 Migration + Document Provenance | Backend | -- | M | 538A, 538B | **Done** (PR #1359) |
| 539 | Contract Review Skill (Backend) | Backend | 538A | L | 539A, 539B | **Done** (PRs #1360, #1361) |
| 540 | Drafting Skill (Backend) | Backend | 538A | L | 540A, 540B | **Done** (PRs #1362, #1363) |
| 541 | Compliance Data Collector + Audit Skill (Backend) | Backend | 538A | L | 541A, 541B | **Done** (PRs #1364, #1365) |
| 542 | Compliance Audit Persistence + Finding Lifecycle | Backend | 538A, 541A | L | 542A, 542B | |
| 543 | Gate Executors + StubAiProvider Extensions | Backend | 539A, 540A, 541A, 542A | M | 543A | |
| 544 | Contract Review Frontend | Frontend | 539A | M | 544A | |
| 545 | Drafting Frontend | Frontend | 540A | M | 545A | |
| 546 | Compliance Dashboard Extension (Frontend) | Frontend | 542A | L | 546A, 546B | |

**Slice count: 14** (9 architecture slices expanded to 14 numbered slices to enforce the backend-frontend separation rule, split heavy backends into sub-slices, and honour the 6-10 files / ~800 LOC slice-sizing budget).

---

## Dependency Graph

```
PHASES already complete:
  Phase 72 (AI Foundation — AiProvider, AiSkill, AiSkillExecutionService, AiExecution,
            AiExecutionGate, GateAction, GateActionExecutor, AiCostService,
            StubAiProvider, FicaVerificationSkill, MatterIntakeSkill)
  Phase 14 (Customer Compliance — ComplianceChecklist, ComplianceChecklistItem)
  Phase 12/31/42 (Document System — Document, DocumentTemplate, Clause, DocumentService)
  Phase 50 (POPIA — DataProtectionController, RetentionService)
  Phase 55 (Legal Foundations — PrescriptionTrackerService)
  Phase 60 (Trust Accounting — TrustAccountService)
                                 │
                                 ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 1 — Migration + Shared Infrastructure (sequential) │
        │                                                          │
        │   [538A  V127 migration (compliance_audit_reports,       │
        │          compliance_audit_findings, ALTER documents      │
        │          for provenance columns)]                        │
        │                       │                                  │
        │                       ▼                                  │
        │   [538B  DocumentTextExtractorService + ExtractedText    │
        │          record + Document entity provenance fields +    │
        │          unit tests for PDF/DOCX/Tiptap extraction]      │
        └─────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 2 — Skill Backends (parallel after 538A)           │
        │                                                          │
        │   [539A  ContractReviewSkill + ContractReviewOutput +    │
        │          system prompt + output schema + canned test     │
        │          response + integration test]                    │
        │                       │                                  │
        │                       ▼                                  │
        │   [539B  AiReviewReportGenerator + Tiptap report        │
        │          builder + integration test]                     │
        │                                                          │
        │   [540A  DraftingSkill + DraftingOutput + system prompt  │
        │          + output schema + canned test response +        │
        │          integration test]                               │
        │                       │                                  │
        │                       ▼                                  │
        │   [540B  AiDraftDocumentGenerator + template filling +   │
        │          integration test]                               │
        │                                                          │
        │   [541A  ComplianceDataCollectorService +                │
        │          ComplianceSnapshot + module guard logic]        │
        │                       │                                  │
        │                       ▼                                  │
        │   [541B  ComplianceAuditSkill + ComplianceAuditOutput    │
        │          + system prompt + output schema + canned test   │
        │          response + integration test]                    │
        └─────────────────────────────────────────────────────────┘
                                 │
                                 ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 3 — Compliance Persistence (sequential after 541A) │
        │                                                          │
        │   [542A  ComplianceAuditReport + ComplianceAuditFinding  │
        │          entities + repos + ComplianceAuditReportService  │
        │          (CRUD + finding lifecycle) + integration tests] │
        │                       │                                  │
        │                       ▼                                  │
        │   [542B  ComplianceAuditReportController + finding       │
        │          status endpoint + audit events + integration    │
        │          tests]                                          │
        └─────────────────────────────────────────────────────────┘
                                 │
                                 ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 4 — Gate Executors + Stubs (after 539A,540A,      │
        │           541A,542A)                                     │
        │                                                          │
        │   [543A  GateAction sealed interface extension (3 new    │
        │          permits) + GateActionExecutor extension (3 new  │
        │          gate types) + StubAiProvider extensions (3 new  │
        │          canned responses) + AiSkillController endpoint  │
        │          additions + integration tests]                  │
        └─────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 5 — Frontends (parallel after Stage 4)             │
        │                                                          │
        │   [544A  Contract review button + results panel on       │
        │          matter documents tab + API hooks]               │
        │                                                          │
        │   [545A  Drafting dialog + variable table + template     │
        │          selector + API hooks]                           │
        │                                                          │
        │   [546A  Compliance audit tab + summary + "Run AI        │
        │          Audit" button + audit history + API hooks]      │
        │                       │                                  │
        │                       ▼                                  │
        │   [546B  Finding list + finding detail + resolution      │
        │          workflow + filters]                             │
        └─────────────────────────────────────────────────────────┘
```

**Parallel opportunities:**
- After **538A** lands, **539A**, **540A**, and **541A** can begin in parallel (three independent skill backends).
- **539B** depends on **539A**; **540B** depends on **540A**; **541B** depends on **541A** (each is the "generator" slice following the "skill" slice).
- **542A** depends on **538A** (migration) and **541A** (compliance snapshot types).
- **543A** depends on all four skill/persistence slices: **539A**, **540A**, **541A**, **542A** (it wires gate executors for all three skills).
- **544A**, **545A**, and **546A** parallelise in Stage 5 -- each skill's frontend is independent.
- **546B** depends on **546A** (finding list extends the audit tab).

---

## Implementation Order

### Stage 1 -- Migration + Shared Infrastructure (sequential)

| Order | Slice | Summary |
|-------|-------|---------|
| 1a | **538A** | V127 tenant migration: `compliance_audit_reports` table, `compliance_audit_findings` table, `ALTER TABLE documents` adding `source` and `ai_execution_id` provenance columns, all indexes per architecture Section 11.7. | **Done** (PR #1359) |
| 1b | **538B** | `DocumentTextExtractorService` (PDF via PDFBox, DOCX via POI OOXML, Tiptap via JSON traversal), `ExtractedText` record, 100KB truncation logic, `Document` entity modification (add `source` + `aiExecutionId` fields), unit tests. | **Done** (PR #1359) |

### Stage 2 -- Skill Backends (parallel after 538A, sequential within each skill)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 2a | **539A** | `ContractReviewSkill` implementing `AiSkill`; `ContractReviewOutput` record + nested records; system prompt resource; output schema resource; canned test response; integration test. | 540A, 541A | **Done** (PR #1360) |
| 2b | **539B** | `AiReviewReportGenerator` (Tiptap report builder from `ContractReviewOutput`); integration test with document creation. | 540B, 541B | **Done** (PR #1361) |
| 2c | **540A** | `DraftingSkill` implementing `AiSkill`; `DraftingOutput` record + nested records; system prompt resource; output schema resource; canned test response; integration test. | 539A, 541A | **Done** (PR #1362) |
| 2d | **540B** | `AiDraftDocumentGenerator` (template filling with AI variable values); integration test with document generation. | 539B, 541B | **Done** (PR #1363) |
| 2e | **541A** | `ComplianceDataCollectorService` + `ComplianceSnapshot` record; module guard logic; data aggregation + outlier extraction. | 539A, 540A | **Done** (PR #1364) |
| 2f | **541B** | `ComplianceAuditSkill` implementing `AiSkill`; `ComplianceAuditOutput` record; system prompt resource; output schema resource; canned test response; concurrent audit prevention; integration test. | 539B, 540B | **Done** (PR #1365) |

### Stage 3 -- Compliance Persistence (sequential after 541A)

| Order | Slice | Summary |
|-------|-------|---------|
| 3a | **542A** | `ComplianceAuditReport` entity + repo; `ComplianceAuditFinding` entity + repo; `ComplianceAuditReportService` (CRUD + finding lifecycle with status validation); integration tests for entity persistence and lifecycle transitions. |
| 3b | **542B** | `ComplianceAuditReportController` (4 endpoints: list reports, get report, list findings, update finding status); `COMPLIANCE_FINDING_STATUS_CHANGED` audit event; integration tests for controller + capability checks. |

### Stage 4 -- Gate Executors + Stubs (after 539A, 540A, 541A, 542A)

| Order | Slice | Summary |
|-------|-------|---------|
| 4a | **543A** | `GateAction` sealed interface extension (3 new permits: `CreateReviewReportAction`, `CreateDraftDocumentAction`, `PublishComplianceReportAction`); `GateActionExecutor` extension (3 new cases); `AiSkillController` endpoint additions (3 new POST endpoints); `StubAiProvider` canned response routing for 3 new skill IDs; end-to-end integration tests. |

### Stage 5 -- Frontends (parallel after Stage 4)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 5a | **544A** | Contract review button on matter documents tab; review results panel; API hooks; loading/error states. | 545A, 546A |
| 5b | **545A** | Drafting dialog (template selector + AI processing + variable editing + clause recommendations); drafting variable table with confidence badges; API hooks. | 544A, 546A |
| 5c | **546A** | Compliance audit tab on compliance dashboard; "Run AI Audit" button; audit summary (grade badge, category scores); audit history panel; API hooks. | 544A, 545A |
| 5d | **546B** | Compliance finding list with severity/category/status filters; finding detail dialog with resolution workflow; finding status transitions. | After 546A |

### Timeline

```
Stage 1: [538A] -> [538B]                                               <- sequential
Stage 2: [539A -> 539B] // [540A -> 540B] // [541A -> 541B]            <- 3 parallel chains
Stage 3: [542A] -> [542B]                                               <- sequential (after 541A)
Stage 4: [543A]                                                         <- after 539A, 540A, 541A, 542A
Stage 5: [544A] // [545A] // [546A -> 546B]                            <- 3-way parallel
```

A realistic day-by-day cadence: 538A days 1-2; 538B days 2-4; 539A + 540A + 541A days 4-8 (3-way parallel); 539B + 540B + 541B days 8-11 (3-way parallel); 542A days 9-12 (can start after 541A); 542B days 12-14; 543A days 14-17; 544A + 545A + 546A days 17-21 (3-way parallel); 546B days 21-23.

---

## Epic 538: V127 Migration + Document Provenance

**Goal**: Create the V127 tenant migration that adds the `compliance_audit_reports` and `compliance_audit_findings` tables, extends `documents` with AI provenance columns (`source`, `ai_execution_id`), and build the `DocumentTextExtractorService` that extracts plain text from PDF, DOCX, and Tiptap documents for AI skill consumption. Add the provenance fields to the `Document` JPA entity.

**References**: Architecture Section 11.7 (V127 Migration), Section 11.6 (`DocumentTextExtractorService`), Section 11.8.1 (Implementation Guidance); [ADR-292](../adr/ADR-292-ai-generated-document-provenance.md).

**Dependencies**: Phase 72 merged (V122 migration established `ai_executions` table which is FK target for `compliance_audit_reports.execution_id` and `documents.ai_execution_id`).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **538A** | 538A.1-538A.3 | ~3 backend files (1 migration + 1 entity modification + 1 test file) | V127 migration (2 new tables + ALTER documents + indexes); `Document` entity provenance fields (`source`, `aiExecutionId`); migration verification test. | **Done** (PR #1359) |
| **538B** | 538B.1-538B.4 | ~5 backend files (1 service + 1 record + 1 test file + optional pom.xml modification + 1 test resource) | `DocumentTextExtractorService` (PDF via PDFBox, DOCX via POI OOXML, Tiptap via JSON traversal); `ExtractedText` record; 100KB truncation logic; unit tests for all three formats. | **Done** (PR #1359) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 538A.1 | Create V127 tenant migration | `backend/src/main/resources/db/migration/tenant/V127__compliance_audit_tables.sql` | verified by 538A.3 (migration runs clean) | existing `V122__ai_foundation.sql` for format | SQL verbatim from architecture Section 11.7: `ALTER TABLE documents ADD COLUMN IF NOT EXISTS source VARCHAR(30) NOT NULL DEFAULT 'MANUAL'`; `ALTER TABLE documents ADD COLUMN IF NOT EXISTS ai_execution_id UUID REFERENCES ai_executions(id)`; partial indexes on `source` and `ai_execution_id`; `CREATE TABLE compliance_audit_reports` (11 columns, 3 indexes including unique on `execution_id`); `CREATE TABLE compliance_audit_findings` (18 columns, 5 indexes including unique on `report_id, finding_id`); `ON DELETE CASCADE` on `findings.report_id`. All `CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` for idempotency. |
| 538A.2 | Add provenance fields to `Document` entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/Document.java` (modify) | 538A.3 | existing entity field patterns in `Document.java` | Add two fields: `@Column(name = "source") private String source = "MANUAL"` and `@Column(name = "ai_execution_id") private UUID aiExecutionId`. Add getter/setter methods following existing patterns. No Lombok. Domain method: `markAsAiGenerated(UUID executionId)` sets `source = "AI_GENERATED"` and `aiExecutionId`. |
| 538A.3 | Integration test for V127 migration + provenance fields | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/document/DocumentProvenanceTest.java` | ~4 tests: (1) V127 migration runs clean; (2) existing documents have `source = MANUAL` default; (3) `markAsAiGenerated()` sets both fields correctly; (4) `aiExecutionId` FK constraint validates against `ai_executions` table | standard `@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")` pattern | Verify migration runs clean on embedded Postgres. Verify round-trip of provenance fields. Verify default value for existing documents. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 538B.1 | Create `ExtractedText` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/ExtractedText.java` | covered by 538B.4 | existing record style; `FicaVerificationOutput.java` for record pattern | `public record ExtractedText(String content, int characterCount, boolean wasTruncated, String truncationWarning) {}`. Per architecture Section 11.6. |
| 538B.2 | Create `DocumentTextExtractorService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/DocumentTextExtractorService.java` | 538B.4 | `FicaDocumentReader.java` for S3 fetch + PDFBox pattern | `@Service`. Main method: `extractText(Document document): ExtractedText`. Implementation: (1) resolve document storage key; (2) fetch bytes from S3 via `StorageAdapter.download()`; (3) dispatch by format: PDF -> PDFBox `PDFTextStripper`, DOCX -> Apache POI `XWPFDocument` paragraph text extraction, Tiptap JSON -> recursive JSON traversal extracting text nodes; (4) if extracted text > 100KB (102,400 chars), truncate with warning; (5) return `ExtractedText` with character count and truncation status. Constructor injection of `StorageAdapter`. Error handling: encrypted/corrupted PDF throws `InvalidStateException("UNSUPPORTED_DOCUMENT")`. |
| 538B.3 | Verify/add POI OOXML dependency | `backend/pom.xml` (modify if needed) | -- | existing dependency management | Check if `poi-ooxml` is already a transitive dependency. If not, add `<dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId></dependency>` with appropriate version. PDFBox should already be present from Phase 72 (`FicaDocumentReader`). |
| 538B.4 | Unit tests for text extraction | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/DocumentTextExtractorServiceTest.java`, `backend/src/test/resources/ai/test-documents/` (test fixture files) | ~6 tests: (1) PDF text extraction produces expected content; (2) DOCX text extraction produces expected content; (3) Tiptap JSON text extraction traverses nested nodes; (4) truncation at 100KB limit produces warning; (5) encrypted/corrupted PDF throws `InvalidStateException`; (6) empty document returns empty `ExtractedText` | unit test with mocked `StorageAdapter` | Create small test fixture files: `test.pdf` (a few paragraphs), `test.docx` (a few paragraphs), `test-tiptap.json` (nested Tiptap JSON). Use `@MockitoBean` on `StorageAdapter` to return fixture bytes. |

### Key Files

**Create (backend):**
- `backend/src/main/resources/db/migration/tenant/V127__compliance_audit_tables.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/ExtractedText.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/DocumentTextExtractorService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/document/DocumentProvenanceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/DocumentTextExtractorServiceTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/Document.java` -- add `source`, `aiExecutionId` fields + `markAsAiGenerated()` method
- `backend/pom.xml` -- add `poi-ooxml` dependency if not already present

**Read for context:**
- `backend/src/main/resources/db/migration/tenant/V122__ai_foundation.sql` -- migration format reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaDocumentReader.java` -- S3 fetch + PDFBox pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/s3/S3StorageAdapter.java` -- `StorageAdapter` interface

### Architecture Decisions

- **Provenance as document metadata** ([ADR-292](../adr/ADR-292-ai-generated-document-provenance.md)) -- `source` and `ai_execution_id` fields on the existing `Document` entity rather than a separate provenance entity. Lightweight, queryable, backward-compatible (`source` defaults to `MANUAL`).
- **All three V127 concerns in one migration** -- compliance audit tables and document provenance columns are logically related (both support Phase 74 AI skills) and land in a single migration file. This avoids wasting migration version numbers.
- **Text extraction as shared service** -- `DocumentTextExtractorService` lives in `integration/ai/skill/` (not `document/`) because it is an AI-infrastructure concern. It converts documents into text for AI consumption. It does not modify documents.
- **100KB truncation** -- hard limit per architecture. Documents larger than 100KB of extracted text are truncated with a warning in the output. This keeps AI token costs predictable.

### Non-scope

- No skill implementations (land in 539A-541B).
- No compliance entities (land in 542A).
- No gate executors (land in 543A).
- No frontend (lands in 544A-546B).

---

## Epic 539: Contract Review Skill (Backend)

**Goal**: Implement the contract review AI skill that extracts text from uploaded documents, classifies the document type, reviews against SA jurisdiction-specific legal framework, and produces structured findings with severity rankings, statutory cross-references, and recommended amendments. Build the `AiReviewReportGenerator` that converts the AI output into a Tiptap document attached to the matter.

**References**: Architecture Section 11.3.1 (Contract Review Flow), Section 11.5.1 (Sequence Diagram), Section 11.6 (`ContractReviewSkill`, `AiReviewReportGenerator`); [ADR-288](../adr/ADR-288-contract-review-document-as-report.md).

**Dependencies**: Epic 538A (V127 migration for `documents.source` and `documents.ai_execution_id`), Epic 538B (`DocumentTextExtractorService`).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **539A** | 539A.1-539A.5 | ~7 backend files (1 skill class + 1 output record + 2 resource files + 1 test resource + 1 test file + 1 schema file) | `ContractReviewSkill` implementing `AiSkill`; `ContractReviewOutput` record with nested records; system prompt resource (SA legal framework); output schema resource; canned test response; integration test. | **Done** (PR #1360) |
| **539B** | 539B.1-539B.3 | ~4 backend files (1 generator service + 1 test file + 1 test resource) | `AiReviewReportGenerator` (Tiptap JSON report builder from `ContractReviewOutput`); integration test verifying document creation with provenance. | **Done** (PR #1361) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 539A.1 | Create `ContractReviewOutput` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/ContractReviewOutput.java` | covered by 539A.5 | existing `FicaVerificationOutput.java` for record pattern | `public record ContractReviewOutput(DocumentClassification documentClassification, String executiveSummary, List<Finding> findings, List<MissingProtection> missingProtections, String overallRiskAssessment, List<RecommendedAction> recommendedActions) {}`. Nested records per architecture Section 11.3.1: `DocumentClassification(String type, String subtype, List<String> partiesIdentified)`, `Finding(String severity, String category, String clauseReference, String title, String description, String riskExplanation, String recommendation, String statutoryReference)`, `MissingProtection(String protection, String reasoning, String recommendation, String priority)`, `RecommendedAction(String action, String reasoning)`. Parsed from AI JSON via Jackson. |
| 539A.2 | Create contract review system prompt | `backend/src/main/resources/ai/skills/contract-review/system.txt` | covered by 539A.5 | existing `ai/skills/fica-verification/system.txt` for prompt format | System prompt per architecture Section 11.3.1 and requirements Section 1.3: role assignment ("contract review assistant for a South African law firm"), SA legal framework injection per review type (commercial: CPA s48, ECT Act; employment: BCEA, LRA, EEA, Basson v Chilwan; corporate: Companies Act 71/2008, King IV, BEE Act), `{firm_profile_block}` placeholder, risk calibration instructions, output format specification with `{output_schema}` placeholder. SA legal knowledge sourced from claude-for-legal-sa topic files at build time. |
| 539A.3 | Create contract review output schema | `backend/src/main/resources/ai/skills/contract-review/output-schema.json` | covered by 539A.5 | existing `ai/skills/fica-verification/output-schema.json` | JSON schema matching `ContractReviewOutput` record structure. Defines types for all nested objects: `document_classification`, `findings[]`, `missing_protections[]`, `recommended_actions[]`. Schema used in AI prompt for structured output. |
| 539A.4 | Create `ContractReviewSkill` implementing `AiSkill` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/ContractReviewSkill.java` | 539A.5 | existing `FicaVerificationSkill.java` for full skill implementation pattern | `@Component`. `skillId()` returns `"contract-review"`. `requiresVision()` returns `false`. `assembleSystemPrompt(AiFirmProfile)`: loads `system.txt`, interpolates `{firm_profile_block}` with `AiFirmProfileService.assembleProfileBlock()`, interpolates `{output_schema}` from `output-schema.json`. `assembleUserPrompt(SkillContext)`: loads document via `DocumentService` (context.entityId = documentId), calls `DocumentTextExtractorService.extractText()`, loads matter context via `ProjectService` (from additionalContext.projectId), auto-classifies review type from content + matter type, assembles user prompt with extracted text + matter context + review type. `createGates()`: one gate with `gate_type = "CREATE_REVIEW_REPORT"`, `proposed_action` = parsed `ContractReviewOutput` JSON, 72h expiry. Constructor injection of `DocumentTextExtractorService`, `DocumentService`, `ProjectService`, `ResourceLoader`. |
| 539A.5 | Create canned test response + integration test | `backend/src/test/resources/ai/stubs/contract-review/response.json`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/ContractReviewSkillTest.java` | ~5 tests: (1) skill assembles valid system prompt with firm profile block; (2) skill assembles user prompt with extracted document text; (3) skill creates one gate with type CREATE_REVIEW_REPORT; (4) output parsing produces valid `ContractReviewOutput` from canned response; (5) validation rejects non-PDF/DOCX documents with clear error | existing `FicaVerificationSkillTest.java` for test pattern | Canned response JSON matching `ContractReviewOutput` schema with 2-3 findings, 1 missing protection, and 1 recommended action. Integration test uses `StubAiProvider` to return canned response. Mock `StorageAdapter` to return a test PDF. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 539B.1 | Create `AiReviewReportGenerator` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/AiReviewReportGenerator.java` | 539B.3 | `DocumentService.createDocument()` for document creation pattern | `@Service`. Method: `generateReviewReport(ContractReviewOutput output, UUID projectId, UUID reviewedDocumentId, UUID executionId, UUID memberId): Document`. Implementation: (1) build Tiptap JSON from output -- title heading ("Contract Review Report"), executive summary paragraph, severity-grouped findings (each with clause reference, description, legal basis, recommendation), missing protections section, overall assessment; (2) call `DocumentService` to create document with `format = "TIPTAP"`, `projectId`, `name = "Contract Review Report - [date]"`; (3) set `source = "AI_GENERATED"` and `aiExecutionId = executionId` via `markAsAiGenerated()`. Constructor injection of `DocumentService`. |
| 539B.2 | Create Tiptap report builder utility | included in `AiReviewReportGenerator.java` (private methods) | covered by 539B.3 | existing Tiptap JSON structure in `DocumentService` or `TiptapRenderer` if present | Private methods in `AiReviewReportGenerator`: `buildHeading(text, level)`, `buildParagraph(text)`, `buildBulletList(items)`, `buildFindingSection(finding)`. Each returns a `Map<String, Object>` representing a Tiptap JSON node. The full document is `{"type": "doc", "content": [...nodes]}`. No external Tiptap library -- pure JSON construction. |
| 539B.3 | Integration test for review report generation | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/AiReviewReportGeneratorTest.java` | ~4 tests: (1) `generateReviewReport()` creates a Document entity with Tiptap content; (2) document has `source = "AI_GENERATED"` and correct `aiExecutionId`; (3) generated Tiptap content includes executive summary heading; (4) findings are grouped by severity in the report structure | standard integration test pattern | Uses real `DocumentService` and embedded Postgres. Creates a test project and execution, then verifies the generated document's content and metadata. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/ContractReviewSkill.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/ContractReviewOutput.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/AiReviewReportGenerator.java`
- `backend/src/main/resources/ai/skills/contract-review/system.txt`
- `backend/src/main/resources/ai/skills/contract-review/output-schema.json`
- `backend/src/test/resources/ai/stubs/contract-review/response.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/ContractReviewSkillTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/AiReviewReportGeneratorTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationSkill.java` -- skill implementation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationOutput.java` -- output record pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/DocumentTextExtractorService.java` -- text extraction (from 538B)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentService.java` -- document creation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` -- matter context loading
- `backend/src/main/resources/ai/skills/fica-verification/system.txt` -- prompt format reference

### Architecture Decisions

- **Document-as-report** ([ADR-288](../adr/ADR-288-contract-review-document-as-report.md)) -- review output is rendered as a Tiptap document entity, not inline annotations. Fits existing document pipeline (view, export, share). `AiReviewReportGenerator` is the only new service needed.
- **Review type auto-detection** -- the skill classifies the document type from content + matter type. Falls back to `GENERAL` when classification is uncertain. SA legal framework injection varies by review type.
- **Text extraction, not vision** -- `requiresVision()` returns `false`. Contract review operates on extracted text, not document images. This differs from FICA verification which uses vision for scanned documents.
- **Provenance via document metadata** ([ADR-292](../adr/ADR-292-ai-generated-document-provenance.md)) -- the generated report document has `source = AI_GENERATED` and `ai_execution_id` linking to the execution that produced it.

### Non-scope

- No gate executor for `CREATE_REVIEW_REPORT` (lands in 543A).
- No controller endpoint for contract review invocation (lands in 543A).
- No frontend (lands in 544A).

---

## Epic 540: Drafting Skill (Backend)

**Goal**: Implement the template-guided drafting skill that loads template structure and variable metadata, assembles matter/customer/firm context, and produces structured output with per-variable fills (confidence scored), narrative section drafts, and clause recommendations. Build the `AiDraftDocumentGenerator` that applies AI values to the template via the existing `DocumentGenerationService` pipeline.

**References**: Architecture Section 11.3.2 (Drafting Flow), Section 11.6 (`DraftingSkill`, `AiDraftDocumentGenerator`); [ADR-289](../adr/ADR-289-template-guided-drafting-over-freeform.md).

**Dependencies**: Epic 538A (V127 migration for `documents.source` and `documents.ai_execution_id`).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **540A** | 540A.1-540A.5 | ~7 backend files (1 skill class + 1 output record + 2 resource files + 1 test resource + 1 test file + 1 schema file) | `DraftingSkill` implementing `AiSkill`; `DraftingOutput` record with nested records; system prompt resource (SA drafting conventions); output schema resource; canned test response; integration test. | **Done** (PR #1362) |
| **540B** | 540B.1-540B.3 | ~3 backend files (1 generator service + 1 test file) | `AiDraftDocumentGenerator` (template filling with AI variable values via `DocumentGenerationService`); integration test with document creation. | **Done** (PR #1363) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 540A.1 | Create `DraftingOutput` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/DraftingOutput.java` | covered by 540A.5 | existing `FicaVerificationOutput.java` for record pattern | `public record DraftingOutput(UUID templateId, List<VariableFill> variableFills, List<NarrativeSection> narrativeSections, List<ClauseRecommendation> clauseRecommendations, List<String> warnings, List<RecommendedAction> recommendedActions) {}`. Nested records per architecture Section 11.3.2: `VariableFill(String variableName, String value, String source, String confidence, String flag)`, `NarrativeSection(String sectionName, String content, String notes)`, `ClauseRecommendation(UUID clauseId, String clauseName, String reasoning)`, `RecommendedAction(String action, String reasoning)`. |
| 540A.2 | Create drafting system prompt | `backend/src/main/resources/ai/skills/drafting/system.txt` | covered by 540A.5 | existing `ai/skills/fica-verification/system.txt` for format | System prompt per architecture Section 11.3.2 and requirements Section 2.3: role assignment ("legal document drafting assistant for a South African law firm"), firm's house style from profile, SA legal drafting conventions (CPA s22 plain language, party naming, execution blocks), template structure + variable metadata, `{firm_profile_block}` placeholder, `{output_schema}` placeholder. |
| 540A.3 | Create drafting output schema | `backend/src/main/resources/ai/skills/drafting/output-schema.json` | covered by 540A.5 | existing `ai/skills/fica-verification/output-schema.json` | JSON schema matching `DraftingOutput` record structure. Defines types for `variable_fills[]` (with source and confidence enums), `narrative_sections[]`, `clause_recommendations[]`. |
| 540A.4 | Create `DraftingSkill` implementing `AiSkill` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/DraftingSkill.java` | 540A.5 | existing `FicaVerificationSkill.java` / `MatterIntakeSkill.java` for skill pattern | `@Component`. `skillId()` returns `"drafting"`. `requiresVision()` returns `false`. `assembleSystemPrompt()`: loads `system.txt`, interpolates firm profile block and output schema. `assembleUserPrompt(SkillContext)`: loads template via `DocumentTemplateService` (from additionalContext.templateId), loads project via `ProjectService` (context.entityId = projectId), loads customer via `CustomerService` (from project's customer), loads available clauses via `ClauseService` (filtered by relevance), assembles user prompt with template structure + variable definitions + matter/customer context + clause summaries. `createGates()`: one gate with `gate_type = "CREATE_DRAFT_DOCUMENT"`, `proposed_action` = parsed `DraftingOutput` JSON, 72h expiry. Constructor injection of `DocumentTemplateService`, `ProjectService`, `CustomerService`, `ClauseService`, `ResourceLoader`. |
| 540A.5 | Create canned test response + integration test | `backend/src/test/resources/ai/stubs/drafting/response.json`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/DraftingSkillTest.java` | ~5 tests: (1) skill assembles valid system prompt with house style; (2) skill assembles user prompt with template variables + matter context; (3) skill creates one gate with type CREATE_DRAFT_DOCUMENT; (4) output parsing produces valid `DraftingOutput` with variable fills; (5) variable fills have correct confidence levels (HIGH for data-sourced, MEDIUM for inferred, UNDETERMINED for missing) | existing `FicaVerificationSkillTest.java` / `MatterIntakeSkillTest.java` for test pattern | Canned response JSON with 5-6 variable fills (mix of HIGH/MEDIUM/UNDETERMINED confidence), 1-2 narrative sections, 1 clause recommendation, 1 warning. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 540B.1 | Create `AiDraftDocumentGenerator` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/AiDraftDocumentGenerator.java` | 540B.3 | `DocumentService.createDocument()` for document creation; `DocumentTemplateService` for template loading | `@Service`. Method: `generateDraft(DraftingOutput output, UUID templateId, UUID projectId, UUID executionId, UUID memberId): Document`. Implementation: (1) load template via `DocumentTemplateService`; (2) build variable map from `output.variableFills()` (variableName -> value pairs); (3) inject narrative content into template sections; (4) create document via `DocumentService` with template-applied content, `projectId`, name derived from template + matter; (5) set `source = "AI_GENERATED"` and `aiExecutionId = executionId` via `markAsAiGenerated()`. Constructor injection of `DocumentTemplateService`, `DocumentService`. |
| 540B.2 | Handle clause injection in draft | included in `AiDraftDocumentGenerator.java` (private method) | covered by 540B.3 | existing `ClauseService` for clause loading | Private method: `injectRecommendedClauses(DraftingOutput output, Document draft)` -- for each accepted `ClauseRecommendation`, loads clause content from `ClauseService` and appends to the document content at the appropriate section. In v1, clauses are appended at the end of the document with a "Recommended Clauses" heading. |
| 540B.3 | Integration test for draft document generation | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/AiDraftDocumentGeneratorTest.java` | ~4 tests: (1) `generateDraft()` creates a Document entity linked to the project; (2) document has `source = "AI_GENERATED"` and correct `aiExecutionId`; (3) variable values from AI output are applied to the template; (4) document name includes template name | standard integration test pattern | Creates a test template, project, and customer, then verifies the generated document's content and metadata. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/DraftingSkill.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/DraftingOutput.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/AiDraftDocumentGenerator.java`
- `backend/src/main/resources/ai/skills/drafting/system.txt`
- `backend/src/main/resources/ai/skills/drafting/output-schema.json`
- `backend/src/test/resources/ai/stubs/drafting/response.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/DraftingSkillTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/AiDraftDocumentGeneratorTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationSkill.java` -- skill pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java` -- template loading + variable metadata
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` -- template entity with variables JSONB
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClauseService.java` -- clause loading
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` -- customer context
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` -- matter context

### Architecture Decisions

- **Template-guided, not freeform** ([ADR-289](../adr/ADR-289-template-guided-drafting-over-freeform.md)) -- the AI fills template variables and generates narrative sections within the template's structure. Reduces hallucination, leverages existing template investment, enables per-variable confidence scoring.
- **Confidence scoring** -- each variable fill has a source (`customer.name`, `AI_GENERATED`, `UNDETERMINED`) and confidence level (`HIGH`, `MEDIUM`, `LOW`). The frontend renders these as color-coded badges for attorney review.
- **Uses existing generation pipeline** -- `AiDraftDocumentGenerator` delegates to `DocumentService` for document creation, using the same pipeline as manual document generation. No new rendering infrastructure.
- **Clause recommendations are additive** -- the AI recommends clauses from the firm's clause library, not generating new clause text. Clauses are toggled by the attorney in the frontend.

### Non-scope

- No gate executor for `CREATE_DRAFT_DOCUMENT` (lands in 543A).
- No controller endpoint for drafting invocation (lands in 543A).
- No frontend dialog or variable editing (lands in 545A).

---

## Epic 541: Compliance Data Collector + Audit Skill (Backend)

**Goal**: Build the `ComplianceDataCollectorService` that aggregates compliance data from 6+ services into a summarised snapshot (statistics + outliers) suitable for AI consumption, and the `ComplianceAuditSkill` that produces a graded compliance report with severity-ranked findings. The data collector respects module guards (skips disabled modules) and manages data volume (aggressive aggregation for large firms).

**References**: Architecture Section 11.3.3 (Compliance Audit Flow), Section 11.3.4 (Finding Lifecycle), Section 11.5.2 (Sequence Diagram), Section 11.6 (`ComplianceDataCollectorService`, `ComplianceAuditSkill`); [ADR-290](../adr/ADR-290-on-demand-compliance-audit-over-scheduled.md).

**Dependencies**: Epic 538A (V127 migration -- `compliance_audit_reports` table must exist for concurrent audit check query on `AiExecution`).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **541A** | 541A.1-541A.4 | ~5 backend files (1 service + 1 snapshot record + 1 category data records + 1 test file) | `ComplianceDataCollectorService` aggregating 6+ services; `ComplianceSnapshot` record with category-level data records; module guard logic; unit tests. | **Done** (PR #1364) |
| **541B** | 541B.1-541B.5 | ~7 backend files (1 skill class + 1 output record + 2 resource files + 1 test resource + 1 test file + 1 schema file) | `ComplianceAuditSkill` implementing `AiSkill`; `ComplianceAuditOutput` record; system prompt (SA regulatory framework); output schema; concurrent audit prevention; canned test response; integration test. | **Done** (PR #1365) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 541A.1 | Create `ComplianceSnapshot` + category data records | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceSnapshot.java` | covered by 541A.4 | existing record style | `public record ComplianceSnapshot(FicaCddSummary ficaCdd, PopiaSummary popia, TrustAccountingSummary trustAccounting, PrescriptionSummary prescription, RetentionSummary retention, int totalActiveCustomers, String dataCollectionNotes) {}`. Nested records: `FicaCddSummary(int compliant, int nonCompliant, int criticallyOverdue, List<FlaggedCustomer> flaggedCustomers)`, `PopiaSummary(int registeredActivities, int unregisteredActivities, int pendingDsars, int overdueDsars)`, `TrustAccountingSummary(boolean moduleEnabled, int accountCount, int unreconciledItems, List<String> boundaryViolations)`, `PrescriptionSummary(boolean moduleEnabled, int approachingCount, int expiredCount, List<FlaggedMatter> flaggedMatters)`, `RetentionSummary(int approachingExpiry, int pastExpiry)`, `FlaggedCustomer(UUID id, String name, String issue)`, `FlaggedMatter(UUID id, String name, String prescriptionDate, String issue)`. |
| 541A.2 | Create `ComplianceDataCollectorService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceDataCollectorService.java` | 541A.4 | architecture Section 11.3.3 (data collection steps) | `@Service`. Method: `collectComplianceSnapshot(): ComplianceSnapshot`. Implementation per architecture: (1) query `CustomerService` for active customers with checklist status; (2) query `ComplianceChecklistService` for completion rates (via `ChecklistInstantiationService` or equivalent); (3) query `DataProtectionController`'s underlying service for POPIA processing activities + DSAR status; (4) if legal module enabled: query `TrustAccountService` for balances + boundary violations + unreconciled items; (5) if legal module enabled: query `PrescriptionTrackerService` for approaching/expired dates; (6) query `RetentionService` for retention status. Module guard: check `VerticalModuleGuard` (or equivalent) to determine if legal module is enabled before querying legal-specific services. Aggregation: produce statistics + outliers per category. For firms with >500 customers, use more aggressive aggregation (fewer individual flagged items, higher severity threshold). Cap flagged items at 50 per category. Tenant context from `RequestScopes.TENANT_ID` per convention. Constructor injection of all queried services + module guard. |
| 541A.3 | Implement data aggregation + token budget management | included in `ComplianceDataCollectorService.java` (private methods) | covered by 541A.4 | -- | Private methods: `aggregateFicaData()`, `aggregatePopiaData()`, `aggregateTrustAccountingData()`, `aggregatePrescriptionData()`, `aggregateRetentionData()`. Each queries its service(s), produces a summary record with counts + flagged items. `limitFlaggedItems(List<T> items, int max)` truncates with a note. Total snapshot string representation targets ~15K tokens (architecture Section 11.3.3). |
| 541A.4 | Unit tests for compliance data collector | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceDataCollectorServiceTest.java` | ~6 tests: (1) full snapshot aggregation with all modules enabled; (2) snapshot with legal module disabled skips trust accounting + prescription; (3) flagged items capped at 50 per category; (4) empty firm (no customers) produces clean snapshot; (5) large firm (>500 customers) uses aggressive aggregation; (6) individual category summary counts are accurate | unit test with `@MockitoBean` on all queried services | Mock all 6+ services to return controlled data. Verify aggregation logic, module guard behavior, and truncation. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 541B.1 | Create `ComplianceAuditOutput` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceAuditOutput.java` | covered by 541B.5 | existing output record pattern | `public record ComplianceAuditOutput(String auditDate, String overallGrade, String overallAssessment, Map<String, CategoryScore> categoryScores, List<AuditFinding> findings, List<Recommendation> recommendations) {}`. Nested records per architecture Section 11.3.3: `CategoryScore(String grade, int compliant, int nonCompliant, int critical)`, `AuditFinding(String id, String severity, String category, String title, String description, String regulatoryBasis, String remediation, List<EntityReference> entityReferences)`, `EntityReference(String type, UUID id, String name)`, `Recommendation(String priority, String recommendation, String estimatedEffort)`. |
| 541B.2 | Create compliance audit system prompt | `backend/src/main/resources/ai/skills/compliance-audit/system.txt` | covered by 541B.5 | existing `ai/skills/fica-verification/system.txt` for format | System prompt per architecture Section 11.3.3 and requirements Section 3.3: role assignment ("compliance audit assistant for a South African law firm"), SA regulatory framework (FICA Act 38/2001 CDD obligations, POPIA Act 4/2013 processing activities, Attorneys Act s78 / Rule 54 trust accounting, Prescription Act 68/1969 periods), `{firm_profile_block}` placeholder, `{output_schema}` placeholder. |
| 541B.3 | Create compliance audit output schema | `backend/src/main/resources/ai/skills/compliance-audit/output-schema.json` | covered by 541B.5 | existing schema files | JSON schema matching `ComplianceAuditOutput` record structure. Defines enums for severity, category, and finding structure. |
| 541B.4 | Create `ComplianceAuditSkill` implementing `AiSkill` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceAuditSkill.java` | 541B.5 | existing `FicaVerificationSkill.java` / `MatterIntakeSkill.java` for skill pattern | `@Component`. `skillId()` returns `"compliance-audit"`. `requiresVision()` returns `false`. `assembleSystemPrompt()`: loads `system.txt`, interpolates firm profile block and output schema. `assembleUserPrompt(SkillContext)`: calls `ComplianceDataCollectorService.collectComplianceSnapshot()`, serializes snapshot as user prompt with category sections. `createGates()`: one gate with `gate_type = "PUBLISH_COMPLIANCE_REPORT"`, `proposed_action` = parsed `ComplianceAuditOutput` JSON, 72h expiry. **Concurrent audit prevention**: before execution, query `AiExecution` for `skill_id = 'compliance-audit' AND status = 'IN_PROGRESS'` with `@Lock(PESSIMISTIC_WRITE)`. If found, throw `ResourceConflictException("A compliance audit is already in progress")`. Constructor injection of `ComplianceDataCollectorService`, `AiExecutionRepository`, `ResourceLoader`. |
| 541B.5 | Create canned test response + integration test | `backend/src/test/resources/ai/stubs/compliance-audit/response.json`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceAuditSkillTest.java` | ~5 tests: (1) skill assembles valid system prompt with regulatory framework; (2) skill assembles user prompt with compliance snapshot data; (3) skill creates one gate with type PUBLISH_COMPLIANCE_REPORT; (4) concurrent audit prevention rejects second invocation; (5) output parsing produces valid `ComplianceAuditOutput` from canned response | existing skill test pattern | Canned response JSON with overall grade "B", 3 category scores, 3-4 findings (mix of CRITICAL/HIGH/MEDIUM), 2 recommendations. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceDataCollectorService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceSnapshot.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceAuditSkill.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceAuditOutput.java`
- `backend/src/main/resources/ai/skills/compliance-audit/system.txt`
- `backend/src/main/resources/ai/skills/compliance-audit/output-schema.json`
- `backend/src/test/resources/ai/stubs/compliance-audit/response.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceDataCollectorServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceAuditSkillTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationSkill.java` -- skill implementation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ChecklistInstantiationService.java` -- checklist data access
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataProtectionController.java` -- POPIA data access
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountService.java` -- trust accounting data
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/PrescriptionTrackerService.java` -- prescription data
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionService.java` -- retention data
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/execution/AiExecutionRepository.java` -- concurrent audit check

### Architecture Decisions

- **On-demand only** ([ADR-290](../adr/ADR-290-on-demand-compliance-audit-over-scheduled.md)) -- the audit runs when the attorney clicks "Run AI Audit". No scheduler, no background jobs, no retry logic. Cost control and gate model alignment.
- **Concurrent audit prevention** -- application-level check via `PESSIMISTIC_WRITE` lock on the `AiExecution` query. Safe because compliance audits are infrequent (at most a few per month per tenant).
- **Data aggregation for token efficiency** -- the collector produces statistics + outliers, not raw records. Flagged items capped at 50 per category. Token budget target: ~15K tokens for the user prompt.
- **Module guard** -- categories for disabled modules are skipped. A firm without trust accounting gets no `trust_accounting` category in the snapshot. The `dataCollectionNotes` field captures which categories were skipped.

### Non-scope

- No compliance audit report/finding persistence (lands in 542A).
- No gate executor for `PUBLISH_COMPLIANCE_REPORT` (lands in 543A).
- No controller endpoint for compliance audit invocation (lands in 543A).
- No frontend (lands in 546A/546B).

---

## Epic 542: Compliance Audit Persistence + Finding Lifecycle

**Goal**: Build the `ComplianceAuditReport` and `ComplianceAuditFinding` JPA entities, their repositories, and the `ComplianceAuditReportService` that handles report CRUD and finding lifecycle transitions (OPEN -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED / FALSE_POSITIVE). Build the `ComplianceAuditReportController` with endpoints for listing reports, viewing findings, and updating finding status. Emit audit events for finding status changes.

**References**: Architecture Section 11.2.1 (ComplianceAuditReport), Section 11.2.2 (ComplianceAuditFinding), Section 11.3.4 (Finding Lifecycle), Section 11.4.2 (Report Endpoints); [ADR-291](../adr/ADR-291-compliance-findings-persistent-lifecycle.md).

**Dependencies**: Epic 538A (V127 migration creates `compliance_audit_reports` and `compliance_audit_findings` tables), Epic 541A (compliance snapshot types referenced by the report service).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **542A** | 542A.1-542A.6 | ~7 backend files (2 entities + 2 repos + 1 service + 1 enum file + 1 test file) | `ComplianceAuditReport` entity + repo; `ComplianceAuditFinding` entity + repo; `FindingSeverity` and `FindingCategory` enums; `ComplianceAuditReportService` (CRUD + finding lifecycle with validation); integration tests. |
| **542B** | 542B.1-542B.4 | ~5 backend files (1 controller + 1 event record + 1 DTO file + 1 test file) | `ComplianceAuditReportController` (4 endpoints); `ComplianceFindingStatusChangedEvent` audit event; response/request DTOs; integration tests for controller + capability checks. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 542A.1 | Create `ComplianceAuditReport` entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReport.java` | 542A.6 | existing entity pattern in `AiExecution.java` or `Document.java` | `@Entity @Table(name = "compliance_audit_reports")`. All fields per architecture Section 11.2.1: `id` (UUID PK), `executionId` (UUID FK to `ai_executions`), `overallGrade` (VARCHAR(5)), `overallAssessment` (TEXT), `categoryScores` (JSONB via `JpaJsonbConverter`), `status` (VARCHAR(20) default DRAFT), `publishedBy` (UUID nullable), `publishedAt` (TIMESTAMPTZ nullable), `createdAt`/`updatedAt`/`createdBy`/`updatedBy`. Domain methods: `publish(UUID publisherId)` sets status=PUBLISHED, publishedBy, publishedAt; `archive()` sets status=ARCHIVED. Protected no-arg + public constructor. `@ManyToOne(fetch = LAZY)` relationship to `AiExecution`. |
| 542A.2 | Create `ComplianceAuditFinding` entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditFinding.java` | 542A.6 | existing entity pattern | `@Entity @Table(name = "compliance_audit_findings")`. All fields per architecture Section 11.2.2: `id` (UUID PK), `reportId` (UUID FK), `findingId` (VARCHAR(10)), `severity` (VARCHAR(10)), `category` (VARCHAR(30)), `title` (VARCHAR(200)), `description` (TEXT), `regulatoryBasis` (TEXT nullable), `remediation` (TEXT nullable), `entityType` (VARCHAR(30) nullable), `entityId` (UUID nullable), `status` (VARCHAR(20) default OPEN), `resolvedBy` (UUID nullable), `resolvedAt` (TIMESTAMPTZ nullable), `resolutionNotes` (TEXT nullable), `createdAt`/`updatedAt`/`createdBy`/`updatedBy`. Domain methods: `acknowledge(UUID memberId)` validates OPEN -> ACKNOWLEDGED; `startProgress(UUID memberId)` validates ACKNOWLEDGED -> IN_PROGRESS; `resolve(UUID memberId, String notes)` validates IN_PROGRESS -> RESOLVED, sets resolvedBy/resolvedAt/resolutionNotes; `markFalsePositive(UUID memberId, String notes)` validates IN_PROGRESS -> FALSE_POSITIVE. `@ManyToOne(fetch = LAZY)` to `ComplianceAuditReport`. |
| 542A.3 | Create finding severity/category/status enums | included as string constants or a separate enum utility file | 542A.6 | existing enum patterns | Severity: CRITICAL, HIGH, MEDIUM, LOW, INFO. Category: FICA_CDD, POPIA, TRUST_ACCOUNTING, PRESCRIPTION, RECORD_RETENTION. Finding status: OPEN, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, FALSE_POSITIVE. Report status: DRAFT, PUBLISHED, ARCHIVED. Used as VARCHAR values in the DB, validated by domain methods. |
| 542A.4 | Create repositories | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReportRepository.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditFindingRepository.java` | 542A.6 | existing repo pattern | `ComplianceAuditReportRepository extends JpaRepository<ComplianceAuditReport, UUID>`. Custom queries: `findByStatusOrderByCreatedAtDesc(String status, Pageable)`, `findByExecutionId(UUID)`. `ComplianceAuditFindingRepository extends JpaRepository<ComplianceAuditFinding, UUID>`. Custom queries: `findByReportIdOrderBySeverityAsc(UUID reportId, Pageable)`, `findByReportIdAndSeverityIn(UUID, List<String>, Pageable)`, `findByReportIdAndCategoryIn(UUID, List<String>, Pageable)`, `findByReportIdAndStatusIn(UUID, List<String>, Pageable)`, combined filter query using `@Query` with optional severity/category/status parameters. |
| 542A.5 | Create `ComplianceAuditReportService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReportService.java` | 542A.6 | existing service pattern | `@Service`. Methods: `publishReport(ComplianceAuditOutput output, UUID executionId, UUID memberId)` -- creates `ComplianceAuditReport` (status=PUBLISHED), persists `ComplianceAuditFinding` entities for each finding (primary entity_type/entity_id from first entity reference), archives previous PUBLISHED report; `findReports(Pageable)` -- returns paginated reports with finding count summary; `findReport(UUID id)` -- single report with category scores; `findFindings(UUID reportId, FindingFilterParams)` -- filterable finding list; `updateFindingStatus(UUID reportId, UUID findingId, String newStatus, String resolutionNotes, UUID memberId)` -- validates transition via entity domain methods, emits `ComplianceFindingStatusChangedEvent`, emits audit event. Constructor injection of both repos + `ApplicationEventPublisher`. |
| 542A.6 | Integration tests for entities + service | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReportServiceTest.java` | ~8 tests: (1) `publishReport()` creates report with PUBLISHED status; (2) `publishReport()` creates findings from output; (3) `publishReport()` archives previous report; (4) finding OPEN -> ACKNOWLEDGED transition succeeds; (5) finding ACKNOWLEDGED -> IN_PROGRESS succeeds; (6) finding IN_PROGRESS -> RESOLVED requires resolution notes; (7) backward transition (RESOLVED -> OPEN) throws `InvalidStateException`; (8) finding IN_PROGRESS -> FALSE_POSITIVE sets resolved fields | standard integration test pattern | Create a mock `ComplianceAuditOutput` and verify full persistence chain. Test all valid and invalid status transitions. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 542B.1 | Create `ComplianceFindingStatusChangedEvent` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceFindingStatusChangedEvent.java` | covered by 542B.4 | existing event pattern in `AiGateApprovedEvent.java` | `public record ComplianceFindingStatusChangedEvent(UUID findingId, UUID reportId, String findingIdCode, String oldStatus, String newStatus, UUID changedBy) {}`. Published by `ComplianceAuditReportService.updateFindingStatus()`. Consumed by audit service for audit trail. |
| 542B.2 | Create response/request DTOs | included in `ComplianceAuditReportController.java` as nested records, or in a `dto/` sub-package | covered by 542B.4 | existing DTO pattern (nested records in controller) | `ComplianceAuditReportResponse(UUID id, String overallGrade, String overallAssessment, String status, Map<String, Object> categoryScores, FindingCounts findingCounts, Instant publishedAt, MemberSummary publishedBy)`. `ComplianceAuditFindingResponse(UUID id, String findingId, String severity, String category, String title, String description, String regulatoryBasis, String remediation, String entityType, UUID entityId, String entityName, String status, MemberSummary resolvedBy, Instant resolvedAt, String resolutionNotes)`. `UpdateFindingStatusRequest(String status, String resolutionNotes)`. `FindingCounts(int critical, int high, int medium, int low, int info)`. |
| 542B.3 | Create `ComplianceAuditReportController` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReportController.java` | 542B.4 | existing controller pattern with `@RequiresCapability`; thin-controller discipline from CLAUDE.md | `@RestController @RequestMapping("/api/compliance/audit-reports")`. Four endpoints: `GET /` (`AI_MANAGE`) -> `service.findReports(pageable)`; `GET /{id}` (`AI_MANAGE`) -> `service.findReport(id)`; `GET /{reportId}/findings` (`AI_MANAGE`) -> `service.findFindings(reportId, filterParams)`; `PATCH /{reportId}/findings/{findingId}` (`AI_REVIEW`) -> `service.updateFindingStatus(reportId, findingId, request.status(), request.resolutionNotes(), memberId)`. Each method is a one-liner delegating to the service. Filter params from `@RequestParam`: severity, category, status (comma-separated). |
| 542B.4 | Integration tests for controller | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReportControllerTest.java` | ~7 tests: (1) GET /audit-reports returns paginated list; (2) GET /audit-reports/{id} returns report with category scores; (3) GET /audit-reports/{reportId}/findings returns filterable list; (4) PATCH finding status transitions correctly; (5) PATCH finding with invalid transition returns 400; (6) GET /audit-reports requires AI_MANAGE (403 without); (7) PATCH finding requires AI_REVIEW (403 without) | standard `@SpringBootTest` + `MockMvc` pattern | Create test data (report + findings) in `@BeforeEach`. Test all four endpoints with valid and invalid inputs. Verify capability gating. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReport.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditFinding.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReportRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditFindingRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReportService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReportController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceFindingStatusChangedEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReportServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/ComplianceAuditReportControllerTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/execution/AiExecution.java` -- FK target for `execution_id`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiGateApprovedEvent.java` -- event record pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CompliancePackService.java` -- existing compliance package context
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateController.java` -- controller pattern with capability checks

### Architecture Decisions

- **Persistent findings with lifecycle** ([ADR-291](../adr/ADR-291-compliance-findings-persistent-lifecycle.md)) -- `ComplianceAuditFinding` is a dedicated entity with OPEN -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED / FALSE_POSITIVE lifecycle. Not tasks, not ephemeral output. Each transition is audited.
- **Report-scoped findings** -- findings are children of a report with cascading delete. `finding_id` uniqueness is scoped to the report, not globally.
- **Soft entity reference** -- `entity_type` + `entity_id` is a soft FK. No hard FK constraints across heterogeneous entity types.
- **No backward transitions** -- a resolved finding that recurs appears as a new finding in the next audit. Preserves audit trail.
- **Previous report archival** -- `publishReport()` archives the previous PUBLISHED report. Only one PUBLISHED report exists at a time.

### Non-scope

- No gate executor that calls `publishReport()` (lands in 543A).
- No compliance dashboard frontend (lands in 546A/546B).

---

## Epic 543: Gate Executors + StubAiProvider Extensions

**Goal**: Wire the three new gate types into the existing `GateAction` sealed interface and `GateActionExecutor`, add three new endpoints to `AiSkillController` for the new skills, and extend `StubAiProvider` with canned response routing for the three new skill IDs. This epic is the integration point that connects the independently-built skills to the existing execution infrastructure.

**References**: Architecture Section 11.4.1 (Skill Invocation), Section 11.4.3 (Gate Approval), Section 11.8.1 (Implementation Guidance); [ADR-288](../adr/ADR-288-contract-review-document-as-report.md) (gate for review report), [ADR-289](../adr/ADR-289-template-guided-drafting-over-freeform.md) (gate for draft), [ADR-291](../adr/ADR-291-compliance-findings-persistent-lifecycle.md) (gate for compliance report).

**Dependencies**: Epic 539A (`ContractReviewSkill`, `AiReviewReportGenerator`), Epic 539B (`AiReviewReportGenerator`), Epic 540A (`DraftingSkill`), Epic 540B (`AiDraftDocumentGenerator`), Epic 541B (`ComplianceAuditSkill`), Epic 542A (`ComplianceAuditReportService`).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **543A** | 543A.1-543A.6 | ~8 backend files (1 sealed interface modification + 1 executor modification + 1 controller modification + 3 gate executor classes + 1 test file + 1 stub extension) | `GateAction` 3 new permits; `GateActionExecutor` 3 new cases; `AiSkillController` 3 new POST endpoints; 3 `GateActionExecutor` implementations (contract review, drafting, compliance); `StubAiProvider` extensions; end-to-end integration tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 543A.1 | Extend `GateAction` sealed interface | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateAction.java` (modify) | covered by 543A.6 | existing permits pattern in `GateAction.java` | Add three new permits: `CreateReviewReportAction(UUID projectId, UUID documentId, Map<String, Object> reviewOutput)`, `CreateDraftDocumentAction(UUID templateId, UUID projectId, Map<String, Object> draftOutput)`, `PublishComplianceReportAction(Map<String, Object> auditOutput)`. Update the `permits` clause to include all six records. |
| 543A.2 | Extend `GateActionExecutor` with 3 new cases | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateActionExecutor.java` (modify) | 543A.6 | existing `execute()` switch pattern in `GateActionExecutor.java` | Add three new cases to the `switch` in `execute()`: `CreateReviewReportAction` -> calls `AiReviewReportGenerator.generateReviewReport()`; `CreateDraftDocumentAction` -> calls `AiDraftDocumentGenerator.generateDraft()`; `PublishComplianceReportAction` -> calls `ComplianceAuditReportService.publishReport()`. Add three new cases to `parseAction()` for gate types `"CREATE_REVIEW_REPORT"`, `"CREATE_DRAFT_DOCUMENT"`, `"PUBLISH_COMPLIANCE_REPORT"`. Constructor injection: add `AiReviewReportGenerator`, `AiDraftDocumentGenerator`, `ComplianceAuditReportService` to existing constructor. |
| 543A.3 | Create individual gate executor service classes | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/ContractReviewGateExecutor.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/DraftingGateExecutor.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceAuditGateExecutor.java` | 543A.6 | -- | **Alternative to 543A.2**: If the `GateActionExecutor` becomes too large (6+ cases), extract skill-specific gate execution logic into dedicated executor classes. Each class encapsulates the output parsing and service delegation for its gate type. The central `GateActionExecutor` dispatches to the appropriate executor by gate type. This is a judgment call at implementation time -- if the central executor remains manageable with inline switch cases, skip this task and keep logic in `GateActionExecutor`. |
| 543A.4 | Add 3 new endpoints to `AiSkillController` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillController.java` (modify) | 543A.6 | existing `POST /api/ai/skills/fica-verification` and `POST /api/ai/skills/matter-intake` endpoints in the same controller | Add: `POST /api/ai/skills/contract-review` (`AI_EXECUTE`) with body `ContractReviewRequest(UUID documentId, UUID projectId)`; `POST /api/ai/skills/drafting` (`AI_EXECUTE`) with body `DraftingRequest(UUID templateId, UUID projectId)`; `POST /api/ai/skills/compliance-audit` (`AI_EXECUTE`) with empty body `ComplianceAuditRequest()`. Each endpoint follows the exact pattern of existing endpoints: validate request, resolve skill bean, build `SkillContext`, call `executionService.executeSkill()`, return result DTO. Request body DTOs as nested records. For compliance audit: `entityType = "FIRM"`, `entityId = sentinel UUID (00000000-0000-0000-0000-000000000000)`. |
| 543A.5 | Extend StubAiProvider routing | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/testutil/StubAiProvider.java` (modify -- only if canned responses aren't already auto-discovered by skill ID) | covered by 543A.6 | existing `StubAiProvider` routing by `metadata.get("skill-id")` | Verify that `StubAiProvider` auto-discovers `test/resources/ai/stubs/contract-review/response.json`, `test/resources/ai/stubs/drafting/response.json`, `test/resources/ai/stubs/compliance-audit/response.json` via the existing `loadCannedResponse(skillId)` mechanism. The canned response files were created in 539A.5, 540A.5, 541B.5. No code change needed if the routing is already generic -- just verify. |
| 543A.6 | End-to-end integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillEndToEndTest.java` | ~9 tests: (1) POST contract-review creates execution + gate with CREATE_REVIEW_REPORT; (2) approve CREATE_REVIEW_REPORT gate creates document with AI_GENERATED source; (3) POST drafting creates execution + gate with CREATE_DRAFT_DOCUMENT; (4) approve CREATE_DRAFT_DOCUMENT gate creates document; (5) POST compliance-audit creates execution + gate with PUBLISH_COMPLIANCE_REPORT; (6) approve PUBLISH_COMPLIANCE_REPORT gate creates ComplianceAuditReport + findings; (7) reject gate is no-op (no document/report created); (8) all three endpoints require AI_EXECUTE (403 without); (9) gate approval requires AI_REVIEW (403 without) | standard `@SpringBootTest` + `MockMvc` pattern; uses `StubAiProvider` for canned responses | The definitive integration test: invoke skill via HTTP, verify execution created, verify gate created, approve gate via HTTP, verify downstream effect (document created or report published). Uses test fixtures for document (mocked S3), template, project, customer. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/contractreview/ContractReviewGateExecutor.java` (optional, see 543A.3)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/drafting/DraftingGateExecutor.java` (optional, see 543A.3)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/complianceaudit/ComplianceAuditGateExecutor.java` (optional, see 543A.3)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillEndToEndTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateAction.java` -- add 3 new permits
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateActionExecutor.java` -- add 3 new switch cases + constructor dependencies
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillController.java` -- add 3 new POST endpoints

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateService.java` -- approve/reject flow
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/testutil/StubAiProvider.java` -- canned response routing
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationSkillTest.java` -- integration test reference

### Architecture Decisions

- **Central `GateActionExecutor`** -- all gate types are dispatched from a single executor via pattern matching switch. This keeps gate approval centralized and avoids scattered executor resolution. If the executor grows too large (6+ cases after Phase 74), a strategy pattern refactor can be considered -- but for 6 cases, a switch is clearer.
- **Sealed interface guarantees exhaustiveness** -- adding new permits to `GateAction` forces the `switch` in `GateActionExecutor` to handle them (non-exhaustive switch warning in Java). Compile-time safety.
- **Controller endpoint pattern** -- new skill endpoints follow the exact pattern of existing FICA and matter intake endpoints. Each is a one-liner delegating to `AiSkillExecutionService.executeSkill()`. Request body DTOs as nested records in the controller.

### Non-scope

- No frontend (lands in 544A, 545A, 546A/546B).
- No execution history filter update (handled in frontend slices).

---

## Epic 544: Contract Review Frontend

**Goal**: Add the "Review with AI" button to the matter detail documents tab and build the contract review results panel that displays the AI's findings, risk assessment, and executive summary. Handle loading states, error states, and the gate approval flow.

**References**: Architecture Section 11.8.2 (Frontend Changes), Requirements Section 6.1 (Contract Review Trigger).

**Dependencies**: Epic 539A (contract review backend), Epic 543A (controller endpoint).

**Scope**: Frontend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **544A** | 544A.1-544A.5 | ~7 frontend files (2 new components + 1 page modification + 1 API client extension + 1 actions file + 1 type file + 1 existing component modification) | "Review with AI" button on document cards; contract review results panel; API hooks; server action for invocation; loading/error states. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 544A.1 | Extend AI API client for contract review | `frontend/lib/api/ai.ts` (extend) | -- | existing `invokeSkill()` pattern in `lib/api/ai.ts` (from 529B) | Add function: `invokeContractReview(documentId: string, projectId: string)` that calls `POST /api/ai/skills/contract-review` with `{ documentId, projectId }`. Add TypeScript types: `ContractReviewOutput`, `ContractReviewFinding`, `MissingProtection`, `DocumentClassification`, `RecommendedAction`. Return type: existing `SkillExecutionResult` with typed `output: ContractReviewOutput`. |
| 544A.2 | Create contract review results component | `frontend/components/ai/contract-review-results.tsx` | -- | existing `fica-result-display.tsx` for AI results rendering pattern | Component displaying: overall risk assessment badge (colored: HIGH=red, MEDIUM=yellow, LOW=green), executive summary text, finding count by severity, expandable finding list (grouped by severity: HIGH first), each finding showing clause reference, description, legal basis, recommendation. "Approve Report" button (calls gate approve) and "Reject" button. Uses Shadcn Card, Badge, Accordion, Button, AlertDialog. Props: `executionResult: SkillExecutionResult<ContractReviewOutput>`, `onApprove`, `onReject`. |
| 544A.3 | Create "Review with AI" button component | `frontend/components/ai/contract-review-button.tsx` | -- | existing `fica-verification-panel.tsx` for button + panel pattern | Button component ("Review with AI" with magnifying glass + sparkle icon) that: (1) checks prerequisites (AI configured, document is PDF/DOCX, member has AI_EXECUTE); (2) on click, shows loading state (~15-30s); (3) on success, renders `contract-review-results.tsx` panel below; (4) on error, shows error toast with message. Disabled with tooltip when prerequisites not met. Uses Shadcn Button, Tooltip, Spinner. Props: `documentId: string`, `projectId: string`, `documentFormat: string`. |
| 544A.4 | Modify matter documents tab | modification to existing matter detail documents tab component | -- | existing document list rendering in matter detail page | Add `contract-review-button.tsx` to each document card in the matter's documents tab. Only show for PDF/DOCX documents. Also add "Draft with AI" button placeholder (component from 545A). Modify the documents tab to import and render the new button alongside existing document actions. File path depends on current route structure -- likely `frontend/app/(app)/org/[slug]/projects/[id]/documents/` or a shared component in `components/projects/`. |
| 544A.5 | Create server action for contract review | `frontend/app/(app)/org/[slug]/projects/[id]/actions.ts` (extend or create) | -- | existing server action patterns in `ai/reviews/actions.ts` | Server action: `invokeContractReviewAction(documentId: string, projectId: string)` that calls `invokeContractReview()` from API client. Handles errors with toast messages. Revalidates the documents tab on gate approval. |

### Key Files

**Create (frontend):**
- `frontend/components/ai/contract-review-button.tsx`
- `frontend/components/ai/contract-review-results.tsx`

**Modify (frontend):**
- `frontend/lib/api/ai.ts` -- add `invokeContractReview()` function + types
- Matter detail documents tab component -- add "Review with AI" button
- `frontend/app/(app)/org/[slug]/projects/[id]/actions.ts` -- add server action

**Read for context:**
- `frontend/components/ai/fica-verification-panel.tsx` -- AI skill invocation + results pattern
- `frontend/components/ai/fica-result-display.tsx` -- AI output rendering pattern
- `frontend/components/ai/execution-gate-card.tsx` -- gate approve/reject pattern
- `frontend/lib/api/ai.ts` -- existing API client functions

### Architecture Decisions

- **Button on document cards** -- "Review with AI" appears on each individual document card (not as a bulk action). One document reviewed at a time per architecture scope.
- **Results panel inline** -- review results appear as an expandable panel below the document card, not in a separate page. This keeps the attorney in context.
- **Gate approval reuses existing flow** -- the "Approve Report" button creates a gate that appears in the AI Reviews page. No new gate UI -- reuses `execution-gate-card.tsx`.

### Non-scope

- No drafting dialog (lands in 545A).
- No compliance dashboard (lands in 546A/546B).

---

## Epic 545: Drafting Frontend

**Goal**: Build the multi-step drafting dialog triggered from the matter documents tab. The dialog includes template selection, AI processing (loading state), results presentation with editable variable fills (confidence-coded badges), narrative previews, clause recommendations, and a "Create Draft" button that triggers the execution gate.

**References**: Architecture Section 11.8.2 (Frontend Changes), Requirements Section 6.2 (Drafting Assistant Dialog), Section 2.6 (Frontend Flow).

**Dependencies**: Epic 540A (drafting backend), Epic 543A (controller endpoint).

**Scope**: Frontend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **545A** | 545A.1-545A.6 | ~8 frontend files (3 new components + 1 page modification + 1 API client extension + 1 actions file + 1 type file + 1 Zod schema) | Drafting dialog (template selector + AI processing + results); drafting variable table with confidence badges; template selector component; API hooks; server action. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 545A.1 | Extend AI API client for drafting | `frontend/lib/api/ai.ts` (extend) | -- | existing API client pattern | Add function: `invokeDrafting(templateId: string, projectId: string)` that calls `POST /api/ai/skills/drafting` with `{ templateId, projectId }`. Add TypeScript types: `DraftingOutput`, `VariableFill`, `NarrativeSection`, `ClauseRecommendation`. Return type: existing `SkillExecutionResult` with typed `output: DraftingOutput`. |
| 545A.2 | Create drafting variable table component | `frontend/components/ai/drafting-variable-table.tsx` | -- | existing form table patterns | Table component displaying variable fills: variable name, current value (editable input), source label, confidence badge (HIGH=green, MEDIUM=yellow, LOW/UNDETERMINED=red). Each row is editable -- attorney can override the AI-suggested value. Flag text shown for UNDETERMINED variables. Uses Shadcn Table, Input, Badge, Tooltip. Props: `variableFills: VariableFill[]`, `onChange: (fills: VariableFill[]) => void`. Sorting: UNDETERMINED first, then LOW, MEDIUM, HIGH (surface items needing attention). |
| 545A.3 | Create drafting dialog component | `frontend/components/ai/drafting-dialog.tsx` | -- | existing multi-step dialog patterns in the codebase | Multi-step dialog: **Step 1**: template selector (dropdown of available templates, fetched from template API, filtered by relevance to matter type). **Step 2**: loading state during AI processing (~10-20s), with progress indicator. **Step 3**: results panel showing: variable fills table (component from 545A.2), narrative section previews (collapsible accordions), clause recommendations (checkboxes with clause name + reasoning), warnings (alert banners). "Create Draft" button at bottom. **Step 4**: confirmation dialog ("Create draft document?") -> server action -> gate created. Uses Shadcn Dialog, Select, Accordion, Checkbox, Alert, Button. Props: `projectId: string`, `isOpen: boolean`, `onClose: () => void`. |
| 545A.4 | Create Zod schema for variable edits | `frontend/lib/schemas/drafting.ts` | -- | existing `lib/schemas/ai-profile.ts` for schema pattern | Schema validating the edited variable fills before submission: `variableFills` array with `variableName` (required string), `value` (nullable string), `source` (string), `confidence` (enum). Used by the dialog to validate before creating the gate. |
| 545A.5 | Add "Draft with AI" button to matter documents tab | modification to existing matter detail documents tab component | -- | same location as 544A.4 modification | Add "Draft with AI" button (sparkle icon + "Draft with AI" text) to the matter documents tab header (alongside existing "Upload" and "Generate" buttons). On click, opens the drafting dialog. Disabled with tooltip when prerequisites not met (no AI config, no templates, no AI_EXECUTE capability). |
| 545A.6 | Create server action for drafting | `frontend/app/(app)/org/[slug]/projects/[id]/actions.ts` (extend) | -- | existing server action patterns | Server action: `invokeDraftingAction(templateId: string, projectId: string)` that calls `invokeDrafting()` from API client. Returns the execution result for the dialog to render. Handles errors with toast messages. |

### Key Files

**Create (frontend):**
- `frontend/components/ai/drafting-dialog.tsx`
- `frontend/components/ai/drafting-variable-table.tsx`
- `frontend/lib/schemas/drafting.ts`

**Modify (frontend):**
- `frontend/lib/api/ai.ts` -- add `invokeDrafting()` function + types
- Matter detail documents tab component -- add "Draft with AI" button
- `frontend/app/(app)/org/[slug]/projects/[id]/actions.ts` -- add server action

**Read for context:**
- `frontend/components/ai/fica-verification-panel.tsx` -- skill invocation UX pattern
- `frontend/components/ai/contract-review-results.tsx` -- AI results rendering (from 544A)
- Existing template selector components if any
- `frontend/lib/api/ai.ts` -- existing API functions

### Architecture Decisions

- **Multi-step dialog** -- the drafting flow has multiple UX stages (select template, wait for AI, review results, confirm). A dialog with step progression is the right container, not a full page.
- **Editable variable fills** -- the attorney can override any AI-suggested value. This is critical for the Attorneys Act liability framework: the attorney is responsible for all content.
- **Confidence badges** -- color-coded per variable fill. GREEN (HIGH) = from structured data, YELLOW (MEDIUM) = AI-inferred, RED (LOW/UNDETERMINED) = needs manual input.
- **Clause recommendations as checkboxes** -- clauses from the firm's library are recommended, not generated. The attorney toggles which clauses to include.

### Non-scope

- No Tiptap editor integration for the generated draft (the draft opens in the existing Tiptap editor via the standard document view).
- No inline AI annotations on the draft.

---

## Epic 546: Compliance Dashboard Extension (Frontend)

**Goal**: Extend the existing compliance dashboard (`/compliance`) with an "AI Audit" tab that displays the "Run AI Audit" button, audit results (grade badge, category scores, finding counts), audit history, and a detailed finding list with severity/category/status filters and a resolution workflow dialog.

**References**: Architecture Section 11.8.2 (Frontend Changes), Requirements Section 6.3 (Compliance Dashboard Extension), Section 3.7 (Dashboard Extension).

**Dependencies**: Epic 542A (compliance audit report backend), Epic 542B (report controller), Epic 543A (skill endpoint).

**Scope**: Frontend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **546A** | 546A.1-546A.5 | ~7 frontend files (3 new components + 1 page modification + 1 API client file + 1 actions file + 1 type file) | "AI Audit" tab on compliance dashboard; "Run AI Audit" button; audit summary (grade, category scores); audit history panel; API hooks. |
| **546B** | 546B.1-546B.4 | ~5 frontend files (2 new components + 1 API client extension + 1 actions extension + 1 type extension) | Finding list with filters; finding detail dialog with resolution workflow; status transition actions. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 546A.1 | Create compliance audit API client | `frontend/lib/api/compliance-audit.ts` | -- | existing `lib/api/ai.ts` for pattern | Functions: `invokeComplianceAudit()` that calls `POST /api/ai/skills/compliance-audit` with empty body; `getAuditReports(page, size)` calling `GET /api/compliance/audit-reports`; `getAuditReport(id)` calling `GET /api/compliance/audit-reports/{id}`. TypeScript types: `ComplianceAuditReportResponse`, `ComplianceAuditOutput`, `CategoryScore`, `FindingCounts`, `AuditFinding`, `Recommendation`. |
| 546A.2 | Create audit summary component | `frontend/components/ai/compliance-audit-summary.tsx` | -- | existing dashboard card patterns in compliance pages | Component displaying: overall grade badge (large, color-coded: A/B=green, C=yellow, D/F=red), overall assessment text, category score table (category name, grade, compliant/non-compliant counts), finding count badges by severity, published date and publisher name. Uses Shadcn Card, Badge, Table. Props: `report: ComplianceAuditReportResponse`. Empty state: "No audits yet. Run an AI audit to assess your compliance posture." |
| 546A.3 | Create audit history component | included in compliance audit tab (list of past audit cards) | -- | existing history/list patterns | Section within the audit tab showing a list of past audit report cards: date, overall grade badge, finding count summary. Click to expand and view full details. Most recent audit at top. Uses Shadcn Card with compact layout. |
| 546A.4 | Create compliance audit tab component | `frontend/components/ai/compliance-audit-tab.tsx` | -- | existing tab patterns on compliance page | Tab component for the "AI Audit" tab on the compliance dashboard. Layout: (1) header with "Run AI Audit" primary button (disabled during in-progress audit, shows loading spinner during execution ~30-60s); (2) latest audit summary (component from 546A.2); (3) audit history (list of past audits). Server component that fetches audit reports. "Run AI Audit" button triggers server action, shows loading state, on completion refreshes to show results. Button disabled with tooltip when: AI not configured, no AI_EXECUTE capability. |
| 546A.5 | Modify compliance dashboard page + create server action | `frontend/app/(app)/org/[slug]/compliance/page.tsx` (modify), `frontend/app/(app)/org/[slug]/compliance/actions.ts` (extend) | -- | existing tab addition patterns | Add "AI Audit" tab to the existing compliance dashboard page alongside existing tabs. Import and render `compliance-audit-tab.tsx` when the tab is active. Server action: `invokeComplianceAuditAction()` calls `invokeComplianceAudit()` API function and revalidates the page. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 546B.1 | Extend API client for findings | `frontend/lib/api/compliance-audit.ts` (extend) | -- | existing API client pattern | Add functions: `getAuditFindings(reportId, filters)` calling `GET /api/compliance/audit-reports/{reportId}/findings` with query params (severity, category, status); `updateFindingStatus(reportId, findingId, status, resolutionNotes)` calling `PATCH /api/compliance/audit-reports/{reportId}/findings/{findingId}`. Add types: `ComplianceAuditFindingResponse`, `UpdateFindingStatusRequest`. |
| 546B.2 | Create finding list component | `frontend/components/ai/compliance-finding-list.tsx` | -- | existing filterable table patterns (e.g., audit log page) | Table component with columns: severity badge, category tag, finding ID, title, entity link (navigable), status dropdown. Filter bar above: severity multi-select (CRITICAL, HIGH, MEDIUM, LOW, INFO), category multi-select (FICA_CDD, POPIA, etc.), status multi-select (OPEN, ACKNOWLEDGED, etc.). Click row to open finding detail dialog. Pagination. Uses Shadcn Table, Select, Badge, Button. Props: `reportId: string`. |
| 546B.3 | Create finding detail dialog | `frontend/components/ai/compliance-finding-detail.tsx` | -- | existing detail dialog patterns | Dialog showing full finding details: severity badge, category, finding ID, title, description (full text), regulatory basis (if present), remediation recommendation, entity link (navigable to customer/project), current status with transition buttons. Status transitions: if OPEN, show "Acknowledge" button; if ACKNOWLEDGED, show "Start Progress" button; if IN_PROGRESS, show "Resolve" and "Mark False Positive" buttons (both require resolution notes textarea). Uses Shadcn Dialog, Badge, Button, Textarea, AlertDialog (confirmation before transition). Props: `finding: ComplianceAuditFindingResponse`, `reportId: string`, `onStatusChange: () => void`. |
| 546B.4 | Create server actions for finding status changes | `frontend/app/(app)/org/[slug]/compliance/actions.ts` (extend) | -- | existing action patterns | Server actions: `acknowledgeFindingAction(reportId, findingId)`, `startProgressAction(reportId, findingId)`, `resolveFindingAction(reportId, findingId, notes)`, `markFalsePositiveAction(reportId, findingId, notes)`. Each calls `updateFindingStatus()` from API client with the appropriate status. Revalidates the findings list. Error handling for invalid transitions (shows toast with error message). |

### Key Files

**Create (frontend):**
- `frontend/lib/api/compliance-audit.ts`
- `frontend/components/ai/compliance-audit-tab.tsx`
- `frontend/components/ai/compliance-audit-summary.tsx`
- `frontend/components/ai/compliance-finding-list.tsx`
- `frontend/components/ai/compliance-finding-detail.tsx`

**Modify (frontend):**
- `frontend/app/(app)/org/[slug]/compliance/page.tsx` -- add "AI Audit" tab
- `frontend/app/(app)/org/[slug]/compliance/actions.ts` -- add server actions for audit invocation + finding status changes

**Read for context:**
- `frontend/app/(app)/org/[slug]/compliance/page.tsx` -- existing compliance dashboard structure
- `frontend/app/(app)/org/[slug]/compliance/actions.ts` -- existing compliance actions
- `frontend/components/ai/execution-gate-card.tsx` -- gate approval UI pattern
- `frontend/app/(app)/org/[slug]/settings/ai/history/page.tsx` -- execution history table pattern
- `frontend/components/ai/fica-result-display.tsx` -- AI output rendering pattern

### Architecture Decisions

- **Tab extension, not new page** -- the AI audit is added as a tab on the existing compliance dashboard, not a separate route. This keeps compliance information unified.
- **Finding resolution workflow in dialog** -- status transitions happen in a detail dialog, not inline in the table. This allows space for resolution notes and confirmation.
- **Status transition validation** -- the frontend enforces valid transitions (only shows applicable buttons). The backend also validates, providing defense in depth.
- **Entity navigation links** -- findings with `entityType` and `entityId` link to the relevant customer or project page. If the entity doesn't exist (deleted), the link is disabled with a note.

### Non-scope

- No execution history filter update for new skill types (the existing history page handles new skills generically via the skill type filter dropdown -- no code change needed if the dropdown is dynamic).
- No AI audit scheduling UI (on-demand only per ADR-290).
- No finding assignment to team members (v1 limitation per ADR-291).

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateAction.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateActionExecutor.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillController.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/Document.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationSkill.java`