# Data Protection

**Status:** filled (Phase D part 1).

## 1. What this concern covers

Data-subject rights compliance, retention enforcement, and secret/credential safety. The product surface is South African PAIA (Promotion of Access to Information Act) plus a GDPR-style DSAR shape; the implementation surface is `datarequest/`, `compliance/`, the `Customer.lifecycleStatus` machine, the project retention clock, and `EncryptedDatabaseSecretStore`. The single load-bearing principle is **anonymisation-over-deletion** (ADR-062): the firm's record of work done is preserved for legal/regulatory reasons; the personally-identifiable links to a real person are severed once consent or retention period expires.

Companion pages: [`20-cross-cutting/audit-and-compliance.md`](audit-and-compliance.md) (the audit pattern this concern leans on), [`30-modules/customer-lifecycle.md`](../30-modules/customer-lifecycle.md) (DSAR endpoints + anonymisation flow), [`30-modules/audit.md`](../30-modules/audit.md) (ADR-262 unsanitised audit slice), [`30-modules/integration-ports.md`](../30-modules/integration-ports.md) (encrypted secret storage).

## 2. Anonymisation-over-deletion (ADR-062)

The system never hard-deletes a `Customer` once it has reached `ACTIVE` or later. Instead, it transitions through `OFFBOARDING → OFFBOARDED → ANONYMIZED` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/LifecycleStatus.java:7`; `customer-lifecycle.md:12`). The `ANONYMIZED` state replaces PII fields — name, email, ID number, addresses — with deterministic placeholders while retaining the structural row so audit, project, time-entry, and invoice rows still resolve their FK to a non-orphan parent.

- Sweep implementation: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataAnonymizationService.java` (`customer-lifecycle.md:120`; `audit-and-compliance.md:58`).
- Preview endpoint (dry-run) + execute endpoint: `datarequest/AnonymizationController.java:16` — `GET /api/customers/{id}/anonymize/preview`, `POST /api/customers/{id}/anonymize`.
- Frontend type: `frontend/lib/types/customer.ts:203` — `AnonymizationResult`.
- Capability: anonymise is owner/admin-only via `@RequiresCapability` (`customer-lifecycle.md:123`).

Why anonymise rather than delete: legal records (especially the legal vertical) must be retained for compliance — Section 86 trust ledgers, Section 23 LSSA registers, FICA records — but the personal link must be severed once the retention basis expires. Hard-deleting the row would break audit linkage and could orphan invoices and trust transactions that statutorily must persist. ADR-193 reinforces this — clarifying the boundary between anonymisation (default) and outright row deletion (forbidden once `ACTIVE`).

The export bundle must be staged **before** the anonymisation sweep runs (ADR-196; `customer-lifecycle.md:146`) — once anonymised, the data is unrecoverable from the live tables. The pre-anonymisation export is the only path back to the original record state.

## 3. Retention clocks

Two independent retention clocks coexist:

| Clock | Anchor | Source | Set on |
|---|---|---|---|
| Customer | `Customer.offboardedAt` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java:78` | `OFFBOARDING → OFFBOARDED` transition (`Customer.java:196`) |
| Project | `Project.retentionClockStartedAt` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:97` | `complete()` (`:259`) and `close()` (`:303`); preserved on reopen (`:311`) |

These clocks **do not cascade** (`customer-lifecycle.md:121`, `:164`). Closing every project on a customer does not auto-offboard the customer; offboarding the customer does not close projects. This is deliberate per ADR-249 — the project clock is the load-bearing one for legal matter retention, and the canonical anchor is the *earliest* close (the reopen flow preserves the original clock so re-completing a previously-closed project does not restart retention).

ADR-249 frames this as the "you cannot un-stamp time" rule that compliance leans on. ADR-194 establishes that retention is per-customer-type and per-jurisdiction (not org-wide), and ADR-197 anchors that DSAR deadlines are computed at read time from anchor + jurisdiction policy rather than stored.

The independence of the two clocks is a known seam (§8) — operators have been confused in past QA cycles that closing all of a customer's matters does not progress the customer towards anonymisation.

## 4. DSAR / PAIA flow

The durable handle for an in-flight request is the `DataSubjectRequest` entity (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequest.java`; `customer-lifecycle.md:15`). The request type enum carries `ACCESS / EXPORT / ANONYMIZATION / DELETION` (`frontend/lib/types/customer.ts:203` — `DataRequestType`). Workflow:

1. **Request created** — admin (firm-side UI at `/compliance/requests`) or via portal (verify — the controller exposes `POST /api/data-requests` per `customer-lifecycle.md:66`; the portal entry path is via the customer-lifecycle module surface).
2. **Status tracked** on `DataSubjectRequest`; status transitions emit audit events through the same path as everything else (`audit-and-compliance.md:62`).
3. **System stages export** — `datarequest/DataExportService.java` bundles the customer's structural data, custom fields, documents, invoices, and audit-trail slice. The export is durable on disk before any destructive operation runs (ADR-196).
4. **Manual review queue** — owner/admin reviews the staged bundle in the firm-side UI before approving destructive transitions.
5. **On approval** — `ExportPdfPipeline` assembles the report through the same Tiptap → PDF pipeline used by invoices and statements (ADR-263; `audit-and-compliance.md:60`). Single PDF code path; the audit slice is included unsanitised (next bullet).
6. **Audit emission throughout** — every step writes an audit row in the same transaction as the change (`audit-and-compliance.md:7`). Per ADR-262 (`30-modules/audit.md:87`), the audit slice in the DSAR pack is **unsanitised** — full `details`, IP addresses, user agents, and justifications ship intact. The reasoning is POPIA §23: the data-subject is entitled to know who handled their request and when, including the operator identifiers that the live portal would otherwise redact. Two channels (live portal vs DSAR pack), two intentional sanitisation postures.

REST surface (`customer-lifecycle.md:66-70`):

- `datarequest/DataRequestController.java:27` — `/api/data-requests` CRUD + status transitions + export staging + execute-deletion + deadline check (8 endpoints). Listed as a thin-controller-discipline violator (TD-009 in `backend/CLAUDE.md`); new endpoints under this prefix must obey the one-service-call rule.
- `datarequest/DataExportController.java:14` — `POST /api/customers/{id}/data-export`, `GET /api/data-exports/{id}`, `GET /api/data-exports`.
- `datarequest/DataProtectionController.java:13` — `POST /api/settings/paia-manual/generate` (ZA-only).
- `datarequest/ProcessingActivityController.java:21` — `/api/settings/processing-activities` CRUD (POPIA register entries; `glossary.md:214`).
- `datarequest/PaiaManualGenerationService.java` — generates the PAIA manual from `ProcessingActivity` records and `JurisdictionDefaults`.

Frontend pages (`customer-lifecycle.md:80-86`):

- `/compliance` — dashboard with in-flight data requests and dormancy check.
- `/compliance/requests` — DSAR queue.
- `/compliance/requests/[id]` — DSAR detail.
- `/settings/compliance` — retention policies, dormancy threshold, processing-activity register.
- `/customers/[id]` — data-protection tab.

Jurisdiction handling: ZA is the only fully-implemented case (PAIA manual generation, retention defaults). Non-ZA tenants fall through to a generic default set in `datarequest/JurisdictionDefaults.java` (`customer-lifecycle.md:131`; `audit-and-compliance.md:121`). DSAR deadline math anchors to request-received-at, not status-changed-at (ADR-195).

## 5. Encrypted secret storage

Tenant-supplied integration credentials (BYOAK — bring-your-own-API-key) are stored encrypted at rest in the per-tenant `org_secrets` table, never on the `OrgIntegration` row itself. Implementation is `EncryptedDatabaseSecretStore` (ADR-090; `30-modules/integration-ports.md:26`, `:85-87`):

- Port interface: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/SecretStore.java:7` — `store / retrieve / delete / exists`.
- Sole implementation: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/EncryptedDatabaseSecretStore.java:19`. `AES/GCM/NoPadding`, 12-byte SecureRandom IV per write (`:104-108`), 128-bit tag (`:23-25`).
- Master key from `integration.encryption-key` env var, validated to 32 bytes at `@PostConstruct` (`:42-56`); the application fails to start if absent or wrong length.
- Per-tenant: `org_secrets` lives in the tenant schema. Key naming is `{domain}:{slug}:api_key` via `integration/IntegrationKeys.java:15` — single source of truth so domain code and UI key-suffix logic agree.
- `OrgSecret.keyVersion` exists (currently always `1`) to support future master-key rotation.

DSAR exports include the *fact of* a connected integration (provider slug, last-6 of API key from `OrgIntegration.keySuffix`) but **never the credential itself** — `DataExportService` explicitly redacts `org_secrets` (`_discovery/A6-cross-cutting.md:421`).

ADR-201 reuses the same `SecretStore` for AI provider keys — the AI-assistant module does not introduce a parallel credential store. ADR-177 frames "API key encryption" generally; ADR-090 is the implementation.

Cross-link: [`30-modules/integration-ports.md` § 6 Secret storage](../30-modules/integration-ports.md#secret-storage-adr-090) for the port-pattern context. The secret store is database-backed (not Vault, not KMS) — a deliberately small dependency footprint.

## 6. Modules affected

- **`customer-lifecycle`** — owns DSAR durability (`DataSubjectRequest`), retention anchor (`Customer.offboardedAt`), anonymisation sweep, and the user-visible compliance pages (`30-modules/customer-lifecycle.md`).
- **`audit`** — provides the unsanitised audit slice for DSAR packs (ADR-262), the in-transaction emission pattern for every status transition, and the export-is-auditable property (ADR-264). Audit retention is configurable but the purge job is deferred (`30-modules/audit.md:85`).
- **`integration-ports`** — encrypted secret storage (`EncryptedDatabaseSecretStore`); DSAR redacts `org_secrets` (`30-modules/integration-ports.md:85-87`).
- **`tenancy-provisioning`** — schema-per-tenant isolation is the foundational data-protection control (ADR-064). Cross-tenant data access requires `TenantScopedRunner` operator iteration; there are no shared tenant tables. Background sweeps for retention/dormancy iterate via `TenantScopedRunner.forEachTenant` (`audit-and-compliance.md:74`).
- **`projects`** — owns the `Project.retentionClockStartedAt` clock (ADR-249).

## 7. Active ADRs

All Active per `90-adr-index.md:198-216`:

| ADR | Title |
|---|---|
| ADR-062 | anonymization-over-hard-deletion |
| ADR-090 | secret-storage-strategy (AES-GCM in `EncryptedDatabaseSecretStore`) |
| ADR-177 | api-key-encryption (BYOAK general framing) |
| ADR-193 | anonymization-vs-deletion (boundary clarification) |
| ADR-194 | retention-policy-granularity (per-customer-type + per-jurisdiction) |
| ADR-195 | dsar-deadline-calculation (anchored to received-at) |
| ADR-196 | pre-anonymization-export-storage (stage before sweep) |
| ADR-197 | calculated-vs-stored-deadlines (compute at read time) |
| ADR-199 | filing-status-lazy-creation (processing-activity records on first need) |
| ADR-201 | secret-store-reuse-for-ai-keys (no parallel store) |
| ADR-249 | retention-clock-starts-on-closure |
| ADR-262 | dsar-audit-trail-unsanitised (POPIA §23) |
| ADR-263 | audit-pdf-via-tiptap-pipeline (single PDF code path) |
| ADR-264 | audit-export-is-auditable |

ADR-064 (dedicated-schema-only) and ADR-T001 (schema-per-tenant) provide the multi-tenancy substrate but are not data-protection-specific — see [`20-cross-cutting/multitenancy.md`](multitenancy.md).

## 8. Known fragilities / open questions

- **Two non-cascading retention clocks.** `Customer.offboardedAt` and `Project.retentionClockStartedAt` are independent (`customer-lifecycle.md:164`; `audit-and-compliance.md:74`). Closing all of a customer's projects does not auto-offboard the customer; offboarding the customer does not close projects. Deliberate per ADR-249 but a recurring source of operator confusion in QA cycles.

- **Anonymisation is irreversible.** ADR-062 — anonymisation replaces PII with placeholders while retaining structural rows for audit linkage; there is no undo (`audit-and-compliance.md:123`). If a customer returns post-anonymisation the operator must create a new customer. The pre-anonymisation export bundle (ADR-196) is the only path to recreate the original record state from durable storage.

- **Audit retention has no compaction.** `purge-enabled: false` is the default (ADR-027); the scheduled purge job is **not yet implemented** (`30-modules/audit.md:85`, `:136`; `audit-and-compliance.md:78`). For long-running tenants this means unbounded growth of `audit_events`. Index degradation, Neon storage cost, and filter-scan latency are open empirical questions.

- **No scheduled retention purge of expired data.** Anonymisation is currently triggered point-in-time by DSAR or operator action — there is no scheduled job that surveys customers/projects whose retention period has expired and queues them for the anonymisation sweep. The dormancy scheduler (`DormancyScheduledJob` daily 02:00) only handles `ACTIVE → DORMANT`, not the downstream `OFFBOARDED → ANONYMIZED` transition. Any retention-driven scrub today is operator-initiated.

- **Master-key rotation for `EncryptedDatabaseSecretStore` is unbuilt.** `OrgSecret.keyVersion` exists (always `1`) but no rotation job is implemented (`30-modules/integration-ports.md:138`). Rotating today means a manual re-encryption sweep across every tenant's `org_secrets`. Acceptable for v1 since the master key is environment-supplied and rotation is rare, but a known gap.

- **PAIA jurisdiction is ZA-only.** `JurisdictionDefaults.java` has ZA fully implemented; non-ZA tenants get a generic default set (`customer-lifecycle.md:131`; `audit-and-compliance.md:121`). The expansion model (adding GDPR / HIPAA / Brazil LGPD) is untested — what changes besides the defaults map? Likely the manual-generation template, status transition deadlines (ADR-195/197), and possibly the unsanitised-export posture (ADR-262 turns on POPIA §23 reasoning specifically — a different jurisdiction may justify a different sanitisation posture).

- **Cross-vertical retention rules.** The legal vertical has Section-86 / LSSA-specific retention obligations (trust ledgers, statement-of-account archives, conflict-check histories) that differ from base retention. ADR-194 establishes per-jurisdiction granularity but the legal-vertical-specific retention rules are not yet codified into `JurisdictionDefaults` — they are encoded in the legal-vertical compliance packs and module-gated services. Cross-link to [`60-verticals/legal-vertical.md`](../60-verticals/legal-vertical.md) when filled.

- **Reflexive audit-export recursion.** ADR-264 mandates that a successful CSV/PDF export emits `audit.export.generated` — so the next export captures the previous export's audit row. Termination is by usage cadence, not by code (`audit-and-compliance.md:125`). Not a correctness issue but a noise floor on tenants with frequent scheduled exports.
