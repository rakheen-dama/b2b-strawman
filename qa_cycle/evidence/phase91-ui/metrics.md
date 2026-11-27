# 670A — §18.1 metrics after (task 670.5)

**Measured on:** branch `phase91/70A-exit-gate` @ `c897562a8` (2026-11-27 13:59 UTC). Its product code is `main` @ `c4595f924`: `git log --oneline c4595f924..HEAD -- backend frontend portal packages gateway compose` prints nothing (the three commits on top are Stage S / M evidence under `qa_cycle/`). **Baseline:** 661B, `main` @ `551778100` ([`baseline/README.md`](baseline/README.md) §1). §18.1's provisional table is not the comparison (P-2).

**Method:**
- Every §18.1 / 661B command is run byte-for-byte from the worktree root under `bash -c` with `B='frontend/app/(app)/org/[slug]'` (git 2.52.0, macOS). The wrapper is [`metrics-run.sh`](metrics-run.sh), committed as executed: `bash qa_cycle/evidence/phase91-ui/metrics-run.sh > qa_cycle/evidence/phase91-ui/metrics-raw.txt`.
- [`metrics-raw.txt`](metrics-raw.txt) holds the command and the full output of every row (listings included), headed `### <n> <metric>`, with each exit code.
- Like-with-like: the rows prefixed `B…` in the raw file re-run the same metrics **on the baseline commit's tree** (`git grep … 551778100 -- …`, no checkout). Every one reproduces the 661B number (71, 22 tables, 33 hex, 4666, 37, 6, 3842), so the before column is re-derived, not copied.
- Reviewers can re-run any row from the worktree root with the command shown in the raw file.

## Before → after

| Metric | Command (661B, verbatim) | 661B @`551778100` | Exit @`c4595f924` | Δ |
|---|---|---|---|---|
| Pages under `org/[slug]` | `find "$B" -name page.tsx \| wc -l` | 58 | **58** | = (no page added or removed) |
| `PageHeader` adoption | `find "$B" -name page.tsx -exec grep -l "PageHeader" {} + \| wc -l` | 7 | **44** | +37. One hit is a comment: `settings/documents/templates/[id]/edit/page.tsx` names PageHeader only in a comment (raw row 2d), so **43 of 58** `page.tsx` render a `<PageHeader` tag (row 2c). The other 15 are the documented exceptions below |
| Files with `<h1` | `grep -rl '<h1' "$B" \| wc -l` | 71 | **5** | −66. The 5: `not-found.tsx`, `compliance/requests/[id]/page.tsx` (DA-1), `legal/disbursements/[id]/detail-client.tsx` (DA-1), `matters/new/new-matter-page-client.tsx` (669 Non-scope), `reports/[reportSlug]/page.tsx` (B-4) |
| Display h1 (`font-display text-3xl`) | `git grep -c "font-display text-3xl" -- frontend/app frontend/components \| awk -F: '{s+=$NF} END {print s}'` | 92 | **3** | −89. Left: `reports/[reportSlug]/page.tsx:52` (B-4, out of scope), `components/auth-page.tsx:24` (auth screen, not `/org/`), `components/marketing/cta-section.tsx:15` (an **h2**, a pattern false positive) |
| Direct tables by area | `grep -rlE "<Table\b\|<table\b" "$B"` grouped by first path segment | 22 files: settings 10, trust-accounting 6, invoices 4, legal 1, documents 1 | **4 files: settings 2, invoices 1, documents 1** | −18. See "Direct tables left" below: of the 4, one is a code comment and two are the §18.0 row 5 files |
| EmptyState calls without `variant` | `perl qa_cycle/evidence/phase91-ui/baseline/scripts/emptystate-scan.pl` | 53 shared (55 all); 64 tags / 54 files | **0 shared (0 all)**; 117 tags / 91 files, every one with `variant` | −53 / −55. **Target met** |
| EmptyState guard allowlist | `grep -n "const ALLOWLIST" frontend/__tests__/emptystate-variant-guard.test.ts` | (guard added by 663A) | **`const ALLOWLIST: Record<string, string> = {};`** (line 18) | **empty** (669B removed the last entries) |
| `variant` required in the type | `grep -n "variant" frontend/components/empty-state.tsx` | optional | **`variant: EmptyStateVariant;`** (line 23, no `?`) | required (669B, P-9) |
| Raw hex (PCRE, `*.tsx`) | `git grep -nP "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" \| wc -l` | 33 | **33** | = (same files, same per-file counts; 3 lines reworded — see "Raw hex") |
| `dark:` lines, whole frontend | `git grep -c "dark:" -- "frontend/**/*.ts" "frontend/**/*.tsx" \| awk -F: '{s+=$NF} END {print s}'` | 3842 | **2521** | **−1321** (−34.4 %); reconciliation below |

Companion rows (inventory §8.1, same commands as 661B's "Extra" line):

| Metric | 661B | Exit | Δ |
|---|---|---|---|
| `dark:` occurrences (`git grep -o "dark:" -- "frontend/**/*.ts" "frontend/**/*.tsx" \| wc -l`) | 4666 | **3154** | −1512 |
| `page.tsx` containing `<h1` | 37 | **2** | −35 (`compliance/requests/[id]`, `reports/[reportSlug]`) |
| `frontend/components` non-test files with `<h1` | 6 | **5** | −1. Left: `auth-page.tsx`, `information-requests/request-detail-client.tsx`, `marketing/hero-section.tsx`, `shared/detail-hero.tsx` (the DetailHero h1), `templates/TemplateEditorClient.tsx` |
| Raw hex ERE (no `\b` on macOS git) | 2 | **2** | = |

## `PageHeader` exceptions (the 15 `page.tsx` without a `<PageHeader` tag, each verified by grep)

| Page | Where its h1 lives | Why (ruling) |
|---|---|---|
| `settings/page.tsx` | none | `redirect(\`/org/${slug}/settings/firm\`)` |
| `settings/{firm,matters,documents,fees,connections}/page.tsx` (5) | each tab's own `PageHeader` in `_tabs/*.tsx` (firm 8, matters 9, documents 4, fees 6, connections 7 tab files import it) | ADR-394: one h1 per tab, no family h1 |
| `dashboard/page.tsx` | `dashboard/dashboard-header.tsx:8` `<PageHeader title="Home" …/>` | client header component (Phase 90 640A) |
| `my-work/page.tsx` | `my-work/my-work-header.tsx:31` `<PageHeader …>` | client header component |
| `matters/[id]/page.tsx`, `clients/[id]/page.tsx` | `DetailHero` via `components/projects/matter-header-card.tsx` and `components/customers/client-header-card.tsx` (h1 at `components/shared/detail-hero.tsx:93`) | ADR-373 |
| `invoices/[id]/page.tsx` | `DetailHero` via `components/invoices/invoice-detail-client.tsx` → `invoice-header-actions.tsx:50` | ADR-396 |
| `legal/disbursements/[id]/page.tsx` | `detail-client.tsx:184`, hand-rolled on the PageHeader recipe | D-92 DA-1 (badges beside the title; PageHeader has no beside-title slot, F-669A-D1) |
| `matters/new/page.tsx` | `new-matter-page-client.tsx:120` | 669 Non-scope |
| `reports/[reportSlug]/page.tsx` | `page.tsx:52` (display h1) | B-4, out of Phase 91 scope |
| `settings/documents/templates/[id]/edit/page.tsx` (counted by the 661B grep through a comment) | `components/templates/TemplateEditorClient.tsx:207` — **read-only branch only**; the editable branch has no page h1 (F-665E-1) | 665E documented exception (comment at `edit/page.tsx:40`) |

Also documented although the page renders a `<PageHeader` tag: `compliance/requests/[id]/page.tsx` and `information-requests/[id]/page.tsx` use `PageHeader` only on the member-denied branch; the admin branch's h1 is hand-rolled at `compliance/requests/[id]/page.tsx:76` (DA-1) and in `components/information-requests/request-detail-client.tsx:172`.

## Direct tables left (§18.0 row 5)

| File | Hit | Disposition |
|---|---|---|
| `settings/firm/_tabs/capability-reference.tsx:30` | `<Table>` (kit) | static capability reference, no paging/sorting; listed (no slice owned it) |
| `settings/documents/_tabs/templates-table.tsx:24` | a **code comment** containing "`<table>`" | false positive of the pattern; the file renders `DataTable` |
| `invoices/[id]/edit/(components)/add-disbursements-picker.tsx:196` | `<table>` | **the orphan picker** (§18.0 row 5), listed separately |
| `documents/loading.tsx:17` | `<table>` | **the loading skeleton** (§18.0 row 5), listed separately |

So the only real migratable direct table left under `org/[slug]` is the capability reference.

## `dark:` reconciliation (whole frontend 3842 → 2521)

The whole-frontend number telescopes exactly over the Phase 91 squashes: raw row D1 counts `dark:` lines on each squash's parent and on the squash itself, and the per-PR deltas sum to **−1321**. No other commit in `551778100..c4595f924` touches `frontend/` (row D2 is empty). The intermediate 3836 after 663A (#1865) holds through 663C (#1867), matching 663C's "3836 → 3836".

| PR | Slice | DARK-AUDIT in the PR body (slice files, before → after) | git, whole frontend | Gap and its cause |
|---|---|---|---|---|
| #1863 | 661B | 3842 → 3842 | 0 | — |
| #1865 | 663A | 96 → 90 (−6) | −6 | — |
| #1866, #1867 | 663B, 663C | 219 → 219, 8 → 8 | 0, 0 | — |
| #1868 | 664A | 5 → 0 (−5) | −2 | +3 in new files: `components/settings/settings-section.tsx` 1, `__tests__/components/settings/settings-family-notice.test.tsx` 2 (row D3) |
| #1869 | 664B | 22 → 20 (−2) | −2 | — |
| #1870 | 665A | 152 → 8 (−144) | −144 | — |
| #1871 | 665B | 87 → 22 (−65) | −65 | — |
| #1872 | 665C | 162 → 6 (−156) | −154 | the PR body's per-file sum is 2 high; git per file (row D4) sums to 154 removed (no file gained) |
| #1873 | 665D | 119 → 1 (−118) | −117 | +1 in `__tests__/app/settings/tax-rates.test.tsx` (outside the slice's table) |
| #1874 | 665E | 74 → 25 (−49) | −49 | — |
| #1875 | 665F | 269 → 137 (−132) | −132 | — |
| #1876 | 666A | 284 → 97 (−187) | −182 | +5 in new files: `fee-note-status-filter.tsx` 2, its test 2, `fee-note-columns.tsx` 1 (the PR's "100 incl. 7 new files" counts 3 of them) |
| #1877 | 666B | 103 → 39 (−64) | −64 | — |
| #1878 | 667A | 104 → 9 (−95) | −94 | +1 in new `trust/client-ledgers-data-table.tsx` |
| #1879 | 667B | 125 → 30 (−95) | −94 | +1 in new `__tests__/trust/investments-data-table.test.tsx` (the 3 in `investments-data-table.tsx` are already inside the PR's 30) |
| #1880 | 668A | 94 → 31 (−63) | −63 | — |
| #1881 | 669A | 231 → 99 (−132) | −132 | — |
| #1882 | 669B | 30 → 9 (−21) | −21 | — |
| | **Total** | **−1334** | **−1321** | **13 = 3 + 2 + 1 + 5 + 1 + 1**, every unit attributed above |

The docs-only PRs #1862 and #1864 change 0. The `dark:` lines still in the frontend are concentrated where no Phase 91 slice edited (Phase 91 replaces pairs only on the lines it touches, §18.6.2).

## Raw hex (33 → 33)

Same files, same count per file (raw row B7b prints no difference). Raw row B7a lists the 3 reworded lines:
- `settings/matters/_tabs/tags-content.tsx`: the luminance helper `return luminance > 0.5 ? "#000000" : "#FFFFFF";` was replaced by a comment naming `--slate-950` ≈ `#07080B` (665C).
- `integrations/XeroConnectionCard.tsx`: the `bg-[#13B5EA]/10` badge gained `shrink-0` (same hex).
- `shell/app-sidebar.tsx`: the "(#1B3A4B)" comment reworded (661B).

Remaining sites and dispositions (full listing in raw row 7a): 17 are comment or help-text false positives (`PR #nnnn` ×13 incl. `dashboard/page.tsx:71`, the `#1B3A4B` sidebar comment, the `#07080B` tags comment, two "e.g. #1a2b3c" help strings); 5 are brand-colour defaults/placeholders (`#000000` in `branding-section.tsx` ×2, `general.tsx`, `general-settings-form.tsx` ×2; F-665A-1 tracks the blank-colour-saves-`#000000` bug); 3 `TagDialog` "#FF5733" examples; 1 `TagInput` contrast helper; 4 Xero brand `#13B5EA` uses (`XeroContactBadge.tsx`, `XeroConnectionCard.tsx` ×3; third-party brand colour, Phase 90 D-5 fix); 1 shadcn `ui/chart.tsx` registry selector; 2 in `__tests__/app-sidebar.test.tsx` (17 + 5 + 3 + 1 + 4 + 1 + 2 = 33). None is a themable surface colour a token should replace; no row is added to the backlog.

## Migrations and dependencies

- **Migrations: none.** `git diff --name-only 551778100..c4595f924 -- backend gateway` is empty (raw row 11), and so is the Flyway path (row 13). No Java landed, so no BE-VERIFY was owed (Epic 670 "Verifies: 0").
- **Dependencies: none added.** `git diff --stat 551778100..c4595f924 -- frontend/package.json portal/package.json packages/*/package.json pnpm-lock.yaml` is empty (row 12).
