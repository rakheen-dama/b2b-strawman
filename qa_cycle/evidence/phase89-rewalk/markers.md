# 636B marker audit — Phase 89 PRs #1716–#1741 (636.5)

Run by walker 636B.3 at 2026-11-06 01:16 SAST from `$WT` (branch `phase89/07B-kc-rewalk`, HEAD `0295b55bf`) with `bash scripts/check-verify-markers.sh --committed-only <pr>` (PR #1740 / 635B). "Committed" = the commit that recorded the marker, as the script reports it.

| PR | Areas touched | Marker committed in | Summary line (verbatim) | Exit |
|---|---|---|---|---|
| #1716 | backend frontend portal | 3369eb8e1  | check-verify-markers: PASS — every touched area of PR #1716 has a verify marker | 0 |
| #1717 | backend | 4486084c8  | check-verify-markers: PASS — every touched area of PR #1717 has a verify marker | 0 |
| #1718 | backend frontend | 7d4f9d611  | check-verify-markers: PASS — every touched area of PR #1718 has a verify marker | 0 |
| #1719 | backend frontend | 1e85fcc72  | check-verify-markers: PASS — every touched area of PR #1719 has a verify marker | 0 |
| #1720 | backend | 28aa7d090  | check-verify-markers: PASS — every touched area of PR #1720 has a verify marker | 0 |
| #1721 | frontend portal | 4a481a35d  | check-verify-markers: PASS — every touched area of PR #1721 has a verify marker | 0 |
| #1722 | backend frontend | 7a1b3de1d  | check-verify-markers: PASS — every touched area of PR #1722 has a verify marker | 0 |
| #1723 | backend frontend | c99955487  | check-verify-markers: PASS — every touched area of PR #1723 has a verify marker | 0 |
| #1724 | backend frontend | 60978d96b  | check-verify-markers: PASS — every touched area of PR #1724 has a verify marker | 0 |
| #1725 | backend frontend | 562cc7d2b  | check-verify-markers: PASS — every touched area of PR #1725 has a verify marker | 0 |
| #1726 | frontend portal | 6beaeedf5  | check-verify-markers: PASS — every touched area of PR #1726 has a verify marker | 0 |
| #1727 | backend frontend | ec2906de4  | check-verify-markers: PASS — every touched area of PR #1727 has a verify marker | 0 |
| #1728 | backend | 9cc348d31  | check-verify-markers: PASS — every touched area of PR #1728 has a verify marker | 0 |
| #1729 | frontend portal | c1c9172d9  | check-verify-markers: PASS — every touched area of PR #1729 has a verify marker | 0 |
| #1730 | backend | 5566c72b1  | check-verify-markers: PASS — every touched area of PR #1730 has a verify marker | 0 |
| #1731 | backend | 85bb3867b  | check-verify-markers: PASS — every touched area of PR #1731 has a verify marker | 0 |
| #1732 | portal | f52bf21e0  | check-verify-markers: PASS — every touched area of PR #1732 has a verify marker | 0 |
| #1733 | backend frontend | 7b7509f87  | check-verify-markers: PASS — every touched area of PR #1733 has a verify marker | 0 |
| #1734 | backend frontend | 1c6bd158a  | check-verify-markers: PASS — every touched area of PR #1734 has a verify marker | 0 |
| #1735 | backend frontend | 6eb56bbc4  | check-verify-markers: PASS — every touched area of PR #1735 has a verify marker | 0 |
| #1736 | frontend | bf8d49d3b  | check-verify-markers: PASS — every touched area of PR #1736 has a verify marker | 0 |
| #1737 | frontend | 000262d91  | check-verify-markers: PASS — every touched area of PR #1737 has a verify marker | 0 |
| #1738 | — | — | check-verify-markers: PR #1738 touches no backend/, frontend/, packages/ or portal/ path — docs/tooling-only, no verify marker required | 0 |
| #1739 | frontend | 8aebe9e65  | check-verify-markers: PASS — every touched area of PR #1739 has a verify marker | 0 |
| #1740 | — | — | check-verify-markers: PR #1740 touches no backend/, frontend/, packages/ or portal/ path — docs/tooling-only, no verify marker required | 0 |
| #1741 | — | — | check-verify-markers: PR #1741 touches no backend/, frontend/, packages/ or portal/ path — docs/tooling-only, no verify marker required | 0 |

**Result: 26 / 26 PRs exit 0. No non-zero exit → no follow-up marker commit needed.** Matches the scout's pre-computed table (brief §7) row for row, including every recording commit.

- **Backend-verified Phase 89 PRs (#1722+): 11** — #1722, #1723, #1724, #1725, #1727, #1728, #1730, #1731, #1733, #1734, #1735 (expected 11, `$TASK:198`). Plus the pre-phase backend markers #1716–#1720 (5).
- **Frontend/portal markers of the no-backend-verify code PRs:** #1726 (frontend portal), #1729 (frontend portal), #1732 (portal), #1736 (frontend), #1737 (frontend), #1739 (frontend) — all OK. (#1721 frontend portal, pre-phase, also OK.)
- **Docs/tooling/evidence-only PRs, no marker required, exit 0:** #1738 (635A), #1740 (635B), #1741 (636A).

## Tree identity for E.11 (run at `0295b55bf`)

| Check | Output | Meaning |
|---|---|---|
| `git diff --stat a8d5b86ae..HEAD -- backend` | 0 lines (empty) | backend byte-identical to #1735's verified head → E.11a scored by the #1735 marker |
| `git diff --stat ef20a6ea9..HEAD -- frontend packages` | 0 lines (empty) | frontend + packages identical to #1739 → E.11b/d by the #1739 marker |
| `git diff --stat 9b67f713b..HEAD -- portal packages` | 0 lines (empty) | portal identical to #1732 |

The 636B branch adds only `qa_cycle/evidence/phase89-rewalk/**` on top of `86e0fd5de`.
