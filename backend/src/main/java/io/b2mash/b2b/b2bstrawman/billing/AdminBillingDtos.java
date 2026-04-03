package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

public final class AdminBillingDtos {

  private AdminBillingDtos() {}

  public record AdminBillingOverrideRequest(
      String status, String billingMethod, Instant currentPeriodEnd, @NotBlank String adminNote) {}

  public record AdminTenantBillingResponse(
      UUID organizationId,
      String organizationName,
      String verticalProfile,
      String subscriptionStatus,
      String billingMethod,
      Instant trialEndsAt,
      Instant currentPeriodEnd,
      Instant graceEndsAt,
      Instant createdAt,
      int memberCount,
      String adminNote,
      boolean isDemoTenant) {

    public static AdminTenantBillingResponse from(
        Organization org, Subscription sub, long memberCount, String verticalProfile) {
      return new AdminTenantBillingResponse(
          org.getId(),
          org.getName(),
          verticalProfile,
          sub.getSubscriptionStatus().name(),
          sub.getBillingMethod().name(),
          sub.getTrialEndsAt(),
          sub.getCurrentPeriodEnd(),
          sub.getGraceEndsAt(),
          sub.getCreatedAt(),
          (int) memberCount,
          sub.getAdminNote(),
          sub.getBillingMethod().isCleanupEligible());
    }
  }

  public record ExtendTrialRequest(@Positive int days) {}
}
