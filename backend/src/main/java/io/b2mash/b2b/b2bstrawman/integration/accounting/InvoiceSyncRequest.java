package io.b2mash.b2b.b2bstrawman.integration.accounting;

import java.time.LocalDate;
import java.util.List;

/** Request to sync an invoice to the external accounting system. */
public record InvoiceSyncRequest(
    String invoiceNumber,
    String customerName,
    List<LineItem> lineItems,
    String currency,
    LocalDate issueDate,
    LocalDate dueDate) {}
