package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import io.b2mash.b2b.b2bstrawman.tax.dto.TaxBreakdownEntry;
import java.util.List;

/**
 * Groups the 6 tax-related fields carried by {@link InvoiceSyncEvent}. Only SENT events populate
 * this; PAID and VOID events pass {@code null} to signal that tax data is irrelevant for those
 * status transitions.
 */
public record TaxContext(
    List<TaxBreakdownEntry> taxBreakdown,
    String taxRegistrationNumber,
    String taxRegistrationLabel,
    String taxLabel,
    boolean taxInclusive,
    boolean hasPerLineTax) {}
