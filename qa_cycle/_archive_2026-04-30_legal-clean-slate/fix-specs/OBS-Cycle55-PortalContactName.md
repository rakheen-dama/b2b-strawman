# Fix Spec: OBS-Cycle55-PortalContactBucketedAsSystem — Resolve portal_contact display names in matter Activity actor filter

## Problem

The matter Activity tab actor filter dropdown buckets every PORTAL_CONTACT event under a single "System" entry (with an "S" avatar) instead of rendering each portal contact's display name (e.g. "Sipho Dlamini"). The same collapsing happens in the Activity feed row labels — rows authored by Sipho show the prefix "System" instead of "Sipho Dlamini" — and the avatar renders "S" instead of the contact's initials ("SD").

The underlying data is intact: cycle-55 day-85 verified that `audit_events.actor_id` carries Sipho's portal_contact UUID `f3f74a9d-…` on 12 of 13 PORTAL_CONTACT rows on the RAF matter (`portal.document.downloaded` ×2, `portal.document.upload_initiated` ×5, `portal.invoice.paid` ×1, `portal.request_item.submitted` ×5; only 1/13 has NULL actor_id, tracked separately as `OBS-Cycle55-PortalInvoicePaidNullActorId`). Rows are queryable by that UUID. Only the firm-side rendering layer collapses them.

This persists across cycles 55 → 56 → 57 (Day 90 final walk reconfirmed under "Reconfirmed observations: OBS-Cycle55-{PortalContactBucketedAsSystem,…} all persist").

Evidence:
- `qa_cycle/checkpoint-results/day-85.md §85.4a + §85.4b`
- `qa_cycle/checkpoint-results/cycle55-day85-1.4-firm-activity-system-filter.png` (System-filter active — selects 7 portal events but labels them all "System")
- `qa_cycle/checkpoint-results/cycle55-day85-1.4-audit-actors.txt` (DB confirms 12/13 PORTAL_CONTACT rows carry portal_contact_id `f3f74a9d-…`)
- `qa_cycle/checkpoint-results/day-90.md §Day 90 Cycle 57` ("Reconfirmed observations: OBS-Cycle55-PortalContactBucketedAsSystem persists")

## Root Cause

`ActivityMessageFormatter.resolveActorName` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java:207-221`) only resolves USER actors. The lookup map handed in by `ActivityService.getProjectActivity` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityService.java:74-83`) is built from `MemberRepository.findAllById(actorIds)` — no parallel lookup against `portal_contacts`.

For a PORTAL_CONTACT actor:
1. `event.getActorId()` is non-null (it carries the portal_contact UUID).
2. `actorMap.get(actorId)` returns null (the portal_contact_id is not a member_id).
3. The fallback at line 217 checks `details.actor_name` — a string only some emitters populate (legacy, ad-hoc).
4. When `details.actor_name` is missing, the formatter returns `"Unknown"`.

In practice the firm sees "System" rather than "Unknown" because `actorId` is null for one specific event (`portal.invoice.paid` — `OBS-Cycle55-PortalInvoicePaidNullActorId`). For every other PORTAL_CONTACT row with a real `actor_id`, the formatter returns either `details.actor_name` (when that emitter happens to set it) or `"Unknown"` (when it doesn't). The user-visible result on the test tenant is that all PORTAL_CONTACT rows collapse into a single "System" bucket — different code paths converging on the same cosmetic outcome.

The actor_type column (`audit_events.actor_type`) — `USER`, `SYSTEM`, `PORTAL_CONTACT`, `WEBHOOK` — is never inspected by the formatter; the resolver branches purely on `actorId == null` vs. lookup-hit/miss.

## Fix

**Direction: Option B — backend hydration, mirroring the existing USER actor pattern.**

The firm-side gold standard is: server-side batch-resolve actor display name + avatar from a tenant-scoped repository, attach to the `ActivityItem` DTO, return ready-to-render. We extend that exact pattern to `PORTAL_CONTACT` actors by adding a parallel `portal_contacts` batch lookup keyed on `event.actorId` for events whose `actor_type = 'PORTAL_CONTACT'`. No new endpoint, no client-side fetch, no contract change to `ActivityItem`. Avatar stays null for portal contacts (consistent with current null-avatar fallback to initials in `ActivityItem.tsx`).

### Why Option B (backend hydrate) over Option A (frontend lookup)

1. **Mirrors the existing USER pattern.** USER display names are batch-resolved server-side from `members`. Adding a parallel batch resolution from `portal_contacts` keeps the architecture coherent — same shape, same place. Option A would split the resolver across the FE/BE boundary, which is harder to reason about and harder to test.
2. **One round-trip, no N+1.** `MemberRepository.findAllById(memberIds)` is already issued as a single batch on every page; `PortalContactRepository.findAllById(portalContactIds)` adds at most one extra batch query. Frontend lookup would need either an extra fetch per matter (one round-trip per Activity tab open) or a denormalised "portal contacts on this matter" list bolted onto the matter-detail GET — both are strictly more work than the backend resolver.
3. **Tenant isolation already correct.** `portal_contacts` is per-tenant (schema-per-tenant), so `findAllById` respects search_path automatically; no extra `orgId` filter needed.
4. **No frontend change.** `ActivityItem.actorName`/`actorAvatarUrl` already exist on the wire; the frontend just renders what the backend sends. Smaller diff, smaller blast radius.

### Backend changes

**File 1 — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityService.java`**

Add `PortalContactRepository` to the constructor. After the existing member batch lookup, build a parallel portal-contact lookup keyed on `actor_id` for events with `actor_type = "PORTAL_CONTACT"`. Pass both maps to the formatter.

```java
// inject
private final PortalContactRepository portalContactRepository;

// inside getProjectActivity, after step 4:
var portalContactIds =
    events.stream()
        .filter(e -> "PORTAL_CONTACT".equals(e.getActorType()))
        .map(AuditEvent::getActorId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

Map<UUID, PortalContact> portalContactMap =
    portalContactRepository.findAllById(portalContactIds).stream()
        .collect(Collectors.toMap(PortalContact::getId, Function.identity()));

// step 5:
var items =
    events.stream()
        .map(event -> activityMessageFormatter.format(event, actorMap, portalContactMap))
        .toList();
```

**File 2 — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java`**

Extend `format(...)` to accept `Map<UUID, PortalContact>` and update `resolveActorName` + `resolveActorAvatarUrl` to branch on `event.getActorType()`:

```java
public ActivityItem format(
    AuditEvent event,
    Map<UUID, Member> actorMap,
    Map<UUID, PortalContact> portalContactMap) {
  String actorName =
      resolveActorName(event.getActorType(), event.getActorId(),
                       actorMap, portalContactMap, event.getDetails());
  String actorAvatarUrl =
      resolveActorAvatarUrl(event.getActorType(), event.getActorId(), actorMap);
  // …rest unchanged
}

private String resolveActorName(
    String actorType,
    UUID actorId,
    Map<UUID, Member> actorMap,
    Map<UUID, PortalContact> portalContactMap,
    Map<String, Object> details) {
  if (actorId == null) {
    return "System";
  }
  if ("PORTAL_CONTACT".equals(actorType)) {
    PortalContact pc = portalContactMap.get(actorId);
    if (pc != null && pc.getDisplayName() != null && !pc.getDisplayName().isBlank()) {
      return pc.getDisplayName();
    }
    if (pc != null && pc.getEmail() != null) {
      return pc.getEmail(); // fallback when display_name not set
    }
    // anonymized / archived / orphan portal_contact
    if (details != null && details.get("actor_name") instanceof String name) {
      return name;
    }
    return "Portal user";
  }
  // existing USER path
  Member member = actorMap.get(actorId);
  if (member != null) {
    return member.getName();
  }
  if (details != null && details.get("actor_name") instanceof String name) {
    return name;
  }
  return "Unknown";
}

private String resolveActorAvatarUrl(
    String actorType, UUID actorId, Map<UUID, Member> actorMap) {
  if (actorId == null || "PORTAL_CONTACT".equals(actorType)) {
    return null; // portal contacts have no avatar; FE falls back to initials
  }
  Member member = actorMap.get(actorId);
  return member != null ? member.getAvatarUrl() : null;
}
```

Existing single-arg `resolveActorAvatarUrl(UUID, Map<UUID, Member>)` is removed in favour of the type-aware overload above.

**File 3 — `backend/src/test/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatterTest.java`**

Add 3 new test cases:
1. PORTAL_CONTACT actor with non-null `display_name` → renders display_name (e.g. `"Sipho Dlamini downloaded …"` instead of `"Unknown …"` / `"System …"`).
2. PORTAL_CONTACT actor with NULL `display_name` but non-null `email` → renders email.
3. PORTAL_CONTACT actor whose UUID is not in `portalContactMap` (anonymized / orphan) → falls back to `details.actor_name`, then to `"Portal user"`.

Existing USER-path tests stay green (signature change is purely additive — extra Map<UUID, PortalContact> param).

### Frontend changes

**None.** `ActivityItem.actorName` / `actorAvatarUrl` are already on the wire and `frontend/components/activity/activity-feed-client.tsx` (lines 44-54) derives the actor-filter dropdown from distinct `item.actorName` values. Once the backend stops returning the same string ("System" / "Unknown") for every portal contact, the dropdown will list each portal contact by display name automatically — and `frontend/components/activity/activity-item.tsx` (lines 49-50) already runs `getInitials(item.actorName)` for the avatar fallback, so "SD" appears for "Sipho Dlamini" with no FE change.

The TODO comment at `activity-feed-client.tsx:39-43` ("client-side actor extraction … TODO(Phase 69): replace with backend facet endpoint") is unaffected — that's a separate completeness improvement (showing actors who touched the matter but are not on the current page).

## Scope

- **Layer**: backend only.
- **Files modified**:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityService.java`
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java`
- **Files added**:
  - `backend/src/test/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatterTest.java` (3 new cases) — file already exists, add cases.
- **Frontend**: no change.
- **Database**: no migration. `portal_contacts` table + `audit_events.actor_id` already populated correctly.
- **API contract**: `ActivityItem` DTO unchanged.

## Verification (QA steps)

Browser-driven on `bugfix_cycle_2026-04-26-slice1` after Dev lands the fix.

1. Authenticate as Bob (admin) at `:3000` via Keycloak; navigate to RAF matter `/org/mathebula-partners/projects/cc390c4f-…?tab=activity`.
2. Capture `cycleNN-OBS-PortalContactName-step2-firm-activity-tab.yml`. **Expected**: actor-filter combobox now lists `All actors / Bob Ndlovu / Sipho Dlamini / Thandi Mathebula / System` (4 named options + System for the 1 NULL actor_id row tracked under `OBS-Cycle55-PortalInvoicePaidNullActorId`). The "System" bucket should drop from ~7 portal events to ~1 (the null-actor `portal.invoice.paid` row).
3. Open the actor combobox and select "Sipho Dlamini". Capture `step3-firm-activity-sipho-filter.yml`. **Expected**: 12 rows render, all prefixed "Sipho Dlamini" (`portal.document.downloaded` ×2, `portal.document.upload_initiated` ×5, `portal.request_item.submitted` ×5). Avatar circle renders "SD" initials, not "S".
4. Switch to "System" filter. Capture `step4-firm-activity-system-filter.yml`. **Expected (after slice 1)**: zero PORTAL_CONTACT rows in this bucket — slice 1 also lands `OBS-Cycle55-PortalInvoicePaidNullActorId` so the previously NULL-actor `portal.invoice.paid` row now resolves through the PRIMARY portal_contact and renders under that contact's name. (Pre-slice-1 baseline: only 1 row — the null-actor `portal.invoice.paid`.)
5. Switch to "Bob Ndlovu" filter. Capture `step5-firm-activity-bob-filter.yml`. **Expected**: 11 firm-user rows unchanged from cycle 55 baseline (regression invariant — USER path untouched).
6. DB invariant: `SELECT COUNT(*) FROM audit_events WHERE actor_type='PORTAL_CONTACT' AND actor_id IS NOT NULL` should still report 12 — the fix only changes rendering, not data.
7. Backend test: `./mvnw test -Dtest=ActivityMessageFormatterTest` passes (existing USER cases + 3 new PORTAL_CONTACT cases).
8. Regression: `OBS-Cycle55-PortalInvoicePaidNullActorId` was **bundled into slice 1** (separate PR #1209) — both fixes ship together, so the 1/13 NULL-actor `portal.invoice.paid` row should now resolve to a real PORTAL_CONTACT actor_id on subsequent webhook reconciliations (existing rows persist as-is — the fix is forward-only).

## Effort

**S** (~30 min)

- ActivityService: 1 new dependency injection + 1 new batch lookup + 1 modified call site (~10 lines).
- ActivityMessageFormatter: 1 method-signature widening + 1 branch in resolveActorName + 1 branch in resolveActorAvatarUrl (~25 lines).
- Tests: 3 new cases mirroring existing patterns (~30 lines).

No backend restart caveats beyond standard Java-source rebuild.

## Severity

**LOW** — cosmetic in the firm-side Activity tab. Data plane is correct; isolation is correct; only the rendering layer collapses portal-contact identity.

## Demo impact

Cosmetic, but **production-readiness for client-facing audit narratives requires accurate actor attribution**. A reviewer evaluating audit-trail completeness (E.14) in the demo would likely ask "who is 'System' on these portal events?" — the current rendering invites that question. Renders production-ready once individual portal contacts are named.

Not a Day 90 blocker (Day 90 walk completed PASS with this OBS persisting). Cleanup polish before any external client demo or POPIA audit walkthrough.

## Do-not-bundle

- **Do NOT bundle with `OBS-Cycle55-PortalInvoicePaidNullActorId`** — that's an emitter-side fix in `PaymentReconciliationService` (audit builder omits `.actorId(...)` on the `portal.invoice.paid` event). Different layer, different file. After this spec lands, that 1/13 NULL row continues to render as "System" — fixing it is the responsibility of the separate emitter-side spec.
- **Do NOT bundle with `GAP-L-100`** — that's the portal `/activity` Firm-actions tab over-disclosure + raw-slug rendering; landed on `main` via PR #1205 (squash `c0c1b60d`). Different surface (portal-side), different file (`PortalActivityEventResponse.summaryFor`).
- **Do NOT bundle with `GAP-L-99`** (manual digest trigger), `GAP-L-101` (retention UI), `TERM-CYCLE57` (terminology basket), or `OBS-Cycle55-KCFormDoubleSubmit` (Playwright/KC interaction quirk).
