# ADR-355: Owner-Only Capabilities Mirrored Client-Side — `OWNER_ONLY` in `lib/capabilities.tsx`, `hasCapability` Denies Admins, Server Actions Never Widen

**Status**: Accepted

**Context**:

Bob (Admin) was offered the matter-closure override and then got a 403 toast (G-D7). The backend is correct: `OVERRIDE_MATTER_CLOSURE`, `MANAGE_COMPLIANCE_DESTRUCTIVE` and `FINANCIAL_RECONCILE` are in `Capability.OWNER_ONLY` (`orgrole/Capability.java:67-71`); `OrgRoleService.resolveCapabilities:70-75` gives admin `ALL_NAMES − OWNER_ONLY`; `GET /api/me/capabilities` returns the resolved set plus `isAdmin`/`isOwner`; `MatterClosureService.close:175-178` requires the capability. The frontend widens: `hasCapability = (cap) => isAdmin || isOwner || capSet.has(cap)` (`lib/capabilities.tsx:119-121`, comment "defense-in-depth", pinned by `__tests__/capabilities.test.tsx:92-108` "isAdmin bypasses capability check"), and the server action `matter-closure-actions.ts:51-53` repeats `caps.isOwner || caps.isAdmin || …`. The correct idiom exists at two sites (`invoices/[id]/page.tsx:145`, `settings/connections/xero/sync-log/page.tsx:27`: `caps.isOwner || caps.capabilities.includes("FINANCIAL_RECONCILE")`). The frontend `CAPABILITIES` map covers **10 of 27** backend capabilities (`.arch-context.md` §10.5), so most gate names are untyped strings. R7 §Q6 classed this as a capability-helper bug any owner-only capability can hit, not a dialog bug.

**Options Considered**:

1. **Mirror `OWNER_ONLY` (three names) in `lib/capabilities.tsx`; `hasCapability = isOwner || (isAdmin && !OWNER_ONLY.has(cap)) || capSet.has(cap)`; fix the server action the same way; pin the three names against `Capability.java` in vitest (CHOSEN).**
   - Pros:
     - Fixes the class, not the symptom.
     - Keeps the no-flicker behaviour the admin short-circuit exists for (an admin's gates render before `/api/me/capabilities` resolves).
     - One small set kept in sync the way `promoted-field-slugs.ts` mirrors `PromotedFieldSlugs.java`.
     - A vitest pin that reads `Capability.java` catches drift in CI.
   - Cons:
     - A mirror is a mirror.
     - Adding a fourth owner-only capability needs a frontend edit (the pin fails the build until it is made).
2. **Fetch effective capabilities (and an `ownerOnly` list) from the backend and trust them: `hasCapability = capSet.has(cap)`.**
   - Pros:
     - Single source of truth.
     - No mirror.
   - Cons:
     - `capabilities.test.tsx:103` and `capability-gating.test.tsx` mount `CapabilityProvider` with `capabilities={[]}` and `isAdmin` for admins.
     - Any layout that does the same would regress to "admin sees nothing" until the fetch lands.
     - The no-flicker behaviour is lost.
     - A larger change for the same security outcome, which the backend already enforces.
3. **Keep `isOwner` branching per site (fix the dialog and the server action only).**
   - Pros:
     - Two lines.
   - Cons:
     - Leaves `hasCapability` lying for the other two owner-only capabilities.
     - The next consumer of `MANAGE_COMPLIANCE_DESTRUCTIVE` repeats G-D7.
     - The correct idiom existing in two places and the wrong one in two others is how the bug arrived.

**Decision**: Option 1 — `lib/capabilities.tsx` exports `OWNER_ONLY_CAPABILITIES` (the three names, mirroring `Capability.OWNER_ONLY`); `hasCapability` denies admins those three; `matter-closure-actions.ts` drops `caps.isAdmin` from the override line; no server action ever grants an owner-only capability to admins; owner-only cases added to `frontend/__tests__/lib/capabilities.test.tsx` pin the three names by reading `Capability.java` at test time. Widening the frontend `CAPABILITIES` map (10 of 27 today) is a later change, not part of this decision.

**Rationale**:

1. **The frontend must not be more permissive than the backend.** The backend is the authority and already denies; the frontend's job is to render only what will succeed. A widening helper produces exactly the "offered, then 403" experience a design partner would read as a broken product.
2. **A mirror with pins is the project's established pattern** for enum-like lists that cross the wire (`promoted-field-slugs.ts`, `PromotedFieldSlugs.java`; `legacy-tab-ids.ts`); it keeps zero-fetch rendering and is enforced by CI, not by memory.
3. **Scope stays at the three owner-only names.** The 17 untyped capability strings in the frontend are a separate drift risk (a typo in a gate name silently returns false for members); widening `CAPABILITIES` to all 27 is the role editor's list and is a later change, unrelated to this bug.
4. **Demo-partner timeline:** security-adjacent and independent; the register orders it early (U18) so the closure re-walk on Day 75 sees Step 3 "Cannot close" for Bob.

**Consequences**:

- Positive:
  - Bob's Continue lands on Step 3.
  - `data-testid="matter-closure-override-toggle"` is absent for admins (G-D7).
  - `day-75-85-closure.spec.ts:319` is un-fixme'd.
  - `FINANCIAL_RECONCILE` and `MANAGE_COMPLIANCE_DESTRUCTIVE` gates can use `hasCapability` and drop their hand-rolled `isOwner ||` branches.
- Negative:
  - `frontend/__tests__/lib/capabilities.test.tsx:92-108` changes from "admin always true" to "admin true except OWNER_ONLY", and gains the owner-only cases that read `Capability.java`.
  - `matter-closure-dialog.test.tsx:207-233` gains `isAdmin: true, capabilities: ["CLOSE_MATTER"]`.
  - `capability-gating.test.tsx` re-run.
  - Adding an owner-only capability becomes a two-file change with a failing vitest pin until the mirror is updated.
- Follow-ups:
  - Migrate the two hand-rolled `isOwner || …includes("FINANCIAL_RECONCILE")` sites to `hasCapability` once the mirror lands, so one idiom remains.
- Related:
  - [ADR-T003](ADR-T003-product-layer-roles-over-keycloak-org-roles.md) (product-layer roles and capabilities), [ADR-354](ADR-354-persisted-retention-end-date.md) (the closure flow this gate protects), `tasks/phase88-research/R7-matter-detail-closure.md` §Q6, `tasks/phase88-gap-register.md` U18.
