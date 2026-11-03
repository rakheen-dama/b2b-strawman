package io.b2mash.b2b.b2bstrawman.integration.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.config.S3Config.S3Properties;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.testutil.InMemoryStorageService;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * G88-F33: the presign key whitelist must accept the identity-based generated-document key {@code
 * org/{tenant}/generated/{uuid}/{fileName}} and keep accepting the legacy flat {@code
 * org/{tenant}/generated/{fileName}} keys stored on existing rows. Runs every case against the
 * production {@link S3StorageAdapter} (real offline presigner, no network) and the {@link
 * InMemoryStorageService} test double, so the two patterns cannot drift apart.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageKeyValidationTest {

  private static final String UUID_SEGMENT = "3f2c8a10-5b7e-4c1d-9a2b-0e6f4d8c1a7b";

  private final StaticCredentialsProvider credentials =
      StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret"));
  private final S3Client s3Client =
      S3Client.builder().region(Region.AF_SOUTH_1).credentialsProvider(credentials).build();
  private final S3Presigner s3Presigner =
      S3Presigner.builder().region(Region.AF_SOUTH_1).credentialsProvider(credentials).build();
  private final S3StorageAdapter s3Adapter =
      new S3StorageAdapter(
          s3Client, s3Presigner, new S3Properties(null, "af-south-1", "test-bucket", null));
  private final InMemoryStorageService inMemory = new InMemoryStorageService();

  @AfterAll
  void closeClients() {
    s3Presigner.close();
    s3Client.close();
  }

  Stream<Arguments> acceptedKeys() {
    return implementations()
        .flatMap(
            impl ->
                Stream.of(
                        // New identity-based shape
                        "org/tenant_5039f2d497cf/generated/"
                            + UUID_SEGMENT
                            + "/statement-of-account-sipho-dlamini-2026-10-29.pdf",
                        "org/tenant_5039f2d497cf/generated/" + UUID_SEGMENT + "/fee-note.docx",
                        // Legacy flat shape still stored on existing rows
                        "org/tenant_5039f2d497cf/generated/statement-of-account-sipho-dlamini-2026-10-29.pdf",
                        "org/tenant_5039f2d497cf/generated/paia-section-51-manual-2026-10-29-1a2b3c4d.pdf")
                    .map(key -> Arguments.of(impl.get()[0], impl.get()[1], key)));
  }

  Stream<Arguments> rejectedKeys() {
    return implementations()
        .flatMap(
            impl ->
                Stream.of(
                        "org/t/generated/" + UUID_SEGMENT + "/nested/name.pdf",
                        "org/t/generated/" + UUID_SEGMENT + "/",
                        "org/t/generated/not-a-uuid/name.pdf",
                        "org/t/generated/../name.pdf",
                        "org/t/generated/" + UUID_SEGMENT + "/../../etc/passwd")
                    .map(key -> Arguments.of(impl.get()[0], impl.get()[1], key)));
  }

  @ParameterizedTest(name = "{0} presigns {2}")
  @MethodSource("acceptedKeys")
  void presignedDownloadResolvesGeneratedKeys(String name, StorageService storage, String key) {
    var presigned = storage.generateDownloadUrl(key, Duration.ofMinutes(5));
    assertThat(presigned.url()).contains(key.substring(key.lastIndexOf('/') + 1));
  }

  @ParameterizedTest(name = "{0} rejects {2}")
  @MethodSource("rejectedKeys")
  void presignedDownloadRejectsMalformedGeneratedKeys(
      String name, StorageService storage, String key) {
    assertThatThrownBy(() -> storage.generateDownloadUrl(key, Duration.ofMinutes(5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid storage key format");
  }

  private Stream<Arguments> implementations() {
    return Stream.of(
        Arguments.of("S3StorageAdapter", s3Adapter),
        Arguments.of("InMemoryStorageService", inMemory));
  }
}
