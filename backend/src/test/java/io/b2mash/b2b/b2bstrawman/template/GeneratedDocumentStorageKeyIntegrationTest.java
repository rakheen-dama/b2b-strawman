package io.b2mash.b2b.b2bstrawman.template;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.StatementService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.GenerateStatementRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F33 regression: every generated document gets its own storage object. Before the fix the S3
 * key was {@code org/{tenant}/generated/{fileName}}, and the file name is built only from the
 * template slug, the slugified matter/client name and a date, so a second Statement of Account for
 * the same matter and period end, a second document for the same entity on the same day, or a
 * document for a different matter or client with the same name overwrote the first object (a client
 * downloaded an internal statement). The key is now {@code
 * org/{tenant}/generated/{uuid}/{fileName}}; the human-readable file name is unchanged.
 *
 * <p>Each test asserts distinct keys, that each key still holds its own bytes, and that the firm
 * download endpoint presigns the new key shape (a byte round-trip alone would pass even if the
 * storage key whitelist rejected it).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratedDocumentStorageKeyIntegrationTest {

  private static final String ORG_ID = "org_gen_doc_storage_key";
  private static final String OWNER_SUBJECT = "user_gen_key_owner";
  private static final String SHARED_MATTER_NAME = "Dlamini v Standard Bank";
  private static final Map<String, Object> CONTENT =
      TestDocumentBuilder.doc()
          .heading(1, "Matter Summary")
          .variable("project.name")
          .variable("project.description")
          .build();

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
  @Autowired private DocumentRepository documentRepository;
  @Autowired private StatementService statementService;
  @Autowired private GeneratedDocumentService generatedDocumentService;
  @Autowired private StorageService storageService;

  private String tenantSchema;
  private Pattern newKeyShape;
  private UUID memberId;
  private UUID templateId;
  private UUID statementMatterId;
  private UUID sameNameMatterA;
  private UUID sameNameMatterB;
  private UUID genericMatterId;
  private UUID genericSameNameA;
  private UUID genericSameNameB;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Generated Doc Storage Key Firm", "legal-za");
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                OWNER_SUBJECT,
                "gen_key_owner@test.com",
                "Gen Key Owner",
                "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    newKeyShape =
        Pattern.compile(
            "^org/"
                + Pattern.quote(tenantSchema)
                + "/generated/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[^/]+$");

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT);
    genericMatterId =
        UUID.fromString(
            TestEntityHelper.createProject(
                mockMvc, ownerJwt, "Generic Key Matter", "Same entity, same day"));
    genericSameNameA =
        UUID.fromString(
            TestEntityHelper.createProject(
                mockMvc, ownerJwt, SHARED_MATTER_NAME, "First client's matter"));
    genericSameNameB =
        UUID.fromString(
            TestEntityHelper.createProject(
                mockMvc, ownerJwt, SHARED_MATTER_NAME, "Second client's matter"));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements"));
                  orgSettingsRepository.save(settings);

                  statementMatterId = matterWithClient("Mokoena Estate", "Mokoena Client", "a");
                  sameNameMatterA = matterWithClient(SHARED_MATTER_NAME, "Sipho Dlamini", "b");
                  sameNameMatterB = matterWithClient(SHARED_MATTER_NAME, "Thandi Dlamini", "c");

                  templateId =
                      documentTemplateRepository
                          .save(
                              new DocumentTemplate(
                                  TemplateEntityType.PROJECT,
                                  "Key Test Letter",
                                  "key-test-letter",
                                  TemplateCategory.ENGAGEMENT_LETTER,
                                  CONTENT))
                          .getId();
                }));
  }

  /** (a) Two Statements of Account for the same matter and the same period end. */
  @Test
  void statement_sameMatterAndPeriodEnd_getsDistinctKeys() throws Exception {
    UUID first = generateStatement(statementMatterId, "2026-04-01", "2026-04-30");
    UUID second = generateStatement(statementMatterId, "2026-03-01", "2026-04-30");

    var gd1 = loadGenerated(first);
    var gd2 = loadGenerated(second);

    assertThat(gd1.getFileName())
        .as("human-readable file name is unchanged")
        .isEqualTo("statement-of-account-mokoena-estate-2026-04-30.pdf")
        .isEqualTo(gd2.getFileName());
    assertDistinctOwnObjects(gd1, gd2);
    assertPairedDocumentSharesKey(gd1);
    assertPairedDocumentSharesKey(gd2);
    assertThat(storageService.download(gd1.getS3Key()))
        .as("the first statement's bytes must survive the second generation")
        .isNotEqualTo(storageService.download(gd2.getS3Key()));
    assertFirmDownloadPresigns(gd1);
    assertFirmDownloadPresigns(gd2);
  }

  /** (c) Statements for two different matters (different clients) with the same name. */
  @Test
  void statement_differentMattersWithSameName_getDistinctKeys() throws Exception {
    UUID first = generateStatement(sameNameMatterA, "2026-04-01", "2026-04-30");
    UUID second = generateStatement(sameNameMatterB, "2026-04-01", "2026-04-30");

    var gd1 = loadGenerated(first);
    var gd2 = loadGenerated(second);

    assertThat(gd1.getFileName()).isEqualTo(gd2.getFileName());
    assertDistinctOwnObjects(gd1, gd2);
    assertThat(storageService.download(gd1.getS3Key()))
        .as("one client's statement must not serve another client's bytes")
        .isNotEqualTo(storageService.download(gd2.getS3Key()));
    assertFirmDownloadPresigns(gd1);
    assertFirmDownloadPresigns(gd2);
  }

  /** (b) Two generic template documents for the same entity on the same day. */
  @Test
  void genericDocument_sameEntitySameDay_getsDistinctKeys() throws Exception {
    var gd1 = generateGeneric(genericMatterId);
    var gd2 = generateGeneric(genericMatterId);

    assertThat(gd1.getFileName())
        .as("human-readable file name is unchanged")
        .startsWith("key-test-letter-generic-key-matter-")
        .endsWith(".pdf")
        .isEqualTo(gd2.getFileName());
    assertDistinctOwnObjects(gd1, gd2);
    assertPairedDocumentSharesKey(gd1);
    assertPairedDocumentSharesKey(gd2);
    assertFirmDownloadPresigns(gd1);
    assertFirmDownloadPresigns(gd2);
  }

  /**
   * (c) Generic documents for two different matters with the same name, same template, same day.
   */
  @Test
  void genericDocument_differentMattersWithSameName_getDistinctKeys() throws Exception {
    var gd1 = generateGeneric(genericSameNameA);
    var gd2 = generateGeneric(genericSameNameB);

    assertThat(gd1.getFileName()).isEqualTo(gd2.getFileName());
    assertDistinctOwnObjects(gd1, gd2);
    assertThat(storageService.download(gd1.getS3Key()))
        .as("one matter's document must not serve another matter's bytes")
        .isNotEqualTo(storageService.download(gd2.getS3Key()));
    assertFirmDownloadPresigns(gd1);
    assertFirmDownloadPresigns(gd2);
  }

  // --- assertions ---

  private void assertDistinctOwnObjects(GeneratedDocument gd1, GeneratedDocument gd2) {
    assertThat(gd1.getS3Key()).isNotEqualTo(gd2.getS3Key());
    for (var gd : List.of(gd1, gd2)) {
      assertThat(gd.getS3Key()).matches(newKeyShape).endsWith("/" + gd.getFileName());
      assertThat(storageService.download(gd.getS3Key()))
          .as("stored object for %s still holds its own bytes", gd.getId())
          .hasSize((int) gd.getFileSize());
    }
  }

  private void assertPairedDocumentSharesKey(GeneratedDocument gd) {
    assertThat(gd.getDocumentId()).isNotNull();
    var paired =
        runInTenantReturning(
            () -> documentRepository.findById(gd.getDocumentId()).orElseThrow().getS3Key());
    assertThat(paired).isEqualTo(gd.getS3Key());
  }

  private void assertFirmDownloadPresigns(GeneratedDocument gd) throws Exception {
    mockMvc
        .perform(
            get("/api/generated-documents/{id}/download", gd.getId())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT)))
        .andExpect(status().isFound())
        .andExpect(
            header().string("Location", org.hamcrest.Matchers.containsString(gd.getS3Key())));
  }

  // --- helpers ---

  private UUID generateStatement(UUID projectId, String periodStart, String periodEnd) {
    return runInTenantReturning(
        () ->
            statementService
                .generate(
                    projectId,
                    new GenerateStatementRequest(
                        LocalDate.parse(periodStart), LocalDate.parse(periodEnd), null, null, null),
                    memberId)
                .id());
  }

  private GeneratedDocument generateGeneric(UUID projectId) {
    UUID id =
        runInTenantReturning(
            () ->
                generatedDocumentService
                    .generateDocument(templateId, projectId, true, true, List.of(), memberId)
                    .generatedDocument()
                    .getId());
    return loadGenerated(id);
  }

  private GeneratedDocument loadGenerated(UUID id) {
    return runInTenantReturning(() -> generatedDocumentRepository.findById(id).orElseThrow());
  }

  private UUID matterWithClient(String matterName, String clientName, String emailTag) {
    var customer =
        customerRepository.saveAndFlush(
            createActiveCustomer(clientName, "gen_key_client_" + emailTag + "@test.com", memberId));
    var project = new Project(matterName, "G88-F33 key test", memberId);
    project.setCustomerId(customer.getId());
    return projectRepository.saveAndFlush(project).getId();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private <T> T runInTenantReturning(Supplier<T> action) {
    Object[] holder = new Object[1];
    runInTenant(() -> holder[0] = action.get());
    @SuppressWarnings("unchecked")
    T value = (T) holder[0];
    return value;
  }
}
