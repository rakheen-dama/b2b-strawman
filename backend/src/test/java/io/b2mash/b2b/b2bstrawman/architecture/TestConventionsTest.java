package io.b2mash.b2b.b2bstrawman.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the "no Testcontainers / no LocalStack" test policy documented in backend/CLAUDE.md.
 * Tests use Zonky embedded Postgres (via {@code TestcontainersConfiguration}, which despite its
 * name does NOT use Docker Testcontainers) and {@code InMemoryStorageService} for S3. Adding a
 * Docker-backed container to tests triggers cascading HikariPool failures under resource
 * constraints and slows the suite dramatically.
 *
 * <p>Unlike {@code LayerDependencyRulesTest}, this analyzer includes test classes because that's
 * where the policy applies.
 */
@AnalyzeClasses(
    packages = "io.b2mash.b2b.b2bstrawman",
    importOptions = ImportOption.OnlyIncludeTests.class)
class TestConventionsTest {

  @ArchTest
  static final ArchRule tests_must_not_use_postgres_container =
      noClasses()
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.testcontainers.containers.PostgreSQLContainer")
          .because(
              "Tests must use Zonky embedded Postgres via TestcontainersConfiguration — "
                  + "see backend/CLAUDE.md. Docker-backed containers cause cascading HikariPool "
                  + "failures under resource constraints.")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule tests_must_not_use_localstack_container =
      noClasses()
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.testcontainers.containers.localstack.LocalStackContainer")
          .because(
              "Tests must use InMemoryStorageService for S3 — see backend/CLAUDE.md. "
                  + "LocalStack containers cost 20+ seconds per test class to start.")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule tests_must_not_use_generic_testcontainers =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.testcontainers.containers..")
          .because(
              "Tests must not add any Testcontainers-backed infrastructure. Use the embedded "
                  + "alternatives provided by TestcontainersConfiguration or mock the dependency.")
          .allowEmptyShould(true);
}
