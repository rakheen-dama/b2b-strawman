package io.b2mash.b2b.b2bstrawman.multitenancy;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link ShardRegistry}. Manages per-shard {@link DataSource} instances.
 * The primary shard reuses the Spring-managed {@code appDataSource}. Secondary shards are
 * configured from environment variables following the naming convention {@code
 * KAZI_SHARD_{SHARD_ID_UPPER}_{PROPERTY}}.
 */
@Component
@ConditionalOnProperty(name = "kazi.sharding.enabled", havingValue = "true")
public class DefaultShardRegistry implements ShardRegistry {

  private static final Logger log = LoggerFactory.getLogger(DefaultShardRegistry.class);
  private static final String PRIMARY_SHARD_ID = "primary";

  private final DataSource primaryDataSource;
  private final ShardConfigRepository shardConfigRepository;
  private final Environment environment;

  private final ConcurrentHashMap<String, DataSource> dataSources = new ConcurrentHashMap<>();

  public DefaultShardRegistry(
      DataSource primaryDataSource,
      ShardConfigRepository shardConfigRepository,
      Environment environment) {
    this.primaryDataSource = primaryDataSource;
    this.shardConfigRepository = shardConfigRepository;
    this.environment = environment;
  }

  @PostConstruct
  void initialize() {
    refresh();
  }

  @Override
  public DataSource getDataSource(String shardId) {
    DataSource ds = dataSources.get(shardId);
    if (ds == null) {
      throw new IllegalArgumentException("Unknown or inactive shard: " + shardId);
    }
    return ds;
  }

  @Override
  public DataSource getPrimaryDataSource() {
    return primaryDataSource;
  }

  @Override
  public Set<String> getActiveShardIds() {
    return Collections.unmodifiableSet(dataSources.keySet());
  }

  @Override
  public void refresh() {
    // Close existing secondary DataSources before rebuilding
    closeSecondaryDataSources();

    // Primary shard always present
    dataSources.put(PRIMARY_SHARD_ID, primaryDataSource);

    var activeShards = shardConfigRepository.findByActiveTrue();
    for (var shard : activeShards) {
      if (PRIMARY_SHARD_ID.equals(shard.getShardId())) {
        continue; // Primary already registered
      }

      DataSource ds = createSecondaryDataSource(shard);
      if (ds != null) {
        dataSources.put(shard.getShardId(), ds);
        log.info(
            "Registered secondary shard: {} (poolSize={})",
            shard.getShardId(),
            shard.getPoolSize());
      }
    }

    log.info(
        "ShardRegistry initialized with {} active shard(s): {}",
        dataSources.size(),
        dataSources.keySet());
  }

  @PreDestroy
  void shutdown() {
    closeSecondaryDataSources();
  }

  private DataSource createSecondaryDataSource(ShardConfig shard) {
    String shardIdUpper = shard.getShardId().toUpperCase();
    String envPrefix = "KAZI_SHARD_" + shardIdUpper;

    String url = environment.getProperty(envPrefix + "_URL");
    String username = environment.getProperty(envPrefix + "_USERNAME");
    String password = environment.getProperty(envPrefix + "_PASSWORD");

    if (url == null || username == null || password == null) {
      log.warn(
          "Missing environment variables for shard '{}'. Expected: {}_URL, {}_USERNAME, {}_PASSWORD. Skipping.",
          shard.getShardId(),
          envPrefix,
          envPrefix,
          envPrefix);
      return null;
    }

    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(url);
    ds.setUsername(username);
    ds.setPassword(password);
    ds.setMaximumPoolSize(shard.getPoolSize());
    ds.setPoolName("shard-" + shard.getShardId());
    ds.setReadOnly(shard.isReadOnly());

    return ds;
  }

  private void closeSecondaryDataSources() {
    for (var entry : dataSources.entrySet()) {
      if (!PRIMARY_SHARD_ID.equals(entry.getKey())
          && entry.getValue() instanceof HikariDataSource hikari) {
        try {
          hikari.close();
        } catch (Exception e) {
          log.warn("Failed to close DataSource for shard '{}': {}", entry.getKey(), e.getMessage());
        }
      }
    }
    dataSources.clear();
  }
}
