# ADR-028: Audit Integrity Approach

**Status**: Accepted

**Context**: An audit trail is only as trustworthy as its integrity guarantees. If audit events can be silently modified or deleted, the trail provides false assurance. The platform needs a strategy for preventing or detecting tampering with audit records, appropriate to its current maturity stage (backend-only Phase 6) and future needs (regulated industries, compliance certifications).

Integrity mechanisms exist on a spectrum from simple application-level immutability to cryptographic tamper-evidence with external anchoring. The right choice depends on the threat model: are we defending against application bugs (accidental updates), malicious insiders (DB admin), or external attackers (SQL injection)?

**Options Considered**:

1. **Application-level immutability only** — No setters on the `AuditEvent` entity. No `updateEvent()` method in the service layer. Rely on code review and tests to enforce append-only semantics.
   - Pros: Zero additional infrastructure. Simple to implement and understand. Sufficient for defending against application bugs.
   - Cons: A direct SQL `UPDATE` or `DELETE` on the table bypasses application-level controls. A malicious DB admin (or SQL injection) can alter the audit trail undetected. No tamper-evidence — there's no way to prove records haven't been changed.

2. **Application-level immutability + database triggers** — Same as Option 1, plus a PostgreSQL trigger that raises an exception on `UPDATE` of `audit_events` rows. This provides database-level enforcement.
   - Pros: Blocks all update paths — application code, raw SQL, ORM bugs. Simple trigger (5 lines of PL/pgSQL). Covers the most common threat (accidental updates from bugs or migrations). Can be combined with future hash-chain verification.
   - Cons: A DB superuser can still disable the trigger and modify data. Does not provide tamper-evidence (you can't prove records *weren't* modified — you can only make it harder to modify them). DELETE is not blocked by this trigger (intentional — retention purge needs it).

3. **Cryptographic hash chain from day one** — Each audit event includes a `hash` column: SHA-256 of `(previous_event_hash, event_data)`. This creates a linked chain where modifying any event breaks the chain for all subsequent events.
   - Pros: Strong tamper-evidence — any modification is detectable by re-computing hashes. Industry-standard approach for regulated audit trails.
   - Cons: Significant implementation complexity: sequential writes (can't parallelize inserts because each depends on the previous hash), hash computation on every write, chain repair after restores or failed inserts. Performance impact on high-volume writes. Overkill for Phase 6 where the primary threat is accidental modification, not adversarial tampering.

4. **Periodic batch hashing (checkpoint model)** — Audit events are written without per-row hashes. A separate scheduled job periodically hashes batches of events (e.g., daily) and stores the hash in an `audit_hash_checkpoints` table. The checkpoint hash chains to the previous checkpoint.
   - Pros: No per-write performance impact (hashing is batch, offline). Provides tamper-evidence with a delay (detects modification within the next checkpoint cycle). Simpler than per-row chaining — no sequential write dependency. Hash chain is on checkpoints, not individual events, so it's manageable. Can be added later without changing the event table schema.
   - Cons: Not real-time tamper detection — a modification between checkpoints could be noticed only at the next hash run. Requires a separate table and scheduled job. Still requires external anchoring (publish hash to an immutable store) for full tamper-proofing.

**Decision**: Application-level immutability + database triggers (Option 2) now, with the periodic batch hashing model (Option 4) documented as a future extension.

**Rationale**: Phase 6's threat model is primarily **defensive against application bugs and accidental modifications**, not against adversarial DB admins or sophisticated attackers. The combination of:

1. **Entity immutability** (no setters, no `updateEvent()`)
2. **Database trigger** (`BEFORE UPDATE ... RAISE EXCEPTION`)
3. **Application tests** (verify that UPDATE raises an exception)

...provides strong protection against the realistic threats for a SaaS platform at this maturity stage. This is the same defense-in-depth approach the platform already uses for other concerns (e.g., Hibernate `@Filter` + RLS for tenant isolation — application-level + database-level).

The periodic batch hashing model (Option 4) is **designed but not implemented** in Phase 6:

- The `AuditHashCheckpoint` entity is documented in the architecture (Section 12.1.2) but not created as a migration.
- The `audit_events` table schema supports hashing — the `(occurred_at, id)` ordering provides a deterministic sequence for computing hashes.
- When the platform serves regulated industries, implementing Option 4 is additive (new table, new scheduled job) — no changes to the event table or service.

The per-row hash chain (Option 3) is rejected because its sequential write requirement conflicts with the platform's concurrent, multi-user nature. Under load, audit events are written simultaneously from multiple HTTP requests within the same tenant. Serializing these writes for hash chaining would create a bottleneck.

**PII considerations for integrity**:
- `ip_address` and `user_agent` are captured for security events but are NOT included in any future hash computation. This allows these fields to be nullified for GDPR erasure without breaking the hash chain.
- The hash input for future checkpoints is limited to: `id`, `event_type`, `entity_type`, `entity_id`, `actor_id`, `actor_type`, `occurred_at`. These fields are either pseudonymous (UUIDs) or non-personal (event metadata).

**Consequences**:
- `audit_events` rows cannot be updated (trigger enforcement). The trigger is created in the `V14` migration.
- `audit_events` rows CAN be deleted — this is intentional for the retention purge job. If delete-protection is needed, a separate `BEFORE DELETE` trigger can be added that only allows deletes from a specific role or with a specific `SET` configuration.
- The `AuditEvent` entity has no setters (except `setTenantId`) and no `updated_at` column.
- Integration tests verify that UPDATE raises a `DataIntegrityViolationException` (or equivalent).
- Future hash-chain implementation is additive — requires only `V15__create_audit_hash_checkpoints.sql` and a scheduled job.
- No external anchoring infrastructure is needed in Phase 6.
