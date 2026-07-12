# Day 3 — Create RAF matter, send FICA info request `[FIRM]` — 2026-07-06

Actor: Bob Ndlovu (same session as Day 2).

| Checkpoint | Result | Evidence |
|---|---|---|
| 3.1 client detail → New Matter | PASS | Client Work > Matters tab → "New Matter" → template picker opens at `/projects?new=1&customerId=f6f8050d…` |
| 3.2 legal template selector | PASS | 6 legal-za templates: Collections, Commercial, Deceased Estate Administration, Litigation (Personal Injury), **Litigation (Road Accident Fund -- RAF)**, Property Transfer (Conveyancing) |
| 3.3 fill matter | PASS | Template RAF selected → Configure step: name "Dlamini v Road Accident Fund", ref RAF-2026-001, client Sipho (pre-bound), lead Bob Ndlovu, work type Litigation. Court "Gauteng Division, Pretoria" set post-create on Details > Fields (dialog has no court field — court is an SA-legal custom field; saved "Saved successfully", value verified). Case number left blank per scenario |
| 3.4 submit → matter detail | PASS | `/projects/272be4f8-255b-40d5-8129-225cf79c08a9` |
| 3.5 header card + 7 grouped tabs | PASS | `matter-header-card`: name / Active / Litigation / RAF-2026-001 / Sipho Dlamini; `header-lifecycle-actions`: Close Matter + Complete Matter; `grouped-tab-bar`: Details, Overview, Work, Finance, Client, Schedule, Activity (all 7 tab-group-* testids). Work sub-tabs: tasks, documents, correspondence, generated, staffing |
| 3.6 promoted fields placement | PASS (with note) | SA-legal matter fields (Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value) render ONCE in Details > Fields under group "SA Legal — Matter Details" — no duplication. They are NOT on the Overview tab; per the scenario's own Phase 73 selector-reference header ("custom fields on a Fields tab"), this is the current intended layout. Overview hosts KPI/FICA/deadlines |
| 3.6a Correspondence empty state | PASS | Work > Correspondence: "No correspondence yet — Inbound correspondence filed against this matter will appear here. Email is filed via your firm's own Claude using the Kazi MCP tools — Kazi never reads your mailbox." |
| 3.7 Client > Requests → New Request | PASS | Dialog "Create Information Request" |
| 3.8 template FICA Onboarding Pack | PASS | Selected from template list (8 templates incl. FICA Onboarding Pack (3 items)) |
| 3.9 addressee auto-populated | PASS | Portal Contact combobox pre-set "Sipho Dlamini (sipho.portal@example.com)" |
| 3.10 items pre-filled | PASS | Request detail `/information-requests/ed20f923-…`: 3 items — "ID copy", "Proof of residence (≤ 3 months)", "Bank statement (≤ 3 months)", each File Upload / Pending with full FICA descriptions |
| 3.11 due date Day 10 | PASS | Due 13 Jul 2026 (today +7) |
| 3.12 Send → status Sent | PASS | REQ-0001 row: Sent, 0/3 accepted, sent 6 Jul 2026 |
| 3.13 portal contact linked | PASS | Request detail Contact block: Sipho Dlamini / sipho.portal@example.com |
| 3.14 magic-link email | PASS (with note) | Mailpit `Y962HNm9Fvt9gdkcfyTePQ` "Information request REQ-0001 from Mathebula & Partners" with link `http://localhost:3002/auth/exchange?token=…&orgId=mathebula-partners`. Note: subject wording contains none of the scenario's suggested phrases ("sign in"/"action required"/"your portal") but is the correct request magic-link email — functional flow intact, not a gap |

Day 3 exit checkpoints: matter ref RAF-2026-001 ✓; template instantiated (9 tasks per template card; RAF description with prescription monitoring) ✓; promoted fields single-location ✓ (Fields tab per Phase 73 layout); FICA request dispatched + magic link sent ✓.

Matter ID for later days: `272be4f8-255b-40d5-8129-225cf79c08a9`. Customer ID: `f6f8050d-34fd-47c0-8894-0c5617dc8251`. REQ-0001 ID: `ed20f923-a847-409c-a6c4-e9c4a69d7b6d`.
