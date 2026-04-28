# Fix Spec: GAP-L-100 — Portal /activity Firm-actions over-disclosure + raw event_type labels

## Problem

Portal `/activity` "Firm actions" tab over-shares **internal firm event types** to portal contacts (clients). Sipho — a portal contact on the RAF matter — currently sees raw firm-internal events on his own activity feed: `time_entry.created` ×4, `time_entry.deleted` ×2, `disbursement.billed/approved/submitted/created` ×4, `court_date.created`, `project.updated` ×2, `project.created_from_template`. In legal practice management, billable time-entry mutations and internal disbursement workflow are **not** client-facing — clients see fee notes / Statement of Account that aggregate them, not the individual line-item events.

Compounding the over-disclosure: every row in the tab renders the **raw `event_type` slug** as its display label (e.g. `statement.generated`, `portal.request_item.submitted`, `disbursement.billed`) instead of a humanised string. The `summaryFor()` helper in `PortalActivityEventResponse.java:57-71` only humanises 7 of the ~20 event types in scope; the rest fall through `default -> eventType` and surface unprocessed.

This is **not a tenant-isolation breach** — every event is bound to Sipho's matter — but it does over-disclose firm internals at a granularity clients should not see, and the raw-slug rendering makes that even more glaring.

Evidence: `qa_cycle/checkpoint-results/day-88.md §Day 88 Walk — Cycle 56 §88.4`; `qa_cycle/checkpoint-results/cycle56-day88-88.4-portal-activity-firm.yml`; `qa_cycle/checkpoint-results/cycle56-day88-88.4-portal-activity-firm-tab.png`.

## Root Cause

Two layers, one in the backend and one in the projection record:

1. **Backend filter is missing.** `AuditEventRepository.findActivityFirmForCustomer` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java:235-259`) returns *every* non-`PORTAL_CONTACT` audit event whose `details->>'project_id'` belongs to a project linked to the customer. There is no allow-list / block-list on `event_type`, so internal events emitted by `TimeEntryService`, `DisbursementService`, `ProjectTemplateService`, `MatterService` flow straight through to the portal response.

2. **Backend humanisation map is incomplete.** `PortalActivityEventResponse.summaryFor()` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalActivityEventResponse.java:57-71`) covers only 7 event types (`invoice.sent`, `invoice.payment_recorded`, `invoice.payment_reversed`, `invoice.payment_partially_reversed`, `portal.document.downloaded`, `portal.document.acknowledged`, `proposal.acceptance.completed`); every other event type falls through to `default -> eventType`, which is what the portal renders as `event.summary`.

`PortalActivityService.listActivity` (`backend/.../portal/PortalActivityService.java:40-67`) routes `Tab.FIRM` straight to the unfiltered repository method and `Tab.ALL` to a similarly unfiltered query — neither applies an allow-list. The portal page (`portal/app/(authenticated)/activity/page.tsx:140-153`) just renders `event.summary` as plain text.

The firm-side equivalent — `ActivityMessageFormatter.formatMessage` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java:33-112`) — already does proper humanisation for ~30 event types and is the right pattern reference.

## Fix

Backend-only. Two edits:

1. Apply an **allow-list filter** at the repository layer for the `FIRM` and `ALL` queries — only events whose `event_type` is on the portal-visible list reach the portal projection.
2. Extend the **humanisation map** in `PortalActivityEventResponse.summaryFor()` to cover every allow-listed event type with a client-friendly label.

Portal frontend stays unchanged — `event.summary` already renders the new labels.

### Step 1 — Define the portal-visible allow-list (single source of truth)

Create `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalActivityEventTypes.java`:

```java
package io.b2mash.b2b.b2bstrawman.portal;

import java.util.Set;

/**
 * The allow-list of audit event_types that may surface on the portal /activity Firm-actions tab.
 * Anything not on this list is firm-internal and must not reach a portal contact's view.
 *
 * <p>Updates to this list are a product / privacy decision — coordinate with Product before
 * adding a new event type. The corresponding humanised label MUST also be added to
 * {@link PortalActivityEventResponse#summaryFor(String)} in the same change.
 */
public final class PortalActivityEventTypes {

  private PortalActivityEventTypes() {}

  /** Client-facing event types — the firm's actions on the matter that the client should see. */
  public static final Set<String> PORTAL_VISIBLE_FIRM_EVENT_TYPES =
      Set.of(
          // Information requests (client-facing lifecycle)
          "information_request.created",
          "information_request.sent",
          "information_request.cancelled",
          "information_request.completed",
          "information_request.item_accepted",
          "information_request.item_rejected",
          "information_request.reminder_sent",
          // Proposals / engagement letters (client-facing)
          "proposal.sent",
          "proposal.accepted",
          "proposal.declined",
          "proposal.expired",
          "proposal.withdrawn",
          "proposal.acceptance.completed",
          // Invoices / fee notes (client-facing)
          "invoice.sent",
          "invoice.payment_recorded",
          "invoice.payment_reversed",
          "invoice.payment_partially_reversed",
          // Trust accounting (client-facing approved transactions only)
          "trust_transaction.approved",
          "trust_transaction.reversed",
          // Documents the firm shared with the client
          "document.generated",
          "docx_document.generated",
          // Statements of Account
          "statement.generated",
          // Matter closure (client-facing milestone)
          "matter_closure.closed",
          "matter_closure.reopened");
}
```

**Block-list note (informational, not enforced)**: every event type currently observed in the cycle-56 walk that is *not* on the allow-list is internal: `time_entry.*`, `time_entry.changed`, `time_entry.rate_re_snapshot`, `disbursement.created`, `disbursement.updated`, `disbursement.submitted`, `disbursement.approved`, `disbursement.rejected`, `disbursement.written_off`, `disbursement.billed`, `disbursement.unmarked_billed`, `disbursement.receipt_attached`, `court_date.created`, `project.created`, `project.updated`, `project.created_from_template`, `task.*`, `comment.*` (firm-internal), `project_member.*`. These stay invisible to the portal by virtue of not appearing on the allow-list.

`portal.invoice.paid`, `portal.document.downloaded`, `portal.document.upload_initiated`, `portal.document.acknowledged`, `portal.request_item.submitted` are emitted with `actor_type=PORTAL_CONTACT` and therefore live on the **MINE** stream, not FIRM — no allow-list entry needed for them.

### Step 2 — Filter the repository queries

Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java`:

Replace the body of `findActivityFirmForCustomer` (lines 235-259) to add `AND ae.event_type IN (:eventTypes)` to both the main query and the count query. Change the method signature to accept the set:

```java
@Query(
    nativeQuery = true,
    value =
        """
        SELECT * FROM audit_events ae
        WHERE ae.actor_type <> 'PORTAL_CONTACT'
          AND ae.event_type IN (:eventTypes)
          AND (ae.details->>'project_id') IS NOT NULL
          AND (ae.details->>'project_id') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
          AND (ae.details->>'project_id')::uuid IN (
            SELECT project_id FROM customer_projects WHERE customer_id = :customerId
          )
        ORDER BY ae.occurred_at DESC
        """,
    countQuery =
        """
        SELECT count(*) FROM audit_events ae
        WHERE ae.actor_type <> 'PORTAL_CONTACT'
          AND ae.event_type IN (:eventTypes)
          AND (ae.details->>'project_id') IS NOT NULL
          AND (ae.details->>'project_id') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
          AND (ae.details->>'project_id')::uuid IN (
            SELECT project_id FROM customer_projects WHERE customer_id = :customerId
          )
        """)
Page<AuditEvent> findActivityFirmForCustomer(
    @Param("customerId") UUID customerId,
    @Param("eventTypes") Set<String> eventTypes,
    Pageable pageable);
```

Apply the same allow-list to the FIRM half of `findActivityForPortalContact` (lines 182-212). The MINE half (`actor_type='PORTAL_CONTACT'` rows) is unchanged — portal contacts always see their own actions:

```java
SELECT * FROM audit_events ae
WHERE (ae.actor_type = 'PORTAL_CONTACT' AND ae.actor_id = :portalContactId)
   OR (
     ae.event_type IN (:eventTypes)
     AND (ae.details->>'project_id') IS NOT NULL
     AND (ae.details->>'project_id') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
     AND (ae.details->>'project_id')::uuid IN (
       SELECT project_id FROM customer_projects WHERE customer_id = :customerId
     )
   )
ORDER BY ae.occurred_at DESC
```

(plus matching `countQuery`). Update the method signature to add `@Param("eventTypes") Set<String> eventTypes`.

`findActivityMineForPortalContact` (lines 214-229) is **not** modified — portal contacts always see their own actions in full.

### Step 3 — Thread the allow-list through the service

Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalActivityService.java`:

```java
case MINE -> ...                                                                   // unchanged
case FIRM -> auditEventRepository.findActivityFirmForCustomer(
                customerId,
                PortalActivityEventTypes.PORTAL_VISIBLE_FIRM_EVENT_TYPES,
                unsorted);
case ALL -> portalContactId == null
              ? auditEventRepository.findActivityFirmForCustomer(
                  customerId,
                  PortalActivityEventTypes.PORTAL_VISIBLE_FIRM_EVENT_TYPES,
                  unsorted)
              : auditEventRepository.findActivityForPortalContact(
                  portalContactId,
                  customerId,
                  PortalActivityEventTypes.PORTAL_VISIBLE_FIRM_EVENT_TYPES,
                  unsorted);
```

### Step 4 — Extend humanisation map

Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalActivityEventResponse.java`. Replace `summaryFor()` (lines 57-71) with a complete map covering every type on the allow-list **plus** the existing PORTAL_CONTACT-emitted types so the MINE tab also shows humanised rows:

```java
private static String summaryFor(String eventType) {
  if (eventType == null) {
    return "";
  }
  return switch (eventType) {
    // PORTAL_CONTACT-emitted (MINE tab)
    case "portal.document.downloaded" -> "You downloaded a document";
    case "portal.document.upload_initiated" -> "You started uploading a document";
    case "portal.document.acknowledged" -> "You acknowledged a document";
    case "portal.request_item.submitted" -> "You submitted an information request item";
    case "portal.invoice.paid" -> "You paid a fee note";
    // Firm-emitted, allow-listed (FIRM tab)
    case "information_request.created" -> "Information request created";
    case "information_request.sent" -> "Information request sent to you";
    case "information_request.cancelled" -> "Information request cancelled";
    case "information_request.completed" -> "Information request completed";
    case "information_request.item_accepted" -> "Information request item accepted";
    case "information_request.item_rejected" -> "Information request item needs re-submission";
    case "information_request.reminder_sent" -> "Reminder sent for information request";
    case "proposal.sent" -> "Engagement letter sent to you";
    case "proposal.accepted" -> "Engagement letter accepted";
    case "proposal.declined" -> "Engagement letter declined";
    case "proposal.expired" -> "Engagement letter expired";
    case "proposal.withdrawn" -> "Engagement letter withdrawn";
    case "proposal.acceptance.completed" -> "Proposal accepted";
    case "invoice.sent" -> "Fee note sent to you";
    case "invoice.payment_recorded" -> "Payment recorded";
    case "invoice.payment_reversed" -> "Payment reversed";
    case "invoice.payment_partially_reversed" -> "Payment partially reversed";
    case "trust_transaction.approved" -> "Trust transaction recorded";
    case "trust_transaction.reversed" -> "Trust transaction reversed";
    case "document.generated", "docx_document.generated" -> "Document generated for you";
    case "statement.generated" -> "Statement of Account generated";
    case "matter_closure.closed" -> "Matter closed";
    case "matter_closure.reopened" -> "Matter reopened";
    default -> eventType;
  };
}
```

The `default -> eventType` arm is now dead code for FIRM rows (everything reaching it is allow-listed and mapped), but is retained as a defensive fallback in case a new allow-list entry is added without a label.

### Step 5 — Tests

Add `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/PortalActivityServiceFirmFilterTest.java`:

Setup mirrors any existing portal integration test (provision tenant, create project, link to customer, register portal contact, seed audit_events directly via `AuditEventRepository.save(...)` for speed). Cases:

1. `firmTab_excludes_time_entry_events` — seed `time_entry.created`, `time_entry.deleted`, `time_entry.changed`, `time_entry.rate_re_snapshot` rows (USER actor) → FIRM tab response contains 0 of these.
2. `firmTab_excludes_disbursement_events` — seed all 9 `disbursement.*` types → FIRM tab response contains 0.
3. `firmTab_excludes_court_date_and_project_internals` — seed `court_date.created`, `project.created`, `project.updated`, `project.created_from_template` → FIRM tab response contains 0.
4. `firmTab_includes_information_request_lifecycle` — seed `information_request.created`, `information_request.sent`, `information_request.completed`, `information_request.item_accepted` → FIRM tab response contains 4.
5. `firmTab_includes_invoice_proposal_statement_closure` — seed `invoice.sent`, `proposal.sent`, `proposal.accepted`, `statement.generated`, `matter_closure.closed` → FIRM tab response contains 5.
6. `firmTab_humanises_event_labels` — seed `statement.generated` and `information_request.sent` → response `summary` fields are `"Statement of Account generated"` and `"Information request sent to you"`, NOT raw slugs.
7. `mineTab_unchanged` — seed `portal.document.downloaded` (PORTAL_CONTACT actor) and `time_entry.created` (USER actor) → MINE tab returns the portal event only; verify the humanised summary is `"You downloaded a document"`. (MINE tab is unaffected by the allow-list — this test guards against accidental regression.)
8. `allTab_filters_firm_side_only` — seed mix of PORTAL_CONTACT events + allow-listed firm events + blocked firm events → ALL tab returns the PORTAL_CONTACT events + allow-listed firm events; 0 blocked.

## Scope

Backend only. No frontend, no migration.

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java` — add `eventTypes` filter to `findActivityFirmForCustomer` and `findActivityForPortalContact`.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalActivityService.java` — pass the allow-list through to both repository methods.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalActivityEventResponse.java` — extend `summaryFor()` with full humanisation map.

Files to create:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalActivityEventTypes.java` — single source of truth for the allow-list.
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/PortalActivityServiceFirmFilterTest.java` — regression coverage for filter + humanisation.

Migration needed: **no** — this is a read-side filter, no DB schema change.
Frontend changes: **no** — `portal/app/(authenticated)/activity/page.tsx` already renders `event.summary` as the row label.

## Verification

QA will, on a fresh `bugfix_cycle_2026-04-26-day88` backend (after Dev pushes the fix and `bash compose/scripts/svc.sh restart backend` lands the new query in JVM):

1. Authenticate Sipho via fresh magic-link (mirrors cycle-56 walk: `/portal/dev/generate-link` for `sipho.portal@example.com` → exchange token at `:3002` → `/home`).
2. Navigate to portal `/activity` → "Firm actions" tab.
3. Confirm tab no longer renders **any** of: `time_entry.*`, `disbursement.*`, `court_date.*`, `project.updated`, `project.created`, `project.created_from_template`, `task.*`, `comment.*`. Visual check: no row label starts with the raw slugs `time_entry`, `disbursement`, `court_date`, `project.`. DB cross-check: `SELECT event_type, count(*) FROM audit_events WHERE actor_type='USER' AND event_type IN (allow-list-set) AND (details->>'project_id')='cc390c4f-…'` should match the rendered row count exactly.
4. Confirm tab still renders the allow-listed events for the RAF matter: at least `statement.generated` ×2, `information_request.sent` ×2, `information_request.completed`, `proposal.sent`, `invoice.sent`, `matter_closure.closed`, `document.generated` ×N (whichever rows the cycle's data plane actually has).
5. Confirm display labels are humanised: every visible row label is a sentence-case English string (e.g. `"Statement of Account generated"`, `"Information request sent to you"`, `"Engagement letter sent to you"`, `"Matter closed"`); **zero** rows show a raw `event_type` slug like `statement.generated` or `information_request.sent`.
6. Switch to "Your actions" tab — confirm Sipho's own rows still render and their labels are also humanised (`"You downloaded a document"`, `"You submitted an information request item"`, `"You paid a fee note"`).
7. Isolation invariant — `grep -ic "moroka|EST-2026|estate"` on the page YAML returns 0.
8. Re-run the cycle-55 OBS-Cycle55-PortalContactBucketedAsSystem reconfirm — that observation is firm-side and unrelated; should remain unchanged by this fix (separate spec, do not bundle).

## Estimated Effort

**S** — under 30 minutes. One new constant class (~30 lines), two repository query edits (+1 parameter each), one service-layer threading change (3 call sites in a switch), one humanisation map expansion (~25 case arms). Most of the time is in the 8-case integration test class. Pattern is well-established (mirrors `ActivityMessageFormatter`'s shape and the existing portal projection).

## Severity / Demo Impact

- **Day 90 blocker**: **NO**. Day 90 is the final scripted day — terminology sweep, field promotion sweep, progressive disclosure, tier-removal scope, and the repeat isolation probe. None of those touch the portal `/activity` Firm-actions tab semantics. QA cycle 56 explicitly flagged this as informational, NOT a Day 90 blocker.
- **E.10 (Isolation gate)**: **NOT AT RISK**. This is over-disclosure of *firm-internal* events on the *correct* matter — there is no cross-tenant or cross-matter leak. Isolation invariant `grep -ic "moroka|EST-2026|estate"` was 0 across all portal pages on the walk and stays 0 after this fix.
- **E.11 (Audit/closure compliance)**: not affected — firm-side audit completeness is unchanged; only the portal projection narrows.
- **E.14 (Audit trail completeness)**: **MET**. The portal still gets the full client-facing slice of the audit trail; only firm-internal noise is suppressed. Demo readiness improves: the portal UI looks polished instead of debug-grade.

## Defer / Now Decision Hint

**Recommendation: FIX NOW (this cycle, before Day 90 walk).**

Rationale:
- Effort is **S** — single small backend change, one new constant class, ≤30 minutes of dev work + a focused integration test.
- Privacy / over-disclosure is product-meaningful even if not a strict isolation breach: a real client sees raw `time_entry.deleted` rows on their own matter timeline, which (a) reveals firm bookkeeping mutations clients shouldn't see, (b) looks unfinished compared to the rest of the portal polish.
- The label-humanisation half is **explicitly called out in §88.4 as a portal-side wow-moment polish item** ("portal-side wow moment: ✓ DEMO-READY structurally but needs polish before final demo: (a) GAP-L-100 internal-event filter, (b) raw event_type slug → human labels"). Sprint-2 scoped polish that can land cheaply now is worth landing now rather than deferring.
- No migration, no frontend, no cross-cutting concerns. Risk is low; rollback (revert the PR) is trivial.

If Day 90 walk needs to happen first for time-pressure reasons, mark **SPEC_READY-DEFERRED** and ship in Sprint 2 — but the better trade is to fix-now since it's S effort and lifts the demo-quality bar on E.14.

## Notes

- **OBS-Cycle55-PortalContactBucketedAsSystem** (firm-side: portal-contact rows label as "System" on firm matter Activity tab) is a **separate** observation at a different layer. Do **not** bundle into this fix — it's a firm-side actor-name resolution issue in `ActivityMessageFormatter.resolveActorName`, not a portal-side filter or label issue.
- **OBS-Cycle55-PortalInvoicePaidNullActorId** (DB: 1 `portal.invoice.paid` row has NULL actor_id) is also separate — that's a `PaymentReconciliationService:90-135` omission. Likewise, do not bundle.
- Architecture note: `PortalActivityEventTypes` lives next to `PortalActivityService` (`portal/` package) rather than alongside `AuditEvent` because it is a **portal-policy** decision, not an audit-domain truth. The audit subsystem must keep emitting every event regardless of portal visibility.
- If a future product decision needs **per-event-type portal visibility configurable via UI** (e.g. some firms want clients to see court_date rows, others don't), the right evolution is a `portal_event_visibility` table keyed on `(tenant_id, event_type)` plus an admin toggle UI — out of scope for this fix.
- The `default -> eventType` fallback in `summaryFor()` is intentionally retained: if a future allow-list addition forgets to add a humanised label, the row still renders the slug rather than crashing; the integration test can backstop catch that with an arch-style "every allow-listed type has a humanised label" assertion if desired (S+ effort to add — not required for this fix).
