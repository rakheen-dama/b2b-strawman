# Fix Spec: BUG-CYCLE26-11 — Trust-activity email CTA links to wrong UUID

## Problem

The auto-fired "Trust account activity" email's "View trust ledger" CTA deep-links to `/trust/{trust_account_id}` instead of `/trust/{matter_id}`. The portal route `portal/app/(authenticated)/trust/[matterId]/page.tsx` looks up the URL parameter by `matterId` against the user's matter list — a trust-account UUID never matches, so the page renders three "No trust balance / The requested resource was not found." empty states.

Evidence:
- Mailpit message `m7qSdt8XULPXMs6mX6Vbcn` ("Mathebula & Partners: Trust account activity") — CTA href observed as `http://localhost:3002/trust/46d1177a-d1c3-48d8-9ba8-427f14b8278f` (= trust account `Mathebula Trust — Main`).
- Working URL via sidebar/`/home` tile auto-redirect: `/trust/cc390c4f-35e2-42b5-8b54-bac766673ae7` (= RAF matter ID; different domain object, different UUID).
- Broken landing snapshot: `qa_cycle/checkpoint-results/cycle26-day11-11.2-trust-by-account.yml` (BLOCKED-AT-LINK).
- Working snapshot: `qa_cycle/checkpoint-results/cycle26-day11-11.3-trust-by-matter.yml` (PASS).

Functional impact medium (broken CTA at exactly the trust-deposit confirmation moment), severity LOW (non-cascading — sidebar Trust nav + home "Last trust movement" tile both reach the correct page via the `/trust` index auto-redirect at `portal/app/(authenticated)/trust/page.tsx:64-68`).

## Root Cause (verified)

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalEmailNotificationChannel.java`:

**Lines 296-300** (`buildTrustActivityContext` overload for `TrustTransactionApprovalEvent`):
```java
// Deep link keys the portal trust page by matter id when present.
UUID matterKey = event.trustAccountId();
context.put(
    "trustUrl",
    matterKey != null ? portalBaseUrl + "/trust/" + matterKey : portalBaseUrl + "/trust");
```

**Lines 321-326** (`buildTrustActivityContext` overload for `TrustTransactionRecordedEvent`):
```java
UUID matterKey = event.trustAccountId();
context.put(
    "trustUrl",
    matterKey != null ? portalBaseUrl + "/trust/" + matterKey : portalBaseUrl + "/trust");
```

The variable is named `matterKey` (and the comment says "matter id") but the value is `event.trustAccountId()` — these are distinct domain objects with distinct UUIDs. The portal `/trust/[matterId]` route at `portal/app/(authenticated)/trust/[matterId]/page.tsx:82` does `res.matters.find((m) => m.matterId === matterId)` — a trust-account UUID never matches, so the page renders the empty-state branches (lines 187-197 + the Transactions/Statements components 404 separately).

Confirming the events do not carry the matter ID today:
- `TrustTransactionRecordedEvent` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustTransactionRecordedEvent.java:24-34`) — fields: `eventType, transactionId, trustAccountId, transactionType, amount, customerId, recordedBy, tenantId, orgId, occurredAt`. **No `projectId`.**
- `TrustTransactionApprovalEvent` (`event/TrustTransactionApprovalEvent.java:13-25`) — same shape minus `recordedBy`/+approval fields. **No `projectId`.**

The `TrustTransaction` entity does carry `projectId` (`transaction/TrustTransaction.java:35-36, 144-146`), and every publish site has access to it (`request.projectId()` / `transaction.getProjectId()`).

## Fix

Thread the matter ID through the event so the URL builder can use it. Smallest correct change:

### Step 1 — Add `projectId` to both event records (backend)

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustTransactionRecordedEvent.java`

Add `UUID projectId` to the record components and to the `recorded(...)` factory. The portal `/trust/[matterId]` deep-link is keyed by matter ID, so the URL builder needs it.

```java
public record TrustTransactionRecordedEvent(
    String eventType,
    UUID transactionId,
    UUID trustAccountId,
    UUID projectId,            // NEW — matter id; may be null for non-matter-scoped txns
    String transactionType,
    BigDecimal amount,
    UUID customerId,
    UUID recordedBy,
    String tenantId,
    String orgId,
    Instant occurredAt) {

  public static TrustTransactionRecordedEvent recorded(
      UUID transactionId,
      UUID trustAccountId,
      UUID projectId,          // NEW
      String transactionType,
      BigDecimal amount,
      UUID customerId,
      UUID recordedBy,
      String tenantId,
      String orgId) {
    return new TrustTransactionRecordedEvent(
        "trust_transaction.recorded",
        transactionId,
        trustAccountId,
        projectId,             // NEW
        transactionType,
        amount,
        customerId,
        recordedBy,
        tenantId,
        orgId,
        Instant.now());
  }
}
```

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustTransactionApprovalEvent.java`

Add `UUID projectId` to the record components and to all three factories (`awaitingApproval`, `approved`, `rejected`). Same shape as above — insert as the new component immediately after `trustAccountId` for consistency.

### Step 2 — Update all publish sites to pass `projectId` (backend)

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java`

Update each `eventPublisher.publishEvent(...)` call to pass `projectId`. There are 6 publish sites (5 in `TrustTransactionService` + at least one elsewhere):

- **Line 274-283** (`recordDeposit` → `TrustTransactionRecordedEvent.recorded`): pass `request.projectId()` (or equivalently `saved.getProjectId()`).
- **Line 434-443** (`recordTransfer` → `TrustTransactionRecordedEvent.recorded` for TRANSFER_OUT): pass `savedOut.getProjectId()` — typically null for cross-customer transfers; that is fine, falls back to `/trust`.
- **Line 444-453** (`recordTransfer` → `TrustTransactionRecordedEvent.recorded` for TRANSFER_IN): pass `savedIn.getProjectId()`.
- **Line 511-519** (`recordPayment` → `awaitingApproval`): pass `saved.getProjectId()`.
- **Line 608-617** (refund flow → `awaitingApproval`): pass `saved.getProjectId()`.
- **Line 676-685** (other flow → `awaitingApproval`): pass the local saved transaction's `getProjectId()`.
- **Line 902-912** (`approveTransaction` → `approved`): pass `transaction.getProjectId()`.
- **Line 961-971** (`rejectTransaction` → `rejected`): pass `transaction.getProjectId()`.

Also update **`backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/TrustLedgerPortalSyncService.java`** if it still re-publishes either event (it should not — it only consumes — but `git grep "TrustTransactionRecordedEvent.recorded\|TrustTransactionApprovalEvent.(approved|awaiting|rejected)"` should be run to catch any others).

### Step 3 — Use `projectId` in the URL builder (backend)

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalEmailNotificationChannel.java`

**Lines 296-300** — replace:
```java
// Deep link keys the portal trust page by matter id when present.
UUID matterKey = event.trustAccountId();
context.put(
    "trustUrl",
    matterKey != null ? portalBaseUrl + "/trust/" + matterKey : portalBaseUrl + "/trust");
```

with:
```java
// Deep link keys the portal trust page by matter (project) id when present.
// Falls back to the /trust index page when the transaction has no matter
// (e.g. cross-customer transfers, or matter id genuinely unset).
UUID matterId = event.projectId();
context.put(
    "trustUrl",
    matterId != null ? portalBaseUrl + "/trust/" + matterId : portalBaseUrl + "/trust");
```

**Lines 321-326** — apply the identical replacement to the twin overload (the one that takes `TrustTransactionRecordedEvent`).

### Step 4 — Update tests (backend)

**File**: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalEmailNotificationChannelIntegrationTest.java`

- Line 134-148 (`trustApprovalEvent_triggersTrustActivityEmail`): add a `UUID projectId = UUID.randomUUID();` and pass it to `TrustTransactionApprovalEvent.approved(...)`.
- Line 179-192 (`trustRecordedEvent_depositTriggersTrustActivityEmail`): same — add a `projectId` and pass it to `TrustTransactionRecordedEvent.recorded(...)`.
- Line 218-228 (the second `recorded` invocation found by grep): same fix.
- **Add a new assertion** to one of these tests that the rendered email body's CTA href contains `"/trust/" + projectId.toString()` (and crucially does NOT contain `trustAccountId.toString()`). The body is already accessible via `MimeMessage.getContent()` — extract the `href` from the rendered HTML and assert.
- **Add a regression test** `trustRecordedEvent_nullProjectIdFallsBackToTrustIndex` that publishes a `TrustTransactionRecordedEvent.recorded(..., projectId=null, ...)` and asserts the CTA href ends with `"/trust"` (not `"/trust/null"`).

**Files** (also update event-factory call sites):
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/TrustLedgerPortalSyncServiceIntegrationTest.java:538, 592` — pass a fresh `projectId`.
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustNotificationHandlerTest.java:76, 118, 162` — pass a fresh `projectId`.

(Run `./mvnw -q test -Dtest='*Trust*,PortalEmailNotificationChannel*'` to surface every call-site that needs updating; the compile error will name them.)

## Scope

**Backend-only.**

**Files to modify**:
1. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustTransactionRecordedEvent.java` (add field + factory arg)
2. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustTransactionApprovalEvent.java` (add field + 3 factory args)
3. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java` (~8 publish sites updated)
4. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalEmailNotificationChannel.java` (lines 296-300 and 321-326 — swap `event.trustAccountId()` → `event.projectId()`, rename local var to `matterId`)
5. `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalEmailNotificationChannelIntegrationTest.java` (update factory calls + add href assertion + add null-projectId regression test)
6. `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/TrustLedgerPortalSyncServiceIntegrationTest.java` (update factory calls)
7. `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustNotificationHandlerTest.java` (update factory calls)

**Files to create**: none.

**Migration needed**: no (event records are in-process Spring events; no DB schema, no JSON wire format).

## Verification

1. Backend rebuild + restart: `bash compose/scripts/svc.sh restart backend`. Confirm `mvn -q -DskipTests=false test -Dtest='*Trust*,PortalEmailNotificationChannel*'` passes.
2. Firm-side: as Bob (admin) on the Keycloak dev stack, navigate to Sipho's RAF matter and record a R 100,00 trust deposit (any small follow-up amount; or open a fresh matter if Day-10 row is the only one).
3. Mailpit: open `http://localhost:8025` and find the new "Mathebula & Partners: Trust account activity" message addressed to `sipho.portal@example.com`. Inspect HTML source — the "View trust ledger" anchor's `href` MUST end in `/trust/{RAF_matter_uuid}` (= `cc390c4f-35e2-42b5-8b54-bac766673ae7`), NOT the trust-account UUID `46d1177a-d1c3-48d8-9ba8-427f14b8278f`.
4. Click the CTA in an authenticated Sipho browser session. Page MUST render the BalanceCard with `R 50 100,00` (or whatever the running balance is) + the Transactions table including the new row + Statements section. NO "The requested resource was not found." text anywhere.
5. Edge case: if a future TRANSFER deposit is recorded (no matter id), confirm the email still sends with CTA href = `{portalBaseUrl}/trust` (the index, which auto-redirects when there's exactly one matter). Cover this in the new unit/integration test, not in the manual run.

Capture evidence YAMLs to `qa_cycle/checkpoint-results/cycle{N}-bug-26-11-fixed-email-href.yml` and `…-fixed-trust-page.yml`.

## Estimated Effort

**S (≈ 30-45 min)**. Mechanical edit across ~7 files; the hardest part is updating each event-factory call-site, which the compiler enforces. New regression tests are ~15 lines each.

## Tests

- Existing: `PortalEmailNotificationChannelIntegrationTest.trustApprovalEvent_triggersTrustActivityEmail` and `trustRecordedEvent_depositTriggersTrustActivityEmail` — extend to assert CTA href contains `/trust/{projectId}` and does NOT contain `/trust/{trustAccountId}`.
- New regression: `trustRecordedEvent_nullProjectIdFallsBackToTrustIndex` — asserts href ends with `/trust` (not `/trust/null`) when `projectId == null`.
- Run all passing test classes that mention `Trust`: `./mvnw test -Dtest='*Trust*,PortalEmailNotificationChannel*'`. All must stay green.

## Regression risk

**Low — fix is contained to a single field added to two event records and a single URL-builder line each in two methods.**

Things to verify do NOT break:
- `TrustLedgerPortalSyncService.onTrustTransactionApproval` and `onTrustTransactionRecorded` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/TrustLedgerPortalSyncService.java:102, 132`) consume `event.trustAccountId()` — that field is **untouched**; they continue to receive the trust-account UUID exactly as before. Behaviour unchanged.
- `TrustNotificationHandler` (`event/TrustNotificationHandler.java:51, 124, 141, 160`) consumes `event.transactionId()`/`event.amount()`/`event.eventType()` — unchanged.
- The `trustUrl` template variable is consumed only by `backend/src/main/resources/templates/email/portal-trust-activity.html:37` (single anchor `th:href="${trustUrl}"`). No other consumer.
- The Spring event records are in-process Java objects; they are not serialised to a wire format, so adding a field is a source-only break — every call-site is enforced by the compiler. There is no DB schema, no message bus, no external contract to evolve.
- TRANSFER_OUT/TRANSFER_IN deposits often have null `projectId` — the existing fallback (`else portalBaseUrl + "/trust"`) handles this correctly. The new spec keeps that fallback behaviour intact.
