package io.b2mash.b2b.b2bstrawman.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/** Tests the StorageService (S3StorageAdapter) and S3PresignedUrlService key builders. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Testcontainers
class S3PresignedUrlServiceTest {

  private static final String TEST_BUCKET = "test-bucket";

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
          .withServices(LocalStackContainer.Service.S3);

  @DynamicPropertySource
  static void overrideS3Properties(
      org.springframework.test.context.DynamicPropertyRegistry registry) {
    registry.add(
        "aws.s3.endpoint",
        () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
    registry.add("aws.s3.region", localstack::getRegion);
    registry.add("aws.s3.bucket-name", () -> TEST_BUCKET);
    registry.add("aws.credentials.access-key-id", localstack::getAccessKey);
    registry.add("aws.credentials.secret-access-key", localstack::getSecretKey);
  }

  @BeforeAll
  static void createBucket() {
    try (var s3 =
        S3Client.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()) {
      s3.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build());
    }
  }

  @Autowired private StorageService storageService;

  @Test
  void uploadUrlAllowsPutAndDownloadUrlAllowsGet() throws IOException, InterruptedException {
    String orgId = "org_test123";
    String projectId = "proj-aaa";
    String documentId = "doc-111";
    String contentType = "text/plain";
    String fileContent = "Hello, S3 integration test!";

    // Build key using utility
    String s3Key = S3PresignedUrlService.buildKey(orgId, projectId, documentId);

    // Generate upload URL via StorageService
    var uploadResult = storageService.generateUploadUrl(s3Key, contentType, Duration.ofHours(1));

    assertThat(uploadResult.url()).isNotBlank();
    assertThat(uploadResult.expiresAt()).isNotNull();

    // Upload via presigned URL
    try (var httpClient = HttpClient.newHttpClient()) {
      var putRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(uploadResult.url()))
              .header("Content-Type", contentType)
              .PUT(HttpRequest.BodyPublishers.ofString(fileContent))
              .build();

      var putResponse = httpClient.send(putRequest, HttpResponse.BodyHandlers.ofString());
      assertThat(putResponse.statusCode()).isEqualTo(200);

      // Generate download URL via StorageService
      var downloadResult = storageService.generateDownloadUrl(s3Key, Duration.ofHours(1));
      assertThat(downloadResult.url()).isNotBlank();
      assertThat(downloadResult.expiresAt()).isNotNull();

      // Download via presigned URL and verify content
      var getRequest = HttpRequest.newBuilder().uri(URI.create(downloadResult.url())).GET().build();

      var getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
      assertThat(getResponse.statusCode()).isEqualTo(200);
      assertThat(getResponse.body()).isEqualTo(fileContent);
    }
  }

  @Test
  void s3KeyFollowsExpectedFormat() {
    String key = S3PresignedUrlService.buildKey("org_abc", "proj-1", "doc-2");
    assertThat(key).isEqualTo("org/org_abc/project/proj-1/doc-2");
  }

  @Test
  void differentOrgsProduceDifferentKeys() {
    String key1 = S3PresignedUrlService.buildKey("org_aaa", "proj-1", "doc-1");
    String key2 = S3PresignedUrlService.buildKey("org_bbb", "proj-1", "doc-1");

    assertThat(key1).isNotEqualTo(key2);
    assertThat(key1).startsWith("org/org_aaa/");
    assertThat(key2).startsWith("org/org_bbb/");
  }

  @Test
  void uploadUrlContainsBucketAndKey() {
    String key = S3PresignedUrlService.buildKey("org_x", "proj-y", "doc-z");
    var result = storageService.generateUploadUrl(key, "application/pdf", Duration.ofHours(1));

    assertThat(result.url()).contains(TEST_BUCKET);
    assertThat(result.url()).contains("org/org_x/project/proj-y/doc-z");
  }

  @Test
  void uploadAndDownloadBytes() {
    String key = "org/test-org/project/test-proj/roundtrip-test";
    byte[] content = "Round-trip test content".getBytes();

    storageService.upload(key, content, "text/plain");
    byte[] downloaded = storageService.download(key);

    assertThat(downloaded).isEqualTo(content);
  }

  @Test
  void generateDownloadUrlRejectsInvalidKey() {
    assertThatThrownBy(
            () -> storageService.generateDownloadUrl("../../etc/passwd", Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void generateUploadUrlRejectsInvalidKey() {
    assertThatThrownBy(
            () ->
                storageService.generateUploadUrl(
                    "arbitrary/path", "text/plain", Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void generateDownloadUrlRejectsNullKey() {
    assertThatThrownBy(() -> storageService.generateDownloadUrl(null, Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deleteIsIdempotent() {
    String key = "org/test-org/project/test-proj/delete-test";
    storageService.upload(key, "to-delete".getBytes(), "text/plain");

    // First delete succeeds
    storageService.delete(key);
    // Second delete is best-effort (no exception)
    storageService.delete(key);
  }
}
