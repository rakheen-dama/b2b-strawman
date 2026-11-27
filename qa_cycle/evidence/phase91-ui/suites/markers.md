# 670A — verify-marker audit over the Phase 91 merged range (task 670.2)

Command, run on 2026-11-27 ≈11:58Z from the 670A worktree at `main` @ `c4595f924`. It uses committed-only mode, so nothing staged in an index can make a PR pass:

```bash
for pr in $(seq 1862 1882); do echo "=== #$pr $(gh pr view $pr --json mergeCommit,title --jq '(.mergeCommit.oid[0:9])+" "+.title')"; bash scripts/check-verify-markers.sh --committed-only $pr; echo "exit $?"; done > /tmp/670A/markers.txt 2>&1
```

Range: #1862–#1882. These are all 21 Phase 91 PRs: the prototype/evidence PR 661A, 661B, 662A (evidence), and 663A–669B. #1860 and #1861 are Phase 93 fixes and fall outside the range.

**Result: 21 / 21 PASS (exit 0).** Every expectation from 670.2 holds:
- 661A (#1862) and 662A (#1864) are docs-only, so the script reports "docs/tooling-only, no verify marker required".
- Every UI slice (661B, 663A–669B) has a frontend marker.
- 661B (#1863) has a portal marker, and so does **669A (#1881)**. 669A touched `packages/shared/src/format.ts` (K-68, D-92 DA-16), and `packages/` maps to frontend + portal. The 670.1 task text named 661B only; that is a brief correction, not a gap.
- **No backend area anywhere.** `grep -E "areas:" /tmp/670A/markers.txt | grep -c backend` = 0. `git diff --name-only 551778100..c4595f924 -- backend gateway | wc -l` = 0.

Columns: "marker" is the verified PR-branch commit in the marker's `commit` field. "in" is the `main` commit that committed the marker, which is the slice's bookkeeping commit.

| PR | Merge | Title | Areas | Markers (verified commit, committed in) | Exit |
|---|---|---|---|---|---|
| #1862 | `0474dcf4e` | docs(661A): light-scheme founder pack | none (docs) | — (docs/tooling-only) | 0 |
| #1863 | `023ddb315` | Epic 661B: light-mode colour scheme A1 | frontend, portal | frontend + portal `d62c5dc19` (in `16423ca4b`) | 0 |
| #1864 | `cb8c26839` | Epic 662A: kinks register (evidence only) | none (docs) | — (docs/tooling-only) | 0 |
| #1865 | `d1fdeb475` | Epic 663A: EmptyState sizing rule | frontend | frontend `8da7beaf9` (in `799c20c53`) | 0 |
| #1866 | `c66dd0575` | Epic 663B: EmptyState variant sweep | frontend | frontend `e405f930a` (in `3bc2110dc`) | 0 |
| #1867 | `5daacb930` | Epic 663C: cross-cutting shell kinks | frontend | frontend `7149f9c11` (in `cee91f031`) | 0 |
| #1868 | `6d0f5377e` | Epic 664A: settings shell | frontend | frontend `b5e430276` (in `e0fc21fb6`) | 0 |
| #1869 | `d90376640` | Epic 664B: DataTable server-paged mode | frontend | frontend `1e5a0b548` (in `02eab076f`) | 0 |
| #1870 | `dac162907` | Epic 665A: Settings Firm I | frontend | frontend `bf2b15f51` (in `60f67bfe3`) | 0 |
| #1871 | `cccc20cde` | Epic 665B: Settings Firm II | frontend | frontend `a8720ea0c` (in `f926ebf22`) | 0 |
| #1872 | `582ad0511` | Epic 665C: Settings Matters | frontend | frontend `b87d6d742` (in `1a867a784`) | 0 |
| #1873 | `4ccb88b07` | Epic 665D: Settings Fees & Trust | frontend | frontend `5d3430e06` (in `18b95bcec`) | 0 |
| #1874 | `f8f51f37e` | Epic 665E: Settings Documents | frontend | frontend `092a21309` (in `d10aa6d3e`) | 0 |
| #1875 | `05ea3eb5d` | Epic 665F: Settings Connections | frontend | frontend `cec81dad5` (in `2ef49ee4b`) | 0 |
| #1876 | `55de88b29` | Epic 666A: Fee notes list, fee runs, debtors, unbilled | frontend | frontend `97d84aeed` (in `b7c8091c2`) | 0 |
| #1877 | `f8417b892` | Epic 666B: Fee-note detail hero + history tables | frontend | frontend `b8a2e2032` (in `fe99599e8`) | 0 |
| #1878 | `795571eea` | Epic 667A: Trust transactions, client ledgers, history | frontend | frontend `c8cbb4d53` (in `427d6826c`) | 0 |
| #1879 | `8e6af4c6b` | Epic 667B: Trust reconciliation, investments, reports | frontend | frontend `a5e0197d5` (in `cf539e61a`) | 0 |
| #1880 | `22eb92ec6` | Epic 668A: Diary, My Work, timesheet frame, notifications | frontend | frontend `7d0336046` (in `ed22a7bfa`) | 0 |
| #1881 | `99f18aea3` | Epic 669A: Light pass — compliance … information requests | frontend, portal | frontend + portal `d61e5dd4e` (in `c54a4cba9`) | 0 |
| #1882 | `23c441961` | Epic 669B: Light pass II — team, AI reviews, platform-admin | frontend | frontend `089a05400` (in `c4595f924`) | 0 |

**Marker commit vs PR head.** For 8 PRs the marker's verified commit is not the PR head: #1863, #1865, #1866, #1867, #1869, #1870, #1872 and #1881. In each of these, one or two commits were pushed after the verified commit. The script accepts any commit from the PR branch. Each post-marker commit was checked through `gh api repos/…/pulls/<n>/commits` and `…/commits/<sha>` (`files[].filename`). None of them touched `frontend/`, `portal/`, `packages/` or `backend/`; they were evidence and docs only. So every marker covers the product code that merged. For the other 13 PRs the marker commit is the PR head.

```
#1863 marker=d62c5dc19 commits-after-marker=1 product-files-after=0
#1865 marker=8da7beaf9 commits-after-marker=2 product-files-after=0
#1866 marker=e405f930a commits-after-marker=2 product-files-after=0
#1867 marker=7149f9c11 commits-after-marker=1 product-files-after=0
#1869 marker=1e5a0b548 commits-after-marker=1 product-files-after=0
#1870 marker=bf2b15f51 commits-after-marker=1 product-files-after=0
#1872 marker=b87d6d742 commits-after-marker=1 product-files-after=0
#1881 marker=d61e5dd4e commits-after-marker=1 product-files-after=0
(all other PRs: commits-after-marker=0)
```

This 670A PR is docs/evidence-only: it touches no `frontend/`, `portal/`, `packages/` or `backend/` path, so it carries no product marker.

<details><summary>Raw output (trimmed to the per-PR verdict lines)</summary>

```
=== #1862 0474dcf4e docs(661A): light-scheme founder pack — screenshots, AA tables, palette check (FD-1)
check-verify-markers: PR #1862 touches no backend/, frontend/, packages/ or portal/ path — docs/tooling-only, no verify marker required
exit 0
=== #1863 023ddb315 Epic 661B: light-mode colour scheme A1 — scoped firm-shell tokens (ADR-392 Accepted)
check-verify-markers: PR #1863 merge 023ddb315 head 8e3fcc1f4 — areas: frontend portal — mode: committed-only
  frontend: OK — committed 16423ca4b (marker commit d62c5dc19)
  portal: OK — committed 16423ca4b (marker commit d62c5dc19)
exit 0
=== #1864 cb8c26839 Epic 662A: kinks register — real-browser walk on the A1 scheme + FD-3 pack (evidence only)
check-verify-markers: PR #1864 touches no backend/, frontend/, packages/ or portal/ path — docs/tooling-only, no verify marker required
exit 0
=== #1865 d1fdeb475 — areas: frontend — head 857ca9f26 — frontend: OK — committed 799c20c53 (marker commit 8da7beaf9) — exit 0
=== #1866 c66dd0575 — areas: frontend — head 574c5224f — frontend: OK — committed 3bc2110dc (marker commit e405f930a) — exit 0
=== #1867 5daacb930 — areas: frontend — head 898e66f3e — frontend: OK — committed cee91f031 (marker commit 7149f9c11) — exit 0
=== #1868 6d0f5377e — areas: frontend — head b5e430276 — frontend: OK — committed e0fc21fb6 (marker commit b5e430276) — exit 0
=== #1869 d90376640 — areas: frontend — head 97ca159f2 — frontend: OK — committed 02eab076f (marker commit 1e5a0b548) — exit 0
=== #1870 dac162907 — areas: frontend — head de8c19a42 — frontend: OK — committed 60f67bfe3 (marker commit bf2b15f51) — exit 0
=== #1871 cccc20cde — areas: frontend — head a8720ea0c — frontend: OK — committed f926ebf22 (marker commit a8720ea0c) — exit 0
=== #1872 582ad0511 — areas: frontend — head 5a1006a11 — frontend: OK — committed 1a867a784 (marker commit b87d6d742) — exit 0
=== #1873 4ccb88b07 — areas: frontend — head 5d3430e06 — frontend: OK — committed 18b95bcec (marker commit 5d3430e06) — exit 0
=== #1874 f8f51f37e — areas: frontend — head 092a21309 — frontend: OK — committed d10aa6d3e (marker commit 092a21309) — exit 0
=== #1875 05ea3eb5d — areas: frontend — head cec81dad5 — frontend: OK — committed 2ef49ee4b (marker commit cec81dad5) — exit 0
=== #1876 55de88b29 — areas: frontend — head 97d84aeed — frontend: OK — committed b7c8091c2 (marker commit 97d84aeed) — exit 0
=== #1877 f8417b892 — areas: frontend — head b8a2e2032 — frontend: OK — committed fe99599e8 (marker commit b8a2e2032) — exit 0
=== #1878 795571eea — areas: frontend — head c8cbb4d53 — frontend: OK — committed 427d6826c (marker commit c8cbb4d53) — exit 0
=== #1879 8e6af4c6b — areas: frontend — head a5e0197d5 — frontend: OK — committed cf539e61a (marker commit a5e0197d5) — exit 0
=== #1880 22eb92ec6 — areas: frontend — head 7d0336046 — frontend: OK — committed ed22a7bfa (marker commit 7d0336046) — exit 0
=== #1881 99f18aea3 Epic 669A: Light pass — compliance, conflict check, adverse parties, disbursements, tariffs, information requests
check-verify-markers: PR #1881 merge 99f18aea3 head 47a944685 — areas: frontend portal — mode: committed-only
  frontend: OK — committed c54a4cba9 (marker commit d61e5dd4e)
  portal: OK — committed c54a4cba9 (marker commit d61e5dd4e)
exit 0
=== #1882 23c441961 — areas: frontend — head 089a05400 — frontend: OK — committed c4595f924 (marker commit 089a05400) — exit 0
(every non-docs PR also printed "check-verify-markers: PASS — every touched area of PR #<n> has a verify marker")
```
</details>
