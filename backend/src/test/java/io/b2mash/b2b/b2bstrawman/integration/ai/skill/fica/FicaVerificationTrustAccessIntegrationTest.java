package io.b2mash.b2b.b2bstrawman.integration.ai.skill.fica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplate;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.testutil.StubAiProvider;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Security review (PR #1750): FICA verification read every uploaded client document straight from
 * storage and sent its text to the LLM. A saved Client Trust Statement is a client document that
 * prints trust money ({@code containsTrustData}), and the skill needs only AI_EXECUTE, so a member
 * without VIEW_TRUST could read trust figures through the skill's output. The skill now leaves
 * trust-bearing documents out for such a member and verifies the rest.
 *
 * <p>The client has a trust deposit with a unique reference (the marker) and two documents: the
 * owner's Client Trust Statement (prints the reference) and an ID document. The stub AI provider
 * records every prompt it receives.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FicaVerificationTrustAccessIntegrationTest {

  private static final String ORG_ID = "org_fica_trust_access_test";
  private static final String OWNER = "user_fta_owner";
  private static final String M = "user_fta_m";
  private static final String T = "user_fta_t";
  private static final String TRUST_MARKER = "FTA7731";
  private static final String ID_MARKER = "IDFTA5512";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustTransactionService trustTransactionService;
  @Autowired private ChecklistTemplateRepository checklistTemplateRepository;
  @Autowired private ChecklistTemplateItemRepository checklistTemplateItemRepository;
  @Autowired private ChecklistInstanceRepository checklistInstanceRepository;
  @Autowired private ChecklistInstanceItemRepository checklistInstanceItemRepository;
  @Autowired private StorageService storageService;
  @Autowired private StubAiProvider stubAiProvider;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String schema;
  private UUID ownerId;
  private UUID trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "FICA Trust Access Attorneys", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "fta_owner@test.com", "FTA Owner", "owner"));
    String mId =
        TestMemberHelper.syncMember(mockMvc, ORG_ID, M, "fta_m@test.com", "FTA M", "member");
    // T keeps the system "member" role, which holds AI_EXECUTE and VIEW_TRUST.
    TestMemberHelper.syncMember(mockMvc, ORG_ID, T, "fta_t@test.com", "FTA T", "member");
    assignRole(mId, createCustomRole("FICA Clerk", "VIEW_LEGAL", "AI_EXECUTE"));
    trustAccountId =
        inTenantTx(
            () ->
                trustAccountService
                    .createTrustAccount(
                        new CreateTrustAccountRequest(
                            "FTA Trust Account",
                            "Standard Bank",
                            "051001",
                            "6200007731",
                            "GENERAL",
                            true,
                            false,
                            null,
                            LocalDate.of(2026, 1, 1),
                            "FICA trust access test"))
                    .id());
  }

  @Test
  void memberWithoutViewTrust_trustStatementNeverSent_runStillCompletesOnTheRest()
      throws Exception {
    UUID clientId = clientWithTrustStatementIdDocumentAndChecklist("Sithole Family Trust", "A");

    String body = runFica(M, clientId);

    assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("COMPLETED");
    assertThat(stubAiProvider.receivedPromptContaining(TRUST_MARKER + "A")).isFalse();
    assertThat(stubAiProvider.receivedPromptContaining(ID_MARKER + "A")).isTrue();
  }

  /** Control: a member holding VIEW_TRUST verifies with the trust statement included. */
  @Test
  void memberWithViewTrust_trustStatementIncluded() throws Exception {
    UUID clientId = clientWithTrustStatementIdDocumentAndChecklist("Zulu Holdings", "B");

    String body = runFica(T, clientId);

    assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("COMPLETED");
    assertThat(stubAiProvider.receivedPromptContaining(TRUST_MARKER + "B")).isTrue();
    assertThat(stubAiProvider.receivedPromptContaining(ID_MARKER + "B")).isTrue();
  }

  // ---- fixture ---------------------------------------------------------------------------------

  /**
   * A client with a trust deposit (reference {@code TRUST_MARKER + code}), the owner's saved Client
   * Trust Statement (prints that reference; containsTrustData), an ID document PDF (prints {@code
   * ID_MARKER + code}) and an in-progress FICA checklist with a pending item.
   */
  private UUID clientWithTrustStatementIdDocumentAndChecklist(String name, String code)
      throws Exception {
    String reference = TRUST_MARKER + code;
    UUID clientId =
        inTenantTx(
            () -> {
              UUID id =
                  customerRepository
                      .save(
                          TestCustomerFactory.createActiveCustomer(
                              name, "fta-" + code + "@test.com", ownerId))
                      .getId();
              trustTransactionService.recordDeposit(
                  trustAccountId,
                  new RecordDepositRequest(
                      id,
                      null,
                      new BigDecimal("3100.00"),
                      reference,
                      "Deposit on instruction",
                      LocalDate.of(2026, 2, 10)));
              return id;
            });

    UUID ctsTemplateId =
        inTenantTx(
            () ->
                documentTemplateRepository
                    .findPackSeeded("legal-za", "client-trust-statement")
                    .getFirst()
                    .getId());
    String generated =
        mockMvc
            .perform(
                post("/api/templates/" + ctsTemplateId + "/generate")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"entityId":"%s","saveToDocuments":true,"acknowledgeWarnings":true}"""
                            .formatted(clientId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<String>read(generated, "$.documentId")).isNotBlank();

    inTenantTx(
        () -> {
          createIdDocument(clientId, ID_MARKER + code);
          createPendingChecklist(clientId);
          return null;
        });
    return clientId;
  }

  private void createIdDocument(UUID clientId, String marker) {
    var doc =
        new Document(
            Document.Scope.CUSTOMER,
            null,
            clientId,
            "id-document.pdf",
            "application/pdf",
            2048L,
            ownerId,
            Document.Visibility.INTERNAL);
    doc.assignS3Key("org/" + ORG_ID + "/customer/" + clientId + "/id-document.pdf");
    doc.confirmUpload();
    documentRepository.saveAndFlush(doc);
    storageService.upload(
        doc.getS3Key(),
        pdfWithText(
            "REPUBLIC OF SOUTH AFRICA IDENTITY DOCUMENT " + marker,
            "Surname and forenames as printed on the green barcoded identity document."),
        "application/pdf");
  }

  private void createPendingChecklist(UUID clientId) {
    var template =
        checklistTemplateRepository.saveAndFlush(
            new ChecklistTemplate(
                "FICA " + clientId,
                "FICA checklist",
                "fica-fta-" + UUID.randomUUID().toString().substring(0, 8),
                "ANY",
                "CUSTOM",
                false));
    var templateItem = new ChecklistTemplateItem(template.getId(), "SA ID Document", 1, true);
    templateItem.setRequiresDocument(true);
    templateItem.setRequiredDocumentLabel("ID Document");
    templateItem = checklistTemplateItemRepository.saveAndFlush(templateItem);
    var instance =
        checklistInstanceRepository.saveAndFlush(
            new ChecklistInstance(template.getId(), clientId, Instant.now()));
    checklistInstanceItemRepository.saveAndFlush(
        new ChecklistInstanceItem(
            instance.getId(),
            templateItem.getId(),
            "South African ID Document",
            "Valid SA ID document",
            1,
            true,
            true,
            "ID Document"));
  }

  private static byte[] pdfWithText(String... lines) {
    try (var pdf = new PDDocument()) {
      var page = new PDPage();
      pdf.addPage(page);
      try (var stream = new PDPageContentStream(pdf, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        stream.newLineAtOffset(25, 700);
        for (String line : lines) {
          stream.showText(line);
          stream.newLineAtOffset(0, -16);
        }
        stream.endText();
      }
      var out = new ByteArrayOutputStream();
      pdf.save(out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String runFica(String subject, UUID clientId) throws Exception {
    return mockMvc
        .perform(
            post("/api/ai/skills/fica-verification")
                .with(member(subject))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\"}".formatted(clientId)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static JwtRequestPostProcessor member(String subject) {
    return TestJwtFactory.memberJwt(ORG_ID, subject);
  }

  private String createCustomRole(String name, String... capabilities) throws Exception {
    String caps = objectMapper.writeValueAsString(List.of(capabilities));
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":%s}"""
                            .formatted(name, caps)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(String memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("MANAGE_TRUST", "VIEW_TRUST"))
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }
}
