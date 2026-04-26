# Phase 70 Ideation — Specialist AI Assistants (In-Product Agents)
**Date**: 2026-04-22

## Lighthouse domain
All three verticals, SA specialisation as the differentiator. Small SA firms where partner + paralegal wear many hats. Phase 52 shipped the AI infrastructure but nobody had felt the "wow" moment — generic chat was a weak demo. Phase 70 turns generic chat into three placed-where-you-work specialists with SA context baked in.

## Decision
Phase 70 ships **three in-product specialist assistants + one automation hook + human-approval review queue + no MCP**:
1. **Specialist framework** — `Specialist` record + `SpecialistRegistry` + `<SpecialistLauncherButton>` + `<SpecialistPanel>`. Each specialist = system prompt + tool subset + launcher metadata. No new LLM provider abstraction — reuses Phase 52.
2. **Billing Assistant** — inline on invoice draft + unbilled-time dialog. Polishes time-entry descriptions; suggests line-item grouping. Two new tools (propose-only; human approval applies).
3. **Intake Assistant** — inline on customer create + info-request review + customer detail prereq prompt. Extracts structured fields from uploaded documents. Text-first via pdfbox; Claude-vision fallback for image-only PDFs (reuses BYOAK key — no new OCR vendor).
4. **Inbox Assistant** — inline on matter Activity / customer detail. Summarises recent activity as a comment. On-demand (REVIEW mode) + scheduled (DIRECT mode via automation rule).
5. **Automation hook** — `INVOKE_AI_SPECIALIST` action in Phase 37 engine + `AiSpecialistInvocation` entity + review queue page + 4 pre-seeded templates.
6. **`SCHEDULED` trigger extension** to Phase 37 registry (cron-like; distinct from delayed-follow-up).
7. **MCP server: deferred.**

## Key design decisions
1. **Inline primary, panel secondary.** Founder call 2026-04-22 — specialists appear as buttons on their specific pages; Phase 52 chat panel stays as generalist fallback. Candidate ADR.
2. **No client-facing AI.** Partners + paralegals only — portal stays untouched.
3. **SA specialisation is the differentiator.** RSA ID / CIPC / VAT / POPIA / LSSA tariff awareness lives in system prompts, not fine-tuned models. Candidate ADR.
4. **Three v1 specialists: Billing, Intake, Inbox.** Drafting + Compliance deferred to Phase 71+. Matches founder's chosen multipliers (drudgery removal + info organisation).
5. **Human approval is default.** Inbox comment-posting (DIRECT mode) is the single exception — justified because comments are low-stakes, reversible by deletion, clearly attributed. Candidate ADR.
6. **OCR via Claude-vision-over-BYOAK.** No new vendor (Textract / Tesseract) — cheapest possible OCR because it rides the tenant's already-provisioned AI envelope. Candidate ADR.
7. **One review-queue table (`AiSpecialistInvocation`) for all specialist outputs** — JSONB payload, many output shapes. Candidate ADR.
8. **Specialist = registry entry, not entity.** No `Specialist` DB table; specialists reloadable from classpath markdown. Candidate ADR.
9. **`SCHEDULED` trigger is a Phase 37 extension, not a new subsystem.** Small trigger-registry addition; reuses existing rule evaluation path. Candidate ADR.
10. **BYOAK cost model unchanged.** No platform-paid AI, no rate limiting. Observed in gap report only.
11. **Specialists audit-emit by default.** `ai.specialist.*` events ride Phase 69's audit surface automatically.
12. **MCP deferred.** Founder call — the demo priority is specialists, not the doorway.

## Scope snapshot
- 6 epics, ~12 slices (A1–A2, B1–B2, C1–C2, D1–D2, E1–E2, F1–F2)
- 1 new entity (`AiSpecialistInvocation`) + 1 migration (V108)
- 7 new tools across the three specialists (all Propose-style or one-shot read-window)
- 4 pre-seeded automation templates
- 1 new action executor + 1 `SCHEDULED` trigger extension in Phase 37
- 3 system prompts (`billing-za.md`, `intake-za.md`, `inbox-za.md`) under `src/main/resources/assistant/specialists/`
- Reuses Phase 52 `LlmChatProvider` + `AssistantToolRegistry` + Phase 21 BYOAK + Phase 46 capability filtering unchanged

## Explicitly parked
- Drafting + Compliance specialists (Phase 71+).
- MCP server (founder deferred).
- Dedicated OCR vendor (AWS Textract / Tesseract / Google Doc AI).
- NL-to-rule builder.
- Fully agentic loops / multi-step AI orchestration.
- Cost metering / rate limiting per tenant.
- Persistent chat history.
- Portal-facing AI.
- Multi-provider routing (OpenAI / local models).
- Fine-tuned custom models.
- DIRECT mode on Billing / Intake (every mutation stays REVIEW-gated).
- Self-learning from approval / rejection feedback.

## Phase roadmap after 70
- **Phase 71** candidates: Drafting Assistant (Tiptap template composition + clause suggestion) + Compliance Assistant (matter closure gate pre-flight, retention watchdog). Natural extensions of the Phase 70 framework.
- **MCP server** likely returns as a Phase 71 or 72 slice once tool definitions stabilise — small standalone scope.
- **Integrations layer** (Xero / Sage Pastel / calendar / Slack) still pending from Phase 67/68 roadmap; commercial unlock for accounting-za + consulting-za.
- **Audit view** (Phase 69) ships between 68 and 70 — audit integration in Phase 70 is downstream of Phase 69's surface.

## Domain notes (SA)
- **RSA ID number** = 13 digits `YYMMDDSSSSCAZ`; includes DOB + citizenship + Luhn. Intake validates internally, flags mismatches.
- **CIPC reg number** = `YYYY/NNNNNN/NN` (year / sequence / entity-type suffix 07=Pty Ltd etc).
- **SA VAT number** = 10 digits starting `4`.
- **Disbursements VAT split** (legal-za): sheriff / deeds office / court fees = zero-rated pass-throughs; counsel / search / travel = standard 15%. Billing Assistant keeps disbursements on separate invoice lines by default.
- **Matrimonial property regimes** (legal-za family matters): in community / out of community without accrual / out of community with accrual — Intake recognises from marriage-certificate annexure references.
- **Trust registration number** (legal-za): `IT NNN/YYYY`. Intake extracts from trust deeds.
- **POPIA §26** special personal information (health, race, biometric) — Intake flags in proposed extractions for explicit consent prompts.
- **Professional register**: third-person passive / nominal preferred for legal time descriptions ("Telephone attendance on the client re: transfer") over first-person ("I called the client about the transfer").

## Next step
`/architecture requirements/claude-code-prompt-phase70.md`
