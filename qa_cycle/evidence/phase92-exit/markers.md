# Phase 92 exit — marker audit (652F, task 652.33)

**Totals.** 36 PRs were checked: **35 Phase 92 slice PRs** (#1786–#1821 except #1805), plus the process PR **#1805**. The results were **36 exit 0 and 0 non-zero**. Of the 35 slice PRs, 33 needed and had a committed marker for every touched area. The other two are docs-only (651D #1813, 651B #1814) and passed with "no verify marker required", as did #1805. No marker was written, edited or fixed.

**Command, run once per PR.** From the worktree root `/Users/rakheendama/Projects/2026/worktree-epic-652F` (HEAD `b08a8916a` = `main`), on 2026-11-19, with each call wrapped in `perl -e 'alarm 18'` so it could not hang:

```
bash scripts/check-verify-markers.sh --committed-only <n> < /dev/null
```

`--committed-only` ignores the index. The script takes the areas from `gh api …/pulls/<n>/files --paginate`. A marker qualifies when it was committed in `<merge>^..HEAD` with `"exit": 0`, and its `commit` is the PR head or a PR-branch ancestor that is not already on `main`. The merge SHA is `mergeCommit` from `gh pr view <n>`. All 36 merge commits are single-parent squash merges. "Areas touched" is `git diff --name-only <merge>^1 <merge>` mapped the way the script maps it.

| Slice | PR | Merge SHA | PR head | Areas touched | Marker(s) found: area @ marker commit (bookkeeping commit) | Exit | Key output line |
|---|---|---|---|---|---|---|---|
| 645A | #1786 | `512b1a6c5` | `23a55222b` | backend, frontend, portal | backend, frontend, portal @`23a55222b` (`aecf93279`) | 0 | `PASS — every touched area of PR #1786 has a verify marker` |
| 649A | #1787 | `a677dca9f` | `05f98e5f0` | backend | backend @`05f98e5f0` (`ad1d63a04`) | 0 | `PASS — every touched area of PR #1787 has a verify marker` |
| 646A | #1788 | `b1d785cf5` | `3285515df` | backend, frontend | backend, frontend @`3285515df` (`5d4704455`) | 0 | `PASS — … PR #1788 …` |
| 649B | #1789 | `dea93bd38` | `91e65c33d` | backend, frontend | backend, frontend @`91e65c33d` (`e7e657787`) | 0 | `PASS — … PR #1789 …` |
| 646B | #1790 | `5d2a4791c` | `65d0f7f72` | backend, frontend | backend, frontend @`65d0f7f72` (`6db212b63`) | 0 | `PASS — … PR #1790 …` |
| 646E | #1791 | `ca02cac48` | `d3964c700` | backend, frontend | backend, frontend @`d3964c700` (`59691960e`) | 0 | `PASS — … PR #1791 …` |
| 646C | #1792 | `e7d3a1d0c` | `92b4b5550` | backend, frontend | backend, frontend @`92b4b5550` (`f7246ced2`) | 0 | `PASS — … PR #1792 …` |
| 646D | #1793 | `66f182a10` | `bea187443` | backend, frontend | backend @`bea187443`; frontend @`a85e728e6` (`1c9fa37c8`) | 0 | `PASS — … PR #1793 …` |
| 647A | #1794 | `7bb94a27c` | `ab1714caf` | backend, frontend | backend, frontend @`ab1714caf` (`64f1325ac`) | 0 | `PASS — … PR #1794 …` |
| 647C | #1795 | `7675b437a` | `df8550113` | backend, frontend | backend @`6e485f412`; frontend @`df8550113` (`7853b7a4f`) | 0 | `PASS — … PR #1795 …` |
| 647B | #1796 | `53eeb7723` | `563ef6185` | backend, frontend | backend, frontend @`563ef6185` (`74bb52cb7`) | 0 | `PASS — … PR #1796 …` |
| 647D | #1797 | `fc214b595` | `45dd3022f` | backend, frontend, portal | backend @`0915e8b0d`; frontend @`45dd3022f`; portal @`0915e8b0d` (`0ab1f6c6c`) | 0 | `PASS — … PR #1797 …` |
| 648C | #1798 | `004cadd24` | `4bb0d595f` | backend, frontend | backend @`4bb0d595f`; frontend @`ef8b6c19b` (`8ca0441ba`) | 0 | `PASS — … PR #1798 …` |
| 648A | #1799 | `c81387734` | `b6afe8c69` | backend, frontend | backend @`b6afe8c69`; frontend @`463e1c64a` (`2a6de24b1`) | 0 | `PASS — … PR #1799 …` |
| 648E | #1800 | `a56f5363d` | `6b4615074` | backend, frontend | backend, frontend @`6b4615074` (`00fb5029f`) | 0 | `PASS — … PR #1800 …` |
| 648B | #1801 | `c30545fc0` | `736bc12e6` | backend, frontend | backend @`c952121f6`; frontend @`736bc12e6` (`2b7be66d6`) | 0 | `PASS — … PR #1801 …` |
| 648D | #1802 | `c441b8143` | `7811f1f86` | backend, portal | backend, portal @`7811f1f86` (`93cfed739`) | 0 | `PASS — … PR #1802 …` |
| 648F | #1803 | `b8940021a` | `f8a8aabb0` | backend | backend @`f8a8aabb0` (`c2fbf2a5f`) | 0 | `PASS — … PR #1803 …` |
| 649C | #1804 | `79bff1b3d` | `a09f3bc91` | backend, frontend | backend, frontend @`a09f3bc91` (`d65884adf`) | 0 | `PASS — … PR #1804 …` |
| *(process PR, not a slice)* | #1805 | — (not printed; docs-only exits before the merge check) | — | none (docs/tooling only) | none required | 0 | `PR #1805 touches no backend/, frontend/, packages/ or portal/ path — docs/tooling-only, no verify marker required` |
| 650A | #1806 | `95b98cc47` | `82cd19bec` | backend, frontend | backend, frontend @`82cd19bec` (`6b65ca2ff`) | 0 | `PASS — … PR #1806 …` |
| 650D | #1807 | `753f64d55` | `662e71131` | backend, frontend | backend @`662e71131`; frontend @`8f9fb18e7` (`58b7a5073`) | 0 | `PASS — … PR #1807 …` |
| 650B | #1808 | `84cf18f5b` | `ae82e5b6e` | backend, frontend | backend @`ae82e5b6e`; frontend @`c0c3a69df` (`67d251e66`) | 0 | `PASS — … PR #1808 …` |
| 650C | #1809 | `417e58073` | `2222963ca` | backend, frontend | backend @`dcd3eb277`; frontend @`733b8352f` (`2cc1caa1c`) | 0 | `PASS — … PR #1809 …` |
| 651C | #1810 | `42daeb7a0` | `af76914fb` | frontend | frontend @`ebb1b0eb8` (`5610b16c2`) | 0 | `PASS — … PR #1810 …` |
| 651A | #1811 | `87b9e2d09` | `66f4a7e3c` | backend | backend @`66f4a7e3c` (`2e7ce9374`) | 0 | `PASS — … PR #1811 …` |
| 651E | #1812 | `23fba0a84` | `540a6e5f7` | backend | backend @`3e85d3307` (`0d282ca04`) | 0 | `PASS — … PR #1812 …` |
| 651D | #1813 | `3631ecdef` | — | none (docs only) | none required | 0 | `PR #1813 touches no backend/, frontend/, packages/ or portal/ path — docs/tooling-only, no verify marker required` |
| 651B | #1814 | `4f01b0c21` | — | none (docs only) | none required | 0 | `PR #1814 touches no … path — docs/tooling-only, no verify marker required` |
| 652A | #1815 | `acf07bc33` | `4a597fba2` | frontend | frontend @`f4ca97684` (`6f55d5e2f`) | 0 | `PASS — … PR #1815 …` |
| 652B | #1816 | `e64de4dc9` | `7a21eb55a` | frontend | frontend @`7a21eb55a` (`30ad1643b`) | 0 | `PASS — … PR #1816 …` |
| 652C | #1817 | `ca2ece798` | `c92ddc0e4` | backend, frontend | backend @`19157ccb0`; frontend @`c92ddc0e4` (`68fad6c80`) | 0 | `PASS — … PR #1817 …` |
| 652D | #1818 | `83998dd40` | `5c2f17f64` | backend | backend @`5c2f17f64` (`1621e4cfe`) | 0 | `PASS — … PR #1818 …` |
| 652G | #1819 | `fa616a66a` | `58bc3b791` | backend | backend @`58bc3b791` (`16c40fa37`) | 0 | `PASS — … PR #1819 …` |
| 652H | #1820 | `72145f505` | `d9f42136e` | backend, frontend | backend, frontend @`d9f42136e` (`66dabd9c1`) | 0 | `PASS — … PR #1820 …` |
| 652E | #1821 | `23c88f239` | `13109ea70` | frontend | frontend @`13109ea70` (`b08a8916a`) | 0 | `PASS — … PR #1821 …` |

The merge SHAs of #1813 and #1814 come from `gh pr view --json mergeCommit`; the script does not print them for a docs-only PR. The raw output of every run is in `/tmp/h652f/markers-0.txt` and `/tmp/h652f/markers-{1..6}.txt` (machine-local).

## Markers whose commit is not the PR head (the script accepts a PR-branch ancestor)

I checked `git diff --name-only <marker commit> <PR head> -- <area>/` for each of the 15 such (PR, area) pairs. In 14 pairs nothing in the area changed after the marker commit. In one pair something did:

- **652C #1817, backend.** The marker is @`19157ccb0` and the head is `c92ddc0e4`. **One backend file changed after the marker:** `TrustReconciliationDraftIntegrationTest.java` (+4 / −1, test-only). That commit is `c92ddc0e4`, "compare the stored completedAt with a stored snapshot (CI ns vs DB µs precision)". The PR body says: "Local full verify … @19157ccb0: EXIT 0, 7 376 testcases … c92ddc0e4 changes one test method only … Backend CI on c92ddc0e4 is the full-suite check of that change". So the committed backend marker does not record a local full verify of the final backend tree. The script passes it by design, because it accepts any PR-branch ancestor. This is recorded as found; it is not judged here.

The 14 pairs with no change in their area after the marker commit:

| PR | Area | Marker commit → head |
|---|---|---|
| #1793 | frontend | `a85e728e6` → `bea187443` |
| #1795 | backend | `6e485f412` → `df8550113` |
| #1797 | backend | `0915e8b0d` → `45dd3022f` |
| #1797 | portal | `0915e8b0d` → `45dd3022f` |
| #1798 | frontend | `ef8b6c19b` → `4bb0d595f` |
| #1799 | frontend | `463e1c64a` → `b6afe8c69` |
| #1801 | backend | `c952121f6` → `736bc12e6` |
| #1807 | frontend | `8f9fb18e7` → `662e71131` |
| #1808 | frontend | `c0c3a69df` → `ae82e5b6e` |
| #1809 | backend | `dcd3eb277` → `2222963ca` |
| #1809 | frontend | `733b8352f` → `2222963ca` |
| #1810 | frontend | `ebb1b0eb8` → `af76914fb` |
| #1812 | backend | `3e85d3307` → `540a6e5f7` |
| #1815 | frontend | `f4ca97684` → `4a597fba2` |

## Conditional slices (all six were reproduced red on `main` and built; none was struck)

`find qa_cycle/evidence -name 'STRUCK*'` returns nothing, so no `STRUCK.md` exists anywhere in the phase.

| Slice | Trigger | Outcome | Red-on-main evidence (`main` SHA) | PR |
|---|---|---|---|---|
| 652A | 652.1 vitest red | **built** | `qa_cycle/evidence/phase92/652A/red-before.txt` (`main` `1d7686586`; vitest exit 1); also `red-before-d6.txt` and the browser repro `repro-browser.md` | #1815 `acf07bc33` |
| 652B | 652.4 vitest red | **built** | `qa_cycle/evidence/phase92/652B/red-before.txt` (`main` `6f55d5e2f`; 3 failed); browser repro `repro-browser.md` | #1816 `e64de4dc9` |
| 652C | `secondCreateForSameAccountAndPeriod_returnsTheDraft` red | **built** (V29) | `qa_cycle/evidence/phase92/652C/red-before.txt` (`main` `30ad1643b`); browser repro `repro-browser.md` | #1817 `ca2ece798` |
| 652D | `StatementBuilderClosedPrimaryAccountIntegrationTest` red | **built** | `qa_cycle/evidence/phase92/652D/red-before-surefire.txt`, `red-before-mvn-summary.txt`, `README.md` (`main` `68fad6c80`; 8 run / 3 failures) | #1818 `83998dd40` |
| 652G | `zeroAmountBalanceRows_areNotImportedAsLines` red | **built** | `qa_cycle/evidence/phase92/652G/repro-red.txt` (`main` `1621e4cfe`; exit 1) | #1819 `fa616a66a` |
| 652H | `ReversedOriginalMatchIntegrationTest` or the 652.36 vitest red | **built** | `qa_cycle/evidence/phase92/652H/red-on-main.txt` (`main` `16c40fa37`; 8 run / 4 failures / 2 errors, plus 2 vitest cases) | #1820 `72145f505` |
