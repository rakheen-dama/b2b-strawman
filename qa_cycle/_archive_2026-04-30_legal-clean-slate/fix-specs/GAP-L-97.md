# Fix Spec: GAP-L-97 — No portal email sent on matter close or SoA generation (templateName/slug mismatch)

## Problem

Closure flow + Generate Statement of Account flow both completed without enqueueing any email to Sipho's portal contact. Mailpit search (`/api/v1/search?query=closure` / `query=statement+of+account` / `query=matter+closed`) returned 0 hits. Sipho's last email is the Day 45 trust deposit notification (16:22:17 UTC ≪ 16:56:04 UTC closure timestamp).

Day 61 portal step 61.1 ("Mailpit → open 'Statement of Account ready' email") cannot be exercised — Sipho has no inbound notification.

Evidence:
- `qa_cycle/checkpoint-results/day-60.md §Day 60 supplementary — Mailpit notification`.
- `qa_cycle/checkpoint-results/day-60.md §Cycle 46 NEW gaps §GAP-L-97`.

## Root Cause (verified)

The email plumbing already exists end-to-end — `PortalDocumentNotificationHandler` listens on `DocumentGeneratedEvent` AFTER_COMMIT and sends `sendDocumentReadyEmail`. The default tenant allowlist is correct: `V117__add_portal_notification_doc_types.sql:19` seeds `portal_notification_doc_types = '["matter-closure-letter", "statement-of-account"]'` (slugs).

**The bug: the event publisher passes the template's display NAME ("Matter Closure Letter" / "Statement of Account") in the `templateName` field, but the handler compares it against an allowlist of SLUGS ("matter-closure-letter" / "statement-of-account").**

Verified call sites:

1. `backend/src/main/java/.../template/GeneratedDocumentService.java:219-220` — closure-letter path:
   ```java
   Map.of("file_name", pdfResult.fileName(), "template_name", templateDetail.name()),
   templateDetail.name(),  // ← passed as DocumentGeneratedEvent.templateName
   ```
   `TemplateDetailResponse.name()` returns `DocumentTemplate.name` (display name "Matter Closure Letter").

2. `backend/src/main/java/.../verticals/legal/statement/StatementService.java:236-237` — SoA path:
   ```java
   ...
   "visibility", "PORTAL"),
   template.getName(),  // ← display name "Statement of Account"
   ...
   ```

3. `backend/src/main/java/.../portal/PortalDocumentNotificationHandler.java:160`:
   ```java
   if (!allowlist.contains(templateName)) {
     log.debug("Skipping portal-document-ready: template={} not in allowlist (tenant={})", ...);
     return;
   }
   ```
   `allowlist` is `["matter-closure-letter", "statement-of-account"]` from `OrgSettings.portalNotificationDocTypes`. `"Matter Closure Letter".contains` → never matches → email skipped.

Template seed evidence (`backend/src/main/resources/template-packs/legal-za/pack.json`):
```json
{ "templateKey": "matter-closure-letter", "name": "Matter Closure Letter", ... }
{ "templateKey": "statement-of-account", "name": "Statement of Account", ... }
```

## Fix

The least-invasive correct fix is to **publish the slug instead of the name** in the `DocumentGeneratedEvent.templateName` field at both call sites. This aligns publishers with the allowlist semantics already documented at `PortalDocumentNotificationHandler:47-48` ("Template name is in the per-tenant `OrgSettings.portalNotificationDocTypes` allowlist (default `["matter-closure-letter", "statement-of-account"]`)" — note the values are clearly slugs).

**Step 1 — `GeneratedDocumentService.java`** (line 217-224): change `templateName` argument from `templateDetail.name()` to `templateDetail.slug()`:

```java
eventPublisher.publishEvent(
    new DocumentGeneratedEvent(
        "document.generated",
        "generated_document",
        generatedDoc.getId(),
        resolveProjectId(templateDetail, entityId),
        memberId,
        actorName,
        tenantId,
        orgId,
        Instant.now(),
        Map.of("file_name", pdfResult.fileName(), "template_name", templateDetail.name()),  // details map keeps display name for activity-feed UX
        templateDetail.slug(),  // <-- was templateDetail.name()
        TemplateEntityType.valueOf(templateDetail.primaryEntityType()),
        entityId,
        pdfResult.fileName(),
        generatedDoc.getId()));
```

The `details.template_name` (display name) is preserved — the firm-side activity feed renders that for UX. Only the structured `templateName` field that the portal-document handler keys off changes.

**Step 2 — `StatementService.java`** (line 217-241): same swap — change `template.getName()` (line 237) to `template.getSlug()`. The `details.template_name` map entry already passes `template.getName()` for activity-feed UX — leave that.

**Step 3 — Javadoc on `DocumentGeneratedEvent.templateName`** to remove ambiguity:
```java
String templateName,  // SLUG (e.g., "matter-closure-letter") — matches allowlists in OrgSettings.portalNotificationDocTypes
```

**Why not "fix the allowlist to use display names"?** The allowlist DEFAULT is shipped as `'["matter-closure-letter", "statement-of-account"]'` in V117. Tenants who customised their allowlist (admin Settings → Notifications) used these slugs. Tenant-side slug is also stable across language packs / display-name renames; display-name is brittle.

## Scope

Backend only.
Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java` — line 220 (templateName arg).
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementService.java` — line 237 (templateName arg).
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DocumentGeneratedEvent.java` — Javadoc note on `templateName` field.

Files to create: none.
Migration needed: no — V117 default is already a slug list, so existing tenant allowlists keep working.

## Verification

1. Restart backend.
2. Mailpit `purge` (clear inbox).
3. As Thandi on a fresh ACTIVE matter for a customer with an ACTIVE portal_contact: close it (Generate closure letter checked).
4. Within 60 seconds, Mailpit `GET /api/v1/search?query=ready` should surface the "document is ready" email to that portal contact — check the body references the closure letter.
5. From the same matter (closed), click `Generate Statement of Account` → period spanning the matter → Save.
6. Mailpit search again → second "document is ready" email for the SoA. **Note**: 5-min Caffeine dedup keyed on `tenant:customer:project` will collapse the SoA email if it lands within 5 minutes of the closure-letter one — this is by design (`PortalDocumentNotificationHandler:213-222`). To verify SoA-only path, generate the SoA on a DIFFERENT matter or wait 5+ minutes.
7. Re-walk Day 61 §61.1: Sipho can now open the inbound email and click through to the SoA.

## Estimated Effort

**S (45 min)** — 2-line code change + 1 docstring + restart + retest. The smallest of the 5 gaps.

## Tests

Backend `PortalDocumentNotificationHandlerTest` (or extend if existing):
- `dispatchesEmailWhenTemplateSlugIsInAllowlist` — emit `DocumentGeneratedEvent(templateName="matter-closure-letter")` → assert PortalEmailService received the call.
- `skipsEmailWhenTemplateSlugIsNotInAllowlist` — emit `DocumentGeneratedEvent(templateName="custom-internal-memo")` → assert no call.
- `regression_skipsEmailIfPublisherStillSendsDisplayName` — emit `DocumentGeneratedEvent(templateName="Matter Closure Letter")` → assert no call (this guards against future regressions where someone reverts to passing the display name).

`GeneratedDocumentServiceTest` / `StatementServiceTest`:
- Assert the captured event's `templateName` is the SLUG, not the name.

## Regression Risk

The activity feed reads `details.template_name` (display name) — unchanged.
The firm-side `NotificationEventHandler.onDocumentGenerated` may also read `event.templateName()` somewhere — verify with `grep -rn "event.templateName()"` and adjust comparisons in any handler that previously assumed display name. Quick scan: `grep -n "event.templateName()" backend/src/main` returns the portal handler + activity-feed formatter; both either compare against slug allowlists already (correct) or are tolerant to either form.
The `details.template_name` value left as display name preserves backwards-compat for any UI consumer.

## Dispatch Recommendation

**Defer-to-later-cycle (LOW severity but smallest effort).** Day 61 unblocks because Sipho can self-serve the SoA from the portal home tile (already proven cycle 45 GAP-L-92). The email is a UX bonus.

**However**: GAP-L-97 is the smallest fix in the bundle (S, ~45 min) and the only fix that meaningfully changes the Day 61 narrative (Sipho receives the email kick-off from her inbox vs cold-opening the portal). If Dev capacity permits, bundle with L-94/L-95 — the backend restart is shared.

Order recommendation: **L-94 + L-95 + L-97 in cycle 47** (one backend restart, three small surgical fixes); **L-93 + L-96 in cycle 48** (frontend touch + idempotent retention seed).
