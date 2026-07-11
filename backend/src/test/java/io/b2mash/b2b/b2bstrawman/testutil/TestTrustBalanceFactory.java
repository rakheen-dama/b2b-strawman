package io.b2mash.b2b.b2bstrawman.testutil;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Shared trust-seeding recipe: creates an INVESTMENT {@link TrustAccount} and a {@link
 * ClientLedgerCard} with a single deposit so a customer surfaces a positive aggregate trust balance
 * (which drives {@code TrustAwareCollectionsAdvisor} → {@code TRUST_FUNDS_AVAILABLE} advice). Kept
 * in one place so the collections triage-service and read-API tests stay in sync (previously the
 * recipe was copied verbatim into both).
 *
 * <p>Test-scope only — the {@code verticals.legal} imports here are fine; {@code
 * CollectionsCoreBoundaryTest} scans main sources under {@code collections/} only.
 */
public final class TestTrustBalanceFactory {

  private TestTrustBalanceFactory() {}

  /**
   * Seeds a trust account plus a client ledger card with a single {@code amount} deposit for {@code
   * customerId}, so the customer's aggregate trust balance is positive.
   */
  public static void seedTrustBalance(
      TrustAccountRepository trustAccountRepository,
      ClientLedgerCardRepository clientLedgerCardRepository,
      UUID customerId,
      BigDecimal amount) {
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
            LocalDate.now(),
            null);
    var savedTrustAccount = trustAccountRepository.save(trustAccount);
    var ledgerCard = new ClientLedgerCard(savedTrustAccount.getId(), customerId);
    ledgerCard.addDeposit(amount, LocalDate.now());
    clientLedgerCardRepository.save(ledgerCard);
  }
}
