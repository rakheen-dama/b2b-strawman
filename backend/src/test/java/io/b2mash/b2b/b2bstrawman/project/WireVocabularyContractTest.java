package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * ADR-337 wire-vocabulary contract — a RATCHET over the Phase 87 rename batches.
 *
 * <p>Enumerates (1) every Spring MVC route whose handler bean lives in {@link #SCOPED_PACKAGES} and
 * (2) every wire record in those packages (architecture §12.3.1 heuristic plus {@link
 * #ADDITIONAL_WIRE_RECORDS}), and collects the ones still carrying the legacy {@code
 * project}/{@code customer} nouns. The current offenders must equal the committed baseline {@code
 * src/test/resources/wire/batch1-known-offenders.txt}:
 *
 * <ul>
 *   <li>an offender NOT in the baseline fails the build (new legacy vocabulary on the wire);
 *   <li>a baseline line that no longer offends fails the build (stale — delete it; the baseline
 *       only shrinks as 607B/607C/608 rename).
 * </ul>
 *
 * <p>Regenerate the baseline (scope change in 608B, or to drop renamed lines) with {@code ./mvnw
 * test -Dtest=WireVocabularyContractTest -Dwire.contract.update=true}; the PR diff of the baseline
 * must be deletions only, except when the scope constant widens.
 *
 * <p>Annotated exactly like {@code McpHandshakeIntegrationTest} so it reuses that context-cache
 * key. Audit/EntityTag literal stability (ADR-342) is NOT here — see {@code
 * EntityTypeLiteralStabilityTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class WireVocabularyContractTest {

  private static final String BASE = "io.b2mash.b2b.b2bstrawman";

  /**
   * Phase 87 Batch 1 (Epic 607). Edited ONLY in 607A (this set), 608B (+ Batch 2) and 609F (scope
   * removed — whole base package, excluding {@code mcp}).
   */
  static final Set<String> SCOPED_PACKAGES =
      Set.of(
          "project",
          "member",
          "projecttemplate",
          "setupstatus",
          "dashboard",
          "activity",
          "comment",
          "budget",
          "task",
          "document",
          "mywork",
          "calendar");

  /**
   * Wire records the §12.3.1 name heuristic misses (their names do not end in
   * Request/Response/Summary/Row/Item/Entry and they live outside {@code .dto}/{@code
   * Controller$}), but which ARE serialised to the frontend and are scheduled for the Batch 1
   * rename. Internal records ({@code ProjectHealthInput}, {@code ProjectHealthResult}, {@code
   * ProjectMemberInfo}, {@code ProjectAccess}) are deliberately NOT listed.
   *
   * <p>Each entry is an ALIAS GROUP {legacy FQCN, post-rename FQCN} (same package; target names
   * from architecture §12.3 / tasks 607B), so 607B/607C can rename these records WITHOUT editing
   * this test: a record is a wire record if its FQCN is in ANY group (the renamed class is still
   * scanned — no false pass), and the existence check only requires AT LEAST ONE name per group to
   * be found (a deletion/move out of the package is still caught; a rename is not). Records whose
   * name does not change are single-element groups.
   */
  static final List<Set<String>> ADDITIONAL_WIRE_RECORDS =
      List.of(
          Set.of(BASE + ".setupstatus.ProjectSetupStatus", BASE + ".setupstatus.MatterSetupStatus"),
          Set.of(
              BASE + ".setupstatus.ProjectUnbilledBreakdown",
              BASE + ".setupstatus.MatterUnbilledBreakdown"),
          Set.of(BASE + ".calendar.CalendarService$CalendarItemDto"),
          Set.of(
              BASE + ".project.ProjectUpcomingDeadline", BASE + ".project.MatterUpcomingDeadline"),
          Set.of(BASE + ".project.ProjectWithRole", BASE + ".project.MatterWithRole"),
          Set.of(BASE + ".budget.BudgetStatus"));

  private static final Set<String> ADDITIONAL_WIRE_RECORD_NAMES =
      ADDITIONAL_WIRE_RECORDS.stream().flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());

  /** Under the renamed roots the id variable must be {@code {matterId}}/{@code {clientId}}. */
  private static final Pattern BARE_ID_UNDER_RENAMED_ROOT =
      Pattern.compile("/api/(matters|clients)/\\{id\\}");

  static final Set<String> FORBIDDEN_COMPONENTS =
      Set.of(
          "projectId",
          "projectIds",
          "projectName",
          "projectRole",
          "customerId",
          "customerIds",
          "customerName",
          "customerEmail",
          "customerAddress",
          "customerType",
          "activeProjectCount",
          "projectBreakdown",
          "byProject");

  private static final Path BASELINE =
      Path.of("src/test/resources/wire/batch1-known-offenders.txt");
  private static final boolean UPDATE = Boolean.getBoolean("wire.contract.update");
  private static final String ROUTE = "ROUTE ";
  private static final String DTO = "DTO ";

  private static final String HEADER =
      """
      # WireVocabularyContractTest ratchet baseline (ADR-337) — Phase 87 wire-rename worklist.
      # Each line is legacy project/customer vocabulary still on the wire inside
      # WireVocabularyContractTest.SCOPED_PACKAGES. This file must only SHRINK:
      #   - 607B/607C delete lines as they rename (the test fails on stale lines);
      #   - the test fails on any offender NOT listed here (new legacy vocabulary).
      # 608B widens SCOPED_PACKAGES to Batch 2 (the only time lines are added). Entries in Batch 1
      # packages whose rename ADR-337 schedules for 608 (e.g. the /api/customers/{customerId}/...
      # document upload route and DocumentResponse#customerId) legitimately remain until then.
      # Never add a line by hand to make the build green — rename the route/record instead.
      # Regenerate: ./mvnw test -Dtest=WireVocabularyContractTest -Dwire.contract.update=true
      # Format: "ROUTE <METHODS> <pattern>" | "DTO <class>" | "DTO <class>#<component>"
      """;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mapping;

  @Test
  void noNewLegacyNounOnScopedRoutes() throws IOException {
    SortedMap<String, String> offenders = new TreeMap<>(); // offender -> handler (worklist output)
    int scopedRoutes = 0;
    for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
      Class<?> beanType = e.getValue().getBeanType();
      if (!inScope(beanType.getPackageName())) {
        continue;
      }
      scopedRoutes++;
      String methods = methodsOf(e.getKey());
      for (String pattern : e.getKey().getPatternValues()) {
        if (isLegacyRoute(pattern)) {
          offenders.put(
              ROUTE + methods + " " + pattern,
              beanType.getSimpleName() + "#" + e.getValue().getMethod().getName());
        }
      }
    }
    assertThat(SCOPED_PACKAGES).isNotEmpty();
    assertThat(scopedRoutes)
        .as(
            "the route scan must see handler methods in %s (guard against a moved package)",
            SCOPED_PACKAGES)
        .isPositive();
    ratchet(ROUTE, offenders);
  }

  @Test
  void noNewLegacyNounInScopedWireRecords() throws IOException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
    SortedMap<String, String> offenders = new TreeMap<>();
    Set<String> seenAdditional = new TreeSet<>();
    int scanned = 0;
    for (String pkg : SCOPED_PACKAGES) {
      for (BeanDefinition bd : scanner.findCandidateComponents(BASE + "." + pkg)) {
        Class<?> c =
            ClassUtils.resolveClassName(bd.getBeanClassName(), getClass().getClassLoader());
        if (isTestClass(c) || !isWireRecord(c)) {
          continue;
        }
        if (ADDITIONAL_WIRE_RECORD_NAMES.contains(c.getName())) {
          seenAdditional.add(c.getName());
        }
        scanned++;
        String simple = c.getSimpleName();
        if (simple.startsWith("Project") || simple.startsWith("Customer")) {
          offenders.put(DTO + c.getName(), "record name");
        }
        for (var rc : c.getRecordComponents()) {
          if (FORBIDDEN_COMPONENTS.contains(rc.getName())) {
            offenders.put(DTO + c.getName() + "#" + rc.getName(), "record component");
          }
        }
      }
    }
    assertThat(scanned)
        .as(
            "the DTO scan must see wire records in %s (guard against a moved package)",
            SCOPED_PACKAGES)
        .isPositive();
    // Every alias group must resolve to at least one scanned record: a rename to the listed
    // post-rename name passes; a deletion, a move out of scope, or a rename to an UNLISTED name
    // fails (re-point the group rather than silently dropping the record from the scan).
    List<Set<String>> missingGroups =
        ADDITIONAL_WIRE_RECORDS.stream()
            .filter(group -> group.stream().noneMatch(seenAdditional::contains))
            .toList();
    assertThat(missingGroups)
        .as(
            "every ADDITIONAL_WIRE_RECORDS alias group must match a scanned record (legacy or"
                + " post-rename name) — re-point the group if the record moved/was renamed")
        .isEmpty();
    ratchet(DTO, offenders);
  }

  // ---- classification ---------------------------------------------------------

  private static boolean inScope(String pkg) {
    if (pkg.startsWith(BASE + ".mcp")) {
      return false; // published plugin contract — pinned by McpToolContractTest (ADR-343)
    }
    for (String s : SCOPED_PACKAGES) {
      String p = BASE + "." + s;
      if (pkg.equals(p) || pkg.startsWith(p + ".")) {
        return true;
      }
    }
    return false;
  }

  /**
   * A route offends when ANY path segment (literal or {@code {variable}}) contains the legacy noun
   * {@code project}/{@code customer} — this covers {@code /api/projects}, {@code
   * /api/project-templates}, {@code /api/dashboard/project-health}, {@code /from-project/{id}},
   * {@code /required-customer-fields}, {@code import-customers}, {@code {projectId}}, {@code
   * {customerId}} — or when a bare {@code {id}} sits directly under {@code /api/matters} / {@code
   * /api/clients}.
   */
  static boolean isLegacyRoute(String pattern) {
    for (String segment : pattern.split("/")) {
      String s = segment.toLowerCase(Locale.ROOT);
      if (s.contains("project") || s.contains("customer")) {
        return true;
      }
    }
    return BARE_ID_UNDER_RENAMED_ROOT.matcher(pattern).find();
  }

  /** Architecture §12.3.1 wire-record heuristic, plus {@link #ADDITIONAL_WIRE_RECORDS}. */
  static boolean isWireRecord(Class<?> c) {
    String n = c.getName();
    String simple = c.getSimpleName();
    if (ADDITIONAL_WIRE_RECORD_NAMES.contains(n)) {
      return true;
    }
    if (n.contains(".mcp.")
        || n.contains(".event.")
        || n.contains(".demo.")
        || n.contains(".seeder.")) {
      return false;
    }
    if (simple.endsWith("Projection") || simple.endsWith("Spec")) {
      return false;
    }
    if (c.getPackageName().contains(".dto")) {
      return true;
    }
    if (n.contains("Controller$") || n.contains("Dtos$")) {
      return true;
    }
    return simple.endsWith("Request")
        || simple.endsWith("Response")
        || simple.endsWith("Summary")
        || simple.endsWith("Row")
        || simple.endsWith("Item")
        || simple.endsWith("Entry");
  }

  private static boolean isTestClass(Class<?> c) {
    CodeSource cs = c.getProtectionDomain().getCodeSource();
    return cs != null && cs.getLocation().getPath().contains("/test-classes/");
  }

  private static String methodsOf(RequestMappingInfo info) {
    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
    return methods.isEmpty()
        ? "ANY"
        : methods.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
  }

  // ---- ratchet ------------------------------------------------------------------

  private static void ratchet(String kind, SortedMap<String, String> offenders) throws IOException {
    System.out.printf(
        "%n[WireVocabularyContractTest] %s offenders in scope %s: %d%n",
        kind.trim(), new TreeSet<>(SCOPED_PACKAGES), offenders.size());
    offenders.forEach((k, v) -> System.out.println("  " + k + "    <- " + v));

    List<String> all = readBaseline();
    Set<String> baseline =
        all.stream().filter(l -> l.startsWith(kind)).collect(Collectors.toCollection(TreeSet::new));

    if (UPDATE) {
      writeBaseline(kind, offenders.keySet(), all);
      System.out.printf(
          "[WireVocabularyContractTest] -Dwire.contract.update=true: rewrote %s section of %s%n",
          kind.trim(), BASELINE);
      return;
    }

    Set<String> unexpected = new TreeSet<>(offenders.keySet());
    unexpected.removeAll(baseline);
    Set<String> stale = new TreeSet<>(baseline);
    stale.removeAll(offenders.keySet());

    assertThat(unexpected)
        .as(
            "ADR-337: NEW legacy project/customer vocabulary on the wire (not in %s). Rename it to"
                + " matter/client — do NOT add it to the baseline.",
            BASELINE)
        .isEmpty();
    assertThat(stale)
        .as(
            "Stale lines in %s — they no longer offend. Delete them (the ratchet only shrinks) or"
                + " rerun with -Dwire.contract.update=true and commit the deletions.",
            BASELINE)
        .isEmpty();
  }

  private static List<String> readBaseline() throws IOException {
    if (!Files.exists(BASELINE)) {
      assertThat(UPDATE)
          .as("Missing %s — generate it with -Dwire.contract.update=true", BASELINE)
          .isTrue();
      return List.of();
    }
    return Files.readAllLines(BASELINE, StandardCharsets.UTF_8).stream()
        .map(String::strip)
        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
        .toList();
  }

  private static void writeBaseline(String kind, Set<String> mine, List<String> existing)
      throws IOException {
    TreeSet<String> lines = new TreeSet<>(mine);
    existing.stream().filter(l -> !l.startsWith(kind)).forEach(lines::add);
    List<String> out = new ArrayList<>(HEADER.lines().toList());
    out.addAll(lines);
    Files.createDirectories(BASELINE.getParent());
    Files.writeString(BASELINE, String.join("\n", out) + "\n", StandardCharsets.UTF_8);
  }
}
