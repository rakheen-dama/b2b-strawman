# ADR-393: EmptyState sizing rule and its enforcement

**Status**: Accepted

**Context**:

Phase 90 restyled the shared `EmptyState` in place (`frontend/components/empty-state.tsx`, adapted from admincn `empty-state-v2`) and gave it `variant?: "default" | "compact" | "inline"`, documented in one line: "default = page-level; compact = widgets and chart cards; inline = one line in a table body" (`:19`). The prop defaults to `"default"` (`:34`), the page-level block (`gap-3 py-16`, 48px icon tile, `h2` title).

At HEAD there are 61 calls of the shared component in 52 files; 8 pass a variant (all `compact`), and 53 render the page-level block wherever they sit — including inside sections ("No tasks assigned" under the "My Tasks" heading, `my-work/assigned-task-list.tsx:65`), cards, and the narrow task-detail Sheet, where `CustomFieldSection`'s "No custom fields configured" (`:525-535`) is at its worst. Two more calls use a local look-alike in `ai/reviews/reviews-client.tsx:88`, and dozens of hand-rolled `py-12 text-center` blocks do the same job (architecture §18.3.3). About 40 of the 53 are genuine page-level or page-level-tab-panel states for which `default` is right. Founder decision RD-4 asks for the empty custom-fields state to become a compact one-liner for everyone, at today's visibility ("Configure Fields" is ungated). Copy is frozen. Precedent for source guards: `frontend/__tests__/dark-mode-contrast-guard.test.ts` (TypeScript compiler API + `__tests__/helpers/source-scan.ts`, self-tested matchers, an allowlist).

**Options Considered**:

1. **Document the rule in JSDoc and rely on review.**
   - Pros: No tooling.
   - Cons: That is what Phase 90 did; 53 of 61 sites ended up at the wrong or unconsidered size. Review does not scale to 61 sites across 52 files.
2. **Change the default to `compact` and require `variant="default"` explicitly for page-level states.**
   - Pros: One-line change; most new call sites (sections, cards) get the right size by default.
   - Cons: Silently shrinks the ≈40 correct page-level states the moment it merges, before anyone has looked at them; the failure is visual and passes every test.
3. **Make `variant` required in the TypeScript type in one slice.**
   - Pros: The compiler enforces it; no custom tooling.
   - Cons: Forces all 61 sites into one PR, including ones that belong to pages later slices restyle (settings, fee notes, trust, diary, team, platform admin) — far over the slice budget, and it decides those pages' variants before they are observed.
4. **A local ESLint rule (`no-restricted-syntax` on `JSXOpeningElement[name.name='EmptyState']:not(:has(JSXAttribute[name.name='variant']))`) in `frontend/eslint.config.mjs`.**
   - Pros: Fails in the editor and in lint.
   - Cons: Matches every component named `EmptyState` (there are two others: the AI-reviews look-alike and `components/setup/empty-state.tsx`); an allowlist in lint config is awkward to ratchet; the config comment warns that restricted-syntax entries must live in one object, which collides with existing rules.
5. **A vitest source-scan guard with a shrinking allowlist, the type tightened once the allowlist is empty (chosen).**
   - Pros: Resolves the import, so only the shared component is checked; multi-line JSX and spread props handled by the TS compiler API; an allowlist lets each surface slice set its own sites after observing them; self-tests keep the matcher honest; the final type change makes it permanent.
   - Cons: A test, not a lint error, so it fails in `pnpm test` rather than in the editor; the allowlist is temporary state that must reach zero (the exit gate checks it).

*Smaller variant*:

6. **Add a fourth variant smaller than `compact`.**
   - Pros: A named "one row" form.
   - Cons: `inline` already is one row (icon, title, description, action, `py-3 text-sm`); a fourth variant would duplicate it.
7. **Use `inline` for one-row slots outside tables too (chosen).**
   - Pros: No new API; the rule's wording becomes "a table body or a single-line slot".
   - Cons: `inline` renders the title as a `<p>`, so a site whose title e2e asserts as a heading cannot move to it.

**Decision**: Options 5 and 7. The rule, written into the component's JSDoc:

| Variant | Use when |
|---|---|
| `default` | the empty state is the whole content of a page or of a page-level tab panel |
| `compact` | inside a section, card, widget, chart card, dialog body or Sheet |
| `inline` | a table body or a single-line slot |

Every call site passes `variant`, enforced by `frontend/__tests__/emptystate-variant-guard.test.ts`, which walks JSX elements named `EmptyState` whose import resolves to `@/components/empty-state` and fails with `file:line` when `variant` is absent outside its allowlist. E3A lands the guard and fixes the named offenders; E3B sets the remaining sites on pages Phase 91 does not otherwise touch; each later surface slice removes its own allowlist entries. The slice that removes the last entry (E9B, the last surface slice) also makes `variant` required in the type, so the allowlist is empty at Phase 91's exit. No default-prop change. No new variant.

**Height budget**, from the real classes (`empty-state.tsx:92-114`: `py-8`, 40px tile, 24px `text-base` title, 20px per `text-sm` description line, `gap-2`, an action row `mt-2` + `h-8` button): one-line description without action 164px, with one action **212px**, two lines without action 184px, two lines with an action 232px. A section-level EmptyState box is at most **13.25rem (212px)**, measured in OBS at the container's real width. So `compact` gets **at most a one-line description and one optional action**; anything that does not fit uses `inline`. The AI-reviews look-alike is deleted and its two uses move onto the shared component.

**Worked example — custom fields (RD-4).** `CustomFieldSection` renders the same empty state in four places: the fee-note detail section, the client and matter Fields tabs, and the task-detail Sheet. Its `fieldsByGroup.size === 0` branch becomes `<EmptyState variant="inline" icon={ListChecks} title="No custom fields configured" description=… actionLabel="Configure Fields" actionHref={settingsHref(slug, "matters", "custom-fields")} />`: one row in all four places, the same text, and the same ungated link as today. `matter-fields-tab.tsx:37-48`'s hand-rolled block takes the same treatment and keeps `data-testid="matter-fields-tab"` on its wrapper. The description stays visible: founder decision FD-7, ruled 2026-11-22 as recommended (the copy freeze, at the cost of a second line in the Sheet).

**Rationale**:

The failure mode Phase 90 left is "nobody chose a size", so the remedy is to make the choice explicit at every site, by the slice that observes that site, and then to make it impossible to forget. A guard that resolves imports checks exactly the shared component; the allowlist matches the phase's slicing (a page's empties are decided when the page is observed); tightening the type at the end turns the guard's job into the compiler's. The default stays `"default"` during the ratchet so nothing changes size without a person deciding it.

**Consequences**:

- Positive:
  - One rule, stated in the component and enforced by a test; section-level empty states stop dominating small containers.
  - The custom-fields state looks right in a narrow Sheet without any gating change.
  - After E9B the type itself requires the choice.
- Negative:
  - An allowlist exists for most of Phase 91 and must be maintained in every surface slice until E9B empties it.
  - `inline` titles are not headings: a site whose title e2e reads as a heading must stay `default` or `compact` (each slice greps before moving a site).
  - `default`/`compact` keep rendering their title as `h2`, which can sit oddly in the heading outline inside cards; changing it would break heading-based e2e (e.g. `project-crud.spec.ts:144`) and is left alone.

**Related**: [ADR-367](ADR-367-design-system-layering.md) (EmptyState stays in `frontend/components` until the portal phase), [ADR-368](ADR-368-admincn-template-adoption.md) (empty-state-v2 adaptation), [ADR-371](ADR-371-dark-mode-light-default.md) (the dark-mode guard this one is modelled on); [architecture §18.2.3, §18.3.3, §18.8.3](../architecture/phase91-ui-modernization-ii.md); founder decisions RD-4, FD-7; slices E3A, E3B, every surface slice, and E9B.
