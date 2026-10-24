package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalAuthIntegrationTest {

  private static final String ORG_ID = "org_portal_test";
  private static final String ORG_ID_B = "org_portal_auth_b";
  private static final String API_KEY = "test-api-key";
  private static final String GENERIC_MESSAGE = "If an account exists, a link has been sent.";

  // JVM-singleton GreenMail on a dynamic port (see GreenMailTestSupport); never a second instance.
  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  @Autowired private MockMvc mockMvc;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private UUID customerId;
  private String tenantSchema;
  private String tenantSchemaB;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Test Org", null);

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
                      "Portal Customer", "portal-customer@test.com", null, null, null, memberId);
              customerId = customer.getId();

              // Portal contact needed by MagicLinkUrlRegressionTests
              portalContactService.createContact(
                  ORG_ID,
                  customerId,
                  "portal-genverify@test.com",
                  "Gen Verify Contact",
                  PortalContact.ContactRole.PRIMARY);

              // Phase 88 U11 (624B): email-only request-link contacts. Each test owns its email
              // (the per-email limiter is a context singleton that is never reset).
              for (String email :
                  List.of(
                      "one-firm-624b@test.com",
                      "two-firms-624b@test.com",
                      "fast-path-624b@test.com",
                      "capped-624b@test.com",
                      "Mixed.Case-624B@Test.com",
                      "ident-one-624b@test.com",
                      "ident-two-624b@test.com",
                      "blank-org-624b@test.com")) {
                portalContactService.createContact(
                    ORG_ID, customerId, email, "Contact A", PortalContact.ContactRole.GENERAL);
              }
            });

    // Second firm (624B): contacts that exist in both firms.
    provisioningService.provisionTenant(ORG_ID_B, "Portal Test Org B", null);
    UUID memberIdB =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID_B,
                "user_portal_auth_owner_b",
                "portal_auth_owner_b@test.com",
                "Portal Owner B",
                "owner"));
    tenantSchemaB = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).get().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaB)
        .where(RequestScopes.ORG_ID, ORG_ID_B)
        .run(
            () -> {
              var customerB =
                  customerService.createCustomer(
                      "Portal Customer B",
                      "portal-customer-b@test.com",
                      null,
                      null,
                      null,
                      memberIdB);
              for (String email :
                  List.of(
                      "two-firms-624b@test.com",
                      "fast-path-624b@test.com",
                      "capped-624b@test.com",
                      "ident-two-624b@test.com")) {
                portalContactService.createContact(
                    ORG_ID_B,
                    customerB.getId(),
                    email,
                    "Contact B",
                    PortalContact.ContactRole.GENERAL);
              }
            });
  }

  @BeforeEach
  void purgeMail() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
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
      mockMvc.perform(get("/portal/matters")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedPortalEndpointsReject401WithInvalidToken() throws Exception {
      mockMvc
          .perform(get("/portal/matters").header("Authorization", "Bearer invalid-token"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedPortalEndpointsAcceptValidPortalJwt() throws Exception {
      String portalToken = portalJwtService.issueToken(customerId, ORG_ID);

      var result =
          mockMvc
              .perform(get("/portal/matters").header("Authorization", "Bearer " + portalToken))
              .andReturn();

      int statusCode = result.getResponse().getStatus();
      assertThat(statusCode).isNotEqualTo(401);
      assertThat(statusCode).isNotEqualTo(403);
    }

    @Test
    void staffJwtCannotAccessPortalEndpoints() throws Exception {
      mockMvc
          .perform(
              get("/portal/matters")
                  .with(
                      jwt()
                          .jwt(
                              j ->
                                  j.subject("user_staff")
                                      .claim("o", Map.of("id", ORG_ID, "rol", "owner")))))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void existingStaffApiUnaffectedByPortalChain() throws Exception {
      mockMvc
          .perform(
              get("/api/clients")
                  .with(
                      jwt()
                          .jwt(
                              j ->
                                  j.subject("user_portal_owner")
                                      .claim("o", Map.of("id", ORG_ID, "rol", "owner")))))
          .andExpect(status().isOk());
    }

    @Test
    void portalJwtCannotAccessStaffApi() {
      String portalToken = portalJwtService.issueToken(customerId, ORG_ID);

      try {
        var result =
            mockMvc
                .perform(get("/api/clients").header("Authorization", "Bearer " + portalToken))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
      } catch (Exception e) {
        assertThat(e).hasStackTraceContaining("JwtDecoder");
      }
    }
  }

  /**
   * Regression tests for BUG-009: Magic link URL must use /auth/exchange?token= path (not
   * /portal/login?token=). Also verifies that the "test" profile is recognized by isDevProfile(),
   * so the magicLink field is non-null in the response.
   */
  @Nested
  class MagicLinkUrlRegressionTests {

    @Test
    void magicLinkUrlContainsAuthExchangePath() throws Exception {
      var result =
          mockMvc
              .perform(
                  post("/portal/auth/request-link")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"email": "portal-genverify@test.com", "orgId": "%s"}
                          """
                              .formatted(ORG_ID)))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      String magicLink = JsonPath.read(body, "$.magicLink");

      // BUG-009 fix: URL must use /auth/exchange?token= (not /portal/login?token=)
      assertThat(magicLink).isNotNull();
      assertThat(magicLink).startsWith("/auth/exchange?token=");
      assertThat(magicLink).contains("orgId=" + ORG_ID);
      assertThat(magicLink).doesNotContain("/portal/login");
    }

    @Test
    void magicLinkIsNonNullInTestProfile() throws Exception {
      // The test profile is active (@ActiveProfiles("test")), so isDevProfile() should return true
      // and the magicLink field should be populated (not null).
      var result =
          mockMvc
              .perform(
                  post("/portal/auth/request-link")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"email": "portal-genverify@test.com", "orgId": "%s"}
                          """
                              .formatted(ORG_ID)))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      String magicLink = JsonPath.read(body, "$.magicLink");

      // Verifies isDevProfile() returns true for "test" profile (and by extension "e2e", "local",
      // "dev")
      assertThat(magicLink)
          .as("magicLink should be non-null in test profile (isDevProfile() == true)")
          .isNotNull()
          .isNotBlank();
    }
  }

  /**
   * Phase 88 U11 / G-D1 (ADR-351): {@code POST /portal/auth/request-link} accepts {@code email}
   * alone, resolves 0..N firms via the global contact directory and sends one branded link per
   * firm, always with the same generic response; a per-email cap (3 / 15 min) applies across firms
   * and both request shapes.
   */
  @Nested
  class EmailOnlyRequestLinkTests {

    @Test
    void emailOnlyOneFirmSendsOneBrandedLink() throws Exception {
      String body =
          requestLink(
                  """
                  {"email": "one-firm-624b@test.com"}
                  """)
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
              .andReturn()
              .getResponse()
              .getContentAsString();

      String magicLink = JsonPath.read(body, "$.magicLink");
      assertThat(magicLink).startsWith("/auth/exchange?token=").contains("orgId=" + ORG_ID);

      var mails = messagesTo("one-firm-624b@test.com");
      assertThat(mails).hasSize(1);
      assertThat(mails.getFirst().getSubject()).contains("Portal Test Org");
      assertThat(content(mails.getFirst())).contains("orgId=" + ORG_ID);
    }

    @Test
    void emailOnlyUnknownEmailSendsNothing() throws Exception {
      requestLink(
              """
              {"email": "nobody-624b@test.com"}
              """)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
          .andExpect(jsonPath("$.magicLink").doesNotExist());

      assertThat(messagesTo("nobody-624b@test.com")).isEmpty();
    }

    @Test
    void emailOnlyTwoFirmsSendsOneBrandedLinkPerFirm() throws Exception {
      requestLink(
              """
              {"email": "two-firms-624b@test.com"}
              """)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));

      var mails = messagesTo("two-firms-624b@test.com");
      assertThat(mails).hasSize(2);

      MimeMessage mailA = null;
      MimeMessage mailB = null;
      for (MimeMessage m : mails) {
        if (m.getSubject().endsWith("Portal Test Org B")) {
          mailB = m;
        } else if (m.getSubject().endsWith("Portal Test Org")) {
          mailA = m;
        }
      }
      assertThat(mailA).as("one mail branded with firm A").isNotNull();
      assertThat(mailB).as("one mail branded with firm B").isNotNull();
      String bodyA = content(mailA);
      String bodyB = content(mailB);
      assertThat(bodyA).contains("orgId=" + ORG_ID).doesNotContain("orgId=" + ORG_ID_B);
      assertThat(bodyB).contains("orgId=" + ORG_ID_B).doesNotContain("orgId=" + ORG_ID);
      assertThat(tokenIn(bodyA)).isNotEqualTo(tokenIn(bodyB));
    }

    @Test
    void genericMessageIsIdenticalForZeroOneAndManyFirms() throws Exception {
      String zero = messageFor("zero-624b@test.com");
      String one = messageFor("ident-one-624b@test.com");
      String many = messageFor("ident-two-624b@test.com");

      assertThat(zero).isEqualTo(GENERIC_MESSAGE);
      assertThat(one).isEqualTo(zero);
      assertThat(many).isEqualTo(zero);
      assertThat(messagesTo("ident-one-624b@test.com")).hasSize(1);
      assertThat(messagesTo("ident-two-624b@test.com")).hasSize(2);
    }

    @Test
    void emailOnlyMatchesAContactStoredInMixedCase() throws Exception {
      requestLink(
              """
              {"email": "mixed.case-624b@test.com"}
              """)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));

      assertThat(messagesTo("Mixed.Case-624B@Test.com")).hasSize(1);
    }

    @Test
    void fastPathWithOrgIdSendsOnlyThatFirm() throws Exception {
      String body =
          requestLink(
                  """
                  {"email": "fast-path-624b@test.com", "orgId": "%s"}
                  """
                      .formatted(ORG_ID_B))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
              .andReturn()
              .getResponse()
              .getContentAsString();

      String magicLink = JsonPath.read(body, "$.magicLink");
      assertThat(magicLink).contains("orgId=" + ORG_ID_B);
      var mails = messagesTo("fast-path-624b@test.com");
      assertThat(mails).hasSize(1);
      assertThat(mails.getFirst().getSubject()).contains("Portal Test Org B");
    }

    @Test
    void blankOrgIdIsTreatedAsEmailOnly() throws Exception {
      requestLink(
              """
              {"email": "blank-org-624b@test.com", "orgId": ""}
              """)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));

      assertThat(messagesTo("blank-org-624b@test.com")).hasSize(1);
    }

    @Test
    void perEmailCapAppliesAcrossFirmsAndRequestShapes() throws Exception {
      String email = "capped-624b@test.com";
      requestLink(
              """
              {"email": "%s", "orgId": "%s"}
              """
                  .formatted(email, ORG_ID))
          .andExpect(status().isOk());
      requestLink(
              """
              {"email": "%s", "orgId": "%s"}
              """
                  .formatted(email, ORG_ID_B))
          .andExpect(status().isOk());
      requestLink(
              """
              {"email": "%s"}
              """
                  .formatted(email))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));
      assertThat(messagesTo(email)).hasSize(4); // 1 (A) + 1 (B) + 2 (A and B)

      requestLink(
              """
              {"email": "%s"}
              """
                  .formatted(email))
          .andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.status").value(429))
          .andExpect(jsonPath("$.title").value("Too many login attempts"));
      assertThat(messagesTo(email)).as("nothing sent once capped").hasSize(4);
    }

    @Test
    void perEmailCapTreatsUnknownEmailsTheSame() throws Exception {
      String json =
          """
          {"email": "unknown-capped-624b@test.com"}
          """;
      for (int i = 0; i < 3; i++) {
        requestLink(json)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));
      }
      requestLink(json)
          .andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.status").value(429))
          .andExpect(jsonPath("$.title").value("Too many login attempts"));
    }

    private String messageFor(String email) throws Exception {
      String body =
          requestLink(
                  """
                  {"email": "%s"}
                  """
                      .formatted(email))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(body, "$.message");
    }
  }

  // --- 624B helpers ---

  private ResultActions requestLink(String json) throws Exception {
    return mockMvc.perform(
        post("/portal/auth/request-link").contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private List<MimeMessage> messagesTo(String recipient) throws Exception {
    List<MimeMessage> out = new ArrayList<>();
    for (MimeMessage m : greenMail.getReceivedMessages()) {
      for (var r : m.getAllRecipients()) {
        if (r.toString().equalsIgnoreCase(recipient)) {
          out.add(m);
        }
      }
    }
    return out;
  }

  /** Text of a (possibly multipart) message — as PortalEmailServiceIntegrationTest. */
  private String content(MimeMessage message) throws Exception {
    Object content = message.getContent();
    if (content instanceof String str) {
      return str;
    }
    if (content instanceof MimeMultipart multipart) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        if (multipart.getBodyPart(i).getContent() instanceof String part) {
          sb.append(part);
        }
      }
      return sb.toString();
    }
    return content.toString();
  }

  /** Token from ".../auth/exchange?token=XYZ&orgId=..." (HTML may encode & as &amp;). */
  private static String tokenIn(String body) {
    Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body);
    assertThat(m.find()).as("magic link token in mail body").isTrue();
    return m.group(1);
  }
}
