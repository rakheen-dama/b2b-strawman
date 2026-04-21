# QA Lifecycle: Portal Client-POV 90-Day (Keycloak Mode)

**Vertical profiles**: `legal-za` / `accounting-za` / `consulting-za` (mixed — one run exercises all three vertical gating branches)
**Story**: "The client quarter" — a portal contact receives a magic-link invitation, onboards, submits info requests, accepts a proposal, pays an invoice, downloads a Statement of Account / financial statement, and closes out the quarter on the client-facing portal at `http://localhost:3002`.
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025 / **portal 3002**)
**Master doc**: `qa/testplan/demo-readiness-keycloak-master.md`
**Driver**: `/qa-cycle-kc qa/testplan/demos/portal-client-90day-keycloak.md`

**Supersedes**: none — this is the first client-POV lifecycle, deliberately distinct from the firm-side 90-day spines (`legal-za-90day-keycloak.md`, `accounting-za-90day-keycloak-v2.md`, `consulting-agency-90day-keycloak.md`).

> **Scope note**: This is a **client-POV** lifecycle. Every checkpoint below is performed **on the portal** (`http://localhost:3002`) as a portal contact — **not** a firm user. The firm-side state the client reacts to (matters created, proposals issued, invoices posted, deadlines scheduled, documents generated) is assumed present via the three corresponding firm-side lifecycles, which must have been run first against the same three tenants.

---

## Driver compatibility — `/qa-cycle-kc` shims needed

`/qa-cycle-kc` was authored against the firm-side Keycloak flow and makes assumptions that do NOT hold for the portal. The QA agent driving this scenario file must apply the shims below:

1. **Port**: portal runs on **`http://localhost:3002`**, not `http://localhost:3000`. Do NOT navigate to `/dashboard` via port 3000 — portal contacts are not firm users and cannot authenticate there.
2. **Auth flow**: portal contacts authenticate via **magic-link → JWT → cookie + localStorage**, NOT the Keycloak OIDC redirect. Use the helpers at `portal/e2e/helpers/auth.ts` (`getPortalJwt(email)` → `loginAsPortalContact(page, jwt)`). The underlying backend endpoints are `POST /portal/auth/request-link` and `POST /portal/auth/exchange`. Do NOT fill a Keycloak login form for any portal step.
3. **Users**: portal contacts are seeded from firm-side client records; they are **not** `alice@example.com` / `bob@example.com` / `carol@example.com`. Use the portal-contact emails declared in the Actors table below. Environment overrides: `PORTAL_CONTACT_EMAIL`.
4. **Org slug**: default is **`e2e-test-org`** (portal helper default) — not `acme-corp`. Override with `PORTAL_ORG_SLUG` per-tenant when three-tenant parity is required.
5. **svc.sh coverage**: `bash compose/scripts/svc.sh start portal` is the canonical way to bring the portal up (already supported — no shim needed). Health check: `GET http://localhost:3002/`.

---

## Actors

| Role | Name | Portal-contact email | Tenant (org slug) | Profile |
|---|---|---|---|---|
| Client — Litigation (individual) | Sipho Dlamini | `sipho.portal@example.com` | `mathebula-partners` (or fixture `e2e-test-org`) | `legal-za` |
| Client — Deceased Estate (trust) | Moroka Family Trust | `moroka.portal@example.com` | `mathebula-partners` | `legal-za` |
| Client — SME (small business) | Zola's Bakery (Pty) Ltd | `zola.portal@example.com` | `ledger-collective` | `accounting-za` |
| Client — Retainer engagement | Thembi Nkosi | `thembi.portal@example.com` | `keystone-consulting` | `consulting-za` |
| Firm Owner (off-screen, creates state) | Thandi Mathebula / Naledi Phiri / Lindiwe Khumalo | firm-side only | respective tenant | — |

> The script uses **`sipho.portal@example.com`** as the primary "all-profile" narrator unless a checkpoint is explicitly profile-gated. Cross-tenant checkpoints (e.g. Day 30 hour-bank) switch to the tenant whose vertical exposes the feature.

## Prerequisites (firm-side state assumed present)

| Tenant | Firm-side lifecycle already run | Produces |
|---|---|---|
| `mathebula-partners` (legal-za) | `legal-za-90day-keycloak.md` Days 0–21 minimum | Matter + info requests + proposal + trust deposit |
| `ledger-collective` (accounting-za) | `accounting-za-90day-keycloak-v2.md` Days 0–60 | Engagement + deadlines + generated financial statement |
| `keystone-consulting` (consulting-za) | `consulting-agency-90day-keycloak.md` Days 0–30 | Retainer engagement + hour-bank + invoice |

## Demo wow moments (capture 📸 on clean pass)

1. **Day 0** — Portal home populated with pending info requests for a newly onboarded client (`/home`)
2. **Day 14** — Proposal accept flow success state (`/proposals/[id]` → Accept → confirmation)
3. **Day 21** — Trust balance card showing first deposit (`/trust`) `[legal-za only]`
4. **Day 30** — Hour-bank remaining + current-period consumption (`/retainer`) `[consulting-za / legal-za]`
5. **Day 45** — Pay-invoice success state (`/invoices/[id]` → Pay → receipt)
6. **Day 60** — Generated document download (`/deadlines` or `/projects` → open doc) `[legal-za SoA / accounting-za financial statement]`
7. **Day 90** — Final digest + activity trail of the quarter

---

## Session 0 — Prep & Reset

Follow `qa/testplan/demo-readiness-keycloak-master.md` → "Session 0 — Stack startup & teardown" for shared steps (M.1–M.9). In addition, for this client-POV lifecycle specifically:

- [ ] **0.A** Confirm **portal** is running on `:3002`: `curl -sS http://localhost:3002/ -o /dev/null -w "%{http_code}\n"` returns `200`. Start via `bash compose/scripts/svc.sh start portal` if not.
- [ ] **0.B** Confirm each of the three firm-side tenants has at least the minimum firm-side lifecycle slice run (see Prerequisites table above). Without this, portal pages render empty states and wow moments cannot be captured.
- [ ] **0.C** Confirm portal contacts listed in Actors exist in the backend (visible on the firm-side matter / engagement detail page as "Client contact"). If missing, create them firm-side before continuing.
- [ ] **0.D** Mailpit (`http://localhost:8025`) is reachable — Day 0, Day 7, Day 21, Day 75, Day 85, Day 90 all involve inspecting an email the portal user receives.

---

## Day 0 — Magic-link login, review pending info requests  `[all profiles]`

**Actor**: Sipho Dlamini (portal contact, `sipho.portal@example.com`) — unauthenticated at start.

### Phase A: Receive magic link, land on `/home`

- [ ] **0.1** Firm issues portal invite for Sipho (firm-side action, off-screen on portal). Confirm backend accepted the invite (200 from `POST /portal/auth/request-link` or firm-side UI success toast)
- [ ] **0.2** Open Mailpit (`http://localhost:8025`) → verify magic-link email arrived for `sipho.portal@example.com` with subject containing "sign in" or "access your portal"
- [ ] **0.3** Click the magic-link in the email → lands on portal `/accept/[token]` (public route)
- [ ] **0.4** Portal exchanges the token (`POST /portal/auth/exchange`) → redirects to `/home`
- [ ] **0.5** Verify `/home` renders: pending info requests list, upcoming deadlines list, recent invoices list all present (may be empty for two of the three — populated section depends on firm-side prerequisite state)
- [ ] **0.6** 📸 **Screenshot**: `day-00-home-landing.png` — portal home with pending info requests visible

### Phase B: Review pending info requests  `[all profiles]`

- [ ] **0.7** Home page "Pending info requests" card shows at least one item → click into it
- [ ] **0.8** Verify info-request detail view renders: title, description, requested-by, due date, upload slot
- [ ] **0.9** Verify the info request is in state **"Requested"** (or "Awaiting documents" — terminology per portal copy)
- [ ] **0.10** 📸 **Screenshot**: `day-00-info-request-detail.png`

**Day 0 checkpoints**
- [ ] Sipho successfully authenticated via magic-link (no Keycloak form, no firm-user flow)
- [ ] `/home` renders without 500 / JS console errors
- [ ] At least one pending info-request is visible and clickable

---

## Day 3 — Upload requested documents, submit info request  `[all profiles]`

**Actor**: Sipho Dlamini (logged in, returning to a half-completed info request)

- [ ] **3.1** Navigate to `/home` → click the same pending info request opened on Day 0
- [ ] **3.2** Upload a document (any PDF ≤ 2 MB) via the upload slot → verify upload progress → completion state
- [ ] **3.3** (If the request has a free-text field) fill a short response: "ID copy attached as requested"
- [ ] **3.4** Click **Submit** → verify info-request state transitions to **"Submitted"** or **"Awaiting review"**
- [ ] **3.5** Verify `/home` "Pending info requests" card no longer shows this request as pending (it may move into a "Recently submitted" sub-list)
- [ ] **3.6** 📸 **Screenshot**: `day-03-info-request-submitted.png`

**Day 3 checkpoints**
- [ ] Document upload succeeded end-to-end (backend stored, portal re-rendered with new state)
- [ ] Info-request state machine progressed correctly
- [ ] No terminology leaks: copy reads "info request" / "document", not firm-side "task" / "attachment"

---

## Day 7 — First weekly digest, click through to Deadlines  `[accounting-za / legal-za]`

> This checkpoint is **skipped for `consulting-za`** (no deadline feed surfaced in consulting profile). If the narrator persona is `thembi.portal@example.com` (consulting), switch actor to `sipho.portal@example.com` (legal) or `zola.portal@example.com` (accounting) for this day.

**Actor**: Sipho Dlamini OR Zola Mbeki (portal contact on a legal-za or accounting-za tenant)

- [ ] **7.1** Open Mailpit → verify weekly digest email arrived with subject containing "Your weekly update" (or similar). Body includes a link labelled "View deadlines" or similar
- [ ] **7.2** Click the "Deadlines" link in the digest → lands on portal `/deadlines` `[accounting-za / legal-za]`
- [ ] **7.3** Verify `/deadlines` renders: list of upcoming deadlines with title, due date, matter/engagement context, urgency indicator
- [ ] **7.4** Click into one deadline → verify detail view renders with description and any attached document(s)
- [ ] **7.5** 📸 **Screenshot**: `day-07-deadlines-list.png`

**Day 7 checkpoints**
- [ ] Digest email generated and delivered via Mailpit
- [ ] Deep-link from email → `/deadlines` preserves auth (no re-login loop)
- [ ] `/deadlines` renders non-empty for the prerequisite-seeded tenants

---

## Day 14 — Review + accept proposal  `[all profiles]`

**Actor**: Sipho Dlamini (all profiles exercise this — use the profile whose firm-side lifecycle has a proposal issued)

- [ ] **14.1** Navigate to `/proposals` → verify the list renders at least one proposal in **"Pending acceptance"** state
- [ ] **14.2** Click into the proposal → `/proposals/[id]` detail renders: scope, fee estimate / retainer terms, effective date, Accept / Decline buttons
- [ ] **14.3** 📸 **Screenshot**: `day-14-proposal-review.png` — full proposal detail
- [ ] **14.4** Click **Accept** → confirmation dialog or inline acceptance toggle
- [ ] **14.5** (If required by tenant) complete acceptance step on `/acceptance` or `/accept/[token]` (some profiles route acceptance through a dedicated flow)
- [ ] **14.6** Verify proposal status transitions to **"Accepted"** → acceptance timestamp + actor recorded
- [ ] **14.7** 📸 **Screenshot**: `day-14-proposal-accepted.png` — success / confirmation state
- [ ] **14.8** Navigate back to `/home` → verify proposal no longer appears in "Pending acceptance" surface

**Day 14 checkpoints**
- [ ] Proposal acceptance recorded (firm-side can verify via their proposals page)
- [ ] No double-accept bug: clicking Accept again shows already-accepted state, not a second state transition
- [ ] Terminology: portal copy reads "proposal" consistently (not "SOW" / "engagement letter" in the same flow)

---

## Day 21 — Trust deposit nudge, view balance  `[legal-za only]`

**Actor**: Sipho Dlamini (legal-za tenant only; skip for accounting-za and consulting-za)

- [ ] **21.1** Open Mailpit → verify trust-deposit nudge email arrived (subject contains "trust deposit" or "funds received")
- [ ] **21.2** Click the "View trust balance" link → lands on `/trust`
- [ ] **21.3** Verify `/trust` renders: trust balance summary for Sipho's matter, recent deposits, ledger preview
- [ ] **21.4** Verify the first deposit recorded firm-side is visible with correct amount + date
- [ ] **21.5** Click into the matter's trust ledger → verify line-level transaction history renders
- [ ] **21.6** 📸 **Screenshot**: `day-21-trust-balance.png`

**Day 21 checkpoints**
- [ ] `/trust` route renders only for a legal-za client with trust ledger entries — other profiles should 404 or show empty state
- [ ] Amount shown matches firm-side Trust Accounting page for the same matter
- [ ] Currency is ZAR (legal-za default)

---

## Day 30 — Hour-bank remaining + current-period consumption  `[consulting-za / legal-za]`

**Actor**: Thembi Nkosi (consulting-za) OR Sipho Dlamini (legal-za with hour-based retainer)

- [ ] **30.1** Navigate to `/retainer` → verify retainer overview renders for at least one engagement/matter
- [ ] **30.2** Verify hour-bank remaining shows (e.g., "18.5 of 40 hours remaining this period")
- [ ] **30.3** Verify current-period consumption card shows: hours consumed, by whom (optional), most-recent work-log entries
- [ ] **30.4** Verify the period boundary (start / end) is visible and correct per the firm-side retainer setup
- [ ] **30.5** 📸 **Screenshot**: `day-30-retainer-hour-bank.png`
- [ ] **30.6** For `legal-za` variant: navigate to `/projects/[matterId]` → verify matter-level retainer summary also renders

**Day 30 checkpoints**
- [ ] Hour-bank math matches firm-side retainer consumption report
- [ ] `/retainer` shows **empty state**, not a 500, for an `accounting-za` contact (profile-gating check)
- [ ] Period boundary displayed in ISO format or localised ZA format — consistent across both variants

---

## Day 45 — Pay first invoice via portal  `[all profiles]`

**Actor**: Sipho Dlamini (or profile-specific narrator)

- [ ] **45.1** Navigate to `/invoices` → verify at least one invoice in **"Sent"** or **"Due"** state
- [ ] **45.2** Click into an invoice → `/invoices/[id]` renders: line items, subtotal, VAT, total, due date, Pay button
- [ ] **45.3** 📸 **Screenshot**: `day-45-invoice-detail.png`
- [ ] **45.4** Click **Pay** → portal payment flow opens (PSP redirect OR embedded payment sheet — depends on implementation)
- [ ] **45.5** Complete payment flow using the seeded sandbox PSP credentials (e.g. "4242 4242 4242 4242" for Stripe-like test card, or the test flag for the portal's current PSP)
- [ ] **45.6** Payment succeeds → invoice status transitions to **"Paid"** → receipt available for download
- [ ] **45.7** 📸 **Screenshot**: `day-45-invoice-paid.png` — success / receipt state
- [ ] **45.8** Navigate back to `/invoices` → verify invoice no longer in "Due" filter, now in "Paid"

**Day 45 checkpoints**
- [ ] Payment recorded firm-side (firm-side invoice page reflects PAID within 60 s)
- [ ] No tier / upsell interstitial in the pay flow
- [ ] Receipt download works (PDF opens or downloads cleanly)

---

## Day 60 — Download Statement of Account / financial statement  `[legal-za / accounting-za]`

**Actor**: Sipho (legal-za SoA) OR Zola (accounting-za financial statement)

- [ ] **60.1** Firm-side has generated:
  - `[legal-za]` Statement of Account (Phase 67 Epic 487) for Sipho's matter
  - `[accounting-za]` financial statement (annual or period) for Zola's Bakery
- [ ] **60.2** Navigate to `/projects/[matterId]` (legal) OR `/projects/[engagementId]` (accounting) → Documents tab
- [ ] **60.3** Verify the generated document appears in the document list with correct title, date, size
- [ ] **60.4** Click **Download** → verify PDF downloads successfully and opens without error
- [ ] **60.5** Open the downloaded PDF → verify it contains:
  - `[legal-za]` Firm letterhead (Mathebula), matter reference, SoA opening balance, line items, closing balance, VAT 15%
  - `[accounting-za]` Firm letterhead (Ledger Collective), period heading, balance sheet / income statement rows
- [ ] **60.6** 📸 **Screenshot**: `day-60-document-downloaded.png` — portal document list with download success indicator

**Day 60 checkpoints**
- [ ] Download byte-size matches firm-side preview byte-size (±5%)
- [ ] Document title exactly matches firm-side copy (no "Untitled document" leak)
- [ ] `consulting-za` contact has a different document set (SoW / status report) or an empty Documents tab — either is acceptable; check for no 500s

---

## Day 75 — Deadline-approaching nudge → mark read → download related doc  `[accounting-za / legal-za]`

**Actor**: Zola (accounting-za) OR Sipho (legal-za)

- [ ] **75.1** Open Mailpit → verify deadline-approaching nudge email (subject contains "deadline" or "due in 7 days" or "reminder")
- [ ] **75.2** Click through → lands on `/deadlines` → the approaching deadline is highlighted / flagged
- [ ] **75.3** Click into the deadline → verify detail view with due date, related document(s), any instructions
- [ ] **75.4** Mark the nudge as **read** (click bell icon / "Mark read" button / dismiss toast — depends on UI)
- [ ] **75.5** Verify the nudge no longer shows unread indicator on `/home` or navbar
- [ ] **75.6** Download the related document from the deadline detail view → PDF opens successfully
- [ ] **75.7** 📸 **Screenshot**: `day-75-deadline-nudge-read.png`

**Day 75 checkpoints**
- [ ] Read-state persists across page reload (not just a transient UI state)
- [ ] Download audit event recorded (firm-side audit log shows the portal contact accessed the doc)
- [ ] `consulting-za` contact either does not receive this nudge type, or receives a different engagement-level nudge — verify no crash either way

---

## Day 85 — Update profile, change digest cadence to biweekly  `[all profiles]`

**Actor**: Sipho Dlamini (all profiles exercise this)

- [ ] **85.1** Navigate to `/profile` → verify profile form renders with editable fields (name, phone, preferred contact method, etc.)
- [ ] **85.2** Update one field (e.g. append " (updated Day 85)" to display name) → Save → verify success toast + persisted value on reload
- [ ] **85.3** Navigate to `/settings/notifications` → verify digest cadence control renders with current value = **"weekly"**
- [ ] **85.4** Change digest cadence to **"biweekly"** → Save → verify success toast
- [ ] **85.5** Reload → verify cadence persisted as "biweekly"
- [ ] **85.6** 📸 **Screenshot**: `day-85-notifications-biweekly.png`

**Day 85 checkpoints**
- [ ] Profile update propagates to backend (firm-side contact record reflects the new display name)
- [ ] Notification cadence persists across logout/login
- [ ] No cross-profile leak (legal-za and accounting-za copies match; terminology consistent)

---

## Day 90 — Final digest, review activity trail  `[all profiles]`

**Actor**: Sipho Dlamini (or whichever narrator completes the quarter)

- [ ] **90.1** Open Mailpit → verify final / quarter-summary digest email arrived. Subject contains "quarter summary" or "your activity" or "Q1 recap". Body summarises the period (proposals accepted, invoices paid, documents downloaded)
- [ ] **90.2** Click through from the digest → lands on `/home` (or `/profile` → Activity, depending on portal surface)
- [ ] **90.3** Navigate to the activity trail view (may be `/home` scrolled to activity section, or `/profile/activity` — use whichever the portal exposes)
- [ ] **90.4** Verify the activity trail shows **at least** the key events from prior days: Day 3 submit, Day 14 accept, Day 45 pay, Day 60 download, Day 75 mark-read, Day 85 profile update
- [ ] **90.5** Verify each entry has a timestamp and (where applicable) a deep-link back to the original context
- [ ] **90.6** 📸 **Screenshot**: `day-90-activity-trail.png`

**Day 90 checkpoints**
- [ ] Quarter digest body matches the activity trail (no "missing event" discrepancy between email and in-app view)
- [ ] Terminology sweep: activity trail uses client-facing vocabulary consistently (e.g. "proposal accepted", not firm-side "offer converted")
- [ ] No 500 / JS console errors across the full lifecycle walkthrough on a single browser session

---

## Exit checkpoints (ALL must pass for demo-ready)

- [ ] **E.1** Every step above is checked across the three exercised profiles (or clearly marked N/A with rationale for profile-gated skips)
- [ ] **E.2** All 7 📸 wow moments captured without visual regression against the Playwright baselines (once 500B populates them)
- [ ] **E.3** Zero BLOCKER or HIGH items in gap report (`tasks/phase68-gap-report.md` — produced in slice 500B)
- [ ] **E.4** **Profile gating** verified — `/trust` is legal-za only, `/retainer` is consulting-za + legal-za only, `/deadlines` is accounting-za + legal-za only; non-eligible profiles show empty state, NOT 500
- [ ] **E.5** **Auth flow** — every portal interaction authenticated via magic-link → JWT (cookie + localStorage); zero Keycloak-form usage, zero firm-side user bleed-through
- [ ] **E.6** **Terminology sweep** — zero firm-side vocabulary leaks on portal pages ("matter" / "project" / "engagement" consistent per tenant profile; no "customer" / "client" inversion)
- [ ] **E.7** **Three-tenant coverage** — every `[all profiles]` checkpoint validated on at least two of the three tenants; every gated checkpoint validated on each applicable tenant
- [ ] **E.8** **Email linkage** — every email-driven checkpoint (Day 0, Day 7, Day 21, Day 75, Day 90) successfully authenticates the follow-through click without re-login
- [ ] **E.9** **Payment flow** (Day 45) completes end-to-end with a sandbox PSP credential; receipt downloads cleanly
- [ ] **E.10** **Visual regression** (once baselines exist in 500B) — diff pixel-ratio ≤ 1% across all 🗎 captured shots at `lg` viewport
- [ ] **E.11** **Test suite gate** (mandatory — see master doc "Test suite gate" section):
  - [ ] `cd portal && pnpm lint` → zero lint errors
  - [ ] `cd portal && pnpm run build` → TypeScript clean
  - [ ] `cd portal && pnpm exec playwright test --list --config=playwright.portal.config.ts` → 4 new specs enumerated without parse errors
  - [ ] Full spec execution + baseline capture deferred to slice **500B** (tasks 500.6–500.7)

**If any checkpoint fails**: log finding to `tasks/phase68-gap-report.md` (produced in slice 500B) using the severity/format defined in the master doc, and let `/qa-cycle-kc` dispatch a fix before re-running the failing step. **Fix PRs that do not pass the test suite gate (E.11) must NOT be merged** — either extend the fix to cover broken tests or revert and re-approach.
