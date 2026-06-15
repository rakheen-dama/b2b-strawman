# AIVERIFY-011 (DECIDED — UI fix) — Members with AI_EXECUTE can't discover/reach AI from the sidebar; matter-intake shows a false "not configured" state

- **Stage**: V10.1 (capability / RBAC gating — AI_EXECUTE)
- **Severity**: Medium (functional gap — members can execute AI but the UI hides the entry point)
- **Owner**: Dev
- **Scope**: Frontend only (no backend change, no migration)
- **Status**: SPEC_READY
- **Effort**: S

---

## Product decision (authorized by user, status log 2026-06-15 DECISIONS)

**Members CAN execute AI.** The backend deliberately grants `AI_EXECUTE` to the `member` role (`backend/.../db/migration/tenant/V122__ai_foundation.sql:141-148`, explicit comment "Member: AI_EXECUTE (REQUIRED…)"). Confirmed live in the test tenant: `tenant_c6107524c9b4.org_role_capabilities` for `slug='member'` contains **`AI_EXECUTE`**, V122 applied (`flyway_schema_history` version 122 success=t). So the backend grant is correct and stays. Owners/admins keep **exclusive** `AI_REVIEW` (gate approval) and `AI_MANAGE` (AI profile + budget). The bug is purely in the UI entry points for members.

## Problem

A plain member (Pieter, `pieter@verifain-test.local`) has `AI_EXECUTE` but the UI gives them no sidebar entry to the AI area, and on the new-matter page the AI intake button shows a misleading "Connect an Anthropic API key" disabled state even though the firm IS configured.

## Root Cause (confirmed in code)

### Finding A — the AI nav group has NO entry for `AI_EXECUTE` (the real "no AI nav" bug)

`frontend/lib/nav-items.ts:303-316` — the `ai` nav group contains a **single** item, gated on `AI_REVIEW`:

```ts
{
  id: "ai", label: "AI", defaultExpanded: false,
  items: [
    { label: "AI Reviews", href: (slug) => `/org/${slug}/ai/reviews`, icon: Brain,
      exact: true, requiredCapability: "AI_REVIEW" },
  ],
},
```

There is **no** nav item gated on `AI_EXECUTE`. Members (who lack `AI_REVIEW`) therefore see the entire AI group collapse to empty and get no AI sidebar entry at all — matching screenshot `v10-dashboard-pieter-no-ai-nav.png`. (The sidebar filters items by `requiredCapability` via the capability context; a group with no visible items renders nothing.)

### Finding B — the skill BUTTONS are already correctly gated on `AI_EXECUTE` (no change needed there)

Contrary to the initial QA framing, the per-skill trigger buttons are **already** gated on `AI_EXECUTE` on every skill surface, with explicit "MEMBER with AI_EXECUTE" comments:

- FICA verification — `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx:642` (`caps.capabilities.includes("AI_EXECUTE")` → renders `FicaVerificationPanel`).
- Matter intake — `frontend/app/(app)/org/[slug]/projects/new/page.tsx:31` (`canExecuteAi`) → `NewMatterPageClient` renders `MatterIntakePanel` (`new-matter-page-client.tsx:259-262`, "gated by AI_EXECUTE").
- Contract review + drafting — `frontend/components/documents/documents-panel.tsx:144-159,361,497,524` (`canExecuteAi` prop, set from `AI_EXECUTE` in `projects/[id]/page.tsx:155`).
- Compliance audit — `frontend/app/(app)/org/[slug]/compliance/page.tsx:33` (`canExecuteAi`).

So members who reach these pages WILL see the buttons. The QA "no skill buttons" observation was driven by (a) the missing nav entry (Finding A) blocking discovery, and (b) Finding C below on the new-matter page.

### Finding C — new-matter page shows a FALSE "not configured" disabled intake button for members

`frontend/app/(app)/org/[slug]/projects/new/page.tsx:35-41` computes `isAiConfigured` by calling `getAiProfile()` **unconditionally**:

```ts
let isAiConfigured = false;
try {
  const profile = await getAiProfile();
  isAiConfigured = profile.coldStartCompleted;
} catch { /* Non-fatal */ }
```

But `GET /api/ai/profile` is `@RequiresCapability("AI_MANAGE")` (`backend/.../AiFirmProfileController.java:27-29`). A member lacks `AI_MANAGE`, so the call **403s**, the catch swallows it, and `isAiConfigured` stays `false`. The `MatterIntakePanel` then renders its button **disabled** with the tooltip "Connect an Anthropic API key in Settings > AI…" (`matter-intake-panel.tsx:38-41`) — even though the firm is fully configured. The member is wrongly told to go configure a key they can't access.

Note the **correct** pattern already exists on the customer and compliance pages:
`customers/[id]/page.tsx:363-375` and `compliance/page.tsx:36-46` only call `getAiProfile()` when the user has `AI_MANAGE`, and otherwise set `isAiConfigured = true` for "MEMBER with AI_EXECUTE" (a member couldn't have `AI_EXECUTE` without cold-start having been done). `projects/new/page.tsx` was missed and does not follow this pattern.

## Capability source of truth (for reviewer context)

Frontend capabilities arrive from `GET /api/me/capabilities` (`frontend/lib/api/capabilities.ts`), which returns `RequestScopes.getCapabilities()` (`backend/.../orgrole/OrgRoleService.java:107`), bound per-request by `MemberFilter` from `resolveCapabilities(memberId)` (`MemberFilter.java:70`), which reads the member role's `org_role_capabilities` rows. So the FE array faithfully reflects what the API authorizes — for a member that includes `AI_EXECUTE`. Pages gate on the raw `caps.capabilities.includes("AI_EXECUTE")` (not the owner/admin-short-circuiting `hasCapability`), which is correct here.

## Fix

Frontend-only.

### Fix A — add an `AI_EXECUTE`-gated AI nav item (members get a sidebar entry)

In `frontend/lib/nav-items.ts`, add an item to the `ai` group gated on `AI_EXECUTE` that points members at an AI surface they can act on. Recommended target: the new-matter intake or a member-appropriate AI landing. Concretely, add an "AI Intake" (or "AI Assistant") entry:

```ts
{
  id: "ai", label: "AI", defaultExpanded: false,
  items: [
    { label: "AI Intake", href: (slug) => `/org/${slug}/projects/new`, icon: Sparkles,
      requiredCapability: "AI_EXECUTE" },
    { label: "AI Reviews", href: (slug) => `/org/${slug}/ai/reviews`, icon: Brain,
      exact: true, requiredCapability: "AI_REVIEW" },
  ],
},
```

(Label/icon/target are an architectural/product call — confirm with orchestrator. The load-bearing requirement: **at least one AI nav item gated on `AI_EXECUTE`** so members see the AI group. Use an existing imported icon, e.g. `Sparkles` or `Brain`; add the import if needed. Keep "AI Reviews" gated on `AI_REVIEW` — owners/admins only.) Do NOT gate the new item on `AI_REVIEW` or `AI_MANAGE`.

### Fix C — stop the false "not configured" state on the new-matter page for members

In `frontend/app/(app)/org/[slug]/projects/new/page.tsx`, mirror the customer/compliance pattern so members don't trigger the 403:

```ts
let isAiConfigured = false;
if (caps.capabilities.includes("AI_MANAGE")) {
  try {
    const profile = await getAiProfile();
    isAiConfigured = profile.coldStartCompleted;
  } catch { /* Non-fatal */ }
} else if (canExecuteAi) {
  // MEMBER with AI_EXECUTE: they couldn't have this capability without cold-start being done.
  isAiConfigured = true;
}
```

### Do NOT change

- The skill-button gating (Finding B) — already correct on `AI_EXECUTE` across customers / projects[id] / compliance / documents. No edits there.
- Gate-approval UI (`ExecutionGateCard`, `/ai/reviews`) — stays `AI_REVIEW` (owner/admin). Verified gated: panels only render gate cards when `canReviewGates` (`fica-verification-panel.tsx:168`, `matter-intake-panel.tsx:172`); nav "AI Reviews" stays `AI_REVIEW`.
- AI profile / budget UI (`/settings/ai`) — stays `AI_MANAGE` (owner/admin). The settings nav card is `adminOnly` (`nav-items.ts:501-506`) and the backend GET/PUT are `AI_MANAGE`-gated.
- Backend V122 seed — correct, keep it.

## Scope (files)

- `frontend/lib/nav-items.ts` — add an `AI_EXECUTE`-gated AI nav item (+ icon import if needed).
- `frontend/app/(app)/org/[slug]/projects/new/page.tsx` — guard `getAiProfile()` behind `AI_MANAGE`; member-with-`AI_EXECUTE` ⇒ `isAiConfigured = true`.

## Tests (stub-based — `pnpm test`)

- Nav: a test over `NAV_GROUPS` asserting the `ai` group contains an item with `requiredCapability === "AI_EXECUTE"` AND retains the `AI_REVIEW` "AI Reviews" item (regression guard so neither gate drifts).
- If a sidebar-render test exists for capability filtering, extend it: a member (caps `["AI_EXECUTE"]`, no `AI_REVIEW`) sees the AI group with the AI_EXECUTE item; an owner sees both.

## Verification (LIVE — mandatory)

1. Log in as member **Pieter** (`pieter@verifain-test.local`) on the cycle-branch stack.
2. Sidebar: the **AI** group is visible with the `AI_EXECUTE` item (no "AI Reviews", no AI settings card).
3. Navigate to a customer detail with documents → **FICA "Verify with AI"** button is visible/enabled. Navigate to **new matter** (`/projects/new`) → **"Get AI Recommendations"** is enabled (NOT the false "Connect an API key" disabled state). Open a project's Documents → **"Draft with AI"** / contract-review buttons visible.
4. Trigger one skill (e.g. FICA verify) → backend returns **200**, execution COMPLETED + metered (live-Claude). (Confirms member can actually execute, end-to-end.)
5. Negative controls still hold (re-confirm 10.2 / 10.3): Pieter still gets a clean **403 + UI gating** on gate approval (`/ai/reviews` not in his nav; approve action denied) and on AI profile edit (`/settings/ai` card hidden; `GET/PUT /api/ai/profile` 403).
6. Re-verify **V10.1** (authorized-amended to "members CAN execute").

Mark VERIFIED only after the live member skill invocation returns 200 AND the gate-approve / profile-edit denials are observed.

## PR

One PR (CLAUDE.md §7), independent of AIVERIFY-010. Frontend-only → merge bar `pnpm lint && pnpm build && pnpm test` all green.
