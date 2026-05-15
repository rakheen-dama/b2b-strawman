# Phase 72 Ideation — AI Foundation + Client Intelligence
**Date**: 2026-05-15

## Catalyst
Anthropic released [Claude for Legal](https://github.com/anthropics/claude-for-legal) — an open-source suite of 90+ legal AI skills (contract review, litigation management, compliance, regulatory monitoring) packaged as Claude Code plugins with MCP connectors. Founder asked: "Is there any point going on with Kazi?"

## Competitive analysis conclusion
**Not a threat — complementary.** Claude for Legal is an AI skill library for legal knowledge work. It has zero practice management: no tenancy, billing, time tracking, client database, trust accounting, invoicing, or portal. It's US-centric (Ironclad, Everlaw, CoCounsel, FMLA). It cannot address SA-specific requirements (§86 trust accounting, FICA/KYC, LSSA tariffs, Attorneys Act liability framework).

Kazi's moat: AI skills embedded in the system of record beat bolt-on tools because the data is already there. No external tool has access to a firm's trust ledger, compliance checklists, time entries, and matter context.

## Decision
**Learn from Claude for Legal's architecture, adapt for SA, embed in Kazi.** Three-phase AI roadmap:
- Phase 72: AI infrastructure + FICA/KYC verification + matter intake intelligence
- Phase 73: Trust accounting watchdog + fee note narrative generator
- Phase 74: Contract/document review + regulatory monitor (Government Gazette, LSSA circulars)

## Key patterns borrowed from Claude for Legal
1. **Cold-start firm profile** → `FirmAiProfile` entity (practice areas, jurisdiction, risk calibration, house style, FICA requirements, fee estimation notes)
2. **Skill-per-task architecture** → focused skills with structured I/O, not a monolithic assistant
3. **Execution gates** → mandatory attorney approval before any AI-recommended action takes effect (Attorneys Act liability)
4. **Graceful degradation** → skills work without full profile, flagging gaps

## Key design decisions
1. **BYOAK** (bring your own API key) via existing `OrgIntegration` + `SecretStore`. No platform-subsidised tokens.
2. **Anthropic only** for v1. No OpenAI/Google stubs.
3. **No streaming, no chat, no RAG.** Each skill: one invocation, structured JSON output, execution gates for actions.
4. **Sonnet default, Opus opt-in.** Configurable per firm.
5. **Prompt caching** on system prompt (firm profile) to reduce cost.
6. **Cost metering** per invocation with tenant budget alerts.
7. **Legal-za first.** Infrastructure is vertical-agnostic; skills target SA legal.

## Kazi positioning shift
From "multi-tenant practice management platform" to **"AI-native practice management platform for SA law firms"**. The AI skills read from and write to the system of record — no external tool has that data access.

## Next step
`/architecture requirements/claude-code-prompt-phase72.md`
