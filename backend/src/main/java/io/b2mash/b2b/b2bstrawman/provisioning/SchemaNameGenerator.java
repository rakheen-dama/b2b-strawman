package io.b2mash.b2b.b2bstrawman.provisioning;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SchemaNameGenerator {

  private static final UUID NAMESPACE = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

  private SchemaNameGenerator() {}

  public static String generateSchemaName(String clerkOrgId) {
    if (clerkOrgId == null || clerkOrgId.isBlank()) {
      throw new IllegalArgumentException("Clerk org ID must not be null or blank");
    }
    byte[] input = (NAMESPACE + clerkOrgId).getBytes(StandardCharsets.UTF_8);
    UUID hash = UUID.nameUUIDFromBytes(input);
    String hex = hash.toString().replace("-", "");
    return "tenant_" + hex.substring(0, 12);
  }
}
