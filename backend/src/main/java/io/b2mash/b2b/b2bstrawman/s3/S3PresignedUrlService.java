package io.b2mash.b2b.b2bstrawman.s3;

import io.b2mash.b2b.b2bstrawman.config.S3Config.S3Properties;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class S3PresignedUrlService {

  private static final Duration URL_EXPIRY = Duration.ofHours(1);
  private static final Pattern S3_KEY_PATTERN = Pattern.compile("^org/[^/]+/project/[^/]+/[^/]+$");

  private final S3Presigner presigner;
  private final String bucketName;

  public S3PresignedUrlService(S3Presigner presigner, S3Properties s3Properties) {
    this.presigner = presigner;
    this.bucketName = s3Properties.bucketName();
  }

  public PresignedUploadResult generateUploadUrl(
      String orgId, String projectId, String documentId, String contentType) {
    String s3Key = buildKey(orgId, projectId, documentId);

    var putRequest =
        PutObjectRequest.builder().bucket(bucketName).key(s3Key).contentType(contentType).build();

    var presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(URL_EXPIRY)
            .putObjectRequest(putRequest)
            .build();

    var presigned = presigner.presignPutObject(presignRequest);
    return new PresignedUploadResult(
        presigned.url().toExternalForm(), s3Key, URL_EXPIRY.toSeconds());
  }

  public PresignedDownloadResult generateDownloadUrl(String s3Key) {
    if (s3Key == null || !S3_KEY_PATTERN.matcher(s3Key).matches()) {
      throw new IllegalArgumentException("Invalid S3 key format: " + s3Key);
    }

    var getRequest = GetObjectRequest.builder().bucket(bucketName).key(s3Key).build();

    var presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(URL_EXPIRY)
            .getObjectRequest(getRequest)
            .build();

    var presigned = presigner.presignGetObject(presignRequest);
    return new PresignedDownloadResult(presigned.url().toExternalForm(), URL_EXPIRY.toSeconds());
  }

  static String buildKey(String orgId, String projectId, String documentId) {
    return "org/" + orgId + "/project/" + projectId + "/" + documentId;
  }

  public record PresignedUploadResult(String url, String s3Key, long expiresInSeconds) {}

  public record PresignedDownloadResult(String url, long expiresInSeconds) {}
}
