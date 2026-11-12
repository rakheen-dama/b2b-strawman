# 642C re-observation, round 2, after `ad4f84ba4`

Captured 2026-11-13 against the e2e mock-auth stack on :3001.

**Build check.** The stack was built from the worktree at `ad4f84ba4`:
- The commit is from 01:16 SAST. The containers were created at 01:22, and the served chunk `1wguwdrfryici.js` was built at 01:21.
- That chunk (HTTP 200) contains the new code:
  - `Math.floor(r.width),n=Math.floor(s.left-r.left+a),i=Math.ceil(s.right-r.left+a)`, which is the `getBoundingClientRect` clearance.
  - the `ResizeObserver` width guard `let e=t.clientWidth;if(e!==r){r=e;…[aria-selected="true"]`.

**Method.**
- User: Alice, light theme.
- Browser: Playwright-library scripts on nvm node v20.11.1, DPR 2 except the 768 mouse check. I did not use the MCP browser. No source edits and no commits.

**Pages** (lifecycle run `mwe6c12fa5b4`, `tenant_7d218705360b`):
- Matter `504406d9-0fbd-4891-9813-5ed1ca24518a`, 7 triggers.
- Client `375c87f2-f266-490c-9492-4759cb6412c7`, 6 triggers.

**Clearance rule.** A trigger counts as clear when:
- it is inside the row viewport (±0.5px), and
- each side is either ≥ 32.0px from the viewport edge (measured with `getBoundingClientRect`, unrounded), or has its fade hidden (row at 0 or at max).

## Results

| # | Check | Result | Key numbers |
|---|---|---|---|
| 1a | Resize 1440 → 390 / 768, last group (`?tab=audit`) | **PASS** | At 1440 the row does not overflow and scrollLeft is 0 on both pages.<br>**Matter "Activity · Audit trail":**<br>• 390: scrollLeft **285/285**<br>• 768: 179/179<br>**Client "Activity":**<br>• 390: 205/205<br>• 768: 99/99<br>In every case the start fade is shown, the end fade is hidden (the row is at max), `scrollY` stays 0 before and after (max 0), and the row fired exactly 1 scroll event.<br>The literal "right ≤ vpRight − 32" cannot hold for the *last* trigger at max scroll. With the end fade hidden it is clear. The script's `pass:false` on these rows comes only from that literal clause. |
| 1b | Resize, non-last group (supp; end fade stays visible, so the strict bound applies) | **PASS** | End gaps after the resize:<br>• matter `?tab=court-dates` (Diary): **32.188** at 390 (scrollLeft 152/210) and 32.188 at 768 (46/104)<br>• matter `?tab=disbursements`: **32.859** at both widths (127/321, 21/215)<br>• client `?tab=onboarding` ("FICA & compliance · FICA onboarding", 278px wide): **32.438** at both widths (286/327, 180/221)<br>Start gaps are ≥ 47.4. Both fades show. scrollY is 0. |
| 2 | No fighting the user @390 | **PASS** | A shift-wheel scrolled the row back to scrollLeft **0** on both pages, with no direct set needed. scrollLeft then stayed **0** through:<br>• the real theme toggle (mock avatar menu, then Dark (`html.dark`=true, see `nofight-matter-390-dark.png`), then Light, then close). No `scrollLeft` write was logged.<br>• the Work tab dropdown, opened with the mouse and closed with Escape. Escape returned focus to Work with `:focus-visible`, and Work is already clear.<br>• the Work dropdown, opened and closed with a genuine outside click (3 cases).<br>• a height-only viewport change (844 → 700 → 844)<br>• a +1px change to the row width<br>One main-run step went to 16 (client, "outside click" at 200,800). That click actually landed on the **"Documents" menu item** (`elementFromPoint` confirms it), so it *selected* `?tab=documents`. The newly active "Matters · Documents" trigger was then scrolled clear (see `nofight-client-390-after-relayouts.png`). This is designed behaviour and an artefact of my script, not a defect. |
| 3 | Sub-pixel clearance, keyboard @390 | **PASS** | I went ArrowRight from Details across every trigger, then ArrowLeft back. Every step is clear.<br>Minimum gaps where that fade is visible:<br>• **matter: end 32.188 (Diary), start 32.156 (Work)**<br>• **client: end 32.375 (FICA & compliance), start 32.156 (Work)**<br>Round 1 had matter Fees 31.2, matter Client 31.8, client FICA 31.4. Those are now Fees **32.23**, Client **32.78** and FICA **32.38**.<br>Ring check: I sampled the outermost fully covered device pixel (and the one next to it) on both sides of every focused trigger against `--ring`. The difference was **Δ0** on all 22 steps, so there is no fade tint. scrollY stayed 0. |
| 4a | Deep link `?tab=disbursements` @390 | **PASS** | "Fees · Disbursements" sits at 150.44–325.14 inside 32–358. End gap is 32.859, scrollLeft 127/321, both fades show. scrollY is 0 and the max scrollY over the whole load is 0. |
| 4b | Mouse click 10px inside the end fade @768 (matter Diary) | **PASS** | Diary spans 691.2–741.8 with the viewport right edge at 728. The click at x 718 hits the trigger (`elementFromPoint`). pointerdown, mousedown, focusin, mouseup and click all report scrollLeft **0**, and the click target is `tab-group-schedule`. The tab went from none to `court-dates` and aria-selected is true. Only after that did the active-tab effect scroll the row to 46. |
| 4c | @1440, both pages | **PASS** | One tablist (1 visible). No overflow (1088/1088, 1136/1136). The Overview underline has opacity 1 at 2px. I focused every trigger through ArrowRight (including the wrap), Tab and Shift+Tab: scrollLeft was always **0**, the row fired **0** scroll events, and scrollY stayed 0. |
| 5 | Console | **PASS** | Both scripts: **0 errors, 0 warnings, 0 hydration** messages across all pages, matter and client included. |

## Observation (not caused by this commit, LOW)

When the row is at max scroll, the last trigger overhangs the viewport's right edge by a fraction of a pixel:
- matter "Activity · Audit trail": 0.25px
- client "Activity": 0.36px

The row's content width is fractional, but `scrollWidth` and the maximum `scrollLeft` are integers. The end fade is hidden there, and the outermost fully covered ring device pixel still matches `--ring` (Δ0), so it is not visible. Round 1 shows the same geometry (client Activity 309–374 in 16–374).

## Files

**Scripts**
- `obs-after-fix2.cjs`: the main run. Output in `run.txt`, data in `obs-after-fix2.json`.
- `obs-after-fix2-supp.cjs`: the non-last-group resize, an instrumented repro of the "outside click" step (logs every `scrollLeft` write with its stack), and the real theme toggle. Output in `run-supp.txt`, data in `obs-after-fix2-supp.json`.

**Screenshots (11)**
- `resize-matter-1440to390-audit.png`, `resize-matter-1440to768-audit.png`, `resize-client-1440to390-audit.png`
- `nofight-{matter,client}-390-after-relayouts.png`, `nofight-matter-390-dark.png`
- `kbd-matter-390-tab-group-finance.png`, `kbd-client-390-tab-group-compliance.png`
- `deeplink-matter-390-disbursements.png`
- `mouse-matter-768-after-click.png`
- `wide-matter-1440-focus.png`
