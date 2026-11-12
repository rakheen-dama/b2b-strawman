# ADR-374: First paint of the stored theme comes from a blocking `<head>` script; `next-themes` stays the runtime owner

**Status**: Accepted (refines [ADR-371](ADR-371-dark-mode-light-default.md) Rationale point 1; ADR-371's decision is unchanged)

**Context**:

ADR-371 puts the `next-themes` `ThemeProvider` inside `<body>` in `frontend/app/layout.tsx`, because `<AuthProvider>` wraps `<html>`. It relies on next-themes' own inline script to set the `dark`/`light` class on `<html>` before the first paint. Slice 637B's real-browser observation (PR #1760, head `1b78ae412`) showed that assumption does not hold under a slow, cold load:

- **Setup.** `kazi-theme=dark` stored, browser cache disabled, CPU throttled 4×, network Slow 4G, then a reload of `/org/e2e-test-org/dashboard`.
- **Result.** In **3 of 10** runs a whole-viewport light frame was presented before the dark frames (mean luminance 246 against about 20 for dark). Every such frame came after the new document's first script ran.
- **Cause.** The streamed HTML chunk ended before next-themes' script inside `<body>`. The browser painted `<body>` with no theme class, so it showed the light `--background`, until that script arrived and ran.
- Evidence: `qa_cycle/evidence/phase90-ui/637B/run1-1b78ae412/` (`README.md`, `fouc-diag-report-batch2.json`, `fouc-frames/b2-dark-nocache-run3-frame3.png`).

**Options Considered**:

1. **Keep next-themes' script as the only initialiser.** No code is needed, but the observed flash stays. Rejected: 637.12 requires no flash of the wrong theme.
2. **`next/script` with `strategy="beforeInteractive"`.** This is the framework's API for early scripts. Rejected: in the App Router, inline `beforeInteractive` content is queued to `self.__next_s` and runs only when the client bootstrap starts (`next/dist/client/script.js`, `app-bootstrap.js`). That is after first paint, so it would not fix the flash.
3. **Move the provider so its script lands in `<head>`.** Rejected: `<AuthProvider>` wraps `<html>`, and a provider between `<html>` and `<body>` would render a `<script>` as a direct child of `<html>`. That is invalid markup, and React's hydration behaviour for it is not guaranteed.
4. **A server-rendered class from a cookie.** Rejected: ADR-371 and open question #5 store the preference in the browser (`localStorage`). A cookie preference is a different decision, out of this phase's scope.
5. **A parser-blocking inline `<script>` in the root layout's `<head>`, built from the same constants as the provider (chosen).**

**Decision**: Option 5.

- `frontend/components/theme/theme-config.ts` is the single source. It holds `THEME_STORAGE_KEY` (`kazi-theme`), `DEFAULT_THEME` (`light`), `THEMED_ROUTE_PREFIX` (`/org/`), the `isThemedRoute` predicate and `THEME_INIT_SCRIPT`. The script string is generated from those constants.
- `app/layout.tsx` renders `<head><script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} /></head>` and stays a Server Component. `KaziThemeProvider` imports the same constants and predicate.
- The script applies exactly what the provider would: forced light unless the pathname starts with `/org/`, stored `light` or `dark`, `system` via `prefers-color-scheme`, otherwise light. It swaps the `dark`/`light` class and sets `style.colorScheme`, and it is wrapped in try/catch, including around `localStorage`.
- next-themes stays the runtime owner of the theme: the toggle, system changes, cross-tab sync and `forcedTheme`. Its own script still runs, and running it after the head script changes nothing.

**Rationale**:

- A `<script>` in `<head>` without `src`/`async`/`defer` blocks the parser before any `<body>` content exists, so no body paint can precede it. Run 2 of OBS at `4816cf440` confirms this: in all 35 throttled reloads (20 dark/no-cache, 10 light/no-cache, 5 dark/cached) the class was set while `document.body` was still `null`, and 0 runs showed the wrong theme (`qa_cycle/evidence/phase90-ui/637B/fouc-diag-report.json`, `head-check-report.json`).
- Duplicating next-themes' small init logic is contained and checked by tests. `frontend/__tests__/components/theme/theme-script.test.tsx` runs the generated script in happy-dom. It also server-renders the real `KaziThemeProvider`, extracts next-themes 0.4.6's own inline script, and asserts that both produce the same class and `colorScheme` across path, storage and OS-scheme combinations. A next-themes upgrade that changes its behaviour fails that test.
- React 19 does not hoist an inline script without `src`, and the root layout is hydrated, not client-created, so there is no "Encountered a script tag" warning. OBS recorded 0 such warnings and 0 hydration warnings. `suppressHydrationWarning` on `<html>` already covers the class and style change.

**Consequences**:

- Positive:
  - No wrong-theme first paint on slow or cold loads.
  - One module defines the theme constants for both the head script and the provider.
- Negative:
  - Two scripts initialise the theme (the head script, then next-themes'). The parity test is what keeps them aligned.
  - The app has no Content-Security-Policy today, so no nonce is needed. If a CSP is introduced, both this script and next-themes' script need a nonce or hash; next-themes accepts a `nonce` prop.
  - Edge differences until next-themes' script runs:
    - Garbage stored value: the head script applies `light`, then next-themes adds the garbage string as a class. Neither sets `dark`.
    - Throwing storage: the head script applies `light`, and next-themes sets nothing.
  - Both edge cases paint light.

**Related**: [ADR-371](ADR-371-dark-mode-light-default.md) (dark mode via next-themes, light default; this ADR refines its placement rationale), [architecture §15](../architecture/phase90-ui-modernization.md); slice 637B (`tasks/phase90-ui-modernization.md`), PR #1760.
