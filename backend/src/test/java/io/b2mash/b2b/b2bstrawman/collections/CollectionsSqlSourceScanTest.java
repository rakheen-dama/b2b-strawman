package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
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

  private static final List<String> DEBTOR_BOOK_SITES =
      List.of(
          "collections/CollectionsReadService.java",
          "collections/CashDigestService.java",
          "collections/CollectionsScanService.java",
          "customer/ClientListRowRepository.java",
          "reporting/InvoiceAgingReportQuery.java");

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
  void everyDebtorBookSiteReadsTheConstant() {
    for (String site : DEBTOR_BOOK_SITES) {
      assertThat(read(PKG.resolve(site)))
          .as(WHY + site + " must read CollectionsSql.OUTSTANDING_PREDICATE")
          .contains("CollectionsSql.OUTSTANDING_PREDICATE");
    }
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
