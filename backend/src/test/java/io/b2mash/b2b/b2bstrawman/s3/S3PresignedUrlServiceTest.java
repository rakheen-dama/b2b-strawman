package io.b2mash.b2b.b2bstrawman.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

  @Autowired private S3PresignedUrlService service;

  @Test
  void uploadUrlAllowsPutAndDownloadUrlAllowsGet() throws IOException, InterruptedException {
    String orgId = "org_test123";
    String projectId = "proj-aaa";
    String documentId = "doc-111";
    String contentType = "text/plain";
    String fileContent = "Hello, S3 integration test!";

    // Generate upload URL
    var uploadResult = service.generateUploadUrl(orgId, projectId, documentId, contentType);

    assertThat(uploadResult.url()).isNotBlank();
    assertThat(uploadResult.s3Key()).isEqualTo("org/org_test123/project/proj-aaa/doc-111");
    assertThat(uploadResult.expiresInSeconds()).isEqualTo(3600);

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

      // Generate download URL
      var downloadResult = service.generateDownloadUrl(uploadResult.s3Key());
      assertThat(downloadResult.url()).isNotBlank();
      assertThat(downloadResult.expiresInSeconds()).isEqualTo(3600);

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
    var result = service.generateUploadUrl("org_x", "proj-y", "doc-z", "application/pdf");

    assertThat(result.url()).contains(TEST_BUCKET);
    assertThat(result.url()).contains("org/org_x/project/proj-y/doc-z");
  }

  @Test
  void downloadUrlRejectsInvalidKeyFormat() {
    assertThatThrownBy(() -> service.generateDownloadUrl("../../etc/passwd"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> service.generateDownloadUrl("arbitrary-key"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> service.generateDownloadUrl(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
