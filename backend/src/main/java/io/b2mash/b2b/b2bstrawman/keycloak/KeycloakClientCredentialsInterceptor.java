package io.b2mash.b2b.b2bstrawman.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * HTTP request interceptor that adds OAuth2 client credentials tokens to outgoing Keycloak Admin
 * API requests. Fetches and caches access tokens, refreshing them 30 seconds before expiry.
 */
class KeycloakClientCredentialsInterceptor implements ClientHttpRequestInterceptor {

  private static final int REFRESH_MARGIN_SECONDS = 30;

  private final String tokenUrl;
  private final String clientId;
  private final String clientSecret;
  private final RestClient tokenClient;

  private String cachedToken;
  private Instant tokenExpiry = Instant.EPOCH;

  KeycloakClientCredentialsInterceptor(
      String serverUrl, String realm, String clientId, String clientSecret) {
    this(serverUrl, realm, clientId, clientSecret, RestClient.create());
  }

  /** Constructor allowing a custom RestClient for the token endpoint (e.g., for TLS in tests). */
  KeycloakClientCredentialsInterceptor(
      String serverUrl,
      String realm,
      String clientId,
      String clientSecret,
      RestClient tokenClient) {
    this.tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.tokenClient = tokenClient;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    request.getHeaders().setBearerAuth(getAccessToken());
    return execution.execute(request, body);
  }

  private synchronized String getAccessToken() {
    if (cachedToken == null || Instant.now().isAfter(tokenExpiry)) {
      refreshToken();
    }
    return cachedToken;
  }

  private void refreshToken() {
    var formData = new LinkedMultiValueMap<String, String>();
    formData.add("grant_type", "client_credentials");
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);

    var response =
        tokenClient
            .post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(formData)
            .retrieve()
            .body(TokenResponse.class);

    if (response == null) {
      throw new IllegalStateException("Failed to obtain access token from Keycloak");
    }

    cachedToken = response.accessToken();
    tokenExpiry = Instant.now().plusSeconds(response.expiresIn() - REFRESH_MARGIN_SECONDS);
  }

  private record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") long expiresIn,
      @JsonProperty("token_type") String tokenType) {}
}
