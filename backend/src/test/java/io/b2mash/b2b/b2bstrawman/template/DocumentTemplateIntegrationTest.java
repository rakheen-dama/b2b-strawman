package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentTemplateIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID_A = "org_dt_test_a";
  private static final String ORG_ID_B = "org_dt_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private DocumentTemplateService documentTemplateService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchemaA;
  private String tenantSchemaB;
  private UUID memberIdOwnerA;
  private UUID memberIdOwnerB;

  @BeforeAll
  void setup() throws Exception {
    // --- Tenant A ---
    provisioningService.provisionTenant(ORG_ID_A, "DT Test Org A");
    planSyncService.syncPlan(ORG_ID_A, "pro-plan");

    memberIdOwnerA =
        UUID.fromString(
            syncMember(ORG_ID_A, "user_dt_owner_a", "dt_owner_a@test.com", "DT Owner A", "owner"));

    tenantSchemaA =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_A).orElseThrow().getSchemaName();

    // --- Tenant B ---
    provisioningService.provisionTenant(ORG_ID_B, "DT Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");

    memberIdOwnerB =
        UUID.fromString(
            syncMember(ORG_ID_B, "user_dt_owner_b", "dt_owner_b@test.com", "DT Owner B", "owner"));

    tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();
  }

  @Test
  void shouldSaveAndRetrieveInDedicatedSchema() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdOwnerA,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var dt =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Test Template",
                          "test-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          "<h1>Hello</h1>");
                  dt = documentTemplateRepository.save(dt);

                  var found = documentTemplateRepository.findById(dt.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getSlug()).isEqualTo("test-template");
                  assertThat(found.get().getCategory())
                      .isEqualTo(TemplateCategory.ENGAGEMENT_LETTER);
                  assertThat(found.get().isActive()).isTrue();
                }));
  }

  @Test
  void findByIdRespectsFilterForCrossTenantIsolation() {
    var idHolder = new UUID[1];
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdOwnerA,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var dt =
                      new DocumentTemplate(
                          TemplateEntityType.CUSTOMER,
                          "Isolation Test",
                          "isolation-test",
                          TemplateCategory.NDA,
                          "<p>NDA content</p>");
                  dt = documentTemplateRepository.save(dt);
                  idHolder[0] = dt.getId();
                }));

    runInTenant(
        tenantSchemaB,
        ORG_ID_B,
        memberIdOwnerB,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = documentTemplateRepository.findById(idHolder[0]);
                  assertThat(found).isEmpty();
                }));
  }

  @Test
  void shouldAutoGenerateSlug() {
    var slug = DocumentTemplate.generateSlug("My Custom Template");
    assertThat(slug).isEqualTo("my-custom-template");
  }

  @Test
  void shouldEnforceSlugUniqueness() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdOwnerA,
        "owner",
        () -> {
          var request1 =
              new DocumentTemplateController.CreateTemplateRequest(
                  "Slug Unique Test",
                  null,
                  TemplateCategory.PROPOSAL,
                  TemplateEntityType.PROJECT,
                  "<p>First</p>",
                  null,
                  "slug-unique-test");
          var response1 = documentTemplateService.create(request1);
          assertThat(response1.slug()).isEqualTo("slug-unique-test");

          var request2 =
              new DocumentTemplateController.CreateTemplateRequest(
                  "Slug Unique Test 2",
                  null,
                  TemplateCategory.PROPOSAL,
                  TemplateEntityType.PROJECT,
                  "<p>Second</p>",
                  null,
                  "slug-unique-test");
          var response2 = documentTemplateService.create(request2);
          assertThat(response2.slug()).isEqualTo("slug-unique-test-2");
        });
  }

  @Test
  void shouldDeactivateTemplate() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdOwnerA,
        "owner",
        () -> {
          var request =
              new DocumentTemplateController.CreateTemplateRequest(
                  "Deactivate Test",
                  null,
                  TemplateCategory.REPORT,
                  TemplateEntityType.PROJECT,
                  "<p>Report</p>",
                  null,
                  "deactivate-test");
          var created = documentTemplateService.create(request);
          assertThat(created.active()).isTrue();

          documentTemplateService.deactivate(created.id());
          var detail = documentTemplateService.getById(created.id());
          assertThat(detail.active()).isFalse();
        });
  }

  @Test
  void shouldCreateTemplateWithAdminRole() {
    syncMemberSafe(ORG_ID_A, "user_dt_admin_a", "dt_admin_a@test.com", "DT Admin A", "admin");

    // Use owner member but with admin role — RBAC is checked by @PreAuthorize on controller
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdOwnerA,
        "admin",
        () -> {
          var request =
              new DocumentTemplateController.CreateTemplateRequest(
                  "Admin Created",
                  null,
                  TemplateCategory.OTHER,
                  TemplateEntityType.CUSTOMER,
                  "<p>Admin template</p>",
                  null,
                  "admin-created");
          var response = documentTemplateService.create(request);
          assertThat(response.name()).isEqualTo("Admin Created");
        });
  }

  @Test
  void shouldListByCategory() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdOwnerA,
        "owner",
        () -> {
          documentTemplateService.create(
              new DocumentTemplateController.CreateTemplateRequest(
                  "Cat Test 1",
                  null,
                  TemplateCategory.COVER_LETTER,
                  TemplateEntityType.PROJECT,
                  "<p>CL1</p>",
                  null,
                  "cat-test-1"));
          documentTemplateService.create(
              new DocumentTemplateController.CreateTemplateRequest(
                  "Cat Test 2",
                  null,
                  TemplateCategory.STATEMENT_OF_WORK,
                  TemplateEntityType.PROJECT,
                  "<p>SOW1</p>",
                  null,
                  "cat-test-2"));

          var coverLetters = documentTemplateService.listByCategory(TemplateCategory.COVER_LETTER);
          assertThat(coverLetters).isNotEmpty();
          assertThat(coverLetters).allMatch(t -> t.category().equals("COVER_LETTER"));
        });
  }

  @Test
  void shouldListByEntityType() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdOwnerA,
        "owner",
        () -> {
          documentTemplateService.create(
              new DocumentTemplateController.CreateTemplateRequest(
                  "Entity Test 1",
                  null,
                  TemplateCategory.PROPOSAL,
                  TemplateEntityType.INVOICE,
                  "<p>Inv1</p>",
                  null,
                  "entity-test-1"));

          var invoiceTemplates =
              documentTemplateService.listByEntityType(TemplateEntityType.INVOICE);
          assertThat(invoiceTemplates).isNotEmpty();
          assertThat(invoiceTemplates).allMatch(t -> t.primaryEntityType().equals("INVOICE"));
        });
  }

  // --- Helpers ---

  private void runInTenant(
      String schema, String orgId, UUID memberId, String role, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, role)
        .run(action);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private void syncMemberSafe(
      String orgId, String clerkUserId, String email, String name, String orgRole) {
    try {
      syncMember(orgId, clerkUserId, email, name, orgRole);
    } catch (Exception e) {
      // Member may already exist from a previous test run
    }
  }
}
