package io.b2mash.b2b.b2bstrawman.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalAcceptanceView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates.AllAcceptanceRequestsFinalGate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 88 Epic 624F (U23, G-B19): a portal contact declines an acceptance request end to end on
 * the real path — legal-za engagement letter generated with {@code saveToDocuments}, sent through
 * the firm API, declined through the permitAll portal endpoint. Proves the tenant row (DECLINED,
 * {@code declined_at}, {@code decline_reason}), the {@code acceptance.declined} audit row with the
 * ADR-349 {@code project_id}, the sender's {@code ACCEPTANCE_DECLINED} notification, the portal
 * read-model sync, re-send after decline (A12), the 409s and the closure gate.
 *
 * <p>Tenant state is asserted through JDBC and HTTP so the class compiles against the pre-624F tree
 * (failing-first). Every assertion is id-scoped (count-bleed rule).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcceptanceDeclineIntegrationTest {

  private static final String ORG_ID = "org_acceptance_decline";
  private static final String OWNER = "user_acc_decline_owner";
  private static final String CONTACT_NAME = "Sipho Dlamini";

  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private AcceptanceRequestRepository acceptanceRequestRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private AuditService auditService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private PortalReadModelRepository portalReadModelRepository;
  @Autowired private AllAcceptanceRequestsFinalGate allAcceptanceRequestsFinalGate;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID templateId;
  private String templateName;
  private UUID customerId;
  private UUID projectId;
  private UUID portalContactId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Acceptance Decline Org", "legal-za")
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "acc_decline_owner@test.com", "Thandi Owner", "owner"));

    inTenant(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var template =
                      documentTemplateRepository
                          .findPackSeeded("legal-za", "engagement-letter-general")
                          .getFirst();
                  templateId = template.getId();
                  templateName = template.getName();
                  var matter = createMatter("Dlamini property", "sipho.decline@test.com");
                  customerId = matter.customerId();
                  projectId = matter.projectId();
                  portalContactId = matter.contactId();
                  return null;
                }));
  }

  @BeforeEach
  void purgeMail() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  @Test
  void declineFromPortal_setsDeclinedState_andReturns200() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);

    decline(sent.token(), "{\"reason\": \"Price too high\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.declinedAt").exists());

    var row = requestRow(sent.requestId());
    assertThat(row.get("status")).isEqualTo("DECLINED");
    assertThat(row.get("declined_at")).isNotNull();
    assertThat(row.get("decline_reason")).isEqualTo("Price too high");
  }

  @Test
  void decline_writesAuditWithProjectIdCustomerIdAndReason() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);

    decline(sent.token(), "{\"reason\": \"Price too high\"}").andExpect(status().isOk());

    var event = declinedAudit(sent.requestId());
    assertThat(event.getDetails())
        .containsEntry("project_id", projectId.toString())
        .containsEntry("customer_id", customerId.toString())
        .containsEntry("decline_reason", "Price too high")
        .containsKey("ip_address");
    assertThat(event.getActorType()).isEqualTo("PORTAL_CONTACT");
    assertThat(event.getActorId()).isEqualTo(portalContactId);
    assertThat(event.getSource()).isEqualTo("PORTAL");
  }

  /** D-18 fallback: a CUSTOMER-primary document resolves its matter via documents.project_id. */
  @Test
  void decline_auditProjectIdFallsBackToSavedDocumentMatter() throws Exception {
    UUID generatedDocumentId =
        inTenant(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var saved =
                          documentRepository.save(
                              new Document(
                                  projectId,
                                  "fallback-letter.pdf",
                                  "application/pdf",
                                  10L,
                                  memberId));
                      var generated =
                          new GeneratedDocument(
                              templateId,
                              TemplateEntityType.CUSTOMER,
                              customerId,
                              "fallback-letter.pdf",
                              "test/fallback-letter.pdf",
                              10L,
                              memberId);
                      generated.linkToDocument(saved.getId());
                      return generatedDocumentRepository.save(generated).getId();
                    }));
    var sent = send(generatedDocumentId, portalContactId);

    decline(sent.token(), "{}").andExpect(status().isOk());

    assertThat(declinedAudit(sent.requestId()).getDetails())
        .containsEntry("project_id", projectId.toString())
        .containsEntry("customer_id", customerId.toString());
  }

  /** ADR-349 / 625A convention: no matter → {@code project_id = ""}. */
  @Test
  void decline_auditProjectIdIsEmptyWhenDocumentHasNoMatter() throws Exception {
    UUID generatedDocumentId =
        inTenant(
            () ->
                transactionTemplate.execute(
                    tx ->
                        generatedDocumentRepository
                            .save(
                                new GeneratedDocument(
                                    templateId,
                                    TemplateEntityType.CUSTOMER,
                                    customerId,
                                    "client-only-letter.pdf",
                                    "test/client-only-letter.pdf",
                                    10L,
                                    memberId))
                            .getId()));
    var sent = send(generatedDocumentId, portalContactId);

    decline(sent.token(), "{}").andExpect(status().isOk());

    assertThat(declinedAudit(sent.requestId()).getDetails())
        .containsEntry("project_id", "")
        .containsEntry("decline_reason", "");
  }

  @Test
  void decline_notifiesSender() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);

    decline(sent.token(), "{\"reason\": \"Price too high\"}").andExpect(status().isOk());

    var notification = declinedNotification(sent.requestId());
    assertThat(notification.getRecipientMemberId()).isEqualTo(memberId);
    assertThat(notification.getTitle())
        .isEqualTo(CONTACT_NAME + " declined " + templateName + ": Price too high");
    assertThat(notification.getReferenceEntityType()).isEqualTo("ACCEPTANCE_REQUEST");
    assertThat(notification.getReferenceProjectId()).isEqualTo(projectId);
  }

  @Test
  void decline_withoutReason_notificationTitleHasNoReasonSuffix() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);

    decline(sent.token(), "{}").andExpect(status().isOk());

    assertThat(declinedNotification(sent.requestId()).getTitle())
        .isEqualTo(CONTACT_NAME + " declined " + templateName);
  }

  /** D-6: notifications.title is varchar(500); a 2000-char reason is abbreviated in the title. */
  @Test
  void decline_longReason_titleIsTruncatedToColumnWidth_reasonKeptInFull() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);
    String reason = "x".repeat(2000);

    decline(sent.token(), "{\"reason\": \"" + reason + "\"}").andExpect(status().isOk());

    var notification = declinedNotification(sent.requestId());
    assertThat(notification.getTitle())
        .hasSizeLessThanOrEqualTo(500)
        .startsWith(CONTACT_NAME + " declined " + templateName + ": xxx")
        .endsWith("…");
    assertThat(notification.getBody()).isEqualTo(reason);
    assertThat(requestRow(sent.requestId()).get("decline_reason")).isEqualTo(reason);
  }

  @Test
  void decline_syncsPortalReadModel() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);

    decline(sent.token(), "{\"reason\": \"Price too high\"}").andExpect(status().isOk());

    assertThat(portalReadModelRepository.findByRequestToken(sent.token()))
        .get()
        .extracting(PortalAcceptanceView::status)
        .isEqualTo("DECLINED");
    assertThat(portalReadModelRepository.findPendingAcceptancesByContactId(portalContactId))
        .extracting(PortalAcceptanceView::id)
        .doesNotContain(sent.requestId());
  }

  /** A12: DECLINED is outside idx_acceptance_requests_active_unique, so the pair is re-sendable. */
  @Test
  void resendAfterDecline_createsNewSentRequest() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);
    decline(sent.token(), "{\"reason\": \"Price too high\"}").andExpect(status().isOk());

    var resent = send(sent.generatedDocumentId(), portalContactId);

    assertThat(resent.requestId()).isNotEqualTo(sent.requestId());
    assertThat(requestRow(resent.requestId()).get("status")).isEqualTo("SENT");
    assertThat(requestRow(sent.requestId()).get("status")).isEqualTo("DECLINED");
  }

  @Test
  void secondDecline_returns409() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);
    decline(sent.token(), "{\"reason\": \"Price too high\"}").andExpect(status().isOk());

    expectCannotDecline(decline(sent.token(), "{\"reason\": \"again\"}"));
    assertThat(requestRow(sent.requestId()).get("decline_reason")).isEqualTo("Price too high");
  }

  @Test
  void declineAfterAccept_returns409_andStaysAccepted() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);
    mockMvc
        .perform(
            post("/api/portal/acceptance/" + sent.token() + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Sipho Dlamini\"}"))
        .andExpect(status().isOk());

    expectCannotDecline(decline(sent.token(), "{}"));
    assertThat(requestRow(sent.requestId()).get("status")).isEqualTo("ACCEPTED");
  }

  /** D-4: accept after decline is rejected (existing 400) and the row stays DECLINED. */
  @Test
  void acceptAfterDecline_isRejected_andStaysDeclined() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);
    decline(sent.token(), "{}").andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/portal/acceptance/" + sent.token() + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Sipho Dlamini\"}"))
        .andExpect(status().isBadRequest());

    var row = requestRow(sent.requestId());
    assertThat(row.get("status")).isEqualTo("DECLINED");
    assertThat(row.get("accepted_at")).isNull();
  }

  @Test
  void declineRevoked_returns409() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);
    mockMvc
        .perform(
            post("/api/acceptance-requests/" + sent.requestId() + "/revoke")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk());

    expectCannotDecline(decline(sent.token(), "{}"));
    assertThat(requestRow(sent.requestId()).get("status")).isEqualTo("REVOKED");
  }

  /** D-2: a SENT request past expires_at is not declinable and is not flipped by the decline. */
  @Test
  void declineExpiredByTime_returns409_withoutSideEffect() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);
    jdbcTemplate.update(
        "UPDATE \"%s\".acceptance_requests SET expires_at = now() - interval '1 day' WHERE id = ?"
            .formatted(tenantSchema),
        sent.requestId());

    expectCannotDecline(decline(sent.token(), "{}"));
    var row = requestRow(sent.requestId());
    assertThat(row.get("status")).isEqualTo("SENT");
    assertThat(row.get("declined_at")).isNull();
  }

  @Test
  void blankReason_isStoredAsNull() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);

    decline(sent.token(), "{\"reason\": \"   \"}").andExpect(status().isOk());

    assertThat(requestRow(sent.requestId()).get("decline_reason")).isNull();
    assertThat(declinedAudit(sent.requestId()).getDetails()).containsEntry("decline_reason", "");
  }

  @Test
  void reasonOver2000_returns400_andStateUnchanged() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);

    decline(sent.token(), "{\"reason\": \"" + "x".repeat(2001) + "\"}")
        .andExpect(status().isBadRequest());

    assertThat(requestRow(sent.requestId()).get("status")).isEqualTo("SENT");
  }

  @Test
  void unknownToken_returns404() throws Exception {
    decline("no-such-token-" + UUID.randomUUID(), "{}").andExpect(status().isNotFound());
  }

  @Test
  void getPageData_forDeclinedToken_returnsDeclinedStatus_withoutViewSideEffect() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);
    decline(sent.token(), "{}").andExpect(status().isOk());

    mockMvc
        .perform(get("/api/portal/acceptance/" + sent.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.declinedAt").exists());

    assertThat(requestRow(sent.requestId()).get("viewed_at")).isNull();
  }

  /** Firm list (624G): DECLINED rows reach the by-customer list with declinedAt/declineReason. */
  @Test
  void firmListByCustomer_includesDeclinedWithReason() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);
    decline(sent.token(), "{\"reason\": \"Price too high\"}").andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/acceptance-requests")
                .param("customerId", customerId.toString())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.id == '%s')].status".formatted(sent.requestId())).value("DECLINED"))
        .andExpect(
            jsonPath("$[?(@.id == '%s')].declineReason".formatted(sent.requestId()))
                .value("Price too high"))
        .andExpect(jsonPath("$[?(@.id == '%s')].declinedAt".formatted(sent.requestId())).exists());
  }

  /** D-8: declining sends no client email (and no firm email — member email prefs default off). */
  @Test
  void decline_sendsNoEmail() throws Exception {
    var sent = generateAndSend(projectId, portalContactId);
    greenMail.purgeEmailFromAllMailboxes();

    decline(sent.token(), "{\"reason\": \"Price too high\"}").andExpect(status().isOk());

    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }

  /** A12: a matter whose only acceptance request is DECLINED passes the closure gate. */
  @Test
  void closureGatePassesAfterDecline() throws Exception {
    var matter =
        inTenant(
            () ->
                transactionTemplate.execute(
                    tx -> createMatter("Gate matter", "gate.decline@test.com")));
    var sent = generateAndSend(matter.projectId(), matter.contactId());

    assertThat(gatePassed(matter.projectId())).isFalse();

    decline(sent.token(), "{}").andExpect(status().isOk());

    assertThat(gatePassed(matter.projectId())).isTrue();
  }

  // --- helpers ---

  private record Matter(UUID customerId, UUID projectId, UUID contactId) {}

  private record Sent(UUID generatedDocumentId, UUID requestId, String token) {}

  /** Must run inside tenant scope + transaction. */
  private Matter createMatter(String matterName, String email) {
    var customer =
        customerRepository.save(
            TestCustomerFactory.createActiveCustomer(CONTACT_NAME, email, memberId));
    var project = new Project(matterName, "Decline matter", memberId);
    project.setCustomerId(customer.getId());
    project = projectRepository.save(project);
    customerProjectRepository.save(
        new CustomerProject(customer.getId(), project.getId(), memberId));
    var contact =
        portalContactRepository.save(
            new PortalContact(
                ORG_ID,
                customer.getId(),
                "portal." + email,
                CONTACT_NAME,
                PortalContact.ContactRole.PRIMARY));
    return new Matter(customer.getId(), project.getId(), contact.getId());
  }

  private Sent generateAndSend(UUID matterId, UUID contactId) throws Exception {
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
    UUID generatedDocumentId =
        UUID.fromString(JsonPath.read(gen.getResponse().getContentAsString(), "$.id"));
    return send(generatedDocumentId, contactId);
  }

  private Sent send(UUID generatedDocumentId, UUID contactId) throws Exception {
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
            .andExpect(jsonPath("$.status").value("SENT"))
            .andReturn();
    UUID requestId =
        UUID.fromString(JsonPath.read(sent.getResponse().getContentAsString(), "$.id"));
    String token =
        inTenant(() -> acceptanceRequestRepository.findById(requestId).orElseThrow())
            .getRequestToken();
    return new Sent(generatedDocumentId, requestId, token);
  }

  private ResultActions decline(String token, String body) throws Exception {
    return mockMvc.perform(
        post("/api/portal/acceptance/" + token + "/decline")
            .contentType(MediaType.APPLICATION_JSON)
            .header("User-Agent", "Decline-Test/1.0")
            .content(body));
  }

  private void expectCannotDecline(ResultActions result) throws Exception {
    result
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Invalid status transition"))
        .andExpect(jsonPath("$.detail").value("This request can no longer be declined"));
  }

  private Map<String, Object> requestRow(UUID requestId) {
    return jdbcTemplate.queryForMap(
        ("SELECT status, declined_at, decline_reason, viewed_at, accepted_at"
                + " FROM \"%s\".acceptance_requests WHERE id = ?")
            .formatted(tenantSchema),
        requestId);
  }

  private AuditEvent declinedAudit(UUID requestId) {
    return inTenant(
        () -> {
          var page =
              auditService.findEvents(
                  new AuditEventFilter(
                      "acceptance_request",
                      requestId,
                      null,
                      "acceptance.declined",
                      null,
                      null,
                      null),
                  PageRequest.of(0, 10));
          assertThat(page.getContent()).hasSize(1);
          return page.getContent().getFirst();
        });
  }

  private Notification declinedNotification(UUID requestId) {
    return inTenant(
        () -> {
          assertThat(
                  notificationRepository.existsByTypeAndReferenceEntityId(
                      "ACCEPTANCE_DECLINED", requestId))
              .isTrue();
          return notificationRepository
              .findByRecipientMemberId(memberId, PageRequest.of(0, 200))
              .getContent()
              .stream()
              .filter(
                  n ->
                      "ACCEPTANCE_DECLINED".equals(n.getType())
                          && requestId.equals(n.getReferenceEntityId()))
              .findFirst()
              .orElseThrow();
        });
  }

  private boolean gatePassed(UUID matterId) {
    return inTenant(
        () ->
            transactionTemplate.execute(
                tx ->
                    allAcceptanceRequestsFinalGate
                        .evaluate(projectRepository.findById(matterId).orElseThrow())
                        .passed()));
  }

  private <T> T inTenant(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(action::get);
  }
}
