package io.b2mash.b2b.b2bstrawman.fielddefinition;

import java.util.Map;

/** Shared utility methods for custom field value inspection. */
public final class CustomFieldUtils {

  private CustomFieldUtils() {}

  /**
   * Determines whether a custom field value is considered "filled" (non-blank). CURRENCY fields
   * require both a non-null {@code amount} and a non-blank {@code currency} code inside the map.
   * All other field types fall back to a simple {@code toString().isBlank()} check.
   */
  public static boolean isFieldValueFilled(FieldDefinition fd, Object value) {
    if (value == null) {
      return false;
    }
    if (fd.getFieldType() == FieldType.CURRENCY) {
      if (!(value instanceof Map<?, ?> map)) {
        return false;
      }
      var amount = map.get("amount");
      var currency = map.get("currency");
      return amount != null && currency != null && !currency.toString().isBlank();
    }
    return !value.toString().isBlank();
  }
}
