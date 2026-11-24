package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Phase 93 657D (§17.3.11): what the trust books read back from a committed take-on, so the books
 * never read the take-on dataset package directly. Read-only.
 */
@Component
public class TakeOnPositionFacts {

  private final TakeOnBatchRepository batches;

  public TakeOnPositionFacts(TakeOnBatchRepository batches) {
    this.batches = batches;
  }

  /**
   * The bank balance entered at take-on for {@code accountId} — present only when the account is
   * the batch's banking account and the balance was entered. An investment account the batch also
   * marked taken on has none: the entered balance is the banking account's.
   */
  public Optional<BigDecimal> enteredBankBalance(UUID batchId, UUID accountId) {
    return batches
        .findById(batchId)
        .filter(batch -> accountId.equals(batch.getTrustAccountId()))
        .map(TakeOnBatch::getBankBalance);
  }
}
