package io.b2mash.b2b.b2bstrawman.expense;

/** Expense category for disbursements tracked against projects. */
public enum ExpenseCategory {
  FILING_FEE,
  TRAVEL,
  COURIER,
  SOFTWARE,
  SUBCONTRACTOR,
  PRINTING,
  COMMUNICATION,
  OTHER;

  /**
   * Case-insensitive lookup of an ExpenseCategory from a string value.
   *
   * @param value the string to convert
   * @return the matching ExpenseCategory
   * @throws IllegalArgumentException if the value does not match any category
   */
  public static ExpenseCategory fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Expense category must not be null");
    }
    try {
      return valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown expense category: " + value);
    }
  }
}
