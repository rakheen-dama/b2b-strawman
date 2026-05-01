# OBS-AUDIT-N1 — Wire orphaned `portal-proposal-expired` template + fix latent PR #1233 constraint gap

**Status**: SPEC_READY (about to ship)
**Severity**: Medium — bug (the email is silently never sent on proposal expiry); also fixes latent ops bug from #1233.
**Filed**: 2026-04-30 (cycle 22 audit pass; reaffirmed by 2026-05-01 slop hunt)
**Branch**: `fix/OBS-AUDIT-N1-portal-proposal-expired`
**Source audit**: `qa_cycle/audits/01-notification-listeners.md`

## Problem

Two bugs in the same class — both about portal-email reference-type registration:

### Bug 1 (the spec target) — orphaned template

`backend/src/main/resources/templates/email/portal-proposal-expired.html` exists but no Java code references its filename. The portal contact never receives an "your proposal has expired" email even though the firm side gets a `PROPOSAL_EXPIRED` notification. Same class as OBS-703 (`portal-new-proposal`) and OBS-2106 (`portal-document-ready`) — a Thymeleaf template waiting for a listener.

`ProposalExpiredEvent` is published correctly. `ProposalExpiredEventHandler.onProposalExpired` already runs post-AFTER_COMMIT and:
- Sends an in-app notification to the firm-side creator.
- Calls `proposalPortalSyncService.updatePortalProposalStatus(..., "EXPIRED")`.
- Has tenant-scope binding via `handleInTenantScope`.

What's missing: a third step that resolves the portal contact (event already carries `portalContactEmail` + `orgId`) and calls `PortalEmailService` to send `portal-proposal-expired`.

### Bug 2 (bundled, same class) — schema-code consistency for PORTAL_NEW_PROPOSAL

`PortalEmailService.NEW_PROPOSAL_REFERENCE_TYPE = "PORTAL_NEW_PROPOSAL"` was added in PR #1233 (OBS-703) but **never added to the V117 `chk_email_delivery_reference_type` constraint**. Initial hypothesis was that this caused silent CHECK-violation rollbacks on every proposal-sent email's `EmailDeliveryLogService.record()` INSERT.

**Empirical finding** (during implementation): the integration test (`ProposalExpiredEmailIntegrationTest`) drives both the OBS-703 send path and the OBS-AUDIT-N1 expiry path. Both deliver to GreenMail successfully, but **`email_delivery_log` ends up empty regardless of the constraint state** (V117 alone or V117 + V119). Either the path doesn't reach `record()` in the test environment, or the INSERT is rolled back silently somewhere upstream of the constraint check. Either way, the active-bug claim was overconfident.

What V119 still earns its keep for:

1. **Schema-code consistency**. `PortalEmailService` declares `PORTAL_NEW_PROPOSAL` as a reference type. The constraint should accept it — running INSERT-blocking constraints out-of-sync with the application's declared types is fragile.
2. **Future-proofing**. If/when the delivery-log path does fire (e.g. someone adds a portal-email dashboard query that depends on log rows, or fixes whatever's swallowing the INSERTs today), the constraint won't reject.
3. **Required for PORTAL_PROPOSAL_EXPIRED** anyway — same migration. Adding NEW_PROPOSAL alongside is a one-line change in the same SQL block.

## Why bundle Bug 2 with Bug 1

Same migration target (the V117 CHECK constraint). Splitting into two PRs would mean two migrations that conflict (V119 + V120 both DROP+re-add the same constraint), or a base-then-followup sequence — both worse than one migration. Quality Gate rule #7's same-class-cluster exception applies.

## Why extend `ProposalExpiredEventHandler` instead of mirroring OBS-703 with a new `ProposalExpiredEmailHandler`

The audit (`audits/01`) recommended mirroring PR #1233 — i.e. a new dedicated handler. **The 2026-05-01 slop hunt (`audits/slop-hunt-BATCH-A.md`) supersedes that recommendation** because it documented "listener-registration drift": `handleInTenantScope` is duplicated 8+ times with diverging tenant-null semantics, dedup mechanisms, subject-template locations, etc. Adding a 9th `ProposalExpiredEmailHandler` with its own `handleInTenantScope` would propagate the drift the slop hunt is asking us to stop.

Trade-off:
- **Mirror OBS-703 (audit's recommendation)**: clean single-responsibility, but adds a 9th `handleInTenantScope` duplicate. Violates the user mandate "never adding more technical debt."
- **Extend existing handler (this spec)**: no new debt; the existing handler already binds tenant scope correctly. Mitigates the SRP concern by isolating each step in its own try-catch block.
- **Extract `TenantScopedRunner` helper (canonical fix)**: also no new debt, follows OBS-703 pattern, BUT requires migrating 8 other handlers and is the slop hunt's recommended consolidation PR — an architectural change separate from this fix.

Decision: extend the existing handler. The `TenantScopedRunner` extraction is captured in `audits/slop-hunt-BATCH-A.md` as a separate consolidation PR to be sequenced later.

## Fix

### Code changes

**`backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiredEventHandler.java`**:

- Inject `PortalContactRepository`, `OrgSettingsRepository`, `PortalEmailService`, `EmailContextBuilder`, `@Value` for product-name + portal-base-url.
- Refactor `onProposalExpired` body so each side-effect step is a private method with its own try-catch — no step's failure cascades to siblings.
- Add `sendPortalExpiredEmail(event)` step that:
  - Returns silently if `event.portalContactEmail() == null` (proposal had no portal contact).
  - Looks up the contact via `portalContactRepository.findByEmailAndOrgId(event.portalContactEmail(), event.orgId())`.
  - Logs and returns silently if the contact is not found (cleaned up since proposal was sent — not an error).
  - Builds the context map with `contactName`, `orgName`, `proposalNumber`, `brandColor` (per the template's HTML comment), plus `subject` for the Thymeleaf renderer.
  - Calls `portalEmailService.sendProposalExpiredEmail(contact, context)`.

**`backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalEmailService.java`**:

- Add constants `PROPOSAL_EXPIRED_TEMPLATE_NAME = "portal-proposal-expired"` and `PROPOSAL_EXPIRED_REFERENCE_TYPE = "PORTAL_PROPOSAL_EXPIRED"`.
- Add `sendProposalExpiredEmail(PortalContact, Map<String, Object>)` thin wrapper delegating to `sendPortalNotification`. Mirrors `sendNewProposalEmail`.

### Migration

**`backend/src/main/resources/db/migration/tenant/V119__add_portal_new_proposal_and_proposal_expired_reference_types.sql`** — drop + re-add `chk_email_delivery_reference_type` with `'PORTAL_NEW_PROPOSAL'` and `'PORTAL_PROPOSAL_EXPIRED'` appended. Mirrors the V117 pattern verbatim.

### Test

**`backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiredEmailIntegrationTest.java`** (new file) — integration test using GreenMail singleton on :13025:

1. Provision tenant + member.
2. Create customer, portal contact, proposal with `expiresAt` in the past, status `SENT`.
3. Trigger the expiry transition (existing service or directly publish `ProposalExpiredEvent` via `ApplicationEventPublisher` with the right payload).
4. Assert: GreenMail received exactly one message to the portal contact, subject contains the proposal number, body matches the template.
5. Assert: `email_delivery_log` has a row with `reference_type = 'PORTAL_PROPOSAL_EXPIRED'` (verifies V119 accepts the new type).

Plus a smaller assertion in an existing integration test that exercises the OBS-703 send path: verify a delivery-log row with `'PORTAL_NEW_PROPOSAL'` is now actually written (V119 unblocks the latent log INSERT). Smallest scope: extend `ProposalSentEmailHandlerTest` if it already drives an integration round-trip, otherwise add the assertion to whichever existing test does. **Need to inspect during implementation.**

## Out of scope (deliberate)

- **`TenantScopedRunner` extraction** — a refactor across 8 handlers. Tracked in `audits/slop-hunt-BATCH-A.md` for a dedicated consolidation PR.
- **ArchUnit rule** for "every `templates/email/portal-*.html` has at least one Java reference" (audit-01 §Action items, item 2). Useful preventative check; defer to Task H or a separate enabling-infrastructure PR.
- **Caffeine dedup for the new handler** — the slop hunt flagged inconsistent dedup (Caffeine vs absent). For proposal-expired, the event fires once at the expiry-boundary scheduled job; no obvious double-fire risk. Same as OBS-703's choice. Skip.
- **Subject in Thymeleaf template** — the `EmailTemplateRenderer` reads `subject` from the context map (line confirmed at `EmailTemplateRenderer.render`). All current callers (magic-link, digest, new-proposal, document-ready, etc.) set the subject inline in Java. So the slop hunt's note about "subject-template location is inline-Java in #1233 vs Thymeleaf elsewhere" appears to have been incorrect — Java-set subject is the consistent pattern. Following it.

## Verification

1. **Inner-loop targeted**: `./mvnw test -Dtest='ProposalExpiredEmailIntegrationTest,ProposalExpiredEventHandler*,ProposalSentEmailHandlerTest,EmailDeliveryLog*'` — green.
2. **Merge gate**: full `./mvnw verify` clean. Marker written to `.claude/markers/verify-backend.json`.
3. **Manual repro** (post-merge, opportunistic):
   - Restart backend; confirm V119 ran for the dev tenant.
   - In a portal proposal flow, set `expiresAt` to 2 minutes ahead, send the proposal, wait for the expiry job.
   - Confirm Mailpit receives the portal-proposal-expired email.
   - psql: `SELECT * FROM email_delivery_log WHERE reference_type IN ('PORTAL_NEW_PROPOSAL', 'PORTAL_PROPOSAL_EXPIRED') ORDER BY created_at DESC LIMIT 5;` — both types present.

## Effort

S — 2 Java files modified, 1 new SQL migration, 1 new integration test. Plus the spec docs. ~2 hours including verify.

## Risk

- **CHECK constraint update**: V119 drops then re-adds the constraint. Within a single Flyway migration this is atomic — no window where INSERTs would be unconstrained. Safe.
- **Backward-data compat**: per user mandate, not a priority. New constraint doesn't reject any existing rows (it only adds two values). Deploying V119 won't fail on populated tables.
- **OBS-703 retroactive log entries**: V119 doesn't create historical log entries for previously-sent new-proposal emails (those exceptions were caught and the inserts rolled back). Acceptable per "no production data, backward compat not priority." Logs from V119 onward will be correct.
