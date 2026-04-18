package io.b2mash.b2b.b2bstrawman.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Tests the StorageService (S3StorageAdapter) and S3PresignedUrlService key builders. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class S3PresignedUrlServiceTest {

  @Autowired private StorageService storageService;

  @Test
  void presignedUrlsAreGenerated() {
    String orgId = "org_test123";
    String projectId = "proj-aaa";
    String documentId = "doc-111";
    String contentType = "text/plain";

    String s3Key = S3PresignedUrlService.buildKey(orgId, projectId, documentId);

    var uploadResult = storageService.generateUploadUrl(s3Key, contentType, Duration.ofHours(1));
    assertThat(uploadResult.url()).isNotBlank();
    assertThat(uploadResult.url()).contains(s3Key);
    assertThat(uploadResult.expiresAt()).isNotNull();

    var downloadResult = storageService.generateDownloadUrl(s3Key, Duration.ofHours(1));
    assertThat(downloadResult.url()).isNotBlank();
    assertThat(downloadResult.url()).contains(s3Key);
    assertThat(downloadResult.expiresAt()).isNotNull();
  }

  @Test
  void s3KeyFollowsExpectedFormat() {
    String key = S3PresignedUrlService.buildKey("org_abc", "proj-1", "doc-2");
    assertThat(key).isEqualTo("org/org_abc/project/proj-1/doc-2");
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
  void generateUrlRejectsPathTraversalAndNullKeys() {
    assertThatThrownBy(
            () -> storageService.generateDownloadUrl("../../etc/passwd", Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class);
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
