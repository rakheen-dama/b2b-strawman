# Bug Report: Missing Dialog Trigger Buttons (RSC × Radix `asChild` incompatibility)

**Status:** Confirmed — 7 broken instances on Server Component pages, 44 client components using the at-risk pattern
**Severity:** High — silently hides primary "Create/Edit/Delete" UI actions from users with correct roles
**First observed:** `/schedules` page — "New Schedule" button missing despite admin role (fixed as proof-of-concept)
**Environment:** Next.js 16.1.6, React 19.2.3, radix-ui (current), "use client" Dialog wrappers

---

## Symptom

A Dialog trigger button that is passed as JSX children from a Server Component to a `"use client"` Dialog component **silently disappears** from the DOM after hydration. No console error, no error boundary trip, no visible hydration warning for the affected subtree. The user sees a working page with a blank space where the primary action button should be.

From the user's perspective: "The Recurring Schedules page loads, but there's no way to create a schedule." Navigation and list views work; the create CTA is just absent.

---

## Root Cause

The bug triggers when **all** of these are true simultaneously:

1. A Server Component (e.g. a `page.tsx` without `"use client"`) renders a Dialog wrapper component.
2. The Dialog wrapper is `"use client"` and accepts `children: React.ReactNode` as a prop.
3. The wrapper uses Radix's `<DialogTrigger asChild>{children}</DialogTrigger>` passthrough pattern.
4. The Server Component passes a `<Button>…</Button>` JSX tree as that `children`.

When these align, the **Radix `Slot.SlotClone` fiber inside `DialogTrigger` renders `null`** during hydration. The server-rendered HTML arrives with an empty wrapper `<div>` (the button placeholder is missing from SSR output), and the client-side React tree also fails to materialize a DOM node for the child. The fiber tree shows `DialogTrigger → Primitive.button → Primitive.button.Slot → Primitive.button.SlotClone` with no descendants — the child Button component is never instantiated.

### Fiber tree at the break point (from live Playwright inspection on `/schedules`)

```
div (flex justify-end)                [hasDOM: yes]
└─ ScheduleCreateDialog                [fiber exists, no DOM]
   └─ Dialog                           [fiber]
      └─ Dialog (Radix)                [fiber]
         └─ DialogProvider             [fiber]
            ├─ DialogTrigger           [fiber]
            │  └─ DialogTrigger        [fiber]
            │     └─ Primitive.button  [fiber]
            │        └─ Slot           [fiber]
            │           └─ SlotClone   [fiber, NO CHILD ← broken]
            └─ DialogContent           [fiber, closed so no portal]
```

Expected: `SlotClone` should have cloned the `<Button>` child with merged trigger props and rendered a `<button>` DOM element. Observed: the SlotClone fiber has no children; the `<Button>` component never runs.

### Why the children get dropped

Likely a known interaction between:

- **RSC serialization of JSX children**: the Server Component serializes the `<Button><Plus /> Text</Button>` subtree into the RSC payload. The children arrive at the client as a serialized React element tree, not as a live render.
- **Radix `Slot` / `asChild`**: internally clones its single child with `React.cloneElement` and needs to attach its own ref + merged props. When the child is a component boundary coming from an RSC payload rather than an inline JSX expression in a client component, Radix's clone appears to produce a fiber with no output.
- **React 19 `useId` mismatch**: a separate hydration mismatch on `aria-controls` IDs was captured on `/customers` (confirmed in Playwright console log) — React 19 explicitly says "This won't be patched up" for these mismatches and drops the subtree to re-render client-side. The `/schedules` case doesn't throw the same visible warning, but the net effect (empty subtree after hydration) is consistent with the same class of mismatch-then-discard behavior.

The critical trait: this failure is **silent**. Nothing in dev-server logs, no red overlay, no thrown exception. The only signal is the missing DOM node.

---

## Confirmed Broken Instances

Server Component pages passing a `<Button>` child to a `"use client"` Dialog wrapper that uses the `<DialogTrigger asChild>{children}</DialogTrigger>` pattern. Each of these almost certainly renders without the intended trigger button:

| # | Page (Server Component) | Line | Dialog | Intended CTA |
|---|---|---|---|---|
| 1 | `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` | 502 | `<DataExportDialog>` | Export customer data |
| 2 | `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` | 527 | `<EditCustomerDialog>` | Edit customer |
| 3 | `frontend/app/(app)/org/[slug]/retainers/page.tsx` | 89 | `<CreateRetainerDialog>` | New Retainer |
| 4 | `frontend/app/(app)/org/[slug]/retainers/[id]/page.tsx` | 100 | `<EditRetainerDialog>` | Edit retainer |
| 5 | `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` | 598 | `<EditProjectDialog>` | Edit matter |
| 6 | `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` | 606 | `<DeleteProjectDialog>` | Delete matter |
| 7 | `frontend/app/(app)/org/[slug]/proposals/page.tsx` | 87 | `<CreateProposalDialog>` | New Proposal |

**Already fixed (verified via Playwright):**
- `frontend/components/schedules/ScheduleCreateDialog.tsx` + `frontend/app/(app)/org/[slug]/schedules/page.tsx` — refactored to match `CreateCustomerDialog` pattern.

---

## At-Risk Surface (Passthrough Pattern Present)

**44 components in `frontend/components/` use `<DialogTrigger asChild>{children}</DialogTrigger>`.** These are only broken when invoked from a Server Component with a Button child; when invoked from another Client Component they render correctly (the RSC boundary isn't crossed at the trigger).

Representative list (abbreviated — full list in the grep below):

- `customers/link-project-dialog`, `archive-customer-dialog`, `edit-customer-dialog`, `data-export-dialog`, `anonymize-customer-dialog`
- `settings/edit-tax-rate-dialog`, `add-tax-rate-dialog`
- `rates/add-rate-dialog`, `edit-rate-dialog`, `delete-rate-dialog`, `add-project-rate-dialog`, `project-rates-tab`, `customer-rates-tab`
- `retainers/create-retainer-dialog`, `edit-retainer-dialog`
- `tasks/create-task-dialog`, `edit-time-entry-dialog`, `delete-time-entry-dialog`, `log-time-dialog`
- `projects/edit-project-dialog`, `delete-project-dialog`, `add-member-dialog`, `transfer-lead-dialog`, `complete-project-dialog`, `link-customer-dialog`
- `expenses/log-expense-dialog`
- `schedules/ScheduleEditDialog`
- `tags/TagDialog`, `DeleteTagDialog`
- `templates/NewFromTemplateDialog`, `SaveAsTemplateDialog`, `ResetTemplateDialog`
- `field-definitions/FieldDefinitionDialog`, `DeleteFieldDialog`, `FieldGroupDialog`, `DeleteGroupDialog`
- `integrations/SetApiKeyDialog`
- `time-tracking/csv-import-dialog`
- `views/CreateViewDialog`
- …and others

Full list:
```sh
grep -rln "DialogTrigger asChild>{children}" frontend/components --include="*.tsx"
```

---

## Why Some Dialogs Work Fine

Counter-example: `frontend/components/customers/create-customer-dialog.tsx` — used on `/customers` page and works correctly. Differences:

1. Takes **no `children` prop**; defines its own trigger button inside the component.
2. **Does not use `<DialogTrigger>` at all**. Opens the dialog by calling `setOpen(true)` from the button's `onClick`.
3. Controls `<Dialog open={open} onOpenChange={...}>` purely via local `useState`.

Because the `<Button>` is declared in the same file as the `useState` hook and the `<Dialog>`, no RSC boundary is crossed at the trigger. The Radix `asChild` / Slot machinery isn't involved.

This is the pattern the fix for `ScheduleCreateDialog` adopted.

---

## Why the Bug is Hidden from QA

Most of the broken pages don't 404 or error out — they render almost completely, minus one button. A manual tester or automated regression can easily miss a missing "Edit" or "New X" button if the list view itself is populated. Only the `/schedules` case was visible because the entire page's purpose is creation-driven and there was no list content to distract from the missing button.

Suspect any place a user report says "I can't find how to X" — this bug is a likely cause.

---

## Recommended Fix Pattern (for reference only; not applied in this report)

For each broken Dialog wrapper:

1. Drop `children: React.ReactNode` from the component's props interface.
2. Remove the `DialogTrigger` import.
3. Replace `<DialogTrigger asChild>{children}</DialogTrigger>` with the trigger button rendered directly in the client component:
   ```tsx
   <Button size="sm" onClick={() => setOpen(true)}>
     <Plus className="mr-1.5 size-4" />
     New Retainer
   </Button>
   ```
4. In each calling Server Component page, remove the `<Button>` JSX child and call the dialog self-closing: `<CreateRetainerDialog slug={slug} customers={customers} />`.
5. If the trigger's label/icon varies by caller, lift the variation into a typed `variant` or `label` prop instead of passing children.

A single concrete before/after is in the `ScheduleCreateDialog` diff already landed.

---

## Open Questions

- Does the bug reproduce on production builds (`next build` + `next start`) or is it dev-only? The observation was made against `next dev`. Production RSC rendering differs materially; this needs explicit validation before assuming prod is affected.
- Is there a Radix UI version bump that fixes the Slot/asChild + React 19 interaction? Worth checking `radix-ui` changelog against the current version pinned in `package.json`.
- Would a project-wide ESLint rule flag the at-risk pattern? A lint rule detecting `<DialogTrigger asChild>{children}` in any `"use client"` file exporting a component with `children` in its props type would catch future regressions.
