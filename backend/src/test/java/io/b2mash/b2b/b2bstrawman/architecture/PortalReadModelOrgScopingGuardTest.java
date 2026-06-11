package io.b2mash.b2b.b2bstrawman.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tenant-isolation structural guard for the portal read-model layer (TD-004).
 *
 * <p>The portal read-model lives in a single shared {@code portal} schema and is queried with raw
 * {@link org.springframework.jdbc.core.simple.JdbcClient} statements, NOT through Hibernate. Unlike
 * the rest of the app — which gets tenant isolation for free from schema-per-tenant {@code
 * search_path} routing — every portal statement must carry its own tenant-scoping predicate by
 * hand. There is no automatic safety net: a single new query that forgets {@code WHERE org_id = ?}
 * (or {@code WHERE customer_id = ?}) is a silent cross-tenant data leak. With ~70 hand-written
 * statements across several repositories, each new query is a copy-paste opportunity to forget the
 * predicate.
 *
 * <p><b>Two tenant discriminators, by design.</b> The portal read-model uses two scoping columns
 * depending on the table:
 *
 * <ul>
 *   <li>{@code org_id} — the original projects/documents/comments/invoices/tasks/requests tables
 *       carry the Clerk org id directly.
 *   <li>{@code customer_id} — the trust-ledger, retainer, and deadline tables (V19–V21) scope by
 *       the globally-unique {@code customer_id} UUID instead and have <b>no {@code org_id}
 *       column</b>; their migrations state this explicitly ("customer_id ... uniquely identifies
 *       the firm customer in the global portal schema"). A {@code customer_id} belongs to exactly
 *       one org, so it is an equivalent tenant boundary.
 * </ul>
 *
 * <p>This test makes a missing predicate a build failure. It scans the source of the read-model
 * package, extracts every {@code jdbc.sql("""...""")} statement keyed to its enclosing method, and
 * asserts each statement either:
 *
 * <ol>
 *   <li><b>(a)</b> <b>binds</b> a tenant discriminator — {@code org_id} or {@code customer_id} — in
 *       a predicate ({@code = ?}, {@code = ANY (?)}) or, for an {@code INSERT}, writes it as a
 *       column (so the row is stamped with its tenant); or
 *   <li><b>(b)</b> is an explicit, per-entry-justified entry in {@link #SCOPING_EXEMPT} — the same
 *       pattern as {@code KNOWN_UNSEEDED_DEFAULTS} in {@code
 *       VerticalProfileModuleSlugValidationTest}.
 * </ol>
 *
 * <p>The check is <b>predicate-aware</b>: a discriminator appearing only in a {@code SELECT} list
 * does NOT count as scoping (it is data, not a filter). {@code findRequestsByPortalContactId}, for
 * example, selects {@code org_id} but filters by {@code portal_contact_id} — it is correctly
 * treated as unscoped and lives on the exemption list.
 *
 * <p><b>Why source-scanning, not ArchUnit:</b> the predicate we must verify lives inside SQL string
 * literals in Java text blocks. ArchUnit analyzes bytecode dependencies and cannot see the contents
 * of a string constant, so it is the wrong tool here. We read the {@code .java} source directly.
 *
 * <p><b>Why a package, not a single class:</b> Wave 3.2b will split {@code
 * PortalReadModelRepository} into per-domain read-repositories. By scanning the whole {@code
 * repository} package (and any future read-model packages added to {@link
 * #READ_MODEL_SOURCE_DIRS}), the split's successor classes — and the existing sibling repositories
 * — are automatically covered without editing this test.
 */
class PortalReadModelOrgScopingGuardTest {

  /**
   * Source directories (relative to the backend module root) whose {@code jdbc.sql(...)} statements
   * must be tenant-scoped. Add the package of any future portal read-model repository here so the
   * guard keeps covering the read-model layer as Wave 3.2b splits it apart.
   */
  private static final List<String> READ_MODEL_SOURCE_DIRS =
      List.of("src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository");

  /** The columns that constitute a valid tenant boundary in the portal read-model. */
  private static final List<String> TENANT_DISCRIMINATORS = List.of("org_id", "customer_id");

  /**
   * Statements that legitimately do NOT bind a tenant discriminator, keyed by {@code
   * "ClassName#methodName"}. Every entry MUST be justified. Categories:
   *
   * <ul>
   *   <li><b>FK child of an org-scoped parent</b> — the table has neither {@code org_id} nor {@code
   *       customer_id}; it is isolated via a {@code FK ... ON DELETE CASCADE} to a tenant-scoped
   *       parent. Adding a discriminator would require a schema change (out of scope for a guard
   *       PR).
   *   <li><b>Globally-unique token lookup</b> — keyed by an unguessable, globally-unique {@code
   *       request_token} (the magic-link credential itself).
   *   <li><b>PK/contact-scoped on a discriminator-bearing table</b> — scoped by PK or {@code
   *       portal_contact_id} on a table that DOES have a discriminator, but the only callers verify
   *       ownership at the service layer, or are internal event handlers driven by already
   *       tenant-scoped domain events (never untrusted portal input). Hardening candidate for Wave
   *       3.2b, not a present leak — see report.
   *   <li><b>Admin/backfill cross-tenant sweep</b> — a deliberately tenant-agnostic statement used
   *       only by trusted internal backfill/tear-down jobs (e.g. enumerate all customers that have
   *       portal rows), never reachable from a portal request.
   * </ul>
   *
   * <p>NONE of these are confirmed cross-tenant leaks; each is isolated by an alternative mechanism
   * verified against the table DDL and the calling code. A genuinely unscoped query that SHOULD be
   * scoped would carry a {@code // TODO: REAL FINDING} marker here and be reported prominently — at
   * the time of writing there are none.
   */
  private static final Map<String, String> SCOPING_EXEMPT = buildExemptions();

  private static Map<String, String> buildExemptions() {
    Map<String, String> m = new LinkedHashMap<>();
    String main = "PortalReadModelRepository#";
    String trust = "PortalTrustReadModelRepository#";
    String deadline = "PortalDeadlineViewRepository#";

    // ── portal_invoice_lines: child of portal_invoices, FK ON DELETE CASCADE, no discriminator ──
    m.put(
        main + "upsertPortalInvoiceLine",
        "portal_invoice_lines has no org_id/customer_id column; FK portal_invoice_id -> "
            + "portal_invoices(id) ON DELETE CASCADE provides isolation. INSERT.");
    m.put(
        main + "deletePortalInvoiceLinesByInvoice",
        "portal_invoice_lines has no discriminator; scoped by FK portal_invoice_id to the "
            + "org-scoped portal_invoices.");
    m.put(
        main + "findInvoiceLinesByInvoice",
        "portal_invoice_lines has no discriminator; scoped by FK portal_invoice_id; the parent "
            + "invoice is fetched org-scoped by the caller first.");

    // ── portal_request_items: child of portal_requests, FK ON DELETE CASCADE, no discriminator ──
    m.put(
        main + "upsertPortalRequestItem",
        "portal_request_items has no discriminator; FK request_id -> portal_requests(id) ON DELETE "
            + "CASCADE provides isolation. INSERT.");
    m.put(
        main + "updatePortalRequestItemStatus",
        "portal_request_items has no discriminator; driven by internal PortalEventHandler off "
            + "already tenant-scoped domain events, not untrusted portal input.");
    m.put(
        main + "recalculatePortalRequestCounts",
        "portal_request_items has no discriminator; subqueries scope by request_id (FK). Driven by "
            + "internal PortalEventHandler off tenant-scoped events.");
    m.put(
        main + "findRequestItemsByRequestId",
        "portal_request_items has no discriminator; scoped by FK request_id. PortalReadModelService "
            + "verifies portal_contact ownership of the parent request first.");

    // ── portal_acceptance_requests: table has NO discriminator column at all (V11 DDL) ──
    m.put(
        main + "saveAcceptanceRequest",
        "portal_acceptance_requests has no org_id/customer_id column; scoped by portal_contact_id / "
            + "request_token. INSERT.");
    m.put(
        main + "updateAcceptanceRequestStatus",
        "portal_acceptance_requests has no discriminator; mutated by PK id obtained from an "
            + "org/contact-scoped lookup.");
    m.put(
        main + "findAcceptanceRequestsByContactId",
        "portal_acceptance_requests has no discriminator; scoped by portal_contact_id, which "
            + "belongs to exactly one org.");
    m.put(
        main + "findByRequestToken",
        "portal_acceptance_requests has no discriminator; lookup by the globally-unique unguessable "
            + "request_token (the magic-link credential itself).");
    m.put(
        main + "findPendingAcceptancesByContactId",
        "portal_acceptance_requests has no discriminator; scoped by portal_contact_id, which "
            + "belongs to exactly one org.");

    // ── portal_requests: HAS org_id, but these scope by contact/PK (service-layer ownership guard)
    // ──
    m.put(
        main + "findRequestsByPortalContactId",
        "portal_requests has org_id, but this lists by portal_contact_id (one contact -> one org). "
            + "Hardening candidate for 3.2b; not a present leak.");
    m.put(
        main + "findRequestById",
        "portal_requests has org_id, but PortalReadModelService.findRequestById verifies "
            + "portal_contact ownership before returning. Hardening candidate for 3.2b.");
    m.put(
        main + "updatePortalRequestStatus",
        "portal_requests has org_id, but driven by internal PortalEventHandler off already "
            + "tenant-scoped domain events (requestId from the event), not portal input. Hardening "
            + "candidate for 3.2b.");

    // ── portal_deadline_view: HAS customer_id, but this delete scopes by (source_entity, id) only
    // ──
    m.put(
        deadline + "deleteBySourceEntityAndId",
        "portal_deadline_view has customer_id, but the cancellation-path delete scopes by the "
            + "composite (source_entity, id) firm-side key. Driven by DeadlinePortalSyncService off "
            + "tenant-scoped firm events, not portal input. Hardening candidate for 3.2b.");

    // ── Admin/backfill cross-tenant sweep — trusted internal jobs only ──
    m.put(
        trust + "findCustomerIdsWithPortalTrustRows",
        "Deliberately tenant-agnostic: enumerates every customer_id that has any portal trust row, "
            + "for the backfill drift-repair job (TrustLedgerPortalSyncService). Never reachable "
            + "from a portal request.");

    return m;
  }

  /**
   * Matches {@code jdbc.sql("..."} / {@code jdbc.sql("""...""")} — both text blocks and one-liners.
   */
  private static final Pattern SQL_ARG =
      Pattern.compile(
          "\\.sql\\(\\s*(\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\]|\\\\.)*)\")", Pattern.DOTALL);

  /** Matches {@code public|private|protected ... methodName(} to attribute SQL to its method. */
  private static final Pattern METHOD_DECL =
      Pattern.compile(
          "^\\s*(?:public|private|protected)\\s+[\\w<>,.\\[\\]?\\s]+?\\b(\\w+)\\s*\\(",
          Pattern.MULTILINE);

  private record SqlStatement(String className, String methodName, String sql) {
    String key() {
      return className + "#" + methodName;
    }
  }

  @Test
  void everyPortalReadModelStatementIsTenantScopedOrExplicitlyExempt() throws IOException {
    Path moduleRoot = resolveModuleRoot();
    List<SqlStatement> statements = collectStatements(moduleRoot);

    assertThat(statements)
        .as(
            "no jdbc.sql(...) statements found under %s — the source-scanning guard is mis-wired "
                + "and would silently pass; fix READ_MODEL_SOURCE_DIRS / resolveModuleRoot()",
            READ_MODEL_SOURCE_DIRS)
        .isNotEmpty();

    for (SqlStatement stmt : statements) {
      boolean scoped = isTenantScoped(stmt.sql());
      boolean exempt = SCOPING_EXEMPT.containsKey(stmt.key());

      assertThat(scoped || exempt)
          .as(
              "Portal read-model tenant-isolation guard (TD-004): %s has a SQL statement that does "
                  + "not bind a tenant discriminator (%s) and is not on the justified exemption "
                  + "list. Either add the scoping predicate (the safe default) or add a per-entry "
                  + "justification to SCOPING_EXEMPT in this test. Offending SQL:%n%s",
              stmt.key(), TENANT_DISCRIMINATORS, indent(stmt.sql()))
          .isTrue();
    }
  }

  /**
   * Exemptions must not rot: every exemption entry must correspond to a real, currently-unscoped
   * statement. If a statement gains a scoping predicate (e.g. the 3.2b hardening lands) or the
   * method is deleted, its stale exemption must be removed so the list stays honest.
   */
  @Test
  void exemptionListHasNoStaleEntries() throws IOException {
    Path moduleRoot = resolveModuleRoot();
    List<SqlStatement> statements = collectStatements(moduleRoot);

    Map<String, Boolean> scopedByKey = new LinkedHashMap<>();
    for (SqlStatement stmt : statements) {
      // A method with multiple statements is "scoped" only if ALL its statements are scoped.
      scopedByKey.merge(stmt.key(), isTenantScoped(stmt.sql()), (a, b) -> a && b);
    }

    for (String exemptKey : SCOPING_EXEMPT.keySet()) {
      assertThat(scopedByKey)
          .as(
              "SCOPING_EXEMPT entry '%s' does not match any statement in the read-model source — "
                  + "the method was removed or renamed. Remove the stale exemption.",
              exemptKey)
          .containsKey(exemptKey);
      assertThat(scopedByKey.get(exemptKey))
          .as(
              "SCOPING_EXEMPT entry '%s' is now tenant-scoped (all its statements bind a "
                  + "discriminator). Remove the stale exemption so the guard tracks reality.",
              exemptKey)
          .isFalse();
    }
  }

  // ── scoping detection ────────────────────────────────────────────────

  /**
   * A statement is tenant-scoped if it BINDS a discriminator: either a predicate ({@code
   * discriminator = ?} or {@code discriminator = ANY (?)}) for SELECT/UPDATE/DELETE, or an {@code
   * INSERT} whose column list contains the discriminator (the row is stamped with its tenant). A
   * discriminator that appears only in a SELECT projection is NOT scoping.
   */
  private static boolean isTenantScoped(String sql) {
    String normalized = sql.toLowerCase(Locale.ROOT);
    for (String col : TENANT_DISCRIMINATORS) {
      // Predicate binding: `col = ?` or `col = any (...)`.
      Pattern predicate = Pattern.compile("\\b" + col + "\\s*=\\s*(\\?|any\\b)");
      if (predicate.matcher(normalized).find()) {
        return true;
      }
      // INSERT column list: the discriminator appears inside the (..) column list of an INSERT.
      if (normalized.contains("insert into") && insertColumnList(normalized).contains(col)) {
        return true;
      }
    }
    return false;
  }

  /** Extracts the parenthesised column list of an {@code INSERT INTO table (...) VALUES} clause. */
  private static String insertColumnList(String normalizedSql) {
    Matcher m =
        Pattern.compile("insert\\s+into\\s+[\\w.\"]+\\s*\\((.*?)\\)\\s*values", Pattern.DOTALL)
            .matcher(normalizedSql);
    return m.find() ? m.group(1) : "";
  }

  // ── source extraction ────────────────────────────────────────────────

  private static List<SqlStatement> collectStatements(Path moduleRoot) throws IOException {
    List<SqlStatement> out = new ArrayList<>();
    for (String dir : READ_MODEL_SOURCE_DIRS) {
      Path sourceDir = moduleRoot.resolve(dir);
      if (!Files.isDirectory(sourceDir)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(sourceDir)) {
        for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
          out.addAll(extractFromFile(file));
        }
      }
    }
    return out;
  }

  private static List<SqlStatement> extractFromFile(Path file) throws IOException {
    String source = Files.readString(file);
    String className = file.getFileName().toString().replace(".java", "");

    // Index method declarations by their start offset so each SQL block can be attributed.
    List<Integer> methodStarts = new ArrayList<>();
    List<String> methodNames = new ArrayList<>();
    Matcher methodMatcher = METHOD_DECL.matcher(source);
    while (methodMatcher.find()) {
      methodStarts.add(methodMatcher.start());
      methodNames.add(methodMatcher.group(1));
    }

    List<SqlStatement> out = new ArrayList<>();
    Matcher sqlMatcher = SQL_ARG.matcher(source);
    while (sqlMatcher.find()) {
      String sql = sqlMatcher.group(2) != null ? sqlMatcher.group(2) : sqlMatcher.group(3);
      String enclosing = enclosingMethod(methodStarts, methodNames, sqlMatcher.start());
      out.add(new SqlStatement(className, enclosing, sql));
    }
    return out;
  }

  private static String enclosingMethod(
      List<Integer> methodStarts, List<String> methodNames, int sqlStart) {
    String name = "<unknown>";
    for (int i = 0; i < methodStarts.size(); i++) {
      if (methodStarts.get(i) <= sqlStart) {
        name = methodNames.get(i);
      } else {
        break;
      }
    }
    return name;
  }

  /**
   * Resolves the backend module root regardless of whether tests run with the working directory set
   * to the repo root or the backend module. The marker is the presence of the read-model package.
   */
  private static Path resolveModuleRoot() {
    Path cwd = Path.of("").toAbsolutePath();
    String marker = READ_MODEL_SOURCE_DIRS.get(0);
    for (Path candidate : new Path[] {cwd, cwd.resolve("backend"), cwd.getParent()}) {
      if (candidate != null && Files.isDirectory(candidate.resolve(marker))) {
        return candidate;
      }
    }
    // Fall back to cwd; the empty-statements assertion will flag a mis-resolution loudly.
    return cwd;
  }

  private static String indent(String sql) {
    return sql.lines().map(l -> "    " + l).reduce((a, b) -> a + "\n" + b).orElse("");
  }
}
