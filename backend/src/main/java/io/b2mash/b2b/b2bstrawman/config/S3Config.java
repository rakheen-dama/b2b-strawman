package io.b2mash.b2b.b2bstrawman.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties({
  S3Config.S3Properties.class,
  S3Config.AwsCredentialsProperties.class
})
public class S3Config {

  @ConfigurationProperties("aws.s3")
  public record S3Properties(String endpoint, String region, String bucketName) {}

  @ConfigurationProperties("aws.credentials")
  public record AwsCredentialsProperties(String accessKeyId, String secretAccessKey) {}

  @Bean(destroyMethod = "close")
  S3Client s3Client(S3Properties s3Props, AwsCredentialsProperties credProps) {
    var builder = S3Client.builder().region(Region.of(s3Props.region()));

    if (s3Props.endpoint() != null && !s3Props.endpoint().isBlank()) {
      builder
          .endpointOverride(URI.create(s3Props.endpoint()))
          .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(
                      credProps.accessKeyId(), credProps.secretAccessKey())));
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }

    return builder.build();
  }

  @Bean(destroyMethod = "close")
  S3Presigner s3Presigner(S3Properties s3Props, AwsCredentialsProperties credProps) {
    var builder = S3Presigner.builder().region(Region.of(s3Props.region()));

    if (s3Props.endpoint() != null && !s3Props.endpoint().isBlank()) {
      builder
          .endpointOverride(URI.create(s3Props.endpoint()))
          .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(
                      credProps.accessKeyId(), credProps.secretAccessKey())));
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }

    return builder.build();
  }
}
