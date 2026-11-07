package io.b2mash.b2b.b2bstrawman.verticals.legal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * G89-F6 / D12: the shipped legal content cites the Legal Practice Act 28 of 2014 (trust accounts
 * under section 86, the LPC Rules) — never the repealed Attorneys Act 53 of 1979 or its section 78.
 * Greps every shipped template pack, clause pack and AI skill prompt for the old citations. The
 * historical reconcile fixtures under {@code src/test/resources/fixtures/**} are {@code from}
 * states and are deliberately out of scope (they are not on the main classpath).
 */
class LegalZaRegulatoryWordingTest {

  private static final List<String> SHIPPED_CONTENT =
      List.of(
          "classpath*:template-packs/**/*.json",
          "classpath*:clause-packs/**/*.json",
          "classpath*:ai/skills/**/*.txt");

  /** The repealed Act by name, and its trust-account section in the forms the content used. */
  private static final Pattern OLD_CITATION =
      Pattern.compile(
          "Attorneys Act|78\\(1\\)|78\\(2\\)|Section 78\\b|\\bs78\\b", Pattern.CASE_INSENSITIVE);

  @Test
  void shippedLegalContent_citesTheLegalPracticeAct_neverTheAttorneysAct() throws IOException {
    var resolver = new PathMatchingResourcePatternResolver();
    var hits = new ArrayList<String>();
    int scanned = 0;
    for (String pattern : SHIPPED_CONTENT) {
      for (Resource resource : resolver.getResources(pattern)) {
        if (resource.getURL().getPath().contains("/fixtures/")) {
          continue;
        }
        scanned++;
        String[] lines = read(resource).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
          var matcher = OLD_CITATION.matcher(lines[i]);
          while (matcher.find()) {
            hits.add("%s:%d '%s'".formatted(resource.getFilename(), i + 1, matcher.group().trim()));
          }
        }
      }
    }
    assertThat(scanned).as("shipped legal content files scanned").isGreaterThan(20);
    assertThat(hits).as("old Attorneys Act / section 78 citations").isEmpty();
  }

  private static String read(Resource resource) throws IOException {
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
