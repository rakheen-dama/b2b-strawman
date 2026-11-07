package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 89 C1: the legal-za contract templates declare their party fields as {@code
 * requiredContextFields} (dotted paths into {@code project.customFields}), so generating an Offer
 * to Purchase for a conveyancing matter with no seller named goes through the existing
 * warn-and-acknowledge flow (422 {@code ValidationWarningException} unless {@code
 * acknowledgeWarnings}) instead of generating a contract with a blank party. Never a block: an
 * acknowledged request generates, and a matter with both parties named generates without warnings.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequiredContextFieldsIntegrationTest {

  private static final String ORG_ID = "org_required_context_fields_test";
  private static final String OWNER = "user_required_fields_owner";
  private static final String PACK_ID = "legal-za";

  private static final String SELLER_FIELD = "customFields.seller_name";
  private static final String PURCHASER_FIELD = "customFields.purchaser_name";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;
  private UUID clientId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Required Fields Attorneys", PACK_ID);
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, OWNER, "required_owner@test.com", "Required Owner", "owner");
    clientId =
        inTenantTx(
            () ->
                customerRepository
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Mokoena Family Trust", "mokoena@test.com", UUID.fromString(memberId)))
                    .getId());
  }

  @Test
  void freshTenant_packSeededContractRows_carryTheDeclaredRequiredContextFields() {
    var expectedParties =
        List.of(
            Map.of("entity", "project", "field", SELLER_FIELD),
            Map.of("entity", "project", "field", PURCHASER_FIELD));

    assertThat(requiredFieldsOf("offer-to-purchase")).isEqualTo(expectedParties);
    assertThat(requiredFieldsOf("deed-of-transfer")).isEqualTo(expectedParties);
    assertThat(requiredFieldsOf("bond-cancellation-instruction"))
        .isEqualTo(List.of(Map.of("entity", "project", "field", SELLER_FIELD)));
    assertThat(requiredFieldsOf("letter-of-demand"))
        .isEqualTo(List.of(Map.of("entity", "project", "field", "customFields.opposing_party")));
  }

  @Test
  void offerToPurchase_blankSeller_notAcknowledged_returns422WithTheSellerField() throws Exception {
    String matterId = createMatter("{\"purchaser_name\":\"Thandi Nkosi\"}");

    generate("offer-to-purchase", matterId, false, false)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.title").value("Required context fields missing"))
        .andExpect(jsonPath("$.validationResult.allPresent").value(false))
        .andExpect(
            jsonPath("$.validationResult.fields[?(@.field == '%s')].present", SELLER_FIELD)
                .value(false))
        .andExpect(
            jsonPath("$.validationResult.fields[?(@.field == '%s')].present", PURCHASER_FIELD)
                .value(true));
  }

  @Test
  void offerToPurchase_blankSeller_acknowledged_generatesAndRecordsTheWarning() throws Exception {
    String matterId = createMatter("{\"purchaser_name\":\"Thandi Nkosi\"}");

    generate("offer-to-purchase", matterId, false, true).andExpect(status().isOk());

    List<Map<String, Object>> warnings =
        inTenantTx(
            () ->
                generatedDocumentRepository
                    .findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
                        TemplateEntityType.PROJECT, UUID.fromString(matterId))
                    .getFirst()
                    .getWarnings());
    assertThat(warnings).isNotEmpty();
    assertThat(warnings.toString()).contains(SELLER_FIELD);
  }

  @Test
  void offerToPurchase_bothPartiesNamed_generatesWithoutAcknowledgement() throws Exception {
    String matterId =
        createMatter("{\"seller_name\":\"Sipho Mokoena\",\"purchaser_name\":\"Thandi Nkosi\"}");

    generate("offer-to-purchase", matterId, false, false).andExpect(status().isOk());
  }

  @Test
  void offerToPurchase_blankSeller_previewReportsTheMissingSeller() throws Exception {
    String matterId = createMatter("{\"purchaser_name\":\"Thandi Nkosi\"}");

    mockMvc
        .perform(
            post("/api/templates/" + packTemplateId("offer-to-purchase") + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityId\":\"%s\"}".formatted(matterId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validationResult.allPresent").value(false))
        .andExpect(
            jsonPath("$.validationResult.fields[?(@.field == '%s')].present", SELLER_FIELD)
                .value(false));
  }

  /**
   * G89 632A(b): the warning names the field the way the firm knows it ("Matter › Seller Name", the
   * field definition's display name), not the raw context path "project.customFields.seller_name".
   */
  @Test
  void offerToPurchase_blankSeller_warningCarriesAReadableLabel() throws Exception {
    String matterId = createMatter("{\"purchaser_name\":\"Thandi Nkosi\"}");

    mockMvc
        .perform(
            post("/api/templates/" + packTemplateId("offer-to-purchase") + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityId\":\"%s\"}".formatted(matterId)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.validationResult.fields[?(@.field == '%s')].label", SELLER_FIELD)
                .value("Matter › Seller Name"));

    generate("offer-to-purchase", matterId, false, false)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.validationResult.fields[?(@.field == '%s')].label", PURCHASER_FIELD)
                .value("Matter › Purchaser Name"));
  }

  // --- helpers ---

  private String createMatter(String customFieldsJson) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Required fields %s","clientId":"%s","workType":"CONVEYANCING",\
                        "customFields":%s}"""
                            .formatted(UUID.randomUUID(), clientId, customFieldsJson)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  private ResultActions generate(
      String templateKey, String matterId, boolean saveToDocuments, boolean acknowledgeWarnings)
      throws Exception {
    return mockMvc.perform(
        post("/api/templates/" + packTemplateId(templateKey) + "/generate")
            .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"entityId":"%s","saveToDocuments":%s,"acknowledgeWarnings":%s}"""
                    .formatted(matterId, saveToDocuments, acknowledgeWarnings)));
  }

  private UUID packTemplateId(String templateKey) {
    return inTenantTx(
        () -> documentTemplateRepository.findPackSeeded(PACK_ID, templateKey).getFirst().getId());
  }

  private List<Map<String, String>> requiredFieldsOf(String templateKey) {
    return inTenantTx(
        () ->
            documentTemplateRepository
                .findPackSeeded(PACK_ID, templateKey)
                .getFirst()
                .getRequiredContextFields());
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }
}
