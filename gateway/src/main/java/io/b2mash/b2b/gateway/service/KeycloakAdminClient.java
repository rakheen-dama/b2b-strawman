package io.b2mash.b2b.gateway.service;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * Client for the Keycloak Admin REST API. Uses a service account (client_credentials grant) to
 * authenticate. Caches the access token and refreshes it before expiry.
 */
@Service
public class KeycloakAdminClient {

  private final RestClient restClient;
  private final RestClient tokenRestClient;
  private final String tokenUrl;
  private final String clientId;
  private final String clientSecret;

  private volatile String cachedToken;
  private volatile Instant tokenExpiry = Instant.MIN;

  public KeycloakAdminClient(
      @Value("${keycloak.admin.url}") String adminUrl,
      @Value("${keycloak.admin.realm}") String realm,
      @Value("${keycloak.admin.client-id}") String clientId,
      @Value("${keycloak.admin.client-secret}") String clientSecret) {
    var httpClient = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    this.restClient =
        RestClient.builder()
            .baseUrl(adminUrl + "/admin/realms/" + realm)
            .requestFactory(requestFactory)
            .defaultStatusHandler(
                HttpStatusCode::isError,
                (request, response) -> {
                  HttpStatusCode status = response.getStatusCode();
                  if (status.value() == 401 || status.value() == 403 || status.value() >= 500) {
                    // Service account auth failure or Keycloak internal error -> 502 Bad Gateway
                    throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Keycloak Admin API unavailable");
                  }
                  // 4xx client errors (400, 404, 409) are meaningful — pass through
                  throw new ResponseStatusException(
                      status, "Keycloak Admin API error: " + status.value());
                })
            .build();
    this.tokenRestClient = RestClient.builder().requestFactory(requestFactory).build();
    this.tokenUrl = adminUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  /** Invites a user to the organization by email with the given role. */
  public Map<String, Object> inviteMember(String orgId, String email, String role) {
    Map<String, Object> body = Map.of("email", email, "roles", List.of(role));
    return restClient
        .post()
        .uri("/orgs/{orgId}/invitations", orgId)
        .header("Authorization", "Bearer " + getServiceAccountToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  /** Lists pending invitations for the organization. */
  public List<Map<String, Object>> listInvitations(String orgId) {
    return restClient
        .get()
        .uri("/orgs/{orgId}/invitations", orgId)
        .header("Authorization", "Bearer " + getServiceAccountToken())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  /** Revokes (deletes) a pending invitation. */
  public void revokeInvitation(String orgId, String invitationId) {
    restClient
        .delete()
        .uri("/orgs/{orgId}/invitations/{invitationId}", orgId, invitationId)
        .header("Authorization", "Bearer " + getServiceAccountToken())
        .retrieve()
        .toBodilessEntity();
  }

  /** Lists members of the organization. */
  public List<Map<String, Object>> listOrgMembers(String orgId) {
    return restClient
        .get()
        .uri("/orgs/{orgId}/members", orgId)
        .header("Authorization", "Bearer " + getServiceAccountToken())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  /** Updates a member's role within the organization. */
  public void updateMemberRole(String orgId, String userId, String role) {
    restClient
        .put()
        .uri("/orgs/{orgId}/members/{userId}/roles", orgId, userId)
        .header("Authorization", "Bearer " + getServiceAccountToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(List.of(role))
        .retrieve()
        .toBodilessEntity();
  }

  @SuppressWarnings("unchecked")
  private synchronized String getServiceAccountToken() {
    if (Instant.now().isBefore(tokenExpiry)) {
      return cachedToken;
    }
    String formBody =
        "grant_type=client_credentials&client_id="
            + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&client_secret="
            + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
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
