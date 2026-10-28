# 627A re-observation, fix cycle 1 (head ba68f24f5)

- Date: 2026-10-28
- Stack: e2e mock-auth. Only the frontend was rebuilt from the worktree (`docker compose ... build frontend`, exit 0) and recreated with `up -d --no-deps frontend`; container created 2026-10-28T05:04:46Z.
- Browser: Playwright MCP, Alice (owner), org `e2e-test-org`.

| Check | Result |
|---|---|
| P1 G-A6 New Matter → conflict link (Sipho, Khumalo) + submit | PASS |
| P2 no client selected | PASS |
| P3 client overflow "Run Conflict Check" back-compat | PASS |
| P4 bad customerId / bogus checkType | PASS |
| P5 header strips + Fees › Time recording tab | PASS |
| P6 console errors | PASS (0 errors, 0 warnings) |

## P1 (G-A6, was O5-1 FAIL): PASS
- The dialog href with Sipho selected is `/org/e2e-test-org/conflict-check?checkType=NEW_MATTER&customerId=421235da-e59f-47fc-be08-be9a3e777b7d` (screenshot 01).
- After clicking, the landing URL is `http://localhost:3001/org/e2e-test-org/conflict-check?checkType=NEW_MATTER&customerId=421235da-e59f-47fc-be08-be9a3e777b7d`. It has no `checkedName`, no `checkedIdNumber` and no ID digits.
- The form shows:
  - Name to Check = `Sipho Ndlovu mvrll4pa359c`
  - ID Number = `8501015800087`
  - Check Type = New Matter
  - Client = Sipho Ndlovu mvrll4pa359c

  Screenshot 02.
- Khumalo lands on `...conflict-check?checkType=NEW_MATTER&customerId=5129c17a-e938-420b-ab97-d7fd2039fd65`. The form shows:
  - Name = `Khumalo Property Holdings mvrll4pa359c`
  - ID = `7605125009081`
  - Registration Number blank (the DB value is also empty)
  - Type = New Matter
  - Client = Khumalo

  Screenshot 04.
- The DB matches: `customers.id_number` is 8501015800087 for Sipho and 7605125009081 for Khumalo.
- Submitting the check for Sipho rendered the result card "Potential Conflict — Checked "Sipho Ndlovu mvrll4pa359c" at 28/10/2026, 07:05:20". The table has 4 matches and History went from (0) to (1). No error. Screenshot 03.

## P2: PASS
With no client selected, the href and landing URL are `?checkType=NEW_MATTER`. Name and ID are blank, Type = New Matter and Client = None (screenshot 05).

## P3: PASS
- Client `421235da…` → More actions → Run Conflict Check lands on `/org/e2e-test-org/conflict-check?customerId=421235da-e59f-47fc-be08-be9a3e777b7d&checkedName=Sipho%20Ndlovu%20mvrll4pa359c`.
- The form shows Name = Sipho Ndlovu mvrll4pa359c and ID = 8501015800087. The ID number is new here; it is resolved server-side and is not in the URL.
- Type is New Client (the default, because this link carries no checkType) and Client = Sipho. Screenshot 07.

## P4: PASS
- `?customerId=not-a-uuid`, `?customerId=00000000-0000-0000-0000-000000000000` and `?checkType=BOGUS` all render the normal "Conflict Check" page.
- In each case Name and ID are blank, Type = New Client and Client = None (screenshots 08–10).
- For the zero UUID, the frontend server log shows the handled line `Failed to resolve conflict-check client: Error [ApiError]: No customer found with id 0000…`. There is no error page.

## P5: PASS
- Matter `7a577920…` `[data-testid="matter-header-fields"]` reads visually "Case no. 2026/12345 · Gauteng Division, Pretoria · Road Accident Fund". "Court" and "Opposing Party" are sr-only, as in after/ (screenshot 11).
- Client `421235da…` `[data-testid="client-header-fields"]` innerText is `ID\n8501015800087` (screenshot 06).
- From `?tab=tasks`, clicking `tab-group-finance` then `tab-item-time` changes the URL to `?tab=time`. The selected tab is "Fees · Time recording" and panel `…-content-time` renders (screenshot 12).

## P6: PASS
`browser_console_messages(level=warning, all=true)` returned 0 errors and 0 warnings. There are no backend ERROR/WARN lines in the window.

## Observations (pre-existing, not introduced by this PR)
- The conflict result table's **Party Name column is empty** for all 4 rows, including EXISTING_CLIENT and MATTER_NAME matches.
- `conflict-check-result.tsx:131` renders `match.adversePartyName`, which is null for these match kinds.
- This PR does not touch that file; it was last changed in #1421.
- Severity: Minor, UX. Candidate for the gap register.
