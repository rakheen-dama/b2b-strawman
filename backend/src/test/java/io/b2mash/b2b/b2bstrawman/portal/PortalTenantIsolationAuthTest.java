package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.directory.PortalContactDirectoryRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Phase 88 U11 (ADR-351, §13.6.3): the global contact directory only selects which tenants to
 * enter. Tokens are minted, stored and exchanged inside the target tenant, so a contact of firm A
 * can never obtain a token that works for firm B, a stale directory row is a no-op, and the
 * directory row's {@code tenant_schema} is never used for routing (org → schema/shard always comes
 * from {@code org_schema_mapping}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalTenantIsolationAuthTest {

  private static final String ORG_A = "org_isolation_auth_a";
  private static final String ORG_B = "org_isolation_auth_b";
  private static final String GENERIC_MESSAGE = "If an account exists, a link has been sent.";

  // JVM-singleton GreenMail on a dynamic port (see GreenMailTestSupport); never a second instance.
  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalContactDirectoryRepository directoryRepository;
  @Autowired private MagicLinkTokenRepository magicLinkTokenRepository;

  private String schemaA;
  private String schemaB;
  private UUID customerIdA;
  private UUID customerIdB;
  private UUID onlyInAContactId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_A, "Isolation Firm A", null);
    provisioningService.provisionTenant(ORG_B, "Isolation Firm B", null);
    UUID memberA =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_A,
                "user_iso_auth_owner_a",
                "iso_auth_owner_a@test.com",
                "Iso Owner A",
                "owner"));
    UUID memberB =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_B,
                "user_iso_auth_owner_b",
                "iso_auth_owner_b@test.com",
                "Iso Owner B",
                "owner"));
    schemaA = orgSchemaMappingRepository.findByClerkOrgId(ORG_A).orElseThrow().getSchemaName();
    schemaB = orgSchemaMappingRepository.findByClerkOrgId(ORG_B).orElseThrow().getSchemaName();

    inTenant(
        schemaA,
        ORG_A,
        () -> {
          customerIdA =
              customerService
                  .createCustomer(
                      "Isolation Customer A", "iso-cust-a@test.com", null, null, null, memberA)
                  .getId();
          onlyInAContactId = createContact(ORG_A, customerIdA, "only-in-a@test.com").getId();
          createContact(ORG_A, customerIdA, "only-in-a-x@test.com");
          createContact(ORG_A, customerIdA, "in-both@test.com");
          createContact(ORG_A, customerIdA, "forged-route@test.com");
          return null;
        });
    inTenant(
        schemaB,
        ORG_B,
        () -> {
          customerIdB =
              customerService
                  .createCustomer(
                      "Isolation Customer B", "iso-cust-b@test.com", null, null, null, memberB)
                  .getId();
          createContact(ORG_B, customerIdB, "in-both@test.com");
          return null;
        });
  }

  @BeforeEach
  void purgeMail() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  @Test
  void requestWithAnotherFirmsOrgIdSendsNothingAndMintsNoToken() throws Exception {
    Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);
    long tokensBefore = tokenCountInA(onlyInAContactId, before);

    requestLink(
            """
            {"email": "only-in-a@test.com", "orgId": "%s"}
            """
                .formatted(ORG_B))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
        .andExpect(jsonPath("$.magicLink").doesNotExist());

    assertThat(messagesTo("only-in-a@test.com")).isEmpty();
    assertThat(tokenCountInA(onlyInAContactId, before)).isEqualTo(tokensBefore);
  }

  @Test
  void firmATokenCannotBeExchangedWithFirmBOrgId() throws Exception {
    String body =
        requestLink(
                """
                {"email": "only-in-a-x@test.com", "orgId": "%s"}
                """
                    .formatted(ORG_A))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = tokenIn(JsonPath.read(body, "$.magicLink"));

    exchange(token, ORG_B)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.title").value("Portal authentication failed"))
        .andExpect(jsonPath("$.detail").value("Invalid magic link token"));

    // The failed attempt against B did not touch A's token: it still exchanges in A.
    exchange(token, ORG_A)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientId").value(customerIdA.toString()));
  }

  @Test
  void multiFirmTokensAreEachBoundToTheirOwnFirm() throws Exception {
    requestLink(
            """
            {"email": "in-both@test.com"}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));

    var mails = messagesTo("in-both@test.com");
    assertThat(mails).hasSize(2);
    String tokenA = null;
    String tokenB = null;
    for (MimeMessage m : mails) {
      String text = content(m);
      if (text.contains("orgId=" + ORG_A)) {
        tokenA = tokenIn(text);
      } else if (text.contains("orgId=" + ORG_B)) {
        tokenB = tokenIn(text);
      }
    }
    assertThat(tokenA).as("firm A link").isNotNull();
    assertThat(tokenB).as("firm B link").isNotNull();

    exchange(tokenA, ORG_B)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Invalid magic link token"));
    exchange(tokenB, ORG_A)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Invalid magic link token"));
    exchange(tokenA, ORG_A)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientId").value(customerIdA.toString()));
    exchange(tokenB, ORG_B)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientId").value(customerIdB.toString()));
  }

  @Test
  void staleDirectoryRowIsANoOp() throws Exception {
    // A routing row with no contact behind it anywhere (e.g. a missed reconcile).
    directoryRepository.upsert("stale-only-b@test.com", ORG_B, schemaB, "Isolation Firm B");
    try {
      requestLink(
              """
              {"email": "stale-only-b@test.com"}
              """)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
          .andExpect(jsonPath("$.magicLink").doesNotExist());

      assertThat(messagesTo("stale-only-b@test.com")).isEmpty();
    } finally {
      directoryRepository.deleteByEmailLowerAndOrgId("stale-only-b@test.com", ORG_B);
    }
  }

  @Test
  void directoryTenantSchemaIsNeverUsedForRouting() throws Exception {
    // forged-route@ is a contact in A only; point its genuine row's routing column at B's schema.
    // An implementation that routed on the row's tenant_schema would search B, find no contact and
    // send nothing. Routing must come from org_schema_mapping by org id.
    directoryRepository.upsert("forged-route@test.com", ORG_A, schemaB, "Isolation Firm A");
    try {
      requestLink(
              """
              {"email": "forged-route@test.com"}
              """)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));

      var mails = messagesTo("forged-route@test.com");
      assertThat(mails).hasSize(1);
      assertThat(content(mails.getFirst())).contains("orgId=" + ORG_A);
    } finally {
      directoryRepository.upsert("forged-route@test.com", ORG_A, schemaA, "Isolation Firm A");
    }
  }

  // --- helpers ---

  private PortalContact createContact(String orgId, UUID customerId, String email) {
    return portalContactService.createContact(
        orgId, customerId, email, "Isolation Contact", PortalContact.ContactRole.GENERAL);
  }

  private long tokenCountInA(UUID contactId, Instant after) {
    return inTenant(
        schemaA,
        ORG_A,
        () -> magicLinkTokenRepository.countByPortalContactIdAndCreatedAtAfter(contactId, after));
  }

  private ResultActions requestLink(String json) throws Exception {
    return mockMvc.perform(
        post("/portal/auth/request-link").contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions exchange(String token, String orgId) throws Exception {
    return mockMvc.perform(
        post("/portal/auth/exchange")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"token": "%s", "orgId": "%s"}
                """
                    .formatted(token, orgId)));
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
  private static String tokenIn(String text) {
    Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(text);
    assertThat(m.find()).as("magic link token").isTrue();
    return m.group(1);
  }

  private static <T> T inTenant(String schema, String orgId, Callable<T> action) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, schema)
          .where(RequestScopes.ORG_ID, orgId)
          .call(action::call);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
