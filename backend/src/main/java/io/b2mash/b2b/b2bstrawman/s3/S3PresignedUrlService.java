package io.b2mash.b2b.b2bstrawman.s3;

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
}
