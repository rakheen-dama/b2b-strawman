package io.b2mash.b2b.b2bstrawman.testutil;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Shared trust-seeding recipe: creates an INVESTMENT {@link TrustAccount} (section 86(3) basis — an
 * INVESTMENT account carries its basis since 648C, ADR-379) and posts a single {@code DEPOSIT}
 * through {@link TrustPostingService} so a customer surfaces a positive derived trust balance
 * (which drives {@code TrustAwareCollectionsAdvisor} → {@code TRUST_FUNDS_AVAILABLE} advice).
 * Client balances are derived from posted rows since Phase 92 / 646A (ADR-378), so the recipe posts
 * a row instead of writing a stored card. Kept in one place so the collections triage-service and
 * read-API tests stay in sync.
 *
 * <p>Test-scope only — the {@code verticals.legal} imports here are fine; {@code
 * CollectionsCoreBoundaryTest} scans main sources under {@code collections/} only.
 */
public final class TestTrustBalanceFactory {

  /** A fixed past posting date — the derived balance has no as-at bound for these readers. */
  private static final LocalDate SEED_DATE = LocalDate.of(2026, 1, 15);

  private TestTrustBalanceFactory() {}

  /**
   * Seeds a trust account and posts a single {@code amount} deposit for {@code customerId}, so the
   * customer's aggregate trust balance is positive. {@code recordedBy} must be a member of the
   * tenant (FK {@code trust_transactions.recorded_by}).
   */
  public static void seedTrustBalance(
      TrustAccountRepository trustAccountRepository,
      TrustPostingService trustPostingService,
      UUID customerId,
      BigDecimal amount,
      UUID recordedBy) {
    var trustAccount =
        new TrustAccount(
            "Test Trust",
            "Test Bank",
            "002",
            "9876543210",
            TrustAccountType.INVESTMENT,
            false,
            false,
            null,
            SEED_DATE,
            null,
            InvestmentBasis.FIRM_DISCRETION);
    var savedTrustAccount = trustAccountRepository.save(trustAccount);
    trustPostingService.record(
        new TrustTransaction(
            savedTrustAccount.getId(),
            "DEPOSIT",
            amount,
            customerId,
            null,
            null,
            "TEST-TRUST-" + UUID.randomUUID().toString().substring(0, 8),
            "Test trust balance",
            SEED_DATE,
            "RECORDED",
            recordedBy));
  }
}
