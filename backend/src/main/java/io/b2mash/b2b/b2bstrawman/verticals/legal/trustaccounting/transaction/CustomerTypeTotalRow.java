package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The posted total of one transaction type for one client on one trust account — a row of {@link
 * TrustTransactionRepository#sumPostedByCustomerAndType}.
 */
public interface CustomerTypeTotalRow {

  UUID getCustomerId();

  String getTransactionType();

  BigDecimal getTotal();
}
