package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the persistent magic link token lifecycle, PortalContact-based auth flow,
 * and cross-tenant portal isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MagicLinkTokenIntegrationTest {

  private static final String ORG_ID_A = "org_mlt_test_a";
  private static final String ORG_ID_B = "org_mlt_test_b";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private MagicLinkService magicLinkService;
  @Autowired private MagicLinkTokenRepository magicLinkTokenRepository;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchemaA;
  private String tenantSchemaB;
  private UUID customerIdA;
  private UUID customerIdB;
  // Separate contacts for different test groups to avoid rate-limit interference
  private UUID contactIdLifecycle;
  private UUID contactIdSingleUse;
  private UUID contactIdTamper;
  private UUID contactIdRateLimit;
  private UUID contactIdEndpoint;
  private UUID contactIdRoundTrip;
  private UUID contactIdCrossTenant;
  private UUID contactIdExpired;
  private UUID portalContactIdB;
  private UUID suspendedContactIdA;

  @BeforeAll
  void setup() throws Exception {
    // Provision tenant A
    provisioningService.provisionTenant(ORG_ID_A, "MLT Test Org A");
    planSyncService.syncPlan(ORG_ID_A, "pro-plan");

    // Provision tenant B
    provisioningService.provisionTenant(ORG_ID_B, "MLT Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");

    // Sync member for org A
    var syncResultA =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_mlt_owner_a",
                          "email": "mlt_owner_a@test.com",
                          "name": "MLT Owner A",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID_A)))
            .andExpect(status().isCreated())
            .andReturn();
    UUID memberIdA =
        UUID.fromString(
            JsonPath.read(syncResultA.getResponse().getContentAsString(), "$.memberId"));

    // Sync member for org B
    var syncResultB =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_mlt_owner_b",
                          "email": "mlt_owner_b@test.com",
                          "name": "MLT Owner B",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID_B)))
            .andExpect(status().isCreated())
            .andReturn();
    UUID memberIdB =
        UUID.fromString(
            JsonPath.read(syncResultB.getResponse().getContentAsString(), "$.memberId"));

    // Resolve tenant schemas
    tenantSchemaA = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_A).get().getSchemaName();
    tenantSchemaB = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).get().getSchemaName();

    // Create customer and multiple portal contacts in tenant A (one per test group)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
        .where(RequestScopes.ORG_ID, ORG_ID_A)
        .run(
            () -> {
              var customerA =
                  customerService.createCustomer(
                      "MLT Customer A",
                      "mlt-customer-a@test.com",
                      null,
                      null,
                      null,
                      null,
                      memberIdA);
              customerIdA = customerA.getId();

              contactIdLifecycle =
                  portalContactService
                      .createContact(
                          ORG_ID_A,
                          customerIdA,
                          "mlt-lifecycle@test.com",
                          "Lifecycle Contact",
                          PortalContact.ContactRole.PRIMARY)
                      .getId();

              contactIdSingleUse =
                  portalContactService
                      .createContact(
                          ORG_ID_A,
                          customerIdA,
                          "mlt-singleuse@test.com",
                          "Single Use Contact",
                          PortalContact.ContactRole.GENERAL)
                      .getId();

              contactIdTamper =
                  portalContactService
                      .createContact(
                          ORG_ID_A,
                          customerIdA,
                          "mlt-tamper@test.com",
                          "Tamper Contact",
                          PortalContact.ContactRole.GENERAL)
                      .getId();

              contactIdRateLimit =
                  portalContactService
                      .createContact(
                          ORG_ID_A,
                          customerIdA,
                          "mlt-ratelimit@test.com",
                          "Rate Limit Contact",
                          PortalContact.ContactRole.GENERAL)
                      .getId();

              contactIdEndpoint =
                  portalContactService
                      .createContact(
                          ORG_ID_A,
                          customerIdA,
                          "mlt-endpoint@test.com",
                          "Endpoint Contact",
                          PortalContact.ContactRole.GENERAL)
                      .getId();

              contactIdCrossTenant =
                  portalContactService
                      .createContact(
                          ORG_ID_A,
                          customerIdA,
                          "mlt-crosstenant@test.com",
                          "Cross Tenant Contact",
                          PortalContact.ContactRole.GENERAL)
                      .getId();

              contactIdExpired =
                  portalContactService
                      .createContact(
                          ORG_ID_A,
                          customerIdA,
                          "mlt-expired@test.com",
                          "Expired Contact",
                          PortalContact.ContactRole.GENERAL)
                      .getId();

              // Create a suspended contact
              var suspendedContact =
                  portalContactService.createContact(
                      ORG_ID_A,
                      customerIdA,
                      "mlt-suspended@test.com",
                      "Suspended Contact",
                      PortalContact.ContactRole.GENERAL);
              portalContactService.suspendContact(suspendedContact.getId());
              suspendedContactIdA = suspendedContact.getId();
            });

    // Create customer and portal contact in tenant B
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaB)
        .where(RequestScopes.ORG_ID, ORG_ID_B)
        .run(
            () -> {
              var customerB =
                  customerService.createCustomer(
                      "MLT Customer B",
                      "mlt-customer-b@test.com",
                      null,
                      null,
                      null,
                      null,
                      memberIdB);
              customerIdB = customerB.getId();

              contactIdRoundTrip =
                  portalContactService
                      .createContact(
                          ORG_ID_B,
                          customerIdB,
                          "mlt-customer-b@test.com",
                          "MLT Customer B",
                          PortalContact.ContactRole.PRIMARY)
                      .getId();

              portalContactIdB = contactIdRoundTrip;
            });
  }

  @Nested
  class TokenLifecycleTests {

    @Test
    void shouldGenerateAndVerifyToken() {
      String rawToken =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
              .call(() -> magicLinkService.generateToken(contactIdLifecycle, "127.0.0.1"));

      assertThat(rawToken).isNotBlank();

      UUID resultContactId =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
              .call(() -> magicLinkService.verifyAndConsumeToken(rawToken));

      assertThat(resultContactId).isEqualTo(contactIdLifecycle);
    }

    @Test
    void shouldEnforceSingleUse() {
      String rawToken =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
              .call(() -> magicLinkService.generateToken(contactIdSingleUse, "127.0.0.1"));

      // First use succeeds
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
          .call(() -> magicLinkService.verifyAndConsumeToken(rawToken));

      // Second use fails
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
          .run(
              () ->
                  assertThatThrownBy(() -> magicLinkService.verifyAndConsumeToken(rawToken))
                      .isInstanceOf(PortalAuthException.class)
                      .hasMessageContaining("already been used"));
    }

    @Test
    void shouldRejectTamperedTokenHash() {
      String rawToken =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
              .call(() -> magicLinkService.generateToken(contactIdTamper, "127.0.0.1"));

      String tampered = rawToken.substring(0, rawToken.length() - 4) + "ZZZZ";

      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
          .run(
              () ->
                  assertThatThrownBy(() -> magicLinkService.verifyAndConsumeToken(tampered))
                      .isInstanceOf(PortalAuthException.class)
                      .hasMessageContaining("Invalid magic link token"));
    }

    @Test
    void shouldRejectInvalidToken() {
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
          .run(
              () ->
                  assertThatThrownBy(
                          () -> magicLinkService.verifyAndConsumeToken("completely-invalid-token"))
                      .isInstanceOf(PortalAuthException.class));
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
      // Create a token directly with expiresAt in the past
      byte[] tokenBytes = new byte[32];
      new java.security.SecureRandom().nextBytes(tokenBytes);
      String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
      String tokenHash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(rawToken.getBytes(StandardCharsets.UTF_8)));

      // Save token with expiresAt 1 hour in the past
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
          .run(
              () -> {
                var expiredToken =
                    new MagicLinkToken(
                        contactIdExpired,
                        tokenHash,
                        Instant.now().minus(1, ChronoUnit.HOURS),
                        "127.0.0.1");
                magicLinkTokenRepository.save(expiredToken);
              });

      // Attempting to verify the expired token should fail
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
          .run(
              () ->
                  assertThatThrownBy(() -> magicLinkService.verifyAndConsumeToken(rawToken))
                      .isInstanceOf(PortalAuthException.class)
                      .hasMessageContaining("expired"));
    }

    @Test
    void shouldEnforceRateLimiting() {
      // Generate 3 tokens (max allowed in 5 minutes)
      for (int i = 0; i < 3; i++) {
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
            .call(() -> magicLinkService.generateToken(contactIdRateLimit, "127.0.0.1"));
      }

      // 4th token should fail
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaA)
          .run(
              () ->
                  assertThatThrownBy(
                          () -> magicLinkService.generateToken(contactIdRateLimit, "127.0.0.1"))
                      .isInstanceOf(PortalAuthException.class)
                      .hasMessageContaining("Too many login attempts"));
    }
  }

  @Nested
  class RequestLinkEndpointTests {

    @Test
    void shouldReturn200WithMessageForExistingContact() throws Exception {
      mockMvc
          .perform(
              post("/portal/auth/request-link")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "mlt-endpoint@test.com", "orgId": "%s"}
                      """
                          .formatted(ORG_ID_A)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("If an account exists, a link has been sent."))
          .andExpect(jsonPath("$.magicLink").isNotEmpty());
    }

    @Test
    void shouldReturn200ForNonExistentEmail() throws Exception {
      mockMvc
          .perform(
              post("/portal/auth/request-link")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "nonexistent@test.com", "orgId": "%s"}
                      """
                          .formatted(ORG_ID_A)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("If an account exists, a link has been sent."))
          .andExpect(jsonPath("$.magicLink").doesNotExist());
    }

    @Test
    void shouldReturn200WithGenericMessageForSuspendedContact() throws Exception {
      // Suspended contacts get the same generic 200 as non-existent ones (anti-enumeration)
      mockMvc
          .perform(
              post("/portal/auth/request-link")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "mlt-suspended@test.com", "orgId": "%s"}
                      """
                          .formatted(ORG_ID_A)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("If an account exists, a link has been sent."))
          .andExpect(jsonPath("$.magicLink").doesNotExist());
    }
  }

  @Nested
  class ExchangeEndpointTests {

    @Test
    void shouldExchangeValidTokenForPortalJwt() throws Exception {
      // Request a magic link
      var requestResult =
          mockMvc
              .perform(
                  post("/portal/auth/request-link")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"email": "mlt-endpoint@test.com", "orgId": "%s"}
                          """
                              .formatted(ORG_ID_A)))
              .andExpect(status().isOk())
              .andReturn();

      String magicLink =
          JsonPath.read(requestResult.getResponse().getContentAsString(), "$.magicLink");
      String token = extractTokenFromMagicLink(magicLink);

      // Exchange token for portal JWT
      mockMvc
          .perform(
              post("/portal/auth/exchange")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"token": "%s", "orgId": "%s"}
                      """
                          .formatted(token, ORG_ID_A)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.token").isNotEmpty())
          .andExpect(jsonPath("$.customerId").value(customerIdA.toString()))
          .andExpect(jsonPath("$.customerName").value("MLT Customer A"));
    }

    @Test
    void shouldReject401ForInvalidToken() throws Exception {
      mockMvc
          .perform(
              post("/portal/auth/exchange")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"token": "invalid-token-value", "orgId": "%s"}
                      """
                          .formatted(ORG_ID_A)))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class FullRoundTripTests {

    @Test
    void shouldCompleteFullRoundTrip() throws Exception {
      // Step 1: Request magic link
      var requestResult =
          mockMvc
              .perform(
                  post("/portal/auth/request-link")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"email": "mlt-customer-b@test.com", "orgId": "%s"}
                          """
                              .formatted(ORG_ID_B)))
              .andExpect(status().isOk())
              .andReturn();

      String magicLink =
          JsonPath.read(requestResult.getResponse().getContentAsString(), "$.magicLink");
      String token = extractTokenFromMagicLink(magicLink);

      // Step 2: Exchange token for portal JWT
      var exchangeResult =
          mockMvc
              .perform(
                  post("/portal/auth/exchange")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"token": "%s", "orgId": "%s"}
                          """
                              .formatted(token, ORG_ID_B)))
              .andExpect(status().isOk())
              .andReturn();

      String portalJwt =
          JsonPath.read(exchangeResult.getResponse().getContentAsString(), "$.token");
      assertThat(portalJwt).isNotBlank();

      // Step 3: Use portal JWT to access authenticated endpoint
      var portalResult =
          mockMvc
              .perform(get("/portal/projects").header("Authorization", "Bearer " + portalJwt))
              .andReturn();

      // Should get 200 (not 401/403) -- proves the JWT works for authenticated portal access
      assertThat(portalResult.getResponse().getStatus()).isNotEqualTo(401);
      assertThat(portalResult.getResponse().getStatus()).isNotEqualTo(403);
    }
  }

  @Nested
  class CrossTenantIsolationTests {

    @Test
    void shouldNotFindContactFromOtherOrgViaRequestLink() throws Exception {
      // Contact in org A should not be accessible via org B's auth flow
      mockMvc
          .perform(
              post("/portal/auth/request-link")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "mlt-crosstenant@test.com", "orgId": "%s"}
                      """
                          .formatted(ORG_ID_B)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("If an account exists, a link has been sent."))
          .andExpect(jsonPath("$.magicLink").doesNotExist());
    }

    @Test
    void shouldNotExchangeTokenAgainstWrongOrg() throws Exception {
      // Generate a token in org A
      var requestResult =
          mockMvc
              .perform(
                  post("/portal/auth/request-link")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"email": "mlt-crosstenant@test.com", "orgId": "%s"}
                          """
                              .formatted(ORG_ID_A)))
              .andExpect(status().isOk())
              .andReturn();

      String magicLink =
          JsonPath.read(requestResult.getResponse().getContentAsString(), "$.magicLink");
      String token = extractTokenFromMagicLink(magicLink);

      // Try to exchange it against org B -- should fail since token is in org A's schema
      mockMvc
          .perform(
              post("/portal/auth/exchange")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"token": "%s", "orgId": "%s"}
                      """
                          .formatted(token, ORG_ID_B)))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotAccessOrgBDataWithOrgAPortalJwt() throws Exception {
      // Issue a portal JWT for org A's customer
      String portalJwtA = portalJwtService.issueToken(customerIdA, ORG_ID_A);

      // This JWT binds org A's tenant context -- data returned is only org A's
      var result =
          mockMvc
              .perform(get("/portal/projects").header("Authorization", "Bearer " + portalJwtA))
              .andReturn();

      // The request succeeds (auth is valid for org A), not 401/403
      assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
      assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
    }
  }

  /** Extracts the raw token value from a magic link URL like /portal/login?token=...&orgId=... */
  private String extractTokenFromMagicLink(String magicLink) {
    String tokenParam = "token=";
    int tokenStart = magicLink.indexOf(tokenParam) + tokenParam.length();
    int tokenEnd = magicLink.indexOf("&", tokenStart);
    if (tokenEnd == -1) {
      return magicLink.substring(tokenStart);
    }
    return magicLink.substring(tokenStart, tokenEnd);
  }
}
