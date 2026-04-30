# Fix Spec: BUG-CYCLE26-10 — Activity feed renders "for unknown" on accepted-item events

## Problem

Evidence: `qa_cycle/checkpoint-results/cycle21-day10-10.1-raf-matter-detail.yml:276-290`

The matter detail Activity tab renders accept-item events with a literal `"unknown"` placeholder instead of the request number:

```
Bob Ndlovu accepted "Bank statement (≤ 3 months)" for unknown
Bob Ndlovu accepted "Proof of residence (≤ 3 months)" for unknown
Bob Ndlovu accepted "ID copy" for unknown
```

Expected:

```
Bob Ndlovu accepted "Bank statement (≤ 3 months)" for REQ-0002
Bob Ndlovu accepted "Proof of residence (≤ 3 months)" for REQ-0002
Bob Ndlovu accepted "ID copy" for REQ-0002
```

Affects every `information_request.item_accepted` activity-feed entry on every matter that has had FICA/onboarding items accepted.

## Root Cause (verified)

The activity message template is in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java:102-104`:

```java
case "information_request.item_accepted" ->
    "%s accepted \"%s\" for %s"
        .formatted(actorName, getItemName(details), getRequestNumber(details));
```

`getRequestNumber()` (lines 236-239) reads `details.get("request_number")` from the audit event's `details` map and returns the literal string `"unknown"` if absent:

```java
private String getRequestNumber(Map<String, Object> details) {
  Object num = details.get("request_number");
  return num instanceof String s ? s : "unknown";
}
```

The audit event for `information_request.item_accepted` is published in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestService.java:453-465`:

```java
var acceptAuditDetails = new HashMap<String, Object>();
acceptAuditDetails.put("request_id", requestId.toString());
acceptAuditDetails.put("item_name", item.getName());
if (request.getProjectId() != null) {
  acceptAuditDetails.put("project_id", request.getProjectId().toString());
}
auditService.log(
    AuditEventBuilder.builder()
        .eventType("information_request.item_accepted")
        .entityType("request_item")
        .entityId(itemId)
        .details(acceptAuditDetails)
        .build());
```

**`request_number` is never put into the details map.** Compare with sibling events in the same service that all correctly include `request_number`:

- `information_request.created` (line 253): `createAuditDetails.put("request_number", requestNumber);` ✓
- `information_request.updated` ad-hoc (line 320): includes `request_number` ✓
- `information_request.sent` (line 360): `sentAuditDetails.put("request_number", saved.getRequestNumber());` ✓
- `information_request.cancelled` (line 402): includes `request_number` ✓
- `information_request.completed` (line 557): includes `request_number` ✓
- Portal `information_request.item_submitted` (`PortalInformationRequestService.java:362`): includes `request_number` ✓

`item_accepted` (and `item_rejected` at line 505-511) is the outlier. The fix is **add `request_number` to the audit details**, which is already loaded — `request.getRequestNumber()` is on the `InformationRequest` aggregate fetched at line 438-440.

## Fix

One required backend line (+ one optional consistency line) in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestService.java`.

### Step 1 — Add `request_number` to `acceptAuditDetails`

In the `acceptItem(...)` method, after line 454 (`acceptAuditDetails.put("request_id", requestId.toString());`), add:

```java
acceptAuditDetails.put("request_number", request.getRequestNumber());
```

The `request` variable is already in scope (loaded at line 437-440). No new database query, no new fields, no signature changes.

### Step 2 — Apply the same fix to `rejectItem(...)` for consistency

The activity-message template for `information_request.item_rejected` (formatter line 105-107) does **not** currently reference `request_number` — it renders `"%s rejected "%s" — waiting for re-submission"`. So this is **not strictly required to fix BUG-10**. However, for consistency with every other `information_request.*` audit event in this service, also add `request_number` to `rejectAuditDetails`:

In the `rejectItem(...)` method, after line 506 (`rejectAuditDetails.put("request_id", requestId.toString());`), add:

```java
rejectAuditDetails.put("request_number", request.getRequestNumber());
```

This is defensive — if a future activity-message template wants to surface the request number on rejection, the data is there.

### Step 3 — Add a regression test

Add a test method to `backend/src/test/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatterTest.java` (file already exists per grep at line 13):

```java
@Test
void formatsItemAcceptedWithRequestNumber() {
  var event = new AuditEvent(...);  // eventType="information_request.item_accepted",
                                    // entityType="request_item",
                                    // details=Map.of("request_number","REQ-0042","item_name","ID copy")
  var item = formatter.format(event, Map.of(actorId, member));
  assertThat(item.message()).isEqualTo("Bob Ndlovu accepted \"ID copy\" for REQ-0042");
  assertThat(item.message()).doesNotContain("unknown");
}
```

(Use the existing test-class fixture conventions — see existing tests for `AuditEvent` construction style.)

Optionally also add an integration assertion in an existing `InformationRequestServiceTest` / `InformationRequestIntegrationTest` confirming the audit row written by `acceptItem` contains `request_number`.

## Scope

**Backend only.**

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestService.java` — add 1 line in `acceptItem`, optionally 1 line in `rejectItem` for consistency
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatterTest.java` — add regression test for `item_accepted` + `request_number` interpolation

Files to create: **none**

Migration needed: **no** (audit events are append-only, fix only affects newly-emitted events going forward; existing rows in `audit_events.details` will continue to render as `"for unknown"` — acceptable for cosmetic polish, not worth a backfill).

**Backend restart required** after change (Java source — no hot reload): `bash compose/scripts/svc.sh restart backend`.

## Verification

1. Run targeted tests: `./mvnw test -Dtest=ActivityMessageFormatterTest,InformationRequestServiceTest` — all pass.
2. Restart backend.
3. Drive a fresh information-request accept flow via browser:
   - Sipho/RAF matter has REQ-0002 with 3 SUBMITTED items (state from Day 4 walk).
   - As Bob, navigate to the request detail and Accept one item.
   - Navigate back to the RAF matter detail Activity tab.
4. Re-capture `qa_cycle/checkpoint-results/cycle21-day10-10.1-raf-matter-detail.yml` — the new accept event should now render as:

```yaml
- paragraph [ref=...]: Bob Ndlovu accepted "ID copy" for REQ-0002
```

Pre-existing accept rows logged before the fix will still show `"for unknown"` (audit events are immutable) — that is expected and acceptable; the fix is forward-going.

## Estimated Effort

S (< 30 min)
