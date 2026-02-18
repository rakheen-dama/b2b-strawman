package io.b2mash.b2b.b2bstrawman.customer;

/** Customer type classification. */
public enum CustomerType {
  INDIVIDUAL,
  COMPANY,
  TRUST;

  /**
   * Parses a string to a CustomerType.
   *
   * @throws IllegalArgumentException if the value is not a valid customer type
   */
  public static CustomerType from(String value) {
    try {
      return valueOf(value);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new IllegalArgumentException(
          "Invalid customer type: '" + value + "'. Valid values: INDIVIDUAL, COMPANY, TRUST");
    }
  }
}
