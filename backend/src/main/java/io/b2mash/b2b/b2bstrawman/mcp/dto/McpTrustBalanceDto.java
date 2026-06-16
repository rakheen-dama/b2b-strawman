package io.b2mash.b2b.b2bstrawman.mcp.dto;

import java.util.UUID;

/**
 * Trust balance projection for {@code get_trust_balance} (§11.4 / Epic 564A). Money is carried as
 * minor units (long) + a 3-letter {@code currency} code resolved from the firm's settings — trust
 * domain DTOs carry {@link java.math.BigDecimal} major units with no currency of their own.
 *
 * @param trustAccountId the trust account the balance belongs to
 * @param customerId the client whose ledger card this is, or {@code null} for the account total
 * @param balanceMinor balance in minor units (e.g. cents)
 * @param currency 3-letter currency code (firm default)
 */
public record McpTrustBalanceDto(
    UUID trustAccountId, UUID customerId, long balanceMinor, String currency) {}
