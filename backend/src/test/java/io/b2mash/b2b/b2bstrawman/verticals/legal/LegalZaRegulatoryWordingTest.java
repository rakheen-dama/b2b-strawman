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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * G89-F6 / D12: the shipped legal content cites the Legal Practice Act 28 of 2014 (trust accounts
 * under section 86, the LPC Rules) — never the repealed Attorneys Act 53 of 1979 or its section 78.
 * Greps every shipped template pack, clause pack and AI skill prompt for the old citations. A
 * pack's ADR-356 {@code reconcile} block is out of scope — its {@code from} values are the
 * previously shipped (old) wording by contract — and so are the historical reconcile fixtures under
 * {@code src/test/resources/fixtures/**} (they are not on the main classpath).
 */
class LegalZaRegulatoryWordingTest {

  private static final List<String> SHIPPED_CONTENT =
      List.of(
          "classpath*:template-packs/**/*.json",
          "classpath*:clause-packs/**/*.json",
          "classpath*:ai/skills/**/*.txt");

  private static final ObjectMapper JSON = new ObjectMapper();

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
        String text = withoutReconcileBlock(resource);
        var matcher = OLD_CITATION.matcher(text);
        while (matcher.find()) {
          int from = Math.max(0, matcher.start() - 40);
          int to = Math.min(text.length(), matcher.end() + 40);
          hits.add("%s: ...%s...".formatted(resource.getFilename(), text.substring(from, to)));
        }
      }
    }
    assertThat(scanned).as("shipped legal content files scanned").isGreaterThan(20);
    assertThat(hits).as("old Attorneys Act / section 78 citations").isEmpty();
  }

  /** The resource text; for a JSON pack, re-serialised without its {@code reconcile} block. */
  private static String withoutReconcileBlock(Resource resource) throws IOException {
    String text;
    try (InputStream in = resource.getInputStream()) {
      text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    if (!String.valueOf(resource.getFilename()).endsWith(".json")) {
      return text;
    }
    JsonNode tree = JSON.readTree(text);
    if (tree instanceof ObjectNode object) {
      object.remove("reconcile");
    }
    return tree.toString();
  }
}
