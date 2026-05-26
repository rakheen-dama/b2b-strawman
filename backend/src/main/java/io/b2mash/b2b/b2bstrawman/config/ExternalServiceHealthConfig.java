package io.b2mash.b2b.b2bstrawman.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
class ExternalServiceHealthConfig {

  @Bean
  HealthIndicator connectionPoolHealth(@Qualifier("appDataSource") HikariDataSource dataSource) {
    return () -> {
      var pool = dataSource.getHikariPoolMXBean();
      if (pool == null) {
        return Health.unknown().withDetail("reason", "pool not yet initialized").build();
      }
      int active = pool.getActiveConnections();
      int total = pool.getTotalConnections();
      int max = dataSource.getMaximumPoolSize();
      double utilization = max > 0 ? (double) active / max : 0;

      var builder =
          utilization < 0.9
              ? Health.up()
              : Health.down().withDetail("reason", "pool utilization >= 90%");
      return builder
          .withDetail("active", active)
          .withDetail("idle", pool.getIdleConnections())
          .withDetail("total", total)
          .withDetail("max", max)
          .withDetail("utilization", String.format("%.0f%%", utilization * 100))
          .withDetail("threadsAwaiting", pool.getThreadsAwaitingConnection())
          .build();
    };
  }

  @Bean
  HealthIndicator s3Health(S3Client s3Client) {
    return () -> {
      try {
        s3Client.listBuckets();
        return Health.up().build();
      } catch (Exception e) {
        return Health.down().withDetail("error", e.getMessage()).build();
      }
    };
  }
}
