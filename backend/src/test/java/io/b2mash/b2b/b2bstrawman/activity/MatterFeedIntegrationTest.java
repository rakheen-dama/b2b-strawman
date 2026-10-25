package io.b2mash.b2b.b2bstrawman.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceRequestRepository;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 88 625A (U5, ADR-349): the matter Activity feed holds every matter-scoped publisher's rows
 * on the REAL write paths — engagement letter sent / viewed / accepted (and a second one declined)
 * through the firm + portal APIs, trust deposit + payment recorded and the payment approved by a
 * second member, fee note created from unbilled time → approved → sent → part-paid → paid (622
 * postPayment). Reads back through {@code GET /api/matters/{id}/activity} and the stored details of
 * {@code findByProjectId}. Every assertion is scoped to the entities the test created (count-bleed
 * rule).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatterFeedIntegrationTest {

  private static final String ORG_ID = "org_matter_feed_625a";
  private static final String OWNER = "user_mf_owner";
  private static final String APPROVER = "user_mf_approver";
  private static final String CONTACT_NAME = "Sipho Dlamini";

  // Acceptance and fee-note sends email best-effort; bind the JVM singleton (never start our own).
  @SuppressWarnings("unused")
  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private AcceptanceRequestRepository acceptanceRequestRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private AuditEventRepository auditEventRepository;

  private String tenantSchema;
  private UUID ownerId;
  private UUID templateId;
  private String trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    // legal-za: the engagement-letter-general template (primaryEntityType PROJECT) and the
    // trust_accounting module come with the profile.
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Matter Feed Firm", "legal-za").schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "mf_owner@test.com", "Thandi Owner", "owner"));
    // Single-approval mode forbids the recorder approving their own trust payment.
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, APPROVER, "mf_approver@test.com", "Bob Approver", "owner");

    templateId =
        inTenant(
            () ->
                documentTemplateRepository
                    .findByPackIdAndPackTemplateKey("legal-za", "engagement-letter-general")
                    .orElseThrow()
                    .getId());

    var account =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Matter Feed Trust",
                          "bankName": "FNB",
                          "branchCode": "250655",
                          "accountNumber": "62625000001",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = id(account);
  }

  @Test
  void engagementLetterSentViewedAccepted_landOnTheMatterFeed_withPortalActor() throws Exception {
    var m = newMatter("Estate Late Dlamini — acceptance", "mf.acc@test.com");
    var sent = sendForAcceptance(generateLetter(m.projectId()), m.contactId());

    mockMvc.perform(get("/api/portal/acceptance/" + sent.token())).andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/portal/acceptance/" + sent.token() + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + CONTACT_NAME + "\"}"))
        .andExpect(status().isOk());

    var rows = feedRows(m.projectId(), "acceptance_request", sent.requestId());
    assertThat(rows)
        .extracting(AuditEvent::getEventType)
        .contains(
            "acceptance.created", "acceptance.sent", "acceptance.viewed", "acceptance.accepted");
    assertThat(rows)
        .allSatisfy(
            e ->
                assertThat(e.getDetails())
                    .containsEntry("project_id", m.projectId().toString())
                    .containsEntry("customer_id", m.customerId().toString()));
    assertThat(rows)
        .filteredOn(
            e ->
                "acceptance.viewed".equals(e.getEventType())
                    || "acceptance.accepted".equals(e.getEventType()))
        .allSatisfy(e -> assertThat(e.getActorId()).isEqualTo(m.contactId()));

    // Through the HTTP feed: the portal contact (not "System") is the actor of viewed / accepted.
    feedJson(m.projectId())
        .andExpect(
            jsonPath("$.content[*].eventType")
                .value(hasItems("acceptance.sent", "acceptance.viewed", "acceptance.accepted")))
        .andExpect(
            jsonPath("$.content[?(@.eventType == 'acceptance.accepted')].actorName")
                .value(hasItem(CONTACT_NAME)))
        .andExpect(
            jsonPath("$.content[?(@.eventType == 'acceptance.viewed')].actorName")
                .value(hasItem(CONTACT_NAME)));
  }

  @Test
  void engagementLetterDeclined_landsOnTheMatterFeed() throws Exception {
    var m = newMatter("Estate Late Dlamini — decline", "mf.dec@test.com");
    var sent = sendForAcceptance(generateLetter(m.projectId()), m.contactId());

    mockMvc
        .perform(
            post("/api/portal/acceptance/" + sent.token() + "/decline")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Fees too high\"}"))
        .andExpect(status().isOk());

    var declined =
        feedRows(m.projectId(), "acceptance_request", sent.requestId()).stream()
            .filter(e -> "acceptance.declined".equals(e.getEventType()))
            .toList();
    assertThat(declined).hasSize(1);
    assertThat(declined.getFirst().getDetails())
        .containsEntry("project_id", m.projectId().toString())
        .containsEntry("customer_id", m.customerId().toString())
        .containsEntry("decline_reason", "Fees too high");
  }

  @Test
  void trustDepositAndApprovedPayment_landOnTheMatterFeed() throws Exception {
    var m = newMatter("Estate Late Dlamini — trust", "mf.trust@test.com");
    UUID depositId = trust("deposit", m, "50000.00", "DEP-MF-1"); // RECORDED; no approval step
    UUID paymentId = trust("payment", m, "2500.00", "PAY-MF-1"); // AWAITING_APPROVAL
    mockMvc
        .perform(
            post("/api/trust-transactions/" + paymentId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, APPROVER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    var deposit = feedRows(m.projectId(), "trust_transaction", depositId);
    assertThat(deposit).extracting(AuditEvent::getEventType).contains("trust_deposit.recorded");
    var payment = feedRows(m.projectId(), "trust_transaction", paymentId);
    assertThat(payment)
        .extracting(AuditEvent::getEventType)
        .contains("trust_payment.recorded", "trust_payment.approved");
    assertThat(java.util.stream.Stream.concat(deposit.stream(), payment.stream()).toList())
        .allSatisfy(
            e ->
                assertThat(e.getDetails())
                    .containsEntry("project_id", m.projectId().toString())
                    .containsEntry("customer_id", m.customerId().toString()));

    feedJson(m.projectId())
        .andExpect(
            jsonPath("$.content[*].eventType")
                .value(
                    hasItems(
                        "trust_deposit.recorded",
                        "trust_payment.recorded",
                        "trust_payment.approved")));
  }

  @Test
  void feeNoteCreatedApprovedSentPartPaidAndPaid_landOnTheMatterFeed() throws Exception {
    var m = newMatter("Estate Late Dlamini — fee note", "mf.inv@test.com");
    UUID timeEntryId = billableTimeEntry(m.projectId());
    String invoiceId = createDraft(m.customerId(), timeEntryId);
    postOk("/api/invoices/" + invoiceId + "/approve", null);
    var sent = postOk("/api/invoices/" + invoiceId + "/send", "{\"overrideWarnings\": true}");
    var total =
        new BigDecimal(
            JsonPath.read(sent.getResponse().getContentAsString(), "$.total").toString());
    var part = new BigDecimal("100.00");
    postOk(
        "/api/invoices/" + invoiceId + "/payment",
        "{\"amount\": %s, \"paymentMethod\": \"EFT\", \"paymentReference\": \"MF-PART-1\"}"
            .formatted(part.toPlainString()));
    postOk(
        "/api/invoices/" + invoiceId + "/payment",
        "{\"amount\": %s, \"paymentMethod\": \"EFT\", \"paymentReference\": \"MF-REST-1\"}"
            .formatted(total.subtract(part).setScale(2).toPlainString()));

    var rows = feedRows(m.projectId(), "invoice", UUID.fromString(invoiceId));
    assertThat(rows)
        .extracting(AuditEvent::getEventType)
        .contains(
            "invoice.created",
            "invoice.approved",
            "invoice.sent",
            "invoice.partially_paid",
            "invoice.paid");
    assertThat(rows)
        .allSatisfy(
            e ->
                assertThat(e.getDetails())
                    .containsEntry("project_id", m.projectId().toString())
                    .containsEntry("customer_id", m.customerId().toString()));

    feedJson(m.projectId())
        .andExpect(
            jsonPath("$.content[*].eventType")
                .value(hasItems("invoice.created", "invoice.approved", "invoice.sent")));
  }

  // --- helpers ---

  private record Matter(UUID customerId, UUID projectId, UUID contactId) {}

  private record Sent(UUID requestId, String token) {}

  /** ACTIVE client with prerequisite fields + matter + client link + primary portal contact. */
  private Matter newMatter(String name, String email) {
    return inTenant(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              CONTACT_NAME, email, ownerId));
                  var project = new Project(name, "625A matter feed", ownerId);
                  project.setCustomerId(customer.getId());
                  project = projectRepository.save(project);
                  customerProjectRepository.save(
                      new CustomerProject(customer.getId(), project.getId(), ownerId));
                  var contact =
                      portalContactRepository.save(
                          new PortalContact(
                              ORG_ID,
                              customer.getId(),
                              "portal." + email,
                              CONTACT_NAME,
                              PortalContact.ContactRole.PRIMARY));
                  return new Matter(customer.getId(), project.getId(), contact.getId());
                }));
  }

  private UUID generateLetter(UUID matterId) throws Exception {
    var gen =
        mockMvc
            .perform(
                post("/api/templates/" + templateId + "/generate")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"entityId": "%s", "saveToDocuments": true, "acknowledgeWarnings": true}
                        """
                            .formatted(matterId)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(id(gen));
  }

  private Sent sendForAcceptance(UUID generatedDocumentId, UUID contactId) throws Exception {
    var sent =
        mockMvc
            .perform(
                post("/api/acceptance-requests")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"generatedDocumentId": "%s", "portalContactId": "%s"}
                        """
                            .formatted(generatedDocumentId, contactId)))
            .andExpect(status().isCreated())
            .andReturn();
    UUID requestId = UUID.fromString(id(sent));
    String token =
        inTenant(() -> acceptanceRequestRepository.findById(requestId).orElseThrow())
            .getRequestToken();
    return new Sent(requestId, token);
  }

  private UUID trust(String kind, Matter m, String amount, String reference) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/" + kind)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clientId": "%s",
                          "matterId": "%s",
                          "amount": %s,
                          "reference": "%s",
                          "description": "625A matter feed",
                          "transactionDate": "2026-10-01"
                        }
                        """
                            .formatted(m.customerId(), m.projectId(), amount, reference)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(id(result));
  }

  private UUID billableTimeEntry(UUID projectId) {
    return inTenant(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var task =
                      taskRepository.save(
                          new Task(
                              projectId,
                              "Estate work",
                              "625A feed task",
                              "MEDIUM",
                              "TASK",
                              null,
                              ownerId));
                  var te =
                      new TimeEntry(
                          task.getId(),
                          ownerId,
                          LocalDate.of(2026, 10, 1),
                          120,
                          true,
                          null,
                          "Drafting");
                  te.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                  return timeEntryRepository.save(te).getId();
                }));
  }

  private String createDraft(UUID customerId, UUID timeEntryId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clientId": "%s", "currency": "ZAR", "timeEntryIds": ["%s"]}
                        """
                            .formatted(customerId, timeEntryId)))
            .andExpect(status().isCreated())
            .andReturn();
    return id(result);
  }

  private MvcResult postOk(String url, String body) throws Exception {
    var request = post(url).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER));
    if (body != null) {
      request = request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
  }

  /** Stored rows of ONE entity in the matter feed query (id-scoped). */
  private List<AuditEvent> feedRows(UUID projectId, String entityType, UUID entityId) {
    return inTenant(
        () ->
            auditEventRepository
                .findByProjectId(projectId.toString(), null, null, PageRequest.of(0, 200))
                .getContent()
                .stream()
                .filter(
                    e -> entityType.equals(e.getEntityType()) && entityId.equals(e.getEntityId()))
                .toList());
  }

  private ResultActions feedJson(UUID projectId) throws Exception {
    return mockMvc
        .perform(
            get("/api/matters/" + projectId + "/activity?size=50")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk());
  }

  private static String id(MvcResult result) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private <T> T inTenant(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(action::get);
  }
}
