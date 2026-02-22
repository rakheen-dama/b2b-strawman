package io.b2mash.b2b.b2bstrawman.integration.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * Abstraction for object storage operations. Domain services inject this interface instead of
 * vendor-specific clients (e.g., S3Client).
 *
 * <p>System-wide: selected via @ConditionalOnProperty, not per-tenant.
 */
public interface StorageService {

  /** Upload a file from bytes and return the storage key. */
  String upload(String key, byte[] content, String contentType);

  /** Upload a file from an InputStream (for large files). */
  String upload(String key, InputStream content, long contentLength, String contentType);

  /** Download a file's content as bytes. */
  byte[] download(String key);

  /** Delete a file. Best-effort -- logs warning on failure. */
  void delete(String key);

  /** Generate a presigned upload URL (time-limited). */
  PresignedUrl generateUploadUrl(String key, String contentType, Duration expiry);

  /** Generate a presigned download URL (time-limited). */
  PresignedUrl generateDownloadUrl(String key, Duration expiry);
}
