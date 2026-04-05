package io.b2mash.b2b.b2bstrawman;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Full Testcontainers configuration with PostgreSQL + LocalStack S3. Use this only for tests that
 * actually need S3. For tests that only need PostgreSQL, use {@link TestPostgresConfiguration}
 * instead.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(TestPostgresConfiguration.class)
public class TestcontainersConfiguration {

  @SuppressWarnings("resource")
  @Bean
  LocalStackContainer localStackContainer() {
    var container =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
            .withServices(LocalStackContainer.Service.S3);
    container.start();
    return container;
  }

  @Bean
  DynamicPropertyRegistrar s3Properties(LocalStackContainer localstack) {
    return registry -> {
      registry.add(
          "aws.s3.endpoint",
          () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
      registry.add("aws.s3.region", localstack::getRegion);
      registry.add("aws.s3.bucket-name", () -> "test-bucket");
      registry.add("aws.credentials.access-key-id", localstack::getAccessKey);
      registry.add("aws.credentials.secret-access-key", localstack::getSecretKey);
    };
  }

  @Bean
  String createTestS3Bucket(LocalStackContainer localstack) {
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
      s3.createBucket(CreateBucketRequest.builder().bucket("test-bucket").build());
    }
    return "test-bucket";
  }
}
