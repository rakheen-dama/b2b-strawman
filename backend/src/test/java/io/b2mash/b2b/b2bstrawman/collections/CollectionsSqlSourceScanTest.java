package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * §13.8.4 / OQ #12 guard: the debtor-book definition lives ONLY in {@link CollectionsSql}. Before
 * Phase 88 five SQL sites each carried their own copy of the due-date guard and the Fee notes page
 * computed a different number again (G-D11 b) — this test turns a re-inlined copy into a red build.
 *
 * <p>Plain JUnit source scan (no Spring context), mirroring {@code EntityTypeLiteralStabilityTest}
 * and {@code CollectionsCoreBoundaryTest}: reads {@code src/main/java} as text. Negative: no {@code
 * due_date IS NOT NULL} (any case/whitespace) outside {@code CollectionsSql.java}. Positive: each
 * of the five debtor-book sites references {@code CollectionsSql.OUTSTANDING_PREDICATE} — the
 * dunning scan's {@code due_date < CURRENT_DATE} carries no inline guard, so only the positive pin
 * catches drift there. A legitimate non-debtor-book {@code due_date IS NOT NULL} must be added to
 * an explicit allowlist here consciously, never by inlining the guard.
 */
class CollectionsSqlSourceScanTest {

  private static final Path MAIN_JAVA = Path.of("").toAbsolutePath().resolve("src/main/java");
  private static final Path PKG = MAIN_JAVA.resolve("io/b2mash/b2b/b2bstrawman");
  private static final Path CONSTANT_HOME = PKG.resolve("collections/CollectionsSql.java");

  private static final Pattern INLINE_GUARD =
      Pattern.compile("due_date\\s+IS\\s+NOT\\s+NULL", Pattern.CASE_INSENSITIVE);

  /**
   * Per-site MINIMUM reference counts of {@code CollectionsSql.OUTSTANDING_PREDICATE} and {@code
   * CollectionsSql.OUTSTANDING_AMOUNT} — the routed usage as of 620B. Routing a new debtor-book
   * query at a site: raise its count here. Lowering one requires proving that query left the debtor
   * book; never lower it to make the build green.
   */
  private static final Map<String, int[]> SITE_MIN_COUNTS =
      Map.of(
          // DEBTOR_BOOK_SQL, DEBTOR_BOOK_COUNT_SQL, OUTSTANDING_INVOICES_SQL (+ balance_due,
          // 622D), OUTSTANDING_SUMMARY_SQL
          "collections/CollectionsReadService.java", new int[] {4, 3},
          "collections/CashDigestService.java", new int[] {1, 1},
          "collections/CollectionsScanService.java", new int[] {1, 0},
          // predicate once; amount in SUM(...) AS total and ORDER BY SUM(...) DESC
          "customer/ClientListRowRepository.java", new int[] {1, 2},
          "reporting/InvoiceAgingReportQuery.java", new int[] {1, 1});

  private static final String TRIAGE_SITE = "collections/CollectionsTriageService.java";
  private static final int TRIAGE_MIN_STATUS_REFS =
      2; // outstanding list + oldestCurrentDaysOverdue

  private static final Pattern STATUS_LITERAL = Pattern.compile("'([A-Z_]+)'");

  private static final String WHY =
      "§13.8.4 / OQ #12: the debtor-book predicate lives only in CollectionsSql — ";

  @Test
  void noInlineDueDateGuardOutsideCollectionsSql() {
    List<Path> sources = javaSources(MAIN_JAVA);
    assertThat(sources).as("main sources must exist (guard against a moved tree)").isNotEmpty();

    List<String> offenders =
        sources.stream()
            .filter(p -> !p.equals(CONSTANT_HOME))
            .filter(p -> INLINE_GUARD.matcher(read(p)).find())
            .map(p -> MAIN_JAVA.relativize(p).toString())
            .toList();
    assertThat(offenders)
        .as(WHY + "use CollectionsSql.OUTSTANDING_PREDICATE instead of an inline due-date guard")
        .isEmpty();
  }

  @Test
  void constantCarriesTheGuardTheScanLooksFor() {
    assertThat(CollectionsSql.OUTSTANDING_PREDICATE)
        .as(WHY + "the scan is vacuous if the constant stops carrying the due-date guard")
        .containsPattern(INLINE_GUARD);
    assertThat(read(CONSTANT_HOME)).containsPattern(INLINE_GUARD);
  }

  @Test
  void everyDebtorBookSiteReadsTheConstantsAtEveryRoutedPosition() {
    SITE_MIN_COUNTS.forEach(
        (site, min) -> {
          String src = read(PKG.resolve(site));
          assertThat(occurrences(src, "CollectionsSql.OUTSTANDING_PREDICATE"))
              .as(
                  WHY
                      + site
                      + " must read CollectionsSql.OUTSTANDING_PREDICATE at least %d times (one"
                      + " per routed query) — update SITE_MIN_COUNTS consciously",
                  min[0])
              .isGreaterThanOrEqualTo(min[0]);
          assertThat(occurrences(src, "CollectionsSql.OUTSTANDING_AMOUNT"))
              .as(
                  WHY
                      + site
                      + " must read CollectionsSql.OUTSTANDING_AMOUNT at least %d times (one per"
                      + " outstanding-amount position) — update SITE_MIN_COUNTS consciously",
                  min[1])
              .isGreaterThanOrEqualTo(min[1]);
        });
  }

  @Test
  void triageOutstandingFilterReadsTheStatusSet() {
    assertThat(occurrences(read(PKG.resolve(TRIAGE_SITE)), "CollectionsSql.OUTSTANDING_STATUSES"))
        .as(
            WHY
                + TRIAGE_SITE
                + " must filter outstanding invoices via CollectionsSql.OUTSTANDING_STATUSES, not an"
                + " inline InvoiceStatus comparison — update TRIAGE_MIN_STATUS_REFS consciously")
        .isGreaterThanOrEqualTo(TRIAGE_MIN_STATUS_REFS);
  }

  @Test
  void statusSetMatchesTheStatusesQuotedInThePredicate() {
    Set<String> quoted = new HashSet<>();
    Matcher m = STATUS_LITERAL.matcher(CollectionsSql.OUTSTANDING_PREDICATE);
    while (m.find()) {
      quoted.add(m.group(1));
    }
    assertThat(quoted).as("the predicate must quote at least one status literal").isNotEmpty();
    assertThat(
            CollectionsSql.OUTSTANDING_STATUSES.stream()
                .map(Enum::name)
                .collect(Collectors.toSet()))
        .as(
            WHY
                + "OUTSTANDING_STATUSES (Java) must list exactly the statuses quoted in"
                + " OUTSTANDING_PREDICATE (SQL) — flip both in the same commit (622D)")
        .isEqualTo(quoted);
  }

  private static int occurrences(String haystack, String needle) {
    int count = 0;
    for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
      count++;
    }
    return count;
  }

  private static String read(Path p) {
    try {
      return Files.readString(p, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + p + " (moved? update the §13.8.4 guard)", e);
    }
  }

  private static List<Path> javaSources(Path dir) {
    try (Stream<Path> walk = Files.walk(dir)) {
      return walk.filter(p -> p.toString().endsWith(".java")).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
