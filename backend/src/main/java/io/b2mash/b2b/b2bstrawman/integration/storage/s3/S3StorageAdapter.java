package io.b2mash.b2b.b2bstrawman.integration.storage.s3;

import io.b2mash.b2b.b2bstrawman.config.S3Config.S3Properties;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** S3 implementation of {@link StorageService}. All AWS SDK types are confined to this class. */
@Component
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3", matchIfMissing = true)
public class S3StorageAdapter implements StorageService {

  private static final Logger log = LoggerFactory.getLogger(S3StorageAdapter.class);

  /** Validates that S3 keys follow the expected org-scoped path structure. */
  private static final Pattern S3_KEY_PATTERN =
      Pattern.compile(
          "^org/[^/]+/(project/[^/]+|org-docs|customer/[^/]+|branding|generated|exports)/[^/]+$");

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final String bucketName;

  public S3StorageAdapter(S3Client s3Client, S3Presigner s3Presigner, S3Properties s3Properties) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
    this.bucketName = s3Properties.bucketName();
  }

  @Override
  public String upload(String key, byte[] content, String contentType) {
    var putRequest =
        PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build();
    s3Client.putObject(putRequest, RequestBody.fromBytes(content));
    return key;
  }

  @Override
  public String upload(String key, InputStream content, long contentLength, String contentType) {
    var putRequest =
        PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build();
    s3Client.putObject(putRequest, RequestBody.fromInputStream(content, contentLength));
    return key;
  }

  @Override
  public byte[] download(String key) {
    var getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
    try (var response = s3Client.getObject(getRequest)) {
      return response.readAllBytes();
    } catch (Exception e) {
      log.warn("Download failed for key: {}", key, e);
      throw new RuntimeException("Failed to download object from storage", e);
    }
  }

  @Override
  public void delete(String key) {
    try {
      var deleteRequest = DeleteObjectRequest.builder().bucket(bucketName).key(key).build();
      s3Client.deleteObject(deleteRequest);
    } catch (Exception e) {
      log.warn("Best-effort S3 deletion failed for key={}: {}", key, e.getMessage());
    }
  }

  @Override
  public PresignedUrl generateUploadUrl(String key, String contentType, Duration expiry) {
    validateKey(key);
    var putRequest =
        PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build();

    var presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .putObjectRequest(putRequest)
            .build();

    var presigned = s3Presigner.presignPutObject(presignRequest);
    return new PresignedUrl(presigned.url().toExternalForm(), Instant.now().plus(expiry));
  }

  @Override
  public PresignedUrl generateDownloadUrl(String key, Duration expiry) {
    validateKey(key);
    var getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();

    var presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .getObjectRequest(getRequest)
            .build();

    var presigned = s3Presigner.presignGetObject(presignRequest);
    return new PresignedUrl(presigned.url().toExternalForm(), Instant.now().plus(expiry));
  }

  private static void validateKey(String key) {
    if (key == null || !S3_KEY_PATTERN.matcher(key).matches()) {
      throw new IllegalArgumentException("Invalid storage key format");
    }
  }
}
