# 634C red evidence (G88-M6, C2) — run at HEAD `1c6bd158a`, before any fix

Only test files were added/extended for these runs. No production code (`backend/src/main`, `frontend/components`,
`frontend/app`, `frontend/lib`) had been changed, and the V11 migration did not exist yet.

Backend command (one run covers both gaps):
`./mvnw test -Dtest="V11NotificationCommentIdTest,NotificationCommentIdIntegrationTest,CommentMentionAccessIntegrationTest,CommentMentionNotificationIntegrationTest" -q`
→ exit 1, `Tests run: 24, Failures: 9, Errors: 1, Skipped: 0`.

Frontend command (one run covers G88-M6):
`pnpm exec vitest run __tests__/notifications/notification-item.test.tsx __tests__/comments/comment-item.test.tsx __tests__/components/tasks/task-detail-sheet.test.tsx __tests__/components/documents/documents-panel.test.tsx`
→ exit 1, `Test Files 4 failed (4)`, `Tests 10 failed | 27 passed (37)`.

## G88-M6 — comment notification deep link

### Backend — `notification/V11NotificationCommentIdTest` (new)

```
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 0.503 s <<< FAILURE! -- in io.b2mash.b2b.b2bstrawman.notification.V11NotificationCommentIdTest
[ERROR]   V11NotificationCommentIdTest.migrateScratchSchemaFromV10:76 » Flyway No migration with a target version 11 could be found. Ensure target is specified correctly and the migration exists.
```

### Backend — `notification/NotificationCommentIdIntegrationTest` (new)

```
[ERROR] Tests run: 4, Failures: 4, Errors: 0, Skipped: 0, Time elapsed: 0.807 s <<< FAILURE! -- in io.b2mash.b2b.b2bstrawman.notification.NotificationCommentIdIntegrationTest
[ERROR]   NotificationCommentIdIntegrationTest.taskComment_notificationCarriesTheCommentId:76->bobNotificationFor:178 [Bob's notification for comment 070b028e-81b0-444f-9bf2-4e2a71bc9ad8]
Expected size: 1 but was: 0 in:
[]
[ERROR]   NotificationCommentIdIntegrationTest.documentComment_notificationCarriesTheCommentId:90->bobNotificationFor:178 [Bob's notification for comment cb608764-50a4-4d92-96e3-53023ce32605]
Expected size: 1 but was: 0 in:
[]
[ERROR]   NotificationCommentIdIntegrationTest.matterComment_notificationCarriesTheCommentId:102->bobNotificationFor:178 [Bob's notification for comment a1527c36-9aae-4589-90ad-4da101b7383c]
Expected size: 1 but was: 0 in:
[]
[ERROR]   NotificationCommentIdIntegrationTest.nonCommentNotification_hasNullCommentId:133
Expecting actual:
  {"body"=null, "createdAt"="2026-11-05T11:39:32.402361Z", "id"="2a63ab6c-…", "isRead"=false, "referenceEntityId"="bf04b995-…", "referenceEntityType"="TASK", "referenceMatterId"="917a96de-…", "title"="Alice Author assigned you to task "Assigned task 1"", "type"="TASK_ASSIGNED"}
to contain key:
  "referenceCommentId"
```

### Frontend — vitest

```
 FAIL  __tests__/notifications/notification-item.test.tsx > NotificationItem > comment deep links (G88-M6) > TASK comment → the task sheet on the comment
-   "/org/acme/matters/p1?tab=tasks&taskId=t1&commentId=c1",
+   "/org/acme/matters/p1?tab=tasks",
 FAIL  … > DOCUMENT comment → the documents tab expanded on the document
-   "/org/acme/matters/p1?tab=documents&documentId=d1&commentId=c1",
+   "/org/acme/matters/p1?tab=documents",
 FAIL  … > PROJECT comment → the matter Comments tab on the comment
-   "/org/acme/matters/p1?tab=comments&commentId=c1",
+   "/org/acme/matters/p1",
 FAIL  … > calls onNavigate after navigating (closes the bell popover)
AssertionError: expected "vi.fn()" to be called 1 times, but got 0 times
 FAIL  __tests__/comments/comment-item.test.tsx > CommentItem deep-link anchor + highlight (G88-M6) > renders an id anchor for every comment
AssertionError: expected null not to be null
 FAIL  … > highlighted: scrolls the comment into view and rings it, then removes the ring after ~2 s
AssertionError: expected null not to be null
 FAIL  … > not highlighted: no scroll and no ring
Error: expect(received).not.toHaveClass()   Received has value: null
 FAIL  __tests__/comments/comment-item.test.tsx > CommentSectionClient ?commentId= targeting (G88-M6) > highlights only the comment named by ?commentId=
Error: expect(received).toHaveClass()   Received has value: null
 FAIL  __tests__/components/tasks/task-detail-sheet.test.tsx > TaskDetailSheet > opens on the Comments tab when a commentId is targeted
Error: expect(element).toHaveAttribute("aria-selected", "true")   (Received: aria-selected="false")
 FAIL  __tests__/components/documents/documents-panel.test.tsx > DocumentsPanel > expands the document named by ?documentId= on mount
TestingLibraryElementError: Unable to find an accessible element with the role "button" and name "Collapse comments for engagement-letter.pdf"
```

The two "legacy shape" pins added beside them are green at HEAD, as they should be: `COMMENT_ADDED without a comment
id (pre-V11 row) keeps the entity-level link` (`?tab=tasks`) and `PROJECT notification without a comment id links to the
matter itself`; so is `opens on the Time Entries tab when no commentId is targeted`.

Reading: `Notification.java` has no comment-id column (the entity stores `reference_entity_type/id/project_id` only),
so `handleCommentCreated` cannot persist which comment the notification is about and `NotificationResponse` has no
`referenceCommentId`. `notification-item.tsx:30-43` therefore builds `?tab=tasks` / `?tab=documents` / the bare matter
URL — no `taskId`, `documentId` or `commentId` — and the bell's `NotificationItem` never closes its popover.
`comment-item.tsx:33` renders `<div className="flex gap-3">` with no `id`, so there is no anchor to scroll to;
`task-detail-sheet.tsx` always opens on "Time Entries"; `documents-panel.tsx:177` seeds `expandedDocId` with `null`.

## C2 — `@mention` recipients filtered by matter access

### Backend — `notification/CommentMentionAccessIntegrationTest` (new)

Members: Alice owner (author); Bob admin and Olive owner, NOT on the matter; Carol Outsider member, NOT on the matter;
Dave Insider and Carol Insider members, on the matter.

```
[ERROR] Tests run: 8, Failures: 5, Errors: 0, Skipped: 0, Time elapsed: 12.38 s <<< FAILURE! -- in io.b2mash.b2b.b2bstrawman.notification.CommentMentionAccessIntegrationTest
[ERROR]   CommentMentionAccessIntegrationTest.memberNotOnTheMatter_isNotNotified:84
Expecting value to be false but was true
[ERROR]   CommentMentionAccessIntegrationTest.mixedMentions_notifyOnlyTheMembersWhoCanViewTheMatter:113
Expecting value to be false but was true
[ERROR]   CommentMentionAccessIntegrationTest.sharedFirstName_onlyTheMemberOnTheMatterIsNotified:125
Expecting value to be false but was true
[ERROR]   CommentMentionAccessIntegrationTest.documentComment_memberNotOnTheMatter_isNotNotified:133
Expecting value to be false but was true
[ERROR]   CommentMentionAccessIntegrationTest.matterComment_memberNotOnTheMatter_isNotNotified:146
Expecting value to be false but was true
```

The positive controls are green at HEAD: `adminNotOnTheMatter_isNotified` (Bob), `ownerNotOnTheMatter_isNotified`
(Olive), `memberOnTheMatter_isNotified` (Dave).

Reading: `NotificationService.resolveMentionedMemberIds` (`NotificationService.java:852-863`) builds its candidates
from `memberRepository.findAll()` and matches names only. Nothing checks whether a candidate can view the matter, so
Carol, who is not on the matter, gets a `COMMENT_ADDED` row whose title carries the task title or document file name,
on TASK, DOCUMENT and PROJECT targets alike.

### `comment/CommentMentionNotificationIntegrationTest` (#1720) — before the D3 setup edit

Same run, green at HEAD: `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0` (its mentioned members are org members,
NOT on the matter). The C2 filter would turn its "is notified" cases red, so D3 adds the four mentioned members to the
matter in `@BeforeAll`. That is a setup change only; no assertion changes. Results after the fix are in the PR body.
