package io.b2mash.b2b.b2bstrawman.view;

import java.util.HashMap;
import java.util.Map;

public final class CustomFieldFilterUtil {

  private CustomFieldFilterUtil() {}

  public static Map<String, String> extractCustomFieldFilters(Map<String, String> allParams) {
    var filters = new HashMap<String, String>();
    if (allParams != null) {
      allParams.forEach(
          (key, value) -> {
            if (key.startsWith("customField[") && key.endsWith("]")) {
              String slug = key.substring("customField[".length(), key.length() - 1);
              filters.put(slug, value);
            }
          });
    }
    return filters;
  }

  public static boolean matchesCustomFieldFilters(
      Map<String, Object> customFields, Map<String, String> filters) {
    if (customFields == null) {
      return filters.isEmpty();
    }
    for (var entry : filters.entrySet()) {
      Object fieldValue = customFields.get(entry.getKey());
      if (fieldValue == null || !fieldValue.toString().equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }
}
