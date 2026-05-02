/**
 * SSR snapshot harness — generalised from PR #1262's `CreateProposalDialog`
 * structural assertion (audit-03 fix-spec recommendation #4).
 *
 * The bug class this defends against is mount-gate-style regressions in
 * dialogs: a `useState(false)` + `useEffect(() => setMounted(true))` +
 * `if (!mounted) return null` pattern that papers over a Radix hydration
 * mismatch by rendering nothing on SSR / first commit. The structural fix
 * is to let Radix render normally on SSR (its `useId` is stable across
 * SSR + client in React 19).
 *
 * `renderDialogToSsr` runs a JSX element through `react-dom/server`'s
 * `renderToString`, then normalises non-deterministic IDs so the snapshot
 * is stable across runs and CI environments.
 *
 * Use with `expect(html).toMatchSnapshot()` (or `toMatchInlineSnapshot()`).
 * Any structural change to the SSR output of a dialog — including the
 * mount-gate regression class — produces a loud snapshot diff in PR review.
 */

import type { ReactElement } from "react";
import { renderToString } from "react-dom/server";

/**
 * Replace non-deterministic IDs in HTML with stable placeholders so the
 * snapshot doesn't churn between runs.
 *
 * Patterns covered:
 * - React 19 `useId()` IDs (`:r0:`, `:r1a:`, `:rb:` …)
 * - Radix legacy `radix-«r0»` IDs (older Radix versions still emit these)
 *
 * Both shapes feed `id`, `aria-controls`, `aria-describedby`,
 * `aria-labelledby`, etc. — by replacing the value globally we don't need
 * to enumerate the attributes.
 */
export function normalizeRadixIds(html: string): string {
  return html.replace(/«r[0-9a-z]+»/gi, "«r0»").replace(/:r[0-9a-z]+:/gi, ":r0:");
}

/**
 * Render a dialog (or any client component) to SSR HTML and normalise
 * non-deterministic IDs.
 *
 * Returns the normalised HTML so the caller can:
 * 1. Snapshot it (`toMatchSnapshot()`) for structural-regression catching.
 * 2. Run targeted assertions (`toContain`, `toMatch`) for explicit contract
 *    checks (e.g. trigger present, `aria-controls` injected by Radix).
 */
export function renderDialogToSsr(element: ReactElement): string {
  const raw = renderToString(element);
  return normalizeRadixIds(raw);
}
