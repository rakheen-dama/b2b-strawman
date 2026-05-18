package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Xero accounting integration.
 *
 * @param clientId Xero OAuth2 client ID
 * @param clientSecret Xero OAuth2 client secret
 * @param redirectUri OAuth2 callback URL
 * @param authorizeUrl Xero authorization endpoint
 * @param tokenUrl Xero token endpoint
 * @param connectionsUrl Xero connections API endpoint
 * @param apiBaseUrl Xero API base URL
 * @param revocationUrl Xero token revocation endpoint
 */
@ConfigurationProperties(prefix = "kazi.xero")
public record XeroProperties(
    String clientId,
    String clientSecret,
    String redirectUri,
    String authorizeUrl,
    String tokenUrl,
    String connectionsUrl,
    String apiBaseUrl,
    String revocationUrl) {

  public XeroProperties {
    if (authorizeUrl == null || authorizeUrl.isBlank()) {
      authorizeUrl = "https://login.xero.com/identity/connect/authorize";
    }
    if (tokenUrl == null || tokenUrl.isBlank()) {
      tokenUrl = "https://identity.xero.com/connect/token";
    }
    if (connectionsUrl == null || connectionsUrl.isBlank()) {
      connectionsUrl = "https://api.xero.com/connections";
    }
    if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
      apiBaseUrl = "https://api.xero.com/api.xro/2.0";
    }
    if (revocationUrl == null || revocationUrl.isBlank()) {
      revocationUrl = "https://identity.xero.com/connect/revocation";
    }
  }
}
