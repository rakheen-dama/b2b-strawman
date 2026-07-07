/**
 * SSR snapshot harness — generalised from PR #1262's `CreateProposalDialog`
 * structural assertion (audit-03 fix-spec recommendation #4).
 *
 * The bug class this defends against is *silent structural drift* in a
 * dialog's SSR output — e.g. a gate regressing to `return null` (blank
 * trigger flash) or Radix ids appearing/disappearing. Note the nuance
 * LZKC-002 added: React 19's `useId` is only SSR-stable when the SSR tree
 * and the client's first render are structurally identical. Under the org
 * app-shell (which has client-divergent nodes) SSR-stamped radix ids are a
 * hydration hazard, so `CreateProposalDialog` deliberately SSRs an id-free
 * bare trigger (mount-gate), while dialogs on hydration-stable subtrees may
 * still SSR the full Radix wrapper (e.g. `LogExpenseDialog`).
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
