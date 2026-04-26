# QA Lifecycle: Legal-ZA Full Lifecycle — Firm + Portal Interleaved (Keycloak Mode)

**Vertical profile**: `legal-za`
**Story**: "Mathebula & Partners" — Johannesburg litigation firm running a 90-day Road Accident Fund (RAF) matter for Sipho Dlamini, with Sipho also active on the customer portal throughout
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025 / **portal 3002**)
**Master doc**: `qa/testplan/demo-readiness-keycloak-master.md`
**Driver**: `/qa-cycle-kc qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`

**Supersedes**: none — this is the first unified firm + client-POV legal lifecycle. Complements, does not replace:
- `qa/testplan/demos/legal-za-90day-keycloak.md` — firm-only legal (retained for firm-only demo runs)
- `qa/testplan/demos/portal-client-90day-keycloak.md` — cross-vertical portal (retained for multi-profile portal gating)

---

## Scope note — interleaved POV

This script runs **one continuous 90-day timeline** on a **single tenant** (`mathebula-partners`, `legal-za` profile). Each day is tagged `[FIRM]`, `[PORTAL]`, or `[FIRM → PORTAL]` to indicate whose chair the QA driver sits in. Every POV switch requires an **explicit context swap** (clear cookies, change port, use the correct auth helper) — the driver MUST NOT reuse a firm user's session on the portal or vice-versa.

The story is deliberately realistic for an SA general-practice litigation firm: RAF claim for an individual client, fund into trust, LSSA tariff-based fee note, PayFast sandbox payment, matter closure → Statement of Account. A **second client (Moroka Family Trust)** is onboarded firm-side on Day 14 solely to drive an **isolation check** on the portal (Day 15) — Sipho's portal session must never surface Moroka's data.

---

## Driver compatibility — `/qa-cycle-kc` shims

`/qa-cycle-kc` is authored around the firm-side Keycloak OIDC flow. This script exercises **both** firm auth (Keycloak redirect) and portal auth (magic-link + portal JWT). The QA agent MUST apply the shims below when a day is tagged `[PORTAL]`:

### Firm-side (`[FIRM]`, default)
- Frontend: `http://localhost:3000`
- Gateway: `http://localhost:8443`
- Auth: Keycloak OIDC redirect via `/dashboard` (realm `docteams`)
- Fixtures: Keycloak users with `@mathebula-test.local` emails (created Day 0 via access-request approval flow)

### Portal-side (`[PORTAL]`)
- Portal: `http://localhost:3002` (NOT :3000 — portal contacts are not firm users, cannot authenticate at :3000)
- Auth flow: magic-link → JWT → cookie + localStorage, via `POST /portal/auth/request-link` + `POST /portal/auth/exchange`. Use the helpers at `portal/e2e/helpers/auth.ts` (`getPortalJwt(email)` → `loginAsPortalContact(page, jwt)`)
- **Never** fill a Keycloak form for any portal step — portal contacts are NOT in Keycloak
- Org slug override: **`PORTAL_ORG_SLUG=mathebula-partners`** (not the default `e2e-test-org`)
- Start: `bash compose/scripts/svc.sh start portal` (health-check `GET http://localhost:3002/`)

### Context-swap protocol at every POV switch
When transitioning from `[FIRM]` to `[PORTAL]` or back:
1. Close current browser context / clear all cookies for the domain you are leaving
2. Open a fresh browser context (incognito or Playwright `context.close()` + new context) — never reuse
3. Confirm the destination port is live (`curl -sS http://localhost:{port}/ -o /dev/null -w "%{http_code}\n"` returns 200)
4. If using Playwright, use the explicit helper for the destination (firm login page vs `loginAsPortalContact`)

---

## Actors

### Firm side (Keycloak users — created Day 0)
| Role | Name | Keycloak email | Password |
|---|---|---|---|
| Owner / Senior Partner | Thandi Mathebula | `thandi@mathebula-test.local` | `SecureP@ss1` |
| Admin / Associate | Bob Ndlovu | `bob@mathebula-test.local` | `SecureP@ss2` |
| Member / Candidate Attorney | Carol Mokoena | `carol@mathebula-test.local` | `SecureP@ss3` |
| Platform Admin | (pre-seeded) | `padmin@docteams.local` | `password` |

### Portal side (portal contact — created firm-side Day 3, first logs in Day 4)
| Role | Name | Portal email | Tenant | Profile |
|---|---|---|---|---|
| Client — RAF litigation (individual) | Sipho Dlamini | `sipho.portal@example.com` | `mathebula-partners` | `legal-za` |

### Firm-side only (isolation-target — never logs into portal)
| Role | Name | Purpose |
|---|---|---|
| Client — Deceased Estate (trust) | Moroka Family Trust (primary contact `moroka.portal@example.com`) | Onboarded Day 14 so Sipho's Day 15 portal session has another customer's data to fail to see |

---

## Clients & matters onboarded

| Client | Day created | Type | Matter | Role in the story |
|---|---|---|---|---|
| Sipho Dlamini | Day 2 | INDIVIDUAL | RAF-2026-001 — Road Accident Fund claim | Primary matter — exercised firm-side AND portal-side |
| Moroka Family Trust | Day 14 | TRUST | EST-2026-002 — Deceased Estate (Peter Moroka) | Isolation target — firm-side activity only, never visible to Sipho on portal |

---

## Demo wow moments (capture 📸 on clean pass)

1. **Day 2** — Conflict check CLEAR + KYC Verified, on Sipho's client record `[FIRM]`
2. **Day 8** — Proposal accept confirmation, client-POV on `/proposals/[id]` `[PORTAL]`
3. **Day 11** — Trust balance card with first deposit, `/trust` `[PORTAL]`
4. **Day 15** — **Isolation** — Sipho's `/home` showing only his matter; direct-URL probe to Moroka's matter denied `[PORTAL]`
5. **Day 30** — PayFast sandbox success + receipt on `/invoices/[id]` `[PORTAL]`
6. **Day 61** — Statement of Account PDF downloaded from `/projects/[matterId]` Documents tab `[PORTAL]`
7. **Day 88** — Matter activity feed (firm-side) alongside client's 90-day activity trail (portal-side) — side-by-side capture

---

## Session 0 — Prep & Reset

Follow `qa/testplan/demo-readiness-keycloak-master.md` → "Session 0 — Stack startup & teardown" for shared steps (M.1–M.9). In addition, for this unified lifecycle specifically:

- [ ] **0.A** Confirm firm stack is healthy: `bash compose/scripts/svc.sh status` shows backend (8080), gateway (8443), frontend (3000), keycloak (8180), mailpit (8025) all UP
- [ ] **0.B** Confirm **portal** is running on `:3002`: `curl -sS http://localhost:3002/ -o /dev/null -w "%{http_code}\n"` returns `200`. Start via `bash compose/scripts/svc.sh start portal` if not.
- [ ] **0.C** Confirm no tenant schema named `tenant_mathebula*` exists (drop if present):
  ```bash
  docker exec -it docteams-postgres psql -U postgres -d app -c "\dn" | grep tenant_mathebula || echo "clean"
  ```
- [ ] **0.D** Delete any Keycloak users with `@mathebula-test.local` emails from the `docteams` realm (use Keycloak admin console at `http://localhost:8180/admin` or Admin REST API)
- [ ] **0.E** Delete any portal contact with email `sipho.portal@example.com` or `moroka.portal@example.com` from the backend (`DELETE FROM public.portal_contacts WHERE email IN (...)` after tenant schema dropped, or re-provisioning Day 0 will fail with duplicate contact)
- [ ] **0.F** Confirm Mailpit inbox is empty or purged (visit `http://localhost:8025` → "Delete all")
- [ ] **0.G** Confirm PayFast sandbox credentials are configured in `OrgIntegration` seed data OR set up a firm-side stub adapter for Day 30 — document which path is in use
- [ ] **0.H** Confirm `legal-za` vertical profile pack is installed (Phase 65/66 infrastructure). From backend logs / `/api/packs/status`: `court-calendar`, `trust-accounting`, `conflict-check`, `lssa-tariff`, `legal-docs` must all be `INSTALLED`.

---

## Day 0 — Firm org onboarding (Keycloak flow)  `[FIRM]`

### Phase A: Access request & OTP verification

**Actor**: Thandi Mathebula (unauthenticated, public)

- [ ] **0.1** Open fresh browser context → navigate to `http://localhost:3000` → landing page loads, zero console errors
- [ ] **0.2** Click **"Get Started"** / **"Request Access"** → routes to `/request-access`
- [ ] **0.3** Form fields visible: Email, Full Name, Organization, Country, Industry
- [ ] **0.4** Fill and submit:
  - Email: `thandi@mathebula-test.local`
  - Full Name: **Thandi Mathebula**
  - Organization: **Mathebula & Partners**
  - Country: **South Africa**
  - Industry: **Legal Services**
- [ ] **0.5** Transitions to OTP step (same page, step 2)
- [ ] **0.6** Mailpit (`http://localhost:8025`) → OTP email for `thandi@mathebula-test.local`, subject contains "verification"
- [ ] **0.7** Copy OTP → enter → **Verify**
- [ ] **0.8** Success card: "Your request has been submitted for review"

### Phase B: Platform admin approval

**Actor**: Platform Admin — **context swap** to a fresh incognito window

- [ ] **0.9** Open fresh incognito → navigate to `http://localhost:3000/dashboard` → redirected to Keycloak login
- [ ] **0.10** Login as `padmin@docteams.local` / `password` → lands on platform admin home
- [ ] **0.11** Navigate to `/platform-admin/access-requests`
- [ ] **0.12** Mathebula & Partners visible in Pending: Industry = Legal Services, Country = South Africa
- [ ] **0.13** Click into request → detail shows all submitted fields
- [ ] **0.14** Click **Approve** → AlertDialog → **Confirm**
- [ ] **0.15** Status = **Approved**, no provisioning error banner
- [ ] **0.16** Vertical profile auto-assigned = `legal-za`. Verify via `curl -sS http://localhost:8443/api/orgs/mathebula-partners/profile -H "Authorization: Bearer $PADMIN_TOKEN"` OR approval card display
- [ ] **0.17** Mailpit → Keycloak invitation email to `thandi@mathebula-test.local`

### Phase C: Owner Keycloak registration

**Actor**: Thandi — **context swap** to a fresh incognito window

- [ ] **0.18** Open the Keycloak invitation link from Mailpit
- [ ] **0.19** Keycloak registration page loads with org = Mathebula & Partners pre-bound
- [ ] **0.20** Fill: First Name = Thandi, Last Name = Mathebula, Password = `SecureP@ss1`, Confirm = `SecureP@ss1`
- [ ] **0.21** Submit → redirected to app → lands on `/org/mathebula-partners/dashboard` (or equivalent slug)
- [ ] **0.22** Sidebar shows org name **Mathebula & Partners**, user name **Thandi Mathebula**
- [ ] **0.23** Legal terminology active: sidebar shows **Matters**, **Clients**, **Fee Notes** — NOT "Projects", "Customers", "Invoices"
- [ ] **0.24** Legal module nav items visible: **Matters**, **Trust Accounting**, **Court Calendar**, **Conflict Check**
- [ ] **0.25** 📸 **Screenshot**: `day-00-firm-dashboard-legal.png` — dashboard with legal nav + terminology

### Phase D: Team invites

**Actor**: Thandi (Owner, logged in)

- [ ] **0.26** Navigate to **Settings > Team** (`/settings/team`)
- [ ] **0.27** Thandi listed as Owner. Confirm no "Upgrade to Pro" gate exists anywhere on the invite flow
- [ ] **0.28** Invite `bob@mathebula-test.local` as **Admin** → Send
- [ ] **0.29** Invite `carol@mathebula-test.local` as **Member** → Send
- [ ] **0.30** Mailpit → two Keycloak invitation emails arrived
- [ ] **0.31** (Context swap) Open Bob's invite in fresh incognito → register (First=Bob, Last=Ndlovu, Password=`SecureP@ss2`) → reaches dashboard → logout
- [ ] **0.32** (Context swap) Open Carol's invite in fresh incognito → register (First=Carol, Last=Mokoena, Password=`SecureP@ss3`) → reaches dashboard → logout

**Day 0 checkpoints**
- [ ] Org created via real access-request → approval → Keycloak registration (no mock IDP anywhere)
- [ ] Three Keycloak users exist under realm `docteams` for `@mathebula-test.local`
- [ ] Vertical profile = `legal-za`, terminology + nav reflect legal
- [ ] No tier / upgrade / billing upsell visible

---

## Day 1 — Firm onboarding polish  `[FIRM]`

**Actor**: Thandi (logged in)

- [ ] **1.1** Navigate to **Settings > Organization** → upload firm logo (any ≤ 200 KB PNG) → set brand colour to Mathebula navy `#1B3358` → Save
- [ ] **1.2** Refresh → verify brand colour applied to sidebar accent + logo renders at top of sidebar
- [ ] **1.3** Navigate to **Settings > Rate Cards** → verify LSSA tariff rates pre-seeded (from `legal-za` rate pack, Phase 55)
- [ ] **1.4** Verify at least one tariff entry: **High Court — attending at court, per hour** with the latest published LSSA schedule in ZAR (currently 2024/2025 at the time of this test run; LSSA tariffs are revised every 2–3 years)
- [ ] **1.5** Navigate to **Settings > Trust Accounts** → create a new trust account:
  - Name: **Mathebula Trust — Main**
  - Bank: **Standard Bank**
  - Account number: `12345678` (test placeholder)
  - Type: **SECTION_86** (Legal Practice Act)
- [ ] **1.6** Trust account saves, no validation error, appears in list with balance **R 0.00**
- [ ] **1.7** 📸 Optional screenshot: `day-01-trust-account-created.png`

**Day 1 checkpoints**
- [ ] Firm branding (logo + colour) persists across logout/login
- [ ] LSSA tariff table pre-populated, non-empty
- [ ] Trust account created under Section 86 basis

---

## Day 2 — Onboard Sipho as client, run conflict check + KYC  `[FIRM]`

**Actor**: Bob Ndlovu (Admin — realistic assignment for intake) — **context swap** to fresh window, login as Bob

- [ ] **2.1** Navigate to **Clients** → click **+ New Client**
- [ ] **2.2** Verify dialog shows legal-specific promoted fields for INDIVIDUAL (ID number, preferred contact, matter_type hint)
- [ ] **2.3** Fill:
  - Type: **INDIVIDUAL**
  - First Name: **Sipho**
  - Last Name: **Dlamini**
  - Email: **sipho.portal@example.com** (this email seeds the portal contact later)
  - ID Number: **8501015800088** (valid SA format)
  - Phone: **+27 82 555 0101**
  - Address: **12 Loveday St, Johannesburg, 2001**
- [ ] **2.4** Submit → client created, redirected to client detail
- [ ] **2.5** On client detail → click **Run Conflict Check** → search runs against existing matters + adverse party registry
- [ ] **2.6** Result = **CLEAR** (no pre-existing records) — green confirmation state renders
- [ ] **2.7** 📸 **Screenshot**: `day-02-conflict-check-clear.png` — CLEAR result badge
- [ ] **2.8** On client detail → click **Run KYC Verification** (if KYC adapter configured; otherwise skip and note in gap report)
- [ ] **2.9** KYC adapter returns **Verified** — KYC badge renders green with verification timestamp
- [ ] **2.10** 📸 **Screenshot**: `day-02-kyc-verified.png` — KYC verified status

**Day 2 checkpoints**
- [ ] Client created with INDIVIDUAL type and legal-specific fields
- [ ] Conflict check CLEAR (no false positive hits)
- [ ] KYC verification badge visible on client detail, or KYC not-configured state logged to gap report

---

## Day 3 — Create RAF matter, send FICA info request  `[FIRM]`

**Actor**: Bob Ndlovu (still logged in)

- [ ] **3.1** On Sipho's client detail → click **+ New Matter**
- [ ] **3.2** Dialog uses legal-specific matter-type template selector (from Phase 64/66 matter templates)
- [ ] **3.3** Fill:
  - Matter reference: **RAF-2026-001**
  - Matter title: **Dlamini v Road Accident Fund**
  - Matter type: **Litigation — Road Accident Fund** (from template)
  - Court: **Gauteng Division, Pretoria**
  - Case number: (blank at intake; populated later)
  - Primary attorney: Bob Ndlovu
- [ ] **3.4** Submit → matter created, redirected to matter detail
- [ ] **3.5** Verify matter sidebar shows tabs: **Overview, Tasks, Documents, Time, Fee Notes, Trust, Activity, Audit**
- [ ] **3.6** Promoted fields (matter_type, court_name, case_number) render inline on Overview tab — **NOT** duplicated in a generic "Custom Fields" section
- [ ] **3.7** Navigate to **Info Requests** tab on matter → click **+ New Info Request**
- [ ] **3.8** Select template: **FICA Onboarding Pack** (from `legal-za` request pack)
- [ ] **3.9** Addressee: **Sipho Dlamini** (portal contact auto-populated from client record)
- [ ] **3.10** Request items pre-filled from template: ID copy, Proof of residence (≤ 3 months), Bank statement (≤ 3 months)
- [ ] **3.11** Due date: Day 10 (7 days from today)
- [ ] **3.12** Click **Send** → info request status = **Sent**
- [ ] **3.13** Verify portal contact `sipho.portal@example.com` was created / linked (visible in matter "Client contact" section)
- [ ] **3.14** Mailpit → magic-link email to `sipho.portal@example.com`, subject contains "sign in" / "action required" / "your portal"

**Day 3 checkpoints**
- [ ] Matter created with reference format RAF-YYYY-NNN
- [ ] Matter-type template instantiated — phase sections present, LSSA tariff linked
- [ ] Promoted matter fields render inline, not duplicated
- [ ] FICA info request dispatched, magic-link email sent

---

## Day 4 — Sipho first portal login, upload FICA documents  `[PORTAL]`

**Context swap** — close firm browser context, open fresh browser context for port 3002, confirm `curl -sS http://localhost:3002/ -o /dev/null -w "%{http_code}\n"` returns 200 before proceeding.

**Actor**: Sipho Dlamini (unauthenticated, arriving via email magic-link)

### Phase A: Magic-link landing

- [ ] **4.1** Open Mailpit (`http://localhost:8025`) → locate FICA info-request magic-link email for `sipho.portal@example.com`
- [ ] **4.2** Click the magic-link in email body → browser navigates to `http://localhost:3002/accept/[token]`
- [ ] **4.3** Portal exchanges token (`POST /portal/auth/exchange` fires) → redirects to `/home`
- [ ] **4.4** Verify `/home` renders: pending info request section shows **"FICA Onboarding Pack"** with due date
- [ ] **4.5** Verify header / sidebar shows Mathebula firm branding (navy accent, firm logo if uploaded Day 1)
- [ ] **4.6** Verify user identity displayed as "Sipho Dlamini" (from firm-side client record)
- [ ] **4.7** 📸 Optional screenshot: `day-04-portal-home-first-login.png`

### Phase B: Upload FICA documents

- [ ] **4.8** Click into "FICA Onboarding Pack" → info-request detail renders
- [ ] **4.9** Verify three upload slots labelled: ID copy, Proof of residence, Bank statement
- [ ] **4.10** Upload a test PDF (any ≤ 2 MB) to each slot → three upload-progress indicators → three completion states
- [ ] **4.11** Add optional note: "All documents current as of this week"
- [ ] **4.12** Click **Submit** → info-request state transitions to **Submitted** (or "Awaiting review")
- [ ] **4.13** Verify `/home` "Pending info requests" card no longer shows this request as pending
- [ ] **4.14** 📸 Optional screenshot: `day-04-fica-submitted.png`

**Day 4 checkpoints**
- [ ] Magic-link login succeeded — no Keycloak form appeared at any step
- [ ] Uploads stored (firm side will verify on Day 5)
- [ ] Info-request state machine progressed: Sent → Submitted
- [ ] No firm-side terminology leaks on portal ("matter" → "your case", "info request" retained, no "task" / "ticket")

---

## Day 5 — Firm reviews FICA submission  `[FIRM]`

**Context swap** — close portal context, open fresh firm browser context on port 3000, login as Bob.

**Actor**: Bob Ndlovu

- [ ] **5.1** Navigate to matter RAF-2026-001 → Info Requests tab
- [ ] **5.2** FICA Onboarding Pack shows status = **Submitted** with 3 documents attached
- [ ] **5.3** Click into request → download each document → verify all three open cleanly
- [ ] **5.4** Click **Mark as Reviewed** / **Approve** → state transitions to **Completed**
- [ ] **5.5** Verify matter Overview shows FICA status = **Complete** (or equivalent lifecycle indicator)
- [ ] **5.6** Mailpit → notification email to Sipho: "Your FICA documents have been received" (or equivalent). Log gap if no such email is sent.

**Day 5 checkpoints**
- [ ] Three uploaded documents retrievable firm-side
- [ ] Info request lifecycle: Submitted → Completed
- [ ] Matter FICA / KYC status indicator updated

---

## Day 7 — Firm drafts + sends proposal (LSSA tariff fee estimate)  `[FIRM]`

**Actor**: Thandi Mathebula (Owner — signs proposals) — context swap, login as Thandi

- [ ] **7.1** Navigate to matter RAF-2026-001 → click **+ New Proposal** (or Proposals tab → New)
- [ ] **7.2** Proposal template dropdown shows legal-specific templates from doc-template pack; select **Litigation Engagement — RAF**
- [ ] **7.3** Verify fee estimate section pre-populates with LSSA tariff line items appropriate for RAF claims (attendances, drafting, court appearances)
- [ ] **7.4** Adjust estimated hours: 30h attorney (Bob) + 5h senior partner (Thandi) — ZAR estimate calculates automatically
- [ ] **7.5** Add engagement scope in Tiptap editor: "Represent client in Road Accident Fund claim following motor vehicle accident on [DATE], up to and including settlement or trial."
- [ ] **7.6** Set effective date = Day 10, expiry = Day 17 (7-day acceptance window)
- [ ] **7.7** Click **Save** → proposal status = **Draft**
- [ ] **7.8** Click **Send for Acceptance** → confirmation dialog → Confirm
- [ ] **7.9** Proposal status transitions to **Sent**, acceptance URL generated
- [ ] **7.10** Mailpit → proposal email to `sipho.portal@example.com` with subject containing "proposal" / "engagement letter" / "please review"
- [ ] **7.11** Verify email body includes click-through link to portal proposal URL

**Day 7 checkpoints**
- [ ] Proposal template from legal-za doc-template pack is instantiable
- [ ] LSSA tariff line items render in fee estimate (tariff integration verified)
- [ ] Proposal dispatched, magic-link / secure link email sent to portal contact

---

## Day 8 — Sipho reviews + accepts proposal  `[PORTAL]`

**Context swap** — close firm context, open portal context on port 3002.

**Actor**: Sipho Dlamini (returning portal contact — reuses the Day 4 magic-link session IF still valid, else re-requests one)

- [ ] **8.1** Mailpit → open the proposal email → click the proposal link → lands on `/proposals/[id]` on portal
- [ ] **8.2** Verify proposal detail page renders: scope, fee estimate breakdown (tariff lines + totals in ZAR incl. VAT), effective date, expiry, Accept / Decline buttons
- [ ] **8.3** Verify fee estimate renders with ZAR currency symbol + VAT 15% line
- [ ] **8.4** 📸 **Screenshot**: `day-08-proposal-review.png`
- [ ] **8.5** Click **Accept** → acceptance confirmation dialog (or inline confirm)
- [ ] **8.6** (If tenant routes through `/accept/[token]`) complete the acceptance step — portal reads "You are about to accept this proposal on behalf of Sipho Dlamini"
- [ ] **8.7** Confirm acceptance → proposal status transitions to **Accepted**, timestamp + actor recorded
- [ ] **8.8** 📸 **Screenshot**: `day-08-proposal-accepted.png` — success / confirmation state
- [ ] **8.9** Navigate back to `/home` → "Pending proposals" surface no longer shows this proposal
- [ ] **8.10** (Optional if visible on portal) Check `/proposals` list — accepted proposal either moves to "Past" tab or shows "Accepted" badge

**Day 8 checkpoints**
- [ ] Proposal accessible via email link without re-authentication (magic-link session valid OR transparent re-exchange)
- [ ] Acceptance recorded (firm will verify on Day 10)
- [ ] No double-accept bug: clicking Accept again shows already-accepted state, not a second transition
- [ ] Terminology consistent: portal copy reads "proposal" throughout

---

## Day 10 — Firm activates matter, deposits trust funds  `[FIRM]`

**Context swap** — portal → firm (port 3000, login as Thandi).

**Actor**: Thandi Mathebula

### Phase A: Verify proposal acceptance flowed through

- [ ] **10.1** Navigate to matter RAF-2026-001 → Proposals tab → verify proposal = **Accepted** with Sipho's timestamp
- [ ] **10.2** Matter lifecycle: verify matter has auto-transitioned to **ACTIVE** (Phase 29 lifecycle), or transition manually per current UX

### Phase B: Trust deposit — bank statement import + deposit recording

- [ ] **10.3** Navigate to **Trust Accounting** → **Mathebula Trust — Main** account
- [ ] **10.4** Either: (a) use **Bank Statement Import** to upload a CSV with a `+R50,000.00` line referencing Sipho, OR (b) record a manual deposit:
  - Amount: **R 50,000.00**
  - Type: **Deposit**
  - Date: Day 10
  - Client: Sipho Dlamini
  - Matter: RAF-2026-001
  - Source description: "Initial trust deposit — RAF-2026-001"
- [ ] **10.5** Submit → transaction enters approval queue (if dual-approval enabled) OR posts directly depending on trust account config
- [ ] **10.6** If in approval queue: switch to Bob → navigate to Trust Accounting → Pending Approvals → approve the deposit
- [ ] **10.7** Trust account balance reflects **R 50,000.00** with Sipho's client ledger card showing +R50,000.00
- [ ] **10.8** Navigate to matter → **Trust** tab → verify matter-level trust balance = **R 50,000.00**
- [ ] **10.9** 📸 Optional: `day-10-firm-trust-deposit-recorded.png`

**Day 10 checkpoints**
- [ ] Proposal acceptance flowed from portal to firm side (timestamp matches)
- [ ] Trust deposit posts against the correct client ledger card (Section 86 compliance)
- [ ] Client ledger + matter trust tab + account balance all reconcile to R 50,000.00

---

## Day 11 — Sipho sees trust balance on portal  `[PORTAL]`

**Context swap** — firm → portal (port 3002).

**Actor**: Sipho Dlamini

- [ ] **11.1** Mailpit → verify trust-deposit nudge email arrived for `sipho.portal@example.com` (subject contains "trust deposit" / "funds received" / "trust balance update")
- [ ] **11.2** Click the "View trust balance" link → lands on `/trust`
- [ ] **11.3** Verify `/trust` renders: trust balance card at top, recent deposits list, ledger preview
- [ ] **11.4** Trust balance card shows **R 50,000.00** (matches firm-side Day 10 posting)
- [ ] **11.5** Recent deposits list shows the R50,000 deposit dated Day 10 with source description (sanitisation rules may strip internal notes — verify only client-safe copy is visible)
- [ ] **11.6** Click into the matter trust ledger → line-level history renders with all transactions (just the one deposit at this point)
- [ ] **11.7** 📸 **Screenshot**: `day-11-portal-trust-balance.png` — trust balance card with first deposit
- [ ] **11.8** Verify currency rendered as **R** / **ZAR** (not $ / EUR / GBP)

**Day 11 checkpoints**
- [ ] Trust deposit visible on portal within 1 business day of firm posting
- [ ] Amount matches firm-side Section 86 ledger (no rounding / display bug)
- [ ] Description sanitisation — any firm-internal `[internal]` tags stripped, copy ≤ 140 chars, safe fallback if no client-safe copy
- [ ] ZAR currency throughout (legal-za default)

---

## Day 14 — Firm onboards Moroka Family Trust (**isolation setup**)  `[FIRM]`

**Why this day exists**: On Day 15 Sipho's portal session must demonstrate he cannot see Moroka's data. This requires Moroka to exist with substantial data (matter, docs, info request, trust deposit) on the same tenant by then.

**Context swap** — portal → firm (port 3000, login as Thandi).

**Actor**: Thandi Mathebula (Owner)

### Phase A: Create Moroka Family Trust client

- [ ] **14.1** Navigate to **Clients** → **+ New Client**
- [ ] **14.2** Fill:
  - Type: **TRUST** (entity client)
  - Trust name: **Moroka Family Trust**
  - Trust registration number: **IT 001234/2024**
  - Primary contact email: **moroka.portal@example.com**
  - Beneficial owners: add 2 (any test names, comply with FICA trust template)
- [ ] **14.3** Submit → client created
- [ ] **14.4** Run **Conflict Check** → CLEAR → proceed (no 📸 required — this is setup, not a demo moment)

### Phase B: Create Moroka matter

- [ ] **14.5** On Moroka client detail → **+ New Matter** → select **Deceased Estate** matter template
- [ ] **14.6** Fill:
  - Matter reference: **EST-2026-002**
  - Matter title: **Estate Late Peter Moroka**
  - Matter type: **Estates — Deceased**
  - Master's Office: Johannesburg
- [ ] **14.7** Submit → matter created

### Phase C: Seed data on Moroka matter (to make isolation check meaningful)

- [ ] **14.8** Send an info request on Moroka matter: template **Liquidation and Distribution Account docs** → addressee = `moroka.portal@example.com`, due Day 30 → Send
- [ ] **14.9** Upload one internal document to Moroka matter Documents tab (e.g., "Death certificate — Moroka.pdf" — use any test PDF)
- [ ] **14.10** Record a trust deposit of R 25,000 against Moroka Trust / EST-2026-002 (either via bank import or manual)
- [ ] **14.11** Note the Moroka matter ID, client ID, info-request ID, document ID, and trust-transaction ID — record these in the `isolation-probe-ids.txt` scratch file (or note in gap-report appendix). The Day 15 portal probes will target these IDs to prove Sipho cannot reach them.

**Day 14 checkpoints**
- [ ] Two clients and two matters exist on the tenant: Sipho (individual, RAF) + Moroka (trust, Estate)
- [ ] Moroka has at least: 1 info request, 1 document, 1 trust deposit
- [ ] Moroka entity IDs captured for Day 15 probes

---

## Day 15 — **Isolation check** — Sipho cannot see Moroka's data  `[PORTAL]`

**Context swap** — firm → portal (port 3002). This day is dedicated to validating that the portal's authorization correctly scopes all data to the logged-in portal contact.

**Actor**: Sipho Dlamini

### Phase A: List-view leak probe (no Moroka data should appear anywhere)

- [ ] **15.1** Login as Sipho (magic-link from Day 4 session OR re-request at `/login`)
- [ ] **15.2** Navigate to `/home` → verify:
  - Pending info requests list shows ONLY Sipho's (no "Liquidation and Distribution" entry)
  - Recent invoices list shows ONLY Sipho's (empty so far — no Moroka invoice leak)
  - Upcoming deadlines list shows ONLY Sipho's
  - Recent documents list shows ONLY Sipho's matter documents (no Moroka death certificate)
- [ ] **15.3** Navigate to `/projects` (or `/matters` — per portal terminology) → verify ONLY Sipho's matter (RAF-2026-001) listed. No Moroka Estate matter.
- [ ] **15.4** Navigate to `/trust` → verify:
  - Balance shows ONLY Sipho's R 50,000 (NOT aggregated R 75,000)
  - Transaction list shows ONLY the Day 10 deposit on RAF-2026-001
  - NO mention of Moroka, EST-2026-002, or the R 25,000 deposit
- [ ] **15.5** Navigate to `/invoices` → verify empty or shows only Sipho's invoices (none yet; Moroka has none either at this point, so empty is correct)
- [ ] **15.6** Navigate to `/deadlines` → verify only Sipho's matter deadlines (none / few); no Moroka Master's Office filing deadlines
- [ ] **15.7** Navigate to `/proposals` → verify only Sipho's accepted proposal; no other
- [ ] **15.8** 📸 **Screenshot**: `day-15-portal-home-isolated.png` — Sipho's `/home` showing only his data

### Phase B: Direct-URL probe (hard negative — Moroka IDs used)

Using the Moroka entity IDs captured Day 14, attempt direct URL navigation while logged in as Sipho:

- [ ] **15.9** Navigate directly to `/projects/[morokaMatterId]` → expect **404** or **403** or redirect to `/home` with an error toast. **MUST NOT** render Moroka matter detail.
- [ ] **15.10** Navigate directly to `/info-requests/[morokaInfoRequestId]` → expect **404** / **403** / redirect. **MUST NOT** show "Liquidation and Distribution Account docs"
- [ ] **15.11** Navigate directly to `/documents/[morokaDocumentId]` → expect **404** / **403** / denial. **MUST NOT** return Moroka's death-certificate PDF
- [ ] **15.12** Navigate directly to `/trust/transactions/[morokaTrustTxId]` (if portal exposes this URL shape) → expect **404** / **403**. **MUST NOT** render Moroka's R 25,000 deposit
- [ ] **15.13** 📸 **Screenshot**: `day-15-portal-denial.png` — denial page or redirect state from one of the probes

### Phase C: API-level probe (hard negative — backend must enforce, not just frontend)

Using Sipho's portal JWT (capture from browser devtools → Application → cookies / localStorage → look for `portal_jwt` or similar):

- [ ] **15.14** `curl -sS http://localhost:8080/portal/api/matters/[morokaMatterId] -H "Authorization: Bearer $SIPHO_JWT"` → expect **403** or **404**. MUST NOT return 200 with Moroka data.
- [ ] **15.15** `curl -sS http://localhost:8080/portal/api/info-requests/[morokaInfoRequestId] -H "Authorization: Bearer $SIPHO_JWT"` → expect **403** / **404**
- [ ] **15.16** `curl -sS http://localhost:8080/portal/api/trust/transactions/[morokaTrustTxId] -H "Authorization: Bearer $SIPHO_JWT"` → expect **403** / **404**
- [ ] **15.17** `curl -sS http://localhost:8080/portal/api/documents/[morokaDocumentId] -H "Authorization: Bearer $SIPHO_JWT"` → expect **403** / **404**; if 200 is returned, confirm response body is empty / error — bytes MUST NOT be Moroka's document content
- [ ] **15.18** `curl -sS http://localhost:8080/portal/api/home -H "Authorization: Bearer $SIPHO_JWT" | jq '.'` → expect response containing ONLY Sipho's entities; no Moroka ids in any list field (infoRequests, deadlines, invoices, projects)

### Phase D: Activity trail + digest leak probe

- [ ] **15.19** Navigate to `/profile` or activity view (whichever portal exposes) → verify any activity / audit trail entries reference ONLY Sipho's matters. No Moroka events.
- [ ] **15.20** (If digest already delivered by Day 15) Inspect the most recent digest email for Sipho in Mailpit → verify body contains ONLY Sipho's matter refs/counts. No Moroka references.

**Day 15 checkpoints (BLOCKER severity — any failure here is a HIGH/BLOCKER gap)**
- [ ] List views on `/home`, `/projects`, `/trust`, `/invoices`, `/deadlines`, `/proposals` show ONLY Sipho's data
- [ ] Direct-URL probes to 4+ Moroka entity IDs denied at the frontend (no matter data renders)
- [ ] API-level probes to 4+ Moroka endpoints denied at backend (403/404, never 200)
- [ ] Trust balance card shows R 50,000 (Sipho's only) — not R 75,000 (aggregate leak)
- [ ] Activity trail / digest have zero Moroka references

**If ANY Phase B or Phase C probe returns 200 with Moroka data → immediate BLOCKER; halt script; file gap report entry; dispatch fix before continuing.**

---

## Day 21 — Firm logs time, adds disbursement, creates court date  `[FIRM]`

**Context swap** — portal → firm (port 3000, login as Bob).

**Actor**: Bob Ndlovu

### Phase A: Time entry against LSSA tariff

- [ ] **21.1** Navigate to matter RAF-2026-001 → Time tab → **+ Log Time**
- [ ] **21.2** Log 2.5h of work → select tariff activity **"Consultation with client — per 15 minutes"** from LSSA dropdown (tariff Phase 55 integration)
- [ ] **21.3** Billable = Yes, rate auto-populates from tariff
- [ ] **21.4** Submit → time entry saved, amount calculated
- [ ] **21.5** Log another 1.5h under **"Drafting particulars of claim — per page / per hour"** tariff entry

### Phase B: Disbursement (Phase 67)

- [ ] **21.6** Navigate to **Disbursements** tab (Phase 67 Epic 486) → **+ New Disbursement**
- [ ] **21.7** Type: **Sheriff's fee**, Amount: **R 1,250.00**, Date: today, Description: "Sheriff service of summons on RAF"
- [ ] **21.8** Mark as **recoverable** (client-rebillable)
- [ ] **21.9** Submit → disbursement saved, appears in matter Disbursements tab with status **UNBILLED**

### Phase C: Court date

- [ ] **21.10** Navigate to matter **Court Calendar** tab → **+ Add Court Date**
- [ ] **21.11** Fill:
  - Court: Gauteng Division, Pretoria
  - Date: Day 35 (14 days from today)
  - Type: Pre-trial conference
  - Attorney attending: Bob Ndlovu
- [ ] **21.12** Submit → court event saved, appears on firm-side **Court Calendar** page and matter Overview

**Day 21 checkpoints**
- [ ] Time entries post against tariff, rate auto-populates
- [ ] Disbursement recorded with recoverable flag (feeds Day 28 fee note)
- [ ] Court date added, visible on calendar + matter

---

## Day 28 — Firm generates first fee note (bulk billing)  `[FIRM]`

**Actor**: Thandi Mathebula (Owner — signs fee notes) — context swap, login as Thandi

- [ ] **28.1** Navigate to **Bulk Billing** → **+ New Billing Run**
- [ ] **28.2** Scope = `By Client`, select Sipho Dlamini → preview shows:
  - Unbilled time: 4h (tariff-rated) → subtotal ZAR
  - Unbilled disbursements: R 1,250 (sheriff's fee)
  - Combined total + VAT 15%
- [ ] **28.3** Cherry-pick: keep all time entries + the disbursement checked
- [ ] **28.4** Click **Generate Fee Notes** → preview opens showing draft fee note
- [ ] **28.5** Verify fee note renders with:
  - Mathebula letterhead (logo + firm details)
  - Matter reference **RAF-2026-001**
  - LSSA tariff line items with activity descriptors
  - Sheriff's fee disbursement line, clearly labelled as disbursement
  - VAT 15% line
  - ZAR total
  - Banking details for payment
- [ ] **28.6** Click **Approve & Send** → fee note status transitions to **SENT**
- [ ] **28.7** Mailpit → fee note email to `sipho.portal@example.com`, subject contains "fee note" (NOT "invoice" — terminology check)
- [ ] **28.8** 📸 Optional: `day-28-firm-fee-note-sent.png`

**Day 28 checkpoints**
- [ ] Fee note generated with tariff lines + disbursement line correctly separated
- [ ] Terminology: firm-side copy reads "Fee Note" (not "Invoice") end-to-end
- [ ] Email dispatched with portal payment link

---

## Day 30 — Sipho pays fee note via PayFast sandbox  `[PORTAL]`

**Context swap** — firm → portal (port 3002).

**Actor**: Sipho Dlamini

- [ ] **30.1** Mailpit → open fee-note email → click **View fee note** link → lands on `/invoices/[id]` on portal (URL uses `/invoices` but display copy may say "Fee Note")
- [ ] **30.2** Verify fee-note detail renders: line items (tariff + disbursement), subtotal, VAT 15%, total, due date, Pay button
- [ ] **30.3** Verify terminology: portal copy either uses "Fee Note" (via terminology override) or "Invoice" — verify at least the URL/page is consistent with firm-side. Note discrepancy in gap report if any.
- [ ] **30.4** 📸 **Screenshot**: `day-30-portal-fee-note-detail.png`
- [ ] **30.5** Click **Pay** → PayFast sandbox redirect opens (or embedded payment sheet, depending on config)
- [ ] **30.6** Complete sandbox payment:
  - Use PayFast sandbox test credentials (document which sandbox creds are configured in `OrgIntegration`)
  - Amount auto-populated from fee-note total
- [ ] **30.7** Payment succeeds → redirect back to portal → fee-note status transitions to **Paid**
- [ ] **30.8** Receipt / payment confirmation available for download
- [ ] **30.9** 📸 **Screenshot**: `day-30-portal-payment-success.png` — success / receipt state
- [ ] **30.10** Navigate to `/invoices` → verify fee note moved from "Due" filter to "Paid"
- [ ] **30.11** **Passive isolation spot-check**: verify `/invoices` list still shows only Sipho's invoices; no Moroka invoices / fee notes visible

**Day 30 checkpoints**
- [ ] PayFast sandbox payment completes end-to-end (webhook-driven reconciliation works)
- [ ] Firm-side fee note reflects PAID within 60s (confirm by switching to firm session briefly if needed)
- [ ] Receipt download works (PDF opens cleanly)
- [ ] Isolation still holding — no Moroka fee notes visible

---

## Day 45 — Firm: second info request + second trust deposit  `[FIRM]`

**Context swap** — portal → firm (port 3000, login as Bob).

**Actor**: Bob Ndlovu

- [ ] **45.1** On matter RAF-2026-001 → **+ New Info Request** → free-form: title "Supporting medical evidence", 2 items (hospital discharge summary, orthopaedic report), due Day 52 → Send
- [ ] **45.2** Mailpit → verify second magic-link email sent to Sipho
- [ ] **45.3** Navigate to Trust Accounting → Mathebula Trust — Main → record / import a second deposit of **R 20,000** against Sipho / RAF-2026-001 (describe as "Top-up per engagement letter")
- [ ] **45.4** Approve (if dual-approval) → client ledger now shows two deposits totalling **R 70,000** (R 50,000 + R 20,000)
- [ ] **45.5** Matter Trust tab shows balance **R 70,000** (minus any fee-transfer-out if applied — in this script, none yet)

**Day 45 checkpoints**
- [ ] Second info request dispatched
- [ ] Trust balance reconciles to R 70,000 on client ledger and matter trust tab

---

## Day 46 — Sipho responds to second info request + trust re-check + isolation spot-check  `[PORTAL]`

**Context swap** — firm → portal (port 3002).

**Actor**: Sipho Dlamini

- [ ] **46.1** Login via magic-link for second info request
- [ ] **46.2** `/home` → "Supporting medical evidence" shows as pending → click into it
- [ ] **46.3** Upload 2 test PDFs (discharge summary, orthopaedic report) → submit → state → **Submitted**
- [ ] **46.4** Navigate to `/trust` → balance now shows **R 70,000** (reflects both deposits)
- [ ] **46.5** Transaction list shows both deposits: Day 10 R 50,000 + Day 45 R 20,000, descending or ascending by date, both dated correctly, amounts correct
- [ ] **46.6** **Passive isolation spot-check** — trust list still shows only Sipho's matter; no Moroka deposit (R 25,000) merged in anywhere
- [ ] **46.7** `/home` → "Pending info requests" no longer shows the medical evidence request
- [ ] **46.8** 📸 Optional: `day-46-portal-trust-two-deposits.png`

**Day 46 checkpoints**
- [ ] Second info request lifecycle complete
- [ ] Trust balance update visible on portal (both deposits)
- [ ] Isolation holds — no Moroka data leak 31 days after the explicit check

---

## Day 60 — Firm matter closure + generate Statement of Account  `[FIRM]`

**Context swap** — portal → firm (port 3000, login as Thandi).

**Actor**: Thandi Mathebula (Owner — required for closure if override ever needed; in this clean-path scenario no override is required)

### Phase A: Settle matter finances before closure

- [ ] **60.1** Generate a final fee note for any remaining unbilled time + disbursements (if any). For the script narrative, assume one more small fee note is issued R 15,000 and settled via trust transfer.
- [ ] **60.2** Navigate to Trust Accounting → **Fee Transfer Out** → transfer R 15,000 from Sipho's trust to firm business account (pay the final fee note from trust)
- [ ] **60.3** Approve the fee transfer → client ledger shows transfer-out, remaining trust balance reconciles

### Phase B: Run matter closure workflow (Phase 67 Epic 489)

- [ ] **60.4** Navigate to matter RAF-2026-001 → **Close Matter**
- [ ] **60.5** Closure dialog Step 1 — gate report renders. Verify all gates GREEN: no unbilled time, no unpaid fee notes, trust balance zero or earmarked, no pending tasks
- [ ] **60.6** Click **Continue** → Step 2 — Close form
- [ ] **60.7** Reason: **CONCLUDED** (settlement reached)
- [ ] **60.8** Leave **Generate closure letter** checked, and also check **Generate Statement of Account** (Phase 67 Epic 491) if surfaced as a separate flag
- [ ] **60.9** Click **Confirm Close** → matter status = **CLOSED**
- [ ] **60.10** Closure letter + Statement of Account documents both attached to matter Documents tab
- [ ] **60.11** Retention policy row inserted with `end_date = today + 5 years` (ADR-249 verify)

**Day 60 checkpoints**
- [ ] Matter closes cleanly on the happy path (no override needed)
- [ ] Statement of Account PDF generated and attached to matter Documents
- [ ] Mailpit → notification email to `sipho.portal@example.com`: "Your Statement of Account is ready" (or equivalent)

---

## Day 61 — Sipho downloads Statement of Account from portal  `[PORTAL]`

**Context swap** — firm → portal (port 3002).

**Actor**: Sipho Dlamini

- [ ] **61.1** Mailpit → open "Statement of Account ready" email → click link → lands on portal `/projects/[matterId]` (matter detail) OR directly at `/documents/[docId]`
- [ ] **61.2** Navigate to Documents tab on matter → verify **Statement of Account — RAF-2026-001** is listed with today's date + file size
- [ ] **61.3** Click **Download** → PDF downloads cleanly
- [ ] **61.4** Open downloaded PDF → verify contents:
  - Mathebula letterhead + contact details
  - Matter reference RAF-2026-001 + party names
  - Opening balance: R 0.00
  - Deposits: R 50,000 (Day 10) + R 20,000 (Day 45) = R 70,000
  - Fee transfers out: Day 30 fee note paid (amount), Day 60 fee note paid (R 15,000)
  - Closing balance: reconciles to 0 or the residual earmarked amount
  - VAT line summary
- [ ] **61.5** 📸 **Screenshot**: `day-61-portal-soa-download.png` — Documents tab with SoA + download indicator
- [ ] **61.6** File byte-size matches firm-side preview byte-size (±5%)
- [ ] **61.7** Document title exactly matches firm-side copy — no "Untitled document" leak
- [ ] **61.8** Also visible: matter closure letter in Documents tab — verify it renders correctly too
- [ ] **61.9** Firm-side audit: switch briefly to firm session → audit log shows portal contact accessed the SoA document with timestamp matching Day 61

**Day 61 checkpoints**
- [ ] SoA downloads cleanly end-to-end
- [ ] Contents reconcile to firm-side Section 86 ledger and Day 60 closure state
- [ ] Firm-side audit event recorded for portal doc access (Phase 50 data-protection traceability)

---

## Day 75 — Weekly digest + late-cycle isolation spot-check  `[PORTAL]`

**Actor**: Sipho Dlamini

- [ ] **75.1** Mailpit → open most recent weekly digest email for `sipho.portal@example.com`. Subject contains "weekly update" / "your week"
- [ ] **75.2** Digest body mentions events from the matter: fee note paid, SoA downloaded, matter closed. All references use client-facing copy.
- [ ] **75.3** Crucially: digest MUST NOT reference Moroka / EST-2026-002 / any other client
- [ ] **75.4** Click a "View activity" link in the digest → lands on portal home or activity view
- [ ] **75.5** Activity trail renders events from Days 4, 8, 11, 15, 30, 46, 61 — all are Sipho's. Zero Moroka references.
- [ ] **75.6** **Passive isolation spot-check** (61 days after Day 14 onboarding of Moroka):
  - `/home` — no Moroka entries
  - `/trust` — balance shows what is remaining against RAF-2026-001 only (whatever the residual after Day 60 transfer); NOT R 25,000 Moroka leak
  - `/projects` — one matter only (RAF-2026-001, now CLOSED — verify it either shows as closed or moves to a "Past" tab)
- [ ] **75.7** 📸 Optional: `day-75-portal-digest-plus-activity.png`

**Day 75 checkpoints**
- [ ] Digest contents match activity trail (no "missing event" discrepancy)
- [ ] Closed matter correctly rendered as closed (not greyed out as error)
- [ ] Isolation still holds at Day 75

---

## Day 85 — Firm final closure paperwork (if any)  `[FIRM]`

**Context swap** — portal → firm.

**Actor**: Thandi

- [ ] **85.1** On RAF-2026-001 matter (CLOSED status) → verify closure letter attached Day 60
- [ ] **85.2** If tenant workflow requires it, generate a final closing letter / thank-you correspondence via the doc-template pack → attach
- [ ] **85.3** Verify matter retention policy (Day 60 created) still shows `end_date ≈ today + 5 years - 25 days` (days elapsed since closure)
- [ ] **85.4** Navigate to **Audit Log** (or platform admin view — requires Phase 69 if shipped, else firm-side activity feed):
  - Filter by matter = RAF-2026-001 → full 85-day history present
  - Filter by actor = Sipho (portal actor) → portal actions (FICA upload, proposal accept, fee-note pay, doc downloads) all recorded
- [ ] **85.5** 📸 Optional: `day-85-firm-audit-filtered.png`

**Day 85 checkpoints**
- [ ] Matter retention row persists correctly
- [ ] Audit log filters by actor work for BOTH firm users AND portal contacts (Phase 50 + Phase 69 readiness)

---

## Day 88 — Activity feed wow moment (side-by-side firm + portal)  `[FIRM → PORTAL]`

This is the closing-demo wow moment: show the firm's 90-day activity feed on the matter, then swap context to the portal and show Sipho's 90-day activity trail. Side-by-side they tell the same story.

- [ ] **88.1** `[FIRM]` On RAF-2026-001 → Activity tab → full 90-day history renders: matter created, info requests sent/received, time entries, disbursements, fee notes, payments, court date, matter closed, documents generated
- [ ] **88.2** 📸 **Screenshot**: `day-88-firm-activity-feed.png` — firm-side 90-day matter activity
- [ ] **88.3** Context swap to portal → login as Sipho → activity trail on `/home` or `/profile`
- [ ] **88.4** Activity trail shows: FICA submit (Day 4), proposal accept (Day 8), first trust balance view (Day 11), fee-note paid (Day 30), second info-req submit (Day 46), SoA download (Day 61)
- [ ] **88.5** 📸 **Screenshot**: `day-88-portal-activity-trail.png` — client-POV 90-day activity
- [ ] **88.6** Verify narrative coherence: every client-visible firm event has a matching client-side entry within ≤ 1 day delay

**Day 88 checkpoints**
- [ ] Firm and portal activity feeds each internally complete
- [ ] Semantic match across POVs — no event firm-side that should have been client-visible is missing from portal

---

## Day 90 — Final regression + exit sweep  `[FIRM]` + `[PORTAL]`

### Firm-side regression sweep

- [ ] **90.1** `[FIRM]` Terminology sweep: walk sidebar, settings pages, every create dialog → zero occurrences of "Project" (must be "Matter"), "Customer" (must be "Client"), "Invoice" (must be "Fee Note"), "Task" where legal terminology applies. Zero accounting / consulting vocabulary leaks.
- [ ] **90.2** `[FIRM]` Field promotion sweep: reopen every create dialog (Client, Matter, Task, Fee Note) → verify no promoted slugs have regressed into a generic Custom Fields section
- [ ] **90.3** `[FIRM]` Progressive disclosure: all 4 legal modules visible (Matters, Trust Accounting, Court Calendar, Conflict Check); no accounting / consulting nav items
- [ ] **90.4** `[FIRM]` Tier removal: Settings > Billing shows flat subscription state only, no tier UI
- [ ] **90.5** `[FIRM]` Console errors: browser devtools open → click through every top-level nav → zero JS errors
- [ ] **90.6** `[FIRM]` Mailpit sweep: no bounced / failed emails across 90 days

### Portal-side regression sweep

- [ ] **90.7** `[PORTAL]` Login as Sipho → walk every portal route (`/home`, `/projects`, `/invoices`, `/trust`, `/deadlines`, `/proposals`, `/profile`, `/settings/notifications`) → zero JS errors, zero 500 responses
- [ ] **90.8** `[PORTAL]` Final isolation probe: re-run Day 15 Phase B + Phase C probes against Moroka IDs → all still denied (no drift)
- [ ] **90.9** `[PORTAL]` Final digest email reviewed in Mailpit — references ONLY Sipho's activity
- [ ] **90.10** `[PORTAL]` Terminology sweep: no firm-side vocabulary leaked ("matter" ok because Sipho has a matter; "engagement" / "task" / "case file" should not appear as inconsistent synonyms for the same object)

**Day 90 checkpoints**
- [ ] Both regression sweeps pass
- [ ] Isolation holds at Day 90 (zero drift from Day 15)
- [ ] Mailpit clean — no bounced / failed emails on either firm or portal side

---

## Exit checkpoints (ALL must pass for demo-ready)

- [ ] **E.1** Every step above is checked; where a step is conditionally skipped (e.g., KYC adapter not configured) the skip is explicitly logged to the gap report with rationale
- [ ] **E.2** All 7 📸 wow moments captured without visual regression against existing Playwright baselines (from Phase 68 Epic 500B)
- [ ] **E.3** Zero BLOCKER or HIGH items in gap report (`qa/gap-reports/legal-za-full-lifecycle-{YYYY-MM-DD}.md`)
- [ ] **E.4** **Tier removal** verified on 3+ screens (Settings > Billing, team invite flow, member count page)
- [ ] **E.5** **Field promotion** verified on Client, Matter, Task, Fee Note create dialogs — no duplication in CustomFieldSection
- [ ] **E.6** **Progressive disclosure** — all 4 legal modules present, no cross-vertical leaks in sidebar, breadcrumbs, settings
- [ ] **E.7** **Keycloak flow** end-to-end — from `/request-access` → approval → owner register → team invites → logged-in firm dashboard, zero mock IDP usage
- [ ] **E.8** **Portal magic-link flow** end-to-end — Sipho authenticated via magic-link on Days 4, 8, 11, 15, 30, 46, 61, 75, 88, 90; zero Keycloak-form usage on portal side
- [ ] **E.9** **Terminology sweep** passed — zero "Project/Customer/Invoice" leaks firm-side; portal terminology consistent within itself
- [ ] **E.10** **Isolation — BLOCKER-severity gate** — Day 15 and Day 90 isolation probes both pass at list, URL, and API levels. No Moroka data leak anywhere on portal throughout 90 days.
- [ ] **E.11** **Trust accounting reconciliation** — firm-side Section 86 ledger + matter Trust tab + portal `/trust` all reconcile to identical balances at Days 11, 46, 61
- [ ] **E.12** **Fee note + payment flow** — Day 28 generation + Day 30 PayFast sandbox payment completes end-to-end; firm PAID status reflects within 60s
- [ ] **E.13** **Matter closure** — Day 60 clean-path closure (no override) + SoA generation + portal download succeed
- [ ] **E.14** **Audit trail completeness** — Day 85 audit log filters return both firm-user events and portal-contact events over the 90 days
- [ ] **E.15** **Test suite gate** (mandatory — see master doc "Test suite gate" section):
  - [ ] `cd backend && ./mvnw -B verify` → BUILD SUCCESS, zero failures, zero newly-skipped tests
  - [ ] `cd frontend && pnpm test` → all vitest suites pass
  - [ ] `cd frontend && pnpm typecheck` → zero TS errors
  - [ ] `cd frontend && pnpm lint` → zero lint errors
  - [ ] `cd portal && pnpm lint && pnpm run build` → clean
  - [ ] Every fix PR merged during this cycle satisfied the same gates before merging (not just the final run)
- [ ] **E.16** Cycle completed on one clean pass — no dev subagent dispatches mid-loop to fix BLOCKER bugs

**If any checkpoint fails**: log finding to `qa/gap-reports/legal-za-full-lifecycle-{YYYY-MM-DD}.md` using the severity/format defined in the master doc, and let `/qa-cycle-kc` dispatch a fix before re-running the failing step. **Fix PRs that do not pass the test suite gate (E.15) must NOT be merged** — either extend the fix to cover broken tests or revert and re-approach. Isolation-failure findings (E.10) are automatically BLOCKER severity regardless of scope.
