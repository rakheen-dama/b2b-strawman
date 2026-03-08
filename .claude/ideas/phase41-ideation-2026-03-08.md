# Phase 41 Ideation — Organisation Roles & Capability-Based Permissions
**Date**: 2026-03-08

## Lighthouse Domain
Universal across all verticals. The 3-role model (Owner/Admin/Member) is the #1 complaint from 10-50 person firms. Every competitor offers either custom roles (Productive.io, Scoro) or permission toggles (Harvest). This is table stakes for practice management SaaS.

## Decision Rationale
Founder came in knowing this was next — org roles had been discussed previously. Competitive analysis confirmed the pattern: fixed tiers + granular toggles, with custom roles as named presets. Harvest's "Manager with opt-in toggles" was the starting inspiration, evolved into Productive.io-style custom role presets with per-user overrides.

### Key Design Choices
1. **Application-level, not Keycloak** — hard constraint. Keycloak still stabilizing after Phase 36. Custom roles and capabilities resolved entirely from tenant DB. Keycloak only knows owner/admin/member.
2. **7 fixed capabilities** — Financial Visibility, Invoicing, Project Management, Team Oversight, Customer Management, Automations, Resource Planning. Product-defined, not user-configurable.
3. **Custom roles = named presets** — firms create their own vocabulary ("Project Manager", "Billing Clerk", "Senior Associate"). Each preset is a flat set of capability toggles.
4. **Per-user overrides** — additive/subtractive from preset. Stored as `+CAP`/`-CAP` entries.
5. **Preset edits cascade** — changing a role's capabilities updates all users on that role. Overrides preserved.
6. **Owner/Admin bypass** — capability checks only apply to custom-role members. System roles short-circuit to all-access.

## Founder Preferences (Confirmed)
- Presets with per-user override (not just toggles, not just presets)
- 7 capability areas approved without modification
- Cascade on preset edit confirmed
- No Keycloak changes (hard constraint)

## Phase Roadmap (Updated)
- Phase 40: Bulk Billing & Batch Operations (in progress)
- **Phase 41: Organisation Roles & Capability-Based Permissions** (spec written)
- Phase 42: Reporting & Data Export (candidate)

## Estimated Scope
~5-6 epics, ~12-15 slices. OrgRole entity + capabilities join table + member overrides. @RequiresCapability annotation replacing ~50 @PreAuthorize annotations. Settings UI for role management. Team page enhancement with role assignment panel. Frontend CapabilityProvider context for sidebar/page/component gating.
