package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.util.Set;
import javax.sql.DataSource;

/**
 * Registry for per-shard DataSource instances. The primary shard always maps to the Spring-managed
 * {@code appDataSource} bean. Secondary shards are resolved from environment variables and
 * configured dynamically at startup.
 */
public interface ShardRegistry {

  /**
   * Returns the DataSource for the given shard.
   *
   * @param shardId the shard identifier
   * @return the DataSource for the shard
   * @throws IllegalArgumentException if the shard is unknown or inactive
   */
  DataSource getDataSource(String shardId);

  /** Returns the DataSource for the primary shard. */
  DataSource getPrimaryDataSource();

  /**
   * Returns a DataSource suitable for running DDL (schema creation, Flyway migrations) against the
   * given shard. DDL statements (CREATE SCHEMA, CREATE TABLE, ...) are rejected by PgBouncer in
   * transaction-pooling mode, so when {@code KAZI_SHARD_{ID}_MIGRATION_URL} is configured this
   * returns a dedicated direct-connection DataSource that bypasses the pooler. When no migration
   * URL is configured it falls back to {@link #getDataSource(String)} (correct for shards that
   * connect directly without PgBouncer). See kazi-infra-review-scheduling-sharding.md finding D3.
   *
   * <p>Callers provisioning/migrating the primary shard should use the dedicated {@code
   * migrationDataSource} bean directly rather than this method.
   *
   * @param shardId the shard identifier
   * @return a DataSource safe for DDL against the shard
   * @throws IllegalArgumentException if the shard is unknown or inactive
   */
  DataSource getMigrationDataSource(String shardId);

  /** Returns the set of active shard IDs (always includes "primary"). */
  Set<String> getActiveShardIds();

  /** Reloads shard configuration from the database and rebuilds secondary DataSource instances. */
  void refresh();
}
