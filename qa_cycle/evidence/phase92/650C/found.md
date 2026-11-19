# 650C found — defects outside the slice (reported, not fixed here)

- **F-650C-1 — MEDIUM (pre-existing on `main` @67d251e66; audit plane) — report PDF-export and execute audit rows are never persisted.**
  `ReportExportService.execute` / `exportAsPdf` run in a `@Transactional(readOnly = true)` transaction (unchanged
  by 650C except for the added `REPEATABLE_READ`). `DatabaseAuditService.log` is plain `@Transactional` (REQUIRED),
  joins that read-only transaction, whose Hibernate flush mode is MANUAL, so `auditEventRepository.save` is never
  flushed (UUID id → no insert at persist time). No error is raised.
  Delta reviewer A's probe (fresh tenant, MockMvc, scratch test since deleted): PDF export `REPORT_EXPORTED` 0→0
  (lost); CSV export 0→1 (kept: `writeCsvAndAudit` runs outside the transaction); `POST /execute`
  `REPORT_GENERATED` 0→0 (lost). Same result with main's annotations (`readOnly = true` only), so 650C neither
  causes nor widens it.
  Related: `ReportingControllerTest.exportPdfPersistsReportExportedAuditEvent` passes falsely (asserts
  `isNotEmpty()` on a tenant where sibling CSV tests already wrote `REPORT_EXPORTED` rows);
  `kazi-architecture/30-modules/reporting.md:62` says `REPORT_GENERATED` is recorded.
  Impact: PDF exports of every report (incl. the Section 35 pack) leave no audit trail (ADR-264 expects exports audited).
  Fix direction: audit write in `REQUIRES_NEW` or after commit; tighten the test to a count delta. → follow-up (decisions log).

- **F-650C-2 — LOW (pre-existing; reports) — receipts & payments drops entries reversed after the period.**
  `TrustTransactionRepository.findForReceiptsPayments` excludes any row whose status is now `REVERSED`, whatever
  the contra's date. After 650C CR-7 the Section 35 sweep evidence (parts 5/6) keeps a sweep reversed after the
  year end, so the same pack can list a sweep in parts 5/6 but not in part 1. Same family as 649B/646E F1
  (receipts & payments and reversals). Fix direction: the as-at contra rule CR-7 applied to `fundSweeps`
  (`r.transaction_date <= :toDate`). → follow-up.

- **F-650C-3 — LOW (wording, carried from v3) — the Section 35 "Client Ledger Statements" note still mentions the
  `customer_id` parameter.** Unchanged text from pack v3; a firm user does not know a parameter name.

- **F-650C-4 — INFO — F-7 (per-account integrity-check cost inside `checkFirm`) remains open**; `trust-position`
  (new definition in v4) calls `checkFirm(asOfDate)`, so its cost scales as in 649C OQ #9 (3 + 9 statements per account).
