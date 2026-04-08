package io.b2mash.b2b.b2bstrawman;

import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.testutil.InMemoryStorageService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Test infrastructure: embedded Postgres (no Docker) + in-memory StorageService (no LocalStack).
 *
 * <p>Only S3PresignedUrlServiceTest needs real S3 — it uses its own LocalStack setup.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean(destroyMethod = "close")
  EmbeddedPostgres embeddedPostgres() throws Exception {
    return EmbeddedPostgres.builder().setServerConfig("max_connections", "300").start();
  }

  @Bean
  DynamicPropertyRegistrar datasourceProperties(EmbeddedPostgres pg) {
    return registry -> {
      String jdbcUrl = pg.getJdbcUrl("postgres", "postgres");
      registry.add("spring.datasource.app.jdbc-url", () -> jdbcUrl);
      registry.add("spring.datasource.app.username", () -> "postgres");
      registry.add("spring.datasource.app.password", () -> "postgres");
      registry.add("spring.datasource.app.maximum-pool-size", () -> "3");
      registry.add("spring.datasource.migration.jdbc-url", () -> jdbcUrl);
      registry.add("spring.datasource.migration.username", () -> "postgres");
      registry.add("spring.datasource.migration.password", () -> "postgres");
      registry.add("spring.datasource.migration.maximum-pool-size", () -> "2");
      registry.add("spring.datasource.portal.jdbc-url", () -> jdbcUrl);
      registry.add("spring.datasource.portal.username", () -> "postgres");
      registry.add("spring.datasource.portal.password", () -> "postgres");
      registry.add("spring.datasource.portal.maximum-pool-size", () -> "2");
      registry.add(
          "spring.datasource.portal.connection-init-sql",
          () -> "SET search_path TO portal, public");
    };
  }

  @Bean
  @Primary
  StorageService inMemoryStorageService() {
    return new InMemoryStorageService();
  }
}
