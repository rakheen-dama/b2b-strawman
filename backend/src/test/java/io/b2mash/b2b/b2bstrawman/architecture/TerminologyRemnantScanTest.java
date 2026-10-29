package io.b2mash.b2b.b2bstrawman.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ADR-357 backend guard (Phase 88 628D): no user-visible backend copy says Project(s) / Customer(s)
 * / Invoice(s), any case, outside {@code terminology-allow-list.json}. Twin of {@code
 * frontend/__tests__/no-terminology-remnants.test.ts}; shape of {@code
 * audit/EntityTypeLiteralStabilityTest} (plain JUnit source scan, no Spring context).
 *
 * <p>Scanned: (1) Java string literals under {@code src/main/java} in message-bearing POSITIONS —
 * arguments of the ProblemDetail exceptions, DeleteGuard, ValidationCheck, PrerequisiteViolation,
 * registry label records and notification builders; enum-constant arguments; literals assigned to a
 * {@code *title|*message|*label|…} variable; (2) every string value of the pack JSON under {@code
 * src/main/resources/{field-packs,template-packs,clause-packs,automation-templates,
 * compliance-packs,request-packs}} — a top-level {@code reconcile} array contributes only its
 * {@code to} values (its {@code from} values are the previously shipped text, ADR-356); (3) text
 * nodes and alt/title/aria-label attributes of {@code templates/email/*.html}.
 *
 * <p>Not words (stripped before matching): {@code {customer.name}} / {@code {{project.name}}}
 * placeholders, dotted paths ({@code invoice.*}), route paths ({@code /invoices/…}), UPPER enum
 * tokens ({@code PROJECT}). A literal that is a single identifier token ({@code "project"}, {@code
 * "customer_tax_number"}, {@code "project-summary.json"}) is a key, not copy (ADR-342) — except the
 * noun argument of {@code DeleteGuard.forEntity} / {@code pluralize}, which IS copy.
 *
 * <p>Not scanned on purpose: {@code IllegalArgumentException} / {@code IllegalStateException}
 * messages (programmer guards; the latter render as a generic 500), {@code log.*}, MCP
 * {@code @Tool} descriptions. Known blind spots (manual review; ADR-357 Cons): copy built in a
 * variable whose name is not message-like, {@code missing.add(…)}, {@code customer.anonymize(…)},
 * {@code TrustBoundaryDecision.refused(…)}, {@code SkillContext} descriptions, sync dead-letter
 * reasons. "project" as a verb is rewritten, not allow-listed (ADR-357).
 *
 * <p>Separately, {@link #notFoundTitlesUseEntityNounConstants()} bans the legacy {@code
 * ResourceNotFoundException} type literals (incl. the CamelCase compounds the word scan cannot see,
 * e.g. {@code "ProjectTemplate"}) — 404 titles come from {@code exception/EntityNoun}.
 */
class TerminologyRemnantScanTest {

  private static final Path MODULE = Path.of("").toAbsolutePath();
  private static final Path MAIN_JAVA = MODULE.resolve("src/main/java");
  private static final Path RESOURCES = MODULE.resolve("src/main/resources");
  private static final List<String> PACK_DIRS =
      List.of(
          "field-packs",
          "template-packs",
          "clause-packs",
          "automation-templates",
          "compliance-packs",
          "request-packs");
  private static final Path EMAIL_DIR = RESOURCES.resolve("templates/email");
  private static final Path ALLOW_LIST =
      MODULE.resolve(
          "src/test/java/io/b2mash/b2b/b2bstrawman/architecture/terminology-allow-list.json");

  static final Pattern NOUN =
      Pattern.compile("\\b(projects?|customers?|invoices?)\\b", Pattern.CASE_INSENSITIVE);

  /** One identifier token: lower-case key / slug / file name, or an UPPER_SNAKE enum value. */
  static final Pattern TOKEN = Pattern.compile("[a-z0-9_\\-./*]+|[A-Z0-9_]+");

  /** ADR-357 reason set (+ "third-party object name", ADR-357 Consequences; + R-6 ruling). */
  static final List<String> REASONS =
      List.of(
          "API/route path",
          "INV- numbering",
          "statutory tax invoice",
          "statutory term",
          "ADR-342 literal",
          "MCP tool description",
          "marketing copy",
          "archived code",
          "third-party object name");

  static final Set<String> MESSAGE_CALLEES =
      Set.of(
          // ProblemDetail-rendering exceptions (GlobalExceptionHandler → title/detail → UI toast)
          "ResourceNotFoundException",
          "ResourceConflictException",
          "InvalidStateException",
          "ForbiddenException",
          "PortalAuthException",
          "withDetail",
          "withType",
          "ProblemDetailFactory.create",
          // DeleteGuard builder ("Cannot delete matter with 2 fee note(s). …")
          "forEntity",
          "checkCountZero",
          "checkNotExists",
          // fee-note send checks + prerequisite modal payloads
          "ValidationCheck",
          "PrerequisiteViolation",
          // registries
          "AuditEventTypeMetadata",
          "ModuleDefinition",
          "NavItem",
          "VariableGroup",
          "VariableInfo",
          "LoopSource",
          "addCustomFieldGroup",
          // notification titles / bodies
          "notifyAdminsAndOwners",
          "createNotification",
          "createIfEnabled",
          "createNotificationsForRecipients",
          "pluralize");

  /** Callees whose single-word noun argument is copy, not a key. */
  static final Set<String> NOUN_ARG_CALLEES = Set.of("forEntity", "pluralize");

  /** Wrappers a literal can sit in without leaving its message position. */
  static final Set<String> PASS_THROUGH =
      Set.of(
          "formatted",
          "format",
          "valueOf",
          "concat",
          "of",
          "ofNullable",
          "orElse",
          "requireNonNullElse");

  static final Pattern MESSAGE_VAR =
      Pattern.compile(
          "(label|message|msg|error|title|description|heading|text|detail|subject|body)$",
          Pattern.CASE_INSENSITIVE);
  static final Pattern ENUM_CONSTANT = Pattern.compile("[A-Z][A-Z0-9_]*");

  /** Legacy 404 type literals — must be {@code EntityNoun} constants (R-1, R-3). */
  static final Pattern LEGACY_404 =
      Pattern.compile(
          "ResourceNotFoundException\\(\\s*\"(Customer|Project|Invoice|Expense|ProjectTemplate"
              + "|InvoiceLine|ProjectMember|ProjectBudget|CustomerProject link|LegalDisbursement)\"");

  private static final Pattern CALLEE =
      Pattern.compile("(?:new\\s+)?([A-Za-z_][\\w.]*)\\s*(?:<[^()]*>)?\\s*$");
  private static final Pattern DECLARATION =
      Pattern.compile(
          "\\s*(?:@\\w+\\s+)*(?:(?:final|private|static|public|protected)\\s+)*"
              + "(?:[\\w<>\\[\\],.?]+\\s+)?([A-Za-z_]\\w*)\\s*=(?!=)");
  private static final String BLOCK = "{";

  record Hit(String file, int line, String where, String text) {
    String render() {
      return file + ":" + line + "  \"" + text + "\"  [" + where + "]";
    }
  }

  record AllowEntry(String file, String text, String reason) {}

  /** A string literal: offset, decoded text, enclosing calls innermost-first ("{" = block). */
  record Literal(int offset, String text, List<String> calls, int statementStart) {}

  // ---- word filter (mirror of the frontend prose()) ------------------------------------------

  static String prose(String text) {
    return text.replaceAll("\\{\\{?[^}]*\\}?\\}", " ")
        .replaceAll("\\b[A-Za-z_][A-Za-z0-9_]*(?:\\.(?:[A-Za-z0-9_]+|\\*))+", " ")
        .replaceAll("(^|\\s)/\\S*", " ")
        .replaceAll("\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b|\\b[A-Z]{2,}[0-9]*\\b", " ");
  }

  static String collapse(String text) {
    return text.strip().replaceAll("\\s+", " ");
  }

  // ---- Java lexer ------------------------------------------------------------------------------

  static List<Literal> literals(String src) {
    var out = new ArrayList<Literal>();
    var calls = new ArrayList<String>(); // outermost first; reversed when captured
    int stmt = 0;
    int i = 0;
    int n = src.length();
    while (i < n) {
      char c = src.charAt(i);
      if (src.startsWith("//", i) || src.startsWith("/*", i)) {
        boolean line = src.startsWith("//", i);
        int j = line ? src.indexOf('\n', i) : src.indexOf("*/", i + 2);
        i = j < 0 ? n : line ? j : j + 2;
        // a comment between statements must not hide the next declaration's name
        if (calls.isEmpty() || BLOCK.equals(calls.getLast())) stmt = i;
        continue;
      }
      if (src.startsWith("\"\"\"", i)) {
        int j = src.indexOf("\"\"\"", i + 3);
        out.add(new Literal(i, src.substring(i + 3, j), innermostFirst(calls), stmt));
        i = j + 3;
        continue;
      }
      if (c == '"' || c == '\'') {
        int j = i + 1;
        while (src.charAt(j) != c) {
          if (src.charAt(j) == '\\') j++;
          j++;
        }
        if (c == '"') {
          out.add(new Literal(i, unescape(src.substring(i + 1, j)), innermostFirst(calls), stmt));
        }
        i = j + 1;
        continue;
      }
      if (c == '(') {
        Matcher m = CALLEE.matcher(src.substring(Math.max(0, i - 200), i));
        String name = m.find() ? m.group(1) : "";
        calls.add(
            name.startsWith("ProblemDetailFactory.")
                ? name
                : name.substring(name.lastIndexOf('.') + 1));
      } else if (c == ')') {
        if (!calls.isEmpty() && !BLOCK.equals(calls.getLast())) calls.removeLast();
      } else if (c == '{') {
        calls.add(BLOCK);
        stmt = i + 1;
      } else if (c == '}') {
        while (!calls.isEmpty() && !BLOCK.equals(calls.removeLast())) {}
        stmt = i + 1;
      } else if (c == ';' && (calls.isEmpty() || BLOCK.equals(calls.getLast()))) {
        stmt = i + 1;
      }
      i++;
    }
    return out;
  }

  private static List<String> innermostFirst(List<String> calls) {
    return List.copyOf(calls.reversed());
  }

  private static String unescape(String s) {
    return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
  }

  /** ADR-357 position of a Java literal, or null when it is not user-visible copy. */
  static String position(Literal lit, String src) {
    String callee = null;
    String outer = null;
    List<String> calls = lit.calls();
    for (int k = 0; k < calls.size(); k++) {
      if (PASS_THROUGH.contains(calls.get(k))) continue;
      callee = BLOCK.equals(calls.get(k)) ? null : calls.get(k);
      outer = k + 1 < calls.size() ? calls.get(k + 1) : null;
      break;
    }
    if (TOKEN.matcher(lit.text().strip()).matches()
        && (callee == null || !NOUN_ARG_CALLEES.contains(callee))) {
      return null;
    }
    if (callee != null) {
      if (MESSAGE_CALLEES.contains(callee)) return "call " + callee;
      if (ENUM_CONSTANT.matcher(callee).matches() && (outer == null || BLOCK.equals(outer))) {
        return "enum " + callee;
      }
      return null;
    }
    Matcher m = DECLARATION.matcher(src.substring(lit.statementStart(), lit.offset()));
    return m.lookingAt() && MESSAGE_VAR.matcher(m.group(1)).find() ? "var " + m.group(1) : null;
  }

  static List<Hit> javaHits(String rel, String src) {
    var hits = new ArrayList<Hit>();
    for (Literal lit : literals(src)) {
      if (!NOUN.matcher(prose(lit.text())).find()) continue;
      String where = position(lit, src);
      if (where != null) {
        hits.add(new Hit(rel, lineOf(src, lit.offset()), where, collapse(lit.text())));
      }
    }
    return hits;
  }

  private static int lineOf(String src, int offset) {
    return (int) src.substring(0, offset).chars().filter(ch -> ch == '\n').count() + 1;
  }

  // ---- resources -------------------------------------------------------------------------------

  static void jsonHits(String rel, JsonNode node, String path, boolean root, List<Hit> out) {
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(
              e -> {
                if (root && e.getKey().equals("reconcile") && e.getValue().isArray()) {
                  // ADR-356: `from` is the previously shipped text — only `to` is current copy.
                  for (int i = 0; i < e.getValue().size(); i++) {
                    JsonNode to = e.getValue().get(i).get("to");
                    if (to != null) jsonHits(rel, to, "/reconcile/" + i + "/to", false, out);
                  }
                } else {
                  jsonHits(rel, e.getValue(), path + "/" + e.getKey(), false, out);
                }
              });
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) jsonHits(rel, node.get(i), path + "/" + i, false, out);
    } else if (node.isTextual()) {
      String text = node.asText();
      if (!TOKEN.matcher(text.strip()).matches() && NOUN.matcher(prose(text)).find()) {
        out.add(new Hit(rel, 0, "json " + path, collapse(text)));
      }
    }
  }

  static List<Hit> emailHits(String rel, String html) {
    var hits = new ArrayList<Hit>();
    String noComments = html.replaceAll("(?s)<!--.*?-->", "");
    for (String text : noComments.replaceAll("<[^>]+>", "\n").split("\n")) {
      if (NOUN.matcher(prose(text)).find()) hits.add(new Hit(rel, 0, "html text", collapse(text)));
    }
    Matcher attr = Pattern.compile("(?:alt|title|aria-label)=\"([^\"]*)\"").matcher(noComments);
    while (attr.find()) {
      if (NOUN.matcher(prose(attr.group(1))).find()) {
        hits.add(new Hit(rel, 0, "html attr", collapse(attr.group(1))));
      }
    }
    return hits;
  }

  // ---- the tree --------------------------------------------------------------------------------

  private static List<Hit> treeHits;

  static synchronized List<Hit> scanTree() {
    if (treeHits != null) return treeHits;
    var out = new ArrayList<Hit>();
    for (Path p : files(MAIN_JAVA, ".java")) out.addAll(javaHits(rel(p), read(p)));
    var mapper = new ObjectMapper();
    for (String dir : PACK_DIRS) {
      for (Path p : files(RESOURCES.resolve(dir), ".json")) {
        try {
          jsonHits(rel(p), mapper.readTree(read(p)), "", true, out);
        } catch (IOException e) {
          throw new UncheckedIOException("cannot parse " + p, e);
        }
      }
    }
    for (Path p : files(EMAIL_DIR, ".html")) out.addAll(emailHits(rel(p), read(p)));
    treeHits = List.copyOf(out);
    return treeHits;
  }

  static List<AllowEntry> allowList() {
    try {
      return new ObjectMapper().readValue(read(ALLOW_LIST), new TypeReference<>() {});
    } catch (IOException e) {
      throw new UncheckedIOException("cannot parse " + ALLOW_LIST, e);
    }
  }

  private static String key(String file, String text) {
    return file + "::" + text;
  }

  private static String rel(Path p) {
    return MODULE.relativize(p).toString();
  }

  private static String read(Path p) {
    try {
      return Files.readString(p, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + p + " (moved? update ADR-357 guard)", e);
    }
  }

  private static List<Path> files(Path dir, String suffix) {
    try (Stream<Path> walk = Files.walk(dir)) {
      return walk.filter(p -> p.toString().endsWith(suffix)).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot walk " + dir + " (moved? update ADR-357 guard)", e);
    }
  }

  private static String report(String header, List<String> lines) {
    return header + " — " + lines.size() + " hit(s):\n" + String.join("\n", lines);
  }

  // ---- tests -----------------------------------------------------------------------------------

  @Test
  void detectorsSeeMessagePositionsOnly() {
    String sample =
        String.join(
            "\n",
            "throw new ResourceConflictException(\"Customer email conflict\", \"x\");", // 1 hit
            "throw new InvalidStateException(\"Bad\", \"Cannot complete project\");", // 2 hit
            "log.warn(\"Failed for project {}\", id);", // 3 log
            "// throw new InvalidStateException(\"Project\", \"x\");", // 4 comment
            "var title = \"You were added to project \\\"%s\\\"\".formatted(name);", // 5 hit var
            "new VariableInfo(\"project.name\", \"Project Name\", \"string\");", // 6 hit (label)
            "a.entityType(\"project\");", // 7 ADR-342 token
            "new ResourceNotFoundException(\"Task\", id);", // 8 no noun
            "if (\"PROJECT\".equals(type)) {}", // 9 enum value
            "LIFECYCLE_ACTIVATION(\"Customer Activation\"),", // 10 hit enum label
            "DeleteGuard.forEntity(\"project\", id);", // 11 hit noun arg
            "new InvalidStateException(\"Bad\", \"entityType must be TASK or PROJECT\");", // 12
            "new NavItem(\"/invoices/billing-runs\", \"Fee runs\", \"finance\");", // 13 route
            "String reason = \"Linked to customer\";"); // 14 not a message var name
    assertThat(javaHits("Sample.java", sample).stream().map(Hit::line).toList())
        .containsExactly(1, 2, 5, 6, 10, 11);
    // json: reconcile `from` is skipped by rule, `to` and ordinary values are scanned
    var json =
        new ObjectMapper()
            .createObjectNode()
            .put("name", "Project Info")
            .put("slug", "project-info");
    json.putArray("reconcile").addObject().put("from", "Customer Info").put("to", "Client Info");
    var out = new ArrayList<Hit>();
    jsonHits("x.json", json, "", true, out);
    assertThat(out).extracting(Hit::text).containsExactly("Project Info");
    assertThat(LEGACY_404.matcher("new ResourceNotFoundException(\n \"ProjectTemplate\", id)"))
        .matches(Matcher::find);
  }

  @Test
  void scansTheRealSourceTree() {
    assertThat(files(MAIN_JAVA, ".java")).hasSizeGreaterThan(1000);
    for (String dir : PACK_DIRS) {
      assertThat(files(RESOURCES.resolve(dir), ".json")).as(dir).isNotEmpty();
    }
    assertThat(files(EMAIL_DIR, ".html")).hasSizeGreaterThanOrEqualTo(20);
  }

  @Test
  void zeroUserVisibleRemnantsOutsideTheAllowList() {
    var allowed =
        allowList().stream().map(e -> key(e.file(), e.text())).collect(Collectors.toSet());
    var remaining =
        scanTree().stream()
            .filter(h -> !allowed.contains(key(h.file(), h.text())))
            .map(Hit::render)
            .toList();
    assertThat(remaining)
        .withFailMessage(
            report(
                "ADR-357: legacy Project/Customer/Invoice copy — rewrite it (Matter/Client/Fee"
                    + " note) or allow-list it with an ADR-357 reason in"
                    + " architecture/terminology-allow-list.json",
                remaining))
        .isEmpty();
  }

  @Test
  void allowListEntriesAreLiveAndCarryAnAdr357Reason() {
    var live = scanTree().stream().map(h -> key(h.file(), h.text())).collect(Collectors.toSet());
    assertThat(
            allowList().stream()
                .filter(
                    e ->
                        !live.contains(key(e.file(), e.text()))
                            || REASONS.stream()
                                .noneMatch(
                                    r -> e.reason().equals(r) || e.reason().startsWith(r + " — ")))
                .toList())
        .as("stale allow-list entry (text no longer present) or reason outside the ADR-357 set")
        .isEmpty();
  }

  @Test
  void notFoundTitlesUseEntityNounConstants() {
    var legacy = new ArrayList<String>();
    for (Path p : files(MAIN_JAVA, ".java")) {
      String src = read(p);
      Matcher m = LEGACY_404.matcher(src);
      while (m.find())
        legacy.add(rel(p) + ":" + lineOf(src, m.start(1)) + "  \"" + m.group(1) + "\"");
    }
    assertThat(legacy)
        .withFailMessage(
            report(
                "ADR-357: 404 titles use exception/EntityNoun (MATTER/CLIENT/FEE_NOTE/…), not a"
                    + " legacy type literal",
                legacy))
        .isEmpty();
  }
}
