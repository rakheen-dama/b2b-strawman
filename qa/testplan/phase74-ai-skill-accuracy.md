# Test Plan: AI Skill Accuracy & Execution Flow
## Phase 74 — Contract Review, Drafting & Compliance Audit

**Version**: 1.0
**Date**: 2026-05-23
**Author**: Product + QA
**Vertical**: legal-za (Mahlangu & Naidoo Attorneys)
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / Keycloak 8180 / Mailpit 8025)
**Depends on**: Phase 72 AI infrastructure operational, Phase 55/60/61 legal features operational

---

## 1. Purpose

Phase 74 ships three AI skills that produce structured output from real data. If a contract review
misses a critical clause, a drafting assistant fills the wrong variable, or a compliance audit
overlooks an overdue FICA check — the firm makes decisions on flawed intelligence. Worse, because
these outputs carry AI authority, they may be trusted more than they should be.

This plan tests the **accuracy and correctness** of each skill's output against known inputs,
verifies that the execution infrastructure (gates, cost metering, audit trail) works correctly,
and confirms error handling for edge cases.

**Core question**: Given known inputs, do the skills produce correct, complete, and safe outputs?

## 2. Scope

| Track | Focus | Checkpoints |
|-------|-------|-------------|
| T1 | Contract review skill accuracy — known contracts with planted issues | ~25 |
| T2 | Drafting skill accuracy — template fills from known matter context | ~20 |
| T3 | Compliance audit accuracy — seeded data with known gaps | ~20 |
| T4 | Execution infrastructure — gates, cost metering, audit trail, error handling | ~25 |

## 3. Prerequisites

### 3.1 Seed Data

Start from the legal-za lifecycle seed. Ensure the following additional state:

| Requirement | Purpose |
|-------------|---------|
| AI configured — real Anthropic API key via OrgIntegration | Skills need a live API for accuracy testing |
| Firm AI profile completed (legal-za, ZA-GP, CONSERVATIVE risk) | All skills read from profile |
| At least 3 customers with varying FICA completion states | T3 compliance audit accuracy |
| At least 2 matters with uploaded contracts (PDF + DOCX) | T1 contract review |
| At least 2 document templates (engagement letter, demand letter) | T2 drafting |
| At least 1 trust account with transactions | T3 trust accounting audit |
| At least 2 matters with prescription dates approaching (within 90 days) | T3 prescription audit |
| Monthly AI budget set to R500 | T4 budget enforcement |
| Mailpit accessible | T4 notification verification |

### 3.2 Test Documents

Prepare these documents BEFORE the test cycle. Upload them to the appropriate matters.

**TD-01: Commercial service agreement (PDF, ~10 pages)**
Planted issues:
- One-sided indemnity clause (provider's negligence excluded)
- No POPIA data processing addendum
- No dispute resolution mechanism
- Penalty clause exceeding Conventional Penalties Act limits
- Missing CPA s22 plain language compliance

**TD-02: Employment contract (DOCX, ~8 pages)**
Planted issues:
- Restraint of trade clause exceeding 2 years and national scope (fails Basson v Chilwan reasonableness test)
- Notice period below BCEA minimum for employee's tenure
- No probation clause
- Missing workplace safety obligations (OHS Act reference)

**TD-03: Clean lease agreement (PDF, ~5 pages)**
No significant issues — tests false positive rate. Standard commercial lease with all expected protections.

**TD-04: Corrupted/unreadable PDF**
Purpose: error handling test. Garbled content or encrypted file.

**TD-05: Very large contract (>100KB extracted text)**
Purpose: truncation handling test.

### 3.3 Notation

- [ ] **ACCURATE** — skill output correctly identifies the planted issue
- [ ] **MISSED** — skill failed to identify a planted issue
- [ ] **FALSE_POS** — skill flagged something that isn't actually an issue
- [ ] **CORRECT_NEG** — skill correctly did NOT flag a non-issue
- [ ] **INFRA_OK** — infrastructure component (gate, audit, cost) worked correctly
- [ ] **INFRA_FAIL** — infrastructure component failed

---

## 4. Track T1 — Contract Review Skill Accuracy

### T1.1 Commercial Service Agreement (TD-01) — Known Issues

Upload TD-01 to a litigation matter. Trigger "Review with AI".

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T1.1.1 | Skill completes without error | `AiExecution.status = COMPLETED` | [ ] |
| T1.1.2 | Document classified as COMMERCIAL_CONTRACT | `document_classification.type = COMMERCIAL_CONTRACT` | [ ] |
| T1.1.3 | One-sided indemnity identified | Finding with severity HIGH, clause reference, cites Afrox Healthcare v Strydom or similar | [ ] |
| T1.1.4 | Missing POPIA DPA identified | Finding in `missing_protections`, references POPIA s19-22 | [ ] |
| T1.1.5 | Missing dispute resolution identified | Finding referencing no arbitration or mediation clause | [ ] |
| T1.1.6 | Penalty clause flagged | Finding referencing Conventional Penalties Act, suggests reduction clause | [ ] |
| T1.1.7 | CPA plain language gap noted | Finding or info note about CPA s22 compliance | [ ] |
| T1.1.8 | Overall risk assessment is MEDIUM or HIGH | Not LOW — document has multiple material issues | [ ] |
| T1.1.9 | All findings have statutory references | Every HIGH/MEDIUM finding cites a specific Act section | [ ] |
| T1.1.10 | Recommendations are actionable | Each finding has a concrete amendment recommendation | [ ] |
| T1.1.11 | Execution gate created (CREATE_REVIEW_REPORT) | Gate in PENDING status with meaningful proposed_action | [ ] |

### T1.2 Employment Contract (TD-02) — Known Issues

Upload TD-02 to a collections/employment matter. Trigger "Review with AI".

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T1.2.1 | Document classified as EMPLOYMENT_CONTRACT | Correct type detection | [ ] |
| T1.2.2 | Unreasonable restraint identified | Flags 2-year national scope, references Basson v Chilwan 4-factor test | [ ] |
| T1.2.3 | BCEA notice period violation flagged | Identifies that notice period is below BCEA s37 minimum for tenure | [ ] |
| T1.2.4 | Missing probation clause noted | Info or LOW finding — not critical but noteworthy | [ ] |
| T1.2.5 | OHS Act gap noted | References employer obligations under Occupational Health and Safety Act | [ ] |
| T1.2.6 | SA-specific analysis, not generic | Findings reference SA statutes, not UK/US employment law | [ ] |

### T1.3 Clean Lease Agreement (TD-03) — False Positive Rate

Upload TD-03 (clean document). Trigger "Review with AI".

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T1.3.1 | Skill completes successfully | No errors | [ ] |
| T1.3.2 | No HIGH-severity findings | Document is clean — no material issues | [ ] |
| T1.3.3 | Overall risk assessment is LOW | Clean document should get LOW risk | [ ] |
| T1.3.4 | Any findings are INFO or LOW | Minor style suggestions acceptable, not false critical flags | [ ] |
| T1.3.5 | False positive count ≤ 2 | At most 2 LOW/INFO findings that aren't truly issues | [ ] |

### T1.4 Risk Calibration Impact

Change firm profile risk calibration from CONSERVATIVE to AGGRESSIVE. Re-run review on TD-01.

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T1.4.1 | Fewer total findings than CONSERVATIVE run | AGGRESSIVE should flag only critical issues | [ ] |
| T1.4.2 | Critical issues still flagged | One-sided indemnity and missing POPIA DPA still identified | [ ] |
| T1.4.3 | Minor issues suppressed | CPA plain language and similar LOW findings may not appear | [ ] |

---

## 5. Track T2 — Drafting Skill Accuracy

### T2.1 Engagement Letter Draft — Full Context Available

Matter: active litigation matter with full customer data (name, address, ID, contact details).
Template: engagement letter template with variables for client name, matter description, scope, fee estimate, jurisdiction.

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T2.1.1 | Skill completes without error | `AiExecution.status = COMPLETED` | [ ] |
| T2.1.2 | Client name filled correctly | Matches customer record exactly, confidence HIGH | [ ] |
| T2.1.3 | Matter description filled | Derived from matter description, confidence MEDIUM or HIGH | [ ] |
| T2.1.4 | Address filled from customer record | Customer's address populated, confidence HIGH | [ ] |
| T2.1.5 | Fee estimate reasonable | Within LSSA tariff range for matter type, references firm's premium | [ ] |
| T2.1.6 | Scope narrative section generated | Coherent paragraph describing engagement scope, uses firm's house style | [ ] |
| T2.1.7 | Narrative uses correct terminology | "Matter" not "Case", "Attorneys" not "Lawyers" (per firm profile house style) | [ ] |
| T2.1.8 | Clause recommendations relevant | Suggests limitation of liability, POPIA clause, fee dispute mechanism | [ ] |
| T2.1.9 | Execution gate created (CREATE_DRAFT_DOCUMENT) | Gate in PENDING status | [ ] |
| T2.1.10 | Undetermined variables flagged | Any variable that can't be determined has confidence LOW + clear explanation | [ ] |

### T2.2 Demand Letter Draft — Partial Context

Matter: collections matter. Customer has missing address (no postal address on file).

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T2.2.1 | Missing address flagged as UNDETERMINED | Variable fill with confidence LOW + warning about missing customer address | [ ] |
| T2.2.2 | Demand narrative generated | Includes the debt amount, basis of claim, and demand timeline | [ ] |
| T2.2.3 | Warning about missing data | Explicit warning that customer address needs to be updated | [ ] |
| T2.2.4 | Other variables still filled | Variables with available data are filled correctly despite the gap | [ ] |

### T2.3 Template Variable Override

Trigger drafting skill. When results appear, manually override the AI-suggested fee estimate with a different value. Create draft.

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T2.3.1 | Override accepted | The overridden value is used in the draft, not the AI suggestion | [ ] |
| T2.3.2 | Other AI fills preserved | Non-overridden variables retain AI-suggested values | [ ] |
| T2.3.3 | Draft document uses override | The generated document contains the manually set fee estimate | [ ] |

### T2.4 No Template Available

Navigate to a matter with no templates in the tenant.

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T2.4.1 | "Draft with AI" button disabled | Clear tooltip explaining no templates available | [ ] |
| T2.4.2 | No skill invocation possible | Cannot trigger the skill without a template | [ ] |

---

## 6. Track T3 — Compliance Audit Accuracy

### T3.1 FICA CDD Gaps

Seed data: 3 customers with incomplete FICA checklists (items outstanding >90 days). 2 are trust structures.

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T3.1.1 | Audit identifies overdue CDD customers | Finding with severity HIGH, category FICA_CDD, count matches seeded data | [ ] |
| T3.1.2 | Trust structures flagged as enhanced CDD | The 2 trust customers specifically noted as requiring enhanced due diligence | [ ] |
| T3.1.3 | Customer entity references correct | `entity_references` point to the right customer UUIDs | [ ] |
| T3.1.4 | Remediation references FICA skill | Recommendation mentions using FICA verification AI skill to accelerate | [ ] |
| T3.1.5 | FICA category grade reflects gaps | Grade is B or lower given 3 out of N customers are non-compliant | [ ] |

### T3.2 Prescription Approaching

Seed data: 2 matters with prescription dates within 60 days, no recent activity.

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T3.2.1 | Both matters identified as CRITICAL | Severity CRITICAL for approaching prescription with no activity | [ ] |
| T3.2.2 | Correct prescription periods cited | Delict = 3 years (s11(d)), debt = 3 years (s11(d)), or correct specific period | [ ] |
| T3.2.3 | Remediation is specific | "File summons" or "obtain acknowledgement of debt" — not generic advice | [ ] |
| T3.2.4 | Matter entity references correct | Links to the right project UUIDs and names | [ ] |

### T3.3 Trust Accounting Integrity

Seed data: 1 trust account with all transactions properly recorded. No boundary violations.

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T3.3.1 | Trust accounting category grade is A or A- | Clean trust account should score well | [ ] |
| T3.3.2 | No false positive boundary violations | No findings claiming trust-business transfers that don't exist | [ ] |
| T3.3.3 | Unreconciled items noted if present | Any items >30 days unreconciled are flagged as LOW/MEDIUM | [ ] |

### T3.4 POPIA Processing Activities

Seed data: 12 registered processing activities, 8 categories unregistered (per Phase 50 data model).

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T3.4.1 | Unregistered categories identified | Finding noting 8 unregistered processing activity categories | [ ] |
| T3.4.2 | POPIA grade reflects gap | Grade C or lower given significant registration gaps | [ ] |
| T3.4.3 | DSAR compliance noted | Any pending/overdue DSARs flagged with 30-day timeline reference | [ ] |

### T3.5 Overall Report Quality

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T3.5.1 | Overall grade reflects weighted severity | Grade accounts for CRITICAL findings (prescription) more heavily | [ ] |
| T3.5.2 | Category scores are consistent with findings | Each category grade matches the finding counts and severities for that category | [ ] |
| T3.5.3 | Recommendations are prioritised | IMMEDIATE items relate to CRITICAL findings, SHORT_TERM to HIGH, etc. | [ ] |
| T3.5.4 | Regulatory citations are correct | Act numbers, section numbers, and legal principles are factually accurate | [ ] |
| T3.5.5 | No hallucinated findings | Every finding corresponds to actual seeded data — no invented customers or matters | [ ] |

### T3.6 Module-Conditional Categories

If trust accounting module is disabled (e.g., switch to consulting-za profile temporarily):

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T3.6.1 | Trust accounting category skipped | No trust findings, category not in report | [ ] |
| T3.6.2 | Prescription category skipped | Legal-only category excluded for non-legal profiles | [ ] |
| T3.6.3 | FICA and POPIA categories still present | These apply to all verticals | [ ] |

---

## 7. Track T4 — Execution Infrastructure

### T4.1 Execution Gate Lifecycle

For each skill, verify the full gate workflow:

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T4.1.1 | Gate created on skill completion | `ai_execution_gate` record with status PENDING | [ ] |
| T4.1.2 | Notification sent to invoking member | Notification bell shows pending review | [ ] |
| T4.1.3 | Gate visible on AI Reviews page | `/ai/reviews` lists the pending gate | [ ] |
| T4.1.4 | Gate shows AI reasoning | Meaningful explanation of proposed action | [ ] |
| T4.1.5 | Approve gate — action executed | Status → APPROVED, action taken (document created / report published) | [ ] |
| T4.1.6 | Reject gate — no action taken | Status → REJECTED, no document created | [ ] |
| T4.1.7 | Gate expiry after 72h | Status → EXPIRED, no action taken (use time manipulation or direct DB update) | [ ] |
| T4.1.8 | Audit event on approval | `AI_GATE_APPROVED` event in audit log | [ ] |
| T4.1.9 | Audit event on rejection | `AI_GATE_REJECTED` event in audit log | [ ] |

### T4.2 Cost Metering

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T4.2.1 | Each execution records token counts | `ai_execution.input_tokens` and `output_tokens` > 0 | [ ] |
| T4.2.2 | Cost calculated correctly | `cost_cents` matches token counts × published Anthropic pricing | [ ] |
| T4.2.3 | Monthly spend aggregated | `GET /api/ai/cost-summary` includes all Phase 74 executions | [ ] |
| T4.2.4 | Budget enforcement at 100% | When monthly spend exceeds R500 budget, next invocation returns budget error | [ ] |
| T4.2.5 | Budget warning at 80% | Notification sent when spend hits R400 | [ ] |

### T4.3 Audit Trail

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T4.3.1 | AI_SKILL_INVOKED event per execution | Audit log shows skill_id, entity_type, entity_id, model | [ ] |
| T4.3.2 | Execution history shows all 3 skills | `/settings/ai/history` lists contract-review, drafting, compliance-audit | [ ] |
| T4.3.3 | Execution detail shows input summary | Not the full prompt — a human-readable summary | [ ] |
| T4.3.4 | Execution detail shows output | Full AI response in structured format | [ ] |
| T4.3.5 | Firm profile version tracked | `ai_execution.firm_profile_version` matches current profile version | [ ] |

### T4.4 Error Handling

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T4.4.1 | Unreadable document (TD-04) | Execution fails with clear `UNSUPPORTED_DOCUMENT` error, user sees actionable message | [ ] |
| T4.4.2 | Large document truncation (TD-05) | Execution succeeds with truncation warning in output | [ ] |
| T4.4.3 | No API key configured | Skill buttons disabled with "Configure AI in Settings" tooltip | [ ] |
| T4.4.4 | No firm profile | Skill buttons disabled with "Complete AI profile setup" tooltip | [ ] |
| T4.4.5 | API timeout simulation | If API takes >120s, execution fails with timeout error, recorded as FAILED | [ ] |
| T4.4.6 | Concurrent compliance audit blocked | Second audit attempt while one is in progress returns clear error | [ ] |
| T4.4.7 | Member without AI_EXECUTE capability | Skill buttons not visible / disabled | [ ] |

### T4.5 Compliance Finding Lifecycle

| # | Check | Expected | Status |
|---|-------|----------|--------|
| T4.5.1 | Findings persisted on gate approval | `compliance_audit_finding` records created with correct data | [ ] |
| T4.5.2 | Finding → ACKNOWLEDGED | Status update works, audit event emitted | [ ] |
| T4.5.3 | Finding → IN_PROGRESS | Status update works | [ ] |
| T4.5.4 | Finding → RESOLVED | Requires resolved_by and optional resolution_notes | [ ] |
| T4.5.5 | Finding → FALSE_POSITIVE | Status update works, finding marked as not a real issue | [ ] |
| T4.5.6 | Invalid transition rejected | Cannot go from RESOLVED back to OPEN | [ ] |
| T4.5.7 | Audit event on status change | `COMPLIANCE_FINDING_STATUS_CHANGED` event in audit log | [ ] |

---

## 8. Execution Notes

### 8.1 Real API vs Stub

- T1, T2, T3 (accuracy tracks) **require a real Anthropic API key**. These test prompt quality and output accuracy — `StubAiProvider` cannot validate this.
- T4 (infrastructure) can use either real API or `StubAiProvider` for most checks. Budget enforcement (T4.2.4) needs real cost data or simulated cost entries.

### 8.2 Determinism

AI outputs are non-deterministic. Run accuracy checks 2-3 times to confirm consistency:
- Critical findings (planted HIGH issues) should be identified in ≥90% of runs.
- False positive rate should be ≤20% (no more than 1 in 5 findings is a false positive on clean documents).
- Variable fills with HIGH confidence should match expected values in ≥95% of runs.

### 8.3 Cost Awareness

Each skill invocation costs real tokens. Estimate per-skill cost:
- Contract review (~10 pages): ~5K input tokens, ~2K output tokens → ~R2-5 per invocation
- Drafting (~template + context): ~3K input tokens, ~1.5K output tokens → ~R1-3 per invocation
- Compliance audit (firm-wide): ~8K input tokens, ~3K output tokens → ~R5-10 per invocation

Budget the test cycle for ~R200-300 total API spend across all test runs.
