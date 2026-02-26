package io.b2mash.b2b.b2bstrawman.tax;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
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
class TaxRateRepositoryIntegrationTest {

  private static final String ORG_ID = "org_tax_rate_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private TaxRateRepository taxRateRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void provisionTenant() {
    tenantSchema = provisioningService.provisionTenant(ORG_ID, "Tax Rate Test Org").schemaName();
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
                      var rate =
                          new TaxRate("Custom Rate", new BigDecimal("12.50"), false, false, 10);
                      var saved = taxRateRepository.save(rate);
                      assertThat(saved.getId()).isNotNull();

                      var found = taxRateRepository.findById(saved.getId());
                      assertThat(found).isPresent();
                      assertThat(found.get().getName()).isEqualTo("Custom Rate");
                      assertThat(found.get().getRate())
                          .isEqualByComparingTo(new BigDecimal("12.50"));
                      assertThat(found.get().isDefault()).isFalse();
                      assertThat(found.get().isExempt()).isFalse();
                      assertThat(found.get().isActive()).isTrue();
                      assertThat(found.get().getSortOrder()).isEqualTo(10);
                      assertThat(found.get().getCreatedAt()).isNotNull();
                      assertThat(found.get().getUpdatedAt()).isNotNull();
                    }));
  }

  @Test
  void findByActiveOrderBySortOrder_returns_active_only() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var inactive =
                          new TaxRate("Inactive Rate", new BigDecimal("5.00"), false, false, 99);
                      inactive.deactivate();
                      var inactiveRate = taxRateRepository.save(inactive);

                      var activeRates = taxRateRepository.findByActiveOrderBySortOrder(true);
                      assertThat(activeRates).isNotEmpty();
                      assertThat(activeRates)
                          .allMatch(TaxRate::isActive)
                          .extracting(TaxRate::getSortOrder)
                          .isSorted();
                      assertThat(activeRates)
                          .extracting(TaxRate::getId)
                          .doesNotContain(inactiveRate.getId());
                    }));
  }

  @Test
  void findAllByOrderBySortOrder_includes_inactive() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var inactive =
                          new TaxRate(
                              "Inactive For All Test", new BigDecimal("7.00"), false, false, 50);
                      inactive.deactivate();
                      taxRateRepository.save(inactive);

                      var all = taxRateRepository.findAllByOrderBySortOrder();
                      // Should include both active and inactive rates
                      assertThat(all).hasSizeGreaterThanOrEqualTo(4); // 3 seed rates + 1 inactive
                      assertThat(all).extracting(TaxRate::getSortOrder).isSorted();
                      assertThat(all).anyMatch(r -> !r.isActive());
                    }));
  }

  @Test
  void findByIsDefaultTrue_returns_default() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var defaultRate = taxRateRepository.findByIsDefaultTrue();
                      assertThat(defaultRate).isPresent();
                      assertThat(defaultRate.get().isDefault()).isTrue();
                      assertThat(defaultRate.get().getName()).isEqualTo("Standard");
                    }));
  }

  @Test
  void existsByName_returns_true_for_existing() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      assertThat(taxRateRepository.existsByName("Standard")).isTrue();
                      assertThat(taxRateRepository.existsByName("Nonexistent")).isFalse();
                    }));
  }

  @Test
  void existsByNameAndIdNot_excludes_self() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var defaultRate = taxRateRepository.findByIsDefaultTrue();
                      assertThat(defaultRate).isPresent();
                      var standardId = defaultRate.get().getId();

                      // "Standard" name exists but not with a different ID — should be false
                      assertThat(taxRateRepository.existsByNameAndIdNot("Standard", standardId))
                          .isFalse();

                      // "Standard" name exists with a different ID — should be true
                      var zeroRated =
                          taxRateRepository.findByActiveOrderBySortOrder(true).stream()
                              .filter(r -> r.getName().equals("Zero-rated"))
                              .findFirst()
                              .orElseThrow();
                      assertThat(
                              taxRateRepository.existsByNameAndIdNot("Standard", zeroRated.getId()))
                          .isTrue();
                    }));
  }

  @Test
  void seed_data_creates_three_default_rates() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var activeRates = taxRateRepository.findByActiveOrderBySortOrder(true);

                      // Find the seed rates by name
                      var standard =
                          activeRates.stream()
                              .filter(r -> r.getName().equals("Standard"))
                              .findFirst();
                      var zeroRated =
                          activeRates.stream()
                              .filter(r -> r.getName().equals("Zero-rated"))
                              .findFirst();
                      var exempt =
                          activeRates.stream()
                              .filter(r -> r.getName().equals("Exempt"))
                              .findFirst();

                      assertThat(standard).isPresent();
                      assertThat(standard.get().getRate())
                          .isEqualByComparingTo(new BigDecimal("15.00"));
                      assertThat(standard.get().isDefault()).isTrue();
                      assertThat(standard.get().isExempt()).isFalse();
                      assertThat(standard.get().getSortOrder()).isZero();

                      assertThat(zeroRated).isPresent();
                      assertThat(zeroRated.get().getRate())
                          .isEqualByComparingTo(new BigDecimal("0.00"));
                      assertThat(zeroRated.get().isDefault()).isFalse();
                      assertThat(zeroRated.get().isExempt()).isFalse();
                      assertThat(zeroRated.get().getSortOrder()).isEqualTo(1);

                      assertThat(exempt).isPresent();
                      assertThat(exempt.get().getRate())
                          .isEqualByComparingTo(new BigDecimal("0.00"));
                      assertThat(exempt.get().isDefault()).isFalse();
                      assertThat(exempt.get().isExempt()).isTrue();
                      assertThat(exempt.get().getSortOrder()).isEqualTo(2);
                    }));
  }
}
