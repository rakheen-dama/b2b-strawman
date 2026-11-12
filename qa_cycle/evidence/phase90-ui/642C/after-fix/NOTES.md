# 642C re-observation after fix `664a58068` (D-642C-OBS-1)

Captured 2026-11-13 against the e2e mock-auth stack on :3001. The stack was built from the worktree @ `664a58068`: the containers were created at 00:49 and the commit is from 00:44. The served chunk `0yrr2g1l7c04i.js` contains the new `onFocus` handler (`t.matches(":focus-visible")` → `ee(viewport, t)`). Alice, light theme. I drove the browser with Playwright-library scripts (nvm node v20.11.1), not the MCP browser. No source edits. The full run was done twice with identical numbers; `run.log` is the second run.

Pages are from the fresh stack (lifecycle run `mwe5644z5770`, found in `tenant_7d218705360b`):
- Matter `e1f5964b-b227-46f2-84a0-463b35aaa834` ("Ndlovu v RAF mwe5644z5770"). It has 7 triggers: details, overview, work, finance, client, schedule, activity.
- Client `94221767-a984-4192-ba8e-7a104b10d1fc` ("Sipho Ndlovu mwe5644z5770"). It has 6 triggers: details, overview, work, finance, compliance, activity.

"Clear" means:
- the trigger is inside the row viewport (±0.5px), and
- it overlaps a *visible* fade by 0px.

I also checked the ring with pixels: from a DPR-2 screenshot I sampled the left, right, top, top-right and bottom strips of the 2px inset ring against `--ring` (rgb 0,146,130).

## Results

| # | Check | Result | Key numbers |
|---|---|---|---|
| 1 | Keyboard, end side @390×844 | **PASS** (sub-pixel note) | I entered the bar with the keyboard (Shift+Tab, then Tab). Details had `:focus-visible` = true. After ArrowRight ×4:<br>• Matter `tab-group-client`: x 255.8–326.2 in viewport 32–358, scrollLeft **93**/210 (before the fix: 0, 9px visible, all of it under the fade).<br>• Client `tab-group-compliance`: 186.4–342.6 in 16–374, scrollLeft **163**/205 (before: 0, 25px visible).<br>The gap to the viewport's right edge is **31.8 / 31.4px**, so the trigger sits 0.2 / 0.6px inside the 32px fade zone (see the note below). The ring pixels match `--ring` exactly on all sides (Δ 0). Every trigger from ArrowRight #1 to the last is in the viewport, and the worst fade overlap is 0.8px (matter Fees). `scrollY`/`scrollX` stay at 0 on every step, and the max window scrollY/X is 0. |
| 2 | Tab key forward | **PASS** (same note) | The numbers are identical to #1, step for step (Overview → Activity on both pages). Focus never leaves the bar. |
| 3 | Start side (ArrowLeft, Shift+Tab) | **PASS** | On every step, either the trigger's left edge is at least 32px inside the viewport's left edge (minimum gap **32.2**: matter Work at scrollLeft 139, client Work at 139), or scrollLeft is 0 (Details). There is 0px overlap with the start fade at every step. ArrowLeft and Shift+Tab produce identical sequences. ArrowLeft from Details wraps to Activity at scrollLeft max (210 / 205), and it is clear. |
| 4 | Fully visible trigger | **PASS** | Details → Overview: scrollLeft stays 0, and the row fired **0** `scroll` events. Work also stays at scrollLeft 0. |
| 5 | Wrap | **PASS** | ArrowRight past Activity puts focus on Details at scrollLeft **0**, with the start fade hidden, on both pages. |
| 6 | Mouse regression | **PASS** | **768 matter:** I clicked `tab-group-schedule` (Diary, x 691–742, viewport right 728, end fade visible) with a real mouse click at x 718, i.e. 10px inside the fade. `elementFromPoint` returned the trigger. pointerdown, mousedown, focusin, mouseup and click all targeted `tab-group-schedule` with scrollLeft **0** at every event. `:focus-visible` was false, so the row did not jump. The tab param went from none to `court-dates`, aria-selected became true, and the active-tab effect then scrolled the row to 46. A Playwright `locator.click()` gave the same navigation.<br>**390 dropdown:** a mouse click at x 353 on matter `tab-group-client` (9px visible, entirely under the end fade) opened the menu: role=menu with 4 items (clients, requests, comments, adverse-parties), scrollLeft 0 at pointerdown. On the client page, a click at x 364 on `tab-group-compliance` (25px visible) opened the menu with 2 items (onboarding, requests), scrollLeft 0. Bonus: after Escape, focus returns to the trigger with `:focus-visible` and the row scrolls it clear (93 / 163). |
| 7 | Deep link @390 | **PASS** | **Matter `?tab=disbursements`:** "Fees · Disbursements" at 151–326 in 32–358, scrollLeft 126/321, 32px gap to the right edge, both fades visible, scrollY 0, max scrollY during load **0**.<br>**Client `?tab=audit`:** Activity at 309–374 in 16–374, scrollLeft 205 (max), start fade visible, end fade hidden, scrollY 0, max **0**. |
| 8 | Wide 1440 / 1024, light | **PASS** | The row does not overflow anywhere: matter 1088/1088 and 672/672, client 1136/1136 and 720/720. I focused every trigger through ArrowRight (including the wrap), Tab and Shift+Tab: scrollLeft is always **0** and the row fired **0** scroll events. Each case has 1 tablist (1 visible) and the active Overview underline (`::after` opacity 1, 2px). The ring box-shadow is `lab(52.87 -45.78 -2.85) 0 0 0 2px inset`, and its pixels match on all sides (Δ 0). Window scrollY/X stay 0. |
| 9 | Console | **PASS** (on the tab-bar pages) | 0 hydration warnings and 0 warnings. There is **1 error**: `MockAuthContextProvider: failed to fetch userinfo TypeError: Failed to fetch` on `/dashboard`, in the wide-layout context, right after login. The script navigates away immediately after `waitForURL(/dashboard/)`, which aborts the userinfo fetch. `obs-after-fix-supp.cjs` lets the dashboard settle (networkidle + 3s) and gets **0** errors. There were 0 errors on any matter or client page. The one warning in `run-supp.log` (Canvas2D `willReadFrequently`) comes from my own pixel probe. |

## Sub-pixel note (not a functional defect)

The focused trigger sometimes stops 0.2–0.8px short of the 32px clearance:

| Trigger | Gap to viewport right | Inside the fade zone by |
|---|---|---|
| Matter Fees | 31.2px | 0.8px |
| Matter Client | 31.8px | 0.2px |
| Client FICA & compliance | 31.4px | 0.6px |

Measured against the strict wording of check #1 ("right edge ≤ viewport right − 32"), these miss by less than 1px. The likely cause is in `frontend/components/shared/grouped-tab-bar.tsx:95-96`: `scrollTriggerClearOfFades` uses `offsetLeft`/`offsetWidth`, which round to integers. The real layout is fractional (Fees is 263.44 wide by 63.33, from the `gap-2` and text widths), and `scrollLeft` is set to an integer.

Visually it does not matter:
- The fade is transparent at its inner edge, so its opacity 0.8px in is about 2.5%.
- For the worst case (matter Fees, `run-supp.log`), the last device pixel of the right ring strip is rgb(2,147,131), against rgb(0,146,130) for the ring.
- Every other ring sample matches exactly.

If the strict bound matters, a fix is `Math.ceil` on the end measured from `getBoundingClientRect()` relative to the row. That is an orchestrator decision; I did not change it.

## Files

- `obs-after-fix.cjs`: the main script. `obs-after-fix-supp.cjs`: worst-case ring pixels plus the dashboard console re-check.
- `obs-after-fix.json`: compact raw data, about 39KB. `run.log` and `run-supp.log`: run output.
- 12 screenshots:
  - `kbd-{matter,client}-390-arrow4-*.png` (focused partly-hidden trigger, now clear and ringed)
  - `kbd-{matter,client}-390-last-tab-group-activity.png`
  - `kbd-{matter,client}-390-arrowleft-tab-group-work.png`
  - `deeplink-matter-390-disbursements.png`, `deeplink-client-390-audit.png`
  - `mouse-matter-768-after-click.png`, `mouse-matter-390-dropdown-open.png`
  - `kbd-client-1440-ring-tab-group-work.png`, `kbd-matter-1024-ring-tab-group-work.png`
