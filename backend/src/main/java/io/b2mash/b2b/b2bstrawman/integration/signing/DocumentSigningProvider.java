package io.b2mash.b2b.b2bstrawman.integration.signing;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;

/**
 * Port for sending documents for e-signature and tracking signing status. Tenant-scoped: each org
 * can configure their own signing provider.
 */
public interface DocumentSigningProvider {

  /** Provider identifier (e.g., "docusign", "noop"). */
  String providerId();

  /** Send a document for e-signature. */
  SigningResult sendForSignature(SigningRequest request);

  /** Check the current signing status. */
  SigningStatus checkStatus(String signingReference);

  /** Download the signed copy of a document. */
  byte[] downloadSigned(String signingReference);

  /** Test connectivity with the configured credentials. */
  ConnectionTestResult testConnection();
}
