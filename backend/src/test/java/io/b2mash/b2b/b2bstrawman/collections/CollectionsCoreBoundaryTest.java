package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ADR-329 boundary proof (592A.4): core {@code collections/} imports NOTHING from {@code
 * verticals/legal}. The trust-aware behaviour lives entirely behind the {@link CollectionsAdvisor}
 * SPI, contributed by a legal-vertical bean ({@code TrustAwareCollectionsAdvisor}) via Spring
 * list-injection — so core stays fork-neutral and the seam is enforced by test, not convention.
 *
 * <p>Plain JUnit source scan (no Spring context, no tenants) mirroring the {@code
 * Phase81BoundaryTest} walk/read/assert mechanics: read every {@code .java} under {@code
 * collections/} and assert none contains the {@code verticals.legal} package token. Scoped to
 * {@code collections/} only, per the task. The scanned file list is asserted non-empty so a
 * moved/renamed package fails loudly rather than silently passing.
 */
class CollectionsCoreBoundaryTest {

  @Test
  void coreCollectionsSourcesHaveNoLegalVerticalImports() throws IOException {
    Path collectionsDir =
        projectRoot().resolve("src/main/java/io/b2mash/b2b/b2bstrawman/collections");

    List<Path> sources;
    try (Stream<Path> walk = Files.walk(collectionsDir)) {
      sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
    }

    assertThat(sources)
        .as("core collections/ sources must exist (guard against a moved package)")
        .isNotEmpty();

    for (Path source : sources) {
      String content = Files.readString(source, StandardCharsets.UTF_8);
      assertThat(content)
          .as(
              "core collections source %s must not import verticals.legal (ADR-329)",
              source.getFileName())
          .doesNotContain("verticals.legal");
    }
  }

  private static Path projectRoot() {
    // Test working dir is the backend module root (where pom.xml + src/ live).
    return Path.of("").toAbsolutePath();
  }
}
