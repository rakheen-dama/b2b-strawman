package io.b2mash.b2b.b2bstrawman.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.b2mash.b2b.b2bstrawman.compliance.ClientFicaAttestationService;
import io.b2mash.b2b.b2bstrawman.customer.dto.ClientListRow;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateComplianceSettingsRequest;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionEquation;
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
 * ADR-365 / P-17 wire-shape pin for the take-on DTOs (Phase 93 653A, extended per slice). Snapshots
 * the record components — name and generic type, in declaration order — of the take-on wire records
 * the frontend mirrors in {@code lib/types/take-on.ts}, and compares them byte-for-byte to {@code
 * src/test/resources/wire/take-on-contract.json}. The frontend twin {@code
 * frontend/__tests__/take-on-wire-shapes.test.ts} imports the same JSON.
 *
 * <p>The actual is always written to {@code target/wire/take-on-contract.actual.json}. Regenerate
 * ONLY deliberately with {@code -Dtakeon.contract.update=true}; the PR must say why and update the
 * frontend literal.
 */
class TakeOnWireContractTest {

  /**
   * The take-on wire records: 18 from 653A, + 5 from 655A (sign-off, attestation statement,
   * attestation and commit requests, portal-contacts response) = 23; each later slice adds the
   * records it introduces. 655A also appended {@code TakeOnBatchResponse.summary} (review B-H2: the
   * stored validation summary, so the attestation step never pages rows into the browser). 655B
   * adds the FICA wire records the review queue reads (C-10): the FICA status, the attestation
   * read, the client-list row and the compliance-settings request = 27. 656A adds the go-live
   * checklist item and its acknowledge request = 29. 657B appends {@code midMonthAcknowledged} and
   * {@code position} to {@code TakeOnBatchResponse}, extends {@code TakeOnPositionRequest} with the
   * reconciled position, and adds the position response and the eight records of a trust batch's
   * {@code summary} (pinned although {@code summary} is a map, so the frontend mirror is checked) =
   * 38.
   */
  static final List<Class<?>> PINNED =
      List.of(
          TakeOnBatchService.TakeOnFieldResponse.class,
          TakeOnBatchService.TakeOnSectionResponse.class,
          TakeOnBatchService.TakeOnDatasetResponse.class,
          TakeOnBatchService.TakeOnParseOptionsResponse.class,
          TakeOnBatchService.TakeOnMappingResponse.class,
          TakeOnBatchService.TakeOnSheetNameResponse.class,
          TakeOnBatchService.TakeOnSourceResponse.class,
          TakeOnBatchService.TakeOnCountsResponse.class,
          TakeOnBatchService.TakeOnBatchResponse.class,
          TakeOnBatchService.TakeOnBatchSummaryResponse.class,
          TakeOnBatchService.TakeOnUploadResponse.class,
          TakeOnBatchService.TakeOnPreviewRow.class,
          TakeOnBatchService.TakeOnPreviewResponse.class,
          TakeOnBatchService.TakeOnRowMessage.class,
          TakeOnBatchService.TakeOnRowResponse.class,
          TakeOnBatchService.TakeOnMappingRequest.class,
          TakeOnBatchService.TakeOnSheetRequest.class,
          TakeOnBatchService.TakeOnPositionRequest.class,
          // 655A
          TakeOnBatchService.TakeOnSignOffResponse.class,
          TakeOnBatchService.TakeOnAttestationStatementResponse.class,
          TakeOnBatchService.TakeOnAttestationRequest.class,
          TakeOnBatchService.TakeOnCommitRequest.class,
          TakeOnBatchService.TakeOnPortalContactsResponse.class,
          // 655B
          InformationRequestDtos.FicaStatusResponse.class,
          ClientFicaAttestationService.ClientFicaAttestationResponse.class,
          ClientListRow.class,
          UpdateComplianceSettingsRequest.class,
          // 656A
          TakeOnGoLiveService.TakeOnGoLiveItemResponse.class,
          TakeOnGoLiveService.TakeOnGoLiveAcknowledgeRequest.class,
          // 657B
          TakeOnBatchService.TakeOnPositionResponse.class,
          TrustPositionEquation.TrustPositionSummary.class,
          TrustPositionEquation.TrustEquationSummary.class,
          TrustPositionEquation.TrustEq1.class,
          TrustPositionEquation.TrustItemsTie.class,
          TrustPositionEquation.TrustEq2.class,
          TrustPositionEquation.TrustInvestmentsTie.class,
          TrustPositionEquation.TrustDeficit.class,
          TrustPositionEquation.TrustPositionMessage.class);

  private static final String UPDATE_FLAG = "takeon.contract.update";
  private static final Path SNAPSHOT = Path.of("src/test/resources/wire/take-on-contract.json");
  private static final Path ACTUAL = Path.of("target/wire/take-on-contract.actual.json");

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
          "[TakeOnWireContractTest] -D"
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
              "ADR-365: a pinned take-on wire record changed shape (the frontend mirrors it in"
                  + " lib/types/take-on.ts).%n"
                  + "Inspect: diff -u %s %s%n"
                  + "If intended, rerun with -D%s=true, update"
                  + " frontend/__tests__/take-on-wire-shapes.test.ts and the TS"
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
