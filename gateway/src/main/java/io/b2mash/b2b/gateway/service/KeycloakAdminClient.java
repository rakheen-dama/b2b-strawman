package io.b2mash.b2b.gateway.service;

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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Client for the Keycloak Admin REST API. Uses the master realm admin credentials (password grant)
 * to authenticate. Caches the access token and refreshes it before expiry.
 */
@Service
public class KeycloakAdminClient {

  private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

  private final RestClient restClient;
  private final RestClient tokenRestClient;
  private final String tokenUrl;
  private final String adminUsername;
  private final String adminPassword;

  private volatile String cachedToken;
  private volatile Instant tokenExpiry = Instant.MIN;

  public KeycloakAdminClient(
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
    // Authenticate against the master realm (standard admin access pattern)
    this.tokenUrl = authServerUrl + "/realms/master/protocol/openid-connect/token";
    this.adminUsername = adminUsername;
    this.adminPassword = adminPassword;
  }

  /**
   * Creates an organization in Keycloak.
   *
   * @return the organization ID from the Location header, or null if not returned
   */
  public String createOrganization(String name, String alias) {
    var response =
        restClient
            .post()
            .uri("/organizations")
            .header("Authorization", "Bearer " + getAdminToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", name, "alias", alias, "enabled", true))
            .retrieve()
            .toBodilessEntity();
    // Extract org ID from Location header: .../organizations/{id}
    var location = response.getHeaders().getLocation();
    if (location != null) {
      String path = location.getPath();
      return path.substring(path.lastIndexOf('/') + 1);
    }
    return null;
  }

  /** Adds a user to an organization. Keycloak 26 expects the user ID as a raw JSON string. */
  public void addMember(String orgId, String userId) {
    restClient
        .post()
        .uri("/organizations/{orgId}/members", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body("\"" + userId + "\"")
        .retrieve()
        .toBodilessEntity();
  }

  /** Lists members of the organization. */
  public List<Map<String, Object>> listOrgMembers(String orgId) {
    return restClient
        .get()
        .uri("/organizations/{orgId}/members", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  /** Lists organizations in the realm. */
  public List<Map<String, Object>> listOrganizations() {
    return restClient
        .get()
        .uri("/organizations")
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  /** Invites a user to the organization by email with the given role. */
  public Map<String, Object> inviteMember(String orgId, String email, String role) {
    Map<String, Object> body = Map.of("email", email, "roles", List.of(role));
    return restClient
        .post()
        .uri("/organizations/{orgId}/invitations", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  /** Lists pending invitations for the organization. */
  public List<Map<String, Object>> listInvitations(String orgId) {
    return restClient
        .get()
        .uri("/organizations/{orgId}/invitations", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  /** Revokes (deletes) a pending invitation. */
  public void revokeInvitation(String orgId, String invitationId) {
    restClient
        .delete()
        .uri("/organizations/{orgId}/invitations/{invitationId}", orgId, invitationId)
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .toBodilessEntity();
  }

  /** Updates a member's role within the organization. */
  public void updateMemberRole(String orgId, String userId, String role) {
    restClient
        .put()
        .uri("/organizations/{orgId}/members/{userId}/roles", orgId, userId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(List.of(role))
        .retrieve()
        .toBodilessEntity();
  }

  /** Finds an organization by alias. Returns null if not found. */
  public Map<String, Object> findOrganizationByAlias(String alias) {
    List<Map<String, Object>> orgs =
        restClient
            .get()
            .uri("/organizations?search={alias}&exact=true", alias)
            .header("Authorization", "Bearer " + getAdminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    if (orgs != null && !orgs.isEmpty()) {
      return orgs.getFirst();
    }
    return null;
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
