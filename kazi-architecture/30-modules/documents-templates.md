# Documents & Templates

**Bounded context:** see [`10-bounded-contexts.md` § documents-templates](../10-bounded-contexts.md). Sibling modules: [`proposals-acceptance.md`](proposals-acceptance.md) (consumes `GeneratedDocument` for e-sign), [`invoicing.md`](invoicing.md) (uses the same render pipeline for invoice PDF), [`customer-portal.md`](customer-portal.md) (download surface), [`automation.md`](automation.md) (generate-document action), [`packs.md`](packs.md) (template + clause pack mechanism), [`integration-ports.md`](integration-ports.md) (S3 `StorageService` port).

---

## 1. Purpose

Two intertwined concerns under one module:

1. **Document storage** — uploaded files (FICA/KYC scans, signed PDFs, working files) and rendered output, persisted to S3 and surfaced through `Document` rows. The S3 dependency is hidden behind a `StorageService` port (`integration/storage/StorageService.java`) with an `S3StorageAdapter` for prod and an `InMemoryStorageService` for tests.
2. **Template-driven generation** — author once, render N times. `DocumentTemplate` holds Tiptap-JSON content with Mustache placeholders; `DocumentGenerationService` resolves the placeholders against an entity-specific context (project / customer / invoice / proposal) and writes the rendered output back to S3 as a `GeneratedDocument`. A second pipeline merges author-supplied `.docx` templates with the same context (Phase 42).

The terminology is load-bearing: `Document` ≠ `GeneratedDocument` (`glossary.md:109,137`). Uploaded files are `Document`. Rendered output is a `GeneratedDocument` that **may** be promoted to a `Document` for the customer-portal surface.

> Tiptap, not Thymeleaf. Document rendering uses Tiptap JSON; Thymeleaf survives only as a dev-portal harness (see `glossary.md:319` and `MEMORY.md` "Tiptap not Thymeleaf"). ADR-056/057/058/059 were authored when the engine was Thymeleaf+OpenHTMLToPDF; the rendering substrate has since migrated to Tiptap (Phase 31) — the architectural decisions on storage (DB), context assembly (builder→Map), and customisation (clone-and-edit) still apply unchanged.

---

## 2. Entities owned

| Entity | Path | Key fields | Notes |
|---|---|---|---|
| `Document` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/Document.java:16` | `projectId`, `customerId`, `fileName`, `contentType`, `size`, `s3Key`, `status`, `scope`, `visibility`, `uploadedBy` | Single table, scope enum + nullable FKs (ADR-018). `scope ∈ {ORG, PROJECT, CUSTOMER}` (ADR-018:13). `visibility ∈ {INTERNAL, SHARED}` — SHARED gates portal exposure (ADR-018:47). |
| `DocumentTemplate` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java:22` | `name`, `slug`, `category`, `primaryEntityType`, `content (jsonb)`, `source`, `packId` | `content` is Tiptap JSON (`glossary.md:113,270`). DB-storage per ADR-057. `source ∈ {PLATFORM, ORG_CUSTOM}` per ADR-059 (clone-and-edit). |
| `GeneratedDocument` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocument.java:20` | `templateId`, `primaryEntityType`, `primaryEntityId`, `s3Key`, `documentId`, `generatedBy` | Output of a render. Optionally `documentId` is non-null when promoted to a `Document`. |
| `Clause` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/Clause.java:23` | `title`, `slug`, `description`, `body (jsonb)` | Reusable clause-library entry. *Note: package is top-level `clause/`, not nested under `template/` as A1 §1 line 22 implies.* |
| `TemplateClause` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/TemplateClause.java` | `templateId`, `clauseId`, `position` | Many-to-many association, ordered. Snapshot semantics on render (ADR-105). |

`Document.scope` constraint per ADR-018:38: `ORG` ⇒ both FKs null; `PROJECT` ⇒ projectId not null; `CUSTOMER` ⇒ customerId not null. `S3 key` follows `org/{orgId}/...` pattern with scope-specific prefixes (ADR-018:54).

Total: **5 JPA entities** in this module (the A1 entity catalogue at `_discovery/A1-backend-map.md:174-259` lists Document, DocumentTemplate, GeneratedDocument; Clause + TemplateClause discovered in `clause/` package).

---

## 3. REST surface

### Documents (uploads / downloads)

| Method | Path | Capability | Purpose |
|---|---|---|---|
| `POST` | `/api/projects/{projectId}/documents/upload-init` | MEMBER+ | Get presigned S3 PUT URL + reserved `Document` row in `PENDING` status |
| `POST` | `/api/documents/{id}/confirm` | MEMBER+ | Promote `PENDING → READY` after browser PUT completes; emits `DocumentUploadedEvent` |
| `GET` | `/api/documents/{id}/presign-download` | MEMBER+ | Time-limited GET URL |
| `DELETE` | `/api/documents/{id}` | ADMIN+ | Soft-delete (S3 object retained for retention) |
| `GET` | `/api/projects/{projectId}/documents` | MEMBER+ | List by project |
| `GET` | `/api/customers/{customerId}/documents` | MEMBER+ | List by customer (CUSTOMER-scope) |

`→ _discovery/A1-backend-map.md:393` confirms ~5 endpoints across `DocumentController`. The two-phase upload (`upload-init` then `confirm`) is the deliberate pattern — direct browser→S3 upload with backend-issued presigned URL.

### Document templates

| Method | Path | Capability | Purpose |
|---|---|---|---|
| `GET` | `/api/document-templates` | MEMBER+ | List metadata-only projection (ADR-057:62) |
| `GET` | `/api/document-templates/{id}` | MEMBER+ | Full content (Tiptap JSON) |
| `POST` | `/api/document-templates` | ADMIN+ | Create `ORG_CUSTOM` template |
| `PUT` | `/api/document-templates/{id}` | ADMIN+ | Edit (only `ORG_CUSTOM` editable per ADR-059) |
| `POST` | `/api/document-templates/{id}/clone` | ADMIN+ | Clone PLATFORM → ORG_CUSTOM (ADR-059:62) |
| `POST` | `/api/document-templates/{cloneId}/reset` | ADMIN+ | Hard-delete clone, surface PLATFORM original (ADR-059:63) |
| `POST` | `/api/document-templates/{id}/generate` | MEMBER+ | Render against `(primaryEntityType, primaryEntityId)` → `GeneratedDocument`; emits `DocumentGeneratedEvent` |

### Clauses

| Method | Path | Capability | Purpose |
|---|---|---|---|
| `GET` | `/api/clauses` | MEMBER+ | List clause library |
| `POST` / `PUT` / `DELETE` | `/api/clauses[/{id}]` | ADMIN+ | CRUD |
| `GET` / `POST` / `DELETE` | `/api/document-templates/{id}/clauses` | ADMIN+ | Manage `TemplateClause` associations + ordering |

`ClauseController` and `TemplateClauseController` live in `clause/`. Concrete REST shape implied by the controller pair (`clause/ClauseController.java`, `clause/TemplateClauseController.java`) — exact endpoint enumeration deferred until controller-by-controller pass.

---

## 4. Frontend pages / components

| Surface | Path | Notes |
|---|---|---|
| Template library | `frontend/app/(app)/org/[slug]/settings/templates/page.tsx` | List + create/clone/edit (`A2 §2`, route line 217) |
| Clause library | `frontend/app/(app)/org/[slug]/settings/clauses/page.tsx` | List + CRUD (`A2 §2`, route line 219) |
| Document upload UI | Embedded in project detail (`app/(app)/org/[slug]/projects/[id]/page.tsx` documents tab) and customer detail (`app/(app)/org/[slug]/customers/[id]/page.tsx`) | Two-phase: presign → browser PUT → confirm |
| Tiptap editor components | `frontend/components/templates/` and `frontend/components/clauses/` | Tiptap React editor with Mustache placeholder helpers; client-only (`"use client"`) |
| Types | `frontend/lib/types/document.ts` | `Document`, `GeneratedDocument`, `TemplateListResponse` (`A2 §4`, line 356) |

The Tiptap editor is the only place `motion/react` and `@tiptap/*` are imported at scale — both are client-only per the conventions in `_discovery/A2-frontend-map.md:451`.

---

## 5. Domain events

| Event | Publisher | Path |
|---|---|---|
| `DocumentUploadedEvent` | `DocumentService.confirmUpload(...)` | `→ _discovery/A1-backend-map.md:456` |
| `DocumentGeneratedEvent` | `DocumentGenerationService.generate(...)` | `→ _discovery/A1-backend-map.md:457` |

**Consumers:**
- `notification/NotificationService` — listens to both for in-app + email notifications (`A1 §4`, line 475).
- `portal/readmodel/*` — listens to `DocumentGeneratedEvent` (only) to project the entry into portal read-model tables for the customer download surface (`A1 §4`, line 476). Uploaded `Document`s are visible in portal only when `visibility = SHARED`.
- `automation/AutomationEventListener` — every `DomainEvent` lands here; tenants can write rules on either event (`A1 §4`, line 474).

Both consumers run under `@TransactionalEventListener(AFTER_COMMIT)` (per `_discovery/A6-cross-cutting.md` §6 pattern) — email is irreversible and read-model writes must reflect committed state.

---

## 6. Cross-cutting touchpoints

### S3 port

`StorageService` is a port (interface) under `integration/storage/`:

- `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/storage/StorageService.java` — interface (`presignPut`, `presignGet`, `delete`, `head`).
- `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/storage/s3/S3StorageAdapter.java` — AWS SDK v2 implementation, used in `local`, `dev`, `prod` profiles.
- `InMemoryStorageService` — `ConcurrentHashMap`-backed test double; auto-registered as `@Primary` by `TestcontainersConfiguration` per `backend/CLAUDE.md` "Testcontainers Policy". Same key-validation regex as the S3 adapter.

This is the same port surfaced in `_discovery/A1-backend-map.md:555` and `30-modules/integration-ports.md`. It is **not** in the `IntegrationDomain` enum (no `STORAGE` domain) — storage is foundational and unguarded, like EMAIL/PAYMENT/KYC (`_discovery/A6-cross-cutting.md` §5).

### Document scope and visibility

`scope ∈ {ORG, PROJECT, CUSTOMER}` per ADR-018. The CHECK constraint enforces FK validity at the DB level — the application layer can rely on it. `visibility ∈ {INTERNAL, SHARED}` is the portal-exposure gate; default `INTERNAL` keeps everything staff-only. SHARED is required (alongside `visibility` checks in `portal/readmodel/`) before a customer can see a document.

### Word DOCX merge pipeline (Phase 42)

A second output adapter alongside the Tiptap pipeline. Per `_discovery/A5-phase-doc-skim.md:45`: "Upload `.docx` with merge fields, auto-discover, generate filled `.docx`. Shipped (active). Second output adapter alongside Tiptap pipeline; shares `TemplateContextBuilder`."

Both adapters consume the same `Map<String, Object>` context produced by `TemplateContextBuilder` (ADR-058). The upload-time merge-field discovery scans the .docx XML for `{{ ... }}` tokens and persists them as the template's expected variable set, so the editor can show a "missing variable" warning when a generation context lacks a token.

### Snapshot semantics (ADR-068, ADR-105)

`GeneratedDocument` is a snapshot of `(template content + clause bodies + entity context)` at render time (ADR-068). Editing a template after rendering does not mutate already-rendered documents. This is the same principle as project templates and checklist instances — predictable behaviour over live binding (`_discovery/A4-adr-triage.md` and ADR-068:30).

For clauses, ADR-105 narrows the snapshot depth to "clause body at render time" — referenced clauses are rendered in-line via string concatenation (ADR-104) and the rendered output captures the resolved HTML, not a clause FK.

### Audit

Every mutating call (`uploadInit`, `confirm`, `delete`, `generate`, template CRUD, clause CRUD) emits an audit event via `AuditService.log(...)` in the same transaction (`_discovery/A6-cross-cutting.md` §3). No event-bus indirection; audit cannot lie about a rollback.

---

## 7. Vertical specifics

Templates and clauses are vertical-specific, but the *mechanism* is universal: they ride the [`packs.md`](packs.md) rail.

- `template/TemplatePackInstaller` — installs `DocumentTemplate` rows (source = `PLATFORM`, `packId` set). `_discovery/A1-backend-map.md:231,558` confirms `PackInstaller` SPI is implemented per pack type (field, compliance, **template**, clause, ...).
- `clause/ClausePackSeeder` (alongside `clause/ClausePackDefinition`) — installs `Clause` rows similarly.

Per-vertical pack inventory:

- **legal-za** ships engagement-letter / mandate / FICA / Section 86 statement templates and legal-specific clauses (payment terms, conflict, Section 86 advisory, mandate scope).
- **accounting-za** ships engagement-letter / IRBA SAICA-style proposal / management-account templates and accounting-specific clauses.
- **consulting-za** / **consulting-generic** ship SOW / NDA / proposal templates with consulting-flavoured clauses.

Switching profiles only **adds** packs (`_discovery/A6-cross-cutting.md` §4 "Known fragility" line 467) — orphaned legal templates remain installed after a `legal-za → consulting-generic` switch, but become unreachable from UI because the documents-templates module isn't itself module-gated; users can still see them in `/settings/templates`. This is a documented gap.

The module is **not** in any vertical's `enabledModules` list — it's universally present (`_discovery/A1-backend-map.md` Table 7, modules 1–18 are core-SaaS).

---

## 8. Active ADRs

| ADR | Subject | Status |
|---|---|---|
| ADR-018 | Document scope representation (single table + scope enum + nullable FKs) | Accepted |
| ADR-056 | PDF engine selection (OpenHTMLToPDF) | Accepted, but predates Tiptap migration — see Open Questions |
| ADR-057 | Template storage (DB TEXT/JSONB columns, not S3) | Accepted |
| ADR-058 | Rendering context assembly (builder → `Map<String, Object>`, not raw entities) | Accepted |
| ADR-059 | Template customisation (clone-and-edit, separate `ORG_CUSTOM` records) | Accepted |
| ADR-068 | Snapshot-based templates (no live binding from instances back to templates) | Accepted |
| ADR-104 | Clause rendering strategy (string concatenation before render, single pass) | Accepted |
| ADR-105 | Clause snapshot depth | Accepted |
| ADR-106 | Template-clause placeholder strategy | Accepted |

ADRs 056/057/058/059 were authored when the substrate was Thymeleaf+OpenHTMLToPDF (Phase 12). The architectural conclusions — DB storage, builder-pattern context assembly, clone-and-edit customisation — are substrate-independent and still hold under the Tiptap pipeline (Phase 31). The PDF engine question (ADR-056) is the one that's genuinely unsettled — see §10.

---

## 9. Key flows

- **Engagement-letter flow** (proposal → engagement → first invoice) — see [`50-flows/proposal-to-engagement-to-billing.md`](../50-flows/proposal-to-engagement-to-billing.md). Documents-templates emits the `GeneratedDocument` that becomes the engagement letter; `proposals-acceptance` attaches it to an `AcceptanceRequest`; on accept, the customer-lifecycle status moves to `ACTIVE` and the project transitions out of proposal-pending.
- **Matter-to-cash flow** — see [`50-flows/matter-to-cash.md`](../50-flows/matter-to-cash.md). The invoice PDF is itself a `GeneratedDocument` rendered from a system-source `DocumentTemplate` (invoice PDF). When trust-flagged invoices are involved, the `TrustBoundaryGuard` (ADR-276) blocks downstream Xero export of the rendered PDF — but the rendering itself is unguarded.
- **Document upload → portal visibility** — `DocumentUploadedEvent` (after-commit) → `portal/readmodel/*` projects to portal read-model **only if** `visibility = SHARED`. INTERNAL documents stay staff-only forever unless explicitly shared. See [`customer-portal.md`](customer-portal.md).

---

## 10. Open questions / known fragility

1. **PDF engine after Tiptap migration.** ADR-056 chose OpenHTMLToPDF for the Thymeleaf→HTML→PDF pipeline. Phase 31 replaced Thymeleaf with Tiptap as the authoring substrate, but the rendering chain still ends in PDF. Two unanswered questions: (a) is OpenHTMLToPDF still the engine, fed by Tiptap-rendered HTML? (b) does the Tiptap pipeline emit DOCX/PDF directly via a Tiptap-native renderer? The discovery note says "Tiptap rewrite" (`A5 §`, phase31) but the engine binding is not explicit. Needs a verifying read of `template/DocumentGenerationService.java` and `template/PdfRenderingService.java` (if still named that). ADR-056 should be amended or superseded.
2. **DOCX pipeline maturity (Phase 42).** Phase 42 is "Shipped (active)" per A5, but acceptance/edge-case behaviour is not documented in this architecture pass: how are unsupported merge-field types handled? What happens when the .docx contains nested merge fields or fields inside table cells? Failure mode (full-document fail vs. best-effort) is unspecified — needs verification before the legal vertical leans on it.
3. **Profile-switch orphans.** Per `_discovery/A6-cross-cutting.md` §4 (line 467), switching `legal-za → consulting-generic` does not uninstall legal template packs. The `/settings/templates` page is not module-gated, so orphaned legal templates remain visible. Either pack uninstall on profile change or templates page module-gating would close this — both are product decisions.
4. **`Document.visibility = SHARED` is org-wide.** There is no per-portal-contact ACL — flipping a document to SHARED makes it visible to every linked portal contact for the customer. For sensitive docs (FICA scans, Section 86 statements) this is too coarse. Either a per-contact share list or visibility tiers (`SHARED_ALL`, `SHARED_OWNER_ONLY`) would address it. Not yet ADR'd.
5. **Clause package location is inconsistent with discovery.** `_discovery/A1-backend-map.md:22` says clauses live "inside `template`"; the actual package is top-level `clause/` (verified at `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/`). A1 needs a small correction; this module page treats `clause/` as authoritative.
