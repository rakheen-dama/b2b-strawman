package io.b2mash.b2b.b2bstrawman.clause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClauseRepositoryIntegrationTest {

  private static final String ORG_ID = "org_clause_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void provisionTenant() {
    tenantSchema = provisioningService.provisionTenant(ORG_ID, "Clause Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
  }

  @Test
  void save_and_findById() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var clause =
                          new Clause(
                              "Test Confidentiality",
                              "test-confidentiality",
                              "The parties agree to keep all information confidential.",
                              "General");
                      var saved = clauseRepository.save(clause);
                      assertThat(saved.getId()).isNotNull();

                      var found = clauseRepository.findById(saved.getId());
                      assertThat(found).isPresent();
                      assertThat(found.get().getTitle()).isEqualTo("Test Confidentiality");
                      assertThat(found.get().getSlug()).isEqualTo("test-confidentiality");
                      assertThat(found.get().getBody())
                          .isEqualTo("The parties agree to keep all information confidential.");
                      assertThat(found.get().getCategory()).isEqualTo("General");
                      assertThat(found.get().getSource()).isEqualTo(ClauseSource.CUSTOM);
                      assertThat(found.get().isActive()).isTrue();
                      assertThat(found.get().getSortOrder()).isZero();
                      assertThat(found.get().getCreatedAt()).isNotNull();
                      assertThat(found.get().getUpdatedAt()).isNotNull();
                    }));
  }

  @Test
  void findByActiveTrueOrderByCategoryAscSortOrderAsc_returns_active_only() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var active1 = new Clause("Active One", "active-one", "Body one", "Billing");
                      clauseRepository.save(active1);

                      var inactive =
                          new Clause(
                              "Inactive Clause", "inactive-clause", "Body inactive", "Billing");
                      inactive.deactivate();
                      var inactiveClause = clauseRepository.save(inactive);

                      var results =
                          clauseRepository.findByActiveTrueOrderByCategoryAscSortOrderAsc();
                      assertThat(results).isNotEmpty();
                      assertThat(results).allMatch(Clause::isActive);
                      assertThat(results)
                          .extracting(Clause::getId)
                          .doesNotContain(inactiveClause.getId());
                    }));
  }

  @Test
  void findBySlug_returns_clause() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      clauseRepository.save(
                          new Clause(
                              "Test Payment Terms",
                              "test-payment-terms",
                              "Payment is due within 30 days.",
                              "Financial"));

                      var found = clauseRepository.findBySlug("test-payment-terms");
                      assertThat(found).isPresent();
                      assertThat(found.get().getTitle()).isEqualTo("Test Payment Terms");
                    }));
  }

  @Test
  void findByCategoryAndActiveTrueOrderBySortOrderAsc_filters_by_category() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      clauseRepository.save(
                          new Clause(
                              "Liability Limit",
                              "liability-limit",
                              "Liability is limited to...",
                              "Legal"));
                      clauseRepository.save(
                          new Clause("Warranty", "warranty", "The provider warrants...", "Legal"));
                      clauseRepository.save(
                          new Clause(
                              "Scope", "test-scope-of-work", "The scope includes...", "Project"));

                      var legalClauses =
                          clauseRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc("Legal");
                      assertThat(legalClauses).hasSizeGreaterThanOrEqualTo(2);
                      assertThat(legalClauses).allMatch(c -> c.getCategory().equals("Legal"));
                    }));
  }

  @Test
  void findAllByOrderByCategoryAscSortOrderAsc_returns_all_including_inactive() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var inactive =
                          new Clause(
                              "Deactivated Clause",
                              "deactivated-clause",
                              "This clause is no longer active.",
                              "Archive");
                      inactive.deactivate();
                      clauseRepository.save(inactive);

                      var all = clauseRepository.findAllByOrderByCategoryAscSortOrderAsc();
                      assertThat(all).anyMatch(c -> !c.isActive());
                      assertThat(all).extracting(Clause::getCategory).isSorted();
                    }));
  }

  @Test
  void findDistinctActiveCategories_returns_unique_categories() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      clauseRepository.save(new Clause("Cat A1", "cat-a1", "Body", "CategoryA"));
                      clauseRepository.save(new Clause("Cat A2", "cat-a2", "Body", "CategoryA"));
                      clauseRepository.save(new Clause("Cat B1", "cat-b1", "Body", "CategoryB"));

                      var inactiveCatC = new Clause("Cat C1", "cat-c1", "Body", "CategoryC");
                      inactiveCatC.deactivate();
                      clauseRepository.save(inactiveCatC);

                      var categories = clauseRepository.findDistinctActiveCategories();
                      assertThat(categories).contains("CategoryA", "CategoryB");
                      assertThat(categories).doesNotContain("CategoryC");
                      // Verify no duplicates
                      assertThat(categories).doesNotHaveDuplicates();
                      assertThat(categories).isSorted();
                    }));
  }

  @Test
  void slug_unique_constraint_enforced() {
    // First transaction: save the original clause
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx ->
                        clauseRepository.save(
                            new Clause(
                                "Unique Slug Test", "unique-slug", "First clause", "Test"))));

    // Second transaction: attempt duplicate slug — should fail
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                    .where(RequestScopes.ORG_ID, ORG_ID)
                    .run(
                        () ->
                            transactionTemplate.executeWithoutResult(
                                tx -> {
                                  clauseRepository.save(
                                      new Clause(
                                          "Duplicate Slug Test",
                                          "unique-slug",
                                          "Second clause",
                                          "Test"));
                                  clauseRepository.flush();
                                })))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
