package io.b2mash.b2b.b2bstrawman.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Create-against-existing-customer request (Phase 80, slice 574A). {@code customerId} and {@code
 * title} are required; the rest are optional (stage defaults to the first OPEN stage, owner
 * defaults to the acting member).
 *
 * @param customerId existing customer to attach the deal to (required)
 * @param title deal title (required, max 200 chars)
 * @param stageId target OPEN stage (nullable — defaults to the first OPEN stage)
 * @param valueAmount deal value (nullable)
 * @param ownerId deal owner (nullable — defaults to the acting member)
 * @param source lead source (nullable)
 * @param expectedCloseDate expected close date (nullable)
 */
public record CreateDealRequest(
    @NotNull(message = "customerId is required") UUID customerId,
    @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must not exceed 200 characters")
        String title,
    UUID stageId,
    BigDecimal valueAmount,
    UUID ownerId,
    String source,
    LocalDate expectedCloseDate) {}
