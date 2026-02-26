package io.b2mash.b2b.b2bstrawman.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.tax.dto.CreateTaxRateRequest;
import io.b2mash.b2b.b2bstrawman.tax.dto.UpdateTaxRateRequest;
import java.math.BigDecimal;
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
class TaxRateServiceTest {

  private static final String ORG_ID = "org_tax_svc_test";

  @Autowired private TaxRateService taxRateService;
  @Autowired private TaxRateRepository taxRateRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Tax Rate Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void createTaxRate_savesAndReturnsResponse() {
    var req = new CreateTaxRateRequest("New Rate", new BigDecimal("10.00"), false, false, 99);
    var response = runInTenant(() -> taxRateService.createTaxRate(req));

    assertThat(response.id()).isNotNull();
    assertThat(response.name()).isEqualTo("New Rate");
    assertThat(response.rate()).isEqualByComparingTo(new BigDecimal("10.00"));
    assertThat(response.isDefault()).isFalse();
    assertThat(response.isExempt()).isFalse();
    assertThat(response.active()).isTrue();
    assertThat(response.sortOrder()).isEqualTo(99);
    assertThat(response.createdAt()).isNotNull();
    assertThat(response.updatedAt()).isNotNull();
  }

  @Test
  void createTaxRate_withDuplicateName_throwsConflict() {
    var req1 = new CreateTaxRateRequest("Unique Rate 1", new BigDecimal("5.00"), false, false, 50);
    runInTenant(() -> taxRateService.createTaxRate(req1));

    var req2 = new CreateTaxRateRequest("Unique Rate 1", new BigDecimal("7.00"), false, false, 51);
    assertThatThrownBy(() -> runInTenant(() -> taxRateService.createTaxRate(req2)))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void createTaxRate_withExemptAndNonZeroRate_throwsInvalidState() {
    var req = new CreateTaxRateRequest("Bad Exempt", new BigDecimal("5.00"), false, true, 50);
    assertThatThrownBy(() -> runInTenant(() -> taxRateService.createTaxRate(req)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void createTaxRate_withDefault_clearsExistingDefault() {
    // Create first default
    var req1 = new CreateTaxRateRequest("First Default", new BigDecimal("10.00"), true, false, 97);
    runInTenant(() -> taxRateService.createTaxRate(req1));

    // Create a new default — should clear the old one
    var req2 = new CreateTaxRateRequest("New Default", new BigDecimal("20.00"), true, false, 98);
    var response = runInTenant(() -> taxRateService.createTaxRate(req2));

    assertThat(response.isDefault()).isTrue();

    // The current default should be the new one
    var currentDefault = runInTenant(() -> taxRateRepository.findByIsDefaultTrue().orElseThrow());
    assertThat(currentDefault.getName()).isEqualTo("New Default");
  }

  @Test
  void updateTaxRate_updatesFields() {
    var created =
        runInTenant(
            () ->
                taxRateService.createTaxRate(
                    new CreateTaxRateRequest(
                        "To Update", new BigDecimal("8.00"), false, false, 60)));

    var updateReq =
        new UpdateTaxRateRequest("Updated Name", new BigDecimal("9.00"), false, false, true, 61);
    var updated = runInTenant(() -> taxRateService.updateTaxRate(created.id(), updateReq));

    assertThat(updated.name()).isEqualTo("Updated Name");
    assertThat(updated.rate()).isEqualByComparingTo(new BigDecimal("9.00"));
    assertThat(updated.sortOrder()).isEqualTo(61);
  }

  @Test
  void updateTaxRate_withDuplicateName_throwsConflict() {
    runInTenant(
        () ->
            taxRateService.createTaxRate(
                new CreateTaxRateRequest("Rate Alpha", new BigDecimal("3.00"), false, false, 70)));
    var beta =
        runInTenant(
            () ->
                taxRateService.createTaxRate(
                    new CreateTaxRateRequest(
                        "Rate Beta", new BigDecimal("4.00"), false, false, 71)));

    var updateReq =
        new UpdateTaxRateRequest("Rate Alpha", new BigDecimal("4.00"), false, false, true, 71);
    assertThatThrownBy(() -> runInTenant(() -> taxRateService.updateTaxRate(beta.id(), updateReq)))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void updateTaxRate_withNonExistentId_throwsNotFound() {
    var updateReq =
        new UpdateTaxRateRequest("Ghost Rate", new BigDecimal("5.00"), false, false, true, 80);
    assertThatThrownBy(
            () -> runInTenant(() -> taxRateService.updateTaxRate(UUID.randomUUID(), updateReq)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void updateTaxRate_withExemptAndNonZeroRate_throwsInvalidState() {
    var created =
        runInTenant(
            () ->
                taxRateService.createTaxRate(
                    new CreateTaxRateRequest(
                        "Update Exempt Test", new BigDecimal("2.00"), false, false, 90)));

    var updateReq =
        new UpdateTaxRateRequest(
            "Update Exempt Test", new BigDecimal("5.00"), false, true, true, 90);
    assertThatThrownBy(
            () -> runInTenant(() -> taxRateService.updateTaxRate(created.id(), updateReq)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void deactivateTaxRate_setsActiveToFalse() {
    var created =
        runInTenant(
            () ->
                taxRateService.createTaxRate(
                    new CreateTaxRateRequest(
                        "To Deactivate", new BigDecimal("6.00"), false, false, 95)));

    runInTenant(
        () -> {
          taxRateService.deactivateTaxRate(created.id());
          return null;
        });

    var found = runInTenant(() -> taxRateRepository.findById(created.id()).orElseThrow());
    assertThat(found.isActive()).isFalse();
    assertThat(found.isDefault()).isFalse();
  }

  @Test
  void deactivateTaxRate_withNonExistentId_throwsNotFound() {
    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> {
                      taxRateService.deactivateTaxRate(UUID.randomUUID());
                      return null;
                    }))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void listTaxRates_withIncludeInactive_returnsAll() {
    var created =
        runInTenant(
            () ->
                taxRateService.createTaxRate(
                    new CreateTaxRateRequest(
                        "Inactive Rate A", new BigDecimal("1.00"), false, false, 97)));
    runInTenant(
        () -> {
          taxRateService.deactivateTaxRate(created.id());
          return null;
        });

    var all = runInTenant(() -> taxRateService.listTaxRates(true));
    assertThat(all).anyMatch(r -> r.id().equals(created.id()));
  }

  @Test
  void listTaxRates_withoutIncludeInactive_returnsActiveOnly() {
    var created =
        runInTenant(
            () ->
                taxRateService.createTaxRate(
                    new CreateTaxRateRequest(
                        "Inactive Rate B", new BigDecimal("1.50"), false, false, 96)));
    runInTenant(
        () -> {
          taxRateService.deactivateTaxRate(created.id());
          return null;
        });

    var active = runInTenant(() -> taxRateService.listTaxRates(false));
    assertThat(active).noneMatch(r -> r.id().equals(created.id()));
  }

  // --- Helpers ---

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
}
