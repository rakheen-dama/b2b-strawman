package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published after a payment pulled from Xero is successfully matched and reconciled against a Kazi
 * invoice. Downstream listeners can use this for notifications, dashboards, or further processing.
 */
public record XeroPaymentReconciledEvent(
    UUID invoiceId, String invoiceNumber, BigDecimal amount, String xeroPaymentId) {}
