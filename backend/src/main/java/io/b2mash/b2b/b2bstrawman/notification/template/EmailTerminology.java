package io.b2mash.b2b.b2bstrawman.notification.template;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Java-side mirror of the portal's {@code TERMINOLOGY} TS map, scoped to the user-visible nouns
 * that appear in transactional email templates (GAP-L-65). Keyed by vertical-profile id (e.g.
 * {@code "legal-za"}) -- the same key feeds the portal {@code <TerminologyProvider>}, so a single
 * customer record renders with one consistent vocabulary across firm UI, portal UI, and email.
 *
 * <p>Invoice nouns were mapped first (slice 19); LZKC-004 added the proposal nouns so client-facing
 * engagement-letter emails and seeded letter bodies stop leaking "proposal" on legal-za tenants.
 * Field names like {@code invoiceNumber} are intentionally NOT translated -- only the user-visible
 * nouns.
 */
@Component
public class EmailTerminology {

  private static final Map<String, Map<String, String>> MAP =
      Map.of(
          "legal-za",
              Map.of(
                  "invoice", "fee note",
                  "invoices", "fee notes",
                  "Invoice", "Fee Note",
                  "Invoices", "Fee Notes",
                  "proposal", "engagement letter",
                  "proposals", "engagement letters",
                  "Proposal", "Engagement Letter",
                  "Proposals", "Engagement Letters"),
          "accounting-za", Map.of(),
          "consulting-za", Map.of());

  /**
   * Returns the term-overrides map for the given namespace. An empty map (identity translation) is
   * returned for {@code null} or unknown namespaces -- callers can safely use {@code
   * map.getOrDefault("Invoice", "Invoice")} without null-guarding.
   */
  public Map<String, String> resolve(String namespace) {
    if (namespace == null) {
      return Map.of();
    }
    return MAP.getOrDefault(namespace, Map.of());
  }

  /**
   * Prefixes a resolved noun with the correct indefinite article ("a"/"an"). Terminology
   * substitution can change the initial sound of a noun (legal-za {@code invoice} -> "fee note",
   * {@code proposal} -> "engagement letter"), so copy with a hardcoded article breaks. A simple
   * initial-vowel test is sufficient for the current term sets (LZKC-003/LZKC-004/LZKC-009).
   */
  public static String withIndefiniteArticle(String noun) {
    if (noun == null || noun.isBlank()) {
      return noun;
    }
    return ("aeiouAEIOU".indexOf(noun.charAt(0)) >= 0 ? "an " : "a ") + noun;
  }
}
