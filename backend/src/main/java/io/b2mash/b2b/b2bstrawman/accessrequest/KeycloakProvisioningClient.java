package io.b2mash.b2b.b2bstrawman.accessrequest;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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
  private final String frontendBaseUrl;

  private volatile String cachedToken;
  private volatile Instant tokenExpiry = Instant.MIN;

  public KeycloakProvisioningClient(
      @Value("${keycloak.admin.auth-server-url}") String authServerUrl,
      @Value("${keycloak.admin.realm}") String realm,
      @Value("${keycloak.admin.username}") String adminUsername,
      @Value("${keycloak.admin.password}") String adminPassword,
      @Value("${app.base-url:http://localhost:3000}") String frontendBaseUrl) {
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
    this.frontendBaseUrl = frontendBaseUrl;
  }

  /**
   * Creates an organization in Keycloak, or returns the existing org ID if one with the same
   * name/alias already exists (409 Conflict).
   *
   * @return the organization ID
   */
  @SuppressWarnings("unchecked")
  public String createOrganization(String name, String slug) {
    var body = Map.of("name", name, "alias", slug, "enabled", true, "redirectUrl", frontendBaseUrl);
    try {
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
    } catch (ResponseStatusException e) {
      if (e.getStatusCode().value() != 409) {
        throw e;
      }
      // Org already exists — look it up by alias
      log.info("Keycloak org with alias '{}' already exists, looking up ID", slug);
      return findOrganizationByAlias(slug);
    }
  }

  private String findOrganizationByAlias(String alias) {
    var orgs =
        restClient
            .get()
            .uri("/organizations?search={alias}&exact=true", alias)
            .header("Authorization", "Bearer " + getAdminToken())
            .retrieve()
            .body(List.class);
    if (orgs == null || orgs.isEmpty()) {
      throw new IllegalStateException(
          "Keycloak returned 409 but no org found with alias: " + alias);
    }
    var org = (Map<String, Object>) orgs.getFirst();
    return (String) org.get("id");
  }

  /**
   * Invites a user to the organization by email. Keycloak 26.5 uses the {@code
   * /members/invite-user} endpoint with form-urlencoded body. After inviting, sets the org's {@code
   * creatorUserId} attribute so the gateway can identify the founding user.
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

  /**
   * Sets the creatorUserId attribute on the organization. The gateway uses this to determine which
   * member is the org owner (Keycloak org memberships don't carry roles by default).
   */
  public void setOrgCreator(String orgId, String email) {
    String userId = findUserIdByEmail(email);
    if (userId == null) {
      log.warn("Could not find Keycloak user for email {} to set as org creator", email);
      return;
    }
    restClient
        .put()
        .uri("/organizations/{orgId}", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("attributes", Map.of("creatorUserId", List.of(userId))))
        .retrieve()
        .toBodilessEntity();
    log.info("Set creatorUserId={} on org {}", userId, orgId);
  }

  @SuppressWarnings("unchecked")
  private String findUserIdByEmail(String email) {
    var users =
        restClient
            .get()
            .uri("/users?email={email}&exact=true", email)
            .header("Authorization", "Bearer " + getAdminToken())
            .retrieve()
            .body(List.class);
    if (users == null || users.isEmpty()) return null;
    var user = (Map<String, Object>) users.getFirst();
    return (String) user.get("id");
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
