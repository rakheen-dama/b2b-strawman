# Slop hunt — PR #1228: fix(OBS-404): rebrand portal DocTeams → Kazi + guardrail

**Batch**: E — bookkeeping/test-fix
**Reviewed**: 2026-05-01
**Verdict**: NIT (guardrail has a coverage gap)

## PR description vs diff

Description claims 3 hardcoded portal sites replaced with `BRAND_NAME` constant + a Vitest guardrail that fails if "DocTeams" leaks into `portal/{app,components,lib}/`. Diff matches: 3 files changed (acceptance-page, login page, portal-footer), 1 new constant (`portal/lib/brand.ts`), 1 new test (`portal/lib/__tests__/brand.test.ts`). The PR correctly excludes out-of-scope leaks (frontend marketing testimonials, mock-auth layout, backend assistant prompts) and tracks them for future cycles. Scope honest.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | MEDIUM | Weak guardrail | `portal/lib/__tests__/brand.test.ts:18` | Roots scanned are limited to `["app", "components", "lib"]`. The portal root also has `hooks/`, `middleware.ts`, `e2e/`, and `public/` directories (and root-level `next.config.ts`, `vitest.setup.ts`). A future "DocTeams" introduced in `portal/hooks/use-branding.ts` or `portal/middleware.ts` would NOT be caught by the guardrail. The PR description claims the guardrail prevents the brand from "leaking back into `portal/{app,components,lib}/`" — accurate to the code but undersells the gap; the actual guardrail surface should be the entire `portal/` source tree. | Extend `roots` to include `hooks`, walk `middleware.ts` directly, and scan files in the portal root. Keep `node_modules` / `.next` / `__tests__` exclusions. |
| 2 | LOW | Weak guardrail | `portal/lib/__tests__/brand.test.ts:34` | File-extension filter is `/\.(tsx?|jsx?)$/` — does not match `.json` (e.g. `manifest.json` has app metadata) or `.mdx` / `.html` / `.svg` (alt text). Low-impact today but allows a brand string in `app/manifest.json` or an alt attribute baked into an `.svg`. | Optional: add `.json|.mdx|.html|.svg` to the regex, or accept that the guardrail is code-only by design. |
| 3 | LOW | Hook race | `portal/components/portal-footer.tsx:14` | `BRAND_NAME` replaces `"DocTeams"` in the static fallback. The `useBranding()` hook (which produces `footerText`) still renders raw `footerText` on line 13 — meaning a tenant whose `org_settings.footer_text` says "DocTeams" would still leak. Out-of-scope for this PR (data-side leak vs code-side leak), but worth tracking. | Consider a separate text-sanitisation pass on tenant-supplied branding fields if the user-input surface ever accepted that string. Today it's manual setup so probably fine. |

## Notes

The brand guardrail concept is correct and the implementation will catch the most common reintroduction surface (`app/`, `components/`). Finding #1 is the most material — the rebrand intent is "no DocTeams in portal source", and the test undersells that. Recommend extending the walk roots before relying on this as the project's brand defence-in-depth.

The Kazi/b2mash mapping matches `feedback_product_name_kazi.md`. No scope creep into the (out-of-scope) frontend testimonials file or backend assistant prompts.
