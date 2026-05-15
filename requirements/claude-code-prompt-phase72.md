# Phase 72 — AI Foundation + Client Intelligence (FICA & Matter Intake)

## System Context

Kazi is a multi-tenant B2B practice-management platform with three live verticals (legal-za, accounting-za, consulting-za). Phase 72 is the first **AI-native phase** — it builds the infrastructure for embedding LLM-powered skills into the platform and ships the first two skills targeting the legal-za lighthouse vertical.

**Inspiration**: Anthropic's open-source [Claude for Legal](https://github.com/anthropics/claude-for-legal) (Apache 2.0, 90+ legal AI skills) demonstrates the architectural patterns — cold-start firm profiling, skill-per-task architecture, execution gates, graceful degradation. Phase 72 adapts these patterns for the SA legal market and embeds them where Claude for Legal cannot reach: inside the system of record (matters, clients, trust ledger, time entries, compliance checklists).

**Competitive moat**: Claude for Legal is US-centric (Ironclad, Everlaw, CoCounsel, FMLA, SEC/NPRM). It cannot address §86 trust accounting, FICA/KYC compliance, LSSA tariff schedules, or the Attorneys Act liability framework. Kazi's AI advantage is that the data is already here — the AI skills read from and write to the same entities that the firm operates on daily.

### Predecessor systems that make Phase 72 cheap

- **Phase 21 — Integration Ports + BYOAK** (PRs #302–#314) — `IntegrationRegistry`, `OrgIntegration`, `SecretStore` (encrypted credentials), `IntegrationGuardService`, `IntegrationController`, integrations settings UI. The pattern for "tenant connects their own API key" is already built. Phase 72 reuses it for Anthropic API keys.
- **Phase 14 — Customer Compliance & Lifecycle** (PRs #208–#224) — `CustomerLifecycleStatus`, `ComplianceChecklist`, `ComplianceChecklistItem`, `DataSubjectRequest`, checklist template seeding, compliance dashboard. The FICA/KYC workflow is already defined — Phase 72 adds AI-powered verification on top of the existing checklist infrastructure.
- **Phase 55 — Legal Foundations** — Conflict check service, LSSA tariff lookup, court calendar. The matter-intake skill orchestrates these existing services rather than replacing them.
- **Phase 64 — Legal Vertical QA** — Terminology pack (`en-ZA-legal`), four matter templates (Litigation, Estates, Collections, Commercial). The intake skill suggests the right template based on matter description.
- **Phase 11 — Tags, Custom Fields & Views** — `FieldDefinition`, `FieldGroup`, field packs, custom field values. The AI profile stores firm preferences as structured data alongside custom fields.
- **Phase 6 — Audit & Compliance Foundations** — `AuditEvent` entity, domain event integration. Every AI invocation is audited.
- **Phase 6.5 — Notifications** — `Notification` entity, email delivery (Phase 24). Execution gates surface as notifications requiring attorney action.

### What is missing today

- **No AI infrastructure.** No provider port, no prompt management, no execution audit trail, no cost metering. Every future AI feature (trust watchdog, fee narratives, document review, regulatory monitoring) needs this foundation.
- **No firm AI profile.** The firm's legal style, risk calibration, jurisdiction assumptions, and house terminology exist implicitly (terminology pack, org settings, compliance profiles) but are not unified into a profile that AI skills can read. Claude for Legal's "cold-start interview" pattern shows the value of this.
- **Manual FICA/KYC verification.** The compliance checklist (Phase 14) tracks which documents are needed, but a human must review every uploaded document against the checklist, check ID expiry dates, verify company registration numbers against CIPC patterns, and manually tick items. For a 4-attorney firm doing 50+ new clients/year, this is hours of tedious work.
- **Blank-canvas matter creation.** Even with templates (Phase 64), creating a new matter requires the attorney to manually select the right template, identify required documents, estimate fees, and run a conflict check. The intake skill automates this sequence.

### Founder decisions that constrain this phase (2026-05-15 ideation)

- **Skill-per-task, not monolithic assistant.** Each skill has focused inputs, outputs, and its own prompt. One skill = one clear job = auditable, testable, improvable independently. No "ask Claude anything" chat interface.
- **Execution gates are mandatory.** The Attorneys Act (SA) makes the attorney personally liable for all work product. AI suggests, human decides. Every skill output that triggers an action (marking KYC complete, selecting a template, clearing a conflict) requires explicit attorney approval. This is a feature, not a limitation — it builds trust.
- **Firm AI profile drives all skills.** The cold-start interview (or manual configuration) writes a profile that all downstream skills read. Missing profile sections degrade gracefully — the skill flags what's missing and produces a more conservative output.
- **BYOAK model.** Each tenant provides their own Anthropic API key via the existing `OrgIntegration` + `SecretStore` infrastructure. No platform-mediated API key. No Kazi-subsidised token spend. Per-tenant cost visibility is mandatory.
- **No PlanTier.** Per the strategic "no plan-tier subscriptions" decision, AI features are capability-gated only. `AI_MANAGE` (configure profile, manage API key), `AI_EXECUTE` (invoke skills), `AI_REVIEW` (approve/reject execution gates). No Starter/Pro gating.
- **Legal-za first, cross-vertical later.** Skills 1 and 2 are designed for SA legal practice. The infrastructure (provider port, execution gates, audit, cost metering) is vertical-agnostic and will serve accounting-za and consulting-za skills in future phases.
- **Anthropic only for v1.** The `AiProvider` port is designed for multiple providers, but only the Anthropic adapter ships. No OpenAI/Google stub code.
- **Phase 73 ships trust watchdog + fee note narratives; Phase 74 ships document review + regulatory monitor.** These are planned but out of scope for Phase 72.

## Objective

Build a **tenant-connectable AI infrastructure** (provider port, firm profile, execution gates, audit trail, cost metering) and ship two embedded AI skills — **FICA/KYC verification assistant** and **matter intake intelligence** — that demonstrate the value of AI inside the system of record. Every AI invocation is audited, every action requires attorney approval, and every tenant controls their own API spend.

## Constraints & Assumptions

- **Schema-per-tenant only** (ADR-T001). All new tables live under `tenant/`. One Flyway migration `V122` (Phase 71 lands V121).
- **Anthropic Claude API only.** Use the `anthropic-java` SDK (or raw HTTP via `RestClient` if the Java SDK is too immature — check at architecture time). Model: `claude-sonnet-4-6` for cost efficiency; `claude-opus-4-6` as an opt-in for firms wanting higher accuracy (stored in firm profile).
- **No streaming UI.** Skills are invoked, produce a result, and the result is displayed. No token-by-token streaming to the frontend. This keeps the architecture simple and the execution gate pattern clean (you can't approve a partial result).
- **No vector database / RAG in v1.** Skills operate on structured data (entities, checklist items, custom field values) and uploaded documents (read via S3). No embedding pipeline, no vector store, no semantic search. If a future phase needs RAG, it builds on Phase 72's infrastructure.
- **No persistent conversation / chat history.** Each skill invocation is stateless — the prompt includes all necessary context. No `ChatMessage` entity, no conversation thread. This is a deliberate simplification.
- **Document reading via S3.** When the FICA skill needs to "read" an uploaded document (ID, proof of address, company registration), it fetches the file from S3, converts to text (PDF → text extraction), and includes the text in the prompt. For images (scanned IDs), use Claude's vision capability. File size limit: 10MB per document, 50MB total per skill invocation.
- **Cost metering is per-invocation.** Each `AiExecution` records input tokens, output tokens, model used, and calculated cost (based on published Anthropic pricing). The tenant's monthly spend is aggregated from execution records. Budget alerts reuse the Phase 8 budget alert pattern.
- **Execution gates expire after 72 hours.** An unreviewed gate auto-expires to `EXPIRED` status. The skill result is retained for reference but the proposed action is not taken.
- **No bulk invocation in v1.** Skills are invoked one-at-a-time (one customer's FICA, one matter's intake). Bulk "verify all pending KYC" is Phase 73+.
- **Test strategy: no Anthropic calls in CI.** All AI-touching tests use a `StubAiProvider` that returns canned responses. Integration tests verify the wiring (execution gates, audit trail, cost metering) without hitting the real API. A manual QA step with a real API key verifies prompt quality.

---

## Section 1 — AI Provider Infrastructure

### 1.1 Provider port

New package `backend/.../ai/`:

- `AiProvider` interface — `providerId(): String`, `complete(AiCompletionRequest): AiCompletionResponse`, `completeWithVision(AiVisionRequest): AiCompletionResponse`, `testConnection(): ConnectionTestResult`. Mirrors the `AccountingProvider` pattern from Phase 21.
- `AiCompletionRequest` — `systemPrompt: String`, `userPrompt: String`, `model: String` (optional, defaults from firm profile), `maxTokens: int`, `temperature: double`, `metadata: Map<String, String>` (for audit context — skill name, entity type, entity ID).
- `AiVisionRequest extends AiCompletionRequest` — adds `images: List<AiImageInput>` where `AiImageInput` is `(mediaType: String, base64Data: String)`.
- `AiCompletionResponse` — `content: String`, `model: String`, `inputTokens: int`, `outputTokens: int`, `stopReason: String`, `durationMs: long`.
- `NoOpAiProvider` — returns a fixed "AI not configured" message. Default when no API key is set.

### 1.2 Anthropic adapter

New package `backend/.../ai/anthropic/`:

- `AnthropicAiProvider implements AiProvider` — `providerId() = "anthropic"`. Translates `AiCompletionRequest` → Anthropic Messages API call → `AiCompletionResponse`.
- `AnthropicApiClient` — thin HTTP client (`RestClient`) wrapping the Anthropic Messages API (`/v1/messages`). Owns: API key attachment (from `SecretStore`), model selection, rate-limit handling (429 → back-off), timeout configuration (120s default for legal document analysis). JSON serialisation via Jackson.
- Uses prompt caching (`cache_control: {"type": "ephemeral"}` on system prompt) to reduce cost for repeated skill invocations with the same firm profile.

### 1.3 Provider registry

- `AiProviderRegistry` — analogous to `IntegrationRegistry`. Maps `providerId` → `AiProvider` bean. Resolves the active provider for a tenant from `OrgIntegration` where `integrationType = 'AI_ANTHROPIC'`.
- Registration via `OrgIntegration` entity (Phase 21) with `integrationType = 'AI_ANTHROPIC'`, API key stored in `SecretStore`.
- Connection test endpoint reuses the existing integration management API pattern.

---

## Section 2 — Firm AI Profile

### 2.1 Entity

New tenant-scoped table `ai_firm_profile` (V122):

- `id` (uuid PK)
- `tenant_id` (FK, unique — one profile per tenant)
- `practice_areas` (jsonb) — list of practice areas the firm handles (e.g. `["litigation", "estates", "collections", "commercial"]`)
- `jurisdiction` (varchar) — primary jurisdiction (e.g. `ZA-GP` for Gauteng, `ZA-WC` for Western Cape)
- `risk_calibration` (varchar, enum: `CONSERVATIVE`, `MODERATE`, `AGGRESSIVE`) — how cautiously the AI should flag issues. Default `CONSERVATIVE`.
- `house_style_notes` (text) — free-text notes about the firm's preferred language, formatting, and communication style.
- `fica_requirements` (jsonb) — firm-specific FICA verification requirements beyond the standard checklist (e.g. enhanced due diligence thresholds, specific document requirements for trusts).
- `fee_estimation_notes` (text) — firm-specific guidance for fee estimation (e.g. "we charge 20% above LSSA tariff for urgent matters").
- `preferred_model` (varchar, default `claude-sonnet-4-6`) — the Claude model the firm prefers for AI skills.
- `monthly_budget_cents` (bigint, nullable) — optional monthly spend cap in cents (ZAR). When reached, skills return a "budget exhausted" message instead of invoking the API.
- `profile_version` (int) — incremented on each update, used for cache invalidation in prompt assembly.
- `cold_start_completed` (boolean, default false) — whether the firm has gone through the cold-start configuration.
- Standard audit columns (`created_at`, `updated_at`, `created_by`, `updated_by`).

### 2.2 Cold-start interview

Not an interactive chat — a **structured configuration wizard** in the frontend:

1. **Practice areas** — multi-select from the terminology pack's known areas, plus free-text "other".
2. **Jurisdiction** — select province + optional magistrate's district.
3. **Risk calibration** — radio: Conservative / Moderate / Aggressive, with explanations of what each means for AI output.
4. **House style** — textarea for free-text notes.
5. **FICA requirements** — checklist of enhanced due diligence options (PEPs, high-value transactions, trust structures).
6. **Fee estimation** — textarea for firm-specific guidance.
7. **Model preference** — radio: Sonnet (faster, cheaper) / Opus (more thorough). Default Sonnet.
8. **Monthly budget** — optional number input.

The wizard writes the `ai_firm_profile` record and sets `cold_start_completed = true`. The profile is editable at any time via Settings → AI Configuration.

### 2.3 Profile in prompt assembly

Every skill's system prompt includes a `<firm-profile>` block assembled from the `ai_firm_profile` record. Example:

```
<firm-profile>
Practice areas: litigation, estates, collections, commercial
Jurisdiction: ZA-GP (Gauteng, South Africa)
Risk calibration: CONSERVATIVE
House style: Formal English, use "Attorneys" not "Lawyers", prefer "Matter" over "Case"
FICA requirements: Enhanced due diligence for all trust structures; PEP screening required for transactions above R100,000
Fee estimation: Standard LSSA tariff + 15% for non-urgent; + 30% for urgent
</firm-profile>
```

---

## Section 3 — AI Execution & Audit

### 3.1 Execution entity

New tenant-scoped table `ai_execution` (V122):

- `id` (uuid PK)
- `tenant_id` (FK)
- `skill_id` (varchar, not null) — e.g. `fica-verification`, `matter-intake`
- `entity_type` (varchar) — the entity the skill operated on (e.g. `CUSTOMER`, `PROJECT`)
- `entity_id` (uuid) — FK to the target entity
- `status` (varchar, enum: `COMPLETED`, `FAILED`, `CANCELLED`) — execution outcome
- `input_summary` (text) — human-readable summary of what was sent to the AI (NOT the full prompt — that's sensitive and large; store a structured summary)
- `output_content` (text) — the full AI response
- `model` (varchar) — model used
- `input_tokens` (int)
- `output_tokens` (int)
- `cost_cents` (bigint) — calculated cost in ZAR cents (converted from USD at a configurable rate)
- `duration_ms` (bigint)
- `invoked_by` (uuid, FK to member) — who triggered the skill
- `firm_profile_version` (int) — which version of the firm profile was used
- `error_message` (text, nullable) — populated on FAILED
- `created_at` (timestamp)

### 3.2 Execution gate entity

New tenant-scoped table `ai_execution_gate` (V122):

- `id` (uuid PK)
- `tenant_id` (FK)
- `execution_id` (uuid, FK to `ai_execution.id`)
- `gate_type` (varchar) — what kind of action needs approval (e.g. `MARK_KYC_COMPLETE`, `SELECT_MATTER_TEMPLATE`, `CLEAR_CONFLICT`)
- `status` (varchar, enum: `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`)
- `proposed_action` (jsonb) — structured description of what the AI wants to do (e.g. `{"action": "mark_kyc_complete", "customer_id": "...", "checklist_items_to_complete": [...]}`)
- `ai_reasoning` (text) — the AI's explanation of why it recommends this action
- `reviewed_by` (uuid, FK to member, nullable)
- `reviewed_at` (timestamp, nullable)
- `review_notes` (text, nullable) — optional notes from the reviewer
- `expires_at` (timestamp) — 72 hours from creation
- `created_at` (timestamp)

### 3.3 Execution gate workflow

1. Skill produces a result with one or more proposed actions.
2. Each proposed action creates an `ai_execution_gate` in `PENDING` status.
3. A notification is sent to the invoking member (and optionally to a configured reviewer role).
4. The reviewer sees the gate in the UI: AI reasoning, proposed action, original documents/data.
5. Reviewer clicks Approve or Reject (with optional notes).
6. On Approve: the system executes the proposed action (e.g. marks checklist items complete, selects a template). An audit event is emitted with `source = AI_ASSISTED`.
7. On Reject: no action is taken. The execution and gate are retained for audit.
8. On Expire (72h): gate moves to `EXPIRED`, no action taken.

### 3.4 Cost metering

- `AiCostService` — aggregates `ai_execution.cost_cents` per tenant per month.
- Exposes `GET /api/ai/cost-summary` — current month spend, budget remaining, projected monthly spend.
- Before each invocation, checks `ai_firm_profile.monthly_budget_cents`. If budget would be exceeded, returns a clear error without calling the API.
- Budget alert notifications at 80% and 100% of monthly budget (reuse Phase 8 budget alert pattern).

### 3.5 Audit integration

Every AI execution emits an `AuditEvent` with:
- `eventType = AI_SKILL_INVOKED` / `AI_GATE_APPROVED` / `AI_GATE_REJECTED` / `AI_GATE_EXPIRED`
- `entityType` and `entityId` pointing to the target entity (customer, project)
- `metadata` including skill ID, model, token counts, cost
- Reuses the Phase 6 audit infrastructure entirely.

---

## Section 4 — Skill 1: FICA/KYC Verification Assistant

### 4.1 Trigger

Invoked manually from the **customer detail page** when FICA documents have been uploaded. Button: "Verify with AI" on the compliance checklist panel. Only enabled when:
- AI is configured (API key set, firm profile exists)
- The customer has at least one uploaded document
- The compliance checklist has at least one unchecked item
- The invoking member has `AI_EXECUTE` capability

### 4.2 Input assembly

The skill assembles context from:

1. **Customer entity** — name, type (individual/company/trust), registration number, ID number, onboarding date.
2. **Compliance checklist** — all items with their status (complete/incomplete), the checklist template name (e.g. `fica-individual`, `fica-company`, `fica-trust`).
3. **Uploaded documents** — for each document linked to the customer:
   - Document metadata (name, type, upload date)
   - Document content (fetched from S3, converted to text for PDFs, sent as image for scanned documents using vision)
4. **Firm AI profile** — FICA requirements, risk calibration, jurisdiction.
5. **Firm terminology** — from the active terminology pack.

### 4.3 Prompt design

System prompt includes:
- Role: "You are a FICA compliance verification assistant for a South African law firm."
- SA FICA Act requirements (Act 38 of 2001, as amended): identification, verification, record-keeping obligations.
- The firm's specific FICA requirements from the AI profile.
- Risk calibration instructions (conservative = flag more, aggressive = flag less).
- Output format specification (structured JSON).

User prompt includes:
- Customer details
- Checklist items with status
- Document contents/images
- Instruction: "Review the uploaded documents against the FICA checklist. For each checklist item, determine if the uploaded documents satisfy the requirement."

### 4.4 Output format

The skill returns structured JSON:

```json
{
  "overall_assessment": "COMPLETE | INCOMPLETE | NEEDS_REVIEW",
  "risk_level": "LOW | MEDIUM | HIGH",
  "checklist_review": [
    {
      "checklist_item_id": "uuid",
      "item_name": "Certified copy of ID document",
      "status": "SATISFIED | NOT_SATISFIED | NEEDS_REVIEW",
      "evidence_document": "filename.pdf",
      "reasoning": "The uploaded ID is a certified copy of a South African ID card. The ID number matches the customer record. The certification date is within 3 months.",
      "flags": ["ID_EXPIRY_APPROACHING"]
    }
  ],
  "missing_documents": ["Proof of residence less than 3 months old"],
  "risk_flags": ["Customer is a trust structure — enhanced due diligence required per firm policy"],
  "recommended_actions": [
    {
      "action": "MARK_ITEMS_COMPLETE",
      "items": ["uuid1", "uuid2"],
      "reasoning": "These items are satisfied by the uploaded documents."
    },
    {
      "action": "REQUEST_ADDITIONAL_DOCUMENT",
      "document_type": "Proof of residence",
      "reasoning": "The uploaded proof of residence is dated 2025-11-15, which is more than 3 months old."
    }
  ]
}
```

### 4.5 Execution gates

Each recommended action with `action = MARK_ITEMS_COMPLETE` creates an execution gate:
- `gate_type = MARK_KYC_COMPLETE`
- `proposed_action` = the items to mark complete, with AI reasoning
- On approval: the system marks the specified checklist items as complete, with `completed_by` set to the reviewing attorney and `completion_notes` including "AI-assisted verification, approved by [attorney name]"

Actions with `action = REQUEST_ADDITIONAL_DOCUMENT` are informational — they appear in the output but don't create gates. The attorney decides whether to request the document through normal channels.

### 4.6 Error handling

- Document too large (>10MB): skip with a note in the output ("Document X was too large to analyse").
- Document unreadable (corrupted PDF, unsupported format): skip with a note.
- API error (timeout, rate limit): execution marked `FAILED`, error message stored, notification sent.
- Firm profile missing: skill runs with conservative defaults and includes a warning that results may be generic.

---

## Section 5 — Skill 2: Matter Intake Intelligence

### 5.1 Trigger

Invoked from the **new matter creation flow**. After the user enters the matter description and selects the customer, a button: "Get AI Recommendations" appears. Only enabled when:
- AI is configured (API key set, firm profile exists)
- The user has entered a matter description (at least 20 characters)
- A customer has been selected
- The invoking member has `AI_EXECUTE` capability

### 5.2 Input assembly

The skill assembles context from:

1. **Matter description** — the free-text description entered by the user.
2. **Customer entity** — name, type, existing matters (names only, for conflict context), lifecycle status.
3. **Available templates** — the list of matter templates (Phase 64) with their names, descriptions, and task counts.
4. **Existing matters** — names and customer names of all active matters in the tenant (for conflict detection). Limited to 500 most recent to stay within token limits.
5. **LSSA tariff data** — the tariff schedule relevant to the firm's jurisdiction (from Phase 55).
6. **Firm AI profile** — practice areas, jurisdiction, risk calibration, fee estimation notes.

### 5.3 Prompt design

System prompt includes:
- Role: "You are a matter intake assistant for a South African law firm."
- SA legal practice context: matter types, LSSA tariff structure, conflict of interest rules (Law Society guidelines).
- The firm's practice areas and fee estimation notes from the AI profile.
- Available matter templates with descriptions.
- Output format specification.

User prompt includes:
- Matter description
- Customer details
- Active matters list (names + customer names)
- Instruction: "Analyse this new matter and provide recommendations for template selection, required documents, fee estimation, and conflict screening."

### 5.4 Output format

```json
{
  "matter_classification": {
    "recommended_type": "LITIGATION | ESTATES | COLLECTIONS | COMMERCIAL | OTHER",
    "confidence": 0.92,
    "reasoning": "The description mentions a Road Accident Fund claim, which is a litigation matter."
  },
  "template_recommendation": {
    "template_id": "uuid",
    "template_name": "Litigation",
    "reasoning": "The Litigation template includes task sequences for pleadings, discovery, and trial preparation that match this matter type.",
    "customisation_notes": "Consider adding a task for RAF-specific medical report procurement."
  },
  "required_documents": [
    {
      "document_type": "Police report / case number",
      "reasoning": "Required for RAF claims to establish the accident details.",
      "priority": "HIGH"
    },
    {
      "document_type": "Medical records and reports",
      "reasoning": "Required to substantiate the injury claim and quantify damages.",
      "priority": "HIGH"
    }
  ],
  "fee_estimate": {
    "tariff_basis": "LSSA Magistrate's Court / High Court tariff",
    "estimated_range_min_cents": 1500000,
    "estimated_range_max_cents": 5000000,
    "reasoning": "Based on LSSA tariff for RAF litigation in the Gauteng jurisdiction, plus the firm's 15% premium for non-urgent matters. Range reflects uncertainty in case complexity.",
    "assumptions": ["Case proceeds to trial", "No appeal", "Single plaintiff"]
  },
  "conflict_screening": {
    "status": "CLEAR | POTENTIAL_CONFLICT | CONFLICT_DETECTED",
    "matches": [
      {
        "existing_matter_name": "Dlamini v RAF",
        "customer_name": "Road Accident Fund",
        "match_type": "OPPOSING_PARTY",
        "reasoning": "The RAF is the respondent in this matter and is also a customer in existing matter 'Dlamini v RAF'. This is likely a standard RAF litigation pattern (multiple claims against RAF) but should be reviewed."
      }
    ]
  },
  "risk_flags": ["RAF claims have a high rate of prescription — verify date of accident is within 3 years"]
}
```

### 5.5 Execution gates

Two gates may be created:

1. **Template selection** (`gate_type = SELECT_MATTER_TEMPLATE`):
   - `proposed_action`: template ID, customisation notes
   - On approval: the matter creation form pre-populates with the selected template's tasks and fields.

2. **Conflict clearance** (`gate_type = CLEAR_CONFLICT`):
   - Only created when `conflict_screening.status = CLEAR` or `POTENTIAL_CONFLICT` (if `CONFLICT_DETECTED`, no gate — the attorney must investigate manually).
   - `proposed_action`: conflict status and reasoning
   - On approval: the conflict check is recorded as cleared by the reviewing attorney.

Fee estimate and required documents are **informational only** — displayed in the UI but don't create gates. The attorney uses them to set the matter budget and send document requests through normal channels.

### 5.6 Integration with existing matter creation

The intake skill does NOT replace the existing matter creation flow. It enhances it:

1. User starts creating a matter (enters description, selects customer).
2. User clicks "Get AI Recommendations".
3. Skill runs (loading indicator, ~5-15 seconds).
4. Results appear in a panel alongside the creation form:
   - Recommended template (with "Apply" button that pre-fills the form)
   - Required documents list
   - Fee estimate range
   - Conflict screening results
   - Risk flags
5. User reviews, applies what they want, ignores what they don't.
6. Execution gates (if any) appear in the notification bell and on a dedicated "AI Reviews" page.

---

## Section 6 — Frontend Components

### 6.1 AI Configuration page

Route: `/settings/ai`

- Connection panel: Anthropic API key management (uses existing integration settings pattern)
- Firm AI profile form (the cold-start wizard, also accessible for editing)
- Cost summary: current month spend, budget, projected cost
- Model preference selector
- Test connection button

### 6.2 AI Execution History page

Route: `/settings/ai/history`

- Table of all AI executions: date, skill, target entity, status, cost, token counts
- Filter by skill, date range, status
- Click to view execution detail (input summary, output, gates)

### 6.3 AI Reviews page (Execution Gates)

Route: `/ai/reviews`

- List of pending execution gates requiring review
- Also accessible from the notification bell (gates create notifications)
- Each gate shows: skill name, target entity, AI reasoning, proposed action
- Approve / Reject buttons with optional notes
- History tab: previously reviewed gates

### 6.4 FICA Verification UI

On the customer detail page, compliance checklist panel:
- "Verify with AI" button (disabled with tooltip when prerequisites not met)
- Loading state during skill execution
- Result panel showing: overall assessment badge, per-item review, missing documents, risk flags
- "Apply Recommendations" button that creates execution gates for the items the AI recommends completing
- Link to full execution detail

### 6.5 Matter Intake UI

On the new matter creation page:
- "Get AI Recommendations" button (appears after description + customer are filled)
- Loading state during skill execution
- Recommendations panel alongside the form:
  - Template suggestion with "Apply Template" button
  - Required documents list
  - Fee estimate range with tariff basis
  - Conflict screening results with status badge
  - Risk flags as warning cards
- All recommendations are suggestions — the form remains fully editable

---

## Section 7 — Capabilities & Permissions

New capabilities (seeded via V122 migration, following Phase 41/46 pattern):

| Capability | Description | Default roles |
|---|---|---|
| `AI_MANAGE` | Configure AI profile, manage API key, view cost data | OWNER, ADMIN |
| `AI_EXECUTE` | Invoke AI skills | OWNER, ADMIN, MEMBER |
| `AI_REVIEW` | Approve/reject execution gates | OWNER, ADMIN |

Capability enforcement follows the existing `@RequiresCapability` annotation pattern.

---

## Section 8 — API Endpoints

### 8.1 AI Configuration

| Method | Path | Capability | Description |
|---|---|---|---|
| GET | `/api/ai/profile` | `AI_MANAGE` | Get firm AI profile |
| PUT | `/api/ai/profile` | `AI_MANAGE` | Create/update firm AI profile |
| GET | `/api/ai/cost-summary` | `AI_MANAGE` | Current month cost summary |
| GET | `/api/ai/executions` | `AI_MANAGE` | List executions (paginated, filterable) |
| GET | `/api/ai/executions/{id}` | `AI_MANAGE` | Execution detail |

### 8.2 Execution Gates

| Method | Path | Capability | Description |
|---|---|---|---|
| GET | `/api/ai/gates` | `AI_REVIEW` | List pending gates |
| GET | `/api/ai/gates/{id}` | `AI_REVIEW` | Gate detail |
| POST | `/api/ai/gates/{id}/approve` | `AI_REVIEW` | Approve gate (with optional notes) |
| POST | `/api/ai/gates/{id}/reject` | `AI_REVIEW` | Reject gate (with optional notes) |

### 8.3 Skills

| Method | Path | Capability | Description |
|---|---|---|---|
| POST | `/api/ai/skills/fica-verification` | `AI_EXECUTE` | Invoke FICA verification for a customer |
| POST | `/api/ai/skills/matter-intake` | `AI_EXECUTE` | Invoke matter intake analysis |

Skill endpoints accept the target entity ID and return the execution ID. The result is fetched via the execution detail endpoint (polling or notification).

---

## Out of Scope

- **Chat interface / conversational AI.** Each skill is a single invocation with structured I/O. No chat history, no follow-up questions, no "ask Claude anything" surface.
- **Vector database / RAG / semantic search.** Skills operate on structured entity data and document text, not embeddings.
- **Bulk invocation.** One customer's FICA at a time, one matter's intake at a time.
- **Trust accounting watchdog** (Phase 73).
- **Fee note narrative generator** (Phase 73).
- **Contract/document review** (Phase 74).
- **Regulatory monitor** (Phase 74).
- **Sage Pastel AI-assisted reconciliation** (Phase 73+ candidate).
- **OpenAI / Google provider adapters.**
- **Streaming responses.**
- **Fine-tuned models / custom model training.**
- **Client portal AI features.** AI is firm-side only in Phase 72.
- **Multi-language support.** English only; Afrikaans/Zulu/other SA languages deferred.
- **Automatic re-verification.** FICA skill is manual-trigger only; scheduled "re-check all KYC" is Phase 73+.

---

## ADR Topics to Address

- **ADR-280**: AI provider port design — why a dedicated `AiProvider` port rather than extending the integration port hierarchy. Comparison to `AccountingProvider` pattern.
- **ADR-281**: Execution gate pattern — why every AI action requires human approval, how gate expiry works, the liability model under the Attorneys Act.
- **ADR-282**: Cost metering strategy — per-invocation tracking, tenant budget enforcement, why BYOAK (no platform-subsidised tokens).
- **ADR-283**: Prompt architecture — firm profile in system prompt with cache_control, skill-specific user prompts, structured JSON output, no conversation history.
- **ADR-284**: Document reading strategy — S3 fetch + PDF-to-text + vision for images, size limits, why no vector store in v1.
- **ADR-285**: Test strategy for AI features — StubAiProvider for CI, canned responses, manual QA for prompt quality.

---

## Style & Boundaries

- Follow all existing conventions in `backend/CLAUDE.md` and `frontend/CLAUDE.md`.
- AI package structure: `backend/.../ai/` for core, `backend/.../ai/anthropic/` for adapter, `backend/.../ai/skill/` for skills, `backend/.../ai/gate/` for execution gates.
- Frontend: new routes under `/settings/ai` for configuration, `/ai/reviews` for gates. Skill UIs are embedded in existing pages (customer detail, matter creation).
- Reuse existing patterns: `@RequiresCapability`, `AuditEvent` emission, notification creation, `SecretStore` for credentials, `OrgIntegration` for connection management.
- No new shared-schema tables. Everything is tenant-scoped.
- Prompt text lives in Java constants or resource files (not in the database). Prompt engineering iteration happens via code changes, not runtime configuration. This is deliberate — prompts are code, not data, and should be version-controlled.
