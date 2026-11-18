# 648F — found (out of scope; reported, not fixed)

- **648D F-1 / 648A N-2 — carried forward unchanged.** Firm-surface wording "section 86(3) investment
  **account**" in `ActivityMessageFormatter.trustTypeNoun`, `AuditEventTypeRegistry trust_firm_inv_place.*`
  and `TrustTransactionTypeLabels.placementNoun` (`FIRM_INV_PLACE` → "placement in a section 86(3) investment
  account"). Not touched by 648F: neither client document calls `placementNoun` (callers:
  `ActivityMessageFormatter`, `TrustNotificationHandler` only), and a section 86(3) placement is
  `INTER_ACCOUNT` with no client, so it never reaches the Statement of Account or the Client Trust Statement.
  Needs its own slice (firm surfaces).
- **648E C-8 — partially closed here.** The two client documents now read an *as-at* invested balance
  (`TrustLedgerQueryRepository.clientInvestedBalance`). The firm client-ledger card's `investedBalance`
  (`investedBalancesByClient`, 648E) is still undated — unchanged by design (out of scope, §18).
