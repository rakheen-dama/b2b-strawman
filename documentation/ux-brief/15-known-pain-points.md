# Known Pain Points & UX Improvement Opportunities

These are areas the founder and development team have identified as problematic or underserving users. The UX expert should pay special attention to these.

---

## Critical Pain Points

### 1. Document Template Editing
**Problem**: Templates are edited as raw HTML/Thymeleaf code. Non-technical users (office managers, paralegals) cannot create or customize templates.
**Impact**: A revenue-generating product surface feels like a developer tool.
**Context**: Phase 31 addresses this with a WYSIWYG editor (Tiptap), but the UX expert should think about the template authoring experience holistically — not just "add a rich editor."
**Questions for the designer**:
- How should template editing feel? (Word-like? Notion-like? Canva-like?)
- How do variables (customer.name, invoice.total) get inserted naturally?
- How do clauses (reusable text blocks) integrate into the editing flow?

### 2. Clause Visibility
**Problem**: System clauses (pre-built standard terms) cannot be read without cloning them. The clause library shows titles and descriptions only — the actual legal text is hidden.
**Impact**: Users can't make informed decisions about which clauses to include in templates.
**Questions**: How should clause content be surfaced? Inline expansion? Side panel? Always visible?

### 3. Custom Fields UX
**Problem**: Custom fields exist (field definitions, field groups, conditional visibility) but the management interface is basic and the in-context editing experience is rough.
**Impact**: A powerful extensibility feature feels bolted-on rather than integrated.
**Questions**: How should custom fields appear in entity forms? Separate section or integrated with standard fields? How should field group management work?

---

## Moderate Pain Points

### 4. Settings Sprawl
**Problem**: 12+ settings pages with no clear hierarchy. Users don't know where to find things.
**Impact**: Configuration tasks feel scattered. "Where do I set up my billing rate?" requires hunting through sidebar.
**Questions**: Should settings be reorganized? Grouped differently? Searchable? Contextual (accessible from where they're used)?

### 5. Navigation Overload
**Problem**: 14 top-level sidebar items. For a new user, it's overwhelming. For a daily user, the items they need most may be buried.
**Impact**: Information architecture doesn't scale — more features = more sidebar items.
**Questions**: Should navigation be role-based? Collapsible sections? Favorites/pinned items? Should some items be nested (e.g., Retainers under Customers)?

### 6. Dashboard vs My Work
**Problem**: Two "home" pages — Dashboard (org-wide) and My Work (personal). Users may not understand when to use which.
**Impact**: Landing experience is unclear.
**Questions**: Should these be merged? Should the landing page be role-dependent?

### 7. Invoice Generation Flow
**Problem**: The path from "customer has unbilled time" to "invoice sent" requires multiple steps across different pages.
**Impact**: The most critical revenue flow feels fragmented.
**Questions**: Can this be streamlined? Should there be a dedicated "billing" workflow?

---

## Opportunity Areas

### 8. Mobile Experience
**Current**: Responsive (works on mobile) but not mobile-optimized. Time logging on a phone is awkward.
**Opportunity**: Time tracking is the feature most likely used on mobile. A dedicated mobile time-logging experience could be a differentiator.

### 9. Onboarding (New Org)
**Current**: After creating an org, users land on an empty dashboard with no guidance.
**Opportunity**: Setup wizard or guided onboarding flow (add team → set rates → create first customer → create project).

### 10. Cross-Entity Navigation
**Current**: Moving between related entities (customer → their projects → a project's invoices → back) requires navigating through list pages.
**Opportunity**: Better contextual links, breadcrumbs, or "related" panels.

### 11. Bulk Operations
**Current**: Most operations are one-at-a-time (invoice one by one, assign tasks one by one).
**Opportunity**: Multi-select and bulk actions on list pages (approve multiple invoices, assign multiple tasks).

### 12. Real-Time Feedback
**Current**: After actions (create, update, delete), feedback is a toast notification.
**Opportunity**: Optimistic updates, inline confirmations, better loading states.

---

## Founder Quotes (Verbatim)
- "First impressions last" — the document/template experience is a first-impression surface
- "Without this being really good I'm afraid a key part of the product is slop"
- "Templates and clauses are all a disaster on UI"
- Custom fields "saw no UX improvement" (Phase 23 was backend-focused)
- "Horrendous" (referring to the template editing experience)

---

## What the Designer Should NOT Change
- **Backend API** — all endpoints are fixed
- **Auth model** — Clerk for main app, magic links for portal
- **Entity model** — the data structures and relationships are set
- **Feature scope** — don't add features, just redesign how existing features are presented
- **Multi-tenancy** — org switching, schema isolation, per-org data

The goal is a new skin and interaction model on the same bones.
