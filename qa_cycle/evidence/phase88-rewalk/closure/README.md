# Phase 88 closure (629D) — measurement index

Measured 2026-10-29T23:36Z–23:40Z (= 2026-10-30 01:36–01:40 SAST) in worktree branch `phase88/12D-closure-report` at HEAD `59aee87316b6e68d5ff05f050aaa160040e8d527` (= origin/main; product tree = last code PR #1709 `87784b799` = 628I gate head `cb10c5d63`). Baseline `50b88ed5c` (#1642). Docs-only slice: no full `./mvnw verify`, FE-GATE or PORTAL-GATE was run; the gate evidence is the 628I markers. The closure report itself is `tasks/phase88-demo-ready-root-cause-closure.md` § Closure report. Local paths in the logs are written as `$WT` (this worktree), `$MAIN` (the main checkout) and `$HOME`.

| Log | Proves | Used in |
|---|---|---|
| `logs/closure-provenance.txt` | HEAD = origin/main; no code commit since #1709 (`87784b799`); empty code diff vs `cb10c5d63`; the 9 docs-only commits since; the three marker files verbatim | C.1, C.7 |
| `logs/closure-metrics.txt` | Before (`50b88ed5c`) / after (HEAD) counts: `@Entity` 103 → 104 (+`CommentAttachment`), `Capability` 27, audit `hasSize` 29 → 33, `InvoiceStatus` 5 → 6, `AcceptanceStatus` 6 → 7, matter tabs 7/21 → 7/22, pages 57 / 71 / 18, `@RestController` 122, latest migrations V3/V1 → V9/V2, 51 redirect rows; `PortalContactDirectoryEntry` is a record; `BaselineSchemaParityTest` targets; `tool-contract.json` history and counts | C.2, C.3 |
| `logs/closure-contract-tests.txt` | Targeted backend pin run at HEAD: 18 classes, 127 `<testcase>` / 0 failures / 0 errors / 0 skipped, EXIT 0, 66 s; wire-test stdout; real-write-path test names present in the reports | C.1, C.3 |
| `logs/closure-vitest-guards.txt` | Frontend guard vitest 7 files / 136 passed; portal 3 files / 23 passed; `no-isvisible-catch` enabled as `"error"` in both ESLint configs | C.1, C.3, C.4 |
| `logs/closure-terminology.txt` | 628I gate grep re-run: FE 38 / 16 / 2 non-test (comments), portal 13 / 8 / 4 (comments), backend 632 / 182 (= 628I); allow-lists 9 / 0; Phase 87 mechanism scan 0; ESLint rule files byte-identical | C.4 |
| `logs/closure-redirects.txt` | Read-only `curl -sI` probes: 7 × 308 on :3000, 2 × 308 on :3001, 1 × 308 on portal :3002; which process serves each port; `legacy-redirects.ts` untouched since the baseline (51 rows) | C.5 |
| `logs/closure-migrations.txt` | Latest V9 / V2; per-file `git log`; only V4 modified (620A, 622B) before its first persistent application; DDL guard counts; read-only Keycloak `flyway_schema_history` re-read (tenants at V9, `public` V2) | C.6 |
| `logs/closure-deck-check.txt` | `exec-deck.html` after the swap: 13 slides, 13 image refs all 1440 × 900, phase88 files not referenced; browser render check not run (Playwright MCP blocks `file:`) | C.10 |
