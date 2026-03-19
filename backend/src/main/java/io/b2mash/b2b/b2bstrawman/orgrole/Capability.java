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
  RESOURCE_PLANNING,
  MANAGE_COMPLIANCE,
  MANAGE_COMPLIANCE_DESTRUCTIVE;

  /** Capabilities restricted to the owner role — admin does NOT inherit these. */
  public static final Set<String> OWNER_ONLY = Set.of(MANAGE_COMPLIANCE_DESTRUCTIVE.name());

  public static final Set<String> ALL_NAMES =
      Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

  public static Capability fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "Invalid capability: null. Valid values: %s".formatted(ALL_NAMES));
    }
    try {
      return valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid capability: '%s'. Valid values: %s".formatted(value, ALL_NAMES));
    }
  }
}
