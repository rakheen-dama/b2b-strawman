package io.b2mash.b2b.b2bstrawman.integration;

/** Categorizes the external integration domains available to tenants. */
public enum IntegrationDomain {
  ACCOUNTING("noop"),
  AI("noop"),
  DOCUMENT_SIGNING("noop"),
  EMAIL("smtp"),
  PAYMENT("noop");

  private final String defaultSlug;

  IntegrationDomain(String defaultSlug) {
    this.defaultSlug = defaultSlug;
  }

  /** Returns the adapter slug to use when no org-level integration is configured. */
  public String getDefaultSlug() {
    return defaultSlug;
  }
}
