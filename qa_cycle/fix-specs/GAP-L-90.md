# Fix Spec: GAP-L-90 — Brand colour CSS var injected but no UI consumes it

## Problem

Day 1 §1.2 (`qa_cycle/checkpoint-results/day-01.md`, screenshot `day-01-1.2-after-refresh.png`):

The org Settings > General "Save Settings" persists the firm brand colour to `org_settings.brand_color` and the org layout injects it as `--brand-color: #1B3358` on `<html>`. But **no CSS rule in the bundled stylesheets references `var(--brand-color)`**, so the setting has zero visible effect on the UI. Users perceive "I picked navy and saved, but nothing happened."

Verified by `document.styleSheets` traversal during QA — 0 hits for `var(--brand-color)`.

## Root Cause (verified)

### Where the variable is set

`frontend/components/desktop-sidebar.tsx` lines 39–54:

```tsx
useEffect(() => {
  if (!brandColor) return;
  const root = document.documentElement;
  const previous = root.style.getPropertyValue("--brand-color");
  root.style.setProperty("--brand-color", brandColor);
  return () => {
    if (previous) root.style.setProperty("--brand-color", previous);
    else root.style.removeProperty("--brand-color");
  };
}, [brandColor]);
```

The DesktopSidebar (client component) sets `--brand-color` on `document.documentElement` from the server-fetched `brandColor` prop. Mobile sidebar relies on this same injection.

### Where the variable should be consumed but isn't

`frontend/app/globals.css` — 203 lines, **zero** references to `var(--brand-color)`. All theme tokens are slate/teal-derived; no hook for brand override.

### Sidebar active indicator (the most-visible accent)

`frontend/components/nav-zone.tsx` line 85 and `frontend/components/desktop-sidebar.tsx` line 159:

```tsx
className="absolute top-1 bottom-1 left-0 w-0.5 rounded-full bg-teal-500"
```

These are the per-zone active-item indicator and the utility-footer active indicator. Hard-coded `bg-teal-500`. Prime real estate for brand-colour theming.

## Fix

Pick the **two highest-signal surfaces** that do NOT require touching every component:

1. **Sidebar active-item accent indicator** — the vertical bar that highlights the current page in the dark sidebar. This is the most visually prominent on every screen.
2. **Sidebar org-name label** — the small teal label above the search bar (`text-teal-500/80` at `desktop-sidebar.tsx` line 74). Reading the firm's name in their own brand colour is a high-signal "wow" moment.

Skip primary buttons, dashboard headings, and login/auth screens — those are global-product surfaces and re-theming them per-tenant is out of scope for a minimal fix.

### Step 1 — Add `--brand-color` fallback in `globals.css`

In `frontend/app/globals.css`, inside the `:root { ... }` block (after the existing `--ring` / sidebar section, around line 131), append:

```css
  /* Tenant brand colour — overridable via inline style on <html>.
     Defaults to teal-500 so unbranded orgs see the standard accent. */
  --brand-color: var(--teal-500);
```

Add the same line to the `.dark { ... }` block (around line 169, after `--sidebar-ring`):

```css
  --brand-color: var(--teal-500);
```

This means even when the DesktopSidebar useEffect has not yet fired (initial paint, no brand colour configured, SSR), the variable resolves to teal-500 and the indicator looks correct.

### Step 2 — Wire the sidebar active indicator to the variable

In `frontend/components/nav-zone.tsx` line 85, change:

```tsx
className="absolute top-1 bottom-1 left-0 w-0.5 rounded-full bg-teal-500"
```

to:

```tsx
className="absolute top-1 bottom-1 left-0 w-0.5 rounded-full"
style={{ backgroundColor: "var(--brand-color)" }}
```

In `frontend/components/desktop-sidebar.tsx` line 159, change:

```tsx
className="absolute top-1 bottom-1 left-0 w-0.5 rounded-full bg-teal-500"
```

to:

```tsx
className="absolute top-1 bottom-1 left-0 w-0.5 rounded-full"
style={{ backgroundColor: "var(--brand-color)" }}
```

(Inline style is required because Tailwind v4 cannot statically extract a non-hex `var()` reference into a utility class without an arbitrary-value escape, and `bg-[var(--brand-color)]` would also work but inline `style` is clearer.)

### Step 3 — Wire the org-name label

In `frontend/components/desktop-sidebar.tsx` line 74, change:

```tsx
<span className="truncate text-xs font-medium text-teal-500/80">{orgName ?? slug}</span>
```

to:

```tsx
<span
  className="truncate text-xs font-medium opacity-80"
  style={{ color: "var(--brand-color)" }}
>
  {orgName ?? slug}
</span>
```

### Step 4 — (Optional) inject early to avoid FOUC

Currently `--brand-color` is set inside `useEffect` after hydration. On first paint the indicator briefly shows teal-500 (the fallback) before flipping to brand. This is acceptable for a minimal fix; if it bothers, set the variable inline on the `<aside>` element instead of `documentElement`:

In `frontend/components/desktop-sidebar.tsx`:

```tsx
<aside
  className="hidden w-60 flex-col bg-slate-950 md:flex"
  style={brandColor ? { ["--brand-color" as string]: brandColor } : undefined}
>
```

…and remove the `useEffect` block entirely. This scopes the var to the sidebar subtree (which is what we're theming anyway) and avoids the global side-effect on `<html>`. Mobile sidebar already mentions in a comment that "brandColor is injected by DesktopSidebar on document.documentElement; no \[need]" — update that comment to reflect the new scoping, and apply the same inline style in `mobile-sidebar.tsx`.

(Step 4 is recommended but not strictly required for verification — it removes a hydration race and a global side-effect.)

### Do NOT (out of scope)

- Don't theme the `--accent` token globally — it's used for buttons, badges, ring focus, etc., and replacing teal everywhere is a brand-takeover, not a brand-accent.
- Don't touch `--sidebar-ring` or `--ring` — focus rings stay teal for accessibility consistency.
- Don't theme login/landing pages — those are pre-auth, no tenant context exists.

## Scope

- Frontend only.
- Files to modify:
  - `frontend/app/globals.css` (2 lines added)
  - `frontend/components/nav-zone.tsx` (1 className change + style)
  - `frontend/components/desktop-sidebar.tsx` (2 className changes + styles; optional refactor of `useEffect` to inline `style`)
  - `frontend/components/mobile-sidebar.tsx` (only if Step 4 is taken — apply matching inline style)
- Files to create: none.
- Migration needed: no.

## Verification

Re-run Day 1 §1.2:

1. Login as Thandi. Settings > General > set brand colour to `#1B3358` (Mathebula navy) > Save.
2. Refresh dashboard.
3. Active sidebar indicator (vertical bar next to current page) MUST render navy `#1B3358`, not teal.
4. Sidebar org-name label ("Mathebula & Partners") MUST render navy.
5. `document.styleSheets` traversal MUST show the new `:root` rule defining `--brand-color: var(--teal-500)` (stylesheet-level fallback). Inline `style={{ backgroundColor: "var(--brand-color)" }}` usages live on the element itself, not in the stylesheet — assert those separately by element inspection (step 6).
6. Element inspection of `.absolute.w-0.5` (active indicator) MUST show computed `background-color: rgb(27, 51, 88)`.
7. Re-take screenshot — replace `day-01-1.2-after-refresh.png`.
8. Logout, login again — colour persists (already covered by existing persistence; no regression).
9. Switch to a tenant without `brand_color` set — indicator falls back to teal-500 (regression check via second tenant).

## Estimated Effort

S (< 30 min) — three small edits + verification screenshot, optional Step 4 adds another 15 min.
