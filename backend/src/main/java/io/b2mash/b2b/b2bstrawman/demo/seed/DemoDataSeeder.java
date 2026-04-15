package io.b2mash.b2b.b2bstrawman.demo.seed;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dispatches demo data seeding to the correct profile-specific seeder based on the vertical
 * profile. Falls back to the generic seeder for unknown profiles.
 */
@Service
public class DemoDataSeeder {

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  private final GenericDemoDataSeeder genericSeeder;
  private final AccountingDemoDataSeeder accountingSeeder;
  private final LegalDemoDataSeeder legalSeeder;

  public DemoDataSeeder(
      GenericDemoDataSeeder genericSeeder,
      AccountingDemoDataSeeder accountingSeeder,
      LegalDemoDataSeeder legalSeeder) {
    this.genericSeeder = genericSeeder;
    this.accountingSeeder = accountingSeeder;
    this.legalSeeder = legalSeeder;
  }

  /**
   * Seeds demo data for the given tenant using the appropriate profile-specific seeder.
   *
   * @param schemaName the tenant schema name (e.g. "tenant_a1b2c3d4e5f6")
   * @param orgId the organization UUID
   * @param verticalProfile the vertical profile (e.g. "generic", "accounting", "legal")
   */
  public void seed(String schemaName, UUID orgId, String verticalProfile) {
    log.info(
        "Dispatching demo data seeding for profile '{}' in schema {}", verticalProfile, schemaName);

    BaseDemoDataSeeder seeder =
        switch (verticalProfile == null ? "" : verticalProfile.toLowerCase()) {
          case "accounting-za" -> accountingSeeder;
          case "legal-za" -> legalSeeder;
          default -> genericSeeder;
        };
    seeder.seed(schemaName, orgId);
  }
}
