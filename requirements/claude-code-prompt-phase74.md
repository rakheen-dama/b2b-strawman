# Phase 74 — AI Intelligence Suite: Contract Review, Drafting & Compliance Audit

## System Context

Kazi is a multi-tenant B2B practice-management platform with three live verticals (legal-za, accounting-za, consulting-za). Phase 74 deepens the AI layer by shipping three new skills that transform existing data into intelligence: reviewing uploaded contracts, generating first-draft legal documents from templates, and auditing the client book for compliance gaps.

**AI positioning**: Phase 72 established the AI-native identity — "AI skills embedded in the system of record beat bolt-on tools because the data is already there." Phase 74 delivers on that promise. A contract review skill that reads from the matter, a drafting skill that fills templates with matter context, and a compliance audit that sweeps the entire client book — none of these are possible from an external tool.

### Predecessor systems this phase builds on

- **Phase 72 — AI Foundation** (PRs #1313–#1324) — `AiProvider` interface, `AnthropicAiProvider`, `AiFirmProfile`, `AiExecution`, `AiExecutionGate`, `AiCostService`, `AiSkillExecutionService`, skill execution infrastructure, `StubAiProvider` for tests. The entire skill lifecycle (invoke → execute → audit → gate → approve/reject) is built.
- **Phase 70 — Specialist AI Assistants** (PRs #1290–#1301) — `Specialist` framework, `SpecialistRegistry`, `NonInteractiveSpecialistRunner`, inline launcher, `AiSkillInvocation` entity, automation hook, review queue. Billing, Intake, and Inbox specialists are live.
- **Phase 52 — In-App AI Assistant** (PRs #802–#812) — `LlmProvider` abstraction, tool framework, read/write tools, `AssistantService`, chat API. The conversational assistant is a separate system from skills but shares the provider infrastructure.
- **Phase 12/31/42 — Document System** (rich editor, templates, clauses, DOCX pipeline) — `DocumentTemplate`, `Clause`, `TiptapRenderer`, `DocxMergeService`, variable metadata, generation pipeline. The drafting skill generates documents through this pipeline.
- **Phase 27 — Document Clauses** — `Clause` entity, template-clause associations, clause packs. The drafting skill can include relevant clauses.
- **Phase 14 — Customer Compliance & Lifecycle** — `ComplianceChecklist`, `ComplianceChecklistItem`, `DataSubjectRequest`, `CustomerLifecycleStatus`. The compliance audit reads checklist completion status.
- **Phase 60 — Trust Accounting** — `TrustAccount`, `TrustTransaction`, `ClientLedgerCard`, approval workflow, reconciliation. The compliance audit verifies §86 boundary integrity.
- **Phase 55 — Legal Foundations** — Conflict check, LSSA tariff, court calendar, prescription tracker. The compliance audit reads prescription data; the drafting skill can reference tariff data.
- **Phase 61 — KYC Adapter** — `KycAdapter` port, KYC verification dialog, checklist integration. The compliance audit includes KYC verification status.
- **Phase 41/46 — RBAC** — `@RequiresCapability`, capability resolution. Existing `AI_MANAGE`, `AI_EXECUTE`, `AI_REVIEW` capabilities cover all three new skills.

### claude-for-legal-sa as knowledge source

The fork at `../claude-for-legal-sa` provides SA-specific legal knowledge that powers the contract review prompts:
- **Commercial topics** (`jurisdictions/za/commercial-legal/topics/` — 6 files): contract interpretation, restraint of trade, consumer protection, electronic transactions.
- **Employment topics** (`jurisdictions/za/employment-legal/topics/` — 6 files): dismissal, CCMA, BCEA, EEA.
- **Corporate topics** (`jurisdictions/za/corporate-legal/topics/` — 5 files): Companies Act, director duties, BEE.
- **Statute YAMLs** (`jurisdictions/za/statutes/` — 47 files): structured statute data with section references, thresholds, effective dates.

This knowledge is baked into system prompts at build time, not loaded at runtime. No runtime dependency on the claude-for-legal-sa repo.

### What is missing today

- **No contract/document review capability.** Attorneys upload contracts to matters but must manually review every clause, flag risks, and annotate. No structured risk analysis, no jurisdiction-specific checks, no severity ranking. A 4-attorney firm reviewing 20+ contracts/month spends hours on document review that AI could accelerate.
- **No intelligent document drafting.** Document templates (Phase 12/42) require manual variable filling. Attorneys copy-paste from previous matters, adapt clauses, and write narrative sections from scratch. The AI could fill variables from matter context and generate narrative sections using firm style.
- **No compliance audit tool.** Compliance status lives across multiple systems — FICA checklists, POPIA processing activities, trust accounting ledgers, prescription trackers, retention policies — but there's no unified view that answers "are we compliant across the board?" Firms discover gaps during LSSA inspections, not proactively.

### Founder decisions that constrain this phase (2026-05-23 ideation)

- **Contract review produces a document-as-report**, not inline annotations. Fits the existing document system. No Tiptap annotation extension work.
- **Drafting is template-guided**, not freeform. The attorney selects a template; the AI fills variable fields and generates narrative sections. Template provides structure and guardrails.
- **Compliance audit is on-demand only**, not scheduled. "Run audit" button on the compliance dashboard. No automated sweep scheduler in v1.
- **Skill-per-task architecture** (Phase 72 decision). Each skill has focused I/O, its own prompt, and plugs into the existing `AiSkill` interface.
- **Execution gates mandatory** (Phase 72 decision). Every action proposed by a skill requires attorney approval.
- **BYOAK model** (Phase 72 decision). Tenant's own Anthropic API key. No platform-subsidised tokens.
- **Legal-za first**. All three skills target SA legal practice. Infrastructure is vertical-agnostic.
- **No new capabilities needed.** Existing `AI_MANAGE`, `AI_EXECUTE`, `AI_REVIEW` cover all three skills.

## Objective

Ship three AI skills — **contract/document review**, **template-guided drafting**, and **on-demand compliance audit** — that plug into the existing Phase 72 skill infrastructure and transform Kazi's data advantage into actionable intelligence. Each skill reads from the system of record, produces structured output, and routes proposed actions through execution gates for attorney approval.

## Constraints & Assumptions

- **Schema-per-tenant only** (ADR-T001). New tables live under `tenant/`. Next migration: `V127`.
- **Anthropic Claude API only.** Reuses `AnthropicAiProvider` from Phase 72. Model default from `AiFirmProfile.preferred_model`.
- **No streaming UI.** Skill invocation → result display. Same pattern as Phase 72.
- **No vector database / RAG.** Skills operate on structured data + document text extraction. No embedding pipeline.
- **No persistent conversation.** Each invocation is stateless — prompt includes all context.
- **Document text extraction** reuses the pattern from the FICA skill (Phase 72): PDF → text via `DocumentTextExtractor`, DOCX → text via existing `DocxMergeService` field discovery.
- **Prompt caching** on system prompt (firm profile block) — same as Phase 72.
- **Token limits**: contract review may process large documents. Max document size: 100KB of extracted text per document (roughly 25K tokens). Documents exceeding this are truncated with a warning.
- **Cost metering** via existing `AiCostService`. No changes to the metering infrastructure.
- **Test strategy**: `StubAiProvider` for CI (canned responses). Manual QA with real API key for prompt quality. Same as Phase 72.

---

## Section 1 — Skill 1: Contract/Document Review

### 1.1 Trigger

Invoked from the **matter detail page**, documents tab. A "Review with AI" button appears on uploaded documents (PDF, DOCX). Only enabled when:
- AI is configured (API key set, firm profile exists with `cold_start_completed = true`)
- The document has extractable content (PDF or DOCX, not an image-only scan without OCR)
- The invoking member has `AI_EXECUTE` capability
- The document size is under the extraction limit

### 1.2 Input assembly

The skill assembles context from:

1. **Document content** — extracted text from the uploaded document (PDF → text, DOCX → text). Truncated to 100KB with a warning if larger.
2. **Matter context** — matter name, description, matter type, customer name, customer type, opposing party (if set), jurisdiction.
3. **Firm AI profile** — practice areas, jurisdiction, risk calibration, house style notes.
4. **Review type** — auto-detected from document content and matter type:
   - `COMMERCIAL_CONTRACT` — sale, supply, service, lease, licence agreements
   - `EMPLOYMENT_CONTRACT` — employment, contractor, restraint of trade
   - `CORPORATE_DOCUMENT` — shareholder, MOI, resolutions
   - `GENERAL` — fallback when type cannot be determined

### 1.3 Prompt design

System prompt includes:
- Role: "You are a contract review assistant for a South African law firm."
- SA legal framework relevant to the detected review type — sourced from claude-for-legal-sa topic files:
  - Commercial: Consumer Protection Act 68/2008, Electronic Communications and Transactions Act, common law contract principles (Shifren principle, specific performance vs damages, no consideration requirement)
  - Employment: BCEA, LRA, EEA, restraint of trade case law (Basson v Chilwan test)
  - Corporate: Companies Act 71/2008, King IV principles, BEE Act
- The firm's risk calibration (conservative → flag more, flag ambiguities; moderate → flag material risks; aggressive → flag only critical issues)
- Output format specification (structured JSON → review report)
- Instruction: produce findings organized by severity, with specific clause references, SA statutory cross-references, and recommended amendments

User prompt includes:
- Document text content
- Matter context (name, type, customer, jurisdiction)
- Instruction: "Review this document and identify legal risks, missing protections, non-standard clauses, and jurisdiction-specific compliance issues."

### 1.4 Output format

```json
{
  "document_classification": {
    "type": "COMMERCIAL_CONTRACT | EMPLOYMENT_CONTRACT | CORPORATE_DOCUMENT | GENERAL",
    "subtype": "Service Agreement",
    "parties_identified": ["Acme Corp (Pty) Ltd", "Beta Services CC"]
  },
  "executive_summary": "A service agreement with standard terms. Three high-severity findings: missing POPIA data processing addendum, one-sided indemnity clause, and no dispute resolution mechanism.",
  "findings": [
    {
      "severity": "HIGH | MEDIUM | LOW | INFO",
      "category": "MISSING_CLAUSE | NON_STANDARD | COMPLIANCE_RISK | AMBIGUITY | FAVOURABLE",
      "clause_reference": "Clause 12.3",
      "title": "One-sided indemnity clause",
      "description": "The indemnity clause in 12.3 requires the client to indemnify the service provider against all losses including those arising from the provider's own negligence. This is unenforceable under SA common law (Afrox Healthcare v Strydom) and commercially unreasonable.",
      "risk_explanation": "If challenged, this clause would likely be struck down, leaving the client with no contractual indemnity protection at all.",
      "recommendation": "Amend to mutual indemnity with exclusion for wilful misconduct and gross negligence.",
      "statutory_reference": "Consumer Protection Act s48(1)(c) — unconscionable terms"
    }
  ],
  "missing_protections": [
    {
      "protection": "POPIA Data Processing Agreement",
      "reasoning": "The agreement involves processing of personal information (client employee data for service delivery) but contains no data processing addendum or POPIA compliance clauses.",
      "recommendation": "Add a data processing addendum per POPIA s19-22 operator obligations.",
      "priority": "HIGH"
    }
  ],
  "overall_risk_assessment": "MEDIUM",
  "recommended_actions": [
    {
      "action": "CREATE_REVIEW_REPORT",
      "reasoning": "Generate a structured review report document attached to this matter for the reviewing attorney."
    }
  ]
}
```

### 1.5 Execution gates

One gate per review:

- `gate_type = CREATE_REVIEW_REPORT`
- `proposed_action`: create a new Document entity (type: `AI_REVIEW_REPORT`) attached to the matter, containing the structured review report rendered as a Tiptap document.
- On approval: the system creates the review report document with findings formatted as sections (executive summary, high/medium/low findings, missing protections, recommended amendments). The document is linked to the matter and the original reviewed document.
- The review report is a real Document entity — it appears in the matter's documents tab, can be shared, downloaded as PDF, and sent to the client.

### 1.6 Review report document format

The generated document uses a structured template:

```
# Contract Review Report
## [Matter Name] — [Document Name]
### Reviewed: [Date] | Reviewer: AI-Assisted (approved by [Attorney Name])

## Executive Summary
[executive_summary text]

## High-Severity Findings
### 1. [title] (Clause [reference])
**Risk**: [description]
**Legal basis**: [statutory_reference]
**Recommendation**: [recommendation]

## Medium-Severity Findings
...

## Missing Protections
...

## Overall Assessment
Risk level: [overall_risk_assessment]
```

The report is rendered as Tiptap JSON and stored as a Document with `format = TIPTAP`. The existing document preview and PDF export pipelines handle display and download.

### 1.7 Error handling

- Document too large (>100KB extracted text): skill runs on truncated text with a warning in the output.
- Document unreadable (encrypted PDF, image-only without OCR, corrupted): execution fails with `UNSUPPORTED_DOCUMENT` error, clear message to the user.
- Document type not recognized: proceeds with `GENERAL` classification and broader review criteria.
- API error (timeout, rate limit): same as Phase 72 pattern.

---

## Section 2 — Skill 2: Template-Guided Drafting Assistant

### 2.1 Trigger

Invoked from two entry points:

1. **Matter detail page → Documents tab → "Draft with AI" button** — opens a dialog for template selection and AI-guided drafting.
2. **Document generation dialog** — an additional "AI Fill" button alongside the existing manual variable filling flow.

Only enabled when:
- AI is configured (API key set, firm profile exists)
- At least one document template exists in the tenant
- The invoking member has `AI_EXECUTE` capability
- A matter is selected (the skill needs matter context)

### 2.2 Input assembly

The skill assembles context from:

1. **Selected template** — template name, description, template content (Tiptap JSON or DOCX structure), defined variables (from `DocumentTemplate.variables` metadata), associated clauses.
2. **Matter context** — matter name, description, matter type, status, tasks (names + statuses), time entries summary, custom field values.
3. **Customer context** — name, type (individual/company/trust), contact details, address, registration/ID numbers, custom field values, lifecycle status.
4. **Firm AI profile** — house style notes, jurisdiction, practice areas, fee estimation notes.
5. **Clause library** — available clauses from the tenant's clause library, filtered by relevance to the template and matter type.
6. **Previous documents** (optional) — if the matter has existing documents of the same template type, the skill can reference them for consistency (e.g., prior engagement letter as style reference).

### 2.3 Prompt design

System prompt includes:
- Role: "You are a legal document drafting assistant for a South African law firm."
- The firm's house style notes and preferred terminology from the AI profile.
- SA legal drafting conventions: Plain Language principles (Consumer Protection Act s22), party naming conventions, execution block formats, jurisdiction-specific boilerplate.
- Template structure and variable metadata — which fields are available and what they mean.
- Output format specification: for each template variable, provide a value; for each narrative section marker, provide draft text.

User prompt includes:
- Template structure with variable placeholders
- Matter and customer context
- Available clauses (names + summaries)
- Instruction: "Fill the template variables from the matter/customer context and draft narrative sections. Use the firm's house style. Flag any variables that cannot be determined from available data."

### 2.4 Output format

```json
{
  "template_id": "uuid",
  "variable_fills": [
    {
      "variable_name": "client_name",
      "value": "Ndlovu Trading (Pty) Ltd",
      "source": "customer.name",
      "confidence": "HIGH"
    },
    {
      "variable_name": "matter_description",
      "value": "Commercial lease dispute regarding premises at 42 Commissioner Street, Johannesburg",
      "source": "AI_GENERATED",
      "confidence": "MEDIUM"
    },
    {
      "variable_name": "fee_estimate",
      "value": null,
      "source": "UNDETERMINED",
      "confidence": "LOW",
      "flag": "Fee estimate requires attorney input — matter complexity not fully assessed."
    }
  ],
  "narrative_sections": [
    {
      "section_name": "scope_of_engagement",
      "content": "We confirm our appointment to act on your behalf in connection with the commercial lease dispute at the above premises. Our mandate includes...",
      "notes": "Adapted from firm's standard engagement scope. Attorney should verify the scope boundaries."
    }
  ],
  "clause_recommendations": [
    {
      "clause_id": "uuid",
      "clause_name": "Standard Limitation of Liability",
      "reasoning": "Recommended for engagement letters — limits firm's liability to fees paid."
    }
  ],
  "warnings": [
    "Customer address is not on file — the template's address block will be empty. Recommend updating customer record."
  ],
  "recommended_actions": [
    {
      "action": "CREATE_DRAFT_DOCUMENT",
      "reasoning": "Generate a draft document with variables filled and narrative sections populated."
    }
  ]
}
```

### 2.5 Execution gates

One gate per drafting invocation:

- `gate_type = CREATE_DRAFT_DOCUMENT`
- `proposed_action`: create a new Document entity with the template applied, variables filled, and narrative sections populated. The document opens in the Tiptap editor for attorney review and editing.
- On approval: the system creates the document using the existing generation pipeline (`DocumentGenerationService`), with AI-provided variable values and narrative content injected. The attorney sees the draft in the editor with AI-generated content highlighted (or marked with a subtle indicator).
- The attorney edits the draft in the Tiptap editor — full editorial control before the document is finalised.

### 2.6 Frontend flow

1. Attorney clicks "Draft with AI" on the matter's documents tab.
2. Dialog opens: template selector (dropdown of available templates).
3. Attorney selects template → AI skill is invoked (loading state, ~10-20 seconds).
4. Results panel shows:
   - Variable fills with confidence indicators (green = HIGH, yellow = MEDIUM, red = LOW/UNDETERMINED)
   - Narrative section previews
   - Clause recommendations
   - Warnings about missing data
5. Attorney reviews, modifies variable values if needed, selects/deselects clauses.
6. "Create Draft" button → execution gate created → notification to reviewer.
7. On gate approval → document created, opens in Tiptap editor.

### 2.7 Error handling

- No templates available: skill disabled with tooltip "Create a document template first."
- Missing customer data (no address, no ID): skill proceeds but flags missing variables as `UNDETERMINED` with specific recommendations to update the customer record.
- Template too complex (>50 variables): skill runs but may produce lower-quality fills for late variables. Warning included.
- API error: same as Phase 72 pattern.

---

## Section 3 — Skill 3: On-Demand Compliance Audit

### 3.1 Trigger

Invoked from the **compliance dashboard** (existing Phase 14 page). A new "Run AI Audit" button triggers a firm-wide compliance sweep. Only enabled when:
- AI is configured (API key set, firm profile exists)
- The invoking member has `AI_EXECUTE` capability
- No audit is currently in progress for this tenant (prevent concurrent audits)

### 3.2 Input assembly

The skill assembles a comprehensive snapshot of the firm's compliance posture:

1. **Customer compliance data** (all active customers):
   - Customer name, type, lifecycle status, onboarding date
   - Compliance checklist completion: which items are complete, incomplete, overdue
   - Last CDD (Customer Due Diligence) review date
   - KYC verification status (from Phase 61)
   - Documents on file: which FICA documents exist, their upload dates
2. **POPIA processing activities** (from Phase 50):
   - Registered processing activities
   - Data retention schedules and compliance status
   - DSAR (Data Subject Access Request) history: pending, completed, overdue
3. **Trust accounting integrity** (from Phase 60, legal-za only):
   - Trust account balances
   - Recent transactions that cross the trust-business boundary
   - Unreconciled items older than 30 days
   - LPFF (Legal Practitioners' Fidelity Fund) contribution status
4. **Prescription tracking** (from Phase 55, legal-za only):
   - Matters with prescription dates approaching (within 90 days)
   - Matters with expired prescription (should have been actioned)
5. **Record retention** (from Phase 50):
   - Records approaching retention expiry
   - Records past retention period but not yet archived/destroyed
6. **Firm AI profile** — jurisdiction, risk calibration, practice areas, FICA requirements.

**Data volume management**: the skill summarises data rather than sending raw records. For example, instead of sending 200 customer records, it sends aggregated statistics with outliers highlighted:
- "150 customers with complete CDD, 35 with incomplete (17 overdue by >90 days), 15 new customers pending first review"
- Individual customer details only for flagged items (overdue, non-compliant, high-risk)

### 3.3 Prompt design

System prompt includes:
- Role: "You are a compliance audit assistant for a South African law firm."
- SA regulatory framework:
  - FICA Act 38/2001: CDD obligations, enhanced due diligence triggers, record retention (s22 — 5 years), RMCP obligations (s42), reporting thresholds (R25k cash, R50m sanctions)
  - POPIA Act 4/2013: processing activity registration, DSAR response timelines (30 days), data retention principles
  - Attorneys Act s78 / Rule 54: trust accounting obligations, LPFF contributions, audit requirements
  - Prescription Act 68/1969: prescription periods by claim type (3 years general, 6 years debt, 15 years mortgage bond)
- The firm's specific requirements from the AI profile (enhanced due diligence thresholds, risk calibration).
- Output format specification: severity-ranked findings with remediation recommendations.

User prompt includes:
- Aggregated compliance data snapshot (see 3.2 above)
- Instruction: "Audit this firm's compliance posture across FICA, POPIA, trust accounting, prescription tracking, and record retention. Identify gaps, rank by severity, and recommend specific remediation actions."

### 3.4 Output format

```json
{
  "audit_date": "2026-05-23",
  "overall_grade": "B",
  "overall_assessment": "The firm has strong CDD compliance for individual clients but significant gaps in trust client verification and POPIA processing activity registration. Two matters have prescription dates within 60 days requiring urgent attention.",
  "category_scores": {
    "fica_cdd": { "grade": "B+", "compliant": 150, "non_compliant": 35, "critical": 5 },
    "popia": { "grade": "C", "compliant": 12, "non_compliant": 8, "critical": 2 },
    "trust_accounting": { "grade": "A-", "compliant": 18, "non_compliant": 2, "critical": 0 },
    "prescription": { "grade": "B", "compliant": 45, "non_compliant": 3, "critical": 2 },
    "record_retention": { "grade": "B+", "compliant": 200, "non_compliant": 15, "critical": 0 }
  },
  "findings": [
    {
      "id": "F001",
      "severity": "CRITICAL",
      "category": "PRESCRIPTION",
      "title": "Two matters approaching prescription with no activity",
      "description": "Matter 'Dlamini v City of Joburg' (delict) has a prescription date of 2026-07-15 and no activity in the last 90 days. Matter 'Nkosi Estate' (debt recovery) prescribes on 2026-08-01 with no issued summons.",
      "regulatory_basis": "Prescription Act s12(1) — 3-year extinctive prescription for delictual claims",
      "remediation": "Urgent: file summons or obtain written acknowledgement of debt to interrupt prescription. Assign to responsible attorney and set a 14-day deadline.",
      "entity_references": [
        { "type": "PROJECT", "id": "uuid-1", "name": "Dlamini v City of Joburg" },
        { "type": "PROJECT", "id": "uuid-2", "name": "Nkosi Estate" }
      ]
    },
    {
      "id": "F002",
      "severity": "HIGH",
      "category": "FICA_CDD",
      "title": "17 customers overdue for CDD review (>90 days)",
      "description": "17 active customers have incomplete FICA checklists with items outstanding for more than 90 days. 5 of these are trust structures requiring enhanced due diligence.",
      "regulatory_basis": "FICA s21(2) — ongoing due diligence; FIC Guidance Note 7",
      "remediation": "Prioritise the 5 trust structures. Send information requests for outstanding documents via the portal. Consider triggering FICA verification AI skill for recently uploaded documents.",
      "entity_references": [
        { "type": "CUSTOMER", "id": "uuid-3", "name": "Mkhize Family Trust" }
      ]
    }
  ],
  "recommendations": [
    {
      "priority": "IMMEDIATE",
      "recommendation": "Address the two prescription matters within 14 days.",
      "estimated_effort": "2-4 hours attorney time"
    },
    {
      "priority": "SHORT_TERM",
      "recommendation": "Schedule a CDD review sprint for the 17 overdue customers. Use FICA verification AI skill to accelerate.",
      "estimated_effort": "1-2 days paralegal time"
    },
    {
      "priority": "MEDIUM_TERM",
      "recommendation": "Register remaining POPIA processing activities. 8 categories are unregistered.",
      "estimated_effort": "Half day compliance officer time"
    }
  ]
}
```

### 3.5 Execution gates

One gate per audit:

- `gate_type = PUBLISH_COMPLIANCE_REPORT`
- `proposed_action`: persist the audit results as a `ComplianceAuditReport` entity and notify relevant team members about critical findings.
- On approval: the system persists the report and creates notifications for findings with `CRITICAL` severity. Findings are stored as `ComplianceAuditFinding` entities linked to the report.

### 3.6 Compliance audit findings persistence

New tenant-scoped table `compliance_audit_report` (V127):

- `id` (uuid PK)
- `tenant_id` (FK)
- `execution_id` (uuid, FK to `ai_execution.id`)
- `overall_grade` (varchar)
- `overall_assessment` (text)
- `category_scores` (jsonb)
- `status` (varchar, enum: `DRAFT`, `PUBLISHED`, `ARCHIVED`)
- `published_by` (uuid, FK to member, nullable)
- `published_at` (timestamp, nullable)
- Standard audit columns

New tenant-scoped table `compliance_audit_finding` (V127):

- `id` (uuid PK)
- `tenant_id` (FK)
- `report_id` (uuid, FK to `compliance_audit_report.id`)
- `finding_id` (varchar, e.g. `F001`)
- `severity` (varchar, enum: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`)
- `category` (varchar, enum: `FICA_CDD`, `POPIA`, `TRUST_ACCOUNTING`, `PRESCRIPTION`, `RECORD_RETENTION`)
- `title` (varchar)
- `description` (text)
- `regulatory_basis` (text)
- `remediation` (text)
- `entity_type` (varchar, nullable)
- `entity_id` (uuid, nullable)
- `status` (varchar, enum: `OPEN`, `ACKNOWLEDGED`, `IN_PROGRESS`, `RESOLVED`, `FALSE_POSITIVE`)
- `resolved_by` (uuid, FK to member, nullable)
- `resolved_at` (timestamp, nullable)
- `resolution_notes` (text, nullable)
- Standard audit columns

### 3.7 Compliance dashboard extension

The existing compliance dashboard (Phase 14, route `/compliance`) gains:

- **Audit history panel**: list of past compliance audit reports with grades and dates.
- **Latest audit summary**: overall grade badge, category score breakdown, finding counts by severity.
- **Finding list**: filterable by severity, category, status. Each finding links to the relevant entity (customer, matter).
- **Finding resolution workflow**: Acknowledge → In Progress → Resolved / False Positive. Each transition is audited.
- **"Run AI Audit" button**: triggers the skill. Disabled while an audit is in progress.

### 3.8 Error handling

- No active customers: audit returns a clean report ("No active customers to audit").
- Module not available (e.g., trust accounting queried but legal module not enabled): category is skipped with a note.
- Data volume too large (>500 active customers with detailed compliance data): aggregation is more aggressive, individual findings limited to top 50 by severity.
- API error: same as Phase 72 pattern.

---

## Section 4 — Shared Infrastructure Extensions

### 4.1 Document text extraction service

New service `DocumentTextExtractorService` (may already partially exist from FICA skill):

- `extractText(Document document): ExtractedText` — fetches document from S3, extracts text based on format:
  - PDF: text extraction (existing PDF library or Apache PDFBox)
  - DOCX: text extraction via existing `DocxMergeService` field discovery or direct OOXML parsing
  - Tiptap JSON: plain text extraction from Tiptap document structure
- Returns `ExtractedText(String content, int characterCount, boolean wasTruncated, String truncationWarning)`
- Enforces the 100KB text limit — truncates with warning.

### 4.2 Compliance data collector service

New service `ComplianceDataCollectorService`:

- `collectComplianceSnapshot(UUID tenantId): ComplianceSnapshot` — aggregates compliance data across all relevant services:
  - Queries `CustomerService` for active customers with checklist status
  - Queries `ComplianceChecklistService` for completion rates
  - Queries `DataProtectionService` (Phase 50) for POPIA processing activities and DSAR status
  - Queries `TrustAccountService` (Phase 60) for trust accounting integrity (if legal module enabled)
  - Queries `PrescriptionTrackerService` (Phase 55) for approaching prescription dates (if legal module enabled)
  - Queries `RetentionService` (Phase 50) for record retention status
- Returns aggregated statistics + flagged outliers, not raw data.
- Respects module guard: skips categories for modules the tenant hasn't enabled.

### 4.3 AI review report document generator

New service `AiReviewReportGenerator`:

- `generateReviewReport(ContractReviewOutput output, Matter matter, Document reviewedDocument): Document` — creates a Tiptap JSON document from the structured review output.
- Uses the existing `DocumentService.createDocument()` pipeline.
- Sets document metadata: `source = AI_GENERATED`, `ai_execution_id = [execution UUID]`.

### 4.4 AI draft document generator

New service `AiDraftDocumentGenerator`:

- `generateDraft(DraftingOutput output, DocumentTemplate template, Matter matter): Document` — applies AI-provided variable values and narrative content to the selected template.
- Uses the existing `DocumentGenerationService` pipeline with AI-sourced variable values instead of user-supplied ones.
- The resulting document opens in the Tiptap editor for attorney review.

---

## Section 5 — Skill Resource Files

### 5.1 Contract review

```
backend/src/main/resources/ai/skills/contract-review/
├── system.txt          # System prompt with SA legal framework
└── output-schema.json  # JSON schema for structured output
```

The system prompt incorporates SA legal knowledge from claude-for-legal-sa:
- Contract interpretation principles (SA common law: consensus, Shifren, specific performance)
- Consumer Protection Act clauses to check (s48 unfair terms, s49 notice requirements, s22 plain language)
- Employment-specific checks (BCEA minimum terms, restraint of trade Basson v Chilwan test)
- Corporate-specific checks (Companies Act fiduciary duties, financial assistance s44/45)

### 5.2 Drafting assistant

```
backend/src/main/resources/ai/skills/drafting/
├── system.txt          # System prompt with drafting conventions
└── output-schema.json  # JSON schema for structured output
```

### 5.3 Compliance audit

```
backend/src/main/resources/ai/skills/compliance-audit/
├── system.txt          # System prompt with SA regulatory framework
└── output-schema.json  # JSON schema for structured output
```

---

## Section 6 — Frontend Components

### 6.1 Contract review trigger (matter detail page)

On the matter detail page, documents tab:
- "Review with AI" button on each uploaded document (PDF/DOCX). Icon: magnifying glass + sparkle.
- Disabled with tooltip when prerequisites not met.
- Loading state during skill execution (~15-30 seconds for a 20-page contract).
- Result panel showing: overall risk assessment badge, finding counts by severity, executive summary.
- "View Full Report" link → opens the generated review report document.
- "Approve Report" button → creates execution gate.

### 6.2 Drafting assistant dialog

Triggered from matter detail page, documents tab, "Draft with AI" button:
- Step 1: Template selector (dropdown of available templates, filtered by matter type relevance).
- Step 2: Loading state during AI processing.
- Step 3: Results panel:
  - Variable fill table with confidence indicators (colour-coded badges)
  - Editable variable values (attorney can override AI suggestions)
  - Narrative section previews (collapsible)
  - Clause recommendations with add/remove toggles
  - Warnings about missing data
- Step 4: "Create Draft" button → execution gate → on approval, document created and opened in editor.

### 6.3 Compliance dashboard extension

Route: `/compliance` (existing page, extended):
- New "AI Audit" tab alongside existing compliance views.
- "Run AI Audit" button (primary action, disabled during in-progress audit).
- Loading state during audit (~30-60 seconds for a firm with 100+ clients).
- Latest audit summary: grade badge (A/B/C/D/F), category scores as a table, finding counts.
- Finding list: table with severity badge, category tag, title, entity link, status dropdown.
- Finding detail dialog: full description, regulatory basis, remediation, resolution workflow (Acknowledge → In Progress → Resolved / False Positive).
- Audit history: list of past audits with dates and grades.

### 6.4 AI execution history extension

Route: `/settings/ai/history` (existing page):
- Three new skill types appear in the filter dropdown: `contract-review`, `drafting`, `compliance-audit`.
- No other changes needed — the existing execution history page handles new skills generically.

---

## Section 7 — API Endpoints

### 7.1 Skill invocation (existing pattern, new skills)

Reuses the existing `AiSkillController` pattern:

- `POST /api/ai/skills/contract-review/invoke` — body: `{ "documentId": "uuid", "projectId": "uuid" }`
- `POST /api/ai/skills/drafting/invoke` — body: `{ "templateId": "uuid", "projectId": "uuid" }`
- `POST /api/ai/skills/compliance-audit/invoke` — body: `{}` (firm-wide, no entity-specific input)

### 7.2 Compliance audit endpoints (new)

- `GET /api/compliance/audit-reports` — list audit reports (paginated, sorted by date desc)
- `GET /api/compliance/audit-reports/{id}` — get audit report with findings
- `GET /api/compliance/audit-reports/{id}/findings` — list findings for a report (filterable by severity, category, status)
- `PATCH /api/compliance/audit-reports/{reportId}/findings/{findingId}` — update finding status (acknowledge, resolve, mark false positive)

### 7.3 Capabilities

No new capabilities. Existing coverage:

| Capability | Usage in Phase 74 |
|---|---|
| `AI_EXECUTE` | Invoke any of the three new skills |
| `AI_REVIEW` | Approve/reject execution gates for all three skills |
| `AI_MANAGE` | View audit report history, manage AI configuration |

---

## Section 8 — Audit Integration

All three skills produce audit events via the existing Phase 6 infrastructure:

| Event Type | When | Metadata |
|---|---|---|
| `AI_SKILL_INVOKED` | Skill execution starts | skill_id, entity_type, entity_id, model |
| `AI_GATE_APPROVED` | Attorney approves gate | gate_type, proposed_action summary |
| `AI_GATE_REJECTED` | Attorney rejects gate | gate_type, rejection_notes |
| `AI_GATE_EXPIRED` | Gate expires after 72h | gate_type |
| `COMPLIANCE_FINDING_STATUS_CHANGED` | Finding status updated | finding_id, old_status, new_status |

---

## Out of Scope

- **Inline document annotations** — requires Tiptap annotation extension. Deferred.
- **Freeform document drafting** (without template) — requires more firm profile maturity. Deferred.
- **Scheduled compliance audits** — on-demand only in v1. Scheduler is a natural follow-up.
- **Bulk contract review** ("review all documents in this matter") — one document at a time in v1.
- **Trust accounting watchdog** — separate skill, not part of this phase.
- **Fee note narrative generator** — separate skill, not part of this phase.
- **Regulatory monitor / Government Gazette integration** — needs external data source. Deferred.
- **Multi-model support** (OpenAI, Google) — Anthropic only per Phase 72 decision.
- **RAG / vector search** — skills operate on structured data, not embeddings.
- **Cross-tenant compliance benchmarking** — each tenant's data is isolated.
- **Compliance finding auto-remediation** — findings are advisory. All remediation requires human action.

## ADR Topics to Address

> **Note**: ADR-286 and ADR-287 were consumed by Phase 73 (sidebar layout, grouped tabs). Phase 74 ADRs start at ADR-288. The architecture doc (`architecture/phase74-ai-intelligence-suite.md`) has the authoritative numbering.

- **ADR-288**: Contract review document-as-report vs inline annotations — decision record for chosen approach and migration path to annotations in future.
- **ADR-289**: Template-guided drafting vs freeform drafting — decision record for template-first approach and conditions for unlocking freeform.
- **ADR-290**: On-demand compliance audit vs scheduled sweeps — trigger model decision.
- **ADR-291**: Compliance finding lifecycle — status transitions, who can resolve findings, audit trail requirements.
- **ADR-292**: AI-generated document provenance — how to track that a document was AI-generated, which execution produced it, and ensure the review trail is intact.

## Style & Boundaries

- Follow existing AI skill patterns from Phase 72 (`AiSkill` interface, `SkillContext`, `AiSkillExecutionService`).
- Follow existing document system patterns from Phase 31/42 for report and draft generation.
- Follow existing compliance dashboard patterns from Phase 14 for the compliance audit extension.
- All new services use constructor injection, no `@Autowired` on fields.
- All new controllers follow thin-controller discipline (one-liner delegation to service).
- All new endpoints use `@RequiresCapability` for authorization.
- Integration tests use `StubAiProvider` for deterministic testing.
- System prompts are external files under `resources/ai/skills/`, not hardcoded in Java.
