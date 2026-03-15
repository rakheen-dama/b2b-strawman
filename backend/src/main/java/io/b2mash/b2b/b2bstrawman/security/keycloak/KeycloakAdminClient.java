package io.b2mash.b2b.b2bstrawman.security.keycloak;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 *
 * <p>This bean is only created when {@code keycloak.admin.auth-server-url} is configured,
 * preventing startup failures in dev/test environments without Keycloak.
 */
@Service
@ConditionalOnProperty(prefix = "keycloak.admin", name = "auth-server-url")
@EnableConfigurationProperties(KeycloakAdminConfig.class)
public class KeycloakAdminClient {

  private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

  private final RestClient restClient;
  private final RestClient tokenRestClient;
  private final String tokenUrl;
  private final String adminUsername;
  private final String adminPassword;

  private volatile String cachedToken;
  private volatile Instant tokenExpiry = Instant.MIN;
  private volatile boolean orgRoleProfileRegistered = false;

  public KeycloakAdminClient(KeycloakAdminConfig config) {
    var httpClient = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    this.restClient =
        RestClient.builder()
            .baseUrl(config.authServerUrl() + "/admin/realms/" + config.realm())
            .requestFactory(requestFactory)
            .defaultStatusHandler(
                HttpStatusCode::isError,
                (request, response) -> {
                  HttpStatusCode status = response.getStatusCode();
                  String body =
                      new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                  log.error("Keycloak Admin API error: {} — {}", status, body);
                  if (status.value() == 401 || status.value() == 403 || status.value() >= 500) {
                    throw new InvalidStateException(
                        "Keycloak unavailable", "Keycloak Admin API unavailable");
                  }
                  throw new InvalidStateException(
                      "Keycloak error", "Keycloak Admin API error: " + status.value());
                })
            .build();
    this.tokenRestClient = RestClient.builder().requestFactory(requestFactory).build();
    this.tokenUrl = config.authServerUrl() + "/realms/master/protocol/openid-connect/token";
    this.adminUsername = config.username();
    this.adminPassword = config.password();
  }

  public String createOrganization(String name, String alias, String creatorUserId) {
    var body =
        Map.of(
            "name",
            name,
            "alias",
            alias,
            "enabled",
            true,
            "attributes",
            Map.of("creatorUserId", List.of(creatorUserId)));
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
    if (location != null) {
      String path = location.getPath();
      return path.substring(path.lastIndexOf('/') + 1);
    }
    return null;
  }

  public void addMember(String orgId, String userId) {
    restClient
        .post()
        .uri("/organizations/{orgId}/members", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("id", userId))
        .retrieve()
        .toBodilessEntity();
  }

  public void deleteOrganization(String orgId) {
    restClient
        .delete()
        .uri("/organizations/{orgId}", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .toBodilessEntity();
  }

  public List<Map<String, Object>> listOrgMembers(String orgId) {
    return restClient
        .get()
        .uri("/organizations/{orgId}/members", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  public List<Map<String, Object>> listOrganizations() {
    return restClient
        .get()
        .uri("/organizations")
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  public Map<String, Object> getOrganization(String orgId) {
    return restClient
        .get()
        .uri("/organizations/{orgId}", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  public void inviteMember(String orgId, String email, String role, String redirectUrl) {
    String formBody = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
    if (redirectUrl != null && !redirectUrl.isBlank()) {
      formBody += "&redirectUrl=" + URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8);
    }
    restClient
        .post()
        .uri("/organizations/{orgId}/members/invite-user", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(formBody)
        .retrieve()
        .toBodilessEntity();
  }

  public List<Map<String, Object>> listInvitations(String orgId) {
    return restClient
        .get()
        .uri("/organizations/{orgId}/invitations", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  public void revokeInvitation(String orgId, String invitationId) {
    restClient
        .delete()
        .uri("/organizations/{orgId}/invitations/{invitationId}", orgId, invitationId)
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .toBodilessEntity();
  }

  public void updateMemberRole(String orgId, String userId, String role) {
    ensureUserProfileAttribute("org_role");
    setUserAttribute(userId, "org_role", role);
    try {
      Map<String, Object> orgRole = ensureOrgRole(orgId, role);
      restClient
          .post()
          .uri("/organizations/{orgId}/members/{userId}/organization-roles/grant", orgId, userId)
          .header("Authorization", "Bearer " + getAdminToken())
          .contentType(MediaType.APPLICATION_JSON)
          .body(List.of(orgRole))
          .retrieve()
          .toBodilessEntity();
    } catch (Exception e) {
      log.debug(
          "KC org role assignment failed (expected on KC 26.x): {} — user attribute fallback used",
          e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  public void ensureUserProfileAttribute(String attributeName) {
    if (orgRoleProfileRegistered) return;
    synchronized (this) {
      if (orgRoleProfileRegistered) return;
      registerProfileAttribute(attributeName);
      orgRoleProfileRegistered = true;
    }
  }

  @SuppressWarnings("unchecked")
  private void registerProfileAttribute(String attributeName) {
    Map<String, Object> profile =
        restClient
            .get()
            .uri("/users/profile")
            .header("Authorization", "Bearer " + getAdminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    if (profile == null) return;
    List<Map<String, Object>> attrs = (List<Map<String, Object>>) profile.get("attributes");
    boolean exists =
        attrs != null && attrs.stream().anyMatch(a -> attributeName.equals(a.get("name")));
    if (exists) return;
    if (attrs == null) attrs = new java.util.ArrayList<>();
    else attrs = new java.util.ArrayList<>(attrs);
    attrs.add(
        Map.of(
            "name",
            attributeName,
            "displayName",
            "Organization Role",
            "permissions",
            Map.of("view", List.of("admin"), "edit", List.of("admin")),
            "multivalued",
            false));
    profile = new java.util.HashMap<>(profile);
    profile.put("attributes", attrs);
    restClient
        .put()
        .uri("/users/profile")
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(profile)
        .retrieve()
        .toBodilessEntity();
    log.info("Registered '{}' in Keycloak user profile", attributeName);
  }

  @SuppressWarnings("unchecked")
  public void setUserAttribute(String userId, String attribute, String value) {
    Map<String, Object> user =
        restClient
            .get()
            .uri("/users/{userId}", userId)
            .header("Authorization", "Bearer " + getAdminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    if (user == null) {
      log.warn("Cannot set attribute — user {} not found", userId);
      return;
    }
    Map<String, Object> attributes =
        user.get("attributes") instanceof Map<?, ?>
            ? new java.util.HashMap<>((Map<String, Object>) user.get("attributes"))
            : new java.util.HashMap<>();
    attributes.put(attribute, List.of(value));
    var body = new java.util.HashMap<>(user);
    body.put("attributes", attributes);
    restClient
        .put()
        .uri("/users/{userId}", userId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> ensureOrgRole(String orgId, String roleName) {
    List<Map<String, Object>> roles =
        restClient
            .get()
            .uri("/organizations/{orgId}/roles", orgId)
            .header("Authorization", "Bearer " + getAdminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    if (roles != null) {
      for (Map<String, Object> r : roles) {
        if (roleName.equals(r.get("name"))) {
          return r;
        }
      }
    }
    restClient
        .post()
        .uri("/organizations/{orgId}/roles", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("name", roleName))
        .retrieve()
        .toBodilessEntity();
    List<Map<String, Object>> updatedRoles =
        restClient
            .get()
            .uri("/organizations/{orgId}/roles", orgId)
            .header("Authorization", "Bearer " + getAdminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    if (updatedRoles != null) {
      for (Map<String, Object> r : updatedRoles) {
        if (roleName.equals(r.get("name"))) {
          return r;
        }
      }
    }
    return Map.of("name", roleName);
  }

  public String resolveOrgId(String aliasOrId) {
    if (aliasOrId != null && aliasOrId.length() == 36 && aliasOrId.contains("-")) {
      return aliasOrId;
    }
    var org = findOrganizationByAlias(aliasOrId);
    if (org == null || org.get("id") == null) {
      throw new InvalidStateException(
          "Organization not found", "Organization not found for alias: " + aliasOrId);
    }
    return (String) org.get("id");
  }

  public Map<String, Object> findOrganizationByAlias(String alias) {
    List<Map<String, Object>> orgs =
        restClient
            .get()
            .uri("/organizations?q=alias:{alias}", alias)
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
