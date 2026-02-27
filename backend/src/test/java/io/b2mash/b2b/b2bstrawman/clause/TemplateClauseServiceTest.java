package io.b2mash.b2b.b2bstrawman.clause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.dto.TemplateClauseRequest;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplateClauseServiceTest {

  private static final String ORG_ID = "org_tc_svc_test";

  @Autowired private TemplateClauseService templateClauseService;
  @Autowired private TemplateClauseRepository templateClauseRepository;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID templateId;
  private UUID clause1Id;
  private UUID clause2Id;
  private UUID clause3Id;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "TC Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Seed test data within tenant schema
    runInTenantVoid(
        () -> {
          var template =
              new DocumentTemplate(
                  TemplateEntityType.PROJECT,
                  "TC Test Template",
                  "tc-test-template",
                  TemplateCategory.ENGAGEMENT_LETTER,
                  "<p>Template content</p>");
          template = documentTemplateRepository.save(template);
          templateId = template.getId();

          var c1 =
              new Clause("Clause One", "clause-one", "<p>Body of clause one here</p>", "general");
          c1 = clauseRepository.save(c1);
          clause1Id = c1.getId();

          var c2 =
              new Clause("Clause Two", "clause-two", "<p>Body of clause two here</p>", "general");
          c2 = clauseRepository.save(c2);
          clause2Id = c2.getId();

          var c3 =
              new Clause(
                  "Clause Three", "clause-three", "<p>Body of clause three here</p>", "payment");
          c3 = clauseRepository.save(c3);
          clause3Id = c3.getId();
        });
  }

  @Test
  void getTemplateClauses_returnsEmptyForNoAssociations() {
    runInTenantVoid(() -> templateClauseRepository.deleteAllByTemplateId(templateId));
    var result = runInTenant(() -> templateClauseService.getTemplateClauses(templateId));
    assertThat(result).isEmpty();
  }

  @Test
  void getTemplateClauses_throwsForNonexistentTemplate() {
    var fakeId = UUID.randomUUID();
    assertThatThrownBy(() -> runInTenant(() -> templateClauseService.getTemplateClauses(fakeId)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void setTemplateClauses_replacesAll() {
    runInTenantVoid(() -> templateClauseRepository.deleteAllByTemplateId(templateId));
    var requests =
        List.of(
            new TemplateClauseRequest(clause1Id, 0, true),
            new TemplateClauseRequest(clause2Id, 1, false));

    var result = runInTenant(() -> templateClauseService.setTemplateClauses(templateId, requests));

    assertThat(result).hasSize(2);
    assertThat(result.get(0).title()).isEqualTo("Clause One");
    assertThat(result.get(0).required()).isTrue();
    assertThat(result.get(0).sortOrder()).isEqualTo(0);
    assertThat(result.get(1).title()).isEqualTo("Clause Two");
    assertThat(result.get(1).required()).isFalse();

    // Replace with a different set
    var newRequests = List.of(new TemplateClauseRequest(clause3Id, 0, true));
    var newResult =
        runInTenant(() -> templateClauseService.setTemplateClauses(templateId, newRequests));

    assertThat(newResult).hasSize(1);
    assertThat(newResult.get(0).title()).isEqualTo("Clause Three");
  }

  @Test
  void setTemplateClauses_throwsForNonexistentTemplate() {
    var fakeId = UUID.randomUUID();
    var requests = List.of(new TemplateClauseRequest(clause1Id, 0, true));
    assertThatThrownBy(
            () -> runInTenant(() -> templateClauseService.setTemplateClauses(fakeId, requests)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void setTemplateClauses_throwsForNonexistentClause() {
    var fakeClauseId = UUID.randomUUID();
    var requests = List.of(new TemplateClauseRequest(fakeClauseId, 0, true));
    assertThatThrownBy(
            () -> runInTenant(() -> templateClauseService.setTemplateClauses(templateId, requests)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void addClauseToTemplate_addsAtEnd() {
    // Clear first
    runInTenantVoid(() -> templateClauseRepository.deleteAllByTemplateId(templateId));

    var detail1 =
        runInTenant(() -> templateClauseService.addClauseToTemplate(templateId, clause1Id, true));
    assertThat(detail1.clauseId()).isEqualTo(clause1Id);
    assertThat(detail1.sortOrder()).isEqualTo(0);
    assertThat(detail1.required()).isTrue();

    var detail2 =
        runInTenant(() -> templateClauseService.addClauseToTemplate(templateId, clause2Id, false));
    assertThat(detail2.sortOrder()).isEqualTo(1);
  }

  @Test
  void addClauseToTemplate_throwsDuplicateConflict() {
    // Ensure clause1 is already associated
    runInTenantVoid(() -> templateClauseRepository.deleteAllByTemplateId(templateId));
    runInTenant(() -> templateClauseService.addClauseToTemplate(templateId, clause1Id, false));

    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> templateClauseService.addClauseToTemplate(templateId, clause1Id, false)))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void removeClauseFromTemplate_deletes() {
    // Setup: add a clause
    runInTenantVoid(() -> templateClauseRepository.deleteAllByTemplateId(templateId));
    runInTenant(() -> templateClauseService.addClauseToTemplate(templateId, clause1Id, false));

    // Remove
    runInTenantVoid(() -> templateClauseService.removeClauseFromTemplate(templateId, clause1Id));

    var result = runInTenant(() -> templateClauseService.getTemplateClauses(templateId));
    assertThat(result).isEmpty();
  }

  @Test
  void removeClauseFromTemplate_idempotentForNonexistent() {
    // Should not throw even if the association does not exist
    runInTenantVoid(
        () -> templateClauseService.removeClauseFromTemplate(templateId, UUID.randomUUID()));
  }

  private <T> T runInTenant(Callable<T> callable) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.ORG_ID, ORG_ID)
          .call(
              () ->
                  transactionTemplate.execute(
                      tx -> {
                        try {
                          return callable.call();
                        } catch (RuntimeException e) {
                          throw e;
                        } catch (Exception e) {
                          throw new RuntimeException(e);
                        }
                      }));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void runInTenantVoid(Runnable runnable) {
    runInTenant(
        () -> {
          runnable.run();
          return null;
        });
  }
}
