package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

/** Type of entity being synced to/from the external accounting system. */
public enum SyncEntityType {
  /** Invoice sync (push invoice to Xero, or pull invoice updates). */
  INVOICE,
  /** Customer/contact sync (push customer to Xero). */
  CUSTOMER,
  /** Payment pull — reconciling payments received in Xero back to Kazi invoices. */
  PAYMENT_PULL
}
