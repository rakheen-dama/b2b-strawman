# Day 5 Re-verify — OBS-503 / OBS-505 / OBS-504 on main — Cycle 2026-06-13

**Executed**: 2026-06-13 (branch `bugfix_cycle_2026-06-13`, dev-stack backend restarted on main with both PRs #1436 + #1437)
**Actor**: Bob Ndlovu (Admin) — fresh Keycloak login on :3000 (`bob@mathebula-test.local` / `SecureP@ss2`, realm `docteams`). Portal as Sipho via new REQ-0002 magic-link on :3002 (no Keycloak).
**Driver**: QA agent via Playwright MCP — browser UI only. Mailpit API used only to extract the REQ-0002 magic-link + list notification emails.
**Method**: Fresh info-request round-trip (NEW request, not the pre-fix REQ-0001 which stays PENDING by design).
**Pre-checks**: svc.sh status — backend (PID 45933) / gateway / frontend / portal all RUNNING + HEALTHY. Backend log fresh post-restart (149 lines, 3 specialists registered: intake-za/billing-za/inbox-za).

## New request used: REQ-0002 `bd2a3297-02de-46e7-b397-dacac7a1537b`
- Ad-hoc, 2 file-upload items: "Updated medical report", "Particulars of claim signature page"
- Sent to Sipho Dlamini (sipho.portal@example.com); magic-link Mailpit `P784eWtujTfx6ppEN3UBiQ`, token `a5jl-Ipz80RS90s9EhmbAHasHBJTkuRaJ5lKHJZ96RA`
- Portal uploads: `particulars-of-claim-draft-v1.pdf` (item 1), `signed-engagement-letter.pdf` (item 2) — uploaded + submitted per-item via portal UI file-chooser
- Documents (now UPLOADED): item1 `fa352070-73a0-46df-8028-3ab1d0e8fd81`, item2 `bca2bcf2-bfb9-4305-8a41-8d3034fa51c2`

## Results

| Fix ID | Result | Evidence |
|--------|--------|----------|
| **OBS-503** | **VERIFIED** | Firm-side Download on each of the 2 submitted docs navigated to a valid LocalStack presigned S3 URL (`/docteams-dev/org/mathebula-partners/project/08ad56c4-…/{fa352070,bca2bcf2}-…?X-Amz-Algorithm=…&X-Amz-Signature=…`) — NOT "Document has not been uploaded yet". `curl` of both presigned URLs returned **HTTP 200, application/pdf, 1172 B (item1) + 974 B (item2)**. Docs confirmed UPLOADED on submit (the `submitItem` status-flip fix). Screenshot `day-05-reverify-obs503-firm-detail.png`. |
| **OBS-504** | **VERIFIED** | Matter Activity feed (Activity group → Activity) new top entry reads **"Information request REQ-0002 sent to Sipho Dlamini"** — the client contact, NOT the actor (Bob). Confirming the fix's 2nd prong: the pre-fix REQ-0001 entry now reads neutral **"Information request REQ-0001 sent to the client contact"** (its audit row predates the fix → no `contact_name`, but the neutralized formatter fallback no longer misattributes to actor). Screenshot `day-05-reverify-obs504-activity.png`. |
| **OBS-505** | **VERIFIED** | On the 2nd (final) Accept, REQ-0002 **auto-completed cleanly**: UI badge "Completed", "2/2 accepted", "Completed on 13 Jun 2026" — page did NOT 500. Backend log: `[INFO] InformationRequestService: Auto-completed information request bd2a3297-… (REQ-0002)`. The seeded INFORMATION_REQUEST_COMPLETED automation (rule `0556d81d…`) fired and its INVOKE_AI_SPECIALIST action **soft-failed** (`[ERROR] InvokeAiSpecialistActionExecutor: …No specialist found with id INTAKE` → `[WARN] Action … failed` → `[INFO] Sent AUTOMATION_ACTION_FAILED notification`). **CRITICAL ASSERTION MET: zero `UnexpectedRollback` / `rollback-only` / `TransactionSystemException` anywhere in the log (grep count = 0); the action failure ran in its own REQUIRES_NEW tx and did NOT abort the completion.** A 2nd rule (`7db0b76c` "Request Complete Follow-up", CREATE_TASK) also soft-failed ("no projectId in context") — same isolated soft-fail path, completion unaffected. Screenshot `day-05-reverify-obs505-completed.png`. |

### OBS-505 note (tenant-data residue, NOT a fix regression)
The seeded tenant rule `0556d81d` still carries the **old uppercase `INTAKE`** specialistId because it was created pre-fix (PR #1300 era) and the planned QA "UI-edit the 4 broken TEMPLATE rules" repair step was NOT performed in this re-verify. The OBS-505 PR #1437 corrected the IDs **in the pack JSONs** (affects future installs) and added the **tx-isolation** that is the actual blocker fix. The KEY behaviour — completion is not rolled back by the automation failure — is VERIFIED. The lingering soft-fail notification is cosmetic admin-bell noise from the un-repaired legacy rule, not a completion blocker.

## OBS-506 observation (Product triage — NOT fixed)
Clicking the **"Extract client-supplied fields"** SpecialistLauncherButton on the info-request detail page (`information-requests/[id]/page.tsx:56` call site) fired:
- Network: **`POST /api/assistant/specialists/INTAKE/sessions` → 404 Not Found**
- Console: `[ERROR] [SpecialistLauncher] startSession failed SpecialistApiError: Specialist API error: 404`
- UI: graceful inline alert **"Could not start specialist session. Please try again."** (NOT silent)

**Two overlapping causes, both visible in the one request:**
1. **Real call-site bug** — the launcher passes the uppercase `INTAKE` id (should be `intake-za`); the 404 URL literally contains `/specialists/INTAKE/sessions`. This is the OBS-506 defect class and would fail in **any** mode.
2. **OBS-201 exemption masking** — in KC mode the `/api/assistant/*` Next→backend proxy is unwired; every `/api/assistant/*` call 404s regardless of id (confirmed: `/api/assistant/invocations?...&status=PENDING_APPROVAL` 404s on info-request, customer detail, and matter pages alike).

**Disposition evidence**: OBS-506 is a **genuine latent bug** (uppercase ID at 5 call sites) that is currently **masked** by the OBS-201 proxy-unwired exemption in KC mode. The 404 outcome here is OBS-201-class, but the uppercase id is independently wrong and would 404 even with the proxy wired. Recommend Product treat OBS-506 as a real (LOW, deferred) frontend fix bundled with whenever the OBS-201 assistant proxy is wired — it is not "exempt/non-existent", it is "real-but-currently-masked".

## Mailpit (REQ-0002 round-trip, all to sipho.portal@example.com)
- `Information request REQ-0002 from Mathebula & Partners` (11:06:58Z, sent)
- `Item accepted — Updated medical report` (11:09:27Z)
- `Item accepted — Particulars of claim signature page` (11:09:35Z)
- `Request REQ-0002 completed (Mathebula & Partners)` (11:09:35Z)

## Console / log health
- Firm (:3000): only `/api/assistant/invocations` 404s (OBS-201 EXEMPT) + the OBS-506 `/api/assistant/specialists/INTAKE/sessions` 404 on launcher click. Zero JS/hydration errors.
- Portal (:3002): **0 console errors** across the full upload+submit flow (1 cosmetic warning only).
- `.svc/logs/backend.log`: exactly **1 ERROR** (the isolated soft-failed automation action). **0 rollback exceptions.**

## Screenshots
- `day-05-reverify-obs503-firm-detail.png` — REQ-0002 firm detail, both items Submitted with Download
- `day-05-reverify-obs504-activity.png` — Activity feed "REQ-0002 sent to Sipho Dlamini"
- `day-05-reverify-obs505-completed.png` — REQ-0002 Completed, 2/2 accepted, "Completed on 13 Jun 2026"
- `day-05-reverify-obs506-launcher-404.png` — SpecialistLauncher "Could not start specialist session" alert
- `day-05-reverify-portal-submitted.png` — portal REQ-0002 "2/2 submitted • IN_PROGRESS"

## Verdict
- OBS-503 → **VERIFIED**
- OBS-504 → **VERIFIED**
- OBS-505 → **VERIFIED** (completion never rolled back; automation soft-fails in isolation)
- OBS-506 → **real-but-masked** (uppercase id call-site bug + OBS-201 proxy unwired); Product to disposition as deferred LOW, not exempt-nonexistent.

All 3 merged fixes pass end-to-end on main. **Day 7 (firm drafts + sends proposal) is unblocked** once OBS-506 is dispositioned (LOW, non-blocking — recommend defer with the OBS-201 proxy work).
