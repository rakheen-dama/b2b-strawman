package io.b2mash.b2b.b2bstrawman.clause;

import static io.b2mash.b2b.b2bstrawman.template.TestDocumentBuilder.doc;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplateClauseSyncTest {

  private static final Map<String, Object> EMPTY_CONTENT =
      Map.of("type", "doc", "content", List.of());
  private static final Map<String, Object> BODY = Map.of("type", "doc", "content", List.of());
  private static final String ORG_ID = "org_tc_sync_test";

  @Autowired private TemplateClauseSync templateClauseSync;
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
    provisioningService.provisionTenant(ORG_ID, "TC Sync Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenantVoid(
        () -> {
          var template =
              new DocumentTemplate(
                  TemplateEntityType.PROJECT,
                  "Sync Test Template",
                  "sync-test-template",
                  TemplateCategory.ENGAGEMENT_LETTER,
                  EMPTY_CONTENT);
          template = documentTemplateRepository.save(template);
          templateId = template.getId();

          var c1 = new Clause("Sync Scope of Work", "sync-scope-of-work", BODY, "general");
          c1 = clauseRepository.save(c1);
          clause1Id = c1.getId();

          var c2 = new Clause("Sync Payment Terms", "sync-payment-terms", BODY, "payment");
          c2 = clauseRepository.save(c2);
          clause2Id = c2.getId();

          var c3 = new Clause("Sync Confidentiality", "sync-confidentiality", BODY, "legal");
          c3 = clauseRepository.save(c3);
          clause3Id = c3.getId();
        });
  }

  @Test
  void syncCreatesRowsForClauseBlockNodes() {
    runInTenantVoid(
        () -> {
          // Clear any existing associations
          templateClauseRepository.deleteAllByTemplateId(templateId);
          templateClauseRepository.flush();

          var content =
              doc()
                  .heading(1, "Engagement Letter")
                  .clauseBlock(clause1Id, "scope-of-work", "Scope of Work", true)
                  .clauseBlock(clause2Id, "payment-terms", "Payment Terms", false)
                  .build();

          templateClauseSync.syncClausesFromDocument(templateId, content);

          var rows = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
          assertThat(rows).hasSize(2);
          assertThat(rows.get(0).getClauseId()).isEqualTo(clause1Id);
          assertThat(rows.get(0).getSortOrder()).isEqualTo(0);
          assertThat(rows.get(0).isRequired()).isTrue();
          assertThat(rows.get(1).getClauseId()).isEqualTo(clause2Id);
          assertThat(rows.get(1).getSortOrder()).isEqualTo(1);
          assertThat(rows.get(1).isRequired()).isFalse();
        });
  }

  @Test
  void syncDeletesRemovedClauseBlocks() {
    runInTenantVoid(
        () -> {
          // Setup: 2 clause blocks
          templateClauseRepository.deleteAllByTemplateId(templateId);
          templateClauseRepository.flush();
          var initialContent =
              doc()
                  .clauseBlock(clause1Id, "scope-of-work", "Scope of Work", true)
                  .clauseBlock(clause2Id, "payment-terms", "Payment Terms", false)
                  .build();
          templateClauseSync.syncClausesFromDocument(templateId, initialContent);
          assertThat(templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId))
              .hasSize(2);

          // Remove clause2 from document
          var updatedContent =
              doc().clauseBlock(clause1Id, "scope-of-work", "Scope of Work", true).build();
          templateClauseSync.syncClausesFromDocument(templateId, updatedContent);

          var rows = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
          assertThat(rows).hasSize(1);
          assertThat(rows.get(0).getClauseId()).isEqualTo(clause1Id);
        });
  }

  @Test
  void syncCreatesNewlyAddedClauseBlocks() {
    runInTenantVoid(
        () -> {
          // Setup: 1 clause block
          templateClauseRepository.deleteAllByTemplateId(templateId);
          templateClauseRepository.flush();
          var initialContent =
              doc().clauseBlock(clause1Id, "scope-of-work", "Scope of Work", true).build();
          templateClauseSync.syncClausesFromDocument(templateId, initialContent);
          assertThat(templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId))
              .hasSize(1);

          // Add clause3
          var updatedContent =
              doc()
                  .clauseBlock(clause1Id, "scope-of-work", "Scope of Work", true)
                  .clauseBlock(clause3Id, "confidentiality", "Confidentiality", false)
                  .build();
          templateClauseSync.syncClausesFromDocument(templateId, updatedContent);

          var rows = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
          assertThat(rows).hasSize(2);
          assertThat(rows.get(0).getClauseId()).isEqualTo(clause1Id);
          assertThat(rows.get(1).getClauseId()).isEqualTo(clause3Id);
          assertThat(rows.get(1).getSortOrder()).isEqualTo(1);
        });
  }

  @Test
  void syncUpdatesSortOrderOnReorder() {
    runInTenantVoid(
        () -> {
          // Setup: clause1 at 0, clause2 at 1
          templateClauseRepository.deleteAllByTemplateId(templateId);
          templateClauseRepository.flush();
          var initialContent =
              doc()
                  .clauseBlock(clause1Id, "scope-of-work", "Scope of Work", true)
                  .clauseBlock(clause2Id, "payment-terms", "Payment Terms", false)
                  .build();
          templateClauseSync.syncClausesFromDocument(templateId, initialContent);

          // Capture original row IDs
          var originalRows =
              templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
          UUID row1Id = originalRows.get(0).getId();
          UUID row2Id = originalRows.get(1).getId();

          // Reorder: clause2 first, then clause1
          var reorderedContent =
              doc()
                  .clauseBlock(clause2Id, "payment-terms", "Payment Terms", false)
                  .clauseBlock(clause1Id, "scope-of-work", "Scope of Work", true)
                  .build();
          templateClauseSync.syncClausesFromDocument(templateId, reorderedContent);

          var rows = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
          assertThat(rows).hasSize(2);
          // clause2 is now first (sortOrder 0)
          assertThat(rows.get(0).getClauseId()).isEqualTo(clause2Id);
          assertThat(rows.get(0).getSortOrder()).isEqualTo(0);
          // clause1 is now second (sortOrder 1)
          assertThat(rows.get(1).getClauseId()).isEqualTo(clause1Id);
          assertThat(rows.get(1).getSortOrder()).isEqualTo(1);
          // Row IDs preserved (smart diff, not delete-all-recreate)
          assertThat(rows.get(0).getId()).isEqualTo(row2Id);
          assertThat(rows.get(1).getId()).isEqualTo(row1Id);
        });
  }

  @Test
  void syncUpdatesRequiredFlag() {
    runInTenantVoid(
        () -> {
          // Setup: clause1 required=true
          templateClauseRepository.deleteAllByTemplateId(templateId);
          templateClauseRepository.flush();
          var initialContent =
              doc().clauseBlock(clause1Id, "scope-of-work", "Scope of Work", true).build();
          templateClauseSync.syncClausesFromDocument(templateId, initialContent);

          var originalRow =
              templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId).get(0);
          assertThat(originalRow.isRequired()).isTrue();
          UUID rowId = originalRow.getId();

          // Toggle required to false
          var updatedContent =
              doc().clauseBlock(clause1Id, "scope-of-work", "Scope of Work", false).build();
          templateClauseSync.syncClausesFromDocument(templateId, updatedContent);

          var rows = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
          assertThat(rows).hasSize(1);
          assertThat(rows.get(0).isRequired()).isFalse();
          assertThat(rows.get(0).getId()).isEqualTo(rowId); // Row ID preserved
        });
  }

  @Test
  void syncDeletesAllRowsWhenNoClauseBlocks() {
    runInTenantVoid(
        () -> {
          // Setup: 2 clause blocks
          templateClauseRepository.deleteAllByTemplateId(templateId);
          templateClauseRepository.flush();
          var initialContent =
              doc()
                  .clauseBlock(clause1Id, "scope-of-work", "Scope of Work", true)
                  .clauseBlock(clause2Id, "payment-terms", "Payment Terms", false)
                  .build();
          templateClauseSync.syncClausesFromDocument(templateId, initialContent);
          assertThat(templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId))
              .hasSize(2);

          // Document with no clause blocks
          var emptyContent =
              doc().heading(1, "Engagement Letter").paragraph("Dear client...").build();
          templateClauseSync.syncClausesFromDocument(templateId, emptyContent);

          var rows = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
          assertThat(rows).isEmpty();
        });
  }

  @Test
  void syncDeduplicatesDuplicateClauseBlocks() {
    runInTenantVoid(
        () -> {
          templateClauseRepository.deleteAllByTemplateId(templateId);
          templateClauseRepository.flush();

          // Same clauseId appears twice (simulates copy-paste in editor)
          var content =
              doc()
                  .clauseBlock(clause1Id, "scope-of-work", "Scope of Work", true)
                  .clauseBlock(clause1Id, "scope-of-work", "Scope of Work", false)
                  .clauseBlock(clause2Id, "payment-terms", "Payment Terms", false)
                  .build();

          templateClauseSync.syncClausesFromDocument(templateId, content);

          var rows = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
          // Only 2 rows: clause1 (first occurrence wins) and clause2
          assertThat(rows).hasSize(2);
          assertThat(rows.get(0).getClauseId()).isEqualTo(clause1Id);
          assertThat(rows.get(0).isRequired()).isTrue(); // first occurrence wins
          assertThat(rows.get(1).getClauseId()).isEqualTo(clause2Id);
        });
  }

  @Test
  void syncSkipsNonExistentClauseIds() {
    runInTenantVoid(
        () -> {
          templateClauseRepository.deleteAllByTemplateId(templateId);
          templateClauseRepository.flush();

          UUID nonExistentId = UUID.randomUUID();
          var content =
              doc()
                  .clauseBlock(clause1Id, "scope-of-work", "Scope of Work", true)
                  .clauseBlock(nonExistentId, "ghost-clause", "Ghost Clause", false)
                  .clauseBlock(clause2Id, "payment-terms", "Payment Terms", false)
                  .build();

          templateClauseSync.syncClausesFromDocument(templateId, content);

          var rows = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
          // Only 2 rows: the non-existent clause is skipped
          assertThat(rows).hasSize(2);
          assertThat(rows.get(0).getClauseId()).isEqualTo(clause1Id);
          assertThat(rows.get(0).getSortOrder()).isEqualTo(0);
          assertThat(rows.get(1).getClauseId()).isEqualTo(clause2Id);
          assertThat(rows.get(1).getSortOrder()).isEqualTo(1);
        });
  }

  // --- runInTenant helpers ---

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
