package io.b2mash.b2b.b2bstrawman.billingrun.dto;

import io.b2mash.b2b.b2bstrawman.billingrun.BillingRun;
import io.b2mash.b2b.b2bstrawman.billingrun.BillingRunStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class BillingRunDtos {

  private BillingRunDtos() {}

  public record CreateBillingRunRequest(
      @Size(max = 300, message = "name must be at most 300 characters") String name,
      @NotNull(message = "periodFrom is required") LocalDate periodFrom,
      @NotNull(message = "periodTo is required") LocalDate periodTo,
      @NotNull(message = "currency is required")
          @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
          String currency,
      boolean includeExpenses,
      boolean includeRetainers) {}

  public record BillingRunResponse(
      UUID id,
      String name,
      BillingRunStatus status,
      LocalDate periodFrom,
      LocalDate periodTo,
      String currency,
      boolean includeExpenses,
      boolean includeRetainers,
      Integer totalCustomers,
      Integer totalInvoices,
      BigDecimal totalAmount,
      Integer totalSent,
      Integer totalFailed,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt,
      Instant completedAt) {

    public static BillingRunResponse from(BillingRun run) {
      return new BillingRunResponse(
          run.getId(),
          run.getName(),
          run.getStatus(),
          run.getPeriodFrom(),
          run.getPeriodTo(),
          run.getCurrency(),
          run.isIncludeExpenses(),
          run.isIncludeRetainers(),
          run.getTotalCustomers(),
          run.getTotalInvoices(),
          run.getTotalAmount(),
          run.getTotalSent(),
          run.getTotalFailed(),
          run.getCreatedBy(),
          run.getCreatedAt(),
          run.getUpdatedAt(),
          run.getCompletedAt());
    }
  }
}
