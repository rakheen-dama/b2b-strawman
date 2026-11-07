package io.b2mash.b2b.b2bstrawman.template;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89 632A(a)(c) / D11: templates that only render inside their own flow (Statement of Account,
 * matter closure letter, payment receipt, trust receipt) are marked {@code dedicatedFlow} on the
 * template list and refused by the generic generate endpoint — rendered there they print hollow
 * ({@code statement.*} / {@code closure.*} / {@code payment.*} are never in the generic context).
 * On a legal-za tenant the universal common pack's matter templates (Standard Engagement Letter,
 * Matter Summary Report) are hidden from the generic Generate menus; Settings still lists them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerateDocumentDedicatedFlowIntegrationTest {

  private static final String ORG_ID = "org_generate_dedicated_flow_test";
  private static final String OWNER = "user_dedicated_flow_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;
  private UUID clientId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Dedicated Flow Attorneys", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, OWNER, "dedicated_owner@test.com", "Dedicated Owner", "owner");
    clientId =
        inTenantTx(
            () ->
                customerRepository
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Dedicated Flow Client",
                            "dedicated_client@test.com",
                            UUID.fromString(memberId)))
                    .getId());
  }

  @Test
  void genericGenerate_statementOfAccount_rejected400() throws Exception {
    String matterId = createMatter();
    UUID soaId = packTemplateId("legal-za", "statement-of-account");

    mockMvc
        .perform(
            post("/api/templates/" + soaId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityId":"%s","saveToDocuments":false,"acknowledgeWarnings":true}"""
                        .formatted(matterId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Generated from its own flow"))
        .andExpect(
            jsonPath("$.detail")
                .value("The Statement of Account is generated from the matter's Statements tab."));
  }

  @Test
  void genericGenerate_ordinaryMatterTemplate_stillGenerates() throws Exception {
    String matterId = createMatter();
    UUID letterId = packTemplateId("legal-za", "engagement-letter-general");

    mockMvc
        .perform(
            post("/api/templates/" + letterId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityId":"%s","saveToDocuments":false,"acknowledgeWarnings":true}"""
                        .formatted(matterId)))
        .andExpect(status().isOk());
  }

  @Test
  void listTemplates_marksDedicatedFlow() throws Exception {
    mockMvc
        .perform(get("/api/templates").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(dedicatedFlowOf("statement-of-account", true))
        .andExpect(dedicatedFlowOf("matter-closure-letter", true))
        .andExpect(dedicatedFlowOf("payment-receipt-za", true))
        .andExpect(dedicatedFlowOf("trust-receipt", true))
        .andExpect(dedicatedFlowOf("engagement-letter-general", false))
        .andExpect(dedicatedFlowOf("fee-note-za", false))
        .andExpect(dedicatedFlowOf("client-trust-statement", false))
        .andExpect(hiddenOf("statement-of-account", true))
        .andExpect(hiddenOf("trust-receipt", true))
        .andExpect(hiddenOf("engagement-letter-general", false))
        .andExpect(hiddenOf("client-trust-statement", false));
  }

  @Test
  void legalZaTenant_commonPackProjectTemplates_notInGenericList() throws Exception {
    String engagementSlug = slugOf("common", "engagement-letter");
    String summarySlug = slugOf("common", "project-summary");
    String coverLetterSlug = slugOf("common", "invoice-cover-letter");

    mockMvc
        .perform(
            get("/api/templates")
                .param("primaryEntityType", "PROJECT")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        // Still listed (Settings › Documents shows them), but never offered in a Generate menu.
        .andExpect(
            jsonPath("$[?(@.slug == '%s')].hiddenFromGenericMenus", engagementSlug).value(true))
        .andExpect(jsonPath("$[?(@.slug == '%s')].hiddenFromGenericMenus", summarySlug).value(true))
        .andExpect(jsonPath("$[?(@.slug == '%s')].dedicatedFlow", summarySlug).value(false));

    mockMvc
        .perform(
            get("/api/templates")
                .param("primaryEntityType", "INVOICE")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.slug == '%s')].hiddenFromGenericMenus", coverLetterSlug).value(false));
  }

  // --- helpers ---

  private org.springframework.test.web.servlet.ResultMatcher dedicatedFlowOf(
      String legalZaKey, boolean expected) {
    return jsonPath("$[?(@.slug == '%s')].dedicatedFlow", slugOf("legal-za", legalZaKey))
        .value(expected);
  }

  private org.springframework.test.web.servlet.ResultMatcher hiddenOf(
      String legalZaKey, boolean expected) {
    return jsonPath("$[?(@.slug == '%s')].hiddenFromGenericMenus", slugOf("legal-za", legalZaKey))
        .value(expected);
  }

  private String createMatter() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Dedicated flow %s","clientId":"%s","workType":"LITIGATION"}"""
                            .formatted(UUID.randomUUID(), clientId)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  private UUID packTemplateId(String packId, String templateKey) {
    return inTenantTx(
        () -> documentTemplateRepository.findPackSeeded(packId, templateKey).getFirst().getId());
  }

  private String slugOf(String packId, String templateKey) {
    return inTenantTx(
        () -> documentTemplateRepository.findPackSeeded(packId, templateKey).getFirst().getSlug());
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }
}
