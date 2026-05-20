package io.b2mash.b2b.b2bstrawman.integration.accounting.xero.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Xero sync settings DTO used for both API request/response and storage in {@code
 * OrgIntegration.configJson}. Validated on inbound PUT requests.
 */
public record XeroSyncSettings(
    @Min(1) @Max(1440) int paymentPollIntervalMinutes,
    @NotNull String pushTrigger,
    @NotNull Boolean autoSyncEnabled) {

  public static XeroSyncSettings defaults() {
    return new XeroSyncSettings(15, "APPROVED", true);
  }
}
