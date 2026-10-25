package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * ADR-349 guard: {@code project_id} in audit details is a PUBLISHER OBLIGATION for every
 * matter-scoped event. The matter Activity feed ({@code AuditEventRepository.findByProjectId}),
 * Home recent activity and the portal Firm-actions trail all correlate on {@code
 * details->>'project_id'}; a publisher that omits it silently drops its rows from all three feeds
 * (G-B8, G-D9). This test turns that silent omission into a red build.
 *
 * <p>Plain JUnit source scan (no Spring context), sibling of {@code EntityTypeLiteralStabilityTest}
 * and {@code CollectionsSqlSourceScanTest}: reads {@code src/main/java} as text. For every {@code
 * auditService.log(...)} call in each listed publisher class, the {@code .details(...)} argument
 * must be built by {@link AuditDetails#matterScoped} — inline, through a local variable whose
 * nearest declaration is initialised by it, or through a private helper method whose body calls it.
 * {@code MatterClosureService} (not migrated in 625A) may instead carry the literal {@code
 * "project_id"} key. An event type that is deliberately NOT a matter-feed event is listed in {@link
 * #PUBLISHERS} with its reason — add to it consciously, never to make the build green.
 */
class MatterScopedPublisherGuardTest {

  private static final Path MAIN =
      Path.of("").toAbsolutePath().resolve("src/main/java/io/b2mash/b2b/b2bstrawman");

  private static final String WHY =
      "ADR-349: matter-scoped audit events must write project_id through"
          + " AuditDetails.matterScoped (findByProjectId / Home / portal Firm trail correlate on"
          + " details->>'project_id') — ";

  private static final String LOG = "auditService.log(";
  private static final String HELPER = "AuditDetails.matterScoped(";
  private static final String CLOSURE = "verticals/legal/closure/MatterClosureService.java";

  /**
   * The ADR-349 publisher list: class → event-type literals exempt from the obligation (draft-edit
   * events on a fee note are not feed milestones; ADR-349's families are fee note
   * created/approved/sent/voided + payments).
   */
  private static final Map<String, Set<String>> PUBLISHERS =
      Map.of(
          "acceptance/AcceptanceService.java",
          Set.of(),
          "acceptance/AcceptanceNotificationService.java",
          Set.of(),
          "verticals/legal/trustaccounting/transaction/TrustTransactionService.java",
          Set.of(),
          "invoice/InvoiceCreationService.java",
          Set.of(
              "invoice.updated",
              "invoice.deleted",
              "invoice.custom_fields_updated",
              "invoice.field_groups_updated"),
          "invoice/InvoiceTransitionService.java",
          Set.of(),
          CLOSURE,
          Set.of());

  /**
   * Minimum non-exempt emit sites per class as of 625A — keeps the scan from passing vacuously if
   * the parse stops finding sites (moved code, renamed field). Raise when a site is added.
   */
  private static final Map<String, Integer> MIN_SITES =
      Map.of(
          "acceptance/AcceptanceService.java",
          9,
          "acceptance/AcceptanceNotificationService.java",
          1,
          "verticals/legal/trustaccounting/transaction/TrustTransactionService.java",
          10,
          "invoice/InvoiceCreationService.java",
          1,
          "invoice/InvoiceTransitionService.java",
          7,
          CLOSURE,
          3);

  private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern CALL = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

  record Site(int line, String eventType, String details, boolean ok) {}

  @Test
  void everyMatterScopedEmitSiteUsesTheHelper() {
    PUBLISHERS.forEach(
        (file, exempt) -> {
          var sites = sites(read(file), exempt, !CLOSURE.equals(file));
          assertThat(sites.size())
              .as(WHY + file + " emit sites found (update MIN_SITES consciously)")
              .isGreaterThanOrEqualTo(MIN_SITES.get(file));
          assertThat(sites.stream().filter(s -> !s.ok()).toList())
              .as(WHY + file + " has emit sites without AuditDetails.matterScoped")
              .isEmpty();
        });
  }

  @Test
  void migratedPublishersNoLongerSpellTheKeyByHand() {
    PUBLISHERS.keySet().stream()
        .filter(file -> !CLOSURE.equals(file))
        .forEach(
            file ->
                assertThat(stripComments(read(file)).contains("\"project_id\""))
                    .as(WHY + file + " must not write the \"project_id\" literal (use the helper)")
                    .isFalse());
  }

  @Test
  void checkerFlagsASiteWithoutTheHelper() {
    String synthetic =
        """
        class X {
          void a() {
            auditService.log(AuditEventBuilder.builder().eventType("x.a")
                .details(Map.of("k", "v")).build());
          }
          void b() {
            var details = AuditDetails.matterScoped(projectId, customerId);
            details.put("k", 1);
            auditService.log(AuditEventBuilder.builder().eventType("x.b").details(details).build());
          }
        }
        """;
    var sites = sites(synthetic, Set.of(), true);
    assertThat(sites).extracting(Site::ok).containsExactly(false, true);
  }

  // ---- scanner ------------------------------------------------------------------

  static List<Site> sites(String raw, Set<String> exempt, boolean helperOnly) {
    String code = stripComments(raw);
    List<Site> out = new ArrayList<>();
    for (int i = code.indexOf(LOG); i >= 0; i = code.indexOf(LOG, i + 1)) {
      int open = i + LOG.length() - 1;
      String block = code.substring(i, matching(code, open, '(', ')') + 1);
      String eventType = argOf(block, ".eventType(");
      if (eventType != null && exempt.stream().anyMatch(x -> eventType.contains("\"" + x + "\""))) {
        continue;
      }
      String details = argOf(block, ".details(");
      int line = (int) raw.substring(0, i).lines().count();
      out.add(new Site(line, eventType, details, detailsOk(code, i, details, helperOnly)));
    }
    return out;
  }

  private static boolean detailsOk(
      String code, int blockStart, String details, boolean helperOnly) {
    if (details == null) {
      return false;
    }
    String d = details.strip();
    if (d.startsWith(HELPER) || (!helperOnly && d.contains("\"project_id\""))) {
      return true;
    }
    if (IDENT.matcher(d).matches()) { // .details(var): nearest preceding declaration wins
      var decl =
          Pattern.compile(
              "(?:var|Map<[^;=]*>|LinkedHashMap<[^;=]*>|HashMap<[^;=]*>)\\s+"
                  + Pattern.quote(d)
                  + "\\s*=\\s*([^;]*);");
      var m = decl.matcher(code.substring(0, blockStart));
      String init = null;
      while (m.find()) {
        init = m.group(1);
      }
      return init != null
          && (init.strip().startsWith(HELPER) || (!helperOnly && init.contains("\"project_id\"")));
    }
    var call = CALL.matcher(d);
    if (call.lookingAt()) { // .details(helper(...)): the helper's body must call matterScoped
      var sig = Pattern.compile("\\s" + Pattern.quote(call.group(1)) + "\\s*\\([^;{]*\\)\\s*\\{");
      var m = sig.matcher(code);
      if (m.find()) {
        int bodyOpen = m.end() - 1;
        String body = code.substring(bodyOpen, matching(code, bodyOpen, '{', '}') + 1);
        return body.contains(HELPER) || (!helperOnly && body.contains("\"project_id\""));
      }
    }
    return false;
  }

  /** Text of the balanced (...) argument after the first {@code marker}, or null. */
  private static String argOf(String block, String marker) {
    int at = block.indexOf(marker);
    if (at < 0) {
      return null;
    }
    int open = at + marker.length() - 1;
    return block.substring(open + 1, matching(block, open, '(', ')'));
  }

  /** Index of the bracket closing the one at {@code open}; string/char literals are skipped. */
  private static int matching(String s, int open, char o, char c) {
    int depth = 0;
    for (int i = open; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (ch == '"') {
        i = skipString(s, i);
      } else if (ch == '\'') {
        i = s.indexOf('\'', i + (s.charAt(i + 1) == '\\' ? 3 : 2));
      } else if (ch == o) {
        depth++;
      } else if (ch == c && --depth == 0) {
        return i;
      }
    }
    throw new IllegalStateException("unbalanced " + o + " at offset " + open);
  }

  /** From the opening quote at {@code i}, the index of the closing quote (text blocks too). */
  private static int skipString(String s, int i) {
    if (s.startsWith("\"\"\"", i)) {
      return s.indexOf("\"\"\"", i + 3) + 2;
    }
    for (int j = i + 1; j < s.length(); j++) {
      if (s.charAt(j) == '\\') {
        j++;
      } else if (s.charAt(j) == '"') {
        return j;
      }
    }
    throw new IllegalStateException("unterminated string at offset " + i);
  }

  /** Blanks line and block comments (length-preserving) without touching string literals. */
  static String stripComments(String s) {
    var b = new StringBuilder(s);
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (ch == '"') {
        i = skipString(s, i);
      } else if (ch == '\'') {
        i = s.indexOf('\'', i + (s.charAt(i + 1) == '\\' ? 3 : 2));
      } else if (s.startsWith("//", i)) {
        int end = s.indexOf('\n', i);
        end = end < 0 ? s.length() : end;
        for (int k = i; k < end; k++) {
          b.setCharAt(k, ' ');
        }
        i = end;
      } else if (s.startsWith("/*", i)) {
        int end = s.indexOf("*/", i + 2) + 2;
        for (int k = i; k < end; k++) {
          if (s.charAt(k) != '\n') {
            b.setCharAt(k, ' ');
          }
        }
        i = end - 1;
      }
    }
    return b.toString();
  }

  private static String read(String relative) {
    Path p = MAIN.resolve(relative);
    try {
      return Files.readString(p, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + p + " (moved? update the ADR-349 guard)", e);
    }
  }
}
