package io.b2mash.b2b.b2bstrawman.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.MagicLinkService;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(
    properties = {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-issuer.example.com",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://test-issuer.example.com/.well-known/jwks.json",
      "internal.api.key=test-api-key",
      "portal.jwt.secret=test-portal-secret-must-be-at-least-32-bytes-long",
      "portal.magic-link.secret=test-magic-link-secret-must-be-at-least-32-bytes-long",
      "aws.s3.endpoint=http://localhost:4566",
      "aws.s3.region=us-east-1",
      "aws.s3.bucket-name=test-bucket",
      "aws.credentials.access-key-id=test",
      "aws.credentials.secret-access-key=test"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DevPortalControllerTest {

  private static final String ORG_ID = "org_dev_harness_test";
  private static final String API_KEY = "test-api-key";
  private static final String CUSTOMER_EMAIL = "dev-harness-customer@test.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private MagicLinkService magicLinkService;
  @Autowired private PortalJwtService portalJwtService;

  private UUID customerId;
  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Dev Harness Test Org");
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
                          "clerkUserId": "user_dev_harness_owner",
                          "email": "dev_harness_owner@test.com",
                          "name": "Dev Harness Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andExpect(status().isCreated())
            .andReturn();

    String memberIdStr = JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId");
    UUID memberId = UUID.fromString(memberIdStr);

    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    // Create a customer in the tenant
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Dev Harness Customer", CUSTOMER_EMAIL, null, null, null, memberId);
              customerId = customer.getId();
            });
  }

  @Nested
  class GenerateLinkPage {

    @Test
    void shouldRenderGenerateLinkForm() throws Exception {
      var result =
          mockMvc
              .perform(get("/portal/dev/generate-link").accept(MediaType.TEXT_HTML))
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("Portal Dev Harness");
      assertThat(body).contains("Generate Magic Link");
      assertThat(body).contains(ORG_ID);
    }

    @Test
    void shouldGenerateMagicLinkForExistingCustomer() throws Exception {
      // First create a portal contact so the customer email is findable
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.ORG_ID, ORG_ID)
          .run(
              () ->
                  portalContactService.createContact(
                      ORG_ID,
                      customerId,
                      "dev-harness-existing@test.com",
                      "Existing Contact",
                      PortalContact.ContactRole.GENERAL));

      var result =
          mockMvc
              .perform(
                  post("/portal/dev/generate-link")
                      .param("email", "dev-harness-existing@test.com")
                      .param("orgId", ORG_ID)
                      .accept(MediaType.TEXT_HTML))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("/portal/dev/exchange");
      assertThat(body).contains("token=");
    }

    @Test
    void shouldAutoCreateContactAndGenerateLinkForCustomerEmail() throws Exception {
      var result =
          mockMvc
              .perform(
                  post("/portal/dev/generate-link")
                      .param("email", CUSTOMER_EMAIL)
                      .param("orgId", ORG_ID)
                      .accept(MediaType.TEXT_HTML))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("/portal/dev/exchange");
      assertThat(body).contains("token=");
    }

    @Test
    void shouldShowErrorForUnknownEmail() throws Exception {
      var result =
          mockMvc
              .perform(
                  post("/portal/dev/generate-link")
                      .param("email", "nobody@unknown.com")
                      .param("orgId", ORG_ID)
                      .accept(MediaType.TEXT_HTML))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("No customer found");
    }

    @Test
    void shouldShowErrorForInvalidOrg() throws Exception {
      var result =
          mockMvc
              .perform(
                  post("/portal/dev/generate-link")
                      .param("email", CUSTOMER_EMAIL)
                      .param("orgId", "org_nonexistent")
                      .accept(MediaType.TEXT_HTML))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("Organization not found");
    }
  }

  @Nested
  class ExchangeEndpoint {

    @Test
    void shouldExchangeTokenAndRedirectToDashboard() throws Exception {
      // Create a contact and generate a magic link token
      UUID contactId =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
              .where(RequestScopes.ORG_ID, ORG_ID)
              .call(
                  () -> {
                    var contact =
                        portalContactService.createContact(
                            ORG_ID,
                            customerId,
                            "dev-harness-exchange@test.com",
                            "Exchange Contact",
                            PortalContact.ContactRole.GENERAL);
                    return contact.getId();
                  });

      String rawToken =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
              .call(() -> magicLinkService.generateToken(contactId, "127.0.0.1"));

      mockMvc
          .perform(
              get("/portal/dev/exchange")
                  .param("token", rawToken)
                  .param("orgId", ORG_ID)
                  .accept(MediaType.TEXT_HTML))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrlPattern("/portal/dev/dashboard?token=*"));
    }

    @Test
    void shouldShowErrorForInvalidToken() throws Exception {
      var result =
          mockMvc
              .perform(
                  get("/portal/dev/exchange")
                      .param("token", "invalid-token")
                      .param("orgId", ORG_ID)
                      .accept(MediaType.TEXT_HTML))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("Invalid or expired magic link");
    }
  }

  @Nested
  class DashboardPage {

    @Test
    void shouldRenderDashboardWithValidJwt() throws Exception {
      String portalJwt = portalJwtService.issueToken(customerId, ORG_ID);

      var result =
          mockMvc
              .perform(
                  get("/portal/dev/dashboard")
                      .param("token", portalJwt)
                      .accept(MediaType.TEXT_HTML))
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("Customer Dashboard");
      assertThat(body).contains(ORG_ID);
    }

    @Test
    void shouldShowErrorForInvalidJwtOnDashboard() throws Exception {
      var result =
          mockMvc
              .perform(
                  get("/portal/dev/dashboard")
                      .param("token", "invalid-jwt")
                      .accept(MediaType.TEXT_HTML))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("Invalid or expired portal token");
    }
  }

  @Nested
  class ProjectDetailPage {

    @Test
    void shouldShowErrorForInvalidJwtOnProjectDetail() throws Exception {
      var result =
          mockMvc
              .perform(
                  get("/portal/dev/project/" + UUID.randomUUID())
                      .param("token", "invalid-jwt")
                      .accept(MediaType.TEXT_HTML))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("Invalid or expired portal token");
    }

    @Test
    void shouldShowErrorForNonexistentProject() throws Exception {
      String portalJwt = portalJwtService.issueToken(customerId, ORG_ID);

      var result =
          mockMvc
              .perform(
                  get("/portal/dev/project/" + UUID.randomUUID())
                      .param("token", portalJwt)
                      .accept(MediaType.TEXT_HTML))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("Error loading project");
    }
  }
}
