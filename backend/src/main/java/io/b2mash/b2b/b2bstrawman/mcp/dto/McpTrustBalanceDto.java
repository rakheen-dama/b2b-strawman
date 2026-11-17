package io.b2mash.b2b.b2bstrawman.mcp.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Trust balance projection for {@code get_trust_balance} (§11.4 / Epic 564A; §16.6.5, 646D). Money
 * is carried as minor units (long) + a 3-letter {@code currency} code resolved from the firm's
 * settings — trust domain DTOs carry {@link java.math.BigDecimal} major units with no currency of
 * their own.
 *
 * <p>Without a client: the account's trust position as at {@code asAt} (today) — {@code
 * balanceMinor} is the client ledgers on the account (meaning unchanged), and beside it {@code
 * investedMinor} (section 86(4) investment creditors), {@code fundLedgerMinor} (Fund interest and
 * charges), {@code unidentifiedDepositsMinor}, {@code totalTrustCreditorsMinor} (every trust
 * creditor) and {@code cashbookMinor}; the books balance when the last two are equal. With a
 * client: that client's general balance in {@code balanceMinor}; the account-level fields are null.
 * Components are appended, never reordered — the consumer plugin reads the JSON keys.
 *
 * @param trustAccountId the trust account the balance belongs to
 * @param customerId the client whose ledger card this is, or {@code null} for the account
 * @param balanceMinor client balance (with a client) or the client ledgers (without), minor units
 * @param currency 3-letter currency code (firm default)
 * @param investedMinor section 86(4) investment creditors (account only; null with a client)
 * @param fundLedgerMinor Fund interest and charges ledger (account only)
 * @param unidentifiedDepositsMinor unidentified deposits (account only)
 * @param totalTrustCreditorsMinor every trust creditor (account only)
 * @param cashbookMinor the account's cashbook (account only)
 * @param asAt the date the account figures are as at (account only)
 */
public record McpTrustBalanceDto(
    UUID trustAccountId,
    UUID customerId,
    long balanceMinor,
    String currency,
    Long investedMinor,
    Long fundLedgerMinor,
    Long unidentifiedDepositsMinor,
    Long totalTrustCreditorsMinor,
    Long cashbookMinor,
    LocalDate asAt) {}
