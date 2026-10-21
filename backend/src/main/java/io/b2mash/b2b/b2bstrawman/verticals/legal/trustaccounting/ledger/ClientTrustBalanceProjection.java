package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One client's trust balance summed over every trust account (client-level, arch §12.14 #12).
 * Returned by {@link ClientLedgerCardRepository#sumBalancesByCustomerIdIn}.
 */
public interface ClientTrustBalanceProjection {

  UUID getCustomerId();

  BigDecimal getBalance();
}
