package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.util.regex.Pattern;

/**
 * Composite tenant identifier combining shard ID and schema name. Used by the connection provider
 * to route queries to the correct database shard and schema. Format: {@code
 * {shardId}:{schemaName}}.
 *
 * @see <a href="../../architecture/ARCHITECTURE.md">ADR-296: Composite Tenant Identifier Format</a>
 */
public record ShardAndSchema(String shardId, String schemaName) {

  /**
   * Shard IDs: lowercase alphanumeric with underscores, 1-50 chars, starting with a letter and not
   * ending in an underscore. Single-character IDs (e.g. "a") are allowed via the optional tail
   * group (D7). "primary" is also accepted (and happens to match this pattern too).
   */
  private static final Pattern SHARD_ID_PATTERN =
      Pattern.compile("^[a-z]([a-z0-9_]{0,48}[a-z0-9])?$");

  /** Schema names: "public" or "tenant_" followed by 12 hex chars. */
  private static final Pattern SCHEMA_NAME_PATTERN = Pattern.compile("^tenant_[0-9a-f]{12}$");

  /** Compact constructor — validates both components so no invalid instances can exist. */
  public ShardAndSchema {
    validateShardId(shardId);
    validateSchemaName(schemaName);
  }

  /** Default identifier for the primary shard's public schema. */
  public static final ShardAndSchema DEFAULT = new ShardAndSchema("primary", "public");

  /**
   * Parses a composite identifier of the form {@code shardId:schemaName}.
   *
   * @param composite the composite identifier string
   * @return a validated ShardAndSchema instance
   * @throws IllegalArgumentException if the format is invalid
   */
  public static ShardAndSchema parse(String composite) {
    if (composite == null || composite.isBlank()) {
      throw new IllegalArgumentException("Composite identifier must not be null or blank");
    }

    int colonIndex = composite.indexOf(':');
    if (colonIndex < 0) {
      throw new IllegalArgumentException(
          "Invalid composite identifier format (missing colon): " + composite);
    }

    String shard = composite.substring(0, colonIndex);
    String schema = composite.substring(colonIndex + 1);

    // Constructor validates both components
    return new ShardAndSchema(shard, schema);
  }

  /**
   * Formats a shard ID and schema name into a composite identifier string. Both parameters are
   * validated before formatting.
   *
   * @param shardId the shard identifier
   * @param schemaName the schema name
   * @return the composite identifier in {@code shardId:schemaName} format
   * @throws IllegalArgumentException if either parameter is invalid
   */
  public static String format(String shardId, String schemaName) {
    validateShardId(shardId);
    validateSchemaName(schemaName);
    return shardId + ":" + schemaName;
  }

  /**
   * Validates a shard identifier, throwing {@link IllegalArgumentException} if it is null, blank,
   * or malformed. Use to fail fast at binding time rather than late during identifier resolution.
   *
   * @param shardId the shard identifier to validate
   */
  public static void requireValidShardId(String shardId) {
    validateShardId(shardId);
  }

  private static void validateShardId(String shardId) {
    if (shardId == null || shardId.isBlank()) {
      throw new IllegalArgumentException("Shard ID must not be null or blank");
    }
    // "primary" is always valid; single-char shard IDs (e.g. "a") are now allowed by the pattern.
    if (!"primary".equals(shardId) && !SHARD_ID_PATTERN.matcher(shardId).matches()) {
      throw new IllegalArgumentException("Invalid shard ID format: " + shardId);
    }
  }

  private static void validateSchemaName(String schemaName) {
    if (schemaName == null || schemaName.isBlank()) {
      throw new IllegalArgumentException("Schema name must not be null or blank");
    }
    if (!"public".equals(schemaName) && !SCHEMA_NAME_PATTERN.matcher(schemaName).matches()) {
      throw new IllegalArgumentException("Invalid schema name format: " + schemaName);
    }
  }
}
