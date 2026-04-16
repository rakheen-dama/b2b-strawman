package io.b2mash.b2b.b2bstrawman.packs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Static utility for computing deterministic content hashes over JSON content. Used to detect
 * whether pack-installed content has been modified by the tenant.
 */
public final class ContentHashUtil {

  private static final JsonMapper CANONICAL_MAPPER =
      JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

  private ContentHashUtil() {}

  /**
   * Computes a SHA-256 hex digest of the given canonical JSON string.
   *
   * @param canonicalJson JSON string with sorted keys and no formatting whitespace
   * @return lowercase hex SHA-256 hash (64 characters)
   */
  public static String computeHash(String canonicalJson) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * Canonicalizes a JSON node by sorting keys alphabetically and stripping formatting whitespace.
   *
   * @param node the JSON node to canonicalize
   * @return canonical JSON string with sorted keys and no extra whitespace
   */
  public static String canonicalizeJson(JsonNode node) {
    try {
      var obj = CANONICAL_MAPPER.treeToValue(node, Object.class);
      return CANONICAL_MAPPER.writeValueAsString(obj);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to canonicalize JSON", e);
    }
  }
}
