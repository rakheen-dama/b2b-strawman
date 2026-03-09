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
  private volatile boolean orgRoleProfileRegistered = false;

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
   * Creates an organization in Keycloak with the creator's user ID stored as an attribute.
   *
   * @return the organization ID from the Location header, or null if not returned
   */
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

  /** Fetches a single organization by ID, including its attributes. */
  public Map<String, Object> getOrganization(String orgId) {
    return restClient
        .get()
        .uri("/organizations/{orgId}", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  /**
   * Invites a user to the organization by email. Keycloak 26.5 uses the {@code
   * /members/invite-user} endpoint with form-urlencoded body. The {@code redirectUrl} tells
   * Keycloak where to send the user after they accept the invitation and complete registration.
   */
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

  /**
   * Updates a member's organization role. Sets the {@code org_role} user attribute (used by the JWT
   * protocol mapper) and attempts to assign the Keycloak org role (may fail on KC 26.x).
   */
  public void updateMemberRole(String orgId, String userId, String role) {
    // Always set the user attribute — this is the reliable path for KC 26.x.
    // The oidc-usermodel-attribute-mapper maps this to the org_role JWT claim.
    // Ensure the attribute is registered in the user profile first (idempotent).
    ensureUserProfileAttribute("org_role");
    setUserAttribute(userId, "org_role", role);

    // Also attempt the native org role API (may 404 on KC 26.x — that's OK)
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

  /**
   * Ensures a custom attribute is registered in the realm's user profile. Keycloak 26.x strict
   * profile silently strips unregistered attributes. Must be called once before setting the
   * attribute on any user. Idempotent — skips if the attribute already exists.
   */
  @SuppressWarnings("unchecked")
  public void ensureUserProfileAttribute(String attributeName) {
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

  /**
   * Sets a single-valued user attribute in Keycloak. Fetches the full user representation first,
   * merges the attribute, and PUTs the entire object back. KC 26.x strict user profile blanks any
   * fields omitted from the PUT body.
   */
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

    // Merge the new attribute into existing attributes
    Map<String, Object> attributes =
        user.get("attributes") instanceof Map<?, ?>
            ? new java.util.HashMap<>((Map<String, Object>) user.get("attributes"))
            : new java.util.HashMap<>();
    attributes.put(attribute, List.of(value));

    // Send the full user representation to avoid blanking required fields
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

  /**
   * Ensures an organization role exists. Creates it if not found. Returns the role representation
   * (with id and name) needed for granting.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> ensureOrgRole(String orgId, String roleName) {
    // Try to find existing role
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

    // Role doesn't exist — create it
    var response =
        restClient
            .post()
            .uri("/organizations/{orgId}/roles", orgId)
            .header("Authorization", "Bearer " + getAdminToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", roleName))
            .retrieve()
            .toBodilessEntity();

    // Re-fetch to get the full representation with ID
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

    // Fallback: return minimal representation
    return Map.of("name", roleName);
  }

  /**
   * Resolves an org identifier (alias or UUID) to a Keycloak org UUID. If the identifier is already
   * a UUID, it is returned as-is. Otherwise, looks up the organization by alias.
   *
   * @throws ResponseStatusException (404) if the alias is not found
   */
  public String resolveOrgId(String aliasOrId) {
    // UUIDs are 36 chars with hyphens — aliases are shorter slugs
    if (aliasOrId != null && aliasOrId.length() == 36 && aliasOrId.contains("-")) {
      return aliasOrId;
    }
    var org = findOrganizationByAlias(aliasOrId);
    if (org == null || org.get("id") == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Organization not found for alias: " + aliasOrId);
    }
    return (String) org.get("id");
  }

  /** Finds an organization by alias. Returns null if not found. */
  public Map<String, Object> findOrganizationByAlias(String alias) {
    // Keycloak 26: "search" matches by name, "q=alias:<value>" matches by alias
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
