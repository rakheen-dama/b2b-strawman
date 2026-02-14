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
  @Autowired private PortalContactService portalContactService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private UUID customerId;
  private String tenantSchema;
  // Separate contacts per test to avoid rate-limit interference
  private UUID contactIdGenVerify;
  private UUID contactIdInvalid;
  private UUID contactIdReuse;
  private UUID contactIdTamper;

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
    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    // Create a customer and portal contacts in the tenant
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Portal Customer",
                      "portal-customer@test.com",
                      null,
                      null,
                      null,
                      null,
                      memberId);
              customerId = customer.getId();

              contactIdGenVerify =
                  portalContactService
                      .createContact(
                          ORG_ID,
                          customerId,
                          "portal-genverify@test.com",
                          "Gen Verify Contact",
                          PortalContact.ContactRole.PRIMARY)
                      .getId();

              contactIdInvalid =
                  portalContactService
                      .createContact(
                          ORG_ID,
                          customerId,
                          "portal-invalid@test.com",
                          "Invalid Contact",
                          PortalContact.ContactRole.GENERAL)
                      .getId();

              contactIdReuse =
                  portalContactService
                      .createContact(
                          ORG_ID,
                          customerId,
                          "portal-reuse@test.com",
                          "Reuse Contact",
                          PortalContact.ContactRole.GENERAL)
                      .getId();

              contactIdTamper =
                  portalContactService
                      .createContact(
                          ORG_ID,
                          customerId,
                          "portal-tamper@test.com",
                          "Tamper Contact",
                          PortalContact.ContactRole.GENERAL)
                      .getId();
            });
  }

  @Nested
  class MagicLinkTests {

    @Test
    void shouldGenerateAndVerifyMagicLinkToken() {
      String rawToken =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
              .call(() -> magicLinkService.generateToken(contactIdGenVerify, "127.0.0.1"));

      UUID resultContactId =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
              .call(() -> magicLinkService.verifyAndConsumeToken(rawToken));

      assertThat(resultContactId).isEqualTo(contactIdGenVerify);
    }

    @Test
    void shouldRejectInvalidMagicLinkToken() {
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .run(
              () ->
                  assertThatThrownBy(() -> magicLinkService.verifyAndConsumeToken("invalid-token"))
                      .isInstanceOf(PortalAuthException.class));
    }

    @Test
    void shouldRejectReusedMagicLinkToken() {
      String rawToken =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
              .call(() -> magicLinkService.generateToken(contactIdReuse, "127.0.0.1"));

      // First use succeeds
      UUID resultContactId =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
              .call(() -> magicLinkService.verifyAndConsumeToken(rawToken));
      assertThat(resultContactId).isEqualTo(contactIdReuse);

      // Second use fails (single-use enforcement)
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .run(
              () ->
                  assertThatThrownBy(() -> magicLinkService.verifyAndConsumeToken(rawToken))
                      .isInstanceOf(PortalAuthException.class)
                      .hasMessageContaining("already been used"));
    }

    @Test
    void shouldRejectTamperedToken() {
      String rawToken =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
              .call(() -> magicLinkService.generateToken(contactIdTamper, "127.0.0.1"));

      // Tamper with the token
      String tampered = rawToken.substring(0, rawToken.length() - 5) + "XXXXX";

      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .run(
              () ->
                  assertThatThrownBy(() -> magicLinkService.verifyAndConsumeToken(tampered))
                      .isInstanceOf(PortalAuthException.class));
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
  }

  @Nested
  class SecurityFilterChainTests {

    @Test
    void portalAuthEndpointsAreAccessibleWithoutToken() throws Exception {
      var result =
          mockMvc
              .perform(
                  post("/portal/auth/request-link")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"email": "test@test.com", "orgId": "org_nonexistent"}
                          """))
              .andReturn();

      int statusCode = result.getResponse().getStatus();
      // With anti-enumeration, unknown org returns 200 with generic message.
      assertThat(statusCode).isNotEqualTo(403);
    }

    @Test
    void authenticatedPortalEndpointsReject401WithoutToken() throws Exception {
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

      var result =
          mockMvc
              .perform(get("/portal/projects").header("Authorization", "Bearer " + portalToken))
              .andReturn();

      int statusCode = result.getResponse().getStatus();
      assertThat(statusCode).isNotEqualTo(401);
      assertThat(statusCode).isNotEqualTo(403);
    }

    @Test
    void staffJwtCannotAccessPortalEndpoints() throws Exception {
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
      String portalToken = portalJwtService.issueToken(customerId, ORG_ID);

      try {
        var result =
            mockMvc
                .perform(get("/api/customers").header("Authorization", "Bearer " + portalToken))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
      } catch (Exception e) {
        assertThat(e).hasStackTraceContaining("JwtDecoder");
      }
    }
  }
}
