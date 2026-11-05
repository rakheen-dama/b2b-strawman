# QA Lifecycle: Portal Client-POV 90-Day (Keycloak Mode)

**Vertical profiles**: `legal-za`
**Story**: "The client quarter" — a portal contact receives a magic-link invitation, onboards, submits info requests, pays an invoice, downloads a Statement of Account / financial statement, and closes out the quarter on the client-facing portal at `http://localhost:3002`.
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025 / **portal 3002**)
**Master doc**: `qa/testplan/demo-readiness-keycloak-master.md`
**Driver**: `/qa-cycle-kc qa/testplan/demos/portal-client-90day-keycloak.md`

**Supersedes**: none — this is the first client-POV lifecycle, deliberately distinct from the firm-side 90-day spines (`legal-za-90day-keycloak.md`).

> **Scope note**: This is a **client-POV** lifecycle. Every checkpoint below is performed **on the portal** (`http://localhost:3002`) as a portal contact — **not** a firm user. The firm-side state the client reacts to (matters created, invoices posted, deadlines scheduled, documents generated) is assumed present via the three corresponding firm-side lifecycles, which must have been run first against the same three tenants.

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
| Firm Owner (off-screen, creates state) | Thandi Mathebula / Naledi Phiri / Lindiwe Khumalo | firm-side only | respective tenant | — |

> The script uses **`sipho.portal@example.com`** as the primary "all-profile" narrator unless a checkpoint is explicitly profile-gated. Cross-tenant checkpoints switch to the tenant whose vertical exposes the feature.

## Prerequisites (firm-side state assumed present)

| Tenant | Firm-side lifecycle already run | Produces |
|---|---|---|
| `mathebula-partners` (legal-za) | `legal-za-90day-keycloak.md` Days 0–21 minimum | Matter + info requests + trust deposit |

## Demo wow moments (capture 📸 on clean pass)

1. **Day 0** — Portal home populated with pending info requests for a newly onboarded client (`/home`)
2. **Day 21** — Trust balance card showing first deposit (`/trust`) `[legal-za only]`
3. **Day 45** — Pay-invoice success state (`/invoices/[id]` → Pay → receipt)
4. **Day 60** — Generated document download (`/deadlines` or `/matters` → open doc) `[legal-za SoA]` (Phase 87, ADR-344 — URL amendment, Epic 617B, PR #1640: portal route /matters (617A))
5. **Day 90** — Activity trail of the quarter (Phase 89 decision 7 / G88-F34 — dropped, founder-authorised 2026-11-03: there is no final / quarter digest; only the weekly Monday digest exists, `PortalDigestScheduler.java:106`)

---

## Session 0 — Prep & Reset

Follow `qa/testplan/demo-readiness-keycloak-master.md` → "Session 0 — Stack startup & teardown" for shared steps (M.1–M.9). In addition, for this client-POV lifecycle specifically:

- [ ] **0.A** Confirm **portal** is running on `:3002`: `curl -sS http://localhost:3002/ -o /dev/null -w "%{http_code}\n"` returns `307` (the portal redirects `/` to `/home`); with `curl -sSL` the final code is `200`. Start via `bash compose/scripts/svc.sh start portal` if not. (Phase 89 decision 7 / G88-F1 — wording amendment, founder-authorised 2026-11-03: `portal/middleware.ts:4-19` redirects `/` → `/home`)
- [ ] **0.B** Confirm each of the three firm-side tenants has at least the minimum firm-side lifecycle slice run (see Prerequisites table above). Without this, portal pages render empty states and wow moments cannot be captured.
- [ ] **0.C** Confirm each client has a portal contact: portal contacts are auto-provisioned from the client's email when the firm creates the client — there is no firm-side create control and no "Client contact" label — so record them with a read-only SELECT on `portal_contacts` (Sipho's is the client email from the 90-day file's 1.7) (Phase 89 decision 7 / G88-F37 — amended, not built, founder-authorised 2026-11-03: `PortalContactAutoProvisioner.java:42-46` provisions on `CustomerCreatedEvent`; no "Client contact" label in `frontend/`)
- [ ] **0.D** Mailpit (`http://localhost:8025`) is reachable — Day 0, Day 7, Day 21, Day 75, Day 85 all involve inspecting an email the portal user receives. (Phase 89 decision 7 / G88-F34 — dropped, founder-authorised 2026-11-03: Day 90's final digest does not exist)

---

## Day 0 — Magic-link login, review pending info requests  `[all profiles]`

**Actor**: Sipho Dlamini (portal contact, `sipho.portal@example.com`) — unauthenticated at start.

### Phase A: Receive magic link, land on `/home`

- [ ] **0.1** Sipho requests his own sign-in link: in a fresh context open `http://localhost:3002/login` → enter his email → click **Send Magic Link** → the page reads "Check your email." (the firm app has no portal-invite control) (Phase 89 decision 7 / G88-F1 — wording amendment, founder-authorised 2026-11-03: `portal/app/login/page.tsx:154,239`; the firm-side magic-link generator was removed in Epic 597 (#1566), as the 90-day file's 68.1 notes)
- [ ] **0.2** Open Mailpit (`http://localhost:8025`) → verify magic-link email arrived for Sipho's client email (`sipho.dlamini@email.co.za`, the 90-day file's 1.7 — see 0.C) with subject "Your portal access link from Mathebula & Partners" (body heading "Access Your Portal", button **Access Portal**) (Phase 89 decision 7 / G88-F1 — wording amendment, founder-authorised 2026-11-03: `PortalEmailService.java:109`)
- [ ] **0.3** Click **Access Portal** in the email → lands on portal `/auth/exchange?token=…` (public route) (Phase 89 decision 7 / G88-F1 — wording amendment, founder-authorised 2026-11-03: `/accept/[token]` is the document-acceptance page; the sign-in link targets `/auth/exchange`, as the 90-day file's 68.1 already reads)
- [ ] **0.4** Portal exchanges the token (`POST /portal/auth/exchange`) → redirects to `/matters` ("Your matters"); open **Home** (`/home`) from the portal nav (Phase 89 decision 7 / G88-F1 — wording amendment, founder-authorised 2026-11-03: `auth/exchange/page.tsx:74,84` defaults the target to `/matters`, the same behaviour register §5 row 13 recorded for the 90-day file's 68.1)
- [ ] **0.5** Verify `/home` renders: the **Pending info requests** card (a count, linking to `/requests`), the **Upcoming deadlines** card (a count for the next 14 days, linking to `/deadlines`) and the **Recent fee notes** list, all present (any may be empty or zero — populated sections depend on firm-side prerequisite state) (Phase 89 decision 7 / G88-F37 — amended, not built, founder-authorised 2026-11-03: the two cards render a count, not an item list, `home/page.tsx:57-99,139-174`; fee notes are a list, `:176-231`)
- [ ] **0.6** 📸 **Screenshot**: `day-00-home-landing.png` — portal home with pending info requests visible

### Phase B: Review pending info requests  `[all profiles]`

- [ ] **0.7** Home page "Pending info requests" card shows a count of at least 1 → click it → `/requests` lists the request (REQ-0001 from the 90-day file's 7.1) → click its row (Phase 89 decision 7 / G88-F37 — amended, not built, founder-authorised 2026-11-03: the card is a count that links to the list, `home/page.tsx:82,93`)
- [ ] **0.8** Verify info-request detail view renders: title, the requested item's name and description, upload slot (the view shows no requested-by or due date) (Phase 89 decision 7 / G88-F37 — amended, not built, founder-authorised 2026-11-03: `requests/[id]/page.tsx:94-117,170-218`)
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
- [ ] **3.2** Choose a document (any PDF ≤ 2 MB) in the item's upload slot — nothing uploads yet; selecting the file only enables **Upload and submit** (the portal shows no upload progress) (Phase 89 decision 7 / G88-F37 — amended, not built, founder-authorised 2026-11-03: file input + disabled-until-chosen button, `requests/[id]/page.tsx:203-216`)
- [ ] **3.3** (If the request has a free-text field) fill a short response: "ID copy attached as requested"
- [ ] **3.4** Click **Upload and submit** (upload and submit are one action; the button reads "Uploading…" while it works, then the item reads **Submitted**) → verify info-request state transitions to **"Submitted"** or **"Awaiting review"** (Phase 89 decision 7 / G88-F1 — wording amendment, founder-authorised 2026-11-03: the click uploads then submits, `requests/[id]/page.tsx:140-167,209-216`)
- [ ] **3.5** Verify `/home` "Pending info requests" card no longer shows this request as pending (it may move into a "Recently submitted" sub-list)
- [ ] **3.6** 📸 **Screenshot**: `day-03-info-request-submitted.png`

**Day 3 checkpoints**
- [ ] Document upload succeeded end-to-end (backend stored, portal re-rendered with new state)
- [ ] Info-request state machine progressed correctly
- [ ] No terminology leaks: copy reads "info request" / "document", not firm-side "task" / "attachment"

---

## Day 7 — First weekly digest, click through to Diary dates (`/deadlines`)  `[legal-za]` (Phase 87, ADR-344 — label amendment, Epic 617B, PR #1640: portal nav label Deadlines → Diary dates, path kept (617A))

**Actor**: Sipho Dlamini — portal contact on a legal-za tenant

- [ ] **7.1** Open Mailpit → verify weekly digest email arrived with subject containing "Your weekly update" (or similar). Body includes an **Open portal** button (links to `/home`) (Phase 88, register §5a row 16 — wording amendment, founder-authorised 2026-10-29: the digest's only CTA is Open portal → `/home`, `portal-weekly-digest.html:72-73`, `PortalDigestScheduler.java:305`)
- [ ] **7.2** Click **Open portal** in the digest → lands on portal `/home`; open **Diary dates** (`/deadlines`) from the portal nav `[legal-za]` (§5a row 16, founder-authorised 2026-10-29)
- [ ] **7.3** Verify `/deadlines` renders: list of upcoming deadlines with title, due date, matter/engagement context, urgency indicator — court dates and prescriptions do not yet sync to the portal, so until that sync is built the list is empty unless a portal-visible custom-field date exists (Phase 88, register §5a row 16 — wording amendment, founder-authorised 2026-10-29: those `DeadlinePortalSyncService` sources are dormant, only `CUSTOM_FIELD_DATE` is wired, `DeadlinePortalSyncService.java:36-49`)
- [ ] **7.4** Click into one deadline → verify detail view renders with description and any attached document(s) — **N/A** when 7.3's list is empty (no portal-visible custom-field date exists) (§5a row 16, founder-authorised 2026-10-29)
- [ ] **7.5** 📸 **Screenshot**: `day-07-deadlines-list.png`

**Day 7 checkpoints**
- [ ] Digest email generated and delivered via Mailpit
- [ ] Link from email → `/home` preserves auth (no re-login loop); `/deadlines` reached from the nav (§5a row 16, founder-authorised 2026-10-29)
- [ ] `/deadlines` renders (empty unless a portal-visible custom-field date exists, until the portal deadline sync is built) (§5a row 16, founder-authorised 2026-10-29)

---

## Day 14 — removed (Phase 86)

> The portal proposal review/accept flow was removed in Phase 86 (Epic 597, ADR-336). Steps 14.x
> are intentionally absent.

---

## Day 21 — Trust deposit nudge, view balance  `[legal-za only]`

**Actor**: Sipho Dlamini (legal-za tenant only)

- [ ] **21.1** Open Mailpit → verify trust-deposit nudge email arrived (subject "Mathebula & Partners: Trust account activity" — one subject for every trust movement) (Phase 89 decision 7 / G88-F1 — wording amendment, founder-authorised 2026-11-03: `PortalEmailNotificationChannel.java:280,312`)
- [ ] **21.2** Click **View trust ledger** in the email → lands on the matter's trust ledger (`/trust/<matterId>`); open **Trust** (`/trust`) from the portal nav for 21.3 (Phase 89 decision 7 / G88-F1 — wording amendment, founder-authorised 2026-11-03: `portal-trust-activity.html:35`; the link targets `/trust/<matterId>` when the transaction has a matter, `PortalEmailNotificationChannel.java:290-298`)
- [ ] **21.3** Verify `/trust` renders: trust balance summary for Sipho's matter, recent deposits, ledger preview
- [ ] **21.4** Verify the first deposit recorded firm-side is visible with correct amount + date
- [ ] **21.5** Click into the matter's trust ledger → verify line-level transaction history renders
- [ ] **21.6** 📸 **Screenshot**: `day-21-trust-balance.png`

**Day 21 checkpoints**
- [ ] `/trust` route renders only for a legal-za client with trust ledger entries — other profiles should 404 or show empty state
- [ ] Amount shown matches the firm-side **Trust** zone (**Overview**) for the same matter (Phase 87, ADR-344 — nav amendment, Epic 617B, PR #1640: firm Trust Accounting → Trust zone (612A))
- [ ] Currency is ZAR (legal-za default)

---

## Day 45 — Pay first invoice via portal  `[all profiles]`

**Actor**: Sipho Dlamini (or profile-specific narrator)

- [ ] **45.1** Navigate to `/invoices` → verify at least one invoice in **"Sent"** state with a payment URL (there is no "Due" status; **Pay** shows only for SENT / PARTIALLY_PAID invoices with a `paymentUrl`) (§5a row 17, founder-authorised 2026-10-29)
- [ ] **45.2** Click into an invoice → `/invoices/[id]` renders: line items, subtotal, VAT, total, due date, Pay button
- [ ] **45.3** 📸 **Screenshot**: `day-45-invoice-detail.png`
- [ ] **45.4** Click **Pay** → redirects to the mock payment gateway's checkout page (`/portal/dev/mock-payment`; local/keycloak profiles) (Phase 88, register §5a row 17 — wording amendment, founder-authorised 2026-10-29: the local/keycloak profiles run `MockPaymentGateway` (`MockPaymentGateway.java:34`) and `MockPaymentController` (`MockPaymentController.java:40`); the portal shows Pay only for a SENT note with a payment URL, `invoices/[id]/page.tsx:90-92`; Payments mandate exemption)
- [ ] **45.5** Complete payment on the mock payment gateway (**Simulate Successful Payment**) — there are no seeded sandbox PSP credentials; on the e2e stack (no PSP) mark 45.4–45.8 **N/A** (Payments mandate exemption) (§5a row 17, founder-authorised 2026-10-29)
- [ ] **45.6** Payment succeeds → invoice status transitions to **"Paid"** → receipt available for download (N/A on the e2e stack — no PSP) (§5a row 17, founder-authorised 2026-10-29)
- [ ] **45.7** 📸 **Screenshot**: `day-45-invoice-paid.png` — success / receipt state (N/A on the e2e stack — no PSP) (§5a row 17, founder-authorised 2026-10-29)
- [ ] **45.8** Navigate back to `/invoices` → verify the fee note's row Status reads **Paid** (the list is a single table with no Due/Paid filter) (N/A on the e2e stack — no PSP) (§5a row 17, founder-authorised 2026-10-29) (Phase 89 decision 7 / G88-F37 — amended, not built, founder-authorised 2026-11-03: `invoices/page.tsx` renders one table, Fee Note # / Status / Issue Date / Due Date / Total / Actions, with no filter control)

**Day 45 checkpoints**
- [ ] Payment recorded firm-side (firm-side invoice page reflects PAID within 60 s) (N/A on the e2e stack — no PSP; §5a row 17)
- [ ] No tier / upsell interstitial in the pay flow (N/A on the e2e stack — no PSP; §5a row 17)
- [ ] Receipt download works (PDF opens or downloads cleanly) (N/A on the e2e stack — no PSP; §5a row 17)

---

## Day 60 — Download Statement of Account / financial statement  `[legal-za]`

**Actor**: Sipho (legal-za SoA)

- [ ] **60.1** Firm-side has generated:
  - `[legal-za]` Statement of Account (Phase 67 Epic 487) for Sipho's matter
- [ ] **60.2** Navigate to `/matters/[matterId]` (legal) → **Documents** section of the matter page (Phase 87, ADR-344 — URL amendment, Epic 617B, PR #1640: portal route /matters (617A)) (Phase 88, register §5a row 18 — wording amendment, founder-authorised 2026-10-29: the portal matter page has no tabs; documents are a section headed Documents, `matters/[id]/page.tsx:196-202`)
- [ ] **60.3** Verify the generated document appears in the document list with correct title, date, size
- [ ] **60.4** Click **Download** → verify PDF downloads successfully and opens without error
- [ ] **60.5** Open the downloaded PDF → verify it contains:
  - `[legal-za]` Firm letterhead (Mathebula), matter reference, SoA opening balance, line items, closing balance, VAT 15%
- [ ] **60.6** 📸 **Screenshot**: `day-60-document-downloaded.png` — the Documents section of the matter page after the download (the file opens in a new tab; there is no download success indicator) (§5a row 18, founder-authorised 2026-10-29) (Phase 89 decision 7 / G88-F37 — amended, not built, founder-authorised 2026-11-03: `document-list.tsx:32-35` opens the presigned URL in a new tab)

**Day 60 checkpoints**
- [ ] Download byte-size matches firm-side preview byte-size (±5%)
- [ ] Document title exactly matches firm-side copy (no "Untitled document" leak)

---

## Day 75 — Deadline-approaching nudge → deadline detail → download related doc  `[legal-za]` (Phase 89 decision 7 / §5b row 19 — mark-read dropped, founder-authorised 2026-11-03: the portal has no mark-read control (ADR-363); 75.4–75.5 and the read-state checkpoint are removed; the deadline steps stay — court dates and prescriptions now sync to `/deadlines` (633B/633C))

**Actor**: Sipho (legal-za)

- [ ] **75.1** Open Mailpit → verify deadline-approaching nudge email (subject contains "deadline" or "due in 7 days" or "reminder")
- [ ] **75.2** Click through → lands on `/deadlines` → the approaching deadline is highlighted / flagged
- [ ] **75.3** Click into the deadline → the **Deadline details** panel shows Label (a court date reads "<type> — <court>", e.g. "Hearing — Gauteng High Court"; a prescription reads "Prescription deadline"), Due date, Status, Description (a court date's court reference only) and Source — never the judge / magistrate, the outcome or the firm's notes (Phase 89 decision 7 / §5b row 19 — sanitised copy, founder-authorised 2026-11-03: ADR-363 copy rule, `DeadlinePortalSyncService.java:433-460`; panel fields `deadline-detail-panel.tsx:124-172`)
- [ ] **75.4** Removed — the portal has no control that marks a deadline nudge read (Phase 89 decision 7 / §5b row 19 — mark-read dropped, founder-authorised 2026-11-03: ADR-363 "No mark-read control"; `deadline-detail-panel.tsx:83-172` has none)
- [ ] **75.5** Removed — with no mark-read there is no unread state to clear (Phase 89 decision 7 / §5b row 19 — mark-read dropped, founder-authorised 2026-11-03: see 75.4)
- [ ] **75.6** Download the related document from the deadline detail view → PDF opens successfully
- [ ] **75.7** 📸 **Screenshot**: `day-75-deadline-nudge-read.png`

**Day 75 checkpoints**
- [ ] Download audit event recorded (firm-side audit log shows the portal contact accessed the doc)

---

## Day 85 — View profile (read-only), check the firm's digest cadence (read-only)  `[all profiles]` (Phase 88, ADR-344 + register §5 row 15 — wording amendment, founder-authorised 2026-10-22: the portal shows the firm's digest cadence read-only; the firm sets it) (Phase 89 decision 7 / §5b row 20 — profile read-only, founder-authorised 2026-11-03: the firm owns contact data)

**Actor**: Sipho Dlamini (all profiles exercise this)

- [ ] **85.1** Navigate to `/profile` → verify the profile renders read-only: **Name**, **Email**, **Role**, **Client** (a description list, no form fields) (Phase 89 decision 7 / §5b row 20 — profile read-only, founder-authorised 2026-11-03: `profile/page.tsx:129-153` (dt/dd only); `PortalProfileController.java:16,26` (GET only))
- [ ] **85.2** Verify `/profile` offers no edit control and no Save button — the firm owns the contact's data and changes it firm-side (Phase 89 decision 7 / §5b row 20 — profile editing dropped, founder-authorised 2026-11-03: `PortalProfileController.java:16,26` exposes GET `/portal/me` only)
- [ ] **85.3** Navigate to `/settings/notifications` → the **Email notifications** card states the firm's cadence as read-only text, e.g. "Your firm sends weekly digests." (or bi-weekly / turned off, per the firm's setting) (Phase 88, ADR-344 + register §5 row 15 — wording amendment, founder-authorised 2026-10-22: `portal/app/(authenticated)/settings/notifications/page.tsx:79-87,278-280`)
- [ ] **85.4** Verify the client has no cadence control (only on/off switches such as **Weekly digest**); the firm sets the cadence under firm-side **Settings › Firm › General** → **Client Portal** → **Portal digest cadence** (Weekly / Bi-weekly / Off) (Phase 88, ADR-344 + register §5 row 15 — wording amendment, founder-authorised 2026-10-22: `frontend/components/settings/portal-settings-section.tsx:16-20,74`)
- [ ] **85.5** Reload → the cadence line still matches the firm's setting (e.g. "Your firm sends bi-weekly digests." when the firm chose Bi-weekly) (Phase 88, ADR-344 + register §5 row 15 — wording amendment, founder-authorised 2026-10-22: read-only on the portal)
- [ ] **85.6** 📸 **Screenshot**: `day-85-notifications-biweekly.png`

**Day 85 checkpoints**
- [ ] Profile shows Name / Email / Role / Client read-only (Phase 89 decision 7 / §5b row 20 — edit and propagation dropped, founder-authorised 2026-11-03: the portal profile is read-only; the firm owns contact data)
- [ ] The firm's cadence line reads the same after logout/login (read-only on the portal) (Phase 88, ADR-344 + register §5 row 15 — wording amendment, founder-authorised 2026-10-22: the cadence is a firm setting)

---

## Day 90 — Review activity trail  `[all profiles]` (Phase 89 decision 7 / G88-F34 — final / quarter digest dropped, founder-authorised 2026-11-03: only the weekly Monday digest exists, `PortalDigestScheduler.java:106`, template `portal-weekly-digest.html`; 90.1–90.2 and the digest checkpoint are removed)

**Actor**: Sipho Dlamini (or whichever narrator completes the quarter)

- [ ] **90.1** Removed — the portal sends no final / quarter-summary email (Phase 89 decision 7 / G88-F34 — dropped, founder-authorised 2026-11-03: `PortalDigestScheduler.java:106` is the weekly digest only)
- [ ] **90.2** Removed — there is no Day-90 digest to click through from; 90.3 opens the activity trail directly (Phase 89 decision 7 / G88-F34 — dropped, founder-authorised 2026-11-03)
- [ ] **90.3** Navigate to the activity trail view (may be `/home` scrolled to activity section, or `/profile/activity` — use whichever the portal exposes)
- [ ] **90.4** Verify the activity trail shows **at least** the key events from prior days: Day 3 submit, Day 45 pay, Day 60 download (Phase 89 decision 7 / §5b rows 19–20 — "Day 75 mark-read" and "Day 85 profile update" dropped, founder-authorised 2026-11-03: no portal mark-read control; the portal profile is read-only)
- [ ] **90.5** Verify each entry shows its summary, the actor ("You" or the firm member) and a relative timestamp (entries are not links back to the original context) (Phase 89 decision 7 / G88-F37 — amended, not built, founder-authorised 2026-11-03: `activity/page.tsx:133-152` renders summary, actor and relative date only)
- [ ] **90.6** 📸 **Screenshot**: `day-90-activity-trail.png`

**Day 90 checkpoints**
- [ ] Terminology sweep: activity trail uses client-facing vocabulary consistently
- [ ] No 500 / JS console errors across the full lifecycle walkthrough on a single browser session

---

## Exit checkpoints (ALL must pass for demo-ready)

- [ ] **E.1** Every step above is checked across the three exercised profiles (or clearly marked N/A with rationale for profile-gated skips)
- [ ] **E.2** All 5 📸 wow moments captured; the walker reviews every captured frame for visual defects (no committed PNG baselines) (Phase 89 decision 7 / P-E.2b — baseline reading retired, founder-authorised 2026-11-03: the walker reviews the captured frames; portal visual baselines are `.gitkeep` only, `portal/e2e/screenshots/portal-v2/**`)
- [ ] **E.3** Zero BLOCKER or HIGH items in the walk's gap register (`gaps.md` in the walk's evidence folder, e.g. `qa_cycle/evidence/phase89-rewalk/gaps.md`) (Phase 89 decision 7 / G88-F1 — wording amendment, founder-authorised 2026-11-03: `tasks/phase68-gap-report.md` is a Phase 68 artefact; the same register path as the 90-day file's E.3)
- [ ] **E.4** **Profile gating** verified — `/trust` is legal-za only, `/deadlines` is legal-za only; non-eligible profiles show empty state, NOT 500
- [ ] **E.5** **Auth flow** — every portal interaction authenticated via magic-link → JWT (held in localStorage; the portal sets no auth cookie); zero Keycloak-form usage, zero firm-side user bleed-through (Phase 89 decision 7 / G88-F1 — wording amendment, founder-authorised 2026-11-03: `portal/lib/auth.ts:1-11`, ADR-077)
- [ ] **E.6** **Terminology sweep** — zero firm-side vocabulary leaks on portal pages ("matter" / "project" / "engagement" consistent per tenant profile; no "customer" / "client" inversion)
- [ ] **E.8** **Email linkage** — every email-driven checkpoint (Day 0, Day 7, Day 21, Day 75) successfully authenticates the follow-through click without re-login (Phase 89 decision 7 / G88-F34 — Day 90 dropped, founder-authorised 2026-11-03: no final digest email)
- [ ] **E.9** **Payment flow** (Day 45) completes end-to-end through the mock payment gateway (local/keycloak profiles); receipt downloads cleanly — N/A on the e2e stack (no PSP; Payments mandate exemption) (Phase 88, register §5a row 17 — wording amendment, founder-authorised 2026-10-29: `MockPaymentGateway.java:34` is profile-gated to local/dev/keycloak/test; there are no sandbox PSP credentials)
- [ ] **E.10** **Visual review** — the walker reviews every captured 📸 frame at the `lg` viewport for layout defects (no committed PNG baselines, no pixel-diff threshold) (Phase 89 decision 7 / P-E.10 — baseline reading retired, founder-authorised 2026-11-03: no baseline PNGs are committed)
- [ ] **E.11** **Test suite gate** (mandatory — see master doc "Test suite gate" section):
  - [ ] `cd portal && pnpm lint` → zero lint errors
  - [ ] `cd portal && pnpm run build` → TypeScript clean
  - [ ] `cd portal && pnpm exec playwright test --list --config=playwright.portal.config.ts` → 4 new specs enumerated without parse errors
  - [ ] Full spec execution + baseline capture deferred to slice **500B** (tasks 500.6–500.7)

**If any checkpoint fails**: log finding to `tasks/phase68-gap-report.md` (produced in slice 500B) using the severity/format defined in the master doc, and let `/qa-cycle-kc` dispatch a fix before re-running the failing step. **Fix PRs that do not pass the test suite gate (E.11) must NOT be merged** — either extend the fix to cover broken tests or revert and re-approach.
