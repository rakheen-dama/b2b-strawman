package io.b2mash.b2b.b2bstrawman.notification.template;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Java-side mirror of the portal's {@code TERMINOLOGY} TS map, scoped to the user-visible nouns
 * that appear in transactional email templates (GAP-L-65). Keyed by vertical-profile id (e.g.
 * {@code "legal-za"}) -- the same key feeds the portal {@code <TerminologyProvider>}, so a single
 * customer record renders with one consistent vocabulary across firm UI, portal UI, and email.
 *
 * <p>Only invoice nouns are mapped here for now (slice 19); slice 23 (closure-pack notifications)
 * may extend the map. Field names like {@code invoiceNumber} are intentionally NOT translated --
 * only the user-visible noun "Invoice"/"invoices".
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
                  "Invoices", "Fee Notes"),
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
}
