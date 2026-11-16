# 649B observed gates — PR #1789 @ 91e65c33d (2026-11-16, SAST)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-649B` (HEAD 91e65c33d, tree clean before and after). No source edits, no commits, no Maven. All state changes went through the browser; psql was used read-only as evidence.

## Stack builds
| Stack | Built | V17 | Evidence |
|---|---|---|---|
| #1 (lifecycle + OBS) | "Building from source at: …/worktree-epic-649B" | flyway v17 "phase92 trust reversal contra" success=t at 20:22:19 UTC | `stack1-build.log`, `stack1-v17-proof.txt`, `stack1-backend.log` (0 ERROR lines) |
| #2 (depth-ii) | same source path, after `e2e-down.sh` wipe (0 trust rows before the suite) | v17 success=t at 20:30:21 UTC | `stack2-build.log`, `stack2-v17-proof.txt` |

- The `/app/app.jar` V17 file (`db/migration/tenant/V17__phase92_trust_reversal_contra.sql`) has sha1 b6b2acb9…. That is **identical** to the worktree file on both stacks. The jar also holds `TrustTransactionTypeLabels.class`, which is new in 649B.
- `chk_trust_txn_posting` is present in its tight form (from `pg_constraint` and `\d+`). The posted branch requires `status IN (RECORDED, APPROVED, REVERSED) AND abs(ledger_effect) = amount AND (cashbook_effect = ledger_effect OR cashbook_effect = 0)`. The unposted branch requires `AWAITING_APPROVAL`/`REJECTED` with zero effects.
- Both stacks had the single tenant schema `tenant_7d218705360b`. Health was UP and `:3001` returned HTTP 200.

## Gate 1 — E2E-LEGAL lifecycle (stack #1): PASS (observed)
**105 passed / 0 failed / 0 skipped in 3.0 min** (184 s wall), exit 0. This matches the baseline of 105. Log: `lifecycle.log`. The 34 trust-touching tests are listed in `lifecycle-trust-tests.txt`; they cover deposits, the fee transfer with Bob's approval leaving Moroka at R241 500, reconciliation, interest and investments. The 16 documentation screenshots are in `lifecycle-artifacts/documentation-screenshots/`.

## Gate 3 — E2E-LEGAL depth-ii (stack #2, fresh): PASS (observed)
**40 passed / 4 skipped / 0 failed in 1.9 min** (114 s wall), exit 0. This matches the baseline of 40/4. Log: `depth-ii.log`, summary in `depth-ii-summary.txt`.

The 4 skips are the baseline ones:
- `day-30-45-soa-writeoff.spec.ts:361` (fixme)
- `null-prefetch-probe.spec.ts:74` ×3

`.last-run.json` reads `passed`. Read-only sums after the run (`depth-ii-derived-sums.out`):
- Sipho has DEPOSIT 5 000 and PAYMENT −1 250, giving 3 750.00.
- The cashbook is 3 750.00 and EQ-2 is 0.00.

## Gate 2 — OBS in a real browser (stack #1, after the lifecycle run): PASS (observed), with two known anomalies
Script: `obs.cjs` (Playwright library, headless Chromium, TZ Africa/Johannesburg, Alice/Bob via `/mock-login` user select). Output:
- `obs/obs-result.json` and `obs/obs-output.log`
- `obs/NN-*.png` and `.txt`
- SQL in `obs-sums.sql` / `obs-sums.out`

Dates used: today 2026-11-16, Apex deposit 2026-11-01, payment 2026-11-06, reversal 2026-11-11.

### a. RECORDED deposit reversal needs another member's approval (QuickCollect R45 000)
| Check | UI | SQL |
|---|---|---|
| Reverse offered on the RECORDED deposit | yes (`01`) | status RECORDED |
| Dialog | Reason field; "Reversal date (optional)"; the note "A deposit reversal must be approved by another member before it takes effect." No raw rule enum (`02`) | — |
| After Alice submits | contra row "Reversal R 45 000,00 AWAITING APPROVAL" (`03`) | contra REVERSAL AWAITING_APPROVAL, posted_at NULL, effects 0/0; original still RECORDED |
| QC balance while pending | R 45 000,00 (unchanged) | 45000.00; EQ-2 286 500 − 286 500 = 0.00 |
| Alice approves her own contra | refused: "The transaction recorder cannot be the sole approver…" (`04`) | — |
| Bob approves | contra APPROVED; original REVERSED; Reverse no longer offered (`05`) | original REVERSED, reversed_by_id = contra id 7ec784af…, effects stay +45 000/+45 000; contra ledger −45 000, cashbook −45 000, posted |
| QC balance after approval | R 0,00 (list `06`, ledger page `07`) | 0.00 |
| EQ-2 after approval | — | 241 500 − 241 500 = 0.00 |

Bonus: Moroka's R250 000 deposit (`18`). Alice could submit the reversal. Bob's approval was **refused** with: "Insufficient trust balance for Moroka Family Trust …. Available: R241 500,00, Requested: R250 000,00". The contra stays AWAITING_APPROVAL with posted_at NULL and zero effects. Moroka stays at 241 500.00.

### b. A back-dated payment reversed with a past reversal date (Apex)
- Recorded through the UI as Alice:
  - deposit R20 000, dated 2026-11-01 (RECORDED);
  - payment R4 000, dated 2026-11-06 (AWAITING APPROVAL).
- Bob approved the payment, so it is APPROVED. The balance is 16 000.00.
- The reverse dialog on the APPROVED payment shows the plain note (no approval note). The date picker's `min` is 2026-11-06.

| Attempt | Outcome | Contra rows |
|---|---|---|
| reversal date 2026-11-04 (before the payment) | blocked by the picker: `rangeUnderflow` is true, "Value must be 2026/11/06 or later.", and nothing is submitted (`12`) | 0 |
| reversal date 2026-11-19 (future) | refused by the server, shown in the dialog: "The reversal date cannot be in the future" (`13`) | 0 |
| reversal date 2026-11-11 | accepted (`14`) | 1 |

The contra posted at once:
- type REVERSAL, status RECORDED;
- **transaction_date = 2026-11-11, which is the chosen reversal date**;
- ledger and cashbook +4 000.

The payment is REVERSED, with reversed_by_id pointing to the contra, and keeps its −4 000 effects. EQ-2 is 261 500 − 261 500 = 0.00.

### c. Apex as-at balances, and the statement
| As-at | Expected | SQL |
|---|---|---|
| 2026-11-05 (before the original) | B = 20 000 | 20000.00 |
| 2026-11-09 (between) | B − P = 16 000 | 16000.00 |
| 2026-11-16 (today) | B = 20 000 | 20000.00 |

Reports → Client Ledger Statement for Apex, 2026-11-09 → 2026-11-16 (`17`):
- Opening is R 16 000,00, credits R 4 000,00, debits R 0,00, closing R 20 000,00. Opening plus the listed rows equals closing.
- The single row is the 2026-11-11 contra.
- **However, its Type column prints the raw `REVERSAL`, and its Description prints the firm's reversal reason** (see anomalies 1 and 2).

The client-ledger page (`15`) shows the row with the human label "Reversal". The ledger-totals breakdown nets reversals into their buckets: Deposits 20 000, Payments 0, Balance 20 000.

### d. The reason never reaches the client portal
`portal.portal_trust_transaction`:
- The REVERSAL rows read "Reversal — REV-DEP/QC/…" and "Reversal — REV-OBS649B-PAY-…".
- 0 portal rows contain the reason text.
- Running balances are QC 45 000 → 0 and Apex 20 000 → 16 000 → 20 000.
- `portal_trust_balance` is QC 0.00 and Apex 20 000.00.

Firm-side, the contra descriptions keep the reasons, as designed.

### e. Errors
**Console errors: 0. Page errors: 0.** The backend log has 0 ERROR lines.

### Final figures (UI vs SQL)
| Figure | UI | SQL (posted effects) |
|---|---|---|
| Overview trust balance (cashbook) | R 261 500,00 | 261500.00 |
| Overview client ledgers "(agrees with the cashbook)" | R 261 500,00 | 261500.00 |
| Moroka | R 241 500,00 | 241500.00 |
| Apex | R 20 000,00 | 20000.00 |
| QuickCollect | R 0,00 | 0.00 |
| EQ-2 | — | 0.00 |

These invariant counts are all 0 (`obs-sums.out` "final: invariants"):
- posted status but unposted;
- pending row with an effect;
- bad effect shape;
- REVERSED without a posted contra;
- contra that is not the negation of its original;
- reversed pair that does not net to zero.

## Anomalies (observed, not fixed)
1. **The Client Ledger Statement report prints the raw type `REVERSAL`** in its Type column (`obs/17-c3-statement-result.png`). This is the pre-existing F7 in `qa_cycle/evidence/phase92/649B/found.md`, which already prints DEPOSIT and PAYMENT raw; it is deferred to 646E. The OBS brief expected a human label here, so this check is **not met**.
2. **The same report shows the firm's reversal reason in its Description column.** This is W1 in found.md: a firm report, but the UI card invites handing it to the client. It is awaiting an orchestrator call. The portal, SoA and Client Trust Statement do not show the reason; the portal was verified here.
3. **The Reverse button is still offered on an original while its contra is pending** (QC during step a; Moroka after the refused approval). This is the known R1 in found.md, deferred to 646E. The backend's "already reversed" guard was not exercised in the UI here.

## Cleanup
- I copied the suite screenshots to `lifecycle-artifacts/` and `depth-ii-artifacts/`, then ran `git clean -fd -- documentation frontend/e2e/screenshots`.
- Afterwards `git status --short` was **empty**: `.epic-brief.md` and `frontend/test-results/` are ignored. HEAD is **91e65c33d**.
- Stack #2 is still running.

---
_Committed in the 649B bookkeeping commit (PR #1789 merged as dea93bd38). As for 646A, the raw suite logs (`lifecycle.log`, `depth-ii.log`), stack build/backend logs and the suites' documentation-screenshot folders were not committed; the summaries, V17 proofs, OBS script, OBS screens and SQL are._
