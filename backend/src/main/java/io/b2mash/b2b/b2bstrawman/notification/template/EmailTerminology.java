package io.b2mash.b2b.b2bstrawman.notification.template;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The user-visible nouns that appear in transactional email templates and stored notification
 * titles (GAP-L-65, LZKC-031). Kazi is a legal-only product (ADR-330) with one vocabulary for every
 * tenant (ADR-338), so the fee-note nouns apply whether or not a tenant carries a vertical profile
 * (628D: an unknown or absent profile used to fall back to "Invoice").
 *
 * <p>Field names like {@code invoiceNumber} are intentionally NOT translated -- only the
 * user-visible nouns.
 */
@Component
public class EmailTerminology {

  /** Legal-only product (ADR-330, ADR-338): one vocabulary for every tenant, profile or not. */
  private static final Map<String, String> LEGAL =
      Map.of(
          "invoice", "fee note",
          "invoices", "fee notes",
          "Invoice", "Fee Note",
          "Invoices", "Fee Notes");

  /**
   * Returns the term-overrides map. The {@code namespace} (vertical-profile id) is kept for the
   * callers' signature but no longer selects a vocabulary: every namespace, including {@code null},
   * gets the legal nouns. Callers' {@code map.getOrDefault("Invoice", "Invoice")} fallbacks are
   * therefore never taken.
   */
  public Map<String, String> resolve(String namespace) {
    return LEGAL;
  }

  /**
   * Prefixes a resolved noun with the correct indefinite article ("a"/"an"). Terminology
   * substitution can change the initial sound of a noun (legal-za {@code invoice} -> "fee note"),
   * so copy with a hardcoded article breaks. A simple initial-vowel test is sufficient for the
   * current term sets (LZKC-003/LZKC-004/LZKC-009).
   */
  public static String withIndefiniteArticle(String noun) {
    if (noun == null || noun.isBlank()) {
      return noun;
    }
    return ("aeiouAEIOU".indexOf(noun.charAt(0)) >= 0 ? "an " : "a ") + noun;
  }
}
