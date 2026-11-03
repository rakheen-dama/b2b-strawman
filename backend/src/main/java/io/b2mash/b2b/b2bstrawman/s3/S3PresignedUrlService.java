package io.b2mash.b2b.b2bstrawman.s3;

import java.util.UUID;

/**
 * Utility class for building S3 storage keys. Presign logic has been moved to {@link
 * io.b2mash.b2b.b2bstrawman.integration.storage.StorageService}.
 */
public final class S3PresignedUrlService {

  private S3PresignedUrlService() {}

  /** Build a PROJECT-scoped storage key: org/{orgId}/project/{projectId}/{documentId}. */
  public static String buildKey(String orgId, String projectId, String documentId) {
    return "org/" + orgId + "/project/" + projectId + "/" + documentId;
  }

  /** Build an ORG-scoped storage key: org/{orgId}/org-docs/{documentId}. */
  public static String buildOrgKey(String orgId, String documentId) {
    return "org/" + orgId + "/org-docs/" + documentId;
  }

  /** Build a CUSTOMER-scoped storage key: org/{orgId}/customer/{customerId}/{documentId}. */
  public static String buildCustomerKey(String orgId, String customerId, String documentId) {
    return "org/" + orgId + "/customer/" + customerId + "/" + documentId;
  }

  /**
   * Build a generated-document storage key: org/{tenantId}/generated/{token}/{fileName}.
   *
   * <p>G88-F33: the file name is display-only — it is built from the template slug, the matter or
   * client name and a date, so it is not unique. {@code token} must be a fresh random UUID per
   * upload so that two documents with the same file name never share (and overwrite) one object.
   * Keeping the file name as the last path segment preserves the browser's default save name.
   */
  public static String buildGeneratedKey(String tenantId, UUID token, String fileName) {
    return "org/" + tenantId + "/generated/" + token + "/" + fileName;
  }
}
