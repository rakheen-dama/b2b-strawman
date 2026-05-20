package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

/**
 * Summary record returned by {@link XeroCustomerImportService#importCustomersFromXero} capturing
 * the outcome of a one-time Xero contact import.
 *
 * @param created number of new Kazi customers created from Xero contacts
 * @param skippedDuplicate number of Xero contacts that matched an existing customer (by email or
 *     name+taxNumber)
 * @param skippedNoEmail number of Xero contacts skipped because they have no email address
 * @param total total number of Xero contacts processed (created + skippedDuplicate +
 *     skippedNoEmail)
 */
public record CustomerImportSummary(
    int created, int skippedDuplicate, int skippedNoEmail, int total) {}
