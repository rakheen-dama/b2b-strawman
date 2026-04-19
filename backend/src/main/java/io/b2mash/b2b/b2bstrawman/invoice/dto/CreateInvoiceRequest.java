package io.b2mash.b2b.b2bstrawman.invoice.dto;

import io.b2mash.b2b.b2bstrawman.invoice.TaxType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(
    @NotNull UUID customerId,
    @NotBlank @Size(min = 3, max = 3) String currency,
    List<UUID> timeEntryIds,
    List<UUID> expenseIds,
    List<UUID> disbursementIds,
    LocalDate dueDate,
    String notes,
    @Size(max = 100) String paymentTerms,
    @Size(max = 100) String poNumber,
    TaxType taxType,
    LocalDate billingPeriodStart,
    LocalDate billingPeriodEnd) {}
