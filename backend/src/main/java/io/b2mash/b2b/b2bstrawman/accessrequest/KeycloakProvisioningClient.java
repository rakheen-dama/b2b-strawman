package io.b2mash.b2b.b2bstrawman.accessrequest;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Client for the Keycloak Admin REST API used by the access-request approval pipeline. Uses the
 * master realm admin credentials (password grant) to authenticate. Caches the access token and
 * refreshes it before expiry.
 *
 * <p>This bean is only created when {@code keycloak.admin.auth-server-url} is configured,
 * preventing startup failures in dev/test environments without Keycloak.
 */
@Service
@ConditionalOnProperty(prefix = "keycloak.admin", name = "auth-server-url")
public class KeycloakProvisioningClient {

  private static final Logger log = LoggerFactory.getLogger(KeycloakProvisioningClient.class);

  private final RestClient restClient;
  private final RestClient tokenRestClient;
  private final String tokenUrl;
  private final String adminUsername;
  private final String adminPassword;

  private volatile String cachedToken;
  private volatile Instant tokenExpiry = Instant.MIN;

  public KeycloakProvisioningClient(
      @Value("${keycloak.admin.auth-server-url}") String authServerUrl,
      @Value("${keycloak.admin.realm}") String realm,
      @Value("${keycloak.admin.username}") String adminUsername,
      @Value("${keycloak.admin.password}") String adminPassword) {
    var httpClient = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    this.restClient =
        RestClient.builder()
            .baseUrl(authServerUrl + "/admin/realms/" + realm)
            .requestFactory(requestFactory)
            .defaultStatusHandler(
                HttpStatusCode::isError,
                (request, response) -> {
                  HttpStatusCode status = response.getStatusCode();
                  String body =
                      new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                  log.error("Keycloak Admin API error: {} — {}", status, body);
                  if (status.value() == 401 || status.value() == 403 || status.value() >= 500) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Keycloak Admin API unavailable");
                  }
                  throw new ResponseStatusException(
                      status, "Keycloak Admin API error: " + status.value());
                })
            .build();
    this.tokenRestClient = RestClient.builder().requestFactory(requestFactory).build();
    this.tokenUrl = authServerUrl + "/realms/master/protocol/openid-connect/token";
    this.adminUsername = adminUsername;
    this.adminPassword = adminPassword;
  }

  /**
   * Creates an organization in Keycloak.
   *
   * <p>Note: Keycloak returns 409 Conflict if the alias already exists, which the error handler
   * will surface as a ResponseStatusException.
   *
   * @return the organization ID extracted from the Location header
   * @throws IllegalStateException if the Location header is missing from the response
   */
  public String createOrganization(String name, String slug) {
    var body = Map.of("name", name, "alias", slug, "enabled", true);
    var response =
        restClient
            .post()
            .uri("/organizations")
            .header("Authorization", "Bearer " + getAdminToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    var location = response.getHeaders().getLocation();
    if (location == null) {
      throw new IllegalStateException(
          "Keycloak org creation succeeded but no Location header returned");
    }
    String path = location.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  /**
   * Invites a user to the organization by email. Keycloak 26.5 uses the {@code
   * /members/invite-user} endpoint with form-urlencoded body.
   */
  public void inviteUser(String orgId, String email) {
    String formBody = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
    restClient
        .post()
        .uri("/organizations/{orgId}/members/invite-user", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(formBody)
        .retrieve()
        .toBodilessEntity();
  }

  @SuppressWarnings("unchecked")
  private synchronized String getAdminToken() {
    if (Instant.now().isBefore(tokenExpiry)) {
      return cachedToken;
    }
    String formBody =
        "grant_type=password"
            + "&client_id=admin-cli"
            + "&username="
            + URLEncoder.encode(adminUsername, StandardCharsets.UTF_8)
            + "&password="
            + URLEncoder.encode(adminPassword, StandardCharsets.UTF_8);
    var response =
        tokenRestClient
            .post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(formBody)
            .retrieve()
            .body(Map.class);
    if (response == null
        || response.get("access_token") == null
        || response.get("expires_in") == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Invalid token response from Keycloak");
    }
    cachedToken = (String) response.get("access_token");
    tokenExpiry = Instant.now().plusSeconds(((Number) response.get("expires_in")).longValue() - 30);
    return cachedToken;
  }
}
