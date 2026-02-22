package io.b2mash.b2b.b2bstrawman;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @SuppressWarnings("resource")
  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
  }

  @Bean
  DynamicPropertyRegistrar datasourceProperties(PostgreSQLContainer container) {
    return registry -> {
      registry.add("spring.datasource.app.jdbc-url", container::getJdbcUrl);
      registry.add("spring.datasource.app.username", container::getUsername);
      registry.add("spring.datasource.app.password", container::getPassword);
      registry.add("spring.datasource.migration.jdbc-url", container::getJdbcUrl);
      registry.add("spring.datasource.migration.username", container::getUsername);
      registry.add("spring.datasource.migration.password", container::getPassword);
      // Portal DataSource: same database, search_path set via connection-init-sql
      registry.add("spring.datasource.portal.jdbc-url", container::getJdbcUrl);
      registry.add("spring.datasource.portal.username", container::getUsername);
      registry.add("spring.datasource.portal.password", container::getPassword);
      registry.add(
          "spring.datasource.portal.connection-init-sql",
          () -> "SET search_path TO portal, public");
    };
  }

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
