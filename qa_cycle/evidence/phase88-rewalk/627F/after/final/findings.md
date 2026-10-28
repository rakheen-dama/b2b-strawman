# 627F final re-observation — PR #1698 head b44f0e47f

- Stack: e2e mock-auth :3001, fresh, frontend built from b44f0e47f (no rebuild). Date 2026-10-28.
- Tenant schema: tenant_7d218705360b. Observer: Bob (admin).
- The final head 37e4ae375 differs from b44f0e47f only by hiding "Open diary list" when the member is unidentified. That state can't be produced through the mock-auth UI; a failing-first unit assertion in `todays-agenda.test.tsx` covers it.

## F1 — PASS (identified member, nothing upcoming)
Pre-check (DB, read-only): 0 court dates in the tenant; 0 tasks due 2026-10-28..11-11.

My work:
- Next deadline: "No upcoming deadlines".
- Upcoming: "Nothing in the diary for the next 14 days".
- Header link "Open diary list" → /org/e2e-test-org/diary?view=list&from=2026-10-29&feeEarner=mine.

Evidence: F1-bob-mywork-empty.png

## F2 — PASS (more than 5 rows, mixed months)
Created through the UI as Bob: matter "627F Diary Links v Bob" (Acme Corp, Litigation template), id 9f4db120-e3c1-4cfc-b35c-471423078cec, with 6 HEARING court dates:

| Date | Court date id |
|---|---|
| 2026-10-29 | d6da4d8c-e57a-41f2-9346-e9b7fd3ed21f |
| 2026-10-31 | edb03f1e-89cd-48a9-b574-7285cf9561a2 |
| 2026-11-02 | fda985c0-5750-45ba-b8ec-f6f4480dc892 |
| 2026-11-04 | 90d13ded-c558-43c2-99e7-c5bf0e405482 |
| 2026-11-06 | 00ac9c7b-29b0-4f02-9859-938c1e25c1de |
| 2026-11-08 | bf4f2369-e90b-4cc0-b2ed-f05842554fcf |

My work:
- Upcoming: 5 rows (29 Oct, 31 Oct, 2 Nov, 4 Nov, 6 Nov) plus "+1 more".
- Next deadline: "Court date · Thu 29 Oct / 627F Court 1 / 1d remaining".

Observed hrefs:
- "Open diary list" → /org/e2e-test-org/diary?view=list&from=2026-10-29&feeEarner=mine
- "+1 more" → /org/e2e-test-org/diary?view=list&from=2026-11-08&feeEarner=mine (the first hidden row)

Clicking each link:
- "+1 more": the URL normalises to from=2026-11-01. The List tab shows "November 2026" with Fee earner "Mine". Rows are 2, 4, 6 and 8 Nov, including the hidden row 627F Court 6 (Sun 8 Nov), fee earner Bob Admin.
- "Open diary list": the URL normalises to from=2026-10-01. The List tab shows "October 2026" with "Mine". Rows are 29 Oct (Court 1) and 31 Oct (Court 2).

Evidence: F2-bob-mywork-5rows-plus1more.png, F2-plus1more-diary-list-november.png, F2-open-diary-list-october.png

## F3 — PASS
Console errors and warnings: 0 on My work (empty and populated) and on both Diary List views.
