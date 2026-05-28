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

  /** Returns the set of active shard IDs (always includes "primary"). */
  Set<String> getActiveShardIds();

  /** Reloads shard configuration from the database and rebuilds secondary DataSource instances. */
  void refresh();
}
