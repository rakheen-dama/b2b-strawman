# ADR-T006: Dual-Author Comments via Discriminator

> Template ADR for `java-keycloak-multitenant-saas` starter template

**Status:** Accepted

**Context:** Comments on projects can come from two sources: members (authenticated via Keycloak/Gateway session) and customers (authenticated via portal magic link JWT). Each source uses a different authentication mechanism and a different entity type (`members` vs `customers`). The system must store, retrieve, and display comments from both sources in a unified timeline.

**Options Considered:**

1. **Single table with discriminator** — One `comments` table with `author_type` (MEMBER/CUSTOMER) and a polymorphic `author_id` referencing either `members.id` or `customers.id`
   - Pros: Unified timeline (single ORDER BY query), no schema duplication, extensible (add new author types), simple UI rendering
   - Cons: No FK constraint on `author_id` (references two tables), application must enforce referential integrity

2. **Separate tables** — `member_comments` and `customer_comments` with separate schemas
   - Pros: Real FK constraints, clean relational model
   - Cons: Duplicated schema and queries, `UNION ALL` needed for timeline view, UI must merge two data sources, adding a third author type requires a third table

3. **JPA inheritance (JOINED or SINGLE_TABLE)** — Abstract `Comment` superclass with `MemberComment` and `CustomerComment` subclasses
   - Pros: Type-safe in Java, JPA handles discrimination automatically
   - Cons: Over-engineered for 2 author types, JPA inheritance has well-known performance pitfalls (N+1, polymorphic queries), adds complexity without proportional benefit

**Decision:** Use a single `comments` table with an `author_type` discriminator column and a polymorphic `author_id`.

**Rationale:**
1. **Unified timeline:** A single table means comments from both sources appear in chronological order with a simple `ORDER BY created_at` query. No UNION, no merge logic.
2. **No duplication:** Two separate tables would duplicate schema, repository interfaces, service methods, controller endpoints, and UI components. This is a template — simplicity matters.
3. **Extensible:** If a third author type is needed (e.g., system-generated comments for status changes), adding a new discriminator value is a single-field addition.
4. **Display simplicity:** The `author_name` denormalization means the UI can render any comment without joining to both `members` and `customers` tables. The `author_type` field provides the visual badge (member vs customer).

**Consequences:**
- Positive: Simple comment listing and rendering — single query, single component
- Positive: Extensible to additional author types without schema changes
- Positive: Demonstrates a real-world pattern that generalizes to any entity needing multi-source input (approvals, feedback, signatures)
- Negative: `author_id` is not a real FK constraint (cannot reference two tables with one FK) — application code must enforce referential integrity
- Negative: `author_name` denormalization can become stale if the source name changes (acceptable for comments where the name at time of writing is the correct attribution)
