package io.b2mash.b2b.b2bstrawman.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.KeycloakOrganization;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Integration tests for {@link KeycloakAdminService} using a real Keycloak container. This is a
 * standalone test (not @SpringBootTest) to avoid needing Postgres/S3 infrastructure.
 *
 * <p>The realm is set up programmatically to avoid the "Session not bound to a realm" bug in
 * Keycloak 26 when importing realms with service account clients and the organization feature
 * enabled. Uses TLS with trust-all certificates for the self-signed Keycloak container cert.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KeycloakAdminServiceTest {

  private static final String REALM = "docteams";
  private static final String ADMIN_CLIENT_ID = "docteams-admin";
  private static final String ADMIN_CLIENT_SECRET = "docteams-admin-secret";

  @SuppressWarnings("resource")
  static final KeycloakContainer keycloak =
      new KeycloakContainer("quay.io/keycloak/keycloak:26.5")
          .withFeaturesEnabled("organization")
          .useTls();

  KeycloakAdminService service;
  String createdOrgId;
  String testUserId;

  @BeforeAll
  void setup() throws Exception {
    keycloak.start();

    var serverUrl = keycloak.getAuthServerUrl();

    // Get master realm admin token
    var masterClient = createMasterAdminRestClient(serverUrl);

    // Set up realm, client, user, and organizations programmatically
    createRealm(masterClient);

    var realmClient = createRealmAdminRestClient(serverUrl);
    createAdminServiceClient(realmClient);
    enableOrganizations(realmClient);
    grantServiceAccountRealmAdmin(realmClient);
    testUserId = createTestUser(realmClient, "testuser", "testuser@example.com");

    // Build the service under test using service account credentials
    // Both the interceptor's token client and the main client need trust-all for self-signed certs
    var trustAllFactory = trustAllRequestFactory();
    var trustAllTokenClient = RestClient.builder().requestFactory(trustAllFactory).build();
    var restClient =
        RestClient.builder()
            .baseUrl(serverUrl + "/admin/realms/" + REALM)
            .requestFactory(trustAllFactory)
            .requestInterceptor(
                new KeycloakClientCredentialsInterceptor(
                    serverUrl, REALM, ADMIN_CLIENT_ID, ADMIN_CLIENT_SECRET, trustAllTokenClient))
            .build();

    service = new KeycloakAdminService(restClient);
  }

  @Test
  @Order(1)
  void createOrganization_succeeds() {
    var org = service.createOrganization("Test Org", "test-org");

    assertThat(org).isNotNull();
    assertThat(org.id()).isNotBlank();
    assertThat(org.name()).isEqualTo("Test Org");
    assertThat(org.alias()).isEqualTo("test-org");
    assertThat(org.enabled()).isTrue();

    createdOrgId = org.id();
  }

  @Test
  @Order(2)
  void createOrganization_conflictOnDuplicateAlias() {
    assertThatThrownBy(() -> service.createOrganization("Duplicate Org", "test-org"))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  @Order(3)
  void addMember_succeeds() {
    service.addMember(createdOrgId, testUserId);
  }

  @Test
  @Order(4)
  void addMember_conflictOnDuplicate() {
    assertThatThrownBy(() -> service.addMember(createdOrgId, testUserId))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  @Order(5)
  void getUserOrganizations_returnsOrg() {
    var orgs = service.getUserOrganizations(testUserId);

    assertThat(orgs).isNotEmpty();
    assertThat(orgs).extracting(KeycloakOrganization::alias).contains("test-org");
  }

  @Test
  @Order(6)
  void getUserOrganizations_emptyForNonMember() throws Exception {
    var realmClient = createRealmAdminRestClient(keycloak.getAuthServerUrl());
    var otherUserId = createTestUser(realmClient, "otheruser", "other@example.com");
    var orgs = service.getUserOrganizations(otherUserId);

    assertThat(orgs).isEmpty();
  }

  @Test
  @Order(7)
  @org.junit.jupiter.api.Disabled("Keycloak invite-user requires SMTP server — tested in e2e stack")
  void inviteToOrganization_succeeds() {
    service.inviteToOrganization(createdOrgId, "invited@example.com");
  }

  @Test
  @Order(8)
  void listInvitations_returnsEmptyWhenNoneExist() {
    var invitations = service.listInvitations(createdOrgId);
    assertThat(invitations).isEmpty();
  }

  @Test
  @Order(9)
  @org.junit.jupiter.api.Disabled(
      "Depends on invite creation which requires SMTP — tested in e2e stack")
  void cancelInvitation_succeeds() {
    var invitations = service.listInvitations(createdOrgId);
    var invitationId = invitations.getFirst().id();

    service.cancelInvitation(createdOrgId, invitationId);

    var remaining = service.listInvitations(createdOrgId);
    assertThat(remaining).noneMatch(inv -> inv.id().equals(invitationId));
  }

  @Test
  @Order(10)
  void deleteOrganization_succeeds() {
    service.deleteOrganization(createdOrgId);
  }

  @Test
  @Order(11)
  void deleteOrganization_notFoundForDeletedOrg() {
    assertThatThrownBy(() -> service.deleteOrganization(createdOrgId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // ---- Setup Helper Methods ----

  private String obtainMasterAdminToken(String serverUrl) throws Exception {
    var tokenClient = RestClient.builder().requestFactory(trustAllRequestFactory()).build();
    var formData = new LinkedMultiValueMap<String, String>();
    formData.add("grant_type", "password");
    formData.add("client_id", "admin-cli");
    formData.add("username", "admin");
    formData.add("password", "admin");

    var tokenResponse =
        tokenClient
            .post()
            .uri(serverUrl + "/realms/master/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(formData)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

    return (String) tokenResponse.get("access_token");
  }

  private RestClient createMasterAdminRestClient(String serverUrl) throws Exception {
    var adminToken = obtainMasterAdminToken(serverUrl);
    return RestClient.builder()
        .requestFactory(trustAllRequestFactory())
        .baseUrl(serverUrl + "/admin/realms")
        .defaultHeader("Authorization", "Bearer " + adminToken)
        .build();
  }

  private RestClient createRealmAdminRestClient(String serverUrl) throws Exception {
    var adminToken = obtainMasterAdminToken(serverUrl);
    return RestClient.builder()
        .requestFactory(trustAllRequestFactory())
        .baseUrl(serverUrl + "/admin/realms/" + REALM)
        .defaultHeader("Authorization", "Bearer " + adminToken)
        .build();
  }

  private void createRealm(RestClient masterClient) {
    masterClient
        .post()
        .uri("")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("realm", REALM, "enabled", true, "sslRequired", "none"))
        .retrieve()
        .toBodilessEntity();
  }

  private void createAdminServiceClient(RestClient realmClient) {
    realmClient
        .post()
        .uri("/clients")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "clientId", ADMIN_CLIENT_ID,
                "enabled", true,
                "publicClient", false,
                "serviceAccountsEnabled", true,
                "directAccessGrantsEnabled", true,
                "standardFlowEnabled", false,
                "secret", ADMIN_CLIENT_SECRET,
                "protocol", "openid-connect"))
        .retrieve()
        .toBodilessEntity();
  }

  private void enableOrganizations(RestClient realmClient) {
    realmClient
        .put()
        .uri("")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("organizationsEnabled", true))
        .retrieve()
        .toBodilessEntity();
  }

  private void grantServiceAccountRealmAdmin(RestClient realmClient) {
    var clients =
        realmClient
            .get()
            .uri("/clients?clientId=realm-management")
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    var realmMgmtClientId = (String) clients.getFirst().get("id");

    var realmAdminRole =
        realmClient
            .get()
            .uri("/clients/{clientId}/roles/realm-admin", realmMgmtClientId)
            .retrieve()
            .body(Map.class);

    var adminClients =
        realmClient
            .get()
            .uri("/clients?clientId=" + ADMIN_CLIENT_ID)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    var adminClientInternalId = (String) adminClients.getFirst().get("id");

    var serviceAccountUser =
        realmClient
            .get()
            .uri("/clients/{clientId}/service-account-user", adminClientInternalId)
            .retrieve()
            .body(Map.class);

    var serviceAccountUserId = (String) serviceAccountUser.get("id");

    realmClient
        .post()
        .uri(
            "/users/{userId}/role-mappings/clients/{clientId}",
            serviceAccountUserId,
            realmMgmtClientId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(List.of(realmAdminRole))
        .retrieve()
        .toBodilessEntity();
  }

  private String createTestUser(RestClient realmClient, String username, String email) {
    var response =
        realmClient
            .post()
            .uri("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "username",
                    username,
                    "email",
                    email,
                    "enabled",
                    true,
                    "firstName",
                    "Test",
                    "lastName",
                    "User"))
            .retrieve()
            .toBodilessEntity();

    var location = response.getHeaders().getLocation();
    if (location == null) {
      throw new IllegalStateException("Keycloak did not return Location header for created user");
    }
    var path = location.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  /** Creates an HttpRequestFactory that trusts all certificates (for self-signed test certs). */
  @SuppressWarnings("java:S4830") // Trusting all certificates is intentional for tests
  private static JdkClientHttpRequestFactory trustAllRequestFactory() throws Exception {
    var trustAllCerts =
        new TrustManager[] {
          new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
          }
        };

    var sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustAllCerts, new SecureRandom());

    var httpClient = HttpClient.newBuilder().sslContext(sslContext).build();
    return new JdkClientHttpRequestFactory(httpClient);
  }
}
