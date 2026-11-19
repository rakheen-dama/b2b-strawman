# 652G — found (deferred, non-blocking)

- **F-652G-1 (comment):** `TrustReconciliationService.java:826-827` says "the parsers set period_end to the last line's date". Since 652G, a trailing zero-amount closing-balance row can be dated after the last returned line. `period_end` still takes that row's date, while `findLastLineDate` returns the last movement's date. Behaviour is unchanged or more correct, because the period-fit rules use the last line. Only the comment wording is stale. Source: reviewer A.
- **F-652G-2 (marker vocabulary):** these descriptions are not recognised as balance markers:
  - a description containing a non-breaking space (U+00A0), because `\s` and `trim()` do not match it;
  - a description with trailing punctuation, such as "Balance b/f:";
  - abbreviations such as "OPENING BAL" and "BAL BF".
  Such a row stays a statement line, exactly as on main, so no money row is ever dropped. Widen the vocabulary only if a real bank export shows one of these forms (L4: generic vocabulary only). Source: reviewer A.
- **F-652G-3 (nit):** `CsvBankStatementParser.isBalanceRow` compiles `\s+` on every call. A `private static final Pattern` would avoid it. Source: reviewer B.
