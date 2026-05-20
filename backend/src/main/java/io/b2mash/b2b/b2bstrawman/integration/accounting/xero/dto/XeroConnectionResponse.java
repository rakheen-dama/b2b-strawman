package io.b2mash.b2b.b2bstrawman.integration.accounting.xero.dto;

import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.XeroConnectionStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for the Xero connection status endpoint. Maps from {@link AccountingXeroConnection}
 * entity fields.
 */
public record XeroConnectionResponse(
    UUID id,
    String xeroOrgName,
    XeroConnectionStatus status,
    Instant connectedAt,
    Instant lastTokenRefreshAt,
    Instant accessTokenExpiresAt,
    String scope,
    Instant lastPollAt) {

  public static XeroConnectionResponse from(AccountingXeroConnection connection) {
    return new XeroConnectionResponse(
        connection.getId(),
        connection.getXeroOrgName(),
        connection.getStatus(),
        connection.getConnectedAt(),
        connection.getLastTokenRefreshAt(),
        connection.getAccessTokenExpiresAt(),
        connection.getScope(),
        connection.getLastPollAt());
  }
}
