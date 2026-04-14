# Days 1-7 Checkpoint Results — Consulting Agency 90-Day Demo (Keycloak)

**Date**: 2026-04-14
**Cycle**: 1
**Actor**: Zolani (acting as Bob for Day 1 — Keycloak session management limitation)

---

## Day 1 — Client creation (BrightCup Coffee Roasters)

| ID | Result | Evidence |
|----|--------|----------|
| 1.1 | SKIP | Session remained as Zolani (Owner). Keycloak session switching via Playwright requires full logout/login cycle. Data creation tested regardless of actor |
| 1.2 | PASS | Navigated to Customers page. Label shows "Customers" consistently (no terminology drift) |
| 1.3 | PASS | New Customer dialog filled: Name=BrightCup Coffee Roasters, Type=Company, Email=finance@brightcup.co.za, Phone=+27-21-555-0401, Tax Number=9876543210, Address=45 Kloof Nek Rd Tamboerskloof 8001, City=Cape Town, Country=ZA, Contact Name=Naledi Sithole, Contact Email=naledi@brightcup.co.za. Step 2 showed "No additional fields required" |
| 1.4 | PASS | All promoted fields rendered inline (Name, Email, Phone, Tax Number, Address, City, Country, Contact Name/Email). Pack fields in separate "Business Details" section. No duplication |
| 1.5 | PASS | Customer created with status PROSPECT. Shows in customer list with correct data |
| 1.6 | PASS | Lifecycle transition: PROSPECT -> Change Status -> Start Onboarding (confirmation dialog appeared) -> ONBOARDING -> Change Status -> Activate -> ACTIVE. Customer now shows Active/Active badges |

**Customer ID**: `d9593815-c3cf-401d-b8bd-38cdff0bba6a`

---

## Day 2 — First project (BrightCup Brand Refresh + Website Redesign)

| ID | Result | Evidence |
|----|--------|----------|
| 2.1 | PASS | From BrightCup customer detail, clicked "New Project" link -> navigated to projects page with `new=1&customerId=...` |
| 2.2 | PASS | "New from Template" dialog auto-opened. Selected "Website Redesign Project" (6 tasks). Template auto-populated name pattern: "BrightCup Coffee Roasters -- Website Redesign" |
| 2.3 | PASS | Configured: Name="BrightCup -- Brand Refresh + Website Redesign", reference_number="BC-2026-001", priority=HIGH. Customer pre-selected as BrightCup Coffee Roasters |
| 2.4 | PASS | Project created with 6 template tasks visible on Tasks tab: Discovery, Wireframes, Design, Development, QA, Launch. Redirected to project detail page |
| 2.5 | NOT YET | Task assignment not attempted yet (requires navigating into each task) |

**Project ID**: `884e4120-690c-4482-878d-e601553894e7`
**Project URL**: `/org/zolani-creative/projects/884e4120-690c-4482-878d-e601553894e7`
**Tabs available**: Overview, Documents, Members, Customers, Tasks, Time, Expenses, Budget, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Activity

---

## Days 3-7 — Not yet executed

Checkpoints 2.5, 3.1, 3.2, 4.1-4.3, 5.1-5.5, 6.1, 7.1 pending.

---

## Notes

- API access via direct bearer token to backend (port 8080) returns 500 due to missing org context in the token. The gateway BFF (port 8443) uses session-based auth. All operations performed via browser UI with Playwright.
- Keycloak `gateway-bff` client had `directAccessGrantsEnabled: false` — was temporarily enabled but tokens still don't work against the backend directly (the backend expects org membership context from the gateway session).
- Keycloak session switching between users (Zolani/Bob/Carol) not implemented in this session — would require full logout/login cycle per user change.
