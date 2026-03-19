package io.b2mash.b2b.b2bstrawman.datarequest;

/**
 * Static utility class with statutory jurisdiction defaults for data protection compliance. Not a
 * Spring bean — no injection, no state. Pattern mirrors VerticalProfileRegistry but simpler (code,
 * not JSON files).
 *
 * <p>See ADR-195 (DSAR deadline calculation) and ADR-194 (retention policy granularity).
 */
public final class JurisdictionDefaults {

  private JurisdictionDefaults() {}

  /**
   * Returns the statutory default DSAR response deadline in days for the given jurisdiction. This
   * is the deadline used when no tenant override is configured.
   */
  public static int getDefaultDeadlineDays(String jurisdiction) {
    return switch (jurisdiction) {
      case "ZA" -> 30; // POPIA Section 23
      case "EU" -> 30; // GDPR Article 12(3)
      case "BR" -> 15; // LGPD Article 18
      case null, default -> 30;
    };
  }

  /**
   * Returns the maximum allowed DSAR deadline days for the jurisdiction. Tenant overrides are
   * capped at this value (ADR-195).
   */
  public static int getMaxDeadlineDays(String jurisdiction) {
    return switch (jurisdiction) {
      case "ZA" -> 30;
      case "EU" -> 30;
      case "BR" -> 15;
      case null, default -> 90;
    };
  }

  /**
   * Returns the minimum financial record retention period in months for the jurisdiction. Financial
   * records (invoices, billable time entries) cannot be retained for shorter than this. ZA: Income
   * Tax Act Section 29 + VAT Act Section 55 (5 years = 60 months).
   */
  public static int getMinFinancialRetentionMonths(String jurisdiction) {
    return switch (jurisdiction) {
      case "ZA" -> 60; // 5 years (SA Income Tax Act + VAT Act)
      case "EU" -> 72; // 6 years (conservative across member states)
      case "BR" -> 60; // 5 years (LGPD)
      case null, default -> 60;
    };
  }

  /** Returns the name of the data protection regulator for the jurisdiction. */
  public static String getRegulatorName(String jurisdiction) {
    return switch (jurisdiction) {
      case "ZA" -> "Information Regulator (South Africa)";
      case "EU" -> "Data Protection Authority";
      case "BR" -> "ANPD (Autoridade Nacional de Proteção de Dados)";
      case null, default -> "";
    };
  }

  /**
   * Returns the mandatory compliance document type for the jurisdiction. Used to determine which
   * template pack is required.
   */
  public static String getMandatoryDocumentType(String jurisdiction) {
    return switch (jurisdiction) {
      case "ZA" -> "paia_section_51_manual";
      case "EU" -> "ropa";
      case "BR" -> "";
      case null, default -> "";
    };
  }
}
