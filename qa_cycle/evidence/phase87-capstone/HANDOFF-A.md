# HANDOFF-A → Walker B (617C capstone, legal-za 90-day, Keycloak)

**Last completed step: 0.68** and the Day 0 checkpoints CP0.1–CP0.9 (`day-00.md`). Walker B starts at **Day 1, step 1.1** (doc line 211).
Walker A totals: Session 0 2/2 PASS; Day 0 81 rows = 78 PASS · 1 FAIL (0.14) · 2 DEFERRED (0.50, CP0.5).

## Stack state (left RUNNING)

- Services started by `bash /Users/rakheendama/Projects/2026/b2b-strawman/compose/scripts/svc.sh start all` from the **main checkout** (HEAD `dac048f12`, backend freshly `clean compile`d). Check health with `bash …/b2b-strawman/compose/scripts/svc.sh status`. Logs: `…/b2b-strawman/.svc/logs/{backend,gateway,frontend,portal}.log`.
- Docker infra: b2b-postgres / b2b-keycloak / b2b-mailpit / b2b-localstack (dev). The **e2e stack is DOWN** (`e2e-down.sh` ran). Part B must bring it back.
- `/tmp/.e2e-keycloak-mode` **exists** (Playwright :3000 hook). Leave it until Part C ends.
- Main checkout: the portal dev server rewrote tracked `portal/next-env.d.ts` (`.next/types` → `.next/dev/types`). This is a dev artefact. Do not commit it. Restore it after services stop: `git -C /Users/rakheendama/Projects/2026/b2b-strawman checkout portal/next-env.d.ts`.
- Playwright MCP screenshots must be written under `/Users/rakheendama/Projects/2026/b2b-strawman/.playwright-mcp/` (the tool rejects `/tmp`; the allowed roots are the main checkout). `cp` them into the worktree `screens/`. The test logo PNG used in 0.40 is `…/b2b-strawman/.playwright-mcp/mathebula-logo.png`.
- Browser state at handoff: logged in as **Thandi** on `/org/mathebula-partners/diary`. Clear cookies before switching to Bob.

## Org / tenant

| Item | Value |
|---|---|
| Org name | Mathebula & Partners |
| Org slug (from post-login URL) | **`mathebula-partners`** → app base `http://localhost:3000/org/mathebula-partners/` |
| KC org id | `2c4034d3-f76d-4323-a172-7eabd018657d` (realm `docteams`) |
| Tenant schema (read-only SELECTs) | `tenant_5039f2d497cf` (db `docteams`, container `b2b-postgres`) |
| Vertical profile | `legal-za`; `terminology_namespace = en-ZA-legal`; `enabled_modules` = court_calendar, conflict_check, lssa_tariff, trust_accounting, disbursements, matter_closure, deadlines, information_requests, bulk_billing |
| Subscription | TRIALING (UI badge "Trial"), MANUAL, trial ends 2026-11-05 |

## Users (Keycloak realm `docteams`)

| Actor | Email | Password | Tenant role | Member id |
|---|---|---|---|---|
| Thandi Mathebula | thandi@mathebula-test.local | `SecureP@ss1` | Owner | `26ee05c9-159c-4fb8-8939-f53a72bb514c` |
| Bob Ndlovu | bob@mathebula-test.local | `SecureP@ss2` | Admin | `cf661689-f653-4dfb-8953-4a5203ef3fc6` |
| Carol Mokoena | carol@mathebula-test.local | `SecureP@ss3` | Member | `e627a33b-0af0-481f-bed1-a3776419c105` |
| Platform admin | padmin@docteams.local | `password` | — | — |

All three firm users logged in with their passwords through the browser (Bob and Carol re-logged in at ~15:18Z). **Never run `keycloak-bootstrap.sh`**: it resets member passwords to `password`.

## Settings configured on Day 0 (inherit; re-observe before relying on them)

- Currency ZAR. Brand colour `#1B3A4B`. Logo uploaded (`org/tenant_5039f2d497cf/branding/logo.png`).
- Billing rates (ZAR, effective 2026-10-22, ongoing): Thandi R2,500 · Bob R1,200 · Carol R550. Cost rates: Thandi R1,000 · Bob R500 · Carol R200.
- Tax: "VAT — Standard" 15% default, pre-seeded (+ Zero-rated, Exempt).
- Field packs: `legal-za-customer` (group "SA Legal — Client Details"), `legal-za-project` ("SA Legal — Matter Details"), `conveyancing-za-project`, FICA individual/company, common-project/task.
- Matter templates (6): Collections (Debt Recovery), Commercial (Corporate & Contract), Deceased Estate Administration, Litigation (Personal Injury / General), Litigation (Road Accident Fund -- RAF), Property Transfer (Conveyancing, 12 tasks).
- Automations: 10 pack rules listed.
- Trust account **"Mathebula & Partners Trust Account"**, GENERAL, Standard Bank, branch 051001, acc no 1234567890 (test values), primary, single approval, id `b53ed14e-1944-4941-bb63-dfa18d8d138a`. LPFF rate 6.50% (share 0.00%) effective 2026-10-22, set under Trust › Interest. Balance R 0,00.
- **No clients or matters exist yet** (Day 0 created none).

## Mailpit (dev :8025) — messages so far (total 4; do NOT delete)

| ID | To | Subject |
|---|---|---|
| `NPjWXS7g8pN7FiNPoaZyg2` | thandi@ | Your Kazi verification code (OTP 432022) |
| `fLxgHjgvvZjaGJhSziGrgi` | thandi@ | Invitation to join the Mathebula & Partners organization |
| `dUwRwPRHSWKzCANGeHboDi` | bob@ | Invitation to join the Mathebula & Partners organization |
| `MFXTKJav3csNveZq3cpyGw` | carol@ | Invitation to join the Mathebula & Partners organization |

## Gotchas hit (read before starting)

1. **Keycloak login is two-step** now that the org exists: `#username` → submit → `#password` → submit. The very first padmin login was single-step. Script it as `fill #username; click submit; waitForSelector #password; fill; click`.
2. **First-compile hydration lag (dev mode):** the first client click on a freshly compiled route can do nothing while Next.js is "Fast Refresh rebuilding". If a button does nothing, reload the page and click again before you call it a FAIL (0.15 needed this).
3. **Snapshot targets:** `[role=tabpanel]` matches hidden Radix panels. Use `[role=tabpanel][data-state=active]` or `role=tabpanel[name="…"]`. The nav landmark is `nav[aria-label="Main navigation"]`.
4. **Role-gated nav:** Carol (Member) sees **no Clients zone** and no AI reviews (zones: Today · Matters · Fees · Trust · Firm). Not judged by Walker A. If Carol needs client pages on Days 6–7, observe and record.
5. Team invites use an inline form ("Invite a team member": Email + Role + **Send Invite**), not an "Invite Member" button.
6. New Client dialog is two steps. Step 1 holds the promoted native fields (Type, Address, Contact, Business Details, Tax Number). Step 2 "Additional Information" holds the SA Legal — Client Details pack fields, with a "Skip for now" option. `getByRole('textbox',{name:'Name'})` is ambiguous with "Contact Name"; use `exact:true`.
7. New Matter "Work Type" is a free-text input with datalist suggestions (LITIGATION / CONVEYANCING / ESTATES / COLLECTIONS / COMMERCIAL).
8. Cookies persist across MCP sessions. Clear them at every actor switch: `async (page) => { await page.context().clearCookies(); }`.

## Carried to Walker B

- **0.50 DEFERRED → Walker B:** on Sipho Dlamini's client detail page (after 1.x), open `tab-group-details` → `tab-item-fields` (Custom fields). Verify none of the 7 promoted slugs (client_type, physical_address, registration_number, tax_number, phone, primary_contact_name, primary_contact_email) appear again in the CustomFieldSection. Record it in `days-01-07.md` and say it closes 0.50 / CP0.5.
- Gaps so far: G-A1 (no access-request detail view) · G-A2 (Home breadcrumb says "Dashboard") · G-A3 (brand colour makes sidebar org name illegible) · G-A4 (invoice/customer/project copy in Tax/Features/Automations settings) · G-A5 ("Project" copy in field packs) · G-A6 (New Matter "Conflict Check — Coming Soon") · G-A7 (seeded templates labelled "Manual"). Append yours to `gaps.md` as G-B1….
