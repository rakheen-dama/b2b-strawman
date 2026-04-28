package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GAP-L-100 -- regression coverage for the portal /activity Firm-actions allow-list filter and the
 * extended {@link PortalActivityEventResponse#summaryFor(String)} humaniser.
 *
 * <p>Drives {@link PortalActivityService#listActivity} directly under a bound {@code RequestScopes}
 * scope rather than going through HTTP, so each case can seed a tightly-controlled audit-event
 * fixture and assert against the projection without auth plumbing noise.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalActivityServiceFirmFilterTest {

  private static final String ORG_ID = "org_pa_firm_filter";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private PortalActivityService portalActivityService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private PortalContactService portalContactService;
  @Autowired private CustomerProjectService customerProjectService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private UUID customerId;
  private UUID portalContactId;
  private UUID projectId;
  private UUID memberId;
  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Firm-Filter Org", null);

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
                          "clerkUserId": "user_pa_ff_owner",
                          "email": "pa_ff_owner@test.com",
                          "name": "Filter Owner",
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
              var cust =
                  customerRepository.save(
                      TestCustomerFactory.createActiveCustomer(
                          "Filter Customer", "pa-ff-cust@test.com", memberId));
              customerId = cust.getId();

              var contact =
                  portalContactService.createContact(
                      ORG_ID,
                      customerId,
                      "pa-ff-cust@test.com",
                      "Filter Customer",
                      PortalContact.ContactRole.PRIMARY);
              portalContactId = contact.getId();

              var proj =
                  projectRepository.save(
                      new Project("Filter Matter", "Allow-list test matter", memberId));
              projectId = proj.getId();

              customerProjectService.linkCustomerToProject(
                  customerId, projectId, memberId, new ActorContext(memberId, "owner"));
            });
  }

  /** Seeds a firm (USER actor) audit event on the test project with no extra details. */
  private void seedFirmEvent(String eventType) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                auditEventRepository.save(
                    new AuditEvent(
                        new AuditEventRecord(
                            eventType,
                            "matter",
                            UUID.randomUUID(),
                            memberId,
                            "USER",
                            "API",
                            null,
                            null,
                            Map.of("project_id", projectId.toString())))));
  }

  /** Seeds a portal-contact-authored audit event for the bound contact. */
  private void seedPortalEvent(String eventType) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                auditEventRepository.save(
                    new AuditEvent(
                        new AuditEventRecord(
                            eventType,
                            "document",
                            UUID.randomUUID(),
                            portalContactId,
                            "PORTAL_CONTACT",
                            "PORTAL",
                            null,
                            null,
                            Map.of("project_id", projectId.toString())))));
  }

  /** Runs a service call under the portal-context scopes and returns the response page. */
  private Page<PortalActivityEventResponse> listActivity(PortalActivityService.Tab tab) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.CUSTOMER_ID, customerId)
        .where(RequestScopes.PORTAL_CONTACT_ID, portalContactId)
        .call(() -> portalActivityService.listActivity(tab, PageRequest.of(0, 100)));
  }

  @Test
  void firmTab_excludes_time_entry_events() {
    seedFirmEvent("time_entry.created");
    seedFirmEvent("time_entry.deleted");
    seedFirmEvent("time_entry.changed");
    seedFirmEvent("time_entry.rate_re_snapshot");

    var page = listActivity(PortalActivityService.Tab.FIRM);

    assertThat(page.getContent())
        .extracting(PortalActivityEventResponse::eventType)
        .doesNotContain(
            "time_entry.created",
            "time_entry.deleted",
            "time_entry.changed",
            "time_entry.rate_re_snapshot");
  }

  @Test
  void firmTab_excludes_disbursement_events() {
    seedFirmEvent("disbursement.created");
    seedFirmEvent("disbursement.updated");
    seedFirmEvent("disbursement.submitted");
    seedFirmEvent("disbursement.approved");
    seedFirmEvent("disbursement.rejected");
    seedFirmEvent("disbursement.written_off");
    seedFirmEvent("disbursement.billed");
    seedFirmEvent("disbursement.unmarked_billed");
    seedFirmEvent("disbursement.receipt_attached");

    var page = listActivity(PortalActivityService.Tab.FIRM);

    assertThat(page.getContent())
        .extracting(PortalActivityEventResponse::eventType)
        .filteredOn(t -> t != null && t.startsWith("disbursement."))
        .isEmpty();
  }

  @Test
  void firmTab_excludes_court_date_and_project_internals() {
    seedFirmEvent("court_date.created");
    seedFirmEvent("project.created");
    seedFirmEvent("project.updated");
    seedFirmEvent("project.created_from_template");

    var page = listActivity(PortalActivityService.Tab.FIRM);

    assertThat(page.getContent())
        .extracting(PortalActivityEventResponse::eventType)
        .doesNotContain(
            "court_date.created",
            "project.created",
            "project.updated",
            "project.created_from_template");
  }

  @Test
  void firmTab_includes_information_request_lifecycle() {
    seedFirmEvent("information_request.created");
    seedFirmEvent("information_request.sent");
    seedFirmEvent("information_request.completed");
    seedFirmEvent("information_request.item_accepted");

    var page = listActivity(PortalActivityService.Tab.FIRM);

    assertThat(page.getContent())
        .extracting(PortalActivityEventResponse::eventType)
        .contains(
            "information_request.created",
            "information_request.sent",
            "information_request.completed",
            "information_request.item_accepted");
  }

  @Test
  void firmTab_includes_invoice_proposal_statement_closure() {
    seedFirmEvent("invoice.sent");
    seedFirmEvent("proposal.sent");
    seedFirmEvent("proposal.accepted");
    seedFirmEvent("statement.generated");
    seedFirmEvent("matter_closure.closed");

    var page = listActivity(PortalActivityService.Tab.FIRM);

    assertThat(page.getContent())
        .extracting(PortalActivityEventResponse::eventType)
        .contains(
            "invoice.sent",
            "proposal.sent",
            "proposal.accepted",
            "statement.generated",
            "matter_closure.closed");
  }

  @Test
  void firmTab_humanises_event_labels() {
    seedFirmEvent("statement.generated");
    seedFirmEvent("information_request.sent");
    seedFirmEvent("proposal.sent");
    seedFirmEvent("matter_closure.closed");
    seedFirmEvent("trust_transaction.approved");
    seedFirmEvent("document.generated");

    var page = listActivity(PortalActivityService.Tab.FIRM);

    var summariesByType =
        page.getContent().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    PortalActivityEventResponse::eventType,
                    PortalActivityEventResponse::summary,
                    (a, b) -> a));

    assertThat(summariesByType.get("statement.generated"))
        .isEqualTo("Statement of Account generated");
    assertThat(summariesByType.get("information_request.sent"))
        .isEqualTo("Information request sent to you");
    assertThat(summariesByType.get("proposal.sent")).isEqualTo("Engagement letter sent to you");
    assertThat(summariesByType.get("matter_closure.closed")).isEqualTo("Matter closed");
    assertThat(summariesByType.get("trust_transaction.approved"))
        .isEqualTo("Trust transaction recorded");
    assertThat(summariesByType.get("document.generated")).isEqualTo("Document generated for you");

    // Defensive: every visible row must have a humanised summary that is NOT a raw slug
    // (no row containing a dot in its summary, which is the slug shape).
    assertThat(page.getContent())
        .filteredOn(
            e -> PortalActivityEventTypes.PORTAL_VISIBLE_FIRM_EVENT_TYPES.contains(e.eventType()))
        .allSatisfy(
            e ->
                assertThat(e.summary())
                    .as("summary for %s must be humanised, not a raw slug", e.eventType())
                    .doesNotContain("."));
  }

  @Test
  void mineTab_unchanged() {
    seedPortalEvent("portal.document.downloaded");
    seedFirmEvent("time_entry.created");

    var page = listActivity(PortalActivityService.Tab.MINE);

    // Allow-list does NOT apply to MINE -- portal contacts always see their own actions.
    assertThat(page.getContent())
        .extracting(PortalActivityEventResponse::eventType)
        .contains("portal.document.downloaded")
        .doesNotContain("time_entry.created");

    var ownDownload =
        page.getContent().stream()
            .filter(e -> "portal.document.downloaded".equals(e.eventType()))
            .findFirst()
            .orElseThrow();
    assertThat(ownDownload.summary()).isEqualTo("You downloaded a document");
  }

  @Test
  void allTab_filters_firm_side_only() {
    seedPortalEvent("portal.request_item.submitted");
    seedFirmEvent("invoice.sent"); // allow-listed
    seedFirmEvent("statement.generated"); // allow-listed
    seedFirmEvent("time_entry.created"); // blocked
    seedFirmEvent("disbursement.billed"); // blocked

    var page = listActivity(PortalActivityService.Tab.ALL);

    assertThat(page.getContent())
        .extracting(PortalActivityEventResponse::eventType)
        .contains("portal.request_item.submitted", "invoice.sent", "statement.generated")
        .doesNotContain("time_entry.created", "disbursement.billed");
  }
}
