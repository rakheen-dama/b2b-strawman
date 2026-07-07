# V10 — Capability / RBAC gating (resume verify, 2026-06-15)

**Cycle**: AI Core Live-Claude Verification (Keycloak)
**Actor**: Pieter Botha (plain **member**, has AI_EXECUTE only — NO AI_REVIEW / AI_MANAGE)
**Login**: `pieter@verifain-test.local` / `SecureP@ss2` (member id `fffaa27f-ce55-474c-a120-3bf4726a76fa`)
**Driver**: Playwright MCP (signed out Nomsa via gateway `/logout` + `context.clearCookies()` to drop the httpOnly BFF session, then fresh KC login as Pieter)

## V10.1 — Members CAN execute AI (AIVERIFY-011, authorized scenario amendment) — ✅ PASS / VERIFIED

The user-authorized amendment: members WITH `AI_EXECUTE` should see AI nav + skill buttons and be able to execute, while AI_REVIEW (approve) + AI_MANAGE (profile/budget) stay owner/admin-only.

### Part A — AI nav item now VISIBLE (was absent at V0)
- Pieter's sidebar now has an **"AI"** group containing an **"AI Intake"** item (→ `/org/verifain-attorneys/projects/new`), AI_EXECUTE-gated. At V0 Pieter had NO AI nav at all. The AI_REVIEW "AI Reviews" item is correctly NOT present for him.
- Evidence: `v10-pieter-ai-intake-nav-visible.png`.

### Part C — new-matter intake is ENABLED (no false "Connect an API key")
- `/projects/new` renders the "AI Matter Intake" panel + "Get AI Recommendations" button for Pieter. Button is disabled only until a customer + ≥20-char description are entered (normal initial state) — **NOT** the prior false "Connect an Anthropic API key" disabled state. Confirms `getAiProfile()` no longer 403s for the member (guarded behind AI_MANAGE; member ⇒ isAiConfigured=true).

### Part B/D — Pieter triggers a skill → 200 + metered (live, attributed to Pieter)
- Selected Sipho Dlamini + a debt-recovery description, clicked "Get AI Recommendations".
- UI rendered substantive matter-specific output (classification COLLECTIONS, recommended template, fee estimate R16 500–R49 500, conflict screening, prescription warnings), **"Cost: R 1.56"**, "Completed in 93216ms".
- DB (`ai_executions`, latest): `matter-intake | COMPLETED | cost_cents=156 | input_tokens=1186 | output_tokens=4719 | invoked_by=fffaa27f… (Pieter) | 2026-06-15 10:27:50`.
- **Non-zero cost + tokens + correct member attribution = live, metered, member-executed.** Stub ruled out.
- Evidence: `v10-1-pieter-matter-intake-completed-metered.png`.

## V10.2 — Pieter blocked from AI_REVIEW (must still hold) — ✅ PASS
- `/ai/reviews` as Pieter → content shows **"You do not have permission to review AI actions. Contact your administrator."** No review queue / approve / reject controls. The fix did not over-expose approval.
- Evidence: `v10-2-pieter-ai-reviews-denied.png`.

## V10.3 — Pieter blocked from AI_MANAGE / profile edit (must still hold) — ✅ PASS
- `/settings/ai` as Pieter → content shows **"You do not have permission to manage AI settings. Contact your administrator."** The configuration form (Practice Areas / Risk Calibration / Monthly Budget / Save) does NOT render. The fix did not over-expose profile/budget editing.
- Evidence: `v10-3-pieter-ai-settings-denied.png`.

## Verdict
**V10.1 = PASS** — member sees AI nav + working intake AND executes a skill (200, metered, attributed) AND is still blocked from review (10.2) and manage (10.3). **AIVERIFY-011 → VERIFIED.** No privilege over-exposure.
