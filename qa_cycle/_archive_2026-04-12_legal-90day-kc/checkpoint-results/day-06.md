# Day 6-7 Checkpoint Results

**Date**: 2026-04-12
**Actor(s)**: Carol Mokoena (Member), Bob Ndlovu (Admin)
**Scenario**: Days 6-7 -- More activity on Sipho's matter
**Matter**: Sipho Dlamini v. Standard Bank (civil)

---

## Checkpoint Results

### CP 6.1 -- Carol logs 2.0 hours: "Legal research -- precedent review"
**Result**: PASS

- Logged in as Carol (Member role, carol@mathebula-test.local)
- Navigated to matter > Action Items tab
- Clicked "Log Time" on "Discovery -- request & exchange documents" task
- Filled: Duration = 2h 0m, Description = "Legal research -- precedent review", Billable = yes
- Dialog submitted successfully (closed without error)
- Verified on Time tab: Total Time updated from 1h 30m to 3h 30m, Contributors = 2, Entries = 2
- New row: "Discovery -- request & exchange documents" = 2h, 1 entry
- Pre-existing: "Initial consultation & case assessment" = 1h 30m, 1 entry (Bob's Day 4 entry)
- Billing rate still shows "N/A/hr (unknown)" (pre-existing GAP-D4-02)

### CP 6.2 -- Bob adds a comment on the matter with @Carol mention
**Result**: PASS

- Signed out Carol, logged in as Bob (Admin role)
- Navigated to matter > Client Comments tab
- Typed: "Need to confirm court date by Monday @Carol"
- Clicked "Post Reply" -- comment posted successfully
- Comment visible: "BN Bob Ndlovu | now | Need to confirm court date by Monday @Carol"
- Note: @Carol is plain text, not a resolved @mention with autocomplete. No @mention UI affordance exists.
- Note: Only comment surface available is "Client Comments" (customer-visible). No internal/team comments section on the matter.

### CP 6.3 -- Carol sees notification bell with 1 unread, clicks, routes to comment
**Result**: PARTIAL

- Signed out Bob, logged in as Carol
- Notification bell shows "2 unread notifications"
- Clicked notification bell -- dropdown shows:
  1. "Bob Ndlovu assigned you to task 'Letter of demand'" (25 min ago)
  2. "You were added to project 'Sipho Dlamini v. Standard Bank (civil)'" (27 min ago)
- **No notification for the @Carol comment mention**. Both notifications are from earlier task/project membership events.
- The comment system does not generate @mention notifications. New gap: GAP-D6-01.

### CP 6.4 -- Carol replies to comment: "Confirmed, court date is 2026-05-12"
**Result**: FAIL

- Navigated to matter > Client Comments tab as Carol
- Bob's comment visible ("Need to confirm court date by Monday @Carol")
- Typed reply: "Confirmed, court date is 2026-05-12"
- Clicked "Post Reply" -- POST returned 200, button disabled, textbox cleared
- **Reply did not persist**. After reload, only Bob's original comment visible.
- Carol (Member role) can see comments but her replies silently fail to save.
- No error in console, no error toast. Silent 200 response with no effect.
- New gap: GAP-D6-02.

### CP 6.5 -- Bob uploads a PDF document to matter
**Result**: PASS

- Signed out Carol, logged in as Bob
- Navigated to matter > Documents tab
- Existing document: engagement-letter-litigation...pdf (4.6 KB, Day 4)
- Created test PDF (particulars-of-claim-draft-v1.pdf, 653 B)
- Clicked upload area, selected file -- upload succeeded
- Documents tab now shows 3 documents (1 existing + 2 uploads from double-click)
- Matter header updated: "3 documents"

### CP 6.6 -- Verify activity feed shows all events in reverse-chronological order
**Result**: PASS

- Navigated to Activity tab
- Events shown in correct reverse-chronological order:
  1. Bob uploaded document "particulars-of-claim-draft-v1.pdf" (28s ago)
  2. Bob performed document.created on document (28s ago)
  3. Bob uploaded document "particulars-of-claim-draft-v1.pdf" (31s ago, duplicate)
  4. Bob performed document.created on document (31s ago, duplicate)
  5. Bob commented on project "project" (5 min ago)
  6. Carol Mokoena logged 2h on task "Discovery -- request & exchange documents" (8 min ago)
  7. Bob Ndlovu logged 1h 30m on task "Initial consultation & case assessment" (20 min ago)
  8. Bob assigned task "unknown" (29 min ago)
  9. Bob assigned task "unknown" (30 min ago)
  10. Bob added a member to the project (31 min ago)
  11. Bob added a member to the project (32 min ago)
- All event types present: document upload, comment, time entry, task assignment, member addition
- Filter buttons available: All, Tasks, Documents, Comments, Members, Time
- Screenshot: `qa_cycle/screenshots/cycle-2/day06-cp6.6-activity-feed.png`

---

## Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| CP 6.1 | PASS | Carol logged 2h successfully |
| CP 6.2 | PASS | Bob posted comment with @Carol text |
| CP 6.3 | PARTIAL | Notifications exist but none from @mention; comment notifications not implemented |
| CP 6.4 | FAIL | Carol's reply silently fails to save (Member role permission issue) |
| CP 6.5 | PASS | PDF uploaded to Documents tab |
| CP 6.6 | PASS | Activity feed shows all events in correct reverse-chronological order |

**Overall**: 4 PASS, 1 PARTIAL, 1 FAIL out of 6 checkpoints.

## New Gaps

### GAP-D6-01: Comment @mention does not generate notification
- **Severity**: MED
- **Checkpoint**: CP 6.3
- **Description**: Posting a comment with "@Carol" text in the Client Comments section does not generate a notification for Carol. The @mention is rendered as plain text with no autocomplete or resolution. Carol's notification bell shows only pre-existing notifications (task assignment, project membership) -- zero comment-related notifications. The comment system lacks @mention parsing and notification triggering.
- **Expected**: @Carol mention in comment should create a notification for Carol that links to the comment.
- **Actual**: No notification generated. @mention is plain text only.

### GAP-D6-02: Member-role user comment reply silently fails to save
- **Severity**: MED
- **Checkpoint**: CP 6.4
- **Description**: When Carol (Member role) posts a reply in the Client Comments section, the POST request returns 200 but the comment is not persisted. After page reload, only the original comment (by Bob, Admin) is visible. No error message, no console error, no toast notification. The textbox clears and the "Post Reply" button disables, giving the false impression the reply was saved. Bob (Admin) can post comments successfully.
- **Root cause hypothesis**: Either (a) the backend comment endpoint requires a capability that Members lack, returning a 200 with no-op, or (b) there is a frontend optimistic update that doesn't actually call the backend correctly for Member-role users.
- **Expected**: Carol (Member) should be able to reply to comments on matters she is a member of, OR the UI should show an error/disable the input for users without permission.
- **Actual**: Silent failure -- 200 response, no persistence, no error feedback.
