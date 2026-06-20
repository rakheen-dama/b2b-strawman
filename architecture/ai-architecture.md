# AI Architecture — Setup & Components

A consolidated, cross-phase view of Kazi's AI subsystem. The AI capability is built across several
phases — this document pulls them together into one architectural picture:

| Phase | Contribution |
|---|---|
| **Phase 21** (ADR-039) | Integration ports + **BYOAK** infrastructure — the `AiProvider` port, `OrgIntegration`, encrypted secret store. |
| **Phase 70** | Conversational **AI Assistant** + specialists (streaming chat, tool-use, invocation queue). |
| **Phase 72** (ADR-280–285) | **AI foundation** — firm profile, the 5 specialist skills, execution gates, cost metering. |
| **Phase 78** (ADR-303) | **MCP server** — read-only "Bring Your Own Claude" exposure of tenant data. |

> **Two heads on one body.** Kazi calls Claude (in-product AI, can write via gates) **and** the firm's
> own Claude calls Kazi (MCP, read-only). Same domain data, same auth/audit, two surfaces.

---

## 1. Design principles

1. **BYOAK (Bring Your Own API Key).** AI runs on the *tenant's* own Anthropic key, stored encrypted per
   tenant. The firm controls cost and data egress. (ADR-039, Phase 21.)
2. **Draft-first, human-approved.** AI proposes; a person decides. Consequential in-product actions are
   held in an `AiExecutionGate` for attorney sign-off before any state change. (ADR-282.)
3. **Tenant- and member-scoped.** Every AI path resolves `tenant → member → capabilities` through the
   same filter chain as the rest of the app — AI never reads beyond the member's permissions.
4. **Grounded.** Output is shaped by the per-tenant `AiFirmProfile` (practice areas, jurisdiction, house
   style, FICA posture) and SA legal knowledge.
5. **Audited & consent-gated.** In-product AI emits `ai.specialist.*`; MCP reads emit `mcp.*`. MCP data
   egress requires explicit POPIA consent (`mcp_egress_consents`). (ADR-303.)

---

## 2. Component map

```mermaid
flowchart TB
    subgraph clients["Clients"]
        UI["Kazi web app<br/>(member, JWT)"]
        FC["Firm's own Claude<br/>(Code / Desktop / claude.ai)"]
    end

    subgraph entry["Entry points (Spring controllers)"]
        SKILL["AiSkillController<br/>/api/ai/skills/*"]
        ASST["AssistantController<br/>/api/assistant/chat (SSE)"]
        GATE["AiExecutionGateController<br/>/api/ai/gates/*"]
        PROF["AiFirmProfileController<br/>/api/ai/profile · /cost-summary"]
        INT["IntegrationController<br/>/api/integrations/{domain}"]
        MCP["MCP server<br/>/mcp (Spring AI 2.0, streamable)"]
    end

    subgraph filters["Auth & scope (filter chain)"]
        SEC["TenantFilter → MemberFilter<br/>tenant · member · capabilities"]
        OAUTH["OAuth2 resource server<br/>RFC 9728 metadata (ADR-303)"]
    end

    subgraph core["AI core (integration.ai.*)"]
        SKILLS["5 Specialist skills<br/>fica · intake · contract-review<br/>drafting · compliance-audit"]
        SPEC["Assistant specialists<br/>+ AssistantToolRegistry (tool-use)"]
        COST["AiCostService<br/>+ AiPricingProperties (ZAR)"]
        FPROF["AiFirmProfileService<br/>(grounding)"]
        GATES["AiExecutionGate engine<br/>PENDING→APPROVED/REJECTED/EXPIRED"]
        PROV["AiProvider port<br/>→ AnthropicAiProvider"]
    end

    subgraph mcpcore["MCP core (mcp.*)"]
        TOOLS["15 read tools + 3 resources<br/>(matters, trust, FICA, docs, …)"]
        ENABLE["McpEnablementService<br/>+ McpEgressConsent (POPIA)"]
        MAUDIT["McpToolAudit"]
    end

    subgraph secrets["BYOAK secret store"]
        ORGI["OrgIntegration (domain=AI/MCP)"]
        ENC["EncryptedDatabaseSecretStore<br/>(AES-256-GCM)"]
    end

    DATA[("Tenant schema<br/>matters · clients · trust · time · checklists")]
    AUDIT[("audit_events<br/>ai.specialist.* · mcp.*")]
    ANTH["Anthropic API"]

    UI --> SKILL & ASST & GATE & PROF & INT
    FC -->|OAuth + bearer| MCP
    SKILL & ASST & GATE & PROF & INT --> SEC
    MCP --> OAUTH --> SEC
    SKILL --> SKILLS
    ASST --> SPEC
    SKILLS & SPEC --> FPROF & COST & PROV
    SKILLS --> GATES
    GATE --> GATES
    INT --> ORGI
    PROV -->|tenant key| ENC --> ORGI
    PROV -->|completion| ANTH
    SKILLS & SPEC & FPROF --> DATA
    MCP --> TOOLS --> ENABLE
    TOOLS --> DATA
    GATES -->|on approve| DATA
    SKILLS & GATES --> AUDIT
    TOOLS --> MAUDIT --> AUDIT
```

---

## 3. The two AI paths

```mermaid
flowchart LR
    subgraph inproduct["In-product AI — Kazi calls Claude"]
        direction TB
        A1["Member triggers a skill / asks the assistant"]
        A2["Kazi runs server-side on the<br/>tenant's BYOAK key"]
        A3["Structured/streamed output"]
        A4{"Consequential<br/>action?"}
        A5["AiExecutionGate (PENDING)<br/>→ attorney approves → state changes"]
        A6["No write — informational"]
        A1 --> A2 --> A3 --> A4
        A4 -->|yes| A5
        A4 -->|no| A6
    end

    subgraph byoc["Bring Your Own Claude — Claude calls Kazi"]
        direction TB
        B1["Firm's Claude connects over MCP (OAuth)"]
        B2["Read-only tools, scoped to the member"]
        B3["Claude drafts in the firm's client"]
        B4["Human commits the result back in Kazi by hand"]
        B1 --> B2 --> B3 --> B4
    end
```

**Key difference:** in-product AI can mutate state — but only *after* an attorney approves a gate. The
MCP path is **read-only by construction** (no write tools exist); any change is a manual human step in
Kazi. (Phase 78 §11.3 "Read-only by construction".)

---

## 4. AI data model

```mermaid
erDiagram
    OrgIntegration ||--o| AiFirmProfile : "AI domain enables"
    AiFirmProfile ||--o{ AiExecution : "grounds + budgets"
    AiExecution ||--o{ AiExecutionGate : "may propose"
    AiSpecialistInvocation ||--o{ AiExecutionGate : "queues (assistant)"
    AuditEvent }o--|| AiExecution : "ai.specialist.*"
    McpEgressConsent }o--|| OrgIntegration : "MCP domain consent"

    OrgIntegration {
        enum domain "AI | MCP | EMAIL | ACCOUNTING …"
        string providerSlug "anthropic"
        string apiKey "encrypted (AES-256-GCM)"
        string keySuffix "last-4 for masking"
        boolean enabled
    }
    AiFirmProfile {
        jsonb practiceAreas
        string jurisdiction "ZA"
        string riskCalibration "CONSERVATIVE|…"
        string houseStyleNotes
        jsonb ficaRequirements
        string feeEstimationNotes
        string preferredModel
        long monthlyBudgetCents "null = unlimited"
        boolean coldStartCompleted
    }
    AiExecution {
        string skillId
        long costCents "ZAR"
        string model
        int inputTokens
        int outputTokens
        long durationMs
        instant createdAt
    }
    AiExecutionGate {
        string gateType "MARK_KYC_COMPLETE | …"
        enum status "PENDING|APPROVED|REJECTED|EXPIRED"
        jsonb proposedAction
        string aiReasoning
        uuid reviewedBy
        instant expiresAt "default +72h"
    }
    McpEgressConsent {
        enum action "GRANTED | REVOKED"
        string consentVersion "popia-egress-v1"
        instant consentedAt "append-only"
    }
```

The `apiKey` is the only secret; everything else is tenant-scoped domain data in the tenant schema.
Cost is summed per calendar month from `AiExecution` for the budget check.

---

## 5. Flow — in-product specialist execution (BYOAK + gate + cost + audit)

```mermaid
sequenceDiagram
    actor M as Member (AI_EXECUTE)
    participant C as AiSkillController
    participant Cost as AiCostService
    participant FP as AiFirmProfile
    participant P as AiProvider (BYOAK)
    participant A as Anthropic
    participant DB as Tenant schema
    participant G as AiExecutionGate
    participant Au as audit_events

    M->>C: POST /api/ai/skills/{skill} {input}
    C->>Cost: checkBudget(monthlyBudgetCents)
    alt budget reached
        Cost-->>M: 403 "monthly AI budget reached"
    else within budget
        C->>FP: load firm profile (grounding)
        C->>DB: read matter/client/document context
        C->>P: complete(prompt, tenant key)
        P->>A: messages.create (firm's key)
        A-->>P: structured output + token usage
        P-->>C: parsed result
        C->>DB: persist AiExecution (cost ZAR, tokens, model)
        C->>G: create gate(s) PENDING for consequential actions
        C->>Au: ai.specialist.invoked
        C-->>M: result + gates + cost
        Note over M,G: later, in the review queue
        M->>G: POST /api/ai/gates/{id}/approve
        G->>DB: apply proposed action (e.g. mark KYC complete)
        G->>Au: ai.specialist.approved (actor + timestamp)
    end
```

---

## 6. Flow — Bring Your Own Claude (MCP OAuth + read)

```mermaid
sequenceDiagram
    actor FC as Firm's Claude (MCP client)
    participant KC as Keycloak (docteams realm)
    participant MCP as /mcp (Spring AI server)
    participant F as TenantFilter → MemberFilter
    participant E as McpEnablementService
    participant T as Read tool
    participant DB as Tenant schema
    participant Au as audit_events

    FC->>MCP: GET /.well-known/oauth-protected-resource
    MCP-->>FC: RFC 9728 metadata (authorization_servers) [ADR-303]
    FC->>KC: OAuth (DCR + authorization-code/PKCE)
    KC-->>FC: access token (org/groups claims)
    FC->>MCP: POST /mcp (Bearer)  initialize / tools/call
    MCP->>F: resolve tenant → member → capabilities
    MCP->>E: enabled? egress consent GRANTED?
    alt not enabled / no consent
        E-->>FC: notEnabled error
    else enabled + consented
        MCP->>T: list_matters / get_trust_balance / …
        T->>DB: read (scoped to member capabilities)
        T->>Au: mcp.tool.invoked (member + entity refs)
        T-->>FC: result (read-only)
    end
```

`scope binding` through the filter chain uses the approved `RequestScopes.runForTenant*` helpers
(ADR-T008) — never raw `ScopedValue.where`.

---

## 7. Surfaces & capabilities

| Surface | Entry | Capability | Writes? |
|---|---|---|---|
| AI Assistant (chat) | `POST /api/assistant/chat` (SSE) | `AI_ASSISTANT_USE` | only via tool-confirm |
| Specialist skills | `POST /api/ai/skills/*` | `AI_EXECUTE` | only via approved gate |
| Review queue | `GET/POST /api/ai/gates/*` | `AI_REVIEW` | applies on approve |
| Firm profile + cost | `GET/PUT /api/ai/profile`, `/cost-summary` | `AI_MANAGE` | profile only |
| BYOAK key | `/api/integrations/AI/*` | `TEAM_OVERSIGHT` | secret store |
| MCP server | `/mcp` + `/api/integrations/mcp/*` | per-tool RBAC + egress consent | never (read-only) |

---

## 8. Key decisions (referenced ADRs)

- **ADR-039 / Phase 21** — `AiProvider` port + BYOAK: AI is a swappable port; the tenant's encrypted key
  is the only secret; no vendor lock-in in the domain layer.
- **ADR-280–285 / Phase 72** — AI foundation: firm profile as the grounding spine; skills return
  structured output; execution gates as the universal write-back safety mechanism; per-tenant ZAR cost
  metering + monthly budget.
- **ADR-303 / Phase 78** — MCP exposes the *existing* read model over an OAuth-authenticated server;
  reuses the JWT/tenant/capability chain rather than forking auth; POPIA egress consent gates data flow;
  read-only by construction (no write tools).
- **ADR-T008** — tenant/member scope binding only via the approved `RequestScopes` helpers (enforced by
  `TenantScopeBindingTest`), including on the MCP request path.

## 9. Related

- `architecture/phase21-integration-ports-byoak.md` — the `AiProvider` port + BYOAK infrastructure
- `architecture/phase78-mcp-server.md` — the MCP server in depth (tools, consent, gating)
- `docs/content/ai/*` — the product-facing AI documentation
- `../claude-for-legal-sa` — the `kazi-legal-za` plugin that consumes the MCP server
