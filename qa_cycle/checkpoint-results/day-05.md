# Day 5 — Firm reviews FICA submission — Cycle 2026-06-13

**Executed**: 2026-06-13 (branch `bugfix_cycle_2026-06-13`)
**Actor**: Bob Ndlovu (Admin) — context swap from Day 4 portal session: fresh Keycloak login on :3000 (`bob@mathebula-test.local` / SecureP@ss2, realm `docteams`), landed on dashboard as "Bob Ndlovu". Portal spot-check reused Sipho's live Day 4 session on :3002 (cookie still valid, no re-auth needed).
**Driver**: QA agent via Playwright MCP — browser UI only; Mailpit API used only to list notification emails.
**Pre-checks**: svc.sh status — backend/gateway/frontend/portal all RUNNING+HEALTHY.
**Result**: 5/6 checkpoints PASS, 1 FAIL (5.3) + day-summary 3/4 PASS, 1 FAIL. **3 new gaps filed: OBS-503 (HIGH), OBS-504 (LOW), OBS-505 (MEDIUM).**

## Checkpoints

| ID | Checkpoint | Result | Evidence | Gap |
|----|-----------|--------|----------|-----|
| 5.1 | Matter RAF-2026-001 → Client group tab (`tab-group-client`) → Requests sub-tab (`tab-item-requests`) | PASS | Both testids resolved via Playwright `getByTestId`. Client dropdown menu rendered (Clients / Requests / Client Comments / Adverse Parties); clicking Requests selected "Client · Requests" tab with requests table. Note: the dropdown click does NOT push `?tab=requests` into the URL (client-state only), but deep-linking `…?tab=requests` works and restores the sub-tab — accepted as equivalent to scenario's parenthetical (prior cycle's PASS did not assert the param either). | — |
| 5.2 | Envelope row In Progress + 0/3 accepted; row link → `/org/{slug}/information-requests/{id}`; detail renders 3 Submitted items with 3 PDFs | PASS | Row: REQ-0001 / Dlamini v Road Accident Fund / Sipho Dlamini / **In Progress** / **0/3 accepted** / Sent 13 Jun 2026. Link href `/org/mathebula-partners/information-requests/de3d6962-6018-43bf-852d-d366d1a4d626` (canonical, OBS-501 holds). Detail page: header REQ-0001 · In Progress, contact sipho.portal@example.com, due 20 Jun 2026, 0/3 accepted; items ID copy / Proof of residence (≤ 3 months) / Bank statement (≤ 3 months) all **Submitted** with fica-id.pdf / fica-address.pdf / fica-bank.pdf + Download + Accept/Reject. | — |
| 5.3 | Each per-item Download button operational | **FAIL** | Clicked all 3 Download buttons (×2 rounds). Zero console errors and zero toasts — but the server action returns `{"success":false,"error":"Document has not been uploaded yet"}` for **all three** documents (captured via network inspection, request body `["60639e26-…"]` etc.). No download occurs; failure is fully silent to the user. Ground truth: all 3 S3 objects exist (`aws s3 ls` LocalStack — 626/627/615 B) but all 3 `documents` rows are stuck at status **PENDING** (read-only psql inspection). Firm side cannot retrieve any client FICA document. | OBS-503 |
| 5.4 | Accept each item in turn: 0/3→1/3→2/3→3/3; envelope auto-completes on third Accept | PASS | Sequential Accepts: ID copy → Accepted, **1/3 accepted**, envelope In Progress; Proof of residence → **2/3 accepted**; Bank statement → **3/3 accepted**, envelope badge **Completed** + "Completed on 13 Jun 2026" stamp. Accept/Reject buttons removed per item after acceptance. No separate "Mark as Reviewed" exists — per-item Accepts close the envelope (OBS-403 lifecycle). Screenshot: `day-05-request-completed.png`. | — |
| 5.5 | Matter Overview FICA card updated; Activity feed full audit trail; FICA card "View request" link canonical | PASS (1 LOW gap noted) | **FICA Status Card**: badge In Progress → **Done**, "Verified 13 Jun 2026", "View request" → `/org/mathebula-partners/information-requests/de3d6962-…` (canonical — OBS-501 holds). **Activity feed** (Activity group tab → Activity): "REQ-0001 completed — all items accepted" (just now), 3× "Bob Ndlovu accepted '…' for REQ-0001", 3× Sipho portal.request_item.submitted, 3× Sipho portal.document.upload_initiated, "Information request REQ-0001 sent to Bob Ndlovu", "Bob Ndlovu created information request REQ-0001" — full trail present. Defect in one line: "sent to **Bob Ndlovu**" misattributes the recipient (was sent to Sipho Dlamini) → OBS-504. | OBS-504 |
| 5.6 | Mailpit: 3× per-item-accepted + 1× envelope-completed emails to Sipho | PASS | Mailpit API: "Item accepted — ID copy (Mathebula & Partners)" 23:59:43Z, "Item accepted — Proof of residence (≤ 3 months) (…)" 23:59:54Z, "Item accepted — Bank statement (≤ 3 months) (…)" 00:00:15Z, "Request REQ-0001 completed (Mathebula & Partners)" 00:00:15Z — all to sipho.portal@example.com, exactly per spec. | — |

## Portal-side post-completion spot-check (Sipho on :3002)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 5.PC1 | `/requests` row REQ-0001: badge COMPLETED + counter 3/3 accepted (OBS-502 verification) | PASS | Day 4 session still authenticated (`:3002` cookie intact — no re-auth, no Keycloak). `/requests` row: REQ-0001 / Dlamini v Road Accident Fund / **COMPLETED** / **3/3 accepted** (not 0/3 submitted — OBS-502 holds). |
| 5.PC2 | Detail header reads `3/3 accepted • status COMPLETED` | PASS | `/requests/de3d6962-…`: header "REQ-0001 / Dlamini v Road Accident Fund / **3/3 accepted • status COMPLETED**"; all 3 items "Submitted — status: ACCEPTED". Screenshot: `day-05-portal-completed.png`. |

## Day 5 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Three uploaded documents retrievable firm-side | **FAIL** | OBS-503 — download server action fails for all 3 documents ("Document has not been uploaded yet"); documents rows stuck PENDING despite S3 objects existing. |
| Info request lifecycle `Sent → IN_PROGRESS → Completed`, envelope closes on last Accept | PASS | Observed IN_PROGRESS (0/3) → Completed (3/3) with "Completed on 13 Jun 2026"; no separate Mark-as-Reviewed button. |
| Matter FICA indicator updated; FICA card link routes to `/information-requests/{id}` (OBS-501) | PASS | FICA card Done / Verified 13 Jun 2026; canonical route confirmed. |
| Portal spot-check: COMPLETED + 3/3 accepted (OBS-502); detail header `3/3 accepted • status COMPLETED` | PASS | Both PC1 + PC2 pass on live Sipho session. |

## Gaps filed

### OBS-503 (HIGH) — Portal-submitted FICA documents stuck PENDING; firm-side Download fails silently
- **Observed**: All 3 Download clicks on `/org/mathebula-partners/information-requests/de3d6962-…` return server-action payload `{"success":false,"error":"Document has not been uploaded yet"}`. No file downloads, no error toast — completely silent failure. Day 5 summary checkpoint "Three uploaded documents retrievable firm-side" fails.
- **Ground truth**: S3 objects exist for all 3 docs (LocalStack `docteams-dev`, 615–627 B, uploaded Day 4 via portal UI). DB `tenant_5039f2d497cf.documents` rows all status **PENDING** (read-only inspection).
- **Root cause (code-traced)**: The portal upload flow never finalizes the Document. Portal `portal/app/(authenticated)/requests/[id]/page.tsx` (~L139–164) does init (`POST /portal/requests/{id}/items/{itemId}/upload`) → presigned S3 PUT → `POST …/submit`. Backend `customerbackend/service/PortalInformationRequestService.initiateUpload` creates the Document (status PENDING) and `submitItem` (L199–227) marks the RequestItem SUBMITTED but **never calls `document.confirmUpload()`** — and no portal-facing confirm endpoint exists (firm-side flow uses `POST /api/documents/{id}/confirm` → `DocumentService.confirmUpload`). Firm-side `DocumentService.getPresignedDownloadUrl` (L488–491) gates on `Status.UPLOADED` → throws `InvalidStateException("Document not uploaded", "Document has not been uploaded yet")`.
- **Secondary defect**: the firm frontend download handler swallows `success:false` without any user feedback (no toast) — should surface the error.
- **Regression note**: prior cycle (2026-05-30) marked 5.3 PASS but only asserted "no console errors" — never inspected the action response or DB status; the documents were likely PENDING then too. This cycle's deeper verification exposes it. Not attributable to PRs #1403–#1435 with current evidence.

### OBS-504 (LOW) — Activity feed misattributes info-request recipient: "Information request REQ-0001 sent to Bob Ndlovu"
- **Observed**: Matter Activity feed line reads "Information request REQ-0001 sent to **Bob Ndlovu**" — REQ-0001 was sent to portal contact Sipho Dlamini; Bob is the sender.
- **Root cause (code-traced)**: `activity/ActivityMessageFormatter.java` L101–103 formats `information_request.sent` as `"Information request %s sent to %s"` using `getContactName(details, actorName)` — falls back to the **actor** when `details.contact_name` is absent. `informationrequest/InformationRequestService.java` send() audit details (L359–363) only put `request_number` + `project_id`, never `contact_name`. Fallback misattribution, formatter dates to PR #545 (2026-03-06) — pre-existing.
- **Fix shape**: include `contact_name` in the sent-event audit details (or fall back to neutral copy without recipient).

### OBS-505 (MEDIUM) — Seeded AI-specialist automations reference non-existent specialist IDs (INTAKE/BILLING/INBOX) — automation fails with 404 + backend ERROR on envelope completion
- **Observed**: At the moment the third Accept completed REQ-0001 (00:00:15Z), backend logged ERROR: `Failed to execute INVOKE_AI_SPECIALIST action: 404 … No specialist found with id INTAKE` for rule `0556d81d…` ("Extract fields from uploaded intake documents", trigger INFORMATION_REQUEST_COMPLETED), full stack trace, action FAILED, and an `AUTOMATION_ACTION_FAILED` notification was sent to admins/owners (firm notification bell 3→6).
- **Root cause (code-traced)**: `resources/automation-templates/ai-specialist-common.json` uses `"specialistId": "INTAKE"` / `"BILLING"`; `ai-specialist-legal-za.json` + `ai-specialist-consulting-za.json` use `"INBOX"`. Registered specialist IDs are `intake-za` (IntakeSpecialistConfig.java:14), `billing-za` (BillingSpecialistConfig.java:14), `inbox-za` (InboxSpecialistConfig.java:14). `automation/executor/InvokeAiSpecialistActionExecutor.java:67` passes the template string straight to `SpecialistRegistry.requireById` → guaranteed 404. **Bug class: every seeded AI-specialist automation template action (6 across 3 packs) can never execute.**
- **Not exempt**: distinct from OBS-201 (frontend assistant proxy 404 in KC mode). This is a backend wiring bug that fails in any mode, before any AI provider call.
- **Note**: templates date to PR #1300 (2026-05-06) — latent since then; first observed because Day 5 envelope completion triggers the INTAKE rule and this cycle checks backend logs per day.

## Console / log health

- Firm session (:3000): only `/api/assistant/invocations` 404s (OBS-201 WONT_FIX-EXEMPT) + one KC `favicon.ico` 404 (cosmetic). Zero JS/hydration errors. One Next.js `scroll-behavior` warning (informational, pre-existing).
- Portal session (:3002): zero errors except portal `favicon.ico` 404 (cosmetic, same as Day 4).
- `.svc/logs/backend.log`: **1 ERROR** — the OBS-505 automation failure stack trace (see above). No other errors.

## Screenshots

- `qa_cycle/checkpoint-results/day-05-request-completed.png` — REQ-0001 firm detail: Completed, 3/3 accepted, all items Accepted
- `qa_cycle/checkpoint-results/day-05-portal-completed.png` — portal REQ-0001 detail: 3/3 accepted • status COMPLETED

## Entity IDs (for downstream days)

- **REQ-0001** `de3d6962-6018-43bf-852d-d366d1a4d626` — envelope **COMPLETED** (Completed on 13 Jun 2026), 3/3 accepted; matter FICA card **Done / Verified 13 Jun 2026**
- **Documents (still PENDING — OBS-503)**: `60639e26…` fica-id.pdf / `c13bb325…` fica-address.pdf / `07d78e24…` fica-bank.pdf
- **Failed automation rule (OBS-505)**: `0556d81d-d36b-4379-8e80-f16d73b1e0ca` "Extract fields from uploaded intake documents"; execution FAILED + admin notification sent
- **Sessions**: Bob authenticated on :3000; Sipho's portal session still live on :3002
- **Matter / client IDs**: unchanged (`08ad56c4-ff5e-49c2-a034-cb5fa04b462c` / `2211a80a-5523-4a6d-8f96-0d638dff88f6`)
