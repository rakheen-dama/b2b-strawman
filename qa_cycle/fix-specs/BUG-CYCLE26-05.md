# Fix Spec: BUG-CYCLE26-05 — Playwright MCP screenshot timeout

## Disposition: WONT_FIX (tooling-only; no product impact)

## Problem

`mcp__playwright__browser_take_screenshot` repeatedly times out (`Timeout 5000ms exceeded after fonts loaded`) on the Next.js dev frontend at `:3000` mid-session during 2026-04-26 cycle-6 verify pass. PNG screenshots taken earlier in the same session succeed (Day 0, Day 1, early Day 2) — failures appear after several hours of uptime.

QA workaround: capture YAML accessibility snapshots via `browser_snapshot --filename` instead. Evidence collection continues unimpeded.

## Why this is not a product bug

1. **The "after fonts loaded" hint points at Playwright MCP's own `waitForLoadState`/`page.evaluate('document.fonts.ready')` step, not at our font configuration.** Our `next/font/google` setup (Sora, IBM Plex Sans, JetBrains Mono per `frontend/CLAUDE.md`) emits standard `<link rel="preload" as="font">` and `font-display: swap` declarations. The same fonts and same dev server worked for screenshots earlier in the session.

2. **No real user is affected.** Production users do not interact with `mcp__playwright__browser_take_screenshot`. They render pages in Chrome/Firefox/Safari which use native font loading and have their own perf budgets. A Playwright MCP timeout is a CI/automation tooling artefact.

3. **Reproduction is environmental.** The same QA harness, same code, same browser session — the failure flips on around hour N of session uptime. Symptoms consistent with: long-lived headless Chromium GPU/CPU pressure, dev-server HMR memory growth, Playwright MCP server-side connection-pool exhaustion. None of these change the bytes shipped to real users.

4. **No font-loading regression is suggested by the evidence.** If real users were hitting a font-loading bug, we would expect (a) console errors about missing fonts, (b) FOIT/FOUT visual artefacts, (c) Core Web Vitals regressions in ops dashboards. None of these are reported. Day 1 GAP-L-90 verify-cycle confirmed sidebar fonts and brand colour render correctly across multiple sessions.

5. **Workaround is sufficient.** YAML DOM snapshots provide higher-fidelity QA evidence than PNGs for accessibility / structure assertions, and the cycle-6 verification log uses them without any blockage to the lifecycle scenario.

## What we'd do if this becomes a real issue

If a customer reports font-loading visible delays in production:
- Verify the issue reproduces in real Chrome/Safari (not Playwright headless).
- Check `frontend/app/layout.tsx` `next/font/google` config for missing `display: "swap"` (it should be the default).
- Inspect Lighthouse/Web Vitals for FCP regressions on document routes that use `font-display`.
- Audit any dynamic `<link rel="preload">` injection added recently.

None of those are warranted now.

## Action

Mark `BUG-CYCLE26-05` as **WONT_FIX** in `qa_cycle/status.md`. Logged for trace-ability; no PR will be raised. If the timeout recurs and blocks future QA cycles materially, re-open as a tooling/infra issue (not a product bug) and investigate Playwright MCP server uptime / Chromium recycling.

## Estimated Effort

n/a — closure only.
