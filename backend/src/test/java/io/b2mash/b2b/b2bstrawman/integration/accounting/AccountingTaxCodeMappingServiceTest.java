package io.b2mash.b2b.b2bstrawman.integration.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingTaxCodeMappingServiceTest {

  private static final String ORG_ID = "org_tax_mapping_test";

  @Autowired private AccountingTaxCodeMappingService taxCodeMappingService;
  @Autowired private AccountingTaxCodeMappingRepository taxCodeMappingRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Tax Mapping Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

  @Test
  void getByProvider_and_update_crudOperations() {
    runInTenant(
        () -> {
          // V121 migration seeds 4 ZA default rows for "xero" — use those directly
          var results = taxCodeMappingService.getByProvider("xero");
          assertThat(results).hasSize(4);
          var found =
              results.stream()
                  .filter(m -> m.getKaziTaxMode().equals("STANDARD_15"))
                  .findFirst()
                  .orElseThrow();
          assertThat(found.getExternalTaxCode()).isEqualTo("OUTPUT2");
          assertThat(found.getDisplayLabel()).isEqualTo("Standard Rate (15%)");

          // Update the mapping
          var updated =
              taxCodeMappingService.update(found.getId(), "TAX002", "Updated Standard Rate");
          assertThat(updated.getExternalTaxCode()).isEqualTo("TAX002");
          assertThat(updated.getDisplayLabel()).isEqualTo("Updated Standard Rate");

          // Verify persistence
          var reloaded = taxCodeMappingRepository.findById(found.getId()).orElseThrow();
          assertThat(reloaded.getExternalTaxCode()).isEqualTo("TAX002");
        });
  }

  @Test
  void resetToDefaults_restoresZaSeedData() {
    runInTenant(
        () -> {
          // Insert a mapping using a valid kazi_tax_mode (per CHECK constraint) to verify
          // that resetToDefaults replaces all rows including non-default ones
          var custom =
              new AccountingTaxCodeMapping(
                  "xero", "STANDARD_OTHER", "CUSTOM_CODE", "Custom Other Rate", false);
          taxCodeMappingRepository.save(custom);

          // Should now have 5 rows (4 seeded + 1 custom)
          assertThat(taxCodeMappingRepository.findByProviderId("xero")).hasSize(5);

          // Reset to defaults
          var defaults = taxCodeMappingService.resetToDefaults("xero");

          assertThat(defaults).hasSize(4);

          // Verify the 4 ZA default rows
          var taxModes = defaults.stream().map(AccountingTaxCodeMapping::getKaziTaxMode).toList();
          assertThat(taxModes)
              .containsExactlyInAnyOrder("STANDARD_15", "ZERO_RATED", "EXEMPT", "OUT_OF_SCOPE");

          // Verify specific mapping values
          var standard =
              defaults.stream()
                  .filter(m -> m.getKaziTaxMode().equals("STANDARD_15"))
                  .findFirst()
                  .orElseThrow();
          assertThat(standard.getExternalTaxCode()).isEqualTo("OUTPUT2");
          assertThat(standard.getDisplayLabel()).isEqualTo("Standard Rate (15%)");
          assertThat(standard.isDefault()).isTrue();

          var zeroRated =
              defaults.stream()
                  .filter(m -> m.getKaziTaxMode().equals("ZERO_RATED"))
                  .findFirst()
                  .orElseThrow();
          assertThat(zeroRated.getExternalTaxCode()).isEqualTo("ZERORATEDOUTPUT");

          // Verify the STANDARD_OTHER custom mapping is gone
          var allForProvider = taxCodeMappingRepository.findByProviderId("xero");
          assertThat(allForProvider).hasSize(4);
          assertThat(
                  allForProvider.stream()
                      .noneMatch(m -> m.getKaziTaxMode().equals("STANDARD_OTHER")))
              .isTrue();
        });
  }

  @Test
  void resolveForTaxMode_returnsMapping_or_throwsNotFound() {
    runInTenant(
        () -> {
          // Ensure defaults exist
          taxCodeMappingService.resetToDefaults("xero");

          // Successful resolve
          var resolved = taxCodeMappingService.resolveForTaxMode("xero", "EXEMPT");
          assertThat(resolved.getExternalTaxCode()).isEqualTo("EXEMPTOUTPUT");
          assertThat(resolved.getDisplayLabel()).isEqualTo("Exempt Output");

          // Not found case
          assertThatThrownBy(
                  () -> taxCodeMappingService.resolveForTaxMode("xero", "NONEXISTENT_MODE"))
              .isInstanceOf(ResourceNotFoundException.class);
        });
  }

  @Test
  void update_nonExistentId_throwsResourceNotFoundException() {
    runInTenant(
        () -> {
          assertThatThrownBy(() -> taxCodeMappingService.update(UUID.randomUUID(), "CODE", "Label"))
              .isInstanceOf(ResourceNotFoundException.class);
        });
  }
}
