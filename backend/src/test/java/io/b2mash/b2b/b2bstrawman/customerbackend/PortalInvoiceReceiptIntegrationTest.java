package io.b2mash.b2b.b2bstrawman.customerbackend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F32 (Phase 89 632B, ADR-364) portal half: the fee-note download serves the fee note even when
 * newer INVOICE-typed documents (receipts) exist; the payments list and the per-payment receipt
 * download are scoped to the contact's client and the invoice.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalInvoiceReceiptIntegrationTest {

  private static final String ORG_ID = "org_portal_receipt_test";
  private static final String OWNER = "user_portal_receipt_owner";
  private static final Map<String, Object> DECOY_CONTENT =
      Map.of(
          "type",
          "doc",
          "content",
          List.of(
              Map.of(
                  "type",
                  "paragraph",
                  "content",
                  List.of(
                      Map.of("type", "text", "text", "Later document for {{customer.name}}")))));

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private GeneratedDocumentService generatedDocumentService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private PaymentEventRepository paymentEventRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID decoyTemplateId;
  private String portalToken;
  private String otherClientToken;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Receipt Law Firm", "legal-za");
    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                OWNER,
                "portal_receipt_owner@test.com",
                "Portal Receipt Owner",
                "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    inTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  customerId =
                      customerRepository
                          .save(
                              TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                                  "Portal Receipt Client",
                                  "portal_receipt_client@test.com",
                                  memberIdOwner))
                          .getId();
                  projectId =
                      projectRepository
                          .save(new Project("Portal Receipt Matter", "Matter", memberIdOwner))
                          .getId();
                  customerProjectRepository.save(
                      new CustomerProject(customerId, projectId, memberIdOwner));
                  // A firm-authored INVOICE-typed template — stands in for "any later INVOICE
                  // document" (a receipt, a future credit note) generated after the fee note.
                  decoyTemplateId =
                      documentTemplateRepository
                          .save(
                              new DocumentTemplate(
                                  TemplateEntityType.INVOICE,
                                  "Firm Later Fee Note Document",
                                  "firm-later-fee-note-document",
                                  TemplateCategory.OTHER,
                                  DECOY_CONTENT))
                          .getId();
                }));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                portalContactService.createContact(
                    ORG_ID,
                    customerId,
                    "portal_receipt_contact@test.com",
                    "Portal Receipt Contact",
                    PortalContact.ContactRole.PRIMARY));
    portalToken = portalJwtService.issueToken(customerId, ORG_ID);

    // A second client of the same firm, with its own portal contact.
    UUID otherCustomerId =
        inTenantCall(
            () ->
                transactionTemplate.execute(
                    tx ->
                        customerRepository
                            .save(
                                TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                                    "Other Receipt Client",
                                    "other_receipt_client@test.com",
                                    memberIdOwner))
                            .getId()));
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                portalContactService.createContact(
                    ORG_ID,
                    otherCustomerId,
                    "other_receipt_contact@test.com",
                    "Other Receipt Contact",
                    PortalContact.ContactRole.PRIMARY));
    otherClientToken = portalJwtService.issueToken(otherCustomerId, ORG_ID);
  }

  /** Red at HEAD: the download takes the newest INVOICE document, not the fee note. */
  @Test
  void feeNoteDownloadServesTheFeeNoteEvenWhenANewerInvoiceDocumentExists() throws Exception {
    String invoiceId = createSentInvoice();
    UUID id = UUID.fromString(invoiceId);
    GeneratedDocument feeNote = inTenantCall(() -> invoiceDocuments(id).getFirst());

    GeneratedDocument later =
        inTenantCall(
            () ->
                generatedDocumentService
                    .generateDocument(decoyTemplateId, id, false, true, List.of(), memberIdOwner)
                    .generatedDocument());
    assertThat(inTenantCall(() -> invoiceDocuments(id).getFirst().getId()))
        .isEqualTo(later.getId());

    var result =
        mockMvc
            .perform(
                get("/portal/invoices/{id}/download", invoiceId)
                    .header("Authorization", "Bearer " + portalToken))
            .andExpect(status().isOk())
            .andReturn();
    String url = JsonPath.read(result.getResponse().getContentAsString(), "$.downloadUrl");
    assertThat(url).contains(feeNote.getS3Key()).doesNotContain(later.getS3Key());
  }

  /**
   * A part payment's receipt is itself a newer INVOICE document — the fee-note download still
   * serves the fee note; the payments list shows the payment with its SA date and label.
   */
  @Test
  void afterAPartPayment_feeNoteDownloadIsTheFeeNote_andThePaymentIsListed() throws Exception {
    String invoiceId = createSentInvoice();
    UUID id = UUID.fromString(invoiceId);
    GeneratedDocument feeNote = inTenantCall(() -> invoiceDocuments(id).getFirst());
    String eventId = postPayment(invoiceId, "1200.00", "EFT", "PORTAL-EFT-1");
    UUID receiptId =
        inTenantCall(
            () ->
                paymentEventRepository
                    .findById(UUID.fromString(eventId))
                    .orElseThrow()
                    .getReceiptGeneratedDocumentId());
    assertThat(receiptId).isNotNull();
    assertThat(inTenantCall(() -> invoiceDocuments(id).getFirst().getId())).isEqualTo(receiptId);

    var download =
        mockMvc
            .perform(
                get("/portal/invoices/{id}/download", invoiceId)
                    .header("Authorization", "Bearer " + portalToken))
            .andExpect(status().isOk())
            .andReturn();
    assertThat((String) JsonPath.read(download.getResponse().getContentAsString(), "$.downloadUrl"))
        .contains(feeNote.getS3Key());

    var createdAt =
        inTenantCall(
            () ->
                paymentEventRepository
                    .findById(UUID.fromString(eventId))
                    .orElseThrow()
                    .getCreatedAt());
    mockMvc
        .perform(
            get("/portal/invoices/{id}/payments", invoiceId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].paymentEventId").value(eventId))
        .andExpect(
            jsonPath("$[0].date").value(LocalDate.ofInstant(createdAt, KaziTime.ZONE).toString()))
        .andExpect(jsonPath("$[0].methodLabel").value("EFT"))
        .andExpect(jsonPath("$[0].amount").value(1200.0))
        .andExpect(jsonPath("$[0].reference").value("PORTAL-EFT-1"))
        .andExpect(jsonPath("$[0].receiptAvailable").value(true));

    var receipt =
        mockMvc
            .perform(
                get("/portal/invoices/{id}/payments/{pe}/receipt", invoiceId, eventId)
                    .header("Authorization", "Bearer " + portalToken))
            .andExpect(status().isOk())
            .andReturn();
    String receiptKey =
        inTenantCall(
            () -> generatedDocumentRepository.findById(receiptId).orElseThrow().getS3Key());
    assertThat((String) JsonPath.read(receipt.getResponse().getContentAsString(), "$.downloadUrl"))
        .contains(receiptKey);
  }

  /**
   * A payment with no receipt (one that predates Phase 89, or whose receipt was deleted): the
   * portal download generates it now, by the fee note's creator, and the pointer is set.
   */
  @Test
  void receiptDownloadForAPaymentWithoutAReceipt_generatesItLazily() throws Exception {
    String invoiceId = createSentInvoice();
    String eventId = postPayment(invoiceId, "300.00", "EFT", "PORTAL-LAZY-1");
    UUID original =
        inTenantCall(
            () ->
                paymentEventRepository
                    .findById(UUID.fromString(eventId))
                    .orElseThrow()
                    .getReceiptGeneratedDocumentId());
    inTenant(() -> generatedDocumentService.delete(original)); // FK → NULL (ON DELETE SET NULL)
    assertThat(
            inTenantCall(
                () ->
                    paymentEventRepository
                        .findById(UUID.fromString(eventId))
                        .orElseThrow()
                        .getReceiptGeneratedDocumentId()))
        .isNull();
    mockMvc
        .perform(
            get("/portal/invoices/{id}/payments", invoiceId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].receiptAvailable").value(false));

    var result =
        mockMvc
            .perform(
                get("/portal/invoices/{id}/payments/{pe}/receipt", invoiceId, eventId)
                    .header("Authorization", "Bearer " + portalToken))
            .andExpect(status().isOk())
            .andReturn();

    var pe =
        inTenantCall(() -> paymentEventRepository.findById(UUID.fromString(eventId)).orElseThrow());
    assertThat(pe.getReceiptGeneratedDocumentId()).isNotNull().isNotEqualTo(original);
    var regenerated =
        inTenantCall(
            () ->
                generatedDocumentRepository
                    .findById(pe.getReceiptGeneratedDocumentId())
                    .orElseThrow());
    assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.downloadUrl"))
        .contains(regenerated.getS3Key());
    assertThat(regenerated.getGeneratedBy()).isEqualTo(memberIdOwner); // the fee note's creator
  }

  /** §14.6.2 isolation: another fee note's payment event and another client's fee note → 404. */
  @Test
  void receiptOfAnotherFeeNote_andAnotherClientsFeeNote_are404() throws Exception {
    String invoiceA = createSentInvoice();
    String invoiceB = createSentInvoice();
    String eventOfA = postPayment(invoiceA, "100.00", "EFT", "PORTAL-ISO-1");

    mockMvc
        .perform(
            get("/portal/invoices/{id}/payments/{pe}/receipt", invoiceB, eventOfA)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("PaymentEvent not found"))
        .andExpect(jsonPath("$.detail").value("No paymentevent found with id " + eventOfA));

    mockMvc
        .perform(
            get("/portal/invoices/{id}/payments", invoiceA)
                .header("Authorization", "Bearer " + otherClientToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Fee note not found"))
        .andExpect(jsonPath("$.detail").value("No fee note found with id " + invoiceA));
    mockMvc
        .perform(
            get("/portal/invoices/{id}/payments/{pe}/receipt", invoiceA, eventOfA)
                .header("Authorization", "Bearer " + otherClientToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Fee note not found"))
        .andExpect(jsonPath("$.detail").value("No fee note found with id " + invoiceA));
  }

  // --- helpers ---

  /** Records a payment through the firm endpoint; returns the new payment event id. */
  private String postPayment(String invoiceId, String amount, String method, String reference)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/invoices/" + invoiceId + "/payment")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"amount": %s, "paymentMethod": "%s", "paymentReference": "%s"}
                        """
                            .formatted(amount, method, reference)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.payments[0].id");
  }

  private List<GeneratedDocument> invoiceDocuments(UUID invoiceId) {
    return generatedDocumentRepository
        .findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
            TemplateEntityType.INVOICE, invoiceId);
  }

  private String createSentInvoice() throws Exception {
    var created =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clientId": "%s", "currency": "ZAR"}
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();
    String invoiceId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/lines")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"matterId": "%s", "description": "Consultation", "quantity": 1.0000,
                     "unitPrice": 5000.00, "sortOrder": 0}
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"overrideWarnings\": true}"))
        .andExpect(status().isOk());
    return invoiceId;
  }

  private void inTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private <T> T inTenantCall(ScopedValue.CallableOp<T, RuntimeException> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(body);
  }
}
