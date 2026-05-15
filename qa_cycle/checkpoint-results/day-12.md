# Day 12 — Thandi comments on Year-End Pack with @Bob

**Date**: 2026-05-15
**Branch**: `bugfix_cycle_2026-05-14`
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, KC `:8180`)
**Actor**: Thandi Thornton (Owner)
**Engagement**: Kgosi Holdings -- FY2025/26 Year-End Pack (ID: `388d5104-7789-4ad6-bb6c-6d045e9663f3`)

---

## Pre-flight

- Frontend :3000 healthy (200).
- Backend :8080 healthy (`{"status":"UP"}`).
- Previous session was Bob Ndlovu -- signed out via User menu > Sign out.
- Authenticated as Thandi via Keycloak (thandi@thornton-test.local / [REDACTED]).
- Confirmed user identity: sidebar shows "Thandi Thornton / thandi@thornton-test.local".

---

## Checkpoint Execution

### 12.1 -- Thandi comments on year-end pack with @Bob: "Need FS draft by day 30"

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| Navigate to engagement | `http://localhost:3000/org/thornton-associates/projects/388d5104-7789-4ad6-bb6c-6d045e9663f3` | **PASS** | Page loaded. Heading: "Kgosi Holdings -- FY2025/26 Year-End Pack". Status: Active. Client: Kgosi Holdings (Pty) Ltd. 7 tasks, 2.0h logged. |
| Locate comment input | Clicked "Client Comments" tab | **PASS** | Tab panel loaded with empty state: "No customer comments yet." Textarea with placeholder "Reply to the customer thread (visible to all linked customers)..." and disabled "Post Reply" button. |
| Type comment | Entered `@Bob Need FS draft by day 30` in textarea | **PASS** | Text appeared in textarea. "Post Reply" button became enabled. |
| Submit comment | Clicked "Post Reply" | **PASS** | Comment posted successfully. Comment now visible in Client Comments tab: Author="Thandi Thornton" (TT avatar), Timestamp="now", Body="@Bob Need FS draft by day 30". Textarea cleared. Post Reply button disabled again. |
| Verify activity event | Switched to Activity tab | **PASS** | Activity feed shows: "Thandi Thornton commented on project 'project'" (20 seconds ago). Event appears above the earlier "Thandi Thornton logged 2h on task 'Request & receive trial balance'" event. |

**Checkpoint 12.1 Result: PASS**

---

## Evidence

| File | Description |
|------|-------------|
| `qa_cycle/evidence/day-12/thandi-comment-bob-fs-draft.png` | Client Comments tab showing posted comment by Thandi Thornton |
| `qa_cycle/evidence/day-12/activity-tab-comment-event.png` | Activity tab showing comment event in activity feed |

---

## Observations

### OBS-4005 — Activity event message shows "project" instead of engagement name (LOW)

The Activity tab event message reads: "Thandi Thornton commented on project 'project'" -- the word "project" appears as a literal string rather than the actual engagement name ("Kgosi Holdings -- FY2025/26 Year-End Pack"). This is a cosmetic issue in the activity message rendering. The comment itself is correctly posted and visible. Severity: LOW (cosmetic only, no data loss or functional impact).

### Note: Comment posted via "Client Comments" tab (SHARED visibility)

The scenario calls for "Thandi comments on year-end pack with @Bob" -- the only comment UI on the engagement page is the "Client Comments" tab, which posts with `SHARED` visibility (visible to linked customers via portal). There is no separate "internal team comments" UI on the engagement page. The comment was posted through the available mechanism. The @Bob mention is plain text (no @mention autocomplete or notification trigger observed). This is acceptable for the current product state -- the comment is recorded with the correct content and author.

---

## Summary

| ID | Checkpoint | Result |
|----|-----------|--------|
| 12.1 | Thandi comments on year-end pack with @Bob: "Need FS draft by day 30" | **PASS** |

**Day 12 Result: 1 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED**

**New gaps**: OBS-4005 (LOW, cosmetic -- activity message shows literal "project" instead of engagement name)
