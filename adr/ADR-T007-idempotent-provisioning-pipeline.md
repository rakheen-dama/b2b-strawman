# ADR-T007: Idempotent Provisioning Pipeline

> Template ADR for `java-keycloak-multitenant-saas` starter template

**Status:** Accepted

**Context:** Tenant provisioning involves multiple steps across different systems: PostgreSQL (schema creation, Flyway migrations), Keycloak (organization creation, user invitation), and the application database (OrgSchemaMapping, Organization records). Any step can fail due to transient errors — network timeouts, Keycloak unavailability, database constraint violations, or deployment-induced restarts. The system must handle partial failures gracefully.

**Options Considered:**

1. **Saga pattern with compensating transactions** — Each step has an explicit rollback operation. On failure, execute compensating transactions in reverse order.
   - Pros: Theoretically clean rollback semantics, well-understood distributed systems pattern
   - Cons: Compensating transactions are hard to get right (what if the compensator fails?), doubles the code surface, some steps (schema creation, Keycloak org creation) are difficult to reverse safely, adds significant complexity

2. **Idempotent forward-only pipeline** — Every step is idempotent (`IF NOT EXISTS`, upsert semantics). On failure, retry the entire pipeline from the beginning.
   - Pros: Simple mental model (run it, check the marker), no compensating logic, natural retry mechanism, each step is independently safe to re-run
   - Cons: Requires careful per-step idempotency design, partial state is visible until pipeline completes, every new step must be validated for idempotency

3. **Two-phase commit (2PC)** — Distributed transaction across all systems
   - Pros: ACID guarantees across systems
   - Cons: Keycloak doesn't support 2PC, extreme complexity, performance penalty, impractical for this use case

**Decision:** Every step in the provisioning pipeline is idempotent. The pipeline uses a forward-only retry model with `OrgSchemaMapping` as the commit marker.

**Rationale:**
1. **Failure recovery:** Transient failures (network timeouts, Keycloak unavailability) should not leave the system in an inconsistent state. An admin can retry the approval, and the pipeline picks up where it left off — or safely re-runs completed steps.
2. **Simplicity over saga:** A saga pattern with compensating transactions is complex and fragile. Idempotent forward-only execution is simpler, has fewer failure modes, and achieves the same reliability guarantee.
3. **OrgSchemaMapping as commit marker:** The mapping is created as the **last step** in the pipeline. If it exists, the tenant is fully provisioned. If it doesn't, the pipeline can be safely re-run. This is a clean, queryable signal.
4. **SQL `IF NOT EXISTS`:** Schema creation (`CREATE SCHEMA IF NOT EXISTS`) and Flyway migrations are inherently idempotent. Keycloak org creation checks for existing orgs before creating. Each step naturally supports re-execution.

**Pipeline Steps (all idempotent):**

| Step | Operation | Idempotency Mechanism |
|------|-----------|----------------------|
| 1 | Validate request | Read-only check |
| 2 | Create Organization record | Upsert by org slug |
| 3 | Generate schema name | Deterministic hash of org slug |
| 4 | Create PostgreSQL schema | `CREATE SCHEMA IF NOT EXISTS` |
| 5 | Run Flyway migrations | Flyway tracks applied migrations |
| 6 | Create Keycloak org | Check-then-create (by slug) |
| 7 | Invite owner to Keycloak org | Check-then-invite (by email) |
| 8 | Create OrgSchemaMapping | Check-then-insert (commit marker) |
| 9 | Mark Organization COMPLETED | Idempotent status update |

**Consequences:**
- Positive: Safe retries without compensating transactions — admin clicks "Approve" again and it works
- Positive: Simple mental model (run the pipeline, check the commit marker)
- Positive: Each step can be tested independently for idempotency
- Positive: Error details captured in `AccessRequest.provisioningError` for debugging
- Negative: Each step must be carefully designed for idempotency (upfront design cost)
- Negative: Partially provisioned state is visible in the database until the pipeline completes (mitigated by OrgSchemaMapping absence signaling "not ready")
