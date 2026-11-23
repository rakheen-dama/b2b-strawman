package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.Locale;
import java.util.UUID;

/**
 * S3 keys of take-on files (§17.6.1): {@code take-on/{tenantSchema}/{batchId}/{uuid}/{fileName}} —
 * a fresh uuid segment per upload, so a re-upload of a same-named file never overwrites the earlier
 * object. Keys are never presigned: downloads stream through the backend and are audited.
 */
public final class TakeOnStorageKeys {

  static final int MAX_NAME_LENGTH = 200;

  private TakeOnStorageKeys() {}

  /** The prefix of every object of one batch (used by tests and the purge). */
  public static String batchPrefix(String tenantSchema, UUID batchId) {
    return "take-on/%s/%s/".formatted(tenantSchema, batchId);
  }

  public static String sourceKey(String tenantSchema, UUID batchId, String sanitisedFileName) {
    return batchPrefix(tenantSchema, batchId) + UUID.randomUUID() + "/" + sanitisedFileName;
  }

  /**
   * A safe file name: path components stripped, control characters removed, anything outside {@code
   * [A-Za-z0-9._ -]} replaced by {@code _}, at most 200 characters keeping the extension; blank →
   * {@code upload.csv} / {@code upload.xlsx} by {@code format}.
   */
  public static String sanitize(String original, TakeOnSource.FileFormat format) {
    String fallback = "upload." + format.name().toLowerCase(Locale.ROOT);
    if (original == null || original.isBlank()) {
      return fallback;
    }
    int lastSlash = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
    String name = lastSlash >= 0 ? original.substring(lastSlash + 1) : original;
    name = name.replaceAll("\\p{Cntrl}", "");
    name = name.replaceAll("[^A-Za-z0-9._ -]", "_").strip();
    if (name.isEmpty() || name.chars().allMatch(c -> c == '.' || c == '_')) {
      return fallback;
    }
    if (name.length() > MAX_NAME_LENGTH) {
      int dot = name.lastIndexOf('.');
      String ext = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
      name = name.substring(0, MAX_NAME_LENGTH - ext.length()) + ext;
    }
    return name;
  }
}
