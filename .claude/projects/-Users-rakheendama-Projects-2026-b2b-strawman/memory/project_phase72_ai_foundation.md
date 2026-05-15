---
name: project-phase72-ai-foundation
description: Phase 72 embeds AI skills into Kazi — AI foundation + FICA/KYC verification + matter intake intelligence, inspired by Claude for Legal patterns adapted for SA legal market
metadata:
  type: project
---

Phase 72 is Kazi's first AI-native phase. Inspired by Anthropic's Claude for Legal (open-source, 90+ skills, US-centric) — adapted for SA legal market.

**Why:** Claude for Legal validates AI-in-legal as a pattern but cannot address SA-specific needs (§86 trust accounting, FICA/KYC, LSSA tariffs). Kazi's moat is AI embedded in the system of record — skills read from matters, clients, trust ledger, time entries.

**How to apply:**
- Phase 72: AI infrastructure (`AiProvider` port, `FirmAiProfile`, execution gates, audit, cost metering) + FICA/KYC verification + matter intake intelligence
- Phase 73: Trust accounting watchdog + fee note narrative generator
- Phase 74: Contract/document review + regulatory monitor (Government Gazette, LSSA circulars)
- Key patterns from Claude for Legal: cold-start firm profile, skill-per-task, execution gates (Attorneys Act liability), graceful degradation
- BYOAK model via existing [[feedback-no-plan-subscriptions]] `OrgIntegration` + `SecretStore`
- Anthropic only, Sonnet default, no streaming/chat/RAG in v1
