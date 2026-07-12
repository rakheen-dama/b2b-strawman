# Day 3 — Create RAF matter, send FICA info request `[FIRM]` — Cycle 2026-07-12

**Actor**: Bob Ndlovu (same session as Day 2).

| Checkpoint | Result | Evidence |
|---|---|---|
| 3.1 client detail → New Matter | PASS | Client Work > Matters tab (`?tab=projects`) → "New Matter" link → template picker at `/projects?new=1&customerId=d0c7daf5…` |
| 3.2 legal template selector | PASS | 6 legal-za templates: Collections (Debt Recovery), Commercial, Deceased Estate Administration, Litigation (Personal Injury / General), **Litigation (Road Accident Fund -- RAF)** (9 tasks), Property Transfer (Conveyancing) |
| 3.3 fill matter | PASS | RAF template → Configure: name "Dlamini v Road Accident Fund", ref RAF-2026-001, client Sipho (pre-bound from customerId), lead Bob Ndlovu, work type Litigation. Court "Gauteng Division, Pretoria" set post-create on Details > Fields ("Saved successfully", value persists on reload) — dialog has no court field, court is an SA-legal custom field (same as prior cycle). Case number left blank per scenario |
| 3.4 submit → matter detail | PASS | `/projects/66451e87-4723-49c4-b363-e696b68ff6b0` |
| 3.5 header card + 7 grouped tabs | PASS | `matter-header-card`: "Dlamini v Road Accident Fund / Active / Litigation / RAF-2026-001 / Sipho Dlamini"; `header-lifecycle-actions`: Close Matter + Complete Matter; `overflow-actions-trigger` present; all 7 `tab-group-*` testids (details, overview, work, finance, client, schedule, activity) |
| 3.6 promoted fields placement | PASS (with note) | SA-legal fields (Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value) render once in Details > Fields group "SA Legal — Matter Details"; NOT on Overview (Overview hosts KPI/FICA/deadlines) — intended Phase 73 layout, matches prior cycle |
| 3.6a Correspondence empty state | PASS | Work > Correspondence: "No correspondence yet — Inbound correspondence filed against this matter will appear here. Email is filed via your firm's own Claude using the Kazi MCP tools — Kazi never reads your mailbox." |
| 3.7 Client > Requests → New Request | PASS | Dialog "Create Information Request" |
| 3.8 template FICA Onboarding Pack | PASS | Selected from combobox (8 templates); dialog shows "Template items: 3" |
| 3.9 addressee auto-populated | PASS | Portal Contact combobox pre-set "Sipho Dlamini (sipho.portal@example.com)" |
| 3.10 items pre-filled | PASS | Request detail `/information-requests/c0f67daa-9ceb-4280-a31b-53e2434adcee`: 3 items — ID copy / Proof of residence (≤ 3 months) / Bank statement (≤ 3 months), each File Upload + Pending with full FICA descriptions |
| 3.11 due date Day 10 | PASS | Due 19 Jul 2026 (today +7) |
| 3.12 Send → status Sent | PASS | REQ-0001 row: Sent, 0/3 accepted, sent 12 Jul 2026 |
| 3.13 portal contact linked | PASS | Request detail Contact block: Sipho Dlamini / sipho.portal@example.com |
| 3.14 magic-link email | PASS (with note) | Mailpit `mbGJM7DAwdk3GrYs6A4SDZ` "Information request REQ-0001 from Mathebula & Partners" with portal link `http://localhost:3002/auth/exchange?token=…&orgId=mathebula-partners`. Subject wording lacks the scenario's suggested phrases ("sign in"/"action required") — same non-gap note as prior cycle |

## Day 3 exit checkpoints

- Matter created with reference RAF-2026-001: PASS
- Matter-type template instantiated (9-task RAF template): PASS
- Promoted matter fields render once (Fields tab, Phase 73 layout): PASS
- FICA info request dispatched, magic-link email sent: PASS

## Gaps

- None new.

## IDs for later days

- Matter: `66451e87-4723-49c4-b363-e696b68ff6b0`
- Customer (Sipho): `d0c7daf5-7085-4560-afb9-e9e937db5abc`
- REQ-0001: `c0f67daa-9ceb-4280-a31b-53e2434adcee`
- Deal DEAL-0001: `3aad1c89-b8b8-4d27-a94d-687be0682180` (draggable id)
