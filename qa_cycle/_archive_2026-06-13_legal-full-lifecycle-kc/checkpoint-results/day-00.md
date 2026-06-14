# Day 0 — Firm org onboarding (Keycloak flow) — Cycle 2026-06-13

**Executed**: 2026-06-13 (clean slate, branch `bugfix_cycle_2026-06-13`)
**Driver**: QA agent via Playwright MCP against Keycloak dev stack (frontend :3000, gateway :8443, KC :8180, Mailpit :8025)
**Result**: 32/32 checkpoints PASS + 4/4 day-summary checkpoints PASS. **Zero gaps filed.**

## Phase A — Access request & OTP

| Checkpoint | Result | Evidence | Gap |
|---|---|---|---|
| 0.1 Landing page loads, zero console errors | PASS | `http://localhost:3000/` → "Kazi — Practice management, built for Africa". Console: only React DevTools info + HMR logs. | — |
| 0.2 Get Started → `/request-access` | PASS | Nav "Get Started" link routed to `/request-access`. | — |
| 0.3 Form fields visible | PASS | Work Email, Full Name, Organisation Name, Country, Industry all present. | — |
| 0.4 Fill & submit | PASS | thandi@mathebula-test.local / Thandi Mathebula / Mathebula & Partners / South Africa / Legal Services submitted. | — |
| 0.5 Transitions to OTP step | PASS | Same page → "Check Your Email" step with Verification Code input + 10:00 expiry timer. | — |
| 0.6 Mailpit OTP email | PASS | Mailpit msg `7BSLq5Z7bHwksgRx2sU6JN`, subject "Your Kazi verification code". | — |
| 0.7 Enter OTP → Verify | PASS | OTP 660624 entered, Verify clicked. | — |
| 0.8 Success card | PASS | "Request Submitted — Your access request has been submitted for review." Screenshot: `day-00-phaseA-request-submitted.png`. | — |

## Phase B — Platform admin approval

| Checkpoint | Result | Evidence | Gap |
|---|---|---|---|
| 0.9 Fresh context → /dashboard → Keycloak login | PASS | Cookies cleared; :3000/dashboard redirected via gateway :8443 to KC :8180 `realms/docteams` login. (Only console error: KC's own favicon.ico 404 on :8180 — Keycloak dev artifact, not frontend.) | — |
| 0.10 padmin login → platform admin home | PASS | padmin@docteams.local landed on `/platform-admin/access-requests`. | — |
| 0.11 Navigate to access-requests | PASS | Already there; nav shows Access Requests / Billing / Demo. | — |
| 0.12 Mathebula & Partners in Pending | PASS | Row: Industry=Legal Services, Country=South Africa, Status=PENDING. | — |
| 0.13 All fields inline on row | PASS | Org Name, Email, Name, Country, Industry, Submitted ("1 minute ago"), Status, Actions all rendered. | — |
| 0.14 Approve → AlertDialog → Confirm | PASS | AlertDialog "Approve Access Request" (text: will create KC org, provision tenant schema, send invitation) → confirmed. | — |
| 0.15 Status = Approved, no error banner | PASS | Approved tab shows row with APPROVED badge; Pending tab empty; no provisioning error banner. | — |
| 0.16 Vertical profile = legal-za | PASS | Read-only DB inspection (same method as 2026-05-30 cycle): `tenant_5039f2d497cf.org_settings` → `vertical_profile=legal-za`, `terminology_namespace=en-ZA-legal`, `default_currency=ZAR`, enabled_modules=[court_calendar, conflict_check, lssa_tariff, trust_accounting, disbursements, matter_closure, deadlines, information_requests, bulk_billing]; `public.organizations.provisioning_status=COMPLETED`. **Script note**: the scenario's curl hint (`GET :8443/api/orgs/mathebula-partners/profile` with bearer padmin token) returns 401 via gateway (BFF session auth, not bearer) and the path 404s on the backend — verification hint is stale, not a product defect. | — |
| 0.17 KC invitation email to Thandi | PASS | Mailpit msg `jD9B9BC5TQH2inpiiby8pV`, "Invitation to join the Mathebula & Partners organization". | — |

## Phase C — Owner Keycloak registration

| Checkpoint | Result | Evidence | Gap |
|---|---|---|---|
| 0.18 Open invite link (fresh context) | PASS | Cookies cleared; accept-invite link → KC registrations endpoint. | — |
| 0.19 Registration page, org pre-bound | PASS | "Create an account to join the Mathebula & Partners organization"; email pre-filled thandi@mathebula-test.local. | — |
| 0.20 Fill registration | PASS | Thandi / Mathebula / SecureP@ss1 ×2. | — |
| 0.21 Submit → app dashboard | PASS | Redirected to `http://localhost:3000/org/mathebula-partners/dashboard`. | — |
| 0.22 Sidebar: org + user name | PASS | "Mathebula & Partners" + "Thandi Mathebula" (thandi@mathebula-test.local) in sidebar. | — |
| 0.23 Legal terminology | PASS | Sidebar: **Matters**, **Clients**, **Fee Notes** — no "Projects"/"Customers"/"Invoices" labels anywhere in nav. | — |
| 0.24 Legal module nav | PASS | Matters, Trust Accounting (+ Transactions, Client Ledgers, Reconciliation, Interest, Investments, Trust Reports, Tariffs), Court Calendar, Conflict Check (+ Engagement Letters, Mandates, Compliance, Adverse Parties) all visible. | — |
| 0.25 Screenshot | PASS | `qa_cycle/checkpoint-results/day-00-firm-dashboard-legal.png`. | — |

## Phase D — Team invites

| Checkpoint | Result | Evidence | Gap |
|---|---|---|---|
| 0.26 Team via top-level nav | PASS | Sidebar Team group → Team → `/org/mathebula-partners/team`. | — |
| 0.27 Thandi = Owner, no upgrade gate | PASS | Members table: Thandi Mathebula / Owner. No tier/upgrade/billing upsell anywhere on invite flow. | — |
| 0.28 Invite Bob as Admin | PASS | "Invitation sent to bob@mathebula-test.local." | — |
| 0.29 Invite Carol as Member | PASS | "Invitation sent to carol@mathebula-test.local." | — |
| 0.30 Two KC invite emails | PASS | Mailpit: Bob `QUr8WhuBoxZveA9r2t9pfW`, Carol `4yHULVL9KRvEBBfXREnMqb`. | — |
| 0.31 Bob registers → dashboard → logout | PASS | Fresh context (cookies cleared) → KC registration (Bob/Ndlovu/SecureP@ss2) → `/org/mathebula-partners/dashboard` (sidebar "Bob Ndlovu") → Sign out → landing page. | — |
| 0.32 Carol registers → dashboard → logout | PASS | Fresh context → KC registration (Carol/Mokoena/SecureP@ss3) → `/org/mathebula-partners/dashboard` (sidebar "Carol Mokoena") → Sign out → landing page. | — |

## Day 0 summary checkpoints

| Checkpoint | Result | Evidence |
|---|---|---|
| Org created via real access-request → approval → KC registration | PASS | Full flow observed end-to-end in browser; no mock IDP used. |
| Three KC users exist for @mathebula-test.local | PASS | All three registered through KC realm `docteams` and authenticated; Thandi's Team page shows 3 members: Thandi=Owner, Bob=Admin, Carol=Member. Screenshot: `day-00-team-three-members.png`. |
| Vertical profile = legal-za, terminology + nav legal | PASS | DB: vertical_profile=legal-za, en-ZA-legal, ZAR; sidebar Matters/Clients/Fee Notes/Trust Accounting/Court Calendar/Conflict Check/Tariffs. |
| No tier / upgrade / billing upsell | PASS | None observed on any page visited (landing, request-access, platform-admin, dashboard, team). |

## Console / log health

- Frontend console: clean across all pages — only React DevTools info, HMR/Fast Refresh logs, and the known Next.js `scroll-behavior: smooth` **warning** (classified INFO/not-an-error in 2026-05-30 cycle day-30 results; not re-filed).
- Keycloak :8180 favicon.ico 404 — KC dev server artifact, not the frontend.
- `.svc/logs/frontend.log`: no errors. `.svc/logs/backend.log`: no ERRORs; only benign WARNs (CglibAopProxy proxy notice, Flyway idempotent column-exists skips during tenant provisioning).

## Gaps filed

None.
