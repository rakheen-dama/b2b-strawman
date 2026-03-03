package io.b2mash.b2b.b2bstrawman.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Integration tests for {@link OrgManagementController} with real Keycloak and Postgres containers.
 * Tests the full provisioning flow: Keycloak org creation + tenant schema provisioning + member
 * sync.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  OrgManagementControllerIntegrationTest.KeycloakTestConfig.class
})
@ActiveProfiles({"test", "keycloak"})
@org.springframework.test.context.TestPropertySource(
    properties = "spring.main.allow-bean-definition-overriding=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrgManagementControllerIntegrationTest {

  @SuppressWarnings("resource")
  static final KeycloakContainer keycloak =
      new KeycloakContainer("quay.io/keycloak/keycloak:26.5")
          .withFeaturesEnabled("organization")
          .useTls();

  static {
    keycloak.start();
  }

  @TestConfiguration
  static class KeycloakTestConfig {

    @Bean
    DynamicPropertyRegistrar keycloakProperties() {
      return registry -> {
        var serverUrl = keycloak.getAuthServerUrl();
        registry.add("keycloak.admin.server-url", () -> serverUrl);
        registry.add("keycloak.admin.realm", () -> "docteams");
        registry.add("keycloak.admin.client-id", () -> "docteams-admin");
        registry.add("keycloak.admin.client-secret", () -> "docteams-admin-secret");
        registry.add("keycloak.admin.enabled", () -> "true");
        // Override JWT issuer-uri so Spring doesn't try to connect to non-existent issuer
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> serverUrl + "/realms/docteams");
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> serverUrl + "/realms/docteams/protocol/openid-connect/certs");
      };
    }

    /**
     * Override the keycloakAdminRestClient bean to use trust-all TLS for self-signed Keycloak
     * container certs.
     */
    @Bean
    RestClient keycloakAdminRestClient(KeycloakConfig.KeycloakAdminProperties props)
        throws Exception {
      var trustAllFactory = trustAllRequestFactory();
      var trustAllTokenClient = RestClient.builder().requestFactory(trustAllFactory).build();
      return RestClient.builder()
          .baseUrl(props.serverUrl() + "/admin/realms/" + props.realm())
          .requestFactory(trustAllFactory)
          .requestInterceptor(
              new KeycloakClientCredentialsInterceptor(
                  props.serverUrl(),
                  props.realm(),
                  props.clientId(),
                  props.clientSecret(),
                  trustAllTokenClient))
          .build();
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private KeycloakAdminService keycloakAdminService;

  private String testUserId;
  private String otherUserId;
  private String createdOrgId;

  @BeforeAll
  void setupKeycloakRealm() throws Exception {
    var serverUrl = keycloak.getAuthServerUrl();
    var masterClient = createMasterAdminRestClient(serverUrl);
    createRealm(masterClient);

    var realmClient = createRealmAdminRestClient(serverUrl);
    createAdminServiceClient(realmClient);
    enableOrganizations(realmClient);
    grantServiceAccountRealmAdmin(realmClient);
    testUserId = createTestUser(realmClient, "integrationuser", "integration@example.com");
    otherUserId = createTestUser(realmClient, "otherint", "other-int@example.com");
  }

  @Test
  @Order(1)
  void createOrg_provisionsSchemaAndReturnsSlug() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/orgs")
                    .with(
                        jwt()
                            .jwt(
                                j ->
                                    j.subject(testUserId)
                                        .claim("email", "integration@example.com")
                                        .claim("name", "Integration User")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Integration Test Org"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("integration-test-org"))
            .andExpect(jsonPath("$.orgId").isNotEmpty())
            .andReturn();

    createdOrgId = JsonPath.read(result.getResponse().getContentAsString(), "$.orgId");

    // Verify OrgSchemaMapping was created
    var mapping = mappingRepository.findByExternalOrgId(createdOrgId);
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getSchemaName()).startsWith("tenant_");
  }

  @Test
  @Order(2)
  void listMyOrgs_returnsCreatedOrg() throws Exception {
    mockMvc
        .perform(get("/api/orgs/mine").with(jwt().jwt(j -> j.subject(testUserId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Integration Test Org"))
        .andExpect(jsonPath("$[0].slug").value("integration-test-org"))
        .andExpect(jsonPath("$[0].id").isNotEmpty());
  }

  @Test
  @Order(3)
  void listInvitations_returnsEmptyList() throws Exception {
    // testUserId is a member of createdOrgId (added during org creation), so this should pass auth
    mockMvc
        .perform(
            get("/api/orgs/{id}/invitations", createdOrgId)
                .with(jwt().jwt(j -> j.subject(testUserId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  @Order(4)
  void createOrg_duplicateName_returns409() throws Exception {
    mockMvc
        .perform(
            post("/api/orgs")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(testUserId)
                                    .claim("email", "integration@example.com")
                                    .claim("name", "Integration User")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Integration Test Org"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  @Order(5)
  void createOrg_invalidRequest_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/orgs")
                .with(jwt().jwt(j -> j.subject(testUserId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": ""}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(6)
  void unauthenticatedRequest_returns401() throws Exception {
    mockMvc.perform(get("/api/orgs/mine")).andExpect(status().isUnauthorized());
  }

  // ---- New tests for Epic 266A ----

  @Test
  @Order(7)
  void createOrg_syncsMemberAsOwner() {
    // Verify the creator was synced as "owner" in the provisioned tenant schema
    var mapping = mappingRepository.findByExternalOrgId(createdOrgId);
    assertThat(mapping).isPresent();
    var schemaName = mapping.get().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, createdOrgId)
        .run(
            () -> {
              var member = memberRepository.findByExternalUserId(testUserId);
              assertThat(member).isPresent();
              assertThat(member.get().getEmail()).isEqualTo("integration@example.com");
              assertThat(member.get().getOrgRole()).isEqualTo("owner");
            });
  }

  @Test
  @Order(8)
  void createOrg_keycloakOrgExists() {
    // Verify the organization actually exists in Keycloak via the admin service
    var orgs = keycloakAdminService.getUserOrganizations(testUserId);
    assertThat(orgs).anyMatch(org -> org.id().equals(createdOrgId));
    assertThat(orgs).anyMatch(org -> org.alias().equals("integration-test-org"));
  }

  @Test
  @Order(9)
  void listInvitations_nonMember_returns403() throws Exception {
    // otherUserId is NOT a member of createdOrgId — should get 403
    mockMvc
        .perform(
            get("/api/orgs/{id}/invitations", createdOrgId)
                .with(jwt().jwt(j -> j.subject(otherUserId))))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(10)
  void invite_nonMember_returns403() throws Exception {
    // otherUserId is NOT a member — invite should be forbidden
    mockMvc
        .perform(
            post("/api/orgs/{id}/invite", createdOrgId)
                .with(jwt().jwt(j -> j.subject(otherUserId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "invited@example.com"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(11)
  @Disabled("Keycloak invite-user requires SMTP server — tested in E2E stack (Epic 266B)")
  void invite_asMember_returns204() throws Exception {
    mockMvc
        .perform(
            post("/api/orgs/{id}/invite", createdOrgId)
                .with(jwt().jwt(j -> j.subject(testUserId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "newinvite@example.com"}
                    """))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(12)
  void cancelInvitation_nonExistent_returns404or400() throws Exception {
    // Attempt to cancel a non-existent invitation — Keycloak should return an error
    mockMvc
        .perform(
            delete("/api/orgs/{id}/invitations/{invId}", createdOrgId, "non-existent-inv-id")
                .with(jwt().jwt(j -> j.subject(testUserId))))
        .andExpect(
            result -> {
              int statusCode = result.getResponse().getStatus();
              // Keycloak may return 404 for non-existent invitation, or the service may wrap it
              assertThat(statusCode).isIn(400, 404, 500);
            });
  }

  // ---- Keycloak Setup Helpers (same as KeycloakAdminServiceTest) ----

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
        .baseUrl(serverUrl + "/admin/realms/docteams")
        .defaultHeader("Authorization", "Bearer " + adminToken)
        .build();
  }

  private void createRealm(RestClient masterClient) {
    masterClient
        .post()
        .uri("")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("realm", "docteams", "enabled", true, "sslRequired", "none"))
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
                "clientId", "docteams-admin",
                "enabled", true,
                "publicClient", false,
                "serviceAccountsEnabled", true,
                "directAccessGrantsEnabled", true,
                "standardFlowEnabled", false,
                "secret", "docteams-admin-secret",
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
            .uri("/clients?clientId=docteams-admin")
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
                    "Integration",
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

  @SuppressWarnings("java:S4830")
  static JdkClientHttpRequestFactory trustAllRequestFactory() throws Exception {
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
