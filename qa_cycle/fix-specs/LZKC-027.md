# Fix Spec: LZKC-027 — Org-level CreateProposalDialog client combobox inert (FormControl discards PopoverTrigger props)

## Problem
Carried forward: the client combobox in the org-level Create Proposal dialog doesn't open on click — visible button, no popover. Pre-existing; reproduced in the prior cycle.

## Root Cause (confirmed)
The composition at `frontend/components/proposals/create-proposal-dialog.tsx:281-296` is the standard shadcn combobox-in-form pattern:

```tsx
<PopoverTrigger asChild>
  <FormControl>
    <Button type="button" role="combobox" ...>
```

Radix `PopoverTrigger asChild` clones its child — here `FormControl` — injecting the trigger props (`onClick`, `onPointerDown`, `aria-haspopup`, `data-state`, `ref`). But this project's `FormControl` (`frontend/components/ui/form.tsx:86-98`) is NOT the upstream shadcn `Slot`; it's a custom component typed `React.ComponentProps<typeof React.Fragment>` that reads **only** `props.children`:

```tsx
function FormControl({ ...props }) {
  const child = React.Children.only(props.children) ...;
  return React.cloneElement(child, { id, "aria-describedby": ..., "aria-invalid": ..., ...child.props });
}
```

Every prop the PopoverTrigger injects into `FormControl` (everything except `children`) is silently discarded — the Button never receives `onClick`/`ref` → inert combobox. Any `<XTrigger asChild><FormControl>…` composition breaks the same way; plain `<FormControl><Input {...field} /></FormControl>` sites work because they never rely on injected props.

## Fix
Replace the custom `FormControl` with the upstream shadcn Slot implementation (`frontend/components/ui/form.tsx:86-98`):

```tsx
import { Slot } from "radix-ui"; // per frontend/CLAUDE.md: unified radix-ui package, never @radix-ui/react-slot

function FormControl(props: React.ComponentProps<typeof Slot.Root>) {
  const { error, formItemId, formDescriptionId, formMessageId } = useFormField();
  return (
    <Slot.Root
      data-slot="form-control"
      id={formItemId}
      aria-describedby={!error ? formDescriptionId : `${formDescriptionId} ${formMessageId}`}
      aria-invalid={!!error}
      {...props}
    />
  );
}
```

`Slot` forwards ALL incoming props (including PopoverTrigger's injected handlers/ref) and merges with the child's own props (child wins on conflicts, event handlers compose, refs compose) — same precedence the current `...child.props`-last cloneElement gives, so existing `<FormControl><Input/></FormControl>` sites keep their behaviour. This fixes the bug class at its root instead of patching the one dialog.

**Regression breadth warning**: `FormControl` is a shared form primitive used across the app — this change mandates the FULL frontend vitest suite (per Quality Gates §5, no path-narrowed runs) and a quick manual pass over one representative form (e.g. the trust-account create dialog, which nests `Checkbox` inside `FormControl` at `CreateTrustAccountDialog.tsx:263-268`).

## Scope
Frontend only.
Files to modify: `frontend/components/ui/form.tsx`.
Test: add a test that mounts the `PopoverTrigger asChild > FormControl > Button` composition and asserts clicking the button opens the popover (red-first on current main — this is the reproduction). Existing form tests cover the plain-input path.
Migration needed: no.

## Verification
- New composition test red → green; full `pnpm test` (all form-using suites), `pnpm lint && pnpm build` + prettier.
- Live: `/org/{slug}/proposals` → Create Proposal → click "Select a customer..." → popover opens, customer selectable. Also spot-check one Input-based form and one Checkbox-based form (create trust account) for unchanged behaviour, and confirm labels still focus their inputs (the `id` merge).

## Estimated Effort
M (30 min – 2 hr) — one-file change, verification breadth is the cost.
