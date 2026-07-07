# Fix Spec: LZKC-003 — "Create a engagement letter" (indefinite article not adjusted)

## Problem
Day 7 / 7.2: the engagement-letter create dialog description reads "Create a engagement letter…" — the hardcoded article "a" is wrong once legal-za substitutes `proposal → engagement letter` (vowel sound).

## Root Cause (verified)
- `frontend/components/proposals/create-proposal-dialog.tsx:212` — `<TerminologyText template="Create a {proposal} for a client engagement." />`
- `frontend/app/(app)/org/[slug]/proposals/page.tsx:107` — `<TerminologyText template="Create a {proposal} to start tracking client engagements." />`
- `frontend/components/terminology-text.tsx:21` does a blind placeholder replace; there is NO article ("a/an") logic anywhere in the terminology surface (`packages/shared/src/terminology-map.ts` has none).
- Sibling audit: only these two sites break — `invoice-generation-dialog.tsx:80` ("a new {invoice}"), `billing-runs/page.tsx:109` (plural), `project-health-widget.tsx:74` ("a matter", consonant) are all safe. `proposal` is the only legal-za term that is vowel-initial AND directly follows "a".

## Fix
Add article-aware substitution and use it at the two sites:
1. `frontend/components/terminology-text.tsx`: support an article token in templates, e.g. `{a proposal}` → resolve term via `t()`, then emit `"an "` if the resolved term starts with a vowel sound (simple `/^[aeiou]/i` test is sufficient for this term set), else `"a "`.
2. Update the two templates to `"Create {a proposal} …"`.
(Alternative: a `tArticle(term)` helper in `frontend/lib/terminology.tsx` — pick whichever is cleaner; token approach keeps RSC-safe usage.)

Mirror the same token support in `portal/lib/terminology` only if portal templates use "a {term}" — none found; skip.

## Scope
Frontend only
Files to modify: `frontend/components/terminology-text.tsx`, `frontend/components/proposals/create-proposal-dialog.tsx`, `frontend/app/(app)/org/[slug]/proposals/page.tsx`
Files to create: none
Migration needed: no

## Verification
Open /proposals and the create dialog on the legal-za tenant: copy reads "Create an engagement letter…". On a non-legal tenant: still "Create a proposal…". Vitest unit test for the article token (vowel + consonant cases).

## Estimated Effort
S (< 30 min)
