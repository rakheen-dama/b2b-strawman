package io.b2mash.b2b.b2bstrawman.proposal;

/** Fee model for a proposal (ADR-129). */
public enum FeeModel {
  FIXED("Fixed Fee"),
  HOURLY("Hourly"),
  RETAINER("Retainer");

  private final String displayLabel;

  FeeModel(String displayLabel) {
    this.displayLabel = displayLabel;
  }

  /** Returns a human-readable display label for UI rendering. */
  public String getDisplayLabel() {
    return displayLabel;
  }
}
