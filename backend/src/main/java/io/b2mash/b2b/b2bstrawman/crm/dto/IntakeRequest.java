package io.b2mash.b2b.b2bstrawman.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Deal intake request (Phase 80, §11.4). UI-decoupled so the {@code intake-triage} AI seam can call
 * the same path. Either {@code customerId} (attach to an existing customer) OR a nested {@code
 * customer} (create a PROSPECT customer atomically) must be supplied — the "exactly one" rule is
 * validated in {@code DealIntakeService}, not via bean validation.
 *
 * @param customerId existing customer to attach the deal to (nullable)
 * @param customer inline customer to create when {@code customerId} is null (nullable)
 * @param title deal title (required)
 * @param stageId target OPEN stage (nullable — defaults to the first OPEN stage)
 * @param valueAmount deal value (nullable — defaults to zero)
 * @param ownerId deal owner (nullable — defaults to the acting member)
 * @param source lead source (nullable)
 * @param expectedCloseDate expected close date (nullable)
 */
public record IntakeRequest(
    UUID customerId,
    NewCustomer customer,
    @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must not exceed 200 characters")
        String title,
    UUID stageId,
    BigDecimal valueAmount,
    UUID ownerId,
    String source,
    LocalDate expectedCloseDate) {

  /** Inline customer details used only when {@code customerId} is null. */
  public record NewCustomer(String name, String email, String phone) {}
}
