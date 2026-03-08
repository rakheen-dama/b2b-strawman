# ADR-163: Override Storage Model

**Status**: Proposed
**Date**: 2026-03-08
**Phase**: 41 (Organisation Roles & Capability-Based Permissions)

## Context

Phase 41's capability model supports per-user overrides: a member assigned to the "Project Manager" role (which has `PROJECT_MANAGEMENT` and `TEAM_OVERSIGHT`) might additionally need `INVOICING` (a `+` override) or might need `TEAM_OVERSIGHT` removed (a `-` override). The effective capabilities are: `role.capabilities + additions - removals`.

The question is how to store these per-user overrides. The cardinality is small — at most 7 additions and 7 removals per member (14 rows), though in practice most members will have 0–2 overrides. The storage model must support:
1. Computing effective capabilities: given a member, return `role.capabilities ± overrides`.
2. Querying overrides: given a member, list which capabilities are overridden and in which direction.
3. Updating overrides: replace a member's override set atomically.
4. Querying impact: given a role update, find all members whose effective capabilities change (members with overrides on the changed capabilities).

## Options Considered

1. **+/- prefixed strings in a join table (chosen)** — A `member_capability_overrides` table with columns `(member_id UUID FK, override VARCHAR(60))`. The `override` column stores `+INVOICING` or `-TEAM_OVERSIGHT`. Composite PK on `(member_id, override)`.
   - Pros: Explicit intent in the data — `+` means addition, `-` means removal. No separate `grant_type` column needed. Simple resolution: iterate overrides, check prefix, add or remove from the base set. Join table enables standard queries: "find members with `+INVOICING`" is `WHERE override = '+INVOICING'`. Small storage: at most 14 rows per member. DELETE/INSERT is atomic for full override replacement. Hibernate `@ElementCollection` maps directly.
   - Cons: String parsing in application code (prefix detection). The `+`/`-` convention is nonstandard — developers must understand the encoding. Cannot use SQL `IN` clauses for capability matching without stripping the prefix. Slightly denormalized: the capability name is embedded in a prefixed string rather than in a separate column.

2. **Materialized effective-capabilities table** — A `member_effective_capabilities` table with columns `(member_id UUID FK, capability VARCHAR(50))`. Whenever a role's capabilities change or an override is set, recompute and replace the member's rows. Resolution is a simple `SELECT capability FROM member_effective_capabilities WHERE member_id = ?`.
   - Pros: Simplest read path — no resolution logic needed at query time. Standard JOIN queries for "which members have INVOICING?" without prefix parsing. No application-level resolution algorithm.
   - Cons: Materialization introduces sync complexity — every role edit must cascade to all assigned members. If a role has 50 members and you add a capability, that's 50 INSERT operations in the same transaction. Risk of stale data if materialization fails or is skipped. Two sources of truth: the role's capabilities and the materialized set must agree. Write amplification: role updates touch `N × capabilities_changed` rows across member tables. For 7 capabilities and 50 members, a role update could touch 350 rows. Harder to debug — "why does this member have INVOICING?" requires tracing whether it came from the role or was materialized independently. Overrides lose their identity — the materialized table shows the final set but not *why* each capability is present (from role or from override). The override intent (`+`/`-`) is erased.

3. **JSONB column on Member** — Add a `capability_overrides JSONB` column to the `members` table. Store `{"additions": ["INVOICING"], "removals": ["TEAM_OVERSIGHT"]}`.
   - Pros: No additional table — overrides are co-located with the member. Single query to fetch member + overrides. JSONB supports indexing for containment queries (`@>` operator). Flexible schema — can add metadata (e.g., `grantedBy`, `grantedAt`) per override without a migration.
   - Cons: JSONB queries are less ergonomic than relational JOINs for "find all members with a specific override." GIN indexes required for containment queries — adds index maintenance overhead. Hibernate JSONB mapping requires a custom `AttributeConverter` or Hypersistence dependency. JSONB validation is application-only — the DB cannot enforce that override values are valid capability names. Harder to query atomically: "remove the +INVOICING override" requires JSONB manipulation functions (`jsonb_set`, `jsonb_array_elements`). The existing codebase has no JSONB column patterns on entity tables (JSONB is used only in `audit_events.details` and `field_definitions.options`).

## Decision

Use **+/- prefixed strings in a join table** (Option 1). Per-user capability overrides are stored in `member_capability_overrides(member_id, override)` with the `+`/`-` prefix encoding addition/removal intent.

## Rationale

The cardinality of overrides is tiny — at most 14 rows per member, with the expected case being 0–2. At this scale, the storage model's primary concern is clarity, not query performance. Option 1's `+`/`-` prefix encoding makes the override intent explicit in every row. A developer querying the database can immediately see that Carol has `+INVOICING` (added beyond her role) and `-TEAM_OVERSIGHT` (removed from her role) without joining to the role's capability set.

Option 2 (materialization) solves a problem that doesn't exist at this scale. Capability resolution is a trivial operation: fetch ~7 base capabilities and ~2 overrides, apply additions and removals. This runs once per request and takes microseconds. Materializing the effective set introduces write amplification and sync complexity for zero read-time benefit. The materialization also erases override intent — the admin can no longer see *which* capabilities were manually added or removed.

Option 3 (JSONB) is viable but introduces patterns not established in the entity layer. The codebase uses JSONB for unstructured metadata (audit details, field options), not for structured entity relationships. A join table is the idiomatic JPA approach and maps directly to Hibernate's `@ElementCollection`. No custom type converters, no GIN indexes, no JSONB manipulation functions.

The `+`/`-` prefix convention is nonstandard but self-documenting. The resolution algorithm is 5 lines of code. The overhead of prefix parsing is negligible compared to the clarity it provides in the raw data. If a future requirement demands more override metadata (e.g., who granted the override, when), the join table can be extended with columns without changing the storage model.

## Consequences

- **Positive**: Override intent is explicit in every row — `+INVOICING` and `-TEAM_OVERSIGHT` are immediately legible
- **Positive**: Simple resolution algorithm — iterate overrides, check first character, add or remove from base set
- **Positive**: Standard JPA mapping via `@ElementCollection` — no custom type converters
- **Positive**: Small, bounded storage — at most 14 rows per member, typically 0–2
- **Positive**: Atomic replacement — `DELETE WHERE member_id = ? ; INSERT` replaces the full override set in one transaction
- **Negative**: String prefix parsing is nonstandard — developers must understand the `+`/`-` convention. Mitigated by clear documentation and a dedicated `OverrideParser` utility
- **Negative**: Cannot use raw SQL `IN` for capability matching without stripping the prefix. Mitigated by the fact that override queries are rare — most queries target the role's capabilities, not individual member overrides
- **Neutral**: The join table adds one more table to the schema (77th entity-related table). The cardinality is negligible
