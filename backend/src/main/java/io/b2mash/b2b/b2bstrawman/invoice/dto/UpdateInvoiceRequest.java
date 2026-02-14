package io.b2mash.b2b.b2bstrawman.invoice.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateInvoiceRequest(
    LocalDate dueDate,
    String notes,
    @Size(max = 100) String paymentTerms,
    @PositiveOrZero BigDecimal taxAmount) {}
