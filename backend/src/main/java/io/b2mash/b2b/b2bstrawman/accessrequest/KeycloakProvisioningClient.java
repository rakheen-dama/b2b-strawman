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
  private final String organizationRedirectUrl;

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
    this.organizationRedirectUrl =
        frontendBaseUrl.replaceAll("/+$", "") + "/accept-invite/complete";
  }

  /**
   * Creates an organization in Keycloak, or returns the existing org ID if one with the same
   * name/alias already exists (409 Conflict).
   *
   * @return the organization ID
   */
  @SuppressWarnings("unchecked")
  public String createOrganization(String name, String slug) {
    // redirectUrl targets /accept-invite/complete so post-registration bounces back through
    // gateway-bff's OAuth2 login flow (the account-client auth code is intentionally discarded).
    // This lets the gateway's OAuth2 success handler fire and set KC_LAST_LOGIN_SUB for the L-22
    // middleware handoff check. See qa_cycle/fix-specs/GAP-L-22-regression.md.
    var body =
        Map.of(
            "name", name, "alias", slug, "enabled", true, "redirectUrl", organizationRedirectUrl);
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
      // Org already exists — look it up by alias and update its redirectUrl so legacy orgs
      // (created before the L-22 fix) get the corrected /accept-invite/complete bounce target.
      // Without this, any pre-existing org keeps the stale /dashboard redirect and the L-22
      // regression persists for that org. See qa_cycle/fix-specs/GAP-L-22-regression.md.
      log.info("Keycloak org with alias '{}' already exists, looking up ID", slug);
      String existingId = findOrganizationByAlias(slug);
      updateOrganizationRedirectUrl(existingId, name, slug);
      return existingId;
    }
  }

  /**
   * Updates the {@code redirectUrl} on an existing Keycloak organization. Used by the 409
   * idempotent retry path in {@link #createOrganization} so orgs provisioned before the L-22 fix
   * get the current bounce-page target rather than a stale {@code /dashboard} redirect.
   *
   * <p>Keycloak's organization update endpoint is {@code PUT
   * /admin/realms/{realm}/organizations/{orgId}} with the organization representation in the body.
   * We include {@code name}, {@code alias}, {@code enabled} alongside {@code redirectUrl} to keep
   * the representation consistent with what {@link #createOrganization} initially POSTed; KC merges
   * these fields into the stored org.
   */
  private void updateOrganizationRedirectUrl(String orgId, String name, String slug) {
    var body =
        Map.of(
            "name", name, "alias", slug, "enabled", true, "redirectUrl", organizationRedirectUrl);
    restClient
        .put()
        .uri("/organizations/{orgId}", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
    log.info("Updated redirectUrl on existing Keycloak org {} (alias '{}')", orgId, slug);
  }

  /**
   * Looks up an organization by alias. Keycloak's {@code /organizations?search=...} query matches
   * on organization <b>name</b>, not alias, so we must fetch the candidate list and filter
   * client-side on the {@code alias} field. See GAP-D0-01.
   */
  @SuppressWarnings("unchecked")
  private String findOrganizationByAlias(String alias) {
    // First attempt: narrow by search (matches name, but may coincidentally contain the alias).
    List<Map<String, Object>> orgs =
        (List<Map<String, Object>>)
            restClient
                .get()
                .uri("/organizations?search={alias}", alias)
                .header("Authorization", "Bearer " + getAdminToken())
                .retrieve()
                .body(List.class);
    String id = matchByAlias(orgs, alias);
    if (id != null) {
      return id;
    }

    // Fallback: list all orgs (up to 200) and match client-side. This path is only exercised in
    // anomaly/idempotency scenarios, so the extra call is acceptable.
    log.info(
        "findOrganizationByAlias: narrow search did not find alias '{}', falling back to list-all",
        alias);
    orgs =
        (List<Map<String, Object>>)
            restClient
                .get()
                .uri("/organizations?first=0&max=200")
                .header("Authorization", "Bearer " + getAdminToken())
                .retrieve()
                .body(List.class);
    id = matchByAlias(orgs, alias);
    if (id != null) {
      return id;
    }

    throw new IllegalStateException("Keycloak returned 409 but no org found with alias: " + alias);
  }

  private static String matchByAlias(List<Map<String, Object>> orgs, String alias) {
    if (orgs == null || orgs.isEmpty()) {
      return null;
    }
    for (Map<String, Object> org : orgs) {
      if (alias.equals(org.get("alias"))) {
        return (String) org.get("id");
      }
    }
    return null;
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
   * Sets a temporary password on a Keycloak user identified by email. Used during local development
   * so that users created via the access-request approval flow can log in immediately without
   * completing the email-based registration. The password is non-temporary (no forced reset).
   *
   * @param email the user's email address
   * @param password the password to set
   */
  public void setUserPassword(String email, String password) {
    String userId = findUserIdByEmail(email);
    if (userId == null) {
      log.warn("Cannot set password — no Keycloak user found for email {}", email);
      return;
    }
    restClient
        .put()
        .uri("/users/{userId}/reset-password", userId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("type", "password", "value", password, "temporary", false))
        .retrieve()
        .toBodilessEntity();
    log.info("Set default password for user {} ({})", userId, email);
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
