package io.b2mash.b2b.b2bstrawman.exception;

/**
 * Display nouns for entities whose class names and stored keys are still the pre-Phase-87 ones
 * (Project / Customer / Invoice / Expense — ADR-342 keeps the keys). ADR-357: the 404 title is
 * "{@code <noun> not found}" and the detail "{@code No <noun lower-cased> found with id …}", built
 * by {@link ResourceNotFoundException}; call sites pass one of these constants, never a literal, so
 * the legal word is resolved once and cannot drift per call site. Guarded by {@code
 * architecture/TerminologyRemnantScanTest}.
 */
public final class EntityNoun {

  public static final String MATTER = "Matter";
  public static final String CLIENT = "Client";
  public static final String FEE_NOTE = "Fee note";
  public static final String DISBURSEMENT = "Disbursement";

  // Compound nouns that used to render as "ProjectTemplate not found" etc. (628D R-3).
  public static final String MATTER_TEMPLATE = "Matter template";
  public static final String MATTER_MEMBER = "Matter member";
  public static final String FEE_NOTE_LINE = "Fee note line";
  public static final String FEE_ESTIMATE = "Fee estimate";
  public static final String CLIENT_MATTER_LINK = "Client-matter link";

  private EntityNoun() {}
}
