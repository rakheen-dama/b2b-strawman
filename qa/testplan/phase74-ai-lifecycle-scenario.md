# Test Plan: Full AI Lifecycle Scenario
## Phase 74 — AI Intelligence Suite End-to-End

**Version**: 1.0
**Date**: 2026-05-23
**Author**: Product + QA
**Vertical**: legal-za (Mahlangu & Naidoo Attorneys)
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / Keycloak 8180 / Mailpit 8025)
**Method**: Browser-driven (Chrome MCP or Playwright). Every checkpoint is observed, not inferred.
**Depends on**: Phase 74 code merged, Phase 72 AI infrastructure operational, real Anthropic API key available

---

## 1. Purpose

This plan tests the **complete AI experience** from a firm administrator's perspective — from
initial BYOAK configuration through daily skill usage to compliance audit review. Unlike the
accuracy test plan (which validates output quality with known inputs), this plan validates that
the end-to-end workflow *feels right* and *works correctly* when a real user clicks through the
product in a browser.

**Core question**: Can an attorney at Mahlangu & Naidoo Attorneys configure AI, use all three
skills in their daily workflow, and trust the results?

## 2. Scope

| Day | Focus | Duration |
|-----|-------|----------|
| Day 1 | BYOAK setup + firm profile configuration | ~30 min |
| Day 5 | Contract review workflow — upload, review, approve, read report | ~45 min |
| Day 10 | Drafting assistant workflow — select template, review fills, create draft, edit | ~45 min |
| Day 15 | Compliance audit workflow — run audit, review findings, resolve findings | ~45 min |
| Day 20 | Cross-skill integration — review queue, cost tracking, audit trail | ~30 min |
| Day 25 | Edge cases and error recovery | ~30 min |
| Day 30 | Regression — verify all skills still work, nothing broke | ~20 min |

## 3. Prerequisites

### 3.1 Firm Setup

Start from a clean legal-za tenant (Mahlangu & Naidoo Attorneys) provisioned via the platform.

| Requirement | How to Create |
|-------------|---------------|
| Owner user: Thabo Mahlangu | Keycloak user with owner role |
| Admin user: Priya Naidoo | Keycloak user with admin role |
| Member user: James van der Merwe | Keycloak user with member role |
| 5+ active customers with varying compliance states | Seed via lifecycle script or manual creation |
| 3+ active matters with documents | Create via UI during Day 1-5 |
| 2+ document templates (engagement letter, demand letter) | Seed or create via Settings > Templates |
| Trust account with transactions | Create via Trust Accounting module |
| Real Anthropic API key | Obtain from Anthropic console |

### 3.2 Notation

Each checkpoint is a browser-observed action + verification. Screenshots recommended at key moments.

- [ ] **PASS** — observed correct behaviour in the browser
- [ ] **FAIL** — incorrect behaviour observed
- [ ] **BLOCKED** — cannot proceed due to prerequisite failure
- [ ] **SCREENSHOT** — screenshot captured for evidence

---

## 4. Day 1 — BYOAK Setup & Firm Profile Configuration

**Actor**: Thabo Mahlangu (owner)

### D1.1 Navigate to AI Settings

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D1.1.1 | Log in as Thabo → navigate to Settings | Settings page loads with sidebar navigation | [ ] |
| D1.1.2 | Click "AI Configuration" in settings sidebar | AI settings page loads at `/settings/ai` | [ ] |
| D1.1.3 | Verify no AI configured state | Page shows "AI not configured" state with setup instructions | [ ] |

### D1.2 Connect Anthropic API Key

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D1.2.1 | Click "Connect Anthropic" or integration card | API key input dialog opens | [ ] |
| D1.2.2 | Enter valid Anthropic API key | Key accepted, saved to SecretStore (masked in UI) | [ ] |
| D1.2.3 | Click "Test Connection" | Connection test succeeds, green checkmark | [ ] |
| D1.2.4 | Verify integration card shows "Connected" | Status badge changes to connected/active | [ ] |

### D1.3 Complete Firm AI Profile

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D1.3.1 | Navigate to firm profile setup (wizard or form) | Profile form loads with empty fields | [ ] |
| D1.3.2 | Select practice areas: Litigation, Estates, Collections, Commercial | Multi-select works, all 4 selected | [ ] |
| D1.3.3 | Select jurisdiction: ZA-GP (Gauteng) | Jurisdiction dropdown populated with SA provinces | [ ] |
| D1.3.4 | Set risk calibration: CONSERVATIVE | Radio button selected, explanation text visible | [ ] |
| D1.3.5 | Enter house style notes: "Formal English. Use 'Attorneys' not 'Lawyers'. Prefer 'Matter' over 'Case'. Address as 'Dear Sir/Madam' in formal correspondence." | Text area accepts input | [ ] |
| D1.3.6 | Configure FICA requirements: enhanced CDD for trusts, PEP screening for >R100k | Checklist/options work | [ ] |
| D1.3.7 | Enter fee estimation notes: "Standard LSSA tariff + 15% for non-urgent. +30% for urgent." | Text area accepts input | [ ] |
| D1.3.8 | Select model preference: Claude Sonnet (default) | Radio button selected | [ ] |
| D1.3.9 | Set monthly budget: R500 | Number input accepts value | [ ] |
| D1.3.10 | Save profile | Profile saved, `cold_start_completed = true`, confirmation message | [ ] |
| D1.3.11 | Verify cost summary shows R0 spent, R500 budget | Cost summary panel displays correctly | [ ] |

### D1.4 Verify AI Feature Activation

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D1.4.1 | Navigate to a customer detail page → click **Overview** tab group (`tab-group-overview`) | "Verify with AI" button visible on the Overview tab (compliance/FICA panel) (if FICA skill from Phase 72 is active) | [ ] |
| D1.4.2 | Navigate to a matter detail page → Documents tab | "Review with AI" button visible on document rows | [ ] |
| D1.4.3 | Navigate to matter → Documents → "Draft with AI" | Button visible and enabled (if templates exist) | [ ] |
| D1.4.4 | Navigate to Compliance dashboard | "Run AI Audit" button visible | [ ] |

### D1.5 Permission Check — Member Role

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D1.5.1 | Log in as James (member role) | Dashboard loads | [ ] |
| D1.5.2 | Navigate to matter → Documents tab | "Review with AI" button visible (AI_EXECUTE capability) | [ ] |
| D1.5.3 | Navigate to AI Reviews page `/ai/reviews` | Page loads (member can view reviews) | [ ] |
| D1.5.4 | Navigate to Settings → AI Configuration | Verify member CANNOT edit API key or profile (AI_MANAGE required) | [ ] |

---

## 5. Day 5 — Contract Review Workflow

**Actor**: Priya Naidoo (admin)

### D5.1 Upload Contract to Matter

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D5.1.1 | Navigate to active litigation matter "Dlamini v Acme Corp" | Matter detail page loads | [ ] |
| D5.1.2 | Click Documents tab | Documents list visible | [ ] |
| D5.1.3 | Upload a commercial service agreement PDF (~10 pages) | Upload succeeds, document appears in list | [ ] |
| D5.1.4 | Verify "Review with AI" button appears on the uploaded document | Button visible with sparkle icon | [ ] |

### D5.2 Trigger Contract Review

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D5.2.1 | Click "Review with AI" on the uploaded contract | Loading state appears (spinner or progress indicator) | [ ] |
| D5.2.2 | Wait for completion (~15-30 seconds) | Result panel appears with review summary | [ ] |
| D5.2.3 | Verify overall risk assessment badge | Badge shows risk level (LOW/MEDIUM/HIGH) with colour coding | [ ] |
| D5.2.4 | Verify finding count summary | Shows count by severity (e.g., "2 HIGH, 3 MEDIUM, 1 LOW") | [ ] |
| D5.2.5 | Verify executive summary text | Readable summary of key findings | [ ] |
| D5.2.6 | Verify individual findings displayed | Each finding shows: severity badge, title, clause reference, description | [ ] |
| D5.2.7 | Verify "Approve Report" button visible | Creates execution gate for report generation | [ ] |

### D5.3 Approve and View Report

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D5.3.1 | Click "Approve Report" | Execution gate created, confirmation message | [ ] |
| D5.3.2 | Navigate to AI Reviews page `/ai/reviews` | Pending gate visible in the list | [ ] |
| D5.3.3 | Open the pending gate | Shows AI reasoning, proposed action (create review report) | [ ] |
| D5.3.4 | Click "Approve" | Gate status → APPROVED, report generation starts | [ ] |
| D5.3.5 | Navigate back to matter → Documents tab | New document "Contract Review Report" appears | [ ] |
| D5.3.6 | Open the review report document | Structured report with executive summary, findings by severity, missing protections | [ ] |
| D5.3.7 | Verify report attribution | "AI-Assisted Review, approved by Priya Naidoo" in report header | [ ] |
| D5.3.8 | Download report as PDF | PDF generates correctly from the Tiptap document | [ ] |

### D5.4 Notification Check

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D5.4.1 | Check notification bell | Notification about gate approval received | [ ] |
| D5.4.2 | Check Mailpit | Email notification sent for the gate (if email notifications enabled) | [ ] |

---

## 6. Day 10 — Drafting Assistant Workflow

**Actor**: Priya Naidoo (admin)

### D10.1 Initiate AI Drafting

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D10.1.1 | Navigate to active collections matter "Nkosi Debt Recovery" | Matter detail page loads | [ ] |
| D10.1.2 | Click Documents tab → "Draft with AI" button | Drafting dialog opens | [ ] |
| D10.1.3 | Verify template selector dropdown | Shows available templates (engagement letter, demand letter, etc.) | [ ] |
| D10.1.4 | Select "Demand Letter" template | Template selected, description visible | [ ] |

### D10.2 AI Processing & Results Review

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D10.2.1 | AI processes the request | Loading state (~10-20 seconds) | [ ] |
| D10.2.2 | Results panel appears | Variable fills table + narrative previews + clause recommendations | [ ] |
| D10.2.3 | Verify client name filled with HIGH confidence | Customer name matches matter's linked customer, green badge | [ ] |
| D10.2.4 | Verify matter description filled | Coherent description derived from matter context | [ ] |
| D10.2.5 | Verify narrative sections generated | "Demand narrative" section with professional language, firm house style | [ ] |
| D10.2.6 | Verify warnings for missing data | If customer address is incomplete, warning shown | [ ] |
| D10.2.7 | Verify clause recommendations | Relevant clauses suggested with reasoning | [ ] |

### D10.3 Edit Variables & Create Draft

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D10.3.1 | Edit one AI-suggested variable (e.g., change fee amount) | Field is editable, value overridden | [ ] |
| D10.3.2 | Toggle a clause recommendation off | Clause deselected | [ ] |
| D10.3.3 | Click "Create Draft" | Execution gate created, confirmation message | [ ] |
| D10.3.4 | Navigate to AI Reviews → approve the gate | Gate approved | [ ] |
| D10.3.5 | Verify draft document created in matter | New document appears in Documents tab | [ ] |
| D10.3.6 | Open draft in Tiptap editor | Document opens in editor with AI-generated content | [ ] |
| D10.3.7 | Verify overridden variable value used | The manually changed fee amount appears in the document, not the AI suggestion | [ ] |
| D10.3.8 | Verify deselected clause omitted | The removed clause is not in the document | [ ] |
| D10.3.9 | Edit the draft (add a paragraph, fix wording) | Tiptap editor fully functional on AI-generated content | [ ] |
| D10.3.10 | Save the edited draft | Document saved successfully | [ ] |

---

## 7. Day 15 — Compliance Audit Workflow

**Actor**: Thabo Mahlangu (owner)

### D15.1 Trigger Compliance Audit

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D15.1.1 | Navigate to Compliance dashboard | Compliance page loads at `/compliance` | [ ] |
| D15.1.2 | Click "AI Audit" tab (or section) | AI audit area visible with "Run AI Audit" button | [ ] |
| D15.1.3 | Click "Run AI Audit" | Loading state appears (~30-60 seconds for firm-wide sweep) | [ ] |
| D15.1.4 | Button disabled during processing | Cannot trigger second audit while one is in progress | [ ] |

### D15.2 Review Audit Results

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D15.2.1 | Audit completes, results displayed | Overall grade badge (A-F), category scores table | [ ] |
| D15.2.2 | Verify category breakdown | Scores for: FICA CDD, POPIA, Trust Accounting, Prescription, Record Retention | [ ] |
| D15.2.3 | Verify finding list | Findings displayed with severity badges, category tags, entity links | [ ] |
| D15.2.4 | Click a finding to see detail | Detail dialog shows: full description, regulatory basis, remediation recommendation | [ ] |
| D15.2.5 | Click entity link in a finding | Navigates to the referenced customer or matter | [ ] |
| D15.2.6 | Navigate back to compliance dashboard | Dashboard still shows audit results | [ ] |

### D15.3 Approve Report Publication

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D15.3.1 | Gate created for PUBLISH_COMPLIANCE_REPORT | Execution gate in PENDING status | [ ] |
| D15.3.2 | Navigate to AI Reviews → approve the gate | Gate approved, report published | [ ] |
| D15.3.3 | Verify findings persisted | Compliance audit findings appear in finding list with OPEN status | [ ] |
| D15.3.4 | Verify critical finding notifications | Notification bell shows alerts for CRITICAL severity findings | [ ] |

### D15.4 Resolve Findings

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D15.4.1 | Open a FICA CDD finding | Finding detail shows with status OPEN | [ ] |
| D15.4.2 | Click "Acknowledge" | Status changes to ACKNOWLEDGED | [ ] |
| D15.4.3 | Click "Mark In Progress" | Status changes to IN_PROGRESS | [ ] |
| D15.4.4 | Click "Resolve" with notes: "CDD refreshed for all 3 trust clients, documents verified" | Status changes to RESOLVED, resolution notes saved | [ ] |
| D15.4.5 | Open a minor finding → mark as "False Positive" | Status changes to FALSE_POSITIVE | [ ] |
| D15.4.6 | Verify finding counts update | Dashboard reflects resolved/false-positive findings (reduced open count) | [ ] |

### D15.5 Audit History

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D15.5.1 | Verify audit history shows this audit | List shows date, grade, finding count summary | [ ] |
| D15.5.2 | Click to view the historical report | Full report with findings loads from history | [ ] |

---

## 8. Day 20 — Cross-Skill Integration

**Actor**: Thabo Mahlangu (owner)

### D20.1 Review Queue — All Skills

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D20.1.1 | Navigate to AI Reviews `/ai/reviews` | Page shows reviews from all 3 skills (contract review, drafting, compliance) | [ ] |
| D20.1.2 | Filter by skill type | Filter dropdown includes all 3 new skills | [ ] |
| D20.1.3 | Filter by status (APPROVED, REJECTED, EXPIRED) | Filters work correctly | [ ] |
| D20.1.4 | Verify reviewed-by attribution | Each approved/rejected gate shows the reviewer's name | [ ] |

### D20.2 Cost Tracking

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D20.2.1 | Navigate to Settings → AI Configuration | Cost summary panel visible | [ ] |
| D20.2.2 | Verify monthly spend reflects all invocations | Total spend accounts for all Day 5, 10, 15 invocations | [ ] |
| D20.2.3 | Verify budget remaining | R500 minus actual spend = correct remaining amount | [ ] |
| D20.2.4 | Verify projected monthly spend | Reasonable projection based on usage so far | [ ] |

### D20.3 Execution History

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D20.3.1 | Navigate to Settings → AI → History | Execution list shows all invocations | [ ] |
| D20.3.2 | Verify 3 skill types in filter | contract-review, drafting, compliance-audit all present | [ ] |
| D20.3.3 | Click a contract review execution | Detail shows: input summary (document name, matter), output (review results), cost, tokens | [ ] |
| D20.3.4 | Click a drafting execution | Detail shows: template used, variables filled, cost | [ ] |
| D20.3.5 | Click a compliance audit execution | Detail shows: aggregated input, full findings output, cost | [ ] |

### D20.4 Audit Trail

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D20.4.1 | Navigate to Audit Log | Global audit log accessible | [ ] |
| D20.4.2 | Filter for AI events | AI_SKILL_INVOKED, AI_GATE_APPROVED, AI_GATE_REJECTED events present | [ ] |
| D20.4.3 | Verify audit event metadata | Each event includes skill_id, model, token counts, cost | [ ] |
| D20.4.4 | Filter for COMPLIANCE_FINDING_STATUS_CHANGED | Finding resolution events present with old/new status | [ ] |
| D20.4.5 | Verify entity-level audit | Matter audit timeline shows AI review and draft creation events | [ ] |

---

## 9. Day 25 — Edge Cases & Error Recovery

**Actor**: Thabo Mahlangu (owner)

### D25.1 Budget Exhaustion

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D25.1.1 | Set monthly budget to R1 (or to current spend amount) | Budget saved | [ ] |
| D25.1.2 | Attempt to invoke any AI skill | Clear error message: "Monthly AI budget exhausted" | [ ] |
| D25.1.3 | Verify no API call made | No new ai_execution record created | [ ] |
| D25.1.4 | Restore budget to R500 | Budget updated, skills work again | [ ] |

### D25.2 Reject a Gate

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D25.2.1 | Trigger a contract review on a new document | Execution completes, gate created | [ ] |
| D25.2.2 | Navigate to AI Reviews → reject the gate with notes: "Not needed for this document" | Gate status → REJECTED | [ ] |
| D25.2.3 | Verify no review report document created | Documents tab unchanged — no new document | [ ] |
| D25.2.4 | Verify rejection recorded | Gate shows rejection notes and rejecting member name | [ ] |

### D25.3 Concurrent Audit Prevention

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D25.3.1 | Trigger a compliance audit | Audit starts processing | [ ] |
| D25.3.2 | Attempt to trigger second audit (open new tab, click Run AI Audit) | Error message: "An audit is already in progress" | [ ] |
| D25.3.3 | Wait for first audit to complete | Button re-enabled | [ ] |

### D25.4 No Templates Available (Drafting)

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D25.4.1 | If all templates deleted: navigate to matter → "Draft with AI" | Button disabled with tooltip "Create a document template first" | [ ] |

### D25.5 Profile Change Impact

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D25.5.1 | Change risk calibration from CONSERVATIVE to MODERATE | Profile saved, version incremented | [ ] |
| D25.5.2 | Re-run contract review on same document | Results may differ (fewer LOW findings with MODERATE calibration) | [ ] |
| D25.5.3 | Verify firm_profile_version tracked | New execution records the updated profile version | [ ] |
| D25.5.4 | Restore to CONSERVATIVE | Profile saved | [ ] |

---

## 10. Day 30 — Regression Check

**Actor**: Thabo Mahlangu (owner)

Quick verification that all three skills still function after 30 days of usage.

| # | Action | Expected | Status |
|---|--------|----------|--------|
| D30.1 | Upload a new document → "Review with AI" | Contract review completes, results displayed | [ ] |
| D30.2 | Navigate to matter → "Draft with AI" → select template | Drafting completes, variable fills displayed | [ ] |
| D30.3 | Navigate to Compliance → "Run AI Audit" | Audit completes, results displayed | [ ] |
| D30.4 | Check cost summary | All invocations accounted for in monthly spend | [ ] |
| D30.5 | Check audit log | All AI events present with correct metadata | [ ] |
| D30.6 | Verify Phase 72 skills still work (FICA, Matter Intake) | Existing skills unaffected by Phase 74 additions | [ ] |
| D30.7 | Verify specialist assistants still work (Billing, Inbox, Intake) | Phase 70 specialists unaffected | [ ] |

---

## 11. Success Criteria

### Must-Pass (blocking)

- [ ] All three skills complete without errors on valid inputs (D5.2, D10.2, D15.1)
- [ ] Execution gates create, approve, and reject correctly (D5.3, D10.3, D15.3, D25.2)
- [ ] Generated documents (review report, draft) are real Document entities in the matter (D5.3.5, D10.3.5)
- [ ] Compliance findings persist and support status transitions (D15.4)
- [ ] Cost metering accounts for all invocations (D20.2)
- [ ] Budget enforcement blocks invocations when exhausted (D25.1)
- [ ] Audit trail records all AI events (D20.4)
- [ ] Permission model enforced — member cannot manage AI config (D1.5.4)
- [ ] Phase 72 and Phase 70 AI features unaffected (D30.6, D30.7)

### Should-Pass (important but non-blocking)

- [ ] Contract review identifies majority of planted issues (verification against accuracy test plan)
- [ ] Drafting fills variables correctly with appropriate confidence levels
- [ ] Compliance audit findings correspond to actual data gaps
- [ ] UI loading states and error messages are clear and helpful
- [ ] Notification bell shows pending gates promptly
- [ ] Email notifications delivered for critical findings (Mailpit verification)

---

## 12. Evidence Collection

At each Day checkpoint, collect:

1. **Screenshots** of key UI states (skill results, gate approval, compliance dashboard)
2. **Backend logs** for each skill invocation (token counts, duration, model)
3. **Audit log entries** for AI events
4. **Cost summary** snapshot showing cumulative spend
5. **Mailpit** screenshot for any email notifications

Store evidence in `qa_cycle/checkpoint-results/phase74-ai-lifecycle/` with filenames matching the day (e.g., `day-05-contract-review.md`).
