# ADR-306: Tool/Resource Contract Design — Shaping, Pagination, Versioning & the Reserved Write-Back Extension

**Status**: Accepted

**Context**:

MCP tool/resource outputs go straight into Claude's context window. Two failure modes must be designed out: (1) **context blowups** — returning raw ORM entity graphs or unbounded lists that exhaust the model's context or cost; and (2) **brittle contracts** — schema drift that silently breaks connected clients. The firm-side service layer is not shaped for this: it returns a mix of entities (`CustomerService`, `getProject`→`ProjectWithRole` wrapping the entity), controller-private records (`ProjectResponse` lives inside `ProjectController`), and uneven pagination (only `ClientLedgerService` txns, `ActivityService`, `AuditService` are `Pageable`; `ProjectService.listProjects`, `CustomerService.listCustomers`, `InvoiceService.findAll`, and `DocumentService` lists return **unbounded** `List`).

Separately, the architecture must not foreclose the reserved (Phase-79+) gated write-back path without building it now.

The decisions: resources-vs-tools split; output-shaping rules; pagination/size-cap strategy; schema versioning; and how the contract stays extensible for the reserved write-back.

**Options Considered**:

1. **Dedicated MCP DTO layer + MCP-layer pagination normalisation + name-versioned, code-defined schemas; resources for browsable entities, tools for queries (CHOSEN)** — Define thin MCP DTOs in `mcp/dto` projecting from service return types: flat named fields, short enums, money as minor-units + currency, ISO-8601 dates, nested collections capped with explicit truncation signalling (`"truncated": true, "total": N`). Every list tool exposes `page`/`size` with a hard server max (e.g. 50, defaults mirroring controller caps — activity 50, audit 200) and slices unbounded firm-side results at the MCP boundary. A global per-call response-size ceiling returns a structured "narrow your query" error rather than a truncated blob. Schemas are typed in code and versioned by **tool/resource name** — a breaking change is a new tool name, never a silent schema change. Stable browsable entities are **resources** (`kazi://firm-profile`, `kazi://matter/{id}`, `kazi://client/{id}`); parameterized queries/searches are **tools**.
   - Pros: Token-efficient and stable by construction; decouples the MCP contract from internal service/entity churn (entities can change without breaking clients); normalises the uneven pagination uniformly; truncation is explicit, never silent; name-versioning gives clients a hard guarantee.
   - Cons: A DTO + mapping layer to build and maintain; per-tool size caps to choose and test.

2. **Serialise service return types / entities directly** — Return `Project`, `Customer`, `Invoice`, `ProjectWithRole` etc. as JSON.
   - Pros: No DTO layer; least code now.
   - Cons: Raw ORM graphs blow context (lazy associations, audit columns, nested entities); leaks internal field names and structure; couples the wire contract to Hibernate mappings so any entity refactor silently breaks every connected client; unbounded lists exhaust context. Directly violates the token-budget and stable-contract requirements.

3. **A single generic `query` tool returning ad-hoc projections** — One flexible tool with a query DSL.
   - Pros: Few tools to define; very flexible.
   - Cons: No stable, discoverable schema for Claude to reason about; pushes shaping/safety to the model; hard to gate per-domain (each Kazi gate maps to a distinct tool); hard to audit (what was queried?); easy to over-fetch. Defeats the point of explicit, gated, token-shaped tools.

**Decision**: Option 1 — a dedicated MCP DTO layer with explicit shaping rules, MCP-layer pagination normalisation with hard caps and a global response-size ceiling, code-defined name-versioned schemas, and a resources-vs-tools split; the tool registry is structurally read-only and extensible for the reserved write-back.

**Rationale**:

1. **The wire contract must be decoupled from internal types.** Because firm-side returns are entities and controller-private records, serialising them directly would both blow context and couple the MCP contract to Hibernate/controller internals. A thin projection layer is the only way to get token-efficient, stable output — and [ADR-302](ADR-302-mcp-read-scope-model.md) already establishes that MCP owns this layer because it delegates to (not duplicates) the services.
2. **Pagination must be normalised at the MCP boundary** precisely because the firm-side layer is uneven — an LLM must never receive an unbounded blob, so list tools slice even the unbounded `List` returns and signal truncation explicitly rather than dropping rows silently.
3. **Name-versioning is the simplest hard guarantee.** Clients bind to tool/resource names + schemas; making a breaking change a new name (never a silent schema mutation) means a connected Claude never breaks under the firm's feet.
4. **Resources vs tools matches MCP's own model** — stable addressable entities are resources Claude can browse/read-first (firm profile as grounding), parameterized queries are tools — improving how the model reasons about the data.
5. **Read-only must be structural.** The v1 registry contains no mutating operation, asserted by a test; the reserved write-back ([Section 11.11](../architecture/phase78-mcp-server.md)) is left open by keeping the registry extensible and by the fact that gate creation (`AiExecutionGate` PENDING) is already a solved server-side capability — so a future `propose_*` tool slots in without redesign.

**Consequences**:
- Positive: Token-efficient, stable, discoverable contracts; internal refactors don't break clients; uniform pagination + explicit truncation; clean read-only invariant.
- Positive: The reserved gated write-back is not foreclosed — a future `propose_*` tool creates an `AiExecutionGate` (PENDING) approved inside Kazi, with no contract redesign.
- Negative: An MCP DTO + mapping layer and per-tool/per-resource size caps must be built, tuned, and tested; default page sizes and the global response ceiling need sensible initial values (mirror existing controller caps where present).
- Negative: Some DTOs replicate enrichment the controllers do (e.g. `CustomerResponse.from` tags + member-names) — MCP must either replicate or deliberately omit, and document which.
- Neutral: Name-versioning means deprecating an old tool name when a new shape is needed, rather than mutating in place.
- Related: [ADR-302](ADR-302-mcp-read-scope-model.md) (why MCP owns the DTO layer), [ADR-300](ADR-300-mcp-transport-and-sdk.md) (the SDK that registers these typed specs), [ADR-304](ADR-304-mcp-tenant-isolation-capability-gating.md) (per-tool gating the registry enforces), Phase 72 `AiExecutionGate` (the reserved write-back mechanism).
