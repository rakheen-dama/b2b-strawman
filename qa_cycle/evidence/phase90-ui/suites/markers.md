# 644A — verify-marker audit over the Phase 90 merged range (task 644.2)

Command (run from the 644A worktree at `main` @ `2773ec9b6`, 2026-11-13), committed-only mode so nothing in an index can pass:

```bash
for pr in $(seq 1758 1777); do bash scripts/check-verify-markers.sh --committed-only $pr; done
```

Range: P1 (#1758) and every Phase 90 code slice 637A–643C (#1759–#1777). #1778/#1779 (trust reconciliation wizard fixes) merged after 643C but are not Phase 90 slices; they are covered by the BE/FE/PORTAL gates on `main` in `README.md`, not by this table.

**Result: 20 / 20 PASS (exit 0).** Expectations from 644.2 all hold: backend markers for P1 (#1758), 643A (#1775), 643B (#1776); a frontend marker for every UI slice; portal markers for 637A (#1759) and 639A (#1763) — plus 640A (#1767) and 640C (#1769), which also touched `packages/`/`portal/`.

Columns: marker = the verified PR-branch commit recorded in the marker's `commit` field; "in" = the `main` commit that committed the marker (the slice's bookkeeping commit).

| PR | Merge | Title | Areas | Markers (verified commit, committed in) | Exit |
|---|---|---|---|---|---|
| #1758 | `f7b7dbdf1` | fix(dashboard): redact previous-period financials in KPI response for non-admins (Phase 90 P1) | backend | backend `1b414711a` (in `43f405922`) | 0 |
| #1759 | `cfe19b8d1` | Phase 90 637A: tokens + Kazi chart palette (E1A) | frontend portal | frontend `874ba8df5` (in `43f405922`); portal `874ba8df5` (in `43f405922`) | 0 |
| #1760 | `acc742d2a` | Phase 90 637B: ThemeProvider + Light/Dark/System toggle + dead chart code (E1B) | frontend | frontend `4816cf440` (in `68cf12103`) | 0 |
| #1761 | `9e3f73462` | Phase 90 638A: shadcn sidebar shell — cookie collapse, ⌘/Ctrl+B, mobile Sheet (E2A) | frontend | frontend `57fa7a98f` (in `9d7d2b62e`) | 0 |
| #1762 | `dd2d40284` | Phase 90 638B: floating AppHeader + command palette restyle (E2B) | frontend | frontend `67a073f7d` (in `248c13a27`) | 0 |
| #1763 | `22e3989e1` | Phase 90 639A: PageHeader, StatCard, EmptyState, DetailHero primitives (E3A) | frontend portal | frontend `f265fc609` (in `3530298d9`); portal `bc7233bf8` (in `3530298d9`) | 0 |
| #1764 | `9c2d736bc` | Phase 90 639B: chart kit core — shadcn chart + recharts presets (E3B) | frontend | frontend `008e77020` (in `83fb71964`) | 0 |
| #1765 | `36839eae1` | Phase 90 639C: DataTable — TanStack client-mode table (E3C) | frontend | frontend `0267c977b` (in `d04244960`) | 0 |
| #1766 | `1207267b1` | Phase 90 639D: micro SVG presets — SparklineTrend, ProgressRadial, StackedMeter (E3D) | frontend | frontend `faa96b0a8` (in `e0e399b24`) | 0 |
| #1767 | `ae54e3c30` | Phase 90 640A: Home KPI row + audience regrid (E4A) | frontend portal | frontend `d78ce3ce5` (in `d2a360527`); portal `d78ce3ce5` (in `d2a360527`) | 0 |
| #1768 | `1f0380b7a` | Phase 90 640B: dashboard widgets on the kit (E4B) | frontend | frontend `4941cd086` (in `2e7c2b424`) | 0 |
| #1769 | `3b528feaa` | Phase 90 640C: My Work weekly summary + Home loading skeleton (E4C) | frontend portal | frontend `2b52bc934` (in `01e6782d1`); portal `2b52bc934` (in `01e6782d1`) | 0 |
| #1770 | `d5547230f` | Phase 90 641A: matters list on DataTable (E5A) | frontend | frontend `69da024c9` (in `99de45261`) | 0 |
| #1771 | `efb5382ca` | Phase 90 641B: matter hero on DetailHero + page split (E5B) | frontend | frontend `4754eda99` (in `38c34ce92`) | 0 |
| #1772 | `67c21f435` | Phase 90 642A: clients list on DataTable (E6A) | frontend | frontend `706919c0a` (in `9b3ec3555`) | 0 |
| #1773 | `df3042512` | Phase 90 642B: client hero on DetailHero + page split (E6B) | frontend | frontend `912249f4a` (in `e2e73ca8f`) | 0 |
| #1774 | `4ef92ee49` | Phase 90 642C: GroupedTabBar restyle — underline, sticky, horizontal scroll (E6C) | frontend | frontend `ad4f84ba4` (in `43142e947`) | 0 |
| #1775 | `7a8e4f012` | Phase 90 643A: trust dashboard + balance trend (E7A) — declared side effect: reconciliation card re-sourced from trust-position | backend frontend | backend `596a20d57` (in `f140c2ffa`); frontend `859d257d2` (in `f140c2ffa`) | 0 |
| #1776 | `5864da54d` | Phase 90 643B: fees billed vs collected + profitability charts (E7B) | backend frontend | backend `20955954f` (in `227022d21`); frontend `606d8052d` (in `227022d21`) | 0 |
| #1777 | `54096a4fa` | Phase 90 643C: profitability tables on DataTable + reports index (E7C) | frontend | frontend `8ca3aab84` (in `3a40db5ed`) | 0 |

Note: #1758 and #1759 both resolve to markers committed in `43f405922`. P1's backend marker (`1b414711a`) was written at P1's merge but never committed in a P1 bookkeeping commit (there is none: `git log f7b7dbdf1^..43f405922` shows only `f7b7dbdf1`, `cfe19b8d1`, `43f405922`). It was back-filled into the 637A bookkeeping commit `43f405922`, whose message says so (G88-F35 class). The script accepts it because the marker's `commit` is P1's PR-branch commit.

<details><summary>Raw output</summary>

```
=== #1758 f7b7dbdf1 fix(dashboard): redact previous-period financials in KPI response for non-admins (Phase 90 P1)
check-verify-markers: PR #1758 merge f7b7dbdf1 head 2539f48f8 — areas: backend — mode: committed-only
  backend: OK — committed 43f405922 (marker commit 1b414711a542fd99362ae6c4b590c8b84ae5f20a)
check-verify-markers: PASS — every touched area of PR #1758 has a verify marker
exit 0
=== #1759 cfe19b8d1 Phase 90 637A: tokens + Kazi chart palette (E1A)
check-verify-markers: PR #1759 merge cfe19b8d1 head 4639bb25d — areas: frontend portal — mode: committed-only
  frontend: OK — committed 43f405922 (marker commit 874ba8df5f5b8f53f619694090b5d01b0a8d6211)
  portal: OK — committed 43f405922 (marker commit 874ba8df5f5b8f53f619694090b5d01b0a8d6211)
check-verify-markers: PASS — every touched area of PR #1759 has a verify marker
exit 0
=== #1760 acc742d2a Phase 90 637B: ThemeProvider + Light/Dark/System toggle + dead chart code (E1B)
check-verify-markers: PR #1760 merge acc742d2a head 569ac441c — areas: frontend — mode: committed-only
  frontend: OK — committed 68cf12103 (marker commit 4816cf4408a883c89ad29db1fb900e2effdaa0d8)
check-verify-markers: PASS — every touched area of PR #1760 has a verify marker
exit 0
=== #1761 9e3f73462 Phase 90 638A: shadcn sidebar shell — cookie collapse, ⌘/Ctrl+B, mobile Sheet (E2A)
check-verify-markers: PR #1761 merge 9e3f73462 head c82647bf4 — areas: frontend — mode: committed-only
  frontend: OK — committed 9d7d2b62e (marker commit 57fa7a98f8c1bfc1a8167cad37f56368d727800f)
check-verify-markers: PASS — every touched area of PR #1761 has a verify marker
exit 0
=== #1762 dd2d40284 Phase 90 638B: floating AppHeader + command palette restyle (E2B)
check-verify-markers: PR #1762 merge dd2d40284 head 7445412e6 — areas: frontend — mode: committed-only
  frontend: OK — committed 248c13a27 (marker commit 67a073f7d452c3fd49e308980635d34acb51455c)
check-verify-markers: PASS — every touched area of PR #1762 has a verify marker
exit 0
=== #1763 22e3989e1 Phase 90 639A: PageHeader, StatCard, EmptyState, DetailHero primitives (E3A)
check-verify-markers: PR #1763 merge 22e3989e1 head 72c687815 — areas: frontend portal — mode: committed-only
  frontend: OK — committed 3530298d9 (marker commit f265fc609c3467a6a6d317664abf920b53a46a59)
  portal: OK — committed 3530298d9 (marker commit bc7233bf824552f6f2bcd5235caad8b32c64e7b9)
check-verify-markers: PASS — every touched area of PR #1763 has a verify marker
exit 0
=== #1764 9c2d736bc Phase 90 639B: chart kit core — shadcn chart + recharts presets (E3B)
check-verify-markers: PR #1764 merge 9c2d736bc head 6a87685b6 — areas: frontend — mode: committed-only
  frontend: OK — committed 83fb71964 (marker commit 008e77020a8cc45298a1ef8b3da786e405b4174f)
check-verify-markers: PASS — every touched area of PR #1764 has a verify marker
exit 0
=== #1765 36839eae1 Phase 90 639C: DataTable — TanStack client-mode table (E3C)
check-verify-markers: PR #1765 merge 36839eae1 head 0267c977b — areas: frontend — mode: committed-only
  frontend: OK — committed d04244960 (marker commit 0267c977bfaf99f07947cd5668dd93fc24ed60cd)
check-verify-markers: PASS — every touched area of PR #1765 has a verify marker
exit 0
=== #1766 1207267b1 Phase 90 639D: micro SVG presets — SparklineTrend, ProgressRadial, StackedMeter (E3D)
check-verify-markers: PR #1766 merge 1207267b1 head 850ab0f15 — areas: frontend — mode: committed-only
  frontend: OK — committed e0e399b24 (marker commit faa96b0a8ddf88c058279388654adfdf2f828d35)
check-verify-markers: PASS — every touched area of PR #1766 has a verify marker
exit 0
=== #1767 ae54e3c30 Phase 90 640A: Home KPI row + audience regrid (E4A)
check-verify-markers: PR #1767 merge ae54e3c30 head 90f8d740f — areas: frontend portal — mode: committed-only
  frontend: OK — committed d2a360527 (marker commit d78ce3ce510b5b516d35d0528747bea384ef56a7)
  portal: OK — committed d2a360527 (marker commit d78ce3ce510b5b516d35d0528747bea384ef56a7)
check-verify-markers: PASS — every touched area of PR #1767 has a verify marker
exit 0
=== #1768 1f0380b7a Phase 90 640B: dashboard widgets on the kit (E4B)
check-verify-markers: PR #1768 merge 1f0380b7a head 4941cd086 — areas: frontend — mode: committed-only
  frontend: OK — committed 2e7c2b424 (marker commit 4941cd086c737a410f9a8ca74f57149ae6392f63)
check-verify-markers: PASS — every touched area of PR #1768 has a verify marker
exit 0
=== #1769 3b528feaa Phase 90 640C: My Work weekly summary + Home loading skeleton (E4C)
check-verify-markers: PR #1769 merge 3b528feaa head 2b52bc934 — areas: frontend portal — mode: committed-only
  frontend: OK — committed 01e6782d1 (marker commit 2b52bc934de8dd4b0cfd367272f5ba1774ce885e)
  portal: OK — committed 01e6782d1 (marker commit 2b52bc934de8dd4b0cfd367272f5ba1774ce885e)
check-verify-markers: PASS — every touched area of PR #1769 has a verify marker
exit 0
=== #1770 d5547230f Phase 90 641A: matters list on DataTable (E5A)
check-verify-markers: PR #1770 merge d5547230f head 69da024c9 — areas: frontend — mode: committed-only
  frontend: OK — committed 99de45261 (marker commit 69da024c9432290ad959f482bb4fd9e4adf9750a)
check-verify-markers: PASS — every touched area of PR #1770 has a verify marker
exit 0
=== #1771 efb5382ca Phase 90 641B: matter hero on DetailHero + page split (E5B)
check-verify-markers: PR #1771 merge efb5382ca head 214672cf1 — areas: frontend — mode: committed-only
  frontend: OK — committed 38c34ce92 (marker commit 4754eda99)
check-verify-markers: PASS — every touched area of PR #1771 has a verify marker
exit 0
=== #1772 67c21f435 Phase 90 642A: clients list on DataTable (E6A)
check-verify-markers: PR #1772 merge 67c21f435 head a9e581223 — areas: frontend — mode: committed-only
  frontend: OK — committed 9b3ec3555 (marker commit 706919c0a)
check-verify-markers: PASS — every touched area of PR #1772 has a verify marker
exit 0
=== #1773 df3042512 Phase 90 642B: client hero on DetailHero + page split (E6B)
check-verify-markers: PR #1773 merge df3042512 head 912249f4a — areas: frontend — mode: committed-only
  frontend: OK — committed e2e73ca8f (marker commit 912249f4a)
check-verify-markers: PASS — every touched area of PR #1773 has a verify marker
exit 0
=== #1774 4ef92ee49 Phase 90 642C: GroupedTabBar restyle — underline, sticky, horizontal scroll (E6C)
check-verify-markers: PR #1774 merge 4ef92ee49 head 370f69a33 — areas: frontend — mode: committed-only
  frontend: OK — committed 43142e947 (marker commit ad4f84ba4)
check-verify-markers: PASS — every touched area of PR #1774 has a verify marker
exit 0
=== #1775 7a8e4f012 Phase 90 643A: trust dashboard + balance trend (E7A) — declared side effect: reconciliation card re-sourced from trust-position
check-verify-markers: PR #1775 merge 7a8e4f012 head fd3685602 — areas: backend frontend — mode: committed-only
  backend: OK — committed f140c2ffa (marker commit 596a20d57)
  frontend: OK — committed f140c2ffa (marker commit 859d257d2)
check-verify-markers: PASS — every touched area of PR #1775 has a verify marker
exit 0
=== #1776 5864da54d Phase 90 643B: fees billed vs collected + profitability charts (E7B)
check-verify-markers: PR #1776 merge 5864da54d head 1525101e7 — areas: backend frontend — mode: committed-only
  backend: OK — committed 227022d21 (marker commit 20955954f)
  frontend: OK — committed 227022d21 (marker commit 606d8052d)
check-verify-markers: PASS — every touched area of PR #1776 has a verify marker
exit 0
=== #1777 54096a4fa Phase 90 643C: profitability tables on DataTable + reports index (E7C)
check-verify-markers: PR #1777 merge 54096a4fa head 079c1e05a — areas: frontend — mode: committed-only
  frontend: OK — committed 3a40db5ed (marker commit 8ca3aab84)
check-verify-markers: PASS — every touched area of PR #1777 has a verify marker
exit 0
```
</details>
