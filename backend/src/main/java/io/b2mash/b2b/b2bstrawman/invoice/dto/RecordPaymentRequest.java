package io.b2mash.b2b.b2bstrawman.invoice.dto;

import io.b2mash.b2b.b2bstrawman.invoice.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Record Payment form body (Phase 88 U15, ADR-347): {@code amount} is the money that moved (a
 * partial amount leaves the fee note PARTIALLY_PAID; above the balance due → 409), {@code
 * paymentMethod} how it arrived.
 *
 * <p>{@code amount} and {@code paymentMethod} are nullable until 622E's form sends amount + method
 * (D5): a reference-only body settles the remaining balance with method {@code OTHER}.
 */
public record RecordPaymentRequest(
    @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
    PaymentMethod paymentMethod,
    @Size(max = 255) String paymentReference) {}
