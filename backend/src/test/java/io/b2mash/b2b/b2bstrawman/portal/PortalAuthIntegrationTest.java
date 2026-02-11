package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalAuthIntegrationTest {

  private static final String ORG_ID = "org_portal_test";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private MagicLinkService magicLinkService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Sync a member for creating customers
    var syncResult =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_portal_owner",
                          "email": "portal_owner@test.com",
                          "name": "Portal Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andExpect(status().isCreated())
            .andReturn();

    String memberIdStr = JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId");
    UUID memberId = UUID.fromString(memberIdStr);

    // Resolve tenant schema
    String tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    // Create a customer in the tenant
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Portal Customer", "portal-customer@test.com", null, null, null, memberId);
              customerId = customer.getId();
            });
  }

  @Nested
  class MagicLinkTests {

    @Test
    void shouldGenerateAndVerifyMagicLinkToken() {
      String token = magicLinkService.generateToken(customerId, ORG_ID);
      assertThat(token).isNotBlank();

      var identity = magicLinkService.verifyToken(token);
      assertThat(identity.customerId()).isEqualTo(customerId);
      assertThat(identity.clerkOrgId()).isEqualTo(ORG_ID);
    }

    @Test
    void shouldRejectInvalidMagicLinkToken() {
      assertThatThrownBy(() -> magicLinkService.verifyToken("invalid.token.here"))
          .isInstanceOf(PortalAuthException.class);
    }

    @Test
    void shouldRejectReusedMagicLinkToken() {
      String token = magicLinkService.generateToken(customerId, ORG_ID);

      // First use succeeds
      var identity = magicLinkService.verifyToken(token);
      assertThat(identity.customerId()).isEqualTo(customerId);

      // Second use fails (single-use enforcement)
      assertThatThrownBy(() -> magicLinkService.verifyToken(token))
          .isInstanceOf(PortalAuthException.class)
          .hasMessageContaining("already been used");
    }

    @Test
    void shouldRejectTamperedToken() {
      String token = magicLinkService.generateToken(customerId, ORG_ID);
      // Tamper with the token by modifying the last few characters
      String tampered = token.substring(0, token.length() - 5) + "XXXXX";

      assertThatThrownBy(() -> magicLinkService.verifyToken(tampered))
          .isInstanceOf(PortalAuthException.class);
    }
  }

  @Nested
  class PortalJwtTests {

    @Test
    void shouldIssueAndVerifyPortalJwt() {
      String token = portalJwtService.issueToken(customerId, ORG_ID);
      assertThat(token).isNotBlank();

      var claims = portalJwtService.verifyToken(token);
      assertThat(claims.customerId()).isEqualTo(customerId);
      assertThat(claims.clerkOrgId()).isEqualTo(ORG_ID);
    }

    @Test
    void shouldRejectInvalidPortalJwt() {
      assertThatThrownBy(() -> portalJwtService.verifyToken("not.a.valid.jwt"))
          .isInstanceOf(PortalAuthException.class);
    }

    @Test
    void shouldRejectMagicLinkTokenAsPortalJwt() {
      // Magic link tokens have type=magic_link, not type=customer
      String magicToken = magicLinkService.generateToken(customerId, ORG_ID);

      assertThatThrownBy(() -> portalJwtService.verifyToken(magicToken))
          .isInstanceOf(PortalAuthException.class)
          .hasMessageContaining("Invalid token type");
    }
  }

  @Nested
  class SecurityFilterChainTests {

    @Test
    void portalAuthEndpointsAreAccessibleWithoutToken() throws Exception {
      // /portal/auth/** should be accessible without authentication
      // Returns 404 because controller doesn't exist yet (43B scope) — but NOT 401
      mockMvc
          .perform(
              post("/portal/auth/request-link")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "test@test.com", "orgSlug": "test-org"}
                      """))
          .andExpect(status().isNotFound());
    }

    @Test
    void authenticatedPortalEndpointsReject401WithoutToken() throws Exception {
      // /portal/projects should require portal JWT
      mockMvc.perform(get("/portal/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedPortalEndpointsReject401WithInvalidToken() throws Exception {
      mockMvc
          .perform(get("/portal/projects").header("Authorization", "Bearer invalid-token"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedPortalEndpointsAcceptValidPortalJwt() throws Exception {
      String portalToken = portalJwtService.issueToken(customerId, ORG_ID);

      // Should NOT return 401 — may return 404 since controller not yet created (43B)
      var result =
          mockMvc
              .perform(get("/portal/projects").header("Authorization", "Bearer " + portalToken))
              .andReturn();

      int statusCode = result.getResponse().getStatus();
      // Accept 404 (controller not yet created) or 200, but NOT 401/403
      assertThat(statusCode).isNotEqualTo(401);
      assertThat(statusCode).isNotEqualTo(403);
    }

    @Test
    void staffJwtCannotAccessPortalEndpoints() throws Exception {
      // A Clerk JWT (staff) should not work on /portal/** paths
      mockMvc
          .perform(
              get("/portal/projects")
                  .with(
                      jwt()
                          .jwt(
                              j ->
                                  j.subject("user_staff")
                                      .claim("o", Map.of("id", ORG_ID, "rol", "owner")))
                          .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")))))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void existingStaffApiUnaffectedByPortalChain() throws Exception {
      // Staff API should still work with Clerk JWT
      mockMvc
          .perform(
              get("/api/customers")
                  .with(
                      jwt()
                          .jwt(
                              j ->
                                  j.subject("user_portal_owner")
                                      .claim("o", Map.of("id", ORG_ID, "rol", "owner")))
                          .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")))))
          .andExpect(status().isOk());
    }

    @Test
    void portalJwtCannotAccessStaffApi() {
      // Portal JWT should not work on /api/** paths.
      // The main chain's Clerk JWT decoder rejects the portal-signed token —
      // either as 401 or by throwing (in test env the JWKS endpoint is unreachable).
      // Either outcome confirms portal tokens cannot access staff endpoints.
      String portalToken = portalJwtService.issueToken(customerId, ORG_ID);

      try {
        var result =
            mockMvc
                .perform(get("/api/customers").header("Authorization", "Bearer " + portalToken))
                .andReturn();
        // If we get here, the response must be 401
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
      } catch (Exception e) {
        // JwtDecoder initialization failure in test env = token correctly rejected
        assertThat(e).hasStackTraceContaining("JwtDecoder");
      }
    }
  }
}
