# Audit 01 — Notification Template ↔ Listener Coverage

## Hypothesis (from cycle 22 verdict)
Same class as OBS-703 (proposal-sent email) and OBS-2106 (closure-pack email): a Thymeleaf template exists at `backend/src/main/resources/templates/email/<name>.html` but no listener renders it to a portal contact, so the email is silently never sent.

## Method
```
ls backend/src/main/resources/templates/email/*.html | xargs basename -s .html | \
  while read t; do echo "=== $t ==="; grep -rln "$t" backend/src/main/java/; done
```

Then narrowed each "no hit" or "only firm-side hit" template against the actual code paths to confirm whether the portal-facing variant is dead code.

## Findings

### Confirmed bug — OBS-AUDIT-N1: `portal-proposal-expired.html` orphaned

**Severity**: bug (medium). Same class as OBS-703 + OBS-2106.

**Evidence**:
- Template: `backend/src/main/resources/templates/email/portal-proposal-expired.html` exists.
- Grep: zero references to `portal-proposal-expired` (the template name) anywhere in `backend/src/main/java/`.
- Adjacent code:
  - `ProposalExpiredEventHandler.java:40` publishes a `PROPOSAL_EXPIRED` notification.
  - `NotificationService.java:168` handles `"PROPOSAL_EXPIRED"`.
  - `EmailNotificationChannel.java:222, 310` switches on `PROPOSAL_EXPIRED` — but this is the **firm-side** channel (admin/owner), per ADR-258 the `PortalEmailNotificationChannel` excludes proposal events.
- No code path invokes `PortalEmailService.sendPortalNotification(..., "portal-proposal-expired", ...)` or any equivalent.

**Impact**: when a proposal's `expiresAt` passes without a portal-side accept/decline, the firm gets a notification but the portal contact does NOT — they're left wondering why their proposal evaporated.

**Recommended fix**: mirror PR #1233 (OBS-703 fix). Add a `ProposalExpiredEmailHandler.java` post-AFTER_COMMIT listener in `proposal/` that resolves the portal contact and renders `portal-proposal-expired` via a new `PortalEmailService.sendProposalExpiredEmail(...)` thin wrapper. Effort: ~2 hours including a `MatterClosureEmailIntegrationTest`-pattern integration test.

### All other portal templates have at least one rendering path
- `portal-document-ready` → `PortalDocumentNotificationHandler` ✅ (OBS-2106 + OBS-2107 fixes apply)
- `portal-magic-link` → `PortalEmailService.sendMagicLink` ✅
- `portal-new-proposal` → `ProposalSentEmailHandler` ✅ (OBS-703 fix)
- `portal-trust-activity` → `PortalEmailNotificationChannel.buildTrustActivityContext` ✅
- `portal-deadline-approaching` → `PortalEmailNotificationChannel` ✅
- `portal-retainer-period-closed` → `PortalEmailNotificationChannel` + `PortalEmailService` ✅
- `portal-weekly-digest` → `PortalEmailService` ✅
- `request-*` (sent / completed / item-accepted / item-rejected / reminder) → `InformationRequestEmailService` ✅

### Out-of-scope notes
- Firm-side `notification-*.html` templates (notification-task, -comment, -invoice, -proposal, -retainer, -budget, -document, -member, -schedule, -automation) are all funnelled through `EmailNotificationChannel` and look complete.
- `acceptance-*.html` (confirmation, reminder, request) → `AcceptanceNotificationService` + `AcceptanceService` + `PortalAcceptanceRequestController` — multiple consumers, looks fine.
- `invoice-delivery.html` → `InvoiceEmailService` ✅
- `demo-welcome.html` → `DemoWelcomeEmailService` ✅
- `base.html` is the layout fragment, not a standalone template.

## Action items

1. **File OBS-AUDIT-N1** as a real bug in the next QA cycle's tracker.
2. **Generalise the audit**: add a build-time check (ArchUnit rule or simple test) that every `templates/email/portal-*.html` has at least one Java reference to its filename. Prevents regression of this class.
