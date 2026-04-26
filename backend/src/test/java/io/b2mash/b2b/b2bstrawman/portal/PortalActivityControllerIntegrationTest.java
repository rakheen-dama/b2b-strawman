package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
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
 * Integration tests for {@link PortalActivityController}.
 *
 * <p>GAP-OBS-Portal-Activity / E4.3 -- exercises the portal activity timeline tabs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalActivityControllerIntegrationTest {

  private static final String ORG_ID = "org_portal_activity";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private PortalContactService portalContactService;
  @Autowired private CustomerProjectService customerProjectService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private UUID customerIdA;
  private UUID otherCustomerId;
  private UUID portalContactIdA;
  private UUID projectIdA;
  private UUID otherProjectId;
  private UUID memberId;
  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Activity Org", null);

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
                          "clerkUserId": "user_pa_owner",
                          "email": "pa_owner@test.com",
                          "name": "Activity Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andExpect(status().isCreated())
            .andReturn();

    memberId =
        UUID.fromString(JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId"));

    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var custA =
                  customerRepository.save(
                      TestCustomerFactory.createActiveCustomer(
                          "Activity Customer A", "pa-cust-a@test.com", memberId));
              customerIdA = custA.getId();

              var otherCust =
                  customerRepository.save(
                      TestCustomerFactory.createActiveCustomer(
                          "Other Customer", "pa-cust-other@test.com", memberId));
              otherCustomerId = otherCust.getId();

              var contactA =
                  portalContactService.createContact(
                      ORG_ID,
                      customerIdA,
                      "pa-cust-a@test.com",
                      "Activity Customer A",
                      PortalContact.ContactRole.PRIMARY);
              portalContactIdA = contactA.getId();

              var projA =
                  projectRepository.save(
                      new Project("Activity Project A", "Linked to customer A", memberId));
              projectIdA = projA.getId();

              var otherProj =
                  projectRepository.save(
                      new Project("Other Customer Project", "Linked to other", memberId));
              otherProjectId = otherProj.getId();

              customerProjectService.linkCustomerToProject(
                  customerIdA, projectIdA, memberId, new ActorContext(memberId, "owner"));
              customerProjectService.linkCustomerToProject(
                  otherCustomerId, otherProjectId, memberId, new ActorContext(memberId, "owner"));

              // Seed audit events
              // 1) PORTAL_CONTACT-authored event for customer A's portal contact (mine)
              auditEventRepository.save(
                  new AuditEvent(
                      new AuditEventRecord(
                          "portal.document.downloaded",
                          "document",
                          UUID.randomUUID(),
                          portalContactIdA,
                          "PORTAL_CONTACT",
                          "PORTAL",
                          null,
                          null,
                          Map.of("project_id", projectIdA.toString()))));

              // 2) USER (firm) event on customer A's project (firm)
              auditEventRepository.save(
                  new AuditEvent(
                      new AuditEventRecord(
                          "invoice.payment_recorded",
                          "invoice",
                          UUID.randomUUID(),
                          memberId,
                          "USER",
                          "API",
                          null,
                          null,
                          Map.of("project_id", projectIdA.toString()))));

              // 3) USER event scoped to a DIFFERENT customer's project -- must NOT leak
              auditEventRepository.save(
                  new AuditEvent(
                      new AuditEventRecord(
                          "invoice.sent",
                          "invoice",
                          UUID.randomUUID(),
                          memberId,
                          "USER",
                          "API",
                          null,
                          null,
                          Map.of("project_id", otherProjectId.toString()))));

              // 4) PORTAL_CONTACT event from a DIFFERENT contact -- must NOT leak into "mine"
              auditEventRepository.save(
                  new AuditEvent(
                      new AuditEventRecord(
                          "portal.document.acknowledged",
                          "document",
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          "PORTAL_CONTACT",
                          "PORTAL",
                          null,
                          null,
                          Map.of("project_id", otherProjectId.toString()))));
            });
  }

  private String portalToken() {
    return portalJwtService.issueToken(customerIdA, ORG_ID);
  }

  @Test
  void shouldReturnBothStreamsWhenTabIsAll() throws Exception {
    String token = portalToken();

    var result =
        mockMvc
            .perform(get("/portal/activity").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    // Both PORTAL_CONTACT (mine) and firm-side USER event on linked project must appear.
    assertThat(body).contains("portal.document.downloaded");
    assertThat(body).contains("invoice.payment_recorded");
  }

  @Test
  void shouldFilterToPortalContactWhenTabIsMine() throws Exception {
    String token = portalToken();

    mockMvc
        .perform(get("/portal/activity?tab=MINE").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].actorType").value("PORTAL_CONTACT"))
        .andExpect(jsonPath("$.content[0].eventType").value("portal.document.downloaded"))
        .andExpect(jsonPath("$.content[0].summary").value("Document downloaded"));
  }

  @Test
  void shouldExcludePortalContactEventsWhenTabIsFirm() throws Exception {
    String token = portalToken();

    var result =
        mockMvc
            .perform(get("/portal/activity?tab=FIRM").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    // Firm-seeded event must be present, portal-contact-authored event must NOT.
    assertThat(body).contains("invoice.payment_recorded");
    assertThat(body).doesNotContain("portal.document.downloaded");
    // Every event in the firm tab must have a non-PORTAL_CONTACT actor type.
    assertThat(body).doesNotContain("\"actorType\":\"PORTAL_CONTACT\"");
  }

  @Test
  void shouldNotLeakOtherCustomersFirmEvents() throws Exception {
    String token = portalToken();

    var result =
        mockMvc
            .perform(get("/portal/activity").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    // Other customer's invoice.sent event uses otherProjectId -- must not appear
    assertThat(body).doesNotContain(otherProjectId.toString());
    assertThat(body).doesNotContain("invoice.sent");
    assertThat(body).doesNotContain("portal.document.acknowledged");
  }

  @Test
  void shouldReturnEmptyPageWhenCustomerHasNoActivity() throws Exception {
    // Issue a token for the "other" customer -- with no portal contact, the request will
    // fail because PORTAL_CONTACT_ID isn't bound. Use a customer with a portal contact but
    // no events -- create one fresh.
    UUID emptyCustomerId =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.ORG_ID, ORG_ID)
            .call(
                () -> {
                  var c =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Empty Customer", "pa-cust-empty@test.com", memberId));
                  portalContactService.createContact(
                      ORG_ID,
                      c.getId(),
                      "pa-cust-empty@test.com",
                      "Empty Customer",
                      PortalContact.ContactRole.PRIMARY);
                  return c.getId();
                });

    String token = portalJwtService.issueToken(emptyCustomerId, ORG_ID);

    mockMvc
        .perform(get("/portal/activity").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.page.totalElements").value(0));
  }
}
