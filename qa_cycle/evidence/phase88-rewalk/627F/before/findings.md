# 627F — reproduction findings (627.12), 2026-10-28

Reproduced by the 627F reproducer agent on the e2e mock-auth stack (:3001, main-equivalent code, tenant `tenant_7d218705360b`) and on the Keycloak dev stack (:3000, read-only, walked tenant `mathebula-partners` = `tenant_5039f2d497cf`). The supporting SELECTs are in `db-selects.txt`.

## G-C2 — Link to Matter crash: REPRODUCED
Steps (Alice, :3001): Legal › Adverse parties → "Road Accident Fund" → Actions → Link to Matter.
Result: the page falls into the error boundary. Console: `TypeError: Cannot read properties of undefined (reading 'map')` (`gc2-console-error.txt`, `gc2-link-to-matter-crash.png`).
Cause: `legal/adverse-parties/actions.ts:137-144` `fetchProjects` returns `.content` of the flat `Matter[]` list, which is `undefined`.
Contrast: the Conflict check matter picker lists 9 matters, because `conflict-check/actions.ts:108-113` handles both shapes (`gc2-conflict-check-matter-picker-ok.png`).

## G-D5 — My work omits the lead's upcoming court date: REPRODUCED (after creating data through the UI)
The fresh e2e data had no court dates and Bob led no matter. As Bob, through the UI, the reproducer created:
- matter `5b7fcf0c-8f25-4295-b56b-875cdde2c63c` "627F repro - Bob lead court date" (Acme Corp; Bob auto-lead);
- court date `cd4c6a2e-094b-4b9c-9460-fda74d0b7b99`, CASE_MANAGEMENT, 2026-11-05 09:30 (today+8).

Result: Bob's My work still shows "Nothing in the diary today" and "No upcoming deadlines" (`gd5-bob-my-work-baseline-no-court-date.png`, `gd5-bob-my-work-after-court-date.png`). The Diary week of 2–8 Nov shows the court date on Thu 5 Nov, credited to Bob Admin (`gd5-bob-diary-week-shows-court-date.png`).
The Keycloak tenant has 0 `court_dates` rows. The "06 Dec CMC" in the gap came from the Phase 87 capstone tenant, which no longer exists.

## G-D12 — phantom pending invitations: NOT reproducible on :3001; REPRODUCED on Keycloak :3000
- :3001 (mock auth): `listInvitations()` returns `[]` without calling the backend, and the mock form hard-codes 0 pending. The page shows "3 members" and an empty Pending tab (`gd12-team-pending-tab-e2e.png`). e2e `pending_invitations` has 0 rows.
- :3000 (Keycloak, Thandi, view only): the header says "3 members", but the line under the invite form says **"5 members (2 pending)"**. The Pending tab lists Carol and Bob with Revoke buttons (`gd12-kc-team-seat-line-5-members-2-pending.png`, `gd12-kc-team-pending-tab.png`).
- Cause: `listInvitationsBff` calls `/api/invitations` with no `?status=PENDING`, and `invite-member-form.tsx:166` adds every invitation to the member count.

## OQ #2 — stored invitation status: ACCEPTED → 627G is N/A
`tenant_5039f2d497cf.pending_invitations` (read-only SELECT):
- `bob@mathebula-test.local | ACCEPTED | 2026-10-24 07:14:47.00444+00`
- `carol@mathebula-test.local | ACCEPTED | 2026-10-24 07:15:01.606816+00`

`members` holds thandi@, bob@ and carol@mathebula-test.local. `tenant_56399d06a773` (`org_phase86_squash_smoke`) has no members.

## G-C1 — no Government party type: REPRODUCED
The Add Party types offered are NATURAL_PERSON, COMPANY, TRUST, CLOSE_CORPORATION, PARTNERSHIP and OTHER, with no Government option (`gc1-add-party-types.png`). The reproducer cancelled without saving. Two existing RAF rows on e2e are stored as NATURAL_PERSON.
Backend: `party_type varchar(20) NOT NULL`, no CHECK constraint, `@NotBlank String`. `GOVERNMENT` fits; `GOVERNMENT_ORGAN_OF_STATE` (25 characters) would not.

## Data created during reproduction
- On e2e: the matter and court date above, created through the UI as Bob.
- On Keycloak: nothing. Only SELECTs and a page view.
