package io.b2mash.b2b.b2bstrawman.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Bans the duplicated tenant-scope-binding helper method names that were consolidated into {@code
 * RequestScopes.runForTenant} / {@code callForTenant} on 2026-05-02.
 *
 * <p>If a future contributor reintroduces a method named {@code handleInTenantScope}, {@code
 * runInTenantScope}, {@code executeInTenantScope}, or {@code withTenantScope} anywhere outside the
 * {@code multitenancy} package, this rule fails the build. Bind tenant scope via the canonical
 * static API on {@code RequestScopes} — see ADR-T008.
 *
 * <p>The companion rule banning direct {@code ScopedValue.where(RequestScopes.TENANT_ID, ...)}
 * calls outside {@code RequestScopes} ships with PR #2 (jobs migration) — it cannot ship now
 * without breaking the 13 unmigrated scheduled jobs.
 *
 * <p>Implementation note: scans {@code target/classes/} directly using the JDK 25 native {@link
 * ClassFile} API rather than ArchUnit's DSL. ArchUnit 1.3.0's class-location resolution silently
 * imports zero classes on JDK 25 in this project's test setup, which would let the rule pass
 * vacuously and defeat its regression-guard purpose. The direct scan is unambiguous and has no
 * external-library dependency. (The codebase's existing ArchUnit-based rules in {@code
 * LayerDependencyRulesTest} / {@code TestConventionsTest} have the same latent silent-pass issue;
 * fixing them is out of scope for this PR — see ADR-T008 follow-ups.)
 */
class TenantScopeBindingTest {

  private static final Set<String> BANNED_HELPER_NAMES =
      Set.of("handleInTenantScope", "runInTenantScope", "executeInTenantScope", "withTenantScope");

  private static final String MULTITENANCY_PACKAGE_PATH = "io/b2mash/b2b/b2bstrawman/multitenancy/";

  @Test
  void no_banned_tenant_scope_helpers_outside_multitenancy_package() throws IOException {
    Path productionClasses = Paths.get("target/classes");
    assertThat(productionClasses)
        .as(
            "target/classes/ must exist; surefire should have compiled production code before "
                + "running tests")
        .exists();

    List<String> violations = new ArrayList<>();
    long classesScanned;
    ClassFile parser = ClassFile.of();

    try (Stream<Path> walk = Files.walk(productionClasses)) {
      classesScanned =
          walk.filter(p -> p.toString().endsWith(".class"))
              .peek(
                  classPath -> {
                    String relative = productionClasses.relativize(classPath).toString();
                    // Skip multitenancy package — this is where RequestScopes lives.
                    if (relative.replace('\\', '/').startsWith(MULTITENANCY_PACKAGE_PATH)) {
                      return;
                    }
                    try {
                      ClassModel model = parser.parse(Files.readAllBytes(classPath));
                      String className = model.thisClass().asInternalName().replace('/', '.');
                      model
                          .methods()
                          .forEach(
                              method -> {
                                String methodName = method.methodName().stringValue();
                                if (BANNED_HELPER_NAMES.contains(methodName)) {
                                  violations.add(className + "#" + methodName);
                                }
                              });
                    } catch (IOException e) {
                      throw new RuntimeException("Failed to parse class file: " + classPath, e);
                    }
                  })
              .count();
    }

    // Sanity: confirm the scan actually saw production classes. Without this, a misconfigured
    // working directory could silently scan zero classes and let the rule pass vacuously.
    assertThat(classesScanned)
        .as("Scan must find production classes in target/classes/")
        .isGreaterThan(100);

    assertThat(violations)
        .as(
            "Tenant-scope binding must go through RequestScopes.runForTenant / callForTenant. "
                + "Found %d class(es) declaring a banned helper method (one of %s) outside the "
                + "multitenancy package. See ADR-T008.",
            violations.size(), BANNED_HELPER_NAMES)
        .isEmpty();
  }
}
