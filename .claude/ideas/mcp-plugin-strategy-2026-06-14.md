# Strategy Note — Kazi as a Grounded Context Layer for Claude (MCP Server + Skill Pack)

**Date**: 2026-06-14
**Type**: Strategy note (not a phase spec — no `/architecture` handoff yet)
**Audience for the plugins**: the law firm's own staff (Kazi clients), using Claude Code / Claude Desktop / claude.ai
**Builds on**: [phase72-ideation-2026-05-15.md](phase72-ideation-2026-05-15.md) (AI Foundation), Phase 21 integration ports, Phase 7/22 portal read-model

---

## The bet

A SA law firm already runs Kazi as its **system of record** *and* its people increasingly reach for Claude. Today those are severed: a lawyer asks Claude to draft a fee note, summarise a matter, or sanity-check FICA, but Claude can't see the trust ledger, the unbilled time, the compliance checklist, or the matter file. The lawyer pastes context by hand — slow, lossy, and a POPIA hazard.

**The play:** ship a **Kazi MCP server** + a **published skill pack** so the firm's Claude is grounded in *their own live data*. Positioning:

> **"Bring your own Claude — Kazi provides the grounded context."**

Two compounding advantages:
- **Moat.** No external AI tool has access to a firm's trust ledger, time entries, and compliance state. Kazi becomes the data layer *underneath* whatever AI the firm adopts (including Anthropic's own [Claude for Legal](https://github.com/anthropics/claude-for-legal) 90+ skills).
- **Cost model.** The firm's *own* Claude subscription/API pays the token bill. Kazi just exposes data over MCP — far lower token liability than the in-product BYOAK path.

**Resolved framing (founder, 2026-06-14):** Kazi must be **fully valuable standalone**; the plugin is an **additive enhancement layer**, not a roadmap pivot. A firm without the plugin loses nothing they have today; a firm *with* it gets a fuller, AI-augmented experience on top of the same engine/store/dashboards/UI.

---

## Why this is mostly an exposure layer, not a new domain

Phases 72 & 74 are **code-complete** (the AI substrate exists; it is *untested against live Claude* — see Risks). An MCP server reuses, rather than rebuilds, almost everything:

| Building block | Status | Role in the MCP play |
|---|---|---|
| `integration.ai.AiProvider` / `anthropic.AnthropicAiProvider` | ✅ built | BYOAK + prompt caching (in-product path) |
| `integration.ai.profile.AiFirmProfile` | ✅ built | house style / jurisdiction / FICA rules Claude should read |
| 5 skills: FICA, matter-intake, contract-review, drafting, compliance-audit (`integration.ai.skill.*`) | ✅ built | skill-pack logic already exists server-side |
| `integration.ai.gate.AiExecutionGate` (PENDING→APPROVED/REJECTED, 72h, attorney sign-off) | ✅ built | the write-back gate reserved for v2 |
| `integration.ai.cost.AiCostService` (per-call + monthly ZAR budget) | ✅ built | meter MCP traffic too |
| Audit registry `ai.specialist.*` | ✅ built | log every MCP read + suggestion |
| `integration.OrgIntegration` + AES-256-GCM `EncryptedDatabaseSecretStore` | ✅ built | per-tenant secret storage |
| JWT auth + schema-per-tenant + capability RBAC (`TenantFilter`, `MemberFilter`, `RequiresCapability`) | ✅ built | the per-user auth the MCP server **must** reuse |
| Portal read-model: 15+ `Portal*View`, `PortalReadModelService` | ✅ built | **ready-made read surface for MCP tools** |
| **MCP server** | ❌ none | the one genuinely new component |

**The architecture insight:** this is a **second head on the same body.**
- Phase 72 in-product AI = **Kazi calls Claude.**
- MCP plugin = **Claude calls Kazi.**

Same domain logic, same gates, same audit, same metering — two surfaces. Build the core once (done); expose it twice.

---

## The two artefacts

### 1. Kazi MCP server (the keystone — the only net-new component)
A per-tenant, **per-user-authenticated** remote MCP server exposing the firm's data.

- **Resources / read tools (v1):** wrap `PortalReadModelService` + `AiFirmProfileService`:
  - `list_matters`, `get_matter_detail` (status, milestones, deadlines, summary)
  - `get_unbilled_time(matter)` — straight from time-entry read model
  - `list_compliance_gaps(customer)` — FICA/KYC checklist state
  - `get_trust_balance(matter)` / `list_trust_transactions` — **read-only**, §86-sensitive
  - `search_documents(matter)` → returns metadata + presigned S3 URLs
  - `get_firm_profile` — practice areas, jurisdiction, house style, risk calibration
- **Auth:** reuse the existing JWT + schema-per-tenant + capability RBAC. Every tool call resolves tenant → member → capabilities exactly like an inbound API request. A user can only see what their Kazi role lets them see.
- **Audit:** every read logged (`ai.specialist.*` family or a new `mcp.read.*`), so the firm has a POPIA-defensible trail of what AI touched which client data.

### 2. Kazi skill pack (a published Claude Code plugin)
SA-legal-tuned skills that orchestrate the MCP tools into outcomes. These **mirror the 5 server-side skills** but run in the firm's Claude client:
- **Monthly fee-note run** — pull unbilled time across matters → draft LSSA-tariff-aware narratives → human pastes/approves back in Kazi.
- **§86 trust reconciliation check** — read trust ledger + transactions, flag anomalies (read-only; never writes).
- **FICA/KYC gap review** — read a matter's checklist, identify missing docs, draft the client request.
- **Client-ready matter status brief** — synthesise activity feed + milestones + deadlines into a plain-language update.
- **Intake triage** — analyse a new-matter description against templates + recent conflicts.
- **Bridge doc** — how to run Anthropic's Claude for Legal skills *against Kazi data* via this MCP server.

Distribution: a Kazi-published plugin (marketplace entry or signed bundle) the firm installs into Claude Code/Desktop, pointed at their tenant's MCP endpoint.

---

## v1 posture: read-only intelligence (founder-selected)

v1 exposes **reads only**. Claude drafts; a human reviews and commits the result back into Kazi by hand. This deliberately sidesteps the §86 trust / Attorneys Act write minefield and ships fast.

**But reserve the write path now.** Specify the v2 write-back contract so it's not reinvented:
- A write tool (e.g. `propose_fee_note`, `propose_kyc_complete`) does **not** mutate state — it creates an `AiExecutionGate` (PENDING) exactly as the in-product skills do.
- The attorney approves/rejects **inside Kazi** (existing `AiExecutionGateController` + UI), never inside Claude.
- Audit records "AI-suggested (via MCP) → attorney-approved" with actor + timestamp.

This keeps the liability surface identical to the in-product path and means v2 is "expose existing gate creation over MCP," not new safety machinery.

---

## Key decisions & open risks

1. **🔴 The AI core is untested against live Claude.** Phases 72/74 are code-complete but no real-Claude testing has happened. *This is the critical pre-req.* Before any MCP work, the in-product skills must be exercised end-to-end against a real Anthropic key (BYOAK), with cost metering, gates, and audit verified. The MCP server inherits every latent bug in that core. **Recommended first action regardless of MCP: an AI-core verification pass.**
2. **Hosting model (decide before build):**
   - *Kazi-hosted remote MCP* — easiest adoption, Kazi bears infra and sees the data traffic.
   - *Locally-run MCP* (firm runs it next to Claude Desktop) — more private, POPIA-friendlier, harder to adopt.
   - Given SA firms + POPIA + the existing hosted backend, **recommend Kazi-hosted remote MCP with strict per-user auth**, with a local option as a later premium/on-prem story.
3. **POPIA / data egress.** Client PII flows from Kazi into the firm's Claude context. Needs: explicit firm opt-in, a processing-basis position, per-tenant enablement via `OrgIntegration` (an `AI`/`MCP` domain flag), and the read audit trail above. Worth a short legal-position doc before GA.
4. **Auth for MCP specifically.** MCP clients authenticate differently from browser sessions. Need an OAuth/device-code or scoped-token flow that still lands in the existing `TenantFilter`/`MemberFilter` capability resolution — do **not** fork a parallel auth model.
5. **Cost attribution.** With BYOAK the firm pays, but Kazi should still meter MCP reads for transparency/billing-tier signals via the existing `AiCostService` hooks (or a lighter read-counter).
6. **Prior art in-repo.** A Phase 70 "AI specialist" assistant exists (`assistant.provider.LlmChatProvider` / `AnthropicLlmProvider`, streaming + tool use, `AiSpecialistInvocation` gates). Worth reviewing — it may already contain tool-use plumbing reusable for MCP, or it may be a parallel path to consolidate.

---

## Recommended sequencing

1. **Phase A — Verify the AI core (do this first, MCP or not).** Live-Claude end-to-end test of the 5 in-product skills: BYOAK key, structured output parsing, gate creation/approval, cost metering, audit. De-risks everything downstream.
2. **Phase B — MCP read server (v1).** Thin MCP exposure layer over `PortalReadModelService` + `AiFirmProfileService`, reusing JWT/RBAC/tenant resolution. Read-only. Per-tenant enablement flag + read audit. Decide hosting (recommend Kazi-hosted).
3. **Phase C — Skill pack + Claude-for-Legal bridge.** Publish the plugin; document installation and the bridge so generic legal skills run against Kazi data.
4. **Phase D (later) — Gated write-back over MCP.** Expose `AiExecutionGate` creation as MCP "propose" tools; approval stays in-product.

Each of B/C/D is a `/ideate` → `/architecture` → `/breakdown` candidate when its turn comes. Phase A is a verification/QA effort, not a feature phase.

---

## One-line summary
The AI engine is already built; what's missing is a **read-only MCP exposure layer** over the existing portal read-model + firm profile, plus a **published skill pack**, so a firm's own Claude becomes grounded in their live Kazi data — additive to a product that already stands alone. Gate the write path for later; reuse the gates you've already built. **Blocker to clear first: the AI core has never been run against live Claude.**
