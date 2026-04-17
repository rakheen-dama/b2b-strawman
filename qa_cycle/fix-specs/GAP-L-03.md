# Fix Spec: GAP-L-03 — KC registration page doesn't show target org name

## Problem

From `day-00.md` §0.20: "form loaded with Email field pre-populated to `thandi@mathebula-test.local`.
Organization name 'Mathebula & Partners' is encoded in the token, but is NOT visibly displayed on
the registration page (the user has no on-screen confirmation of which org they're joining)."

User-impact: during org-invite registration, users cannot visually confirm which organization they
are joining before submitting credentials. This is a trust-signal gap, not a functional defect.

## Root Cause (validated)

Validated via grep/read:

1. **Our custom `Register.tsx` only renders a static title**:
   `compose/keycloak/theme/src/login/pages/Register.tsx` line 18:
   ```tsx
   <Layout title="Create your account">
   ```
   — no branching on `messageHeader` or any org attribute.

2. **The keycloakify reference does render `messageHeader` via `advancedMsg`**:
   `compose/keycloak/theme/node_modules/keycloakify/src/login/pages/Register.tsx` line 50:
   ```tsx
   headerNode={messageHeader !== undefined ? advancedMsg(messageHeader) : msg("registerTitle")}
   ```
   And line 221 of `KcContext.ts` confirms `Register` type has `messageHeader?: string`. During an
   org-invite flow, Keycloak 26 sets `messageHeader` to a templated message like
   `"You are registering as member of organization {0}"` (where `{0}` is the org name) — this is
   KC-core behavior when a `login-actions/action-token` for an `ORGIVT` action is being processed.

3. **No custom KcContext extension exists**: `compose/keycloak/theme/src/login/KcContext.ts` does
   not extend the `Register` page with typed `organization` fields. We can either (a) just render
   `messageHeader` (simplest — KC has already substituted the org name into it) or (b) extend
   `KcContext` to expose a typed `organization` object. Option (a) is sufficient for this cycle.

**Unvalidated hypothesis (documented, not trusted)**: I could not verify with this codebase alone
that KC 26.5 actually injects an org-mentioning `messageHeader` during the invite-register flow
(the KC source is not in this repo). The QA screenshot shows only "Create your account" as the
page heading, which suggests either (i) KC is emitting a `messageHeader` our template ignores —
most likely — or (ii) KC is not emitting one and Phase B below is needed. Dev agent should
check the rendered `kcContext` at runtime (see Verification) and adjust if Phase A proves empty.

## Fix

### Phase A — Render `messageHeader` when present (10 min)

Edit `compose/keycloak/theme/src/login/pages/Register.tsx`:

1. Line 11 — extend destructure to include `messageHeader`:
   ```tsx
   const { url, message, profile, messageHeader } = kcContext;
   ```
2. Line 12 — add `advancedMsgStr` from `i18n`:
   ```tsx
   const { msg, msgStr, advancedMsgStr } = i18n;
   ```
3. Line 18 — make the title dynamic:
   ```tsx
   <Layout title={messageHeader ? advancedMsgStr(messageHeader) : "Create your account"}>
   ```

If KC emits `messageHeader` for org-invite (expected), this alone surfaces the org name. Run
Phase A and verify with the QA checkpoint before investing in Phase B.

### Phase B (only if Phase A shows no change) — Backend injects org name into login URL (40 min)

If Phase A yields no org mention (because KC doesn't emit `messageHeader` for `ORGIVT`), fall back
to plumbing the org name as a query-string hint through our existing bounce page (from GAP-L-01's
spec):

1. Edit `compose/keycloak/themes/docteams/email/html/org-invite.ftl` line 9 (updated version
   from GAP-L-01's Phase 2):
   ```html
   <#assign bounceUrl = "http://localhost:3000/accept-invite?kcUrl="
                       + link?url('UTF-8')
                       + "&orgName=" + organization.name?url('UTF-8')/>
   ```
2. Edit `frontend/app/accept-invite/page.tsx` (from GAP-L-01) to extract `orgName` and:
   - Display "You are registering with **{orgName}**" on the bounce page itself (pre-KC).
   - Persist via sessionStorage so the frontend post-login redirect handler could show a
     confirmation toast.
3. Since our theme can't easily access `sessionStorage` (it runs on KC's origin), the confirmation
   lives on our bounce page — not the KC form itself.

Phase B is a compromise that puts the confirmation on the bounce page rather than the KC
registration form. If truly-on-the-KC-form is required, we'd need a Keycloak SPI
`AuthenticatorFactory` extension (>2hr, out-of-scope this cycle).

## Scope

- **Keycloak theme**: YES (Phase A).
- **Frontend**: YES if Phase B is needed (builds on GAP-L-01's `/accept-invite` page).
- **Email template**: YES if Phase B is needed.
- **Backend / Gateway / Seed / Realm JSON**: NO.

Files to modify:
- Phase A: `compose/keycloak/theme/src/login/pages/Register.tsx` (3 small edits, one file).
- Phase B (conditional): `compose/keycloak/themes/docteams/email/html/org-invite.ftl`,
  `frontend/app/accept-invite/page.tsx` (already created by GAP-L-01).

Files to create: none (reuses `/accept-invite` from GAP-L-01 if Phase B needed).
Migration needed: NO.
JAR rebuild required: YES (for Phase A; can batch with GAP-L-02 rebuild).
KC restart required: YES (once, batched with GAP-L-01/02 restart).

## Verification

**Runtime verification step before writing code**: add a temporary
`<pre>{JSON.stringify({ messageHeader, profile: profile?.attributes }, null, 2)}</pre>` at the
top of `Register.tsx` and reload the invite URL in QA. The rendered `kcContext` will reveal
whether `messageHeader` contains org info. Remove the debug block once confirmed.

**After-fix QA checkpoint**: re-run Day 0 §0.20. Expected: the registration page heading (or
subtitle) mentions "Mathebula & Partners" — not just "Create your account".

## Estimated Effort

**Phase A: S (~10 min)** — three small edits + rebuild.
**Phase B (if needed): M (~40 min)** — email + frontend update; depends on GAP-L-01's bounce
page existing.

Recommend starting with Phase A; only escalate to Phase B if runtime evidence shows
`messageHeader` is empty during the invite register flow.

## Notes

**Not WONT_FIX**: user explicitly called this out as a Product concern (trust-signal missing) and
the fix is small. Keep it in-scope.

**Alternative rejected — Keycloak core patch to always emit org name**: requires forking KC or
writing an SPI; >2hr and high risk.

**Dependency**: Phase B, if needed, reuses the bounce page from GAP-L-01. Dev should implement
GAP-L-01 FIRST, then GAP-L-03's Phase A, then (only if necessary) Phase B.

**Open question (for user)**: is a registration-form-side confirmation strictly required, or is a
bounce-page confirmation acceptable? If the latter, Phase B is trivially sufficient — the user sees
the org name before KC even renders. Default assumption: bounce-page confirmation is acceptable
given this is a LOW-severity cosmetic gap.
