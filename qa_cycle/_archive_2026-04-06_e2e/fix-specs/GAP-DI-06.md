# Fix Spec: GAP-DI-06 — ipAddress not in audit API response

## Problem

The audit event API (`GET /api/audit-events`) does not include `ipAddress` or `userAgent` in the response DTO, even though both fields are stored in the database and populated by `AuditEventBuilder.build()`. The QA checkpoint T4.10.7 found: "API response does not include ipAddress field (may be internal only)." These fields are useful for security auditing (identifying which IP performed an action) and should be exposed in the API response.

## Root Cause (hypothesis)

The `AuditEventResponse` record in `AuditEventController.java` (lines 67-89) maps 9 fields from `AuditEvent` but omits `ipAddress` and `userAgent`. The `AuditEvent` entity has both `getIpAddress()` (line 109) and `getUserAgent()` (line 113), and `AuditEventBuilder.build()` (lines 290-301) correctly captures both from `HttpServletRequest`. The data is in the DB but the DTO simply doesn't include those two fields.

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java`
- Lines 67-76: `AuditEventResponse` record — missing `ipAddress` and `userAgent`
- Lines 78-89: `from()` factory method — does not map `getIpAddress()` or `getUserAgent()`

## Fix

1. **Add fields to `AuditEventResponse`** in `AuditEventController.java`:
   - Add `String ipAddress` and `String userAgent` to the record declaration (after `source`, before `details`)
2. **Update the `from()` factory method** to map the two new fields:
   - Add `event.getIpAddress()` and `event.getUserAgent()` to the constructor call

### Before (lines 67-89):
```java
public record AuditEventResponse(
    UUID id,
    String eventType,
    String entityType,
    UUID entityId,
    UUID actorId,
    String actorType,
    String source,
    Map<String, Object> details,
    Instant occurredAt) {

  public static AuditEventResponse from(AuditEvent event) {
    return new AuditEventResponse(
        event.getId(),
        event.getEventType(),
        event.getEntityType(),
        event.getEntityId(),
        event.getActorId(),
        event.getActorType(),
        event.getSource(),
        event.getDetails(),
        event.getOccurredAt());
  }
}
```

### After:
```java
public record AuditEventResponse(
    UUID id,
    String eventType,
    String entityType,
    UUID entityId,
    UUID actorId,
    String actorType,
    String source,
    String ipAddress,
    String userAgent,
    Map<String, Object> details,
    Instant occurredAt) {

  public static AuditEventResponse from(AuditEvent event) {
    return new AuditEventResponse(
        event.getId(),
        event.getEventType(),
        event.getEntityType(),
        event.getEntityId(),
        event.getActorId(),
        event.getActorType(),
        event.getSource(),
        event.getIpAddress(),
        event.getUserAgent(),
        event.getDetails(),
        event.getOccurredAt());
  }
}
```

3. **Update tests** that assert on `AuditEventResponse` shape:
   - Search for tests referencing `AuditEventResponse` and update expected field counts or JSON assertions.

## Scope

Backend only.

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java`
- Any test files that construct or assert on `AuditEventResponse` (check `AuditEventControllerTest.java`, `InternalAuditControllerTest.java`)

Files to create: none
Migration needed: no (data already in DB)

## Verification

Re-run T4.10 checkpoint — `GET /api/audit-events` should now include `ipAddress` and `userAgent` fields in each event response.

## Estimated Effort

S (< 30 min) — add two fields to a record and its factory method, plus update test assertions.
