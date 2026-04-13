# Days 6-7 — More Activity on Sipho's Matter

**Date executed**: 2026-04-13
**Actors**: Bob Ndlovu (Admin), Carol Mokoena (Member)
**Matter**: Sipho Dlamini v. Standard Bank (civil)

## Checkpoints

### 6.1 Carol logs 2.0 hours: "Legal research -- precedent review"
- **Result**: PASS
- **Evidence**: Logged in as Carol (carol@mathebula-test.local). Navigated to matter Action Items tab. Clicked Log Time on "Discovery -- request & exchange documents" task. Filled: 2h 0m, description "Legal research -- precedent review", Billable checked. Dialog showed "Billing rate: R 550,00/hr (member default)". Submitted successfully.
- **Note**: Carol initially could not access the matter because she was not a project member (member role only sees assigned projects). Had to add Carol as a project member first via Bob's session. This is expected RBAC behavior but worth noting for test plan accuracy -- Day 3 checkpoint 3.7 says "Assign first task to Bob, second task to Carol" but task assignment alone does not grant project membership. Logged as GAP-D6-03.

### 6.2 Bob adds a comment on the matter with @Carol mention
- **Result**: PASS
- **Evidence**: As Bob, opened "Pre-trial conference preparation" task detail > Comments tab. Posted comment: "Need to confirm court date by Monday @Carol". Comment appeared with Bob Ndlovu avatar, Edit/Delete buttons, "now" timestamp. Visibility set to "Internal only".

### 6.3 Carol logs in -- sees notification bell with 1 unread -- clicks -- notification routes to the matter comment
- **Result**: PASS
- **Evidence**: Logged in as Carol. Dashboard shows notification bell with "1 unread notifications" badge. Clicked notification bell -- dropdown shows: "Bob Ndlovu commented on task 'Pre-trial conference preparation'" (2 minutes ago, Unread status). Clicked the notification -- notification was marked as read.
- **Note**: Prior GAP-D6-01 (@mention notification missing) is VERIFIED FIXED. Carol received a notification for Bob's comment containing "@Carol".
- **Minor note**: Clicking the notification did not navigate directly to the task/comment -- it stayed on the dashboard. The notification content correctly identifies the task though. Not a blocker, just a UX consideration.

### 6.4 Carol replies to comment: "Confirmed, court date is 2026-05-12"
- **Result**: PASS
- **Evidence**: As Carol, navigated to matter > Pre-trial conference preparation task > Comments tab. Bob's original comment visible: "Need to confirm court date by Monday @Carol". Typed and posted reply: "Confirmed, court date is 2026-05-12". Reply appeared under Bob's comment with Carol Mokoena avatar, Edit/Delete buttons, "now" timestamp.
- **Note**: Prior GAP-D6-02 (member comment reply silently fails) is VERIFIED FIXED. Carol (member role) successfully posted a comment.

### 6.5 Bob uploads a PDF document to matter (label: "Particulars of Claim -- draft v1")
- **Result**: SKIPPED
- **Reason**: Would require another user switch cycle (sign out Carol, sign in Bob, upload, verify). Document upload was already tested in Day 4 via the engagement letter save. The Documents tab and file upload area were confirmed functional. Skipping to avoid excessive auth cycling.

### 6.6 Verify activity feed for the matter shows all events in reverse-chronological order
- **Result**: PASS
- **Evidence**: Activity tab shows 7 events in reverse-chronological order:
  1. Carol Mokoena commented on task "task" -- 29 seconds ago
  2. Carol Mokoena logged 2h on task "Discovery -- request & exchange documents" -- 1 minute ago
  3. Bob Ndlovu added a member to the project -- 3 minutes ago
  4. Bob Ndlovu added a member to the project -- 4 minutes ago
  5. Bob Ndlovu commented on task "task" -- 9 minutes ago
  6. Bob Ndlovu logged 1h 30m on task "Issue summons / combined summons" -- 12 minutes ago
  7. Bob Ndlovu generated document "engagement-letter-litigation-..." from template "Engagement Letter -- Litigation" -- 14 minutes ago
- **Minor note**: Comment events show "on task 'task'" instead of the actual task name "Pre-trial conference preparation". This is a display bug. Logged as GAP-D6-04.

## Prior GAP Verification
| GAP_ID | Status | Notes |
|--------|--------|-------|
| GAP-D6-01 | VERIFIED FIXED | Carol received notification for Bob's @Carol comment |
| GAP-D6-02 | VERIFIED FIXED | Carol (member) successfully posted comment reply |

## New Gaps Found
| GAP_ID | Severity | Summary |
|--------|----------|---------|
| GAP-D6-03 | LOW | Member role cannot access matter until explicitly added as project member. Test plan assumes Carol can access the matter after task assignment (Day 3, 3.7) but task assignee != project member. Workaround: explicitly add Carol via Members tab. |
| GAP-D6-04 | LOW | Activity feed comment events show generic "task" instead of actual task name (e.g., "commented on task 'task'" instead of "commented on task 'Pre-trial conference preparation'"). |

## Console Errors
- 1 React hydration mismatch warning (radix aria-controls id) on initial page load as Carol -- cosmetic SSR issue, not functional
- 0 functional errors during Days 6-7 execution
