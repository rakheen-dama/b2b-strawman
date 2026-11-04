package io.b2mash.b2b.b2bstrawman.integration.storage;

import java.util.regex.Pattern;

/**
 * The single definition of which storage keys may be presigned. Every {@link StorageService}
 * implementation (the S3 adapter and the in-memory test double) validates through here, so the
 * production whitelist and the one the test suite exercises cannot drift apart.
 *
 * <p>Keys are org-scoped: {@code org/{tenant}/{family}/{name}}. Generated documents use {@code
 * generated/{uuid}/{fileName}} (G88-F33); the flat {@code generated/{fileName}} form is kept so
 * keys stored on rows written before that change still presign. Anything else — extra path
 * segments, {@code ..} traversal, a non-uuid token — is rejected.
 */
public final class StorageKeyPolicy {

  private static final Pattern PRESIGNABLE_KEY =
      Pattern.compile(
          "^org/[^/]+/(project/[^/]+|org-docs|customer/[^/]+|branding|generated|generated/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|exports|templates/[^/]+)/[^/]+$");

  private StorageKeyPolicy() {}

  /**
   * Throws if {@code key} is not a presignable storage key.
   *
   * @throws IllegalArgumentException with message {@code "Invalid storage key format"}
   */
  public static void requireValidKey(String key) {
    if (key == null || !PRESIGNABLE_KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("Invalid storage key format");
    }
  }
}
