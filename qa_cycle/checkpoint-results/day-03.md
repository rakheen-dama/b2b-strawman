# Day 3 — Create RAF matter, send FICA info request — Cycle 2026-06-13

**Executed**: 2026-06-13 (branch `bugfix_cycle_2026-06-13`)
**Actor**: Bob Ndlovu (Admin) — still logged in from Day 2 (persistent Playwright browser context; sidebar identity "Bob Ndlovu / bob@mathebula-test.local" confirmed on first page load)
**Driver**: QA agent via Playwright MCP against Keycloak dev stack (frontend :3000, gateway :8443, KC :8180, Mailpit :8025)
**Result**: 14/14 checkpoints PASS + 4/4 day-summary checkpoints PASS. **Zero new gaps filed.**

## Checkpoints

| Checkpoint | Result | Evidence | Gap |
|---|---|---|---|
| 3.1 Sipho's client detail → + New Matter | PASS | Client detail `/customers/2211a80a-…` → Work group tab → Matters sub-tab (`?tab=projects`) → "New Matter" link → `/org/mathebula-partners/projects?new=1&customerId=2211a80a-…`. "New from Template — Select Template" dialog opened automatically. | — |
| 3.2 Legal-specific matter-type template selector | PASS | 6 legal-za templates listed: Collections (Debt Recovery) 9 tasks, Commercial (Corporate & Contract) 9 tasks, Deceased Estate Administration 9 tasks, Litigation (Personal Injury / General) 9 tasks, **Litigation (Road Accident Fund -- RAF) 9 tasks**, Property Transfer (Conveyancing) 12 tasks. Phase 64/66 templates. | — |
| 3.3 Fill matter form | PASS | Selected RAF template → Configure step pre-filled Name="Sipho Dlamini - RAF Claim", Description=RAF Act 56 of 1996 workflow text, Client=Sipho Dlamini (auto-selected). Set: Name="Dlamini v Road Accident Fund", Reference Number="RAF-2026-001", Matter lead=Bob Ndlovu, Work Type=Litigation. Court is a custom field (Fields tab) — set post-creation: Court="Gauteng Division, Pretoria", Opposing Party="Road Accident Fund"; persisted across full page reload. Case Number left blank per scenario (populated later). | — |
| 3.4 Submit → matter created, redirected | PASS | "Create Matter" → redirected to `/org/mathebula-partners/projects/08ad56c4-ff5e-49c2-a034-cb5fa04b462c`. | — |
| 3.5 Header card + grouped tab bar (7 groups) | PASS | DOM check: `data-testid="matter-header-card"` ✓, `data-testid="grouped-tab-bar"` ✓ with exactly 7 group testids (details, overview, work, finance, client, schedule, activity). Header: "Dlamini v Road Accident Fund", badges Active + Litigation, code RAF-2026-001, client link Sipho Dlamini, lifecycle buttons (Close/Complete Matter). Sub-tabs enumerated per group: **Details** = Details, Fields; **Work** = Tasks, Documents, Generated Docs, Staffing (`tab-item-staffing` replaces old flat Members); **Finance** = Time, Disbursements, Fee Estimate (`tab-item-budget`), Rates, Financials, Statements, Trust; **Client** = Clients, Requests, Client Comments, Adverse Parties; **Activity** = Activity, Audit Trail (Team Oversight enabled). Overview + Schedule standalone. Note: no generic "Expenses" sub-tab — intentional legal-za dedupe, `frontend/components/projects/project-tabs.tsx:163-167` ("Expenses" → "Disbursements" terminology collision; generic Expenses tab suppressed when Disbursements module shown). Not a gap. | — |
| 3.6 Promoted fields inline, not duplicated | PASS | `matter_type` promoted to header badge "Litigation" (inline). Court + Case Number live ONLY on Details > Fields under "SA Legal — Matter Details" group (7 fields: Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value). No duplication on Overview tab — same rendering as the prior cycle's accepted PASS interpretation. | — |
| 3.7 Client group → Requests sub-tab → + New Request | PASS | `tab-group-client` → `tab-item-requests` (`?tab=requests`) → "No information requests yet" empty state → "New Request" → "Create Information Request" dialog opened. | — |
| 3.8 Select template: FICA Onboarding Pack | PASS | Template dropdown: 8 options (Ad-hoc, Tax Return Supporting Docs 5, Monthly Bookkeeping 4, Liquidation and Distribution Account Pack 5, **FICA Onboarding Pack 3**, Conveyancing Intake (SA) 7, Company Registration 4, Annual Audit Document Pack 5). Selected FICA Onboarding Pack → "Template items: 3". | — |
| 3.9 Addressee auto-populated | PASS | Portal Contact pre-populated "Sipho Dlamini (sipho.portal@example.com)" from client record — no manual selection. | — |
| 3.10 Items pre-filled from template | PASS | Request detail (post-send) renders exactly 3 File Upload items with template descriptions: **ID copy** (certified SA ID/passport bio page), **Proof of residence (≤ 3 months)**, **Bank statement (≤ 3 months)** — all Pending / "Waiting for client". | — |
| 3.11 Due date Day 10 (7 days from today) | PASS | Due Date set 2026-06-20 (today 2026-06-13 + 7d); request detail shows "Due 20 Jun 2026". | — |
| 3.12 Send → status Sent | PASS | "Send Now" → Requests table row: **REQ-0001** / Dlamini v Road Accident Fund / Sipho Dlamini / **Sent** / 0/3 accepted / 13 Jun 2026. | — |
| 3.13 Portal contact created/linked | PASS | Client > Clients sub-tab (`?tab=customers`): 1 row — Sipho Dlamini, sipho.portal@example.com, **ACTIVE**, linked to matter. | — |
| 3.14 Mailpit magic-link email | PASS | Mailpit msg `hhoVkD8UxgQaLsn2dG2oNu`: From noreply@kazi.app → sipho.portal@example.com, Subject "Information request REQ-0001 from Mathebula & Partners", body "…sent you an information request (REQ-0001) with 3 item(s)…", magic link `http://localhost:3002/auth/exchange?token=nsJKu6Q0-…&orgId=mathebula-partners`. Firm branding present (Mathebula & Partners header + S3 logo URL). Same subject format accepted as PASS in prior cycle. | — |

## Day 3 summary checkpoints

| Checkpoint | Result | Evidence |
|---|---|---|
| Matter created with reference RAF-YYYY-NNN | PASS | RAF-2026-001 rendered as header code chip; matter ID `08ad56c4-ff5e-49c2-a034-cb5fa04b462c`. |
| Matter-type template instantiated — phase sections present, LSSA tariff linked | PASS | Tasks tab shows **9 tasks** instantiated from RAF template (Initial RAF claim assessment & instructions → File RAF1 claim form → Statutory medical reports → Insurer correspondence -- RAF tariff schedule → Settlement negotiation with RAF → Court action (Section 24 RAF Act) → Trial/hearing attendance → Settlement/judgment payout & costs → Prescription monitoring 3yr/5yr); 7 auto-assigned to matter lead Bob Ndlovu. Work Type=Litigation. LSSA tariff linkage is firm-wide at the tariff module level (Day 1 verified 19 items), not per-matter — consistent with prior-cycle reading and OBS-2101 exemption. |
| Promoted matter fields render inline, not duplicated | PASS | matter_type → header badge; SA Legal fields only on Fields sub-tab; Overview has no field duplication. |
| FICA info request dispatched, magic-link email sent | PASS | REQ-0001 Sent 0/3; matter Overview FICA card transitioned **Not Started → In Progress** ("Awaiting client response and firm-side review") with "View request" link emitting canonical `/org/mathebula-partners/information-requests/de3d6962-…` (OBS-501 verification ✓); Mailpit email delivered with portal auth-exchange link. |

## Console / log health

- Frontend console (Day 3 walk): only **OBS-201 class** `GET /api/assistant/invocations` 404s (customer + project contexts) — WONT_FIX-EXEMPT carry-over, not re-filed. Zero other JS/hydration/render errors.
- Two `GET /api/customers/{id}` / `GET /api/orgs/{slug}/customers/{id}` 404s visible in the session-wide console buffer pre-date this walk (console log `23-24-58Z`, before Day 3 start `23-28-42Z`) — they are the Day 2 QA agent's documented API-route probes, not product errors.
- `.svc/logs/backend.log`: 0 ERROR lines. `.svc/logs/frontend.log` / `portal.log`: no errors/exceptions.

## Gaps filed

None. (Missing generic "Expenses" Finance sub-tab investigated and proven intentional legal-za design — `frontend/components/projects/project-tabs.tsx:163-167`.)

## Screenshots

- `qa_cycle/checkpoint-results/day-03-matter-created.png` — matter Overview: header card, RAF-2026-001, FICA In Progress card
- `qa_cycle/checkpoint-results/day-03-fica-request-sent.png` — REQ-0001 detail: Sent, due 20 Jun 2026, 3 pending FICA items

## Entity IDs (for downstream days)

- **Matter ID**: `08ad56c4-ff5e-49c2-a034-cb5fa04b462c` — "Dlamini v Road Accident Fund", ref RAF-2026-001 (`/org/mathebula-partners/projects/08ad56c4-ff5e-49c2-a034-cb5fa04b462c`)
- **Info request ID**: `de3d6962-6018-43bf-852d-d366d1a4d626` — REQ-0001, FICA Onboarding Pack, Sent, due 2026-06-20 (`/org/mathebula-partners/information-requests/de3d6962-6018-43bf-852d-d366d1a4d626`)
- **Magic-link email**: Mailpit ID `hhoVkD8UxgQaLsn2dG2oNu`; token link `http://localhost:3002/auth/exchange?token=nsJKu6Q0-pI87cRzaViMkltCnxF1hrwoGaPQzusxvFM&orgId=mathebula-partners` (Day 4 entry point)
- **Client**: Sipho Dlamini `2211a80a-5523-4a6d-8f96-0d638dff88f6` (unchanged from Day 2)
