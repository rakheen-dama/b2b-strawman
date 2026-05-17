package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

/** Status of the OAuth2 connection to a Xero tenant. */
public enum XeroConnectionStatus {
  /** Active connection — tokens are valid or refreshable. */
  CONNECTED,
  /** Token refresh failed — needs re-authorization. */
  REFRESH_FAILED,
  /** Connection revoked by user in Xero — must reconnect. */
  REVOKED
}
