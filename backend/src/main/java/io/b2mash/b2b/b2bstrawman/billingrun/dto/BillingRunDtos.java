package io.b2mash.b2b.b2bstrawman.billingrun.dto;

import io.b2mash.b2b.b2bstrawman.billingrun.BillingRun;
import io.b2mash.b2b.b2bstrawman.billingrun.BillingRunItemStatus;
import io.b2mash.b2b.b2bstrawman.billingrun.BillingRunStatus;
import io.b2mash.b2b.b2bstrawman.billingrun.EntryType;
import io.b2mash.b2b.b2bstrawman.expense.Expense;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseCategory;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

  public record LoadPreviewRequest(List<UUID> customerIds) {}

  public record BillingRunPreviewResponse(
      UUID billingRunId,
      int totalCustomers,
      BigDecimal totalUnbilledAmount,
      List<BillingRunItemResponse> items) {}

  public record BillingRunItemResponse(
      UUID id,
      UUID customerId,
      String customerName,
      BillingRunItemStatus status,
      BigDecimal unbilledTimeAmount,
      BigDecimal unbilledExpenseAmount,
      int unbilledTimeCount,
      int unbilledExpenseCount,
      BigDecimal totalUnbilledAmount,
      boolean hasPrerequisiteIssues,
      String prerequisiteIssueReason,
      UUID invoiceId,
      String failureReason) {}

  public record TimeEntryResponse(
      UUID id,
      UUID taskId,
      UUID memberId,
      LocalDate date,
      int durationMinutes,
      String description,
      boolean billable,
      BigDecimal billingRateSnapshot,
      String billingRateCurrency,
      BigDecimal billableValue) {

    public static TimeEntryResponse from(TimeEntry te) {
      return new TimeEntryResponse(
          te.getId(),
          te.getTaskId(),
          te.getMemberId(),
          te.getDate(),
          te.getDurationMinutes(),
          te.getDescription(),
          te.isBillable(),
          te.getBillingRateSnapshot(),
          te.getBillingRateCurrency(),
          te.getBillableValue());
    }
  }

  public record CustomerUnbilledSummary(
      UUID customerId,
      String customerName,
      String customerEmail,
      int unbilledTimeEntryCount,
      BigDecimal unbilledTimeAmount,
      int unbilledExpenseCount,
      BigDecimal unbilledExpenseAmount,
      BigDecimal totalUnbilledAmount,
      boolean hasPrerequisiteIssues,
      String prerequisiteIssueReason) {}

  public record ExpenseResponse(
      UUID id,
      UUID projectId,
      UUID memberId,
      LocalDate date,
      String description,
      BigDecimal amount,
      String currency,
      ExpenseCategory category,
      boolean billable,
      BigDecimal markupPercent,
      BigDecimal billableAmount) {

    public static ExpenseResponse from(Expense e) {
      return new ExpenseResponse(
          e.getId(),
          e.getProjectId(),
          e.getMemberId(),
          e.getDate(),
          e.getDescription(),
          e.getAmount(),
          e.getCurrency(),
          e.getCategory(),
          e.isBillable(),
          e.getMarkupPercent(),
          e.getBillableAmount());
    }
  }

  public record UpdateEntrySelectionsRequest(
      @NotNull(message = "selections is required") List<EntrySelectionDto> selections) {}

  public record EntrySelectionDto(
      @NotNull(message = "entryType is required") EntryType entryType,
      @NotNull(message = "entryId is required") UUID entryId,
      boolean included) {}
}
