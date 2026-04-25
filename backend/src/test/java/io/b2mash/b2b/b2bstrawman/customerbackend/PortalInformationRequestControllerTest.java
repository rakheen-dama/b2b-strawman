package io.b2mash.b2b.b2bstrawman.customerbackend;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestService;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestItem;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestItemRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.ResponseType;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortalInformationRequestControllerTest {

  private static final String ORG_ID = "org_portal_info_req";
  private static final String CLERK_USER_ID = "user_portal_info_req";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private InformationRequestService informationRequestService;
  @Autowired private InformationRequestRepository requestRepository;
  @Autowired private RequestItemRepository itemRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID customerIdB;
  private UUID portalContactId;
  private UUID portalContactIdB;
  private UUID projectId;

  // Request and item IDs populated during setup
  private UUID sentRequestId;
  private UUID fileItemId;
  private UUID textItemId;
  private UUID acceptedItemId;

  // For customer-scoped request (no project)
  private UUID customerScopedRequestId;
  private UUID customerScopedItemId;

  @BeforeAll
  void setup() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Portal Info Request Org", null).schemaName();

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, CLERK_USER_ID, "portal-info-req@test.com", "Portal Info Owner", null, "admin");
    memberId = syncResult.memberId();

    runInTenantWithMember(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Customer A -- owns the requests
                  var custA =
                      createActiveCustomer(
                          "Info Request Customer A", "info-req-a@test.com", memberId);
                  custA = customerRepository.save(custA);
                  customerId = custA.getId();

                  // Customer B -- different customer for isolation tests
                  var custB =
                      createActiveCustomer(
                          "Info Request Customer B", "info-req-b@test.com", memberId);
                  custB = customerRepository.save(custB);
                  customerIdB = custB.getId();

                  var project = new Project("Info Request Project", "Test project", memberId);
                  project = projectRepository.save(project);
                  projectId = project.getId();

                  // Portal contacts
                  var contactA =
                      new PortalContact(
                          ORG_ID,
                          customerId,
                          "info-req-a@test.com",
                          "Contact A",
                          PortalContact.ContactRole.PRIMARY);
                  contactA = portalContactRepository.save(contactA);
                  portalContactId = contactA.getId();

                  var contactB =
                      new PortalContact(
                          ORG_ID,
                          customerIdB,
                          "info-req-b@test.com",
                          "Contact B",
                          PortalContact.ContactRole.PRIMARY);
                  contactB = portalContactRepository.save(contactB);
                  portalContactIdB = contactB.getId();

                  // ── Request 1: project-scoped, SENT, with file + text items ──
                  var request =
                      new InformationRequest("PIR-001", customerId, portalContactId, memberId);
                  request.setProjectId(projectId);
                  request = requestRepository.save(request);
                  sentRequestId = request.getId();

                  var fileItem =
                      new RequestItem(
                          sentRequestId,
                          "Tax Certificate",
                          "Upload your tax cert",
                          ResponseType.FILE_UPLOAD,
                          true,
                          ".pdf,.jpg",
                          0);
                  fileItem = itemRepository.save(fileItem);
                  fileItemId = fileItem.getId();

                  var textItem =
                      new RequestItem(
                          sentRequestId,
                          "Notes",
                          "Additional notes",
                          ResponseType.TEXT_RESPONSE,
                          false,
                          null,
                          1);
                  textItem = itemRepository.save(textItem);
                  textItemId = textItem.getId();

                  // An item that will be pre-accepted for testing "submit on ACCEPTED" rejection
                  var acceptedItem =
                      new RequestItem(
                          sentRequestId,
                          "Already Accepted",
                          "This one is done",
                          ResponseType.FILE_UPLOAD,
                          false,
                          null,
                          2);
                  acceptedItem = itemRepository.save(acceptedItem);
                  acceptedItem.submit(UUID.randomUUID());
                  acceptedItem.accept(memberId);
                  acceptedItem = itemRepository.save(acceptedItem);
                  acceptedItemId = acceptedItem.getId();

                  // ── Request 2: customer-scoped (no project), SENT ──
                  var custRequest =
                      new InformationRequest("PIR-002", customerId, portalContactId, memberId);
                  custRequest = requestRepository.save(custRequest);
                  customerScopedRequestId = custRequest.getId();

                  var custItem =
                      new RequestItem(
                          customerScopedRequestId,
                          "ID Document",
                          "Upload your ID",
                          ResponseType.FILE_UPLOAD,
                          true,
                          ".pdf",
                          0);
                  custItem = itemRepository.save(custItem);
                  customerScopedItemId = custItem.getId();
                }));

    // Send both requests (triggers domain events -> read-model sync)
    runInTenantWithMember(
        () -> {
          informationRequestService.send(sentRequestId);
          informationRequestService.send(customerScopedRequestId);
        });
  }

  // ── 1. List requests by portal contact ──────────────────────────────

  @Test
  @Order(1)
  void shouldListRequestsForPortalContact() throws Exception {
    String token = tokenForCustomerA();

    mockMvc
        .perform(get("/portal/requests").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].requestNumber").isNotEmpty())
        .andExpect(jsonPath("$[0].status").value("SENT"));
  }

  // ── 2. Get request detail with items ────────────────────────────────

  @Test
  @Order(2)
  void shouldGetRequestDetailWithItems() throws Exception {
    String token = tokenForCustomerA();

    mockMvc
        .perform(
            get("/portal/requests/{id}", sentRequestId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(sentRequestId.toString()))
        .andExpect(jsonPath("$.requestNumber").value("PIR-001"))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.projectName").value("Info Request Project"))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].name").value("Tax Certificate"))
        .andExpect(jsonPath("$.items[0].responseType").value("FILE_UPLOAD"))
        .andExpect(jsonPath("$.items[0].required").value(true))
        .andExpect(jsonPath("$.items[1].name").value("Notes"));
  }

  // ── 3. Upload initiation (project-scoped) ──────────────────────────

  @Test
  @Order(3)
  void shouldInitiateUploadAndReturnPresignedUrl() throws Exception {
    String token = tokenForCustomerA();

    var result =
        mockMvc
            .perform(
                post("/portal/requests/{id}/items/{itemId}/upload", sentRequestId, fileItemId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "tax-cert.pdf", "contentType": "application/pdf", "size": 2048}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.documentId").isNotEmpty())
            .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
            .andExpect(jsonPath("$.expiresAt").isNotEmpty())
            .andReturn();

    // Verify document was created in DB
    String docIdStr = JsonPath.read(result.getResponse().getContentAsString(), "$.documentId");
    UUID documentId = UUID.fromString(docIdStr);

    runInTenantWithMember(
        () -> {
          var doc = documentRepository.findById(documentId).orElseThrow();
          assertThat(doc.getScope()).isEqualTo(Document.Scope.PROJECT);
          assertThat(doc.getVisibility()).isEqualTo(Document.Visibility.SHARED);
          assertThat(doc.getFileName()).isEqualTo("tax-cert.pdf");
          assertThat(doc.getProjectId()).isEqualTo(projectId);
        });

    // GAP-L-75c: portal.document.upload_initiated audit row with PORTAL_CONTACT actor
    runInTenantWithMember(
        () -> {
          var events =
              auditEventRepository
                  .findByFilter(
                      "document",
                      documentId,
                      null,
                      "portal.document.upload_initiated",
                      null,
                      null,
                      org.springframework.data.domain.PageRequest.of(0, 10))
                  .getContent();
          assertThat(events).isNotEmpty();
          var auditEvent = events.get(0);
          assertThat(auditEvent.getActorType()).isEqualTo("PORTAL_CONTACT");
          assertThat(auditEvent.getActorId()).isEqualTo(portalContactId);
          assertThat(auditEvent.getSource()).isEqualTo("PORTAL");
          assertThat(auditEvent.getDetails().get("project_id")).isEqualTo(projectId.toString());
          assertThat(auditEvent.getDetails().get("file_name")).isEqualTo("tax-cert.pdf");
        });
  }

  // ── 4. Submit file (PENDING -> SUBMITTED) ──────────────────────────

  @Test
  @Order(4)
  void shouldSubmitFileResponse() throws Exception {
    String token = tokenForCustomerA();

    // First initiate upload to get a documentId
    var uploadResult =
        mockMvc
            .perform(
                post("/portal/requests/{id}/items/{itemId}/upload", sentRequestId, fileItemId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "tax-cert-v2.pdf", "contentType": "application/pdf", "size": 1024}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String docIdStr =
        JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.documentId");

    // Submit the item with the document
    mockMvc
        .perform(
            post("/portal/requests/{id}/items/{itemId}/submit", sentRequestId, fileItemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"documentId": "%s"}
                    """
                        .formatted(docIdStr)))
        .andExpect(status().isOk());

    // Verify item status changed
    runInTenantWithMember(
        () -> {
          var item = itemRepository.findById(fileItemId).orElseThrow();
          assertThat(item.getStatus().name()).isEqualTo("SUBMITTED");
          assertThat(item.getDocumentId()).isEqualTo(UUID.fromString(docIdStr));
        });

    // GAP-L-75c: portal.request_item.submitted audit row with PORTAL_CONTACT actor + project_id
    runInTenantWithMember(
        () -> {
          var events =
              auditEventRepository
                  .findByFilter(
                      "request_item",
                      fileItemId,
                      null,
                      "portal.request_item.submitted",
                      null,
                      null,
                      org.springframework.data.domain.PageRequest.of(0, 10))
                  .getContent();
          assertThat(events).isNotEmpty();
          var auditEvent = events.get(0);
          assertThat(auditEvent.getActorType()).isEqualTo("PORTAL_CONTACT");
          assertThat(auditEvent.getActorId()).isEqualTo(portalContactId);
          assertThat(auditEvent.getSource()).isEqualTo("PORTAL");
          assertThat(auditEvent.getDetails().get("project_id")).isEqualTo(projectId.toString());
          assertThat(auditEvent.getDetails().get("response_type")).isEqualTo("FILE");
        });
  }

  // ── 5. Submit text response ─────────────────────────────────────────

  @Test
  @Order(5)
  void shouldSubmitTextResponse() throws Exception {
    String token = tokenForCustomerA();

    mockMvc
        .perform(
            post("/portal/requests/{id}/items/{itemId}/submit", sentRequestId, textItemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"textResponse": "Here are my additional notes for the request."}
                    """))
        .andExpect(status().isOk());

    // Verify item status
    runInTenantWithMember(
        () -> {
          var item = itemRepository.findById(textItemId).orElseThrow();
          assertThat(item.getStatus().name()).isEqualTo("SUBMITTED");
          assertThat(item.getTextResponse())
              .isEqualTo("Here are my additional notes for the request.");
        });

    // GAP-L-75c: portal.request_item.submitted audit row tagged response_type=TEXT
    runInTenantWithMember(
        () -> {
          var events =
              auditEventRepository
                  .findByFilter(
                      "request_item",
                      textItemId,
                      null,
                      "portal.request_item.submitted",
                      null,
                      null,
                      org.springframework.data.domain.PageRequest.of(0, 10))
                  .getContent();
          assertThat(events).isNotEmpty();
          var auditEvent = events.get(0);
          assertThat(auditEvent.getActorType()).isEqualTo("PORTAL_CONTACT");
          assertThat(auditEvent.getDetails().get("response_type")).isEqualTo("TEXT");
        });
  }

  // ── 6. Re-submit after rejection (REJECTED -> SUBMITTED) ───────────

  @Test
  @Order(6)
  void shouldReSubmitAfterRejection() throws Exception {
    // Reject the file item first (via internal service)
    runInTenantWithMember(
        () -> informationRequestService.rejectItem(sentRequestId, fileItemId, "Wrong document"));

    String token = tokenForCustomerA();

    // Upload a new file
    var uploadResult =
        mockMvc
            .perform(
                post("/portal/requests/{id}/items/{itemId}/upload", sentRequestId, fileItemId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "tax-cert-corrected.pdf", "contentType": "application/pdf", "size": 3072}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String docIdStr =
        JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.documentId");

    // Re-submit
    mockMvc
        .perform(
            post("/portal/requests/{id}/items/{itemId}/submit", sentRequestId, fileItemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"documentId": "%s"}
                    """
                        .formatted(docIdStr)))
        .andExpect(status().isOk());

    // Verify re-submission
    runInTenantWithMember(
        () -> {
          var item = itemRepository.findById(fileItemId).orElseThrow();
          assertThat(item.getStatus().name()).isEqualTo("SUBMITTED");
          assertThat(item.getRejectionReason()).isNull();
        });
  }

  // ── 7. Auto-transition SENT -> IN_PROGRESS on first submit ─────────

  @Test
  @Order(7)
  void shouldAutoTransitionSentToInProgress() throws Exception {
    // Use the customer-scoped request which is still in SENT status
    String token = tokenForCustomerA();

    // Upload and submit
    var uploadResult =
        mockMvc
            .perform(
                post(
                        "/portal/requests/{id}/items/{itemId}/upload",
                        customerScopedRequestId,
                        customerScopedItemId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "id-doc.pdf", "contentType": "application/pdf", "size": 512}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String docIdStr =
        JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.documentId");

    // Verify the document is customer-scoped (no project)
    runInTenantWithMember(
        () -> {
          var doc = documentRepository.findById(UUID.fromString(docIdStr)).orElseThrow();
          assertThat(doc.getScope()).isEqualTo(Document.Scope.CUSTOMER);
          assertThat(doc.getCustomerId()).isEqualTo(customerId);
          assertThat(doc.getProjectId()).isNull();
        });

    mockMvc
        .perform(
            post(
                    "/portal/requests/{id}/items/{itemId}/submit",
                    customerScopedRequestId,
                    customerScopedItemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"documentId": "%s"}
                    """
                        .formatted(docIdStr)))
        .andExpect(status().isOk());

    // Verify request transitioned to IN_PROGRESS
    runInTenantWithMember(
        () -> {
          var request = requestRepository.findById(customerScopedRequestId).orElseThrow();
          assertThat(request.getStatus().name()).isEqualTo("IN_PROGRESS");
        });
  }

  // ── 8. Different portal contact denied (404) ────────────────────────

  @Test
  @Order(8)
  void shouldDenyAccessForDifferentPortalContact() throws Exception {
    // Customer B's token -- their portal contact is different from the request's contact
    String tokenB = portalJwtService.issueToken(customerIdB, ORG_ID);

    mockMvc
        .perform(
            get("/portal/requests/{id}", sentRequestId).header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());
  }

  // ── 9. No token returns 401 ─────────────────────────────────────────

  @Test
  @Order(9)
  void shouldReturn401WithoutToken() throws Exception {
    mockMvc.perform(get("/portal/requests")).andExpect(status().isUnauthorized());
  }

  // ── 10. Submit on ACCEPTED item rejected (400) ──────────────────────

  @Test
  @Order(10)
  void shouldRejectSubmitOnAcceptedItem() throws Exception {
    String token = tokenForCustomerA();

    mockMvc
        .perform(
            post("/portal/requests/{id}/items/{itemId}/submit", sentRequestId, acceptedItemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"documentId": "%s"}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isBadRequest());
  }

  // ── 11. Submit text on ACCEPTED item rejected (400) ─────────────────

  @Test
  @Order(11)
  void shouldRejectTextSubmitOnAcceptedItem() throws Exception {
    String token = tokenForCustomerA();

    mockMvc
        .perform(
            post("/portal/requests/{id}/items/{itemId}/submit", sentRequestId, acceptedItemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"textResponse": "Trying to overwrite accepted item"}
                    """))
        .andExpect(status().isBadRequest());
  }

  // ── 12. Submit with both fields null rejected (400) ────────────────

  @Test
  @Order(12)
  void shouldRejectSubmitWithBothFieldsNull() throws Exception {
    String token = tokenForCustomerA();

    mockMvc
        .perform(
            post("/portal/requests/{id}/items/{itemId}/submit", sentRequestId, textItemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {}
                    """))
        .andExpect(status().isBadRequest());
  }

  // ── Helpers ──────────────────────────────────────────────────────────

  private String tokenForCustomerA() {
    return portalJwtService.issueToken(customerId, ORG_ID);
  }

  private void runInTenantWithMember(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(action);
  }
}
