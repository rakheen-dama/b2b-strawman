package io.b2mash.b2b.b2bstrawman.clause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.dto.CreateClauseRequest;
import io.b2mash.b2b.b2bstrawman.clause.dto.UpdateClauseRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClauseServiceTest {

  private static final String ORG_ID = "org_clause_svc_test";

  private static final Map<String, Object> BODY =
      Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph")));

  private static final Map<String, Object> UPDATED_BODY =
      Map.of(
          "type",
          "doc",
          "content",
          List.of(
              Map.of(
                  "type",
                  "paragraph",
                  "content",
                  List.of(Map.of("type", "text", "text", "Updated")))));

  @Autowired private ClauseService clauseService;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Clause Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void createClause_savesAndReturnsResponse() {
    var req = new CreateClauseRequest("Test Clause", "A test clause", BODY, "general");
    var response = runInTenant(() -> clauseService.createClause(req));

    assertThat(response.id()).isNotNull();
    assertThat(response.title()).isEqualTo("Test Clause");
    assertThat(response.slug()).isEqualTo("test-clause");
    assertThat(response.description()).isEqualTo("A test clause");
    assertThat(response.body()).containsEntry("type", "doc");
    assertThat(response.category()).isEqualTo("general");
    assertThat(response.source()).isEqualTo(ClauseSource.CUSTOM);
    assertThat(response.active()).isTrue();
  }

  @Test
  void createClause_generatesUniqueSlug_whenDuplicate() {
    var req1 = new CreateClauseRequest("Duplicate Slug", null, BODY, "general");
    var resp1 = runInTenant(() -> clauseService.createClause(req1));
    assertThat(resp1.slug()).isEqualTo("duplicate-slug");

    var req2 = new CreateClauseRequest("Duplicate Slug", null, BODY, "general");
    var resp2 = runInTenant(() -> clauseService.createClause(req2));
    assertThat(resp2.slug()).isEqualTo("duplicate-slug-2");
  }

  @Test
  void updateClause_updatesFieldsAndSlug() {
    var createReq = new CreateClauseRequest("Update Me", null, BODY, "general");
    var created = runInTenant(() -> clauseService.createClause(createReq));

    var updateReq = new UpdateClauseRequest("Updated Title", "Updated desc", UPDATED_BODY, "legal");
    var updated = runInTenant(() -> clauseService.updateClause(created.id(), updateReq));

    assertThat(updated.title()).isEqualTo("Updated Title");
    assertThat(updated.slug()).isEqualTo("updated-title");
    assertThat(updated.description()).isEqualTo("Updated desc");
    assertThat(updated.body()).containsEntry("type", "doc");
    assertThat(updated.category()).isEqualTo("legal");
  }

  @Test
  void updateClause_blocksSystemSource() {
    var createReq = new CreateClauseRequest("System Clause", null, BODY, "general");
    var created = runInTenant(() -> clauseService.createClause(createReq));

    // Update source to SYSTEM directly in DB
    runInTenant(
        () -> {
          jdbcTemplate.update("UPDATE clauses SET source = 'SYSTEM' WHERE id = ?", created.id());
          return null;
        });

    var updateReq = new UpdateClauseRequest("New Title", null, BODY, "general");
    assertThatThrownBy(() -> runInTenant(() -> clauseService.updateClause(created.id(), updateReq)))
        .isInstanceOf(io.b2mash.b2b.b2bstrawman.exception.InvalidStateException.class);
  }

  @Test
  void deleteClause_removesClause() {
    var createReq = new CreateClauseRequest("Delete Me", null, BODY, "general");
    var created = runInTenant(() -> clauseService.createClause(createReq));

    runInTenantVoid(() -> clauseService.deleteClause(created.id()));

    assertThatThrownBy(() -> runInTenant(() -> clauseService.getById(created.id())))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void deleteClause_throwsConflict_whenReferenced() {
    var createReq = new CreateClauseRequest("Referenced Clause", null, BODY, "general");
    var created = runInTenant(() -> clauseService.createClause(createReq));

    // Insert a document_template first (FK requires valid template_id), then reference the clause
    runInTenant(
        () -> {
          var templateId = UUID.randomUUID();
          jdbcTemplate.update(
              "INSERT INTO document_templates (id, name, slug, category, primary_entity_type, content, source, active, created_at, updated_at) "
                  + "VALUES (?, 'Test Template', 'test-template-ref', 'PROPOSAL', 'PROJECT', '{\"type\":\"doc\",\"content\":[]}'::jsonb, 'ORG_CUSTOM', true, now(), now())",
              templateId);
          jdbcTemplate.update(
              "INSERT INTO template_clauses (template_id, clause_id, sort_order) VALUES (?, ?, 0)",
              templateId,
              created.id());
          return null;
        });

    assertThatThrownBy(() -> runInTenantVoid(() -> clauseService.deleteClause(created.id())))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void deactivateClause_setsInactive() {
    var createReq = new CreateClauseRequest("Deactivate Me", null, BODY, "general");
    var created = runInTenant(() -> clauseService.createClause(createReq));
    assertThat(created.active()).isTrue();

    var deactivated = runInTenant(() -> clauseService.deactivateClause(created.id()));
    assertThat(deactivated.active()).isFalse();
  }

  @Test
  void cloneClause_createsClonedCopy() {
    var createReq = new CreateClauseRequest("Original Clause", "Original desc", BODY, "general");
    var original = runInTenant(() -> clauseService.createClause(createReq));

    var cloned = runInTenant(() -> clauseService.cloneClause(original.id()));

    assertThat(cloned.id()).isNotEqualTo(original.id());
    assertThat(cloned.title()).isEqualTo("Copy of Original Clause");
    assertThat(cloned.slug()).startsWith("copy-of-original-clause");
    assertThat(cloned.source()).isEqualTo(ClauseSource.CLONED);
    assertThat(cloned.sourceClauseId()).isEqualTo(original.id());
    assertThat(cloned.body()).isEqualTo(original.body());
    assertThat(cloned.category()).isEqualTo(original.category());
  }

  @Test
  void listClauses_returnsActiveOnly_byDefault() {
    var req1 = new CreateClauseRequest("List Active 1", null, BODY, "list-test");
    var req2 = new CreateClauseRequest("List Active 2", null, BODY, "list-test");
    var created1 = runInTenant(() -> clauseService.createClause(req1));
    runInTenant(() -> clauseService.createClause(req2));
    runInTenant(() -> clauseService.deactivateClause(created1.id()));

    var activeClauses = runInTenant(() -> clauseService.listClauses(false, "list-test"));
    var allClauses = runInTenant(() -> clauseService.listClauses(true, "list-test"));

    assertThat(activeClauses.size()).isLessThan(allClauses.size());
  }

  @Test
  void listClauses_filtersByCategory() {
    var req = new CreateClauseRequest("Category Filter", null, BODY, "unique-category-test");
    runInTenant(() -> clauseService.createClause(req));

    var result = runInTenant(() -> clauseService.listClauses(false, "unique-category-test"));
    assertThat(result).allMatch(c -> c.category().equals("unique-category-test"));
  }

  @Test
  void listCategories_returnsDistinctActiveCategories() {
    var req1 = new CreateClauseRequest("Cat A Clause", null, BODY, "cat-a-unique");
    var req2 = new CreateClauseRequest("Cat B Clause", null, BODY, "cat-b-unique");
    runInTenant(() -> clauseService.createClause(req1));
    runInTenant(() -> clauseService.createClause(req2));

    var categories = runInTenant(() -> clauseService.listCategories());
    assertThat(categories).contains("cat-a-unique", "cat-b-unique");
  }

  @Test
  void getById_throwsNotFound_whenMissing() {
    var randomId = UUID.randomUUID();
    assertThatThrownBy(() -> runInTenant(() -> clauseService.getById(randomId)))
        .isInstanceOf(RuntimeException.class);
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
