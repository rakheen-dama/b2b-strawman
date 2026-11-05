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

## After (634D fix, branch `phase89/05D-disbursement-container`)

Observed 2026-11-05 13:34–13:36 UTC by the 634D builder using Playwright MCP (Chromium) on the :3001 E2E stack, after `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend` from the worktree (`e2e-frontend` created `2026-11-05T13:34:34Z`, healthy). The measured source is this PR's commit: `git hash-object` of `frontend/components/legal/disbursement-list-view.tsx` = `b590e4e81db90b655b77634caa61ad4d53f28505`, of the test = `663b8bc8ae9c6dc48949842bdc84c75baa11bd80`. Every row below was measured on that build. Each check ran the same snippet as "Before", plus a visible-element check: the visible Billing badge and the row's Actions trigger have `getBoundingClientRect()` inside the wrapper's rect. Same data and actor as Before (Alice, 4 firm rows, 1 matter-tab row). No SQL or REST was used.

### Design (orchestrator ruling 2026-11-05)

- The wrapper is `@container relative overflow-x-auto`. `overflow-x-auto` stays at all widths as the safety net. `relative` and `data-testid="disbursement-list"` are unchanged.
- Column priority uses measured container breakpoints. Supplier hides first (`@min-[84rem]`), then Category (`@min-[75rem]`), then Matter (`@min-[66rem]`). Each hidden value goes to a muted second line under the Description (`text-xs text-slate-500`, truncated, with `title`). Each fragment hides again at the breakpoint where its column comes back (`@min-[…]:hidden`). The Matter fragment renders only when the mount passes a matter name, so the matter tab shows no "—" fragment.
- Below `@min-[58rem]` (compact): the Billing badge stacks under the Approval badge and only the separate Billing column hides, so Billing stays visible. Cells go from `px-4` to `px-2`. The Description cap goes from 28ch to 20ch. The Amount header may wrap. Date, Description, Amount, Approval and Actions are never hidden.

Threshold probe (firm list at 1440, wrapper forced to width X with `style.width`, table `min-content` read back, reverted afterwards):

| Layout (columns shown) | Table min-content | Breakpoint | Margin |
|---|---:|---|---:|
| Compact: Date, Description (20ch, + 2nd line), Amount, Approval (+ stacked Billing), Actions | 586 | below 58rem (928) | 70 px at the 656 px matter tab @1024 |
| + Billing column, `px-4`, 28ch | 907 | 58rem = 928 | 21 |
| + Matter | 1033 | 66rem = 1056 | 23 |
| + Category (widest label "Deeds Office Fees" is in the data) | 1193 | 75rem = 1200 | 7 |
| + Supplier | 1328 | 84rem = 1344 | 16 |

Forced widths 600, 620, 640, 656, 704, 912, 927, 928, 960, 1024, 1055, 1056, 1072, 1120, 1199, 1200, 1343 and 1344 all gave `scrollWidth − clientWidth = 0`. (An earlier iteration with Matter at 64rem gave 9 px of overflow at a 1024 px container, so Matter moved to 66rem. Billing moved from 57rem to 58rem for margin. The Amount header wrap brought the compact layout from 656 down to 586.)

### Numbers (after)

| Mount | Viewport | Wrapper clientWidth | Wrapper scrollWidth | Table scrollWidth | Overflow | Doc scrollWidth | Columns visible (px) | Billing badge inside | Actions trigger inside | Result |
|---|---|---:|---:|---:|---:|---:|---|---|---|---|
| Firm list | 1440×900 | 1120 | 1120 | 1120 | **0** | 1440 | Date 116, Matter 213, Description 247, Amount 200, Approval 140, Billing 116, Actions 88 | 4/4 rows | 3/3 rows with a menu | **PASS** |
| Firm list | 1024×768 | 704 | 704 | 704 | **0** | 1024 | Date 107, Description 189, Amount 197, Approval 133 (Billing stacked), Actions 77 | 4/4 (stacked) | 3/3 | **PASS** |
| Matter tab | 1440×900 | 1072 | 1072 | 1072 | **0** | 1440 | Date 128, Matter 91, Description 272, Amount 220, Approval 154, Billing 112, Actions 97 | 1/1 | 1/1 | **PASS** |
| Matter tab | 1024×768 | 656 | 656 | 656 | **0** | 1024 | Date 100, Description 176, Amount 184, Approval 124 (Billing stacked), Actions 72 | 1/1 (stacked) | 1/1 | **PASS** |
| Firm list | 1280×720 | 960 | 960 | 960 | **0** | 1280 | Date 123, Description 261, Amount 212, Approval 148, Billing 123, Actions 93 | 4/4 | 3/3 | **PASS** |
| Matter tab | 1280×720 | 912 | 912 | 912 | **0** | 1280 | Date 139, Description 245, Amount 255, Approval 173 (Billing stacked), Actions 100 | 1/1 (stacked) | 1/1 | **PASS** |

Second lines observed: at 1440, firm list "Deeds Office Fees · Deeds Office Pretoria", "Sheriff Fees · Sheriff Johannesburg North" (×2) and "Counsel Fees · Adv. N. Mokoena SC". At 1024 and 1280 (Matter hidden), the matter-named row adds "Ndlovu — transition repro mw3kbytr4f62 · …", truncated with a `title`. On the matter tab: "Sheriff Fees · Sheriff Johannesburg North", with no Matter fragment.

Other checks: Description `title` = full text (for example "Deeds office search fee" and "Sheriff fee — re-service mw3kbytr4f62"). At 1024 on the firm list, the Actions "…" menu opens: "Write off" on an UNBILLED row and "Undo write-off" on the WRITTEN_OFF row (`after-1024-firm-list-actions-menu.png`). In every case `documentElement.scrollWidth === innerWidth`.

### Screenshots

- `after-1440-firm-list.png`, `after-1024-firm-list.png`, `after-1440-matter-tab.png`, `after-1024-matter-tab.png`
- `after-1280-firm-list.png` (the Playwright default viewport)
- `after-1024-firm-list-actions-menu.png`

**Verdict (after): G88-M4 closed on the observed build.** In both mounts at 1024, 1280 and 1440, `scrollWidth === clientWidth` on `[data-testid="disbursement-list"]`, and the Billing badge and Actions trigger are inside the container.
