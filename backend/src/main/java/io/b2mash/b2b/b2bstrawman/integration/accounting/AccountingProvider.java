package io.b2mash.b2b.b2bstrawman.integration.accounting;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;

/**
 * Port for syncing financial data to external accounting software. Tenant-scoped: each org can
 * configure their own provider (Xero, QuickBooks, etc.).
 */
public interface AccountingProvider {

  /** Provider identifier (e.g., "xero", "quickbooks", "noop"). */
  String providerId();

  /** Sync an invoice to the external accounting system. */
  AccountingSyncResult syncInvoice(InvoiceSyncRequest request);

  /** Sync a customer record to the external accounting system. */
  AccountingSyncResult syncCustomer(CustomerSyncRequest request);

  /** Test connectivity with the configured credentials. */
  ConnectionTestResult testConnection();
}
