package io.b2mash.b2b.b2bstrawman.orgrole;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum Capability {
  FINANCIAL_VISIBILITY,
  INVOICING,
  PROJECT_MANAGEMENT,
  TEAM_OVERSIGHT,
  CUSTOMER_MANAGEMENT,
  AUTOMATIONS,
  RESOURCE_PLANNING;

  public static final Set<String> ALL_NAMES =
      Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

  public static Capability fromString(String value) {
    try {
      return valueOf(value);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new IllegalArgumentException(
          "Invalid capability: '%s'. Valid values: %s".formatted(value, ALL_NAMES));
    }
  }
}
