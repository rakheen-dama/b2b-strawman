# ADR-233: Bank Reconciliation Matching Strategy

**Status**: Accepted

**Context**:

Trust reconciliation requires matching imported bank statement lines to recorded trust transactions. SA banks (FNB, Standard Bank, Nedbank, ABSA) export CSV statements with varying column layouts and inconsistent reference formatting. The matcher must handle exact matches (same reference), probable matches (same amount and close date), and flag uncertain cases for manual review. The UX is a split-pane interface where bookkeepers manually match remaining items.

A firm processing 200+ trust transactions per month cannot afford to match every statement line by hand. At the same time, trust accounting errors have legal consequences under the Legal Practice Act and FICA regulations — an incorrect auto-match that links the wrong deposit to the wrong matter could misstate a trust balance and trigger regulatory action. The matching strategy must balance automation speed with reconciliation accuracy.

**Options Considered**:

1. **Manual matching only** — Import the bank statement, display statement lines alongside trust transactions in a split-pane view, and let the bookkeeper match every line by hand. No algorithmic assistance.
   - Pros:
     - Zero risk of incorrect auto-matches. Every match is a deliberate human decision. For trust accounting, where errors have legal consequences, this is the safest approach.
     - Simplest implementation: import the CSV, render two lists, let the user drag-and-drop or click to match. No matching algorithm, no confidence scoring, no threshold tuning.
     - Bookkeepers retain full control. Some firms may prefer this — they do not trust software to match trust transactions and want to verify every line personally.
   - Cons:
     - Extremely tedious at scale. A firm with 200 trust transactions per month and a corresponding bank statement with 200+ lines would spend hours on matching — most of which are obvious matches (same reference, same amount, same date) that software could handle instantly.
     - Error-prone in practice. Manual matching of hundreds of lines leads to fatigue errors — the very errors this approach claims to prevent. A bookkeeper on line 180 of 200 is more likely to mismatch than an algorithm checking reference + amount + date.
     - No competitive advantage. Every trust accounting package on the market (Legal Suite, GhostPractice, LegalTrek) offers at least basic auto-matching. A manual-only approach would be perceived as a regression.

2. **Reference-only auto-matching** — Auto-match only when the bank statement reference exactly matches a trust transaction reference (case-insensitive string equality). Everything that does not match exactly is presented for manual review.
   - Pros:
     - Very low false-positive risk. An exact reference match on the same bank account is almost certainly the correct transaction. The auto-match confidence is near 100%.
     - Simple to implement and reason about. The matching logic is a single SQL join or hash lookup — no scoring, no thresholds, no fuzzy matching.
     - Easy to explain to users. "We auto-matched these because the reference was identical" is a clear, auditable justification.
   - Cons:
     - Misses a large percentage of valid matches. SA bank references are routinely truncated, reformatted, or decorated with extra characters during processing. A deposit entered with reference "MABENA-TRUST-DEP" might appear on the FNB statement as "MABENA TRUST D" (truncated to 15 characters, hyphens replaced with spaces). An exact match fails, but the amount + date combination is unambiguous to a human — and to a scoring algorithm.
     - Different banks apply different transformations: FNB truncates to 15 characters, Standard Bank preserves full references but uppercases them, Nedbank strips special characters, ABSA prepends branch codes. A reference-only matcher would need bank-specific normalization rules to achieve even moderate match rates — at which point it is no longer "simple."
     - The match rate in practice would be 30-50% at best, leaving the bookkeeper to manually match the remaining 50-70%. This is marginally better than Option 1 but still tedious for high-volume firms.

3. **Multi-signal scored matching with confidence threshold** — Score potential matches on multiple signals (reference similarity, amount equality, date proximity), assign a confidence score between 0 and 1, auto-match above a configurable threshold, and flag everything below the threshold for manual review.
   - Pros:
     - Handles real-world messy data. The scoring tiers accommodate the full spectrum of match quality:
       - Exact reference match: confidence 1.0 (auto-match)
       - Same amount + same date + single candidate: confidence 0.8 (auto-match)
       - Same amount + date within +/-3 days + single candidate: confidence 0.6 (manual review)
       - Same amount only + multiple candidates: confidence 0.4 (manual review)
     - Expected auto-match rate of 70-85% for well-referenced transactions, reducing bookkeeper workload dramatically. The remaining 15-30% that require manual review are genuinely ambiguous cases where human judgment adds value.
     - The confidence score is stored with each match, providing an audit trail. A reviewer can filter by confidence to spot-check auto-matched transactions — "show me all matches below 0.9" is a one-click audit.
     - Extensible: new signals (payee name, transaction type, historical pattern) can be added to the scoring model without changing the matching architecture.
   - Cons:
     - More complex to implement than Options 1 or 2. Requires a scoring function, candidate generation, deduplication logic (one statement line maps to at most one transaction), and threshold configuration.
     - The confidence thresholds need tuning based on real data. The initial values (0.8 auto-match threshold) are based on patterns from existing trust accounting software, but may need adjustment after observing actual match rates across different banks and firms.
     - Potential for false positives in edge cases: two trust deposits of the same amount on the same day from different clients. The scorer would assign high confidence to both candidates, but only one is correct. This is mitigated by the "single candidate" requirement for auto-matching — if multiple candidates exist, the match drops to manual review regardless of score.

4. **ML-based matching** — Train a machine learning model on historical match data to predict future matches. The model learns bank-specific reference transformations, firm-specific patterns, and temporal correlations from past reconciliations.
   - Pros:
     - Highest theoretical accuracy. A model trained on thousands of historical matches would learn patterns that a hand-tuned scoring function cannot — bank-specific truncation rules, firm-specific reference conventions, seasonal patterns in transaction timing.
     - Improves over time: as bookkeepers correct matches, the model retrains on the corrections. Match rates improve with usage.
     - Handles novel patterns: a scoring function with fixed rules cannot adapt to a new bank format or a firm that changes its reference convention. A model can, given enough examples.
   - Cons:
     - No training data exists. The platform is new; there are no historical reconciliations to train on. The cold-start problem is severe — the model would perform worse than the scoring approach until hundreds of reconciliations have been completed and reviewed.
     - Infrastructure complexity: requires a model training pipeline, feature extraction, model versioning, inference serving, and monitoring. This is a significant engineering investment for a matching problem that the scoring approach handles adequately.
     - Explainability: trust reconciliation is a regulated activity. "The model predicted this match with 87% confidence" is less auditable than "the reference matched exactly and the amount matched." Bookkeepers and auditors need to understand why a match was made, not just how confident the system is.
     - Overkill for the volume. A firm with 200 transactions/month generates 2,400 training examples per year — far too few for a model to learn meaningful patterns beyond what the scoring function already captures.
     - Regulatory risk: if the model makes an incorrect match that leads to a trust balance error, the firm cannot point to a clear rule that was followed — only a probabilistic prediction. This is a harder position to defend in a disciplinary hearing.

**Decision**: Option 3 — Multi-signal scored matching with confidence threshold.

**Rationale**:

Option 1 is tedious and counterproductive. A firm with 200 transactions/month would spend hours on manual matching — most of which are obvious matches that software should handle. Manual matching at scale also introduces fatigue errors, undermining the safety argument. The whole point of trust reconciliation software is to automate the predictable cases and focus human attention on the genuinely ambiguous ones.

Option 2 misses too many valid matches. SA bank references are routinely truncated, reformatted, or stripped of special characters during interbank processing. A deposit reference entered as "MABENA-TRUST-DEP" might appear on the bank statement as "MABENA TRUST D" — an exact match fails, but the amount + date combination is unambiguous. Reference-only matching would achieve 30-50% auto-match rates at best, leaving the bookkeeper to manually handle the majority of transactions. This is marginally better than Option 1 but not enough to justify the software's value proposition.

Option 3 handles the real-world messy data that SA bank integrations produce. The multi-signal approach combines reference similarity, amount equality, and date proximity into a single confidence score. The scoring tiers are calibrated conservatively:

- Exact reference match produces confidence 1.0 — auto-matched.
- Same amount + same date + single candidate produces confidence 0.8 — auto-matched.
- Same amount + date within +/-3 days + single candidate produces confidence 0.6 — flagged for manual review.
- Same amount only + multiple candidates produces confidence 0.4 — flagged for manual review.

The auto-match threshold is set at 0.8 (>=). This means only high-confidence matches — exact references or unambiguous amount+date combinations with a single candidate — are matched automatically. Everything else goes to the bookkeeper for review. The threshold is deliberately conservative: it is better to flag a match for human review than to auto-match incorrectly. Trust reconciliation errors have legal consequences under the Legal Practice Act, and an incorrect auto-match that misstates a trust balance could trigger regulatory action against the firm.

Option 4 is premature. There is no training data — the platform is new and no historical reconciliations exist. The volume (200 transactions/month per firm, 5-20 firms) is too low to train a useful model. The multi-signal scoring approach already handles 80%+ of cases based on patterns observed in existing trust accounting software (Legal Suite, GhostPractice). ML-based matching can be revisited when the platform has accumulated thousands of reviewed reconciliations — at that point, the historical match data from Option 3 becomes the training set for Option 4. The scoring approach is not throwaway; it is the foundation for future ML enhancement.

**Consequences**:

- BankStatementLine carries three matching fields: `match_status` (enum: UNMATCHED, AUTO_MATCHED, MANUALLY_MATCHED, EXCLUDED), `match_confidence` (decimal 0-1, null when unmatched), and `trust_transaction_id` (FK to TrustTransaction, null when unmatched). The `match_status` distinguishes how a match was made — auto-matched lines are auditable separately from manually matched lines.
- Auto-matching runs as a batch operation after CSV import, before the bookkeeper opens the reconciliation review screen. The import flow is: (1) upload CSV, (2) parse and persist statement lines, (3) run auto-matcher, (4) present results in the split-pane review UI with auto-matched items pre-linked and flagged.
- The matcher only considers approved trust transactions that do not already have an existing bank statement link, within the statement date range +/-7 days. This prevents double-matching (a transaction already reconciled in a previous statement) and limits the candidate set to a reasonable time window.
- CSV parser must be pluggable (strategy pattern) to handle different SA bank formats. FNB, Standard Bank, Nedbank, and ABSA each have different column layouts, date formats, and reference field positions. Each bank format is a `BankStatementParser` implementation selected by the user during import (or auto-detected from CSV headers where possible). New bank formats are added by implementing the interface — no changes to the matching logic.
- The confidence threshold (0.8) is a constant, not a configurable setting. Trust reconciliation accuracy is not something firms should be able to lower. If a firm wants more aggressive auto-matching, they should improve their reference consistency at the point of deposit — not lower the matching threshold. This is a deliberate product decision, not a technical limitation.
- The split-pane review UI shows: (a) auto-matched items with their confidence scores (bookkeeper can unmatch if incorrect), (b) suggested matches below the threshold (bookkeeper can accept or reject), (c) completely unmatched items on both sides. The bookkeeper works through the unmatched and suggested items, then marks the reconciliation as complete.
- Audit trail: every match (auto or manual) is recorded with timestamp, user (for manual matches), confidence score, and the signals that contributed to the score. This supports regulatory review — an auditor can see exactly why each statement line was matched to a specific trust transaction.
- Related: trust transaction approval workflow (transactions must be approved before they are eligible for matching), bank account configuration (each trust bank account maps to a specific bank format parser).
