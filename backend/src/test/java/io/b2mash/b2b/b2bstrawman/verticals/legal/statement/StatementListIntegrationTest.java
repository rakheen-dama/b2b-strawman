package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.HashMap;
import java.util.List;
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
 * 634B: the matter's Statements tab ({@code GET /api/matters/{id}/statements}) lists only real
 * statements — documents {@code StatementService.generate} produced, whose generation snapshot
 * carries the {@code summary} block — from the base {@code statement-of-account} template AND its
 * {@code statement-of-account-*} clones. A Statement of Account generated from the generic document
 * menu has no summary snapshot and printed "R 0,00" on the tab; a statement generated from a cloned
 * template was missing from the tab altogether.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementListIntegrationTest {

  private static final String ORG_ID = "org_statement_list_634b";
  private static final String OWNER = "user_statement_list_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Statement List Firm", "legal-za");
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "statement_list@test.com", "List Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements"));
                  orgSettingsRepository.save(settings);
                  customerId =
                      customerRepository
                          .saveAndFlush(
                              createActiveCustomer("List Client", "list_client@test.com", memberId))
                          .getId();
                }));
  }

  @Test
  void genericMenuSoa_withoutSummary_excluded() throws Exception {
    UUID matterId = newMatter("Generic Menu SoA Matter");
    String realStatementId = generateStatement(matterId, null);

    // What the generic document menu writes for the SoA template: the base template id, but a
    // GeneratedDocumentService snapshot — no `summary` block (only StatementService writes one).
    UUID genericId =
        runInTenantReturning(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      UUID baseTemplateId =
                          documentTemplateRepository
                              .findBySlug(StatementService.SYSTEM_TEMPLATE_SLUG)
                              .orElseThrow()
                              .getId();
                      var generic =
                          new GeneratedDocument(
                              baseTemplateId,
                              TemplateEntityType.PROJECT,
                              matterId,
                              "statement-of-account-generic.pdf",
                              "org/test/generated/generic.pdf",
                              10,
                              memberId);
                      var snapshot = new HashMap<String, Object>();
                      snapshot.put("template_name", "Statement of Account");
                      snapshot.put("entity_type", "PROJECT");
                      generic.setContextSnapshot(snapshot);
                      return generatedDocumentRepository.saveAndFlush(generic).getId();
                    }));

    List<String> listed = listedIds(matterId);

    assertThat(listed)
        .as("a generic-menu SoA has no summary snapshot — it is not a statement row")
        .doesNotContain(genericId.toString())
        .contains(realStatementId);
  }

  @Test
  void clonedTemplateStatement_included() throws Exception {
    UUID matterId = newMatter("Cloned Template Statement Matter");
    String baseTemplateId =
        runInTenantReturning(
            () ->
                documentTemplateRepository
                    .findBySlug(StatementService.SYSTEM_TEMPLATE_SLUG)
                    .orElseThrow()
                    .getId()
                    .toString());
    var cloned =
        mockMvc
            .perform(
                post("/api/templates/" + baseTemplateId + "/clone")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    String cloneId = JsonPath.read(cloned.getResponse().getContentAsString(), "$.id");
    String cloneSlug = JsonPath.read(cloned.getResponse().getContentAsString(), "$.slug");
    assertThat(cloneSlug).startsWith(StatementService.SYSTEM_TEMPLATE_SLUG + "-");

    String statementId = generateStatement(matterId, cloneId);

    assertThat(listedIds(matterId))
        .as("a statement generated from a statement-of-account-* clone is a statement row")
        .contains(statementId);
  }

  // ---------- helpers ----------

  private String generateStatement(UUID matterId, String templateId) throws Exception {
    String body =
        """
        {"periodStart": "2026-04-01", "periodEnd": "2026-04-30", "templateId": %s}
        """
            .formatted(templateId == null ? "null" : "\"" + templateId + "\"");
    var result =
        mockMvc
            .perform(
                post("/api/matters/" + matterId + "/statements")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private List<String> listedIds(UUID matterId) throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/matters/" + matterId + "/statements")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.content[*].id");
  }

  private UUID newMatter(String name) {
    return runInTenantReturning(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var matter = new Project(name, "634B statement list", memberId);
                  matter.setCustomerId(customerId);
                  return projectRepository.saveAndFlush(matter).getId();
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private <T> T runInTenantReturning(java.util.function.Supplier<T> action) {
    Object[] holder = new Object[1];
    runInTenant(() -> holder[0] = action.get());
    @SuppressWarnings("unchecked")
    T value = (T) holder[0];
    return value;
  }
}
