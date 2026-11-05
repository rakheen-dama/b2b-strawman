# 634D — G88-M4 disbursement tables: observation

## Before (origin/main 6eb56bbc4)

Observed 2026-11-05 13:18–13:22 UTC by the 634D scout agent using Playwright MCP (Chromium). No source was modified. The scout did not rebuild, reseed or tear down the stack. No data was created, and no SQL or REST was used. The disbursement rows were already on the stack. Their names carry the `mw3kbytr4f62` run suffix of the legal-depth-ii Playwright suite, which created them through the UI.

### Stack / build

| Item | Value |
|------|-------|
| Stack | E2E mock-auth (frontend :3001, backend :8081) |
| `docker inspect e2e-frontend --format '{{.Created}}'` | `2026-11-05T13:09:18.722313841Z` (running, healthy) |
| Component at HEAD | `frontend/components/legal/disbursement-list-view.tsx` last changed in 8a0b96265 (628B). The wrapper is already `className="relative overflow-x-auto" data-testid="disbursement-list"`, so the testid already exists. |
| Actor | Alice (owner) via `/mock-login`, org `e2e-test-org` |

### Mounts and URLs

| Mount | URL | Data |
|------|-----|------|
| Firm-wide list (`disbursements-list-client.tsx:208`) | `http://localhost:3001/org/e2e-test-org/legal/disbursements` | 4 rows: Deeds Office Fees "Deeds office search fee" (Deeds Office Pretoria); Sheriff Fees "Sheriff fee — re-service mw3kbytr4f62" (Sheriff Johannesburg North); Sheriff Fees "Sheriff fee — service of process"; Counsel Fees "Counsel's opinion on shareholders' agreement mw3kbytr4f62" (Adv. N. Mokoena SC) |
| Matter tab (`project-disbursements-tab.tsx:82`) | `http://localhost:3001/org/e2e-test-org/matters/61c4a7ab-4f5a-444f-b140-4d8e8538f1a4?tab=disbursements` | Matter "Ndlovu — transition repro mw3kbytr4f62" (`61c4a7ab-4f5a-444f-b140-4d8e8538f1a4`). 1 row: Sheriff Fees "Sheriff fee — re-service mw3kbytr4f62", Sheriff Johannesburg North, R 150,00, Approved, Unbilled |

### Numbers (measured on `[data-testid="disbursement-list"]`, the current `relative overflow-x-auto` wrapper)

| Mount | Viewport | Wrapper clientWidth | Wrapper scrollWidth | Table scrollWidth | Overflow (sw − cw) | Document scrollWidth | Result |
|------|---------|--------------------:|--------------------:|------------------:|-------------------:|--------------------:|--------|
| Firm list | 1440×900 | 1120 | 1455 | 1455 | **335** | 1440 | **RED**: Approval, Billing and Actions sit behind the inner scroll |
| Firm list | 1024×768 | 704 | 1455 | 1455 | **751** | 1024 | **RED** |
| Matter tab | 1440×900 | 1072 | 1259 | 1259 | **187** | 1440 | **RED**: Billing and Actions sit behind the inner scroll |
| Matter tab | 1024×768 | 656 | 1259 | 1259 | **603** | 1024 | **RED** |

The document itself never overflows (`documentElement.scrollWidth === innerWidth`), which matches 628B's page-overflow fix. Only the inner scroll remains, and that is G88-M4 as filed.

Container widths are set by the ancestors. Firm list: `main` (784 at 1024) → `mx-auto max-w-7xl px-6 py-6 lg:px-10` → 704. Matter tab: same shell → `space-y-6 p-4 lg:p-6` (704) → 656. The task file's "firm list 1249 px wide" figure is the Phase 88 KC **table** width, not the container width.

Column widths (`thead th` getBoundingClientRect().width, px):

| Mount @ viewport | Date | Matter | Category | Description | Supplier | Amount (incl VAT) | Approval | Billing | Actions |
|---|---|---|---|---|---|---|---|---|---|
| Firm list (both widths, fixed) | 106 | 127 | 160 | 384 (= `max-w-sm`, capped) | 134 | 200 | 140 | 116 | 88 |
| Matter tab (both widths) | 106 | 82 ("—") | 124 | 283 | 134 | 200 | 140 | 102 | 88 |

### What-if simulation (inline styles via `browser_evaluate`, reverted afterwards — not source changes)

| Mount | Container | Spec as written (`@xl` = 36rem = 576 px → Category and Supplier **shown** at every measured width) + Description `max-width: 28ch` | Category and Supplier hidden + Description 28ch |
|---|---:|---:|---:|
| Firm list 1440 | 1120 | sw **1317** → still overflows by 197 | sw **1120** → fits (natural ≈ 1023) |
| Matter tab 1440 | 1072 | sw **1222** → overflows by 150 | sw **1072** → fits (natural ≈ 964) |
| Firm list 1024 | 704 | sw 1317 → overflows by 613 | sw **1023** → overflows by 319 |
| Matter tab 1024 | 656 | sw 1222 → overflows by 566 | sw **964** → overflows by 308 |

Further 1024 experiments:

| Variant | Firm list (704) | Matter tab (656) |
|---|---|---|
| Category and Supplier hidden, desc 28ch, sort-header buttons `white-space: normal` (Amount 200 → 130) | sw 953 | — |
| Same + Matter hidden | sw 826 | sw 812 |
| Same as the row above + cell padding `px-2` | — | sw 732 |
| Same as the row above + desc 20ch | — | sw 661 (still 5 px over) |
| Category, Supplier and **Description** hidden, header wrap (Matter kept) | sw 706 (2 px over) | — |

### JS snippet used (base measurement)

```js
() => {
  const w = document.querySelector('[data-testid="disbursement-list"]'); // matter tab: '[data-testid="project-disbursements-tab"] [data-testid="disbursement-list"]'
  const t = w.querySelector('table');
  return {
    viewport: window.innerWidth,
    docScrollWidth: document.documentElement.scrollWidth,
    wrapperClass: w.className,
    wrapperScrollWidth: w.scrollWidth,
    wrapperClientWidth: w.clientWidth,
    tableScrollWidth: t.scrollWidth,
    overflowPx: w.scrollWidth - w.clientWidth,
    columns: [...t.querySelectorAll('thead th')].map(th => th.innerText.trim() + ':' + Math.round(th.getBoundingClientRect().width)),
  };
}
```

What-if snippets: the same function, plus setting `style.maxWidth='28ch'` on every `tr.children[3]` and `style.display='none'` on `tr.children[2]` and `tr.children[4]` (Category and Supplier), then re-reading `w.scrollWidth`. All styles were reset afterwards, and re-measurement showed the original 1455 / 1259.

### Screenshots

- `before-1440-firm-list.png`: Approval, Billing and Actions are cut off at the right edge. The date wraps "2026-11-" / "05".
- `before-1024-firm-list.png`
- `before-1440-matter-tab.png`
- `before-1024-matter-tab.png`

**Verdict (before): G88-M4 reproduced.** `scrollWidth > clientWidth` in both mounts at both 1440 and 1024.

## After (634D fix + review-findings fix, branch `phase89/05D-disbursement-container`)

**This section supersedes the measurements taken on f52f26aeb.** The review-findings commit ("fix(634D): keep Supplier sort reachable, co-locate breakpoint pairs") adds a Supplier sort control to the Description header and restructures the breakpoint constants. Every row below was re-measured on that build.

Observed 2026-11-05 13:52–13:55 UTC by the 634D fixer using Playwright MCP (Chromium) on the :3001 E2E stack, after `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend` from the worktree (`e2e-frontend` created `2026-11-05T13:52:33Z`, healthy). The measured source: `git hash-object` of `frontend/components/legal/disbursement-list-view.tsx` = `3c8fbd7b089551c427cd9934c119c9a372e12c58`, of the test = `f023ee5d1ffa6eb076f3e3ddbb196510d1bb431f`. Each check ran the same snippet as "Before", plus three visible-element checks:

- the visible Billing badge and the row's Actions trigger have `getBoundingClientRect()` inside the wrapper's rect;
- exactly one VISIBLE `thead` button whose name matches /supplier/ exists, and it is inside the wrapper;
- a Playwright click on that control reorders the rows by supplier.

Same data and actor as Before (Alice, 4 firm rows, 1 matter-tab row). No SQL or REST was used.

### Design (orchestrator ruling 2026-11-05; ruling item 6 withdrawn after review)

- The wrapper is `@container relative overflow-x-auto`. `overflow-x-auto` stays at all widths as the safety net. `relative` and `data-testid="disbursement-list"` are unchanged.
- Column priority uses measured container breakpoints. Supplier hides first (`@min-[84rem]`), then Category (`@min-[75rem]`), then Matter (`@min-[66rem]`). Each hidden value goes to a muted second line under the Description (`text-xs text-slate-500`, truncated, with `title`). Each fragment hides again at the breakpoint where its column comes back (`@min-[…]:hidden`). The Matter fragment renders only when the mount passes a matter name, so the matter tab shows no "—" fragment. In the source, each column's pair is co-located in one `PRIORITY` entry (`{ col, sub }`).
- **Supplier sort stays reachable.** While the Supplier column is hidden, the Description header shows "Description · Supplier ↕". This is a ghost sort button with the same style as the other sort headers, `aria-label="Sort by supplier"`, and the `PRIORITY.supplier.sub` class (`@min-[84rem]:hidden`). Exactly one Supplier sort control is visible at any width: the header control below 84rem, the column's own button from 84rem. The header may wrap; on the 656 px matter tab it reads "DESCRIPTION" / "· SUPPLIER ↕".
- Below `@min-[58rem]` (compact; all variants grouped in one `COMPACT` const): the Billing badge stacks under the Approval badge and only the separate Billing column hides, so Billing stays visible. Cells go from `px-4` to `px-2`. The Description cap goes from 28ch to 20ch. The Amount header may wrap. Date, Description, Amount, Approval and Actions are never hidden.

Threshold re-probe on the new build (firm list at 1440, wrapper forced to width X with `style.width`, table `min-content` read back, all styles reverted afterwards, re-read 1120/1120):

| Layout (columns shown) | Table min-content | Breakpoint | Margin |
|---|---:|---|---:|
| Compact: Date, Description (20ch, + 2nd line, + Supplier sort in header), Amount, Approval (+ stacked Billing), Actions | 586 | below 58rem (928) | 70 px at the 656 px matter tab @1024 |
| + Billing column, `px-4`, 28ch | 907 | 58rem = 928 | 21 |
| + Matter | 1033 | 66rem = 1056 | 23 |
| + Category (widest label "Deeds Office Fees" is in the data) | 1193 | 75rem = 1200 | 7 |
| + Supplier (header control hidden, column button shown) | 1328 | 84rem = 1344 | 16 |

The min-content widths are identical to f52f26aeb's. The header control is narrower than the Description cap, so it does not widen the column, and no breakpoint needed re-tuning. Forced widths 586, 600, 620, 640, 656, 704, 912, 927, 928, 960, 1024, 1055, 1056, 1072, 1120, 1199, 1200, 1343 and 1344 all gave `scrollWidth − clientWidth = 0`. At every forced width exactly one Supplier sort control was visible: the Description-header control at 586–1343, and the Supplier column's button at 1344. (560 px, below any real container, overflows by 26 px. That is expected, because the compact min-content is 586.)

### Numbers (after)

| Mount | Viewport | Wrapper clientWidth | Wrapper scrollWidth | Table scrollWidth | Overflow | Doc scrollWidth | Columns visible (px) | Billing badge inside | Actions trigger inside | Visible Supplier sort | Result |
|---|---|---:|---:|---:|---:|---:|---|---|---|---|---|
| Firm list | 1440×900 | 1120 | 1120 | 1120 | **0** | 1440 | Date 116, Matter 213, Description · Supplier 247, Amount 200, Approval 140, Billing 116, Actions 88 | 4/4 rows | 3/3 rows with a menu | 1 (Description header), inside | **PASS** |
| Firm list | 1280×720 | 960 | 960 | 960 | **0** | 1280 | Date 123, Description · Supplier 261, Amount 212, Approval 148, Billing 123, Actions 93 | 4/4 | 3/3 | 1 (Description header), inside | **PASS** |
| Firm list | 1024×768 | 704 | 704 | 704 | **0** | 1024 | Date 101, Description · Supplier 221, Amount 185, Approval 125 (Billing stacked), Actions 72 | 4/4 (stacked) | 3/3 | 1 (Description header), inside | **PASS** |
| Matter tab | 1440×900 | 1072 | 1072 | 1072 | **0** | 1440 | Date 128, Matter 91, Description · Supplier 272, Amount 220, Approval 154, Billing 112, Actions 97 | 1/1 | 1/1 | 1 (Description header), inside | **PASS** |
| Matter tab | 1280×720 | 912 | 912 | 912 | **0** | 1280 | Date 131, Description · Supplier 286, Amount 240, Approval 162 (Billing stacked), Actions 94 | 1/1 (stacked) | 1/1 | 1 (Description header), inside | **PASS** |
| Matter tab | 1024×768 | 656 | 656 | 656 | **0** | 1024 | Date 100, Description · Supplier 203, Amount 157, Approval 124 (Billing stacked), Actions 72 | 1/1 (stacked) | 1/1 | 1 (Description header), inside | **PASS** |

**Supplier-sort check.** On the firm list (4 rows, default order date-desc), a Playwright `click()` on the visible Description-header control produced the following row suppliers:

- At 1440 and 1024: "Adv. N. Mokoena SC", "Deeds Office Pretoria", "Sheriff Johannesburg North", "Sheriff Johannesburg North" (ascending).
- At 1280: the same ascending order. A second click gave "Sheriff Johannesburg North" ×2, "Deeds Office Pretoria", "Adv. N. Mokoena SC" (descending).

On the matter tab (1 row) the control's click succeeded at 1024 and 1440. Playwright clicks only visible, actionable elements. Reordering cannot be shown with one row.

Second lines are unchanged from f52f26aeb. At 1440 on the firm list: "Deeds Office Fees · Deeds Office Pretoria", "Sheriff Fees · Sheriff Johannesburg North" (×2) and "Counsel Fees · Adv. N. Mokoena SC". At 1024 and 1280 (Matter hidden), the matter-named row adds "Ndlovu — transition repro mw3kbytr4f62 · …", truncated with a `title`. On the matter tab: "Sheriff Fees · Sheriff Johannesburg North", with no Matter fragment.

Other checks: at 1024 on the firm list, the Actions "…" menu opens (`after-1024-firm-list-actions-menu.png`). In every case `documentElement.scrollWidth === innerWidth`.

### Screenshots (replaced on the review-findings build)

- `after-1440-firm-list.png`, `after-1024-firm-list.png`, `after-1440-matter-tab.png`, `after-1024-matter-tab.png`
- `after-1280-firm-list.png` (the Playwright default viewport)
- `after-1024-firm-list-actions-menu.png`

**Verdict (after): G88-M4 closed on the observed build.** In both mounts at 1024, 1280 and 1440, `scrollWidth === clientWidth` on `[data-testid="disbursement-list"]`, and the Billing badge and Actions trigger are inside the container. Exactly one Supplier sort control is visible, and it sorts by supplier.
