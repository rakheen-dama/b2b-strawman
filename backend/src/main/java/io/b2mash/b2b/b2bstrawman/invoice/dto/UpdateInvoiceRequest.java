package io.b2mash.b2b.b2bstrawman.invoice.dto;

import io.b2mash.b2b.b2bstrawman.invoice.TaxType;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateInvoiceRequest(
    LocalDate dueDate,
    String notes,
    @Size(max = 100) String paymentTerms,
    @PositiveOrZero BigDecimal taxAmount,
    @Size(max = 100) String poNumber,
    TaxType taxType,
    LocalDate billingPeriodStart,
    LocalDate billingPeriodEnd) {}
