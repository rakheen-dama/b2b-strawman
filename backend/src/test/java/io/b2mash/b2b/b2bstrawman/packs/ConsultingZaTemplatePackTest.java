package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreement;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreementRepository;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerFrequency;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerType;
import io.b2mash.b2b.b2bstrawman.retainer.RolloverPolicy;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.ProjectContextBuilder;
import io.b2mash.b2b.b2bstrawman.template.TiptapRenderer;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the {@code consulting-za} document template pack: asserts the pack is
 * discovered and installed via the Phase 65 {@link TemplatePackInstaller} pipeline when a {@code
 * consulting-za} tenant is provisioned, that all 4 agency Tiptap documents land with pack-install
 * metadata + content hashes, and that variable resolution against seeded project + retainer
 * fixtures resolves the expected keys.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsultingZaTemplatePackTest {

  private static final String ORG_ID = "org_cz_tmpl_pack_test";

  private static final String PACK_ID = "consulting-za";

  private static final List<String> EXPECTED_TEMPLATE_KEYS =
      List.of(
          "creative-brief", "statement-of-work", "engagement-letter", "monthly-retainer-report");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectService projectService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectContextBuilder projectContextBuilder;
  @Autowired private TiptapRenderer tiptapRenderer;
  @Autowired private RetainerAgreementRepository retainerAgreementRepository;
  @Autowired private MemberSyncService memberSyncService;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(
        ORG_ID, "Consulting ZA Template Pack Test Org", "consulting-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    var syncResult =
        memberSyncService.syncMember(
            ORG_ID,
            "user_cz_tmpl_owner",
            "cz_tmpl_owner@test.com",
            "Consulting Template Owner",
            null,
            "owner");
    memberId = syncResult.memberId();
  }

  @Test
  void provisioningCreatesPackInstallWithFourTemplates() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  assertThat(install.getPackType()).isEqualTo(PackType.DOCUMENT_TEMPLATE);
                  assertThat(install.getItemCount()).isEqualTo(4);
                  assertThat(install.getPackName()).isEqualTo("Agency Templates");
                }));
  }

  @Test
  void allFourTemplatesHaveSourcePackInstallIdAndContentHash() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var install = packInstallRepository.findByPackId(PACK_ID).orElseThrow();
                  var templates =
                      documentTemplateRepository.findBySourcePackInstallId(install.getId());
                  assertThat(templates).hasSize(4);
                  assertThat(templates)
                      .allSatisfy(
                          t -> {
                            assertThat(t.getSourcePackInstallId()).isEqualTo(install.getId());
                            assertThat(t.getContentHash()).isNotNull().hasSize(64);
                            assertThat(t.getPackId()).isEqualTo(PACK_ID);
                          });
                  var keys = templates.stream().map(DocumentTemplate::getPackTemplateKey).toList();
                  assertThat(keys).containsExactlyInAnyOrderElementsOf(EXPECTED_TEMPLATE_KEYS);
                }));
  }

  @Test
  void provisioningAutoInstallsTheTemplatePack() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    assertThat(packInstallRepository.findByPackId(PACK_ID))
                        .as(
                            "Pack %s should auto-install during provisionTenant(consulting-za)",
                            PACK_ID)
                        .isPresent()));
  }

  @SuppressWarnings("unchecked")
  @Test
  void seededContentIsValidTiptapJson() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates =
                      documentTemplateRepository.findByActiveTrueOrderBySortOrder().stream()
                          .filter(t -> PACK_ID.equals(t.getPackId()))
                          .toList();
                  assertThat(templates).hasSize(4);
                  assertThat(templates)
                      .allSatisfy(
                          t -> {
                            assertThat(t.getContent()).isNotNull();
                            assertThat(t.getContent()).containsEntry("type", "doc");
                            assertThat(t.getContent()).containsKey("content");
                            assertThat(t.getContent().get("content")).isInstanceOf(List.class);
                            List<Map<String, Object>> content =
                                (List<Map<String, Object>>) t.getContent().get("content");
                            assertThat(content).isNotEmpty();
                          });
                }));
  }

  /**
   * Variable resolution test (Task 482.10 part 1): seeds an active customer + project, sets {@code
   * campaign_type = WEBSITE_BUILD} on the project's custom fields, and asserts the {@link
   * ProjectContextBuilder} populates the expected keys plus that the rendered creative-brief HTML
   * contains the resolved value.
   */
  @Test
  void creativeBriefResolvesAgainstSeededProjectFixture() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  Customer customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Acme Brand Co.", "brand-lead@acme.example.com", memberId));
                  Map<String, Object> customFields = Map.of("campaign_type", "WEBSITE_BUILD");
                  Project project =
                      projectService.createProject(
                          "Website rebuild",
                          "Q1 site refresh",
                          memberId,
                          customFields,
                          List.of(),
                          customer.getId(),
                          null);

                  var template =
                      documentTemplateRepository.findByActiveTrueOrderBySortOrder().stream()
                          .filter(t -> PACK_ID.equals(t.getPackId()))
                          .filter(t -> "creative-brief".equals(t.getPackTemplateKey()))
                          .findFirst()
                          .orElseThrow();

                  Map<String, Object> context =
                      projectContextBuilder.buildContext(project.getId(), memberId);
                  assertThat(context).containsKey("project");
                  assertThat(context).containsKey("customer");
                  @SuppressWarnings("unchecked")
                  Map<String, Object> projectMap = (Map<String, Object>) context.get("project");
                  assertThat(projectMap).containsEntry("name", "Website rebuild");
                  @SuppressWarnings("unchecked")
                  Map<String, Object> projectCustomFields =
                      (Map<String, Object>) projectMap.get("customFields");
                  // Note: ProjectContextBuilder resolves dropdown labels via
                  // TemplateContextHelper.resolveDropdownLabels(), so the raw stored value
                  // "WEBSITE_BUILD" surfaces as the field-pack label "Website Build" in the
                  // template-rendering context. Assert against the resolved label.
                  assertThat(projectCustomFields).containsEntry("campaign_type", "Website Build");
                  @SuppressWarnings("unchecked")
                  Map<String, Object> customerMap = (Map<String, Object>) context.get("customer");
                  assertThat(customerMap).containsEntry("name", "Acme Brand Co.");

                  String html =
                      tiptapRenderer.render(template.getContent(), context, Map.of(), null);
                  assertThat(html).contains("Acme Brand Co.");
                  assertThat(html).contains("Website rebuild");
                  assertThat(html).contains("Website Build");
                }));
  }

  /**
   * Variable resolution test (Task 482.10 part 2): the monthly retainer report references {@code
   * retainer.periodStart}, {@code retainer.periodEnd}, {@code retainer.hoursUsed}, and {@code
   * retainer.hourBank}. There is currently no {@code RetainerContextBuilder} registered, so this
   * test asserts the underlying {@link RetainerAgreement} fields exist and round-trip — confirming
   * the template uses variable keys consistent with the entity shape (no invented variables).
   */
  @Test
  void retainerReportVariablesMapToRetainerAgreementFields() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  Customer customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Retainer Client Pty Ltd",
                              "ops@retainer-client.example.com",
                              memberId));

                  RetainerAgreement saved =
                      retainerAgreementRepository.save(
                          new RetainerAgreement(
                              customer.getId(),
                              "Monthly social media retainer",
                              RetainerType.HOUR_BANK,
                              RetainerFrequency.MONTHLY,
                              LocalDate.of(2026, 4, 1),
                              LocalDate.of(2026, 4, 30),
                              new BigDecimal("40.00"),
                              new BigDecimal("60000.00"),
                              RolloverPolicy.FORFEIT,
                              null,
                              "Test fixture",
                              memberId));

                  // The template's variable keys MUST round-trip to RetainerAgreement fields:
                  //   retainer.periodStart  -> RetainerAgreement.startDate
                  //   retainer.periodEnd    -> RetainerAgreement.endDate
                  //   retainer.hourBank     -> RetainerAgreement.allocatedHours
                  //   retainer.hoursUsed    -> aggregated from time entries (test asserts shape
                  //                            via allocatedHours presence + numeric type).
                  assertThat(saved.getId()).isNotNull();
                  assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 4, 1));
                  assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 4, 30));
                  assertThat(saved.getAllocatedHours())
                      .isEqualByComparingTo(new BigDecimal("40.00"));

                  var template =
                      documentTemplateRepository.findByActiveTrueOrderBySortOrder().stream()
                          .filter(t -> PACK_ID.equals(t.getPackId()))
                          .filter(t -> "monthly-retainer-report".equals(t.getPackTemplateKey()))
                          .findFirst()
                          .orElseThrow();

                  String contentJson = template.getContent().toString();
                  assertThat(contentJson).contains("retainer.periodStart");
                  assertThat(contentJson).contains("retainer.periodEnd");
                  assertThat(contentJson).contains("retainer.hoursUsed");
                  assertThat(contentJson).contains("retainer.hourBank");
                  // Must NOT emit the forbidden composed alias (architecture 66.10).
                  assertThat(contentJson).doesNotContain("retainer.hoursRemaining");
                  assertThat(contentJson).doesNotContain("retainer.customerName");
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
