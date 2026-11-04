package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * ADR-365 wire-shape pin for the compliance screens (G88-F43, G88-F44; G88-F38 for ConflictDetail).
 * Snapshots the record components — name and generic type, in declaration order — of the four
 * response records the frontend mirrors in {@code lib/types/data-protection.ts} / {@code
 * lib/types/legal.ts}, and compares them byte-for-byte to {@code
 * src/test/resources/wire/data-protection-contract.json}. The frontend twin {@code
 * frontend/__tests__/customers/data-protection-wire-shapes.test.ts} imports the same JSON and
 * asserts each TS literal's key list against it.
 *
 * <p>The actual is always written to {@code target/wire/data-protection-contract.actual.json}.
 * Regenerate ONLY deliberately with {@code -Ddataprotection.contract.update=true} ({@code
 * WireVocabularyContractTest} keeps its own {@code -Dwire.contract.update}); the PR that does so
 * must say why in its body and update the frontend literal.
 */
class DataProtectionWireContractTest {

  static final List<Class<?>> PINNED =
      List.of(
          ExportStatusResponse.class,
          AnonymizationPreview.class,
          DataAnonymizationService.AnonymizationResult.class,
          ConflictCheckService.ConflictDetail.class);

  private static final String UPDATE_FLAG = "dataprotection.contract.update";
  private static final Path SNAPSHOT =
      Path.of("src/test/resources/wire/data-protection-contract.json");
  private static final Path ACTUAL = Path.of("target/wire/data-protection-contract.actual.json");

  private static final JsonMapper CANONICAL =
      JsonMapper.builder()
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .build();

  @Test
  void pinnedRecordShapesMatchCommittedSnapshot() throws IOException {
    String actual = normalise(CANONICAL.writeValueAsString(recordShapes()));
    Files.createDirectories(ACTUAL.getParent());
    Files.writeString(ACTUAL, actual, StandardCharsets.UTF_8);

    if (Boolean.getBoolean(UPDATE_FLAG)) {
      Files.createDirectories(SNAPSHOT.getParent());
      Files.writeString(SNAPSHOT, actual, StandardCharsets.UTF_8);
      System.out.println(
          "[DataProtectionWireContractTest] -D"
              + UPDATE_FLAG
              + "=true: rewrote "
              + SNAPSHOT
              + " — the PR body must justify the change (ADR-365)");
      return;
    }

    assertThat(SNAPSHOT)
        .as("missing %s — generate with -D%s=true (ADR-365)", SNAPSHOT, UPDATE_FLAG)
        .exists();
    String expected = normalise(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
    if (!expected.equals(actual)) {
      fail(
          String.format(
              "ADR-365: a pinned compliance wire record changed shape (the frontend mirrors it in"
                  + " lib/types/data-protection.ts / lib/types/legal.ts).%n"
                  + "Inspect: diff -u %s %s%n"
                  + "If intended, rerun with -D%s=true, update"
                  + " frontend/__tests__/customers/data-protection-wire-shapes.test.ts and the TS"
                  + " type, and justify the change in the PR body.%n%s",
              SNAPSHOT, ACTUAL, UPDATE_FLAG, diff(expected, actual)));
    }
  }

  /** {@code SimpleName -> [{name, type}, ...]} in declaration order; keys sorted (TreeMap). */
  private static Map<String, List<Map<String, String>>> recordShapes() {
    Map<String, List<Map<String, String>>> shapes = new TreeMap<>();
    for (Class<?> c : PINNED) {
      assertThat(c.isRecord()).as("%s must stay a record (ADR-365)", c.getName()).isTrue();
      List<Map<String, String>> components = new ArrayList<>();
      for (RecordComponent rc : c.getRecordComponents()) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", rc.getName());
        m.put("type", rc.getGenericType().getTypeName());
        components.add(m);
      }
      assertThat(shapes.put(c.getSimpleName(), components))
          .as("duplicate simple name %s in PINNED", c.getSimpleName())
          .isNull();
    }
    return shapes;
  }

  private static String normalise(String s) {
    return s.replace("\r\n", "\n").stripTrailing() + "\n";
  }

  /** Positional line diff of the first 40 differing lines (copied from McpToolContractTest). */
  private static String diff(String expected, String actual) {
    List<String> e = expected.lines().toList();
    List<String> a = actual.lines().toList();
    StringBuilder sb = new StringBuilder();
    int shown = 0;
    for (int i = 0; i < Math.max(e.size(), a.size()) && shown < 40; i++) {
      String el = i < e.size() ? e.get(i) : null;
      String al = i < a.size() ? a.get(i) : null;
      if (Objects.equals(el, al)) {
        continue;
      }
      sb.append("@@ line ").append(i + 1).append('\n');
      if (el != null) {
        sb.append("- ").append(el).append('\n');
      }
      if (al != null) {
        sb.append("+ ").append(al).append('\n');
      }
      shown++;
    }
    return sb.toString();
  }
}
