# Day 14 — Firm onboards Moroka Family Trust (isolation setup) `[FIRM]`

**Cycle**: bugfix_cycle_2026-06-13 (Legal ZA Full Lifecycle — Keycloak)
**Date**: 2026-06-13 SAST
**Actor (intended)**: Thandi Mathebula (Owner), firm app :3000 (Keycloak realm `docteams`)
**Status**: **BLOCKED — 0/11 step checkpoints executed; 0/3 Day-14 summary checkpoints met.**

## Outcome

**Could not begin Day 14.** Authentication as Thandi on the firm app (:3000) is impossible
in this browser environment. A third-party browser extension is intercepting input events
on the Kazi/Keycloak sign-in page: the FIRST `computer` click after a page load registers,
but it immediately surfaces an (invisible-to-screenshot) overlay that causes EVERY subsequent
interaction tool to fail with:

```
Cannot access a chrome-extension:// URL of different extension
```

This affects `computer` (left_click, type, key, screenshot after first interaction),
`javascript_tool` (javascript_exec), on the login page. Because filling the email AND
submitting both require interaction and the second action is always blocked, the login form
can never be submitted, so the dashboard is never reached and no firm-side checkpoint can run.

Read-only / non-event tools still work (`navigate`, `find`, `read_page`, `form_input`, and the
first `screenshot` after a fresh navigate) — that is how the blocked-state evidence below was
captured.

## Reproduction (what was tried, in order)

1. Navigate `:3000/dashboard` → redirects to Keycloak email-discovery step (single "Email" field + "Sign In"). Screenshot OK.
2. `computer` click Email field → OK; `computer` type → **FAIL** (chrome-extension error).
3. `navigate` to KC URL again, `computer` screenshot → **FAIL** (page now fully hijacked).
4. New tab (`tabs_create_mcp`), closed old tab, re-navigated → environment is Microsoft **Edge** (`edge://newtab`). Same failure.
5. `form_input` to set email value (works — value confirmed set) → then `computer` click Sign In by coordinate → **FAIL**; by `ref` → **FAIL**; `key Return` → **FAIL**.
6. `javascript_tool` `form.requestSubmit()` → **FAIL** (chrome-extension error — JS context also hijacked).
7. `resize_window` (1400×900, to dismiss any autofill overlay) → click still **FAIL**.
8. `list_connected_browsers` → only ONE browser connected (`Browser 1`, macOS, local) — NOT an MCP-instance conflict.
9. Fresh navigate + immediate batch [click → type → key → screenshot]: first click OK, type **FAIL**. Confirms "first click then blocked" pattern definitively.

All recovery avenues (fresh tab, re-navigate, resize, ref-based click, JS submit, key submit) exhausted.

## Why no workaround was used

- The scenario requires Day 14 to be **browser-driven** firm-side (mandate: "No SQL shortcuts. APIs and browser UI only." + "QA must drive browser, not REST"). Creating the Moroka client / matter / info-request / document / trust deposit via backend REST or SQL to fabricate the entities would violate the cycle mandate and would not exercise the firm UI the checkpoints test. So Moroka was **not** created by any back door.
- The only sanctioned workarounds (Mailpit API for OTP/links; dev-only Keycloak token issues) do not address an in-page input-event hijack by a foreign extension.
- Per Quality Gate "Pride and honesty" + "PASS means observed": no checkpoint is marked PASS. This is reported as a genuine BLOCKER.

## Checkpoint results

| # | Checkpoint | Result | Evidence |
|---|-----------|--------|----------|
| — | Authenticate as Thandi on :3000 | **BLOCKED** | Extension event-interception on KC login; see Reproduction. Screenshot: clean sign-in page rendered, but no interaction possible. |
| 14.1 | Clients → + New Client | NOT REACHED | login blocked |
| 14.2 | Fill TRUST client (Moroka Family Trust, IT 001234/2024, moroka.portal@example.com, 2 beneficial owners) | NOT REACHED | — |
| 14.3 | Submit → client created | NOT REACHED | — |
| 14.4 | Conflict Check → CLEAR | NOT REACHED | — |
| 14.5 | + New Matter → Deceased Estate template | NOT REACHED | — |
| 14.6 | Fill matter (EST-2026-002, Estate Late Peter Moroka, Estates—Deceased, Master's Office JHB) | NOT REACHED | — |
| 14.7 | Submit → matter created | NOT REACHED | — |
| 14.8 | Send info request: Liquidation & Distribution Account docs → moroka.portal@example.com, due Day 30 | NOT REACHED | — |
| 14.9 | Upload internal doc to Moroka matter (Work → Documents) | NOT REACHED | — |
| 14.10 | Record R 25 000 trust deposit vs Moroka / EST-2026-002 | NOT REACHED | — |
| 14.11 | Capture Moroka entity IDs into isolation-probe-ids.txt | **BLOCKED** | IDs recorded as `<PENDING>` placeholders; no entities exist to capture. |

**Day 14 summary checkpoints**
- [ ] Two clients + two matters on tenant (Sipho + Moroka) — **NOT MET** (only Sipho exists; Moroka not created).
- [ ] Moroka has ≥1 info request, ≥1 document, ≥1 trust deposit — **NOT MET**.
- [ ] Moroka entity IDs captured for Day 15 — **NOT MET** (placeholders only).

## Impact on Day 15

Day 15 (portal isolation check — Sipho must NOT see Moroka data) **cannot run** until Day 14
is completed, because there is no Moroka data to fail to see and no real Moroka IDs to probe.
`isolation-probe-ids.txt` Moroka section left as `<PENDING>` to prevent Day 15 from targeting
stale/wrong IDs.

## Environment note (carry-over check)

OBS-1001 trust-deposit combobox (14.10) could not be exercised. It was confirmed VERIFIED-working
on Day 10 of this same cycle, so no regression is implied by this blocker.

## Disposition / next action

This is an **environmental blocker**, not a product defect — **no OBS-14xx gap is filed** (it is
not a Kazi bug; it is a browser-extension input-event hijack in the QA harness). To unblock:
- Disable/remove the interfering browser extension (likely a password manager or form-filler) in
  the Edge instance hosting the Claude browser extension, OR run the QA browser with a clean profile
  (no other extensions), then re-run Day 14 from 14.1.
- Alternatively pair a different clean browser via `switch_browser`/`select_browser`.

Stack itself is healthy (svc.sh: backend/gateway/frontend/portal all RUNNING + HEALTHY; KC :8180 up).
The block is purely at the browser-input layer.
