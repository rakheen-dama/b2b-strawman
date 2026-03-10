# ADR-170: Sidebar Zone Structure

**Status**: Accepted
**Date**: 2026-03-10
**Phase**: Phase 44 — Navigation Zones, Command Palette & Settings Modernization

## Context

The DocTeams sidebar has grown to 15 top-level navigation items over 43 phases. The items are listed in a flat, ungrouped list with no visual hierarchy or logical sectioning. Research on cognitive load (Miller's Law) suggests that 5-7 items is the maximum a user can comfortably scan without grouping. At 15 items, users must read the entire list to find what they need — there is no progressive disclosure and no way to skip past irrelevant sections.

The items also grew in an order determined by when features were built, not by how users think about the product. "Team" sits between "Customers" and "Notifications" despite being conceptually unrelated to either. Financial items (Invoices, Profitability, Reports) are scattered across the list.

We need to decide how to structure the sidebar: whether zones are fixed by the product team or configurable by users, and whether zone membership is static or dynamic.

## Options Considered

### 1. **Fixed zones with capability-based visibility (chosen)** — Product-defined groups with items shown/hidden based on user capabilities

- Pros:
  - Consistent experience across all users in an org
  - No configuration UI needed (zero implementation cost for preferences)
  - Zones reflect a curated product mental model (Work → Delivery → Clients → Finance → Team)
  - Empty zones auto-hide via capability filtering — no dead UI
  - Simple data model: a static array of `NavGroup` objects
- Cons:
  - Users cannot customize grouping to match their personal workflow
  - If the product's mental model is wrong, all users suffer

### 2. **User-configurable zones** — Users drag items between custom-named groups, stored in localStorage or backend preferences

- Pros:
  - Maximum flexibility — each user tailors the sidebar
  - Power users can optimize for their workflow
- Cons:
  - Requires drag-and-drop UI, preference storage, and sync across devices
  - New features must be placed somewhere — orphaned items need a default zone
  - Onboarding friction: new users face an empty or arbitrary sidebar until they configure it
  - Backend storage needed for cross-device sync (violates frontend-only constraint)
  - Significant implementation complexity for a navigation restructure

### 3. **Role-based zones** — Different zone configurations per org role (owner sees Finance, member does not)

- Pros:
  - Tailored sidebar per role without per-user configuration
  - Admins see admin-relevant items grouped together
- Cons:
  - Roles don't map cleanly to zones — a member with `INVOICING` capability needs the Finance zone
  - Capability system already handles item visibility; role-based zones would be redundant
  - More configuration to maintain as roles evolve
  - Conflates navigation structure with access control

## Decision

Use **Option 1** — fixed zones with capability-based visibility. The product defines 5 zones (Work, Delivery, Clients, Finance, Team & Resources) plus a utility footer (Notifications, Settings). Individual items within zones are shown or hidden based on the user's capabilities via the existing `useCapabilities()` hook. If all items in a zone are hidden, the entire zone (header and items) is not rendered.

## Rationale

Fixed zones are the right choice for a B2B SaaS product at this stage. The zone structure reflects a universal mental model for professional services: daily work, project delivery, client management, financials, and team/resources. This mental model is shared across accounting firms, consulting firms, and legal practices — the product's target verticals.

The existing capability system already solves the "show relevant items" problem at the item level. Zones simply add a grouping layer on top. A member without `INVOICING` or `FINANCIAL_VISIBILITY` capabilities sees no Finance zone items, so the zone auto-hides. This is capability-driven progressive disclosure without any new configuration surface.

User-configurable zones would add substantial complexity (drag-and-drop, preference storage, migration handling for new items) for a feature that benefits only power users who disagree with the product's grouping. If this becomes a real complaint, localStorage persistence of expand/collapse state is a trivial follow-up that addresses the most common need (hiding zones you rarely use) without full customization.

## Consequences

- **Positive**: Consistent sidebar experience across all users; zero configuration required; zones auto-adapt to capability profile
- **Positive**: No new backend endpoints or database schema needed (stays frontend-only)
- **Positive**: Clean extension point — adding a new nav item means adding it to the right zone in `NAV_GROUPS`
- **Negative**: Users cannot reorder items or create custom groups — the product's mental model is imposed
- **Neutral**: If sidebar collapse state persistence is requested later, it can be added via localStorage without changing the zone architecture
