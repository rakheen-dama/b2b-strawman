package io.b2mash.b2b.b2bstrawman.demo.seed;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dispatches demo data seeding to the profile-specific seeder. Since Phase 86 (Epic 604, ADR-332)
 * {@code legal-za} is the only vertical profile, so it is the only seeder; any other profile id is
 * rejected (no silent generic fallback).
 */
@Service
public class DemoDataSeeder {

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  private final LegalDemoDataSeeder legalSeeder;

  public DemoDataSeeder(LegalDemoDataSeeder legalSeeder) {
    this.legalSeeder = legalSeeder;
  }

  /**
   * Seeds demo data for the given tenant using the appropriate profile-specific seeder.
   *
   * @param schemaName the tenant schema name (e.g. "tenant_a1b2c3d4e5f6")
   * @param orgId the organization UUID
   * @param verticalProfile the vertical profile — must be {@code "legal-za"}
   * @throws InvalidStateException if the profile is not {@code legal-za}
   */
  public void seed(String schemaName, UUID orgId, String verticalProfile) {
    log.info(
        "Dispatching demo data seeding for profile '{}' in schema {}", verticalProfile, schemaName);

    BaseDemoDataSeeder seeder =
        switch (verticalProfile == null ? "" : verticalProfile.toLowerCase()) {
          case VerticalProfileRegistry.DEFAULT_PROFILE_ID -> legalSeeder;
          default ->
              throw new InvalidStateException(
                  "Unknown vertical profile",
                  "No demo data seeder for vertical profile '%s'".formatted(verticalProfile));
        };
    seeder.seed(schemaName, orgId);
  }
}
