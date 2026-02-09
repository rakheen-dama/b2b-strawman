# ADR-014: Plan Enforcement Strategy

**Status**: Accepted

**Context**: Plan limits (member count, feature access) must be enforced consistently across the stack. Enforcement at a single layer creates bypass risks — a malicious or buggy client could circumvent frontend-only checks, and a backend-only approach provides no user feedback until the operation fails. The system must prevent Starter orgs from exceeding 2 members and Pro orgs from exceeding 10, and must gate Pro-only features appropriately.

**Options Considered**:

1. **Frontend-only enforcement** — Use Clerk's `has()` and `<Protect>` components to hide features and prevent actions.
   - Pros: Immediate user feedback; good UX (features are hidden, not failed).
   - Cons: Easily bypassed via direct API calls; no server-side guarantee; a bug in UI gating = limit violation.

2. **Backend-only enforcement** — Validate all limits and entitlements in Spring Boot services.
   - Pros: Authoritative enforcement; cannot be bypassed by clients.
   - Cons: Poor UX (user attempts an action, gets a 403 error); no proactive guidance (e.g., "Upgrade to Pro"); latency to discover limit violations (requires API round-trip).

3. **Three-layer enforcement: Clerk + Backend + Frontend** — Clerk enforces member limits at the invitation/join level; backend validates on every mutating operation; frontend gates UI elements and surfaces upgrade prompts.
   - Pros: Defense-in-depth; good UX (frontend prevents most violations proactively); authoritative backend validation catches edge cases; Clerk provides first-class member limit enforcement.
   - Cons: Three places to maintain limit definitions (can drift); slightly more implementation work.

**Decision**: Three-layer enforcement.

**Rationale**: Each layer serves a distinct purpose:
- **Clerk (first line)**: Member limits are configured per-plan in the Clerk Dashboard. When a Starter org reaches 2 members, Clerk blocks additional invitations at the platform level. This prevents the most common limit violation without any application code.
- **Backend (authoritative)**: The `MemberSyncService` validates member count against `Organization.tier` before creating a member. Even if Clerk's limit is misconfigured or a race condition allows an extra invitation, the backend rejects the sync. Feature-gated endpoints check `Organization.tier` and return `403 Forbidden` with a `PlanLimitExceededException` that includes an upgrade prompt.
- **Frontend (UX)**: `<Protect plan="pro">` hides Pro-only UI elements. `has({ plan: 'pro' })` in server components conditionally renders features. `<UpgradePrompt>` component shown as fallback when Starter users encounter gated features.

Limit definitions are maintained in two places:
1. Clerk Dashboard (member limits per plan — enforced by Clerk).
2. Application constants or `Organization.tier` checks (backend enforcement).

The risk of drift is low because limits are simple (2 vs 10 members) and rarely change. A future improvement could read limits from Clerk's plan features API to maintain a single source of truth.

**Member Limit Enforcement**:

| Layer | Mechanism | What It Catches |
|-------|-----------|-----------------|
| Clerk | Per-plan member limit in Dashboard | Blocks invitations when at limit |
| Backend | `MemberSyncService` count check | Rejects sync if member count exceeds tier limit |
| Frontend | `useSubscription()` + conditional UI | Hides "Invite" button when at limit |

**Feature Gating**:

| Layer | Mechanism | Example |
|-------|-----------|---------|
| Frontend | `has({ plan: 'pro' })` | Hide "Advanced Settings" for Starter |
| Frontend | `<Protect plan="pro" fallback={<UpgradePrompt />}>` | Show upgrade CTA instead of Pro feature |
| Backend | `Organization.tier` check in service layer | Return 403 for Pro-only API endpoints |

**Consequences**:
- New exception: `PlanLimitExceededException` (HTTP 403 with structured body including upgrade URL).
- `MemberSyncService.syncMember()` checks member count against tier limit before creating.
- Frontend components use `has({ plan: 'pro' })` for conditional rendering.
- New `<UpgradePrompt>` component for consistent upgrade CTA across the app.
- Clerk Dashboard must have member limits configured correctly per plan (operational requirement).
- Plan limit constants defined in a `PlanLimits` utility class for backend enforcement.
