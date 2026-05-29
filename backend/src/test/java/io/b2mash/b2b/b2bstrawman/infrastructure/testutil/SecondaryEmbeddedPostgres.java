package io.b2mash.b2b.b2bstrawman.infrastructure.testutil;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;

/**
 * JVM-wide singleton for a secondary embedded Postgres instance. Used by multi-shard integration
 * tests that need a second DataSource distinct from the primary (TestcontainersConfiguration).
 *
 * <p>Uses the same zonky embedded-postgres API as the primary instance. Lower max_connections (50)
 * since only a few test classes use this instance.
 *
 * <p>Port is auto-allocated — no explicit port configuration needed.
 *
 * <p><b>Status:</b> Pre-staged for Epic 555B (multi-shard integration tests). Currently unused —
 * will be referenced once shard-aware integration test classes are introduced in that epic.
 */
public final class SecondaryEmbeddedPostgres {

  private static final EmbeddedPostgres POSTGRES = startSecondaryPostgres();

  private SecondaryEmbeddedPostgres() {}

  private static EmbeddedPostgres startSecondaryPostgres() {
    try {
      EmbeddedPostgres pg =
          EmbeddedPostgres.builder().setServerConfig("max_connections", "50").start();
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
                  "secondary-embedded-pg-shutdown"));
      return pg;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start secondary embedded Postgres for tests", e);
    }
  }

  /** Returns a DataSource connected to the secondary embedded Postgres. */
  public static DataSource getDataSource() {
    return POSTGRES.getPostgresDatabase();
  }

  /** Returns the JDBC URL for the secondary embedded Postgres. */
  public static String getJdbcUrl() {
    return POSTGRES.getJdbcUrl("postgres", "postgres");
  }
}
