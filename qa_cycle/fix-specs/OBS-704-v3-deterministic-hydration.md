# Fix Spec: OBS-704 v3 — remove dead mount-gate workaround in `CreateProposalDialog`

**Severity**: workaround → cleanup (HIGH per slop-hunt batch B finding 1)
**Surface**: Frontend — `components/proposals/create-proposal-dialog.tsx` + `hooks/use-now-ms.ts` + `__tests__/create-proposal-dialog.test.tsx`
**Effort**: S (~1h end-to-end including review)

## Problem

PR #1234 ("OBS-704 hydration v2") shipped a mount-gate workaround in `CreateProposalDialog`:

```tsx
const [mounted, setMounted] = useState(false);
useEffect(() => { setMounted(true); }, []);
// ...
if (!mounted) {
  return <>{children}</>;  // pre-mount: bare children, no Radix Dialog tree
}
return <Dialog>...<DialogTrigger asChild>{children}</DialogTrigger>...</Dialog>;
```

The slop-hunt audit (`qa_cycle/audits/slop-hunt-BATCH-B.md`, finding 1, HIGH) flagged this as a textbook hydration antipattern: it doesn't make SSR + first-commit identical, it skips the problematic Radix subtree on the server and re-renders to the wrapped tree after `useEffect`. The effective "fix" was subtree elision, not deterministic rendering.

## Root cause (verified)

The original mismatch was on `aria-controls` injected by `Radix.Dialog.Trigger` pointing at `Radix.Dialog.Content`'s id. The id is allocated by `Dialog.Provider` via `@radix-ui/react-id`'s `useId` hook:

```js
// node_modules/@radix-ui/react-id/dist/index.mjs
var useReactId = React[" useId ".trim().toString()] || (() => void 0);
function useId(deterministicId) {
  const [id, setId] = React.useState(useReactId());  // initialized from React.useId()
  // ...
  return deterministicId || (id ? `radix-${id}` : "");
}
```

`React.useId()` is SSR-stable in React 19. On both the server and the first client render, `useReactId()` returns the same id, so `useState(useReactId())` initialises to the same value, and `Dialog.Trigger`'s `aria-controls=context.contentId` is identical on both sides.

The mount-gate was therefore likely addressing an issue that was real on an older React + Radix combination but is no longer reachable on the current stack (React 19.2.3 + `radix-ui` 1.4.3 / `@radix-ui/react-dialog` 1.1.15). It survived as dead defensive code because no SSR test exercised the contract.

## Fix

### Part 1 — Remove the mount-gate

Delete the `mounted` state, the `useEffect` that flips it, and the early `if (!mounted) return <>{children}</>` block in `create-proposal-dialog.tsx`. Restore single-render Dialog tree on SSR + first commit.

### Part 2 — Re-document `useNowMs`

Per the audit's recommendation in `slop-hunt-BATCH-B.md`:

> #1231 — revert the `useNowMs` hook OR re-document what bug class it actually guards (it's not OBS-704). LOW-effort.

Re-document is the safer option. `useNowMs` legitimately guards a different hydration class — `Date.now()` returns different values on the server vs the client, and `proposal-table.tsx` uses `useNowMs()` as the anchor for relative-time rendering of expiry countdowns. Returning 0 pre-mount lets consumers render a safe fallback on SSR + first commit and switch to a live value after `useEffect`. This is real, just not what OBS-704 was about. The JSDoc now says so.

### Part 3 — SSR snapshot regression guard

Add a vitest test that asserts `renderToString(<CreateProposalDialog>...)` emits the trigger element with `aria-controls="radix-..."`:

```tsx
const ssrHtml = renderToString(
  <CreateProposalDialog slug="legal-test" customers={CUSTOMERS}>
    <Button data-testid="ssr-trigger">New Proposal</Button>
  </CreateProposalDialog>
);
expect(ssrHtml).toContain('data-testid="ssr-trigger"');
expect(ssrHtml).toMatch(/aria-controls="radix-[^"]+"/);
```

This is the "5-line SSR snapshot test" the audit named explicitly. Without the fix (under the old mount-gate), the SSR HTML would have been the bare `<Button>` with no `aria-controls`. Under the fix, Radix renders normally and the attribute is present. If a future change reintroduces a mount-gate (or downgrades Radix below the `useId`-using version), this test fails.

## Scope

- `frontend/components/proposals/create-proposal-dialog.tsx` — remove mount-gate.
- `frontend/hooks/use-now-ms.ts` — re-document.
- `frontend/components/proposals/__tests__/create-proposal-dialog.test.tsx` — add SSR snapshot test.

Out of scope (separate PR per "one fix per PR"):

- The four audit-03 files still carrying `*Trigger asChild` siblings (`customer-rates-tab`, `project-rates-tab`, `expense-list`, `comment-item`). That's the next backlog item.
- The audit's recommendation #4 ("add an SSR-snapshot test harness for the dialog component family"). Out of scope here — the test in this PR covers `CreateProposalDialog` specifically. A reusable harness is a separate refactor.

## Verification

- Vitest: `pnpm test` — 2130 / 0F / 2 skip (340 test files) including the new SSR snapshot test.
- Lint: `pnpm lint` — 0 errors, 98 pre-existing warnings (none introduced).
- Build: `pnpm build` — succeeds, full route tree renders.

Browser-driven verify of the hydration warning was skipped: port 3000 (Keycloak mode) is not auth-reachable from agents per `CLAUDE.md`, and standing up the mock-auth E2E stack for a single attribute-presence check is heavier than the regression test the audit asked for. The SSR snapshot test is the regression guard; it will catch a re-regression in CI without depending on a running browser.

## Implemented As

PR #N — see `OBS-704-v3-deterministic-hydration.implementation-note.md` once merged.
