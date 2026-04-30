# Fix Spec: GAP-L-74 — Closure pack (closure letter + SoA) not surfaced to portal Documents tab

## Problem (verbatim from QA evidence)

Day 61 cycle-1 verify (2026-04-25 ~21:42 SAST,
`qa_cycle/checkpoint-results/day-61.md` §"Day 61 Re-Verify — Cycle 1"
Step 61.2 "Documents tab"). Sipho Dlamini's portal session against
matter `e788a51b-3a73-…` (CLOSED at 2026-04-25 20:44:20):

- Portal `/projects/e788a51b-…` Documents tab lists ONLY the 6 FICA
  upload duplicates (`fica-id.pdf` / `fica-address.pdf` /
  `fica-bank.pdf`, 344 B each, status Pending).
- The two firm-side closure-pack outputs are MISSING from Sipho's view:
  - **(L-74a)** `matter-closure-letter-dlamini-v-road-accident-fund-2026-04-25.pdf`
    (1.6 KB, `documents.2bad9b06-…`, `scope=PROJECT`, `status=UPLOADED`,
    `visibility=INTERNAL`).
  - **(L-74b)** `statement-of-account-dlamini-v-road-accident-fund-2026-04-25.pdf`
    (4 109 B, `generated_documents.c0931e79-…`,
    `document_id IS NULL` — no row in `documents` at all).

Evidence:
`qa_cycle/checkpoint-results/day-61-cycle1-portal-matter-detail.yml`
lines 117-186; `day-61-cycle1-portal-projects.yml`. Side-evidence —
portal Trust ledger and Invoices both render Sipho's data correctly
with no leakage and no blockers, so the portal data-pipe is healthy
generally — this is specifically a Documents-surface filter +
structural gap.

**Net effect**: Sipho cannot see, download, or reconcile the SoA from
his portal session per scenario steps 61.2 / 61.3 / 61.4 / 61.7 / 61.8,
and cannot review the closure letter per step 61.8.

**Blocks**:
- **Day 61** entire flow (61.2 through 61.8) — re-walk requires this fix.
- **Day 88** activity-feed wow moment (88.4 portal-side activity trail
  expects "SoA download (Day 61)" entry — impossible if Sipho can't
  reach the SoA).
- Exit checkpoint **E.13** (closure-pack delivered to client).

Per dispatch hard rule "If a Day 61 checkpoint blocks (e.g., SoA not
in portal contact's view, download 404, etc.), STOP, log gap, exit",
this gap blocks slice progression entirely — cannot be worked around
with REST/SQL.

## Root Cause (grep-confirmed)

Two distinct sub-issues, both in backend, on the same fix surface
(closure-pack document persistence + portal visibility filter).

### L-74a — Closure letter is INTERNAL, portal filter requires SHARED

Closure-letter generation goes through the standard
`GeneratedDocumentService.generateDocument(saveToDocuments=true)` path
which DOES create a paired `Document` row — but the constructor used
defaults `visibility=INTERNAL` and the portal Documents API filters
to `SHARED` only.

- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java`
  line 73 — `static final String CLOSURE_LETTER_SLUG = "matter-closure-letter";`
- Line 165-167 — `req.generateClosureLetter()` →
  `self.generateClosureLetterSafely(projectId, …)` (REQUIRES_NEW).
- Line 264-266 — calls
  `generatedDocumentService.generateForProject(projectId, CLOSURE_LETTER_SLUG, actingMemberId)`.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java`
  line 282 — `generateForProject` calls
  `generateDocument(template.getId(), projectId, true, true, List.of(), actingMemberId)`
  — `saveToDocuments=true`.
- Line 199-201 — `if (saveToDocuments) { var document =
  createLinkedDocument(…); generatedDoc.linkToDocument(document.getId()); }`
  — paired `Document` row IS created and linked back via
  `generated_documents.document_id`.
- Line 644-687 — `createLinkedDocument(…)` for `entityType=PROJECT`
  uses the `Document(projectId, fileName, contentType, size,
  uploadedBy)` constructor (lines 653-659).
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/Document.java`
  line 71 — that 5-arg constructor sets `this.visibility =
  Visibility.INTERNAL;` unconditionally.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalQueryService.java`
  line 78-85 — `listProjectDocuments(projectId, customerId)` filters
  `documentRepository.findProjectScopedByProjectId(projectId).stream()
  .filter(doc -> Document.Visibility.SHARED.equals(doc.getVisibility()))`
  — INTERNAL docs are dropped.

So the closure letter `documents` row exists with proper `project_id`
linkage (firm-side Documents tab confirms this in QA evidence) but
`visibility='INTERNAL'`, so the portal API filter at line 83 of
`PortalQueryService` excludes it.

### L-74b — SoA never gets a `documents` row at all

`StatementService.generate` bypasses
`GeneratedDocumentService.generateDocument` entirely (with stated
reason — period-bound context doesn't fit the standard
`TemplateContextBuilder` interface) and persists ONLY a
`GeneratedDocument` row. No paired `Document` row, no
`generated_documents.document_id` link.

- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementService.java`
  lines 95-175 — `@Transactional public StatementResponse generate(…)`.
- Lines 120-139 — constructs
  `new GeneratedDocument(template.getId(), TemplateEntityType.PROJECT,
  projectId, fileName, s3Key, pdfBytes.length, memberId)` and
  `generatedDocumentRepository.save(generatedDoc)` directly.
- **No call to `documentRepository.save(...)`, no
  `generatedDoc.linkToDocument(documentId)`.**
- Lines 39-50 (Javadoc) explicitly note: "Bypasses
  `GeneratedDocumentService#generateDocument` because Statement of
  Account context is period-bound (start/end dates) and the standard
  `TemplateContextBuilder.buildContext(entityId, memberId)` interface
  is too narrow." — but the bypass also drops the
  `createLinkedDocument` step that creates the `documents` row.

Net effect: SoA is structurally orphaned from the documents listing
pipeline. Even if (a) is fixed and the portal filter starts admitting
more visibility levels, the SoA still cannot appear on any Documents
tab (firm-side OR portal-side) until a paired `documents` row is
created.

## Chosen Fix — Two-part, both backend-only, contained to two services

### Part A — `StatementService.generate` creates the paired `Document` row

Mirror the `GeneratedDocumentService.createLinkedDocument` pattern in
`StatementService.generate` (don't refactor — surface area is small,
SoA stays a deliberate bypass per its existing Javadoc rationale).
After persisting the `GeneratedDocument`, also persist a paired
`Document` row pointing at the SAME S3 key, then link via
`generatedDoc.linkToDocument(document.getId())`. Use the
`Document(projectId, fileName, contentType, size, uploadedBy)`
constructor + immediately `setVisibility(Document.Visibility.SHARED)`
(or use the 8-arg scope-aware constructor and pass `SHARED` directly —
see Part C for the rationale on `SHARED` here).

### Part B — `MatterClosureService` closure letter is `SHARED`

After the closure-letter generation in `generateClosureLetterSafely`
returns the `GeneratedDocument`, fetch the linked `Document` (via
`generatedDoc.getDocumentId()`) and toggle its visibility to
`SHARED`. Two viable shapes:

- **Smallest**: in `MatterClosureService.generateClosureLetterSafely`
  after the existing `if (result == null …) return null;` guard,
  resolve the linked document id from
  `result.generatedDocument().getDocumentId()`, then call
  `documentService.toggleVisibility(documentId,
  Document.Visibility.SHARED)`. Keeps closure-letter visibility
  decision co-located with closure logic.
- **Slightly broader**: extend
  `GeneratedDocumentService.generateDocument` to accept an optional
  `Document.Visibility` for the linked document (default INTERNAL,
  callers like closure letter / SoA pass SHARED). More general but
  changes the public method signature; defer to Sprint 2 hardening.

Choose the **smallest** shape for this verify cycle.

### Part C — Audit the portal filter to confirm SHARED is the right gate

`PortalQueryService.listProjectDocuments` line 83 filters `Visibility.SHARED`.
This already aligns with the existing
`DocumentService.toggleVisibility` capability flow used by firm users
who want to make a document visible to portal contacts. Both closure
letter and SoA are by-definition client-facing artefacts (per scenario
steps 60.10 + 61.2 + 61.8), so flipping them to SHARED at generation
time is correct. **No change to `PortalQueryService` needed.**

If the team prefers a separate "PORTAL"/"CLIENT_VISIBLE" enum value
distinct from manually-shared `SHARED` for audit purposes, that's a
Sprint 2 visibility-model refactor (carve out as `L-74-followup`) —
out of scope for this verify cycle.

## Fix — step-by-step

### 1. `StatementService.generate` — persist paired `Document` + link

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementService.java`:

Add to the existing constructor + field list:

```java
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;

private final DocumentRepository documentRepository;

public StatementService(
    …existing 9 params…,
    DocumentRepository documentRepository) {
  …existing assignments…
  this.documentRepository = documentRepository;
}
```

In `generate(...)` after the existing `generatedDoc =
generatedDocumentRepository.save(generatedDoc);` (line 139):

```java
// Create paired Document row so the SoA shows up on portal Documents tab
// (and firm-side Documents tab too — currently it's only visible via
// Statements tab because document_id is NULL). Mirrors
// GeneratedDocumentService.createLinkedDocument PROJECT branch.
var pairedDocument = new Document(
    projectId, fileName, "application/pdf", pdfBytes.length, memberId);
pairedDocument.assignS3Key(s3Key);
pairedDocument.confirmUpload();
pairedDocument.setVisibility(Document.Visibility.SHARED); // client-visible per scenario 61.2
var savedDocument = documentRepository.save(pairedDocument);
generatedDoc.linkToDocument(savedDocument.getId());
// generatedDoc is managed in this @Transactional — no explicit save needed
```

(`Document(UUID, String, String, long, UUID)` is the existing
PROJECT-scope convenience constructor at `Document.java:62`; it
defaults `scope=PROJECT`, `status=PENDING`, `visibility=INTERNAL` —
we override visibility immediately after construction. `assignS3Key`
+ `confirmUpload` flip status to `UPLOADED` so portal presigned-download
checks at `PortalQueryService.getPresignedDownloadUrl` line 170 pass.)

### 2. `MatterClosureService` — flip closure letter visibility to SHARED

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java`:

Inject `DocumentService` (or `DocumentRepository` — `DocumentService`
preferred so the existing audit-event for visibility changes is
emitted, see `DocumentService.toggleVisibility` lines 212-244):

```java
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentService;

private final DocumentService documentService;
```

Add to constructor params + assignment.

In `generateClosureLetterSafely(...)` (lines 261-286), after the
existing letter-id capture:

```java
UUID letterDocId = result.generatedDocument().getId();
UUID linkedDocumentId = result.generatedDocument().getDocumentId();
matterClosureLogRepository.findById(closureLogId).ifPresent(logRow -> {
  logRow.setClosureLetterDocumentId(letterDocId);
  matterClosureLogRepository.save(logRow);
});

// Flip the linked Document to SHARED so it appears on the portal
// Documents tab (per scenario step 61.8). Closure letter is
// always client-facing — visibility decision is deterministic here.
if (linkedDocumentId != null) {
  try {
    documentService.toggleVisibility(linkedDocumentId, Document.Visibility.SHARED);
  } catch (RuntimeException e) {
    log.warn(
        "Failed to flip closure-letter document visibility to SHARED: project={}, doc={}",
        projectId, linkedDocumentId, e);
    // Don't rethrow — close already committed; visibility flip is a
    // best-effort post-step matching the existing letter-generation
    // best-effort contract (REQUIRES_NEW + try/catch).
  }
}

return letterDocId;
```

`DocumentService.toggleVisibility` already validates the visibility
string is one of `INTERNAL`/`SHARED` (lines 213-220), persists the
change, and emits a `document.visibility_changed` audit event. We're
on the existing `REQUIRES_NEW` letter-generation transaction so a
visibility-flip failure rolls back only this letter-step transaction
without affecting the (already-committed) close.

### 3. Tests

#### 3a. `StatementServiceTest` (or new
`StatementServicePortalDocumentTest`) — assert paired Document creation:

```java
@Test
void generate_persistsLinkedDocumentRow_visibleToPortal() {
  // Setup: project with portal contact, period with at least one
  // disbursement (re-use L-71 fixture).
  var response = statementService.generate(projectId, request, memberId);

  // Assert generated_documents row has document_id populated
  var generated = generatedDocumentRepository.findById(response.id()).orElseThrow();
  assertThat(generated.getDocumentId()).as("SoA must have paired Document").isNotNull();

  // Assert documents row is SHARED + UPLOADED + same S3 key
  var paired = documentRepository.findById(generated.getDocumentId()).orElseThrow();
  assertThat(paired.getVisibility()).isEqualTo(Document.Visibility.SHARED);
  assertThat(paired.getStatus()).isEqualTo(Document.Status.UPLOADED);
  assertThat(paired.getS3Key()).isEqualTo(generated.getS3Key());
  assertThat(paired.getProjectId()).isEqualTo(projectId);

  // Assert PortalQueryService surfaces the SoA
  var portalDocs = portalQueryService.listProjectDocuments(projectId, customerId);
  assertThat(portalDocs).extracting(Document::getId).contains(paired.getId());
}
```

#### 3b. `MatterClosureServiceTest` — assert closure letter is SHARED post-close:

```java
@Test
void close_flipsClosureLetterVisibility_toShared() {
  // Setup: project ready for closure (re-use existing closure test
  // fixture from MatterClosureServiceIntegrationTest), generateClosureLetter=true.
  var response = matterClosureService.close(projectId,
      new ClosureRequest(reason, notes, /*generateClosureLetter*/ true,
                        /*override*/ false, null), memberId);

  // Assert closure letter Document is SHARED + visible on portal
  assertThat(response.closureLetterDocumentId()).isNotNull();
  var generated = generatedDocumentRepository.findById(response.closureLetterDocumentId()).orElseThrow();
  var linkedDoc = documentRepository.findById(generated.getDocumentId()).orElseThrow();
  assertThat(linkedDoc.getVisibility()).isEqualTo(Document.Visibility.SHARED);

  var portalDocs = portalQueryService.listProjectDocuments(projectId, customerId);
  assertThat(portalDocs).extracting(Document::getFileName)
      .anyMatch(name -> name.startsWith("matter-closure-letter"));
}
```

(If the existing closure test class doesn't already have the
portal-customer linkage fixture, add the minimum — a `Customer` ACTIVE
+ `customer_projects` link to the test project — using
`TestCustomerFactory` + `TestEntityHelper` per backend/CLAUDE.md.)

## Scope

**Backend-only.** No frontend, no DB migration, no template content
change. Visibility model is unchanged (existing `INTERNAL`/`SHARED`
enum is sufficient for the verify-cycle outcome).

**Files modified:**

1. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementService.java`
   — inject `DocumentRepository`; persist paired `Document` row +
   link via `generatedDoc.linkToDocument(...)` after
   `generatedDocumentRepository.save(...)`.
2. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java`
   — inject `DocumentService`; in `generateClosureLetterSafely`
   call `documentService.toggleVisibility(linkedDocId,
   Document.Visibility.SHARED)` wrapped in try/catch (best-effort,
   close has already committed).

**Files added:**

3. `backend/src/test/.../verticals/legal/statement/` — new test (or
   extension to existing `StatementServiceTest`) asserting paired
   Document row is created with `SHARED` visibility + appears in
   `PortalQueryService.listProjectDocuments`.
4. `backend/src/test/.../verticals/legal/closure/` — new test (or
   extension to existing `MatterClosureServiceIntegrationTest`)
   asserting closure-letter linked Document is flipped to `SHARED`
   post-close + appears in `PortalQueryService.listProjectDocuments`.

**Lines touched (estimate):** ~30 production + ~80 test.

**NEEDS_REBUILD:** true (backend Java changes — Spring restart
required after `bash compose/scripts/svc.sh restart backend`).

## Verification (per QA — re-walk after fix lands)

1. Backend rebuild + restart on Keycloak dev stack (port 8080).
2. From Day 60 final state (Dlamini matter `e788a51b-…` already
   CLOSED, SoA already generated as `generated_documents.c0931e79-…`,
   closure letter already at `documents.2bad9b06-…`):
   - **Re-generate SoA** via Statements tab toolbar to get a fresh
     `generated_documents` row with the new code path (the existing
     `c0931e79-…` was created pre-fix and still has `document_id=NULL`
     — won't appear on portal even after fix). Confirm new row has
     `document_id` populated and a corresponding `documents` row with
     `visibility='SHARED'`, `status='UPLOADED'`, same `s3_key`.
   - **Flip the existing closure letter `documents.2bad9b06-…` to
     SHARED** via the firm-side Documents tab visibility toggle UI
     (one-time data backfill — matches how the new code path will
     write it for future closures). Alternatively re-run a fresh
     close on a NEW test matter to exercise the new path end-to-end.
3. Drive Sipho's portal session via portal `/login` magic-link
   self-service (workaround for L-72) → `/projects/e788a51b-…`
   matter detail → Documents tab.
4. **Expected**: Documents tab now lists the closure letter PDF
   AND the freshly-regenerated SoA PDF alongside the 6 FICA uploads.
   Click Download next to SoA → presigned-URL roundtrip → PDF opens
   cleanly, byte-size matches firm-side.
5. Re-walk Day 61 steps 61.2 → 61.8 against scenario per
   `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md:691-718`.
6. After re-walk passes, advance to Day 75 (weekly digest) → Day 85
   (final closure paperwork) → Day 88 (activity-feed wow moment) →
   Day 90 + exit checkpoints.

## Estimated Effort

**M (~3 hr)** all-in:

- ~20 min — `StatementService.generate` Document persistence (+ inject
  `DocumentRepository`).
- ~15 min — `MatterClosureService.generateClosureLetterSafely`
  visibility flip (+ inject `DocumentService`).
- ~60 min — tests (paired-Document assertion in StatementService;
  closure-letter visibility assertion in MatterClosureService;
  PortalQueryService roundtrip assertion in both).
- ~15 min — restart + browser-driven SoA regen + closure-letter
  visibility data-backfill on existing matter for QA.
- ~30 min — buffer for any test-fixture wiring (PortalContact +
  CustomerProject linkage in legal closure test classes if not
  already present).

Fits the SPEC_READY <2 hr lower bar at the implementation level
(~35 min of code change is the critical path); upper bound covers
test-fixture extension if the closure test class doesn't yet have
portal-customer linkage.

## Sprint 2 follow-up (carved out, NOT in scope here)

- **L-74-followup** — generic `Document.Visibility` enum extension
  to add a distinct `PORTAL`/`CLIENT_VISIBLE` value (separate from
  manually-toggled `SHARED`) so audit can distinguish "auto-shared
  by closure-pack generator" vs "manually shared by firm user".
  Also broaden `PortalQueryService.listProjectDocuments` filter to
  admit both. Not needed for verify-cycle exit; current `SHARED`
  semantics are correct for closure-pack artefacts.
- **L-74-followup-2** — refactor `StatementService.generate` to
  reuse `GeneratedDocumentService.generateDocument` once the
  period-bound context constraint is generalised (e.g. via a
  `PeriodContextBuilder` interface variant). Removes the bypass
  duplication; deferred because it's a larger refactor and the
  Document persistence inline here is small and self-contained.
