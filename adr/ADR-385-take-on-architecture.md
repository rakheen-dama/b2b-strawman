# ADR-385: Take-on architecture: a generic mapping wizard over batch, source and row entities

**Status**: Accepted

**Context**:

A firm moving onto Kazi cannot run two sets of books, so a pilot starts on a cutover date with the firm's clients, matters, trust position and debtors already in Kazi. Today the only imports are the API-based Xero client pull (`XeroCustomerImportService`, which creates PROSPECT clients behind a one-shot `customersImported` flag) and trust bank statements (reconciliation lines, not balances). The design-partner pitch closes on "we move your data for you", and the dominant SA incumbent offers bulk import only at its Enterprise tier (requirements, "Why this phase").

The requirements fix several product decisions: no source-system presets until a design partner supplies a real export (reproduce-before-fix, root CLAUDE.md §4); CSV and Excel (.xlsx); master data and opening balances at a cutover date, no history; capability-gated (`MANAGE_TAKE_ON`, admin-level), not module-gated; batches re-runnable, with idempotency from dedup keys rather than a global "already ran" flag (requirements decisions 4–6 (RD-4 to RD-6)). ADR-341 reserved a `settings/matters?tab=take-on` tab. Apache POI is on the classpath but nothing reads XLSX. `CsvBankStatementParser` has tolerant disciplines worth copying, but its helpers are protected instance methods and two are wrong for this use (it strips every comma from amounts, and has no multi-line quoted fields). Everything runs in one tenant schema; the commit for large files must survive a redeploy.

**Options Considered**:

1. **Source-system presets (AJS, GhostPractice, Lexpro parsers).**
   - Pros: One-click for firms on those systems; no mapping step.
   - Cons: Built against export formats nobody has shown us; every preset is a guess that must be corrected when the first real file arrives; still needs a generic path for every other system.
2. **A generic import framework (pluggable pipelines, a DSL for mappings, a staging schema).**
   - Pros: Reusable for future imports (WIP, documents, contacts).
   - Cons: A framework for four datasets; months of generality nobody asked for; the requirements say "reuse, no framework".
3. **Four separate one-off importers, each with its own upload, parse and commit code.**
   - Pros: Each is simple and independent.
   - Cons: Four copies of upload, sheet picking, mapping, dry run, result report and audit; four places to get file safety and PII handling right.
4. **A generic mapping wizard over one batch/source/row model, with a small handler per dataset (chosen).**
   - Pros: One upload, reader, mapping, dry run, hash, sign-off, commit runner and audit path; the dataset-specific logic is a handler (`fields`, `mapAndValidate`, `validateBatchLevel`, `commit`); the persisted mapping JSON is the seed for presets later; mirrors shapes the codebase already has (BillingRun, Xero import).
   - Cons: The handler interface must fit four quite different datasets (the trust position commits all-or-nothing across several files; the others commit per row); one more abstraction to learn.

**Decision**: Take-on is a generic CSV/XLSX mapping wizard under Settings → Matters → Take-on, built on `take_on_batches` (one attempt at one dataset, with a guarded `UPLOADED → MAPPED → VALIDATED → COMMITTING → COMPLETED | CANCELLED` lifecycle), `take_on_sources` (one file and sheet per section), `take_on_rows` (raw and mapped values, per-row status and result FK) and `take_on_sign_offs`, read through one CSV/XLSX row model, validated as a dry run bound to a content hash, committed asynchronously by one per-tenant `take_on_drain` job (plus a 5-minute sweep) that claims batches and rows with `FOR UPDATE SKIP LOCKED` and runs as the member who requested the commit, at most one batch `COMMITTING` per tenant, made idempotent by a cross-batch unique index on `(dataset, dedup_key)` for created rows, and gated by `MANAGE_TAKE_ON` plus the dataset's own capability.

**Rationale**:

The four datasets share everything except what a row means, so the variable part is a handler and the rest is written once. The shapes are copied, not invented: `BillingRun`/`BillingRunItem` for guarded transitions, per-item status, `failureReason` and result FK; the Xero import for dedup order, the 10 000-row valve, parking of unmapped values in `customFields` and two-phase validate-then-persist through a `TransactionTemplate`; the bank-statement upload for multipart, the 10 MB limit, upload-to-S3-after-parse and the `{prefix}/{tenant}/{id}/{uuid}/{file}` key. A source table separate from the batch departs from the requirements' E1 (file metadata on the batch) because the trust position is several of the old system's reports that must be committed together; one file with a row-type column would force heterogeneous mapping on the user.

Idempotency comes from the data, as requirements decision 6 (RD-6) requires: a partial unique index `uq_take_on_rows_created_key (dataset, dedup_key) WHERE status = 'CREATED'` makes the same client, matter or fee note impossible to create twice, by one batch re-run or by two batches committing at once, with no flag anywhere; `uq_take_on_batches_committing` allows one committing batch per tenant. Per-row datasets commit one row per transaction so a crash resumes from the first non-terminal row.

The commit engine is shaped by the job queue as it is at HEAD, not as a per-batch job would assume: `idx_job_queue_dedup` is unique on `(job_type, tenant_id)` while a job is pending or claimed and ignores the payload (global `V1__baseline.sql:369`), so a second per-batch job would be silently dropped and its batch left `COMMITTING`; claims older than 15 minutes are put back to `PENDING` (`JobQueueProperties.java:18`, `StaleJobRecoveryTask`), so a long job can run twice at once; and a handler gets the tenant and org but no member. Hence one per-tenant drain that loops until no batch is `COMMITTING` and stops taking work after 10 minutes; the enqueue made in its own transaction after the claim has committed — not from an `afterCommit` callback, where the `@Transactional` enqueue would join the finished transaction and never commit, and not inside the claim transaction, where a dedup violation would mark it rollback-only and undo the claim; a commit refused up front (409) when `kazi.job-queue.enabled` is false, since the enqueuer then silently no-ops; a ShedLock sweep every 5 minutes that enqueues only for tenants with a `COMMITTING` batch or a report missing its files (one query over the tenant schemas listed in `org_schema_mapping`, not a fan-out to every tenant), so a deduplicated enqueue, an unfinished run or a lost render is picked up; row and batch claims by `FOR UPDATE SKIP LOCKED` with the status re-checked under the lock, so a duplicate delivery skips work another worker holds and never sees a finished row again; and `RequestScopes.runForTenantWithMember(…, commit_requested_by, …)` so audit rows name the member who confirmed. Serialising per tenant rather than per dataset costs nothing for a one-off migration and removes every cross-dataset interleaving. The dry run binds to a SHA-256 over inputs, mapping, file hashes and row values, so an attestation or sign-off covers exactly what is committed.

The tab lives under Settings → Matters as ADR-341 reserved, rendered through the typed `MATTERS_TAB_LOADERS` map, with the wizard in the same tab (`?tab=take-on&batch={id}`) so the route count stays 17. It is not a `VerticalModuleRegistry` module: Kazi is legal-only (ADR-330) and a firm that cannot take on its data is not a firm Kazi can serve. The second, per-dataset capability (`CUSTOMER_MANAGEMENT`, `PROJECT_MANAGEMENT`, `VIEW_TRUST`/`MANAGE_TRUST`, `INVOICING`) exists so the take-on screens never show data more widely than the screens that normally show it — the Phase 92 R-2 lesson.

**Consequences**:

- Positive:
  - One reader, one dry run, one audit family, one PII discipline for all four datasets.
  - Re-runnable batches with no "already ran" state to get stuck in; duplicates are refused by the database.
  - Presets later are data: a stored mapping for a known export format.
  - XLSX support without a second row model.
- Negative:
  - The firm maps columns by hand for its first take-on.
  - Commit is asynchronous; the UI polls. A commit request that lands just as a drain finishes waits for the next sweep (at most about five minutes).
  - Only one batch commits at a time per tenant; a second commit request is refused with "Another take-on batch is committing. Wait for it to finish."
  - A server with the job queue switched off cannot commit a take-on at all (the test profile is one; tests drive the drain handler directly).
  - A run reclaimed by stale-claim recovery may still finish and mark its job completed; nothing relies on the job row's status.
  - The commit runners must be safe under at-least-once delivery; the `SKIP LOCKED` claims are a correctness requirement, covered by a two-concurrent-drains test.
  - A source table adds a join and a concept beyond the requirements' sketch.
  - `take_on_rows.raw_values` holds PII for the retention period of committed batches (mitigated by the purge and redaction rules in architecture §17.10 X4).

**Related**: [ADR-341](ADR-341-settings-taxonomy-five-families-controls-stay-pages-go.md) (the tab), [ADR-321](ADR-321-mcp-write-tool-category.md) (capability grants), [ADR-386](ADR-386-trust-opening-entries-in-the-registry.md)–[ADR-391](ADR-391-take-on-report-and-sign-off.md) (the dataset decisions); [architecture §17.2.1–17.2.4, §17.3.1–17.3.2, §17.10 X5](../architecture/phase93-client-trust-take-on.md); slice 653A.
