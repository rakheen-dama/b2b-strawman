package io.b2mash.b2b.b2bstrawman;

import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.testutil.InMemoryStorageService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Test infrastructure: embedded Postgres (no Docker) + in-memory StorageService (no LocalStack).
 *
 * <p>The embedded Postgres instance is a <b>JVM-wide singleton</b> — one real postmaster process
 * shared across every Spring application context in a {@code mvn verify} run. A naive {@code @Bean}
 * approach would spawn a fresh postmaster per unique Spring context (each test class with a
 * different property source, profile, or {@code @TestConfig} inner class is a distinct cache key),
 * which accumulates dozens of postgres processes mid-run and causes cascading {@code initdb} spawn
 * failures once kernel resources saturate. We hoist the instance outside Spring's bean lifecycle
 * and register a JVM shutdown hook so the postmaster is terminated cleanly even when the JVM is
 * killed abruptly.
 *
 * <p>Tenant isolation between tests is already handled at the schema boundary — each test uses its
 * own unique {@code ORG_ID} via {@code TenantProvisioningService}, which creates a dedicated {@code
 * tenant_*} schema. Public-schema tables ({@code organizations}, {@code org_schema_mapping}, {@code
 * processed_webhooks}) are keyed by {@code org_id}, so unique orgs per test keep them naturally
 * partitioned.
 *
 * <p>Only S3PresignedUrlServiceTest needs real S3 — it uses its own LocalStack setup.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  // JVM-wide singleton — one postmaster for the entire test run.
  private static final EmbeddedPostgres POSTGRES = startEmbeddedPostgres();

  private static EmbeddedPostgres startEmbeddedPostgres() {
    try {
      EmbeddedPostgres pg =
          EmbeddedPostgres.builder()
              // Singleton Postgres is shared across Spring's test-context cache (default max 32
              // contexts). Even with minimumIdle=0 below, worst-case concurrent load is dozens of
              // Hikari pools draining connections at once during context eviction/handover. 300
              // gives generous headroom for 32 contexts × 7-connection pools without flirting with
              // the "too many clients already" FATAL.
              .setServerConfig("max_connections", "300")
              .start();
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      pg.close();
                    } catch (IOException e) {
                      // Best effort on shutdown — the JVM is exiting anyway.
                    }
                  },
                  "embedded-pg-shutdown"));
      return pg;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start embedded Postgres for tests", e);
    }
  }

  @Bean
  DynamicPropertyRegistrar datasourceProperties() {
    return registry -> {
      String jdbcUrl = POSTGRES.getJdbcUrl("postgres", "postgres");
      registry.add("spring.datasource.app.jdbc-url", () -> jdbcUrl);
      registry.add("spring.datasource.app.username", () -> "postgres");
      registry.add("spring.datasource.app.password", () -> "postgres");
      registry.add("spring.datasource.app.maximum-pool-size", () -> "3");
      // minimum-idle=0 lets Hikari release idle connections when a context is not actively
      // running a query. Without this, each cached Spring context holds its max pool size
      // worth of live Postgres connections indefinitely, and ~32 cached contexts × 7 connections
      // blow past the shared singleton's max_connections ceiling with "too many clients already".
      registry.add("spring.datasource.app.minimum-idle", () -> "0");
      registry.add("spring.datasource.migration.jdbc-url", () -> jdbcUrl);
      registry.add("spring.datasource.migration.username", () -> "postgres");
      registry.add("spring.datasource.migration.password", () -> "postgres");
      registry.add("spring.datasource.migration.maximum-pool-size", () -> "2");
      registry.add("spring.datasource.migration.minimum-idle", () -> "0");
      registry.add("spring.datasource.portal.jdbc-url", () -> jdbcUrl);
      registry.add("spring.datasource.portal.username", () -> "postgres");
      registry.add("spring.datasource.portal.password", () -> "postgres");
      registry.add("spring.datasource.portal.maximum-pool-size", () -> "2");
      registry.add("spring.datasource.portal.minimum-idle", () -> "0");
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
