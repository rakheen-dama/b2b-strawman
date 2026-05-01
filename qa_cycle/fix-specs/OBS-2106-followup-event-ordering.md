# Fix Spec: OBS-2106 follow-up — closure-letter canonical event must carry visibility atomically

**Severity**: workaround → structural fix (HIGH per slop-hunt PR-1246, finding 1)
**Surface**: Backend — `GeneratedDocumentService` + `MatterClosureService` + `MatterClosureEmailIntegrationTest`
**Effort**: M (~2h end-to-end including review)

## Problem

The OBS-2106 fix that landed in PR #1246 worked around a real race in the canonical document-generation event publication, rather than fixing it. The slop-hunt audit (`qa_cycle/audits/slop-hunt-PR-1246.md`, finding 1) flagged this as a "workaround masquerading as fix":

> The PR publishes a **second** `DocumentGeneratedEvent` to compensate for the first event being emitted before `markSystemAutoShared` commits. The right fix is to publish the canonical event AFTER the visibility flip, or to atomically include `visibility=PORTAL` in the original event. Instead this PR adds a **second emitter** in a different class (closure service) that compensates for what the canonical emitter (`GeneratedDocumentService`) gets wrong.

Concretely, prior to this fix:

1. `MatterClosureService.generateClosureLetterSafely` calls `generatedDocumentService.generateForProject(...)`.
2. Inside `generateDocument`, the linked `Document` is created at `Visibility.INTERNAL` and the canonical `DocumentGeneratedEvent` is published with `details = {file_name, template_name}` (no `scope`, no `visibility`).
3. The closure caller then calls `documentService.markSystemAutoShared(linkedDocumentId)` — flipping the linked Document to `Visibility.PORTAL` within the same `REQUIRES_NEW` transaction.
4. As a workaround for the canonical event's missing `visibility`, the closure caller publishes a **second** `DocumentGeneratedEvent` from `MatterClosureService.publishPortalReadyFollowUp` carrying `visibility=PORTAL` in details.
5. `PortalDocumentNotificationHandler` dedups on `(tenant, customer, project)` over a 5-minute Caffeine window, so the two events coalesce into a single email — usually.

The dedup is best-effort (single-JVM, evicted on listener restart, cold-tenant empty). Under unfortunate timing (Day 60 OBS-2106 regression), the listener could observe the **first** canonical event, fall through to its DB-fallback path in `isPortalVisible`, read the still-INTERNAL Document row before the visibility flip's commit was visible, and silently drop the email.

## Root cause (verified)

`GeneratedDocumentService.generateDocument` always creates the linked `Document` at `Visibility.INTERNAL` and emits a canonical event without scope/visibility hints, regardless of what the caller intends. Callers that want the doc to be portal-visible have to:

1. Call `markSystemAutoShared` after the fact (a separate transactional boundary that's wrapped in best-effort try/catch in the closure path), AND
2. Publish a compensating event (because the canonical event lacks the visibility hint).

The `StatementService` SoA path doesn't have this problem — it builds its own paired `Document` at `Visibility.PORTAL` from the start (`StatementService.java:161-167`) and publishes both `DocumentCreatedEvent` (for the portal projection) and `DocumentGeneratedEvent` (with explicit `scope=PROJECT, visibility=PORTAL` in details, `StatementService.java:217-246`) atomically. That path has been the architectural model all along.

## Fix

Mirror the SoA pattern for `generateDocument`/`generateForProject`:

### Part 1 — `GeneratedDocumentService` accepts an intended visibility

Add an `intendedVisibility` parameter to overloads of `generateDocument` and `generateForProject`. When non-null:

- The linked `Document` is created at the intended visibility from the start (no post-generation flip).
- The canonical `DocumentGeneratedEvent` carries explicit `scope` + `visibility` in its `details` map — taken from the linked Document — so `PortalDocumentNotificationHandler.isPortalVisible` decides on the event payload alone, no DB-fallback race.
- For portal-visible artefacts, a `DocumentCreatedEvent` is published so `PortalEventHandler.onDocumentCreated` projects the doc onto `portal.portal_documents` (replaces the projection that previously fired off `DocumentVisibilityChangedEvent` on the post-generation flip).

The original 6-arg `generateDocument` signature and 3-arg `generateForProject` signature are preserved as delegating overloads passing `intendedVisibility=null` — backward compatible with `RecurringScheduleService` and `DocumentTemplateController`, both of which still default to `INTERNAL`.

### Part 2 — `MatterClosureService.generateClosureLetterSafely` uses the new contract

- Passes `Document.Visibility.PORTAL` to `generateForProject` — closure letter is born portal-visible.
- Removes the `documentService.markSystemAutoShared(linkedDocumentId)` call (no flip needed).
- Removes the `publishPortalReadyFollowUp(...)` helper method and its call site (no compensating event needed).
- Removes now-unused dependencies: `DocumentService`, `MemberNameResolver`, and a few imports.
- Trims the long inline comment block that recited the workaround's reasoning — that history now lives in this fix-spec and the PR description.

### Part 3 — Reproducer test

`MatterClosureEmailIntegrationTest` gains `@RecordApplicationEvents` plus a new test method `close_publishesSingleCanonicalEventWithExplicitPortalVisibility` that asserts:

1. Closing a matter with `generateClosureLetter=true` publishes **exactly one** `DocumentGeneratedEvent` for the closure-letter (was 2 before this fix).
2. That single canonical event carries `details.scope = "PROJECT"` and `details.visibility = "PORTAL"` (was missing both before this fix).

This is the regression guard — if a future change reintroduces the dedup-coalescence dependency or reverts the visibility-hint plumbing, this test fails immediately rather than waiting for a flaky Day-60-style production regression.

## Scope

- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java` — `generateDocument` + `generateForProject` overloads, threaded `intendedVisibility` into `createLinkedDocument`, `DocumentCreatedEvent` emit on portal-visible birth.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java` — closure-letter caller passes `PORTAL`; flip + follow-up event + their helpers + unused dependencies removed.
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureEmailIntegrationTest.java` — `@RecordApplicationEvents` + new test.

Out of scope (explicit per Quality Gate #7 "one fix per PR"):

- Slop-hunt PR-1246 findings 2–7 (UX dedup invariant audit, log-spam from diagnostic uplift, test capability shortcut, test-pattern drift, fire-and-forget DLQ, comment density). Each ships separately if/when prioritised.
- The `Document.Visibility` constant on the `Document` class is unchanged. Callers of `markSystemAutoShared` outside the closure path (none in production at the moment) keep their semantics — this fix only redirects the closure path away from the post-generation flip.

## Verification

- Targeted: `./mvnw test -Dtest='*MatterClosure*,*PortalDocument*,*GeneratedDocument*,*StatementService*'` — 64 tests pass (including the new reproducer + the existing `close_withGenerateClosureLetterTrue_flipsLinkedDocumentVisibility_toPortal` which still passes because the linked Document is portal-visible just born there).
- Full: `./mvnw verify` — clean (5013/0F/0E/26 skip baseline preserved, plus the new test).
- Browser-driven verification of the closure-pack portal email path was already done at the OBS-2106 cycle (Day 60 regression cycle 47); this fix is a structural cleanup behind the same observable behaviour. The scenario assertion (closure → portal-document-ready email arrives) is unchanged.

## Implemented As

PR #N — see `OBS-2106-followup-event-ordering.implementation-note.md` once merged.
