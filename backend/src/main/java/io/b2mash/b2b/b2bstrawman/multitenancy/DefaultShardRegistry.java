package io.b2mash.b2b.b2bstrawman.multitenancy;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link ShardRegistry}. Manages per-shard {@link DataSource} instances.
 * The primary shard reuses the Spring-managed {@code appDataSource}. Secondary shards are
 * configured from environment variables following the naming convention {@code
 * KAZI_SHARD_{SHARD_ID_UPPER}_{PROPERTY}}.
 *
 * <p>Implements {@link SmartInitializingSingleton} instead of using {@code @PostConstruct} to defer
 * the {@link #refresh()} call until after all singleton beans (including the EntityManagerFactory)
 * are fully initialized. This avoids a circular dependency: EMF -> HibernateMultiTenancyConfig ->
 * ShardAwareConnectionProvider -> ShardRegistry -> ShardConfigRepository -> EMF. The primary shard
 * DataSource is registered eagerly in the constructor so that Hibernate's dialect detection (via
 * {@link ShardAwareConnectionProvider#getAnyConnection()}) works during EMF bootstrap.
 */
@Component
@ConditionalOnProperty(name = "kazi.sharding.enabled", havingValue = "true")
public class DefaultShardRegistry implements ShardRegistry, SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(DefaultShardRegistry.class);
  private static final String PRIMARY_SHARD_ID = "primary";

  private final DataSource primaryDataSource;
  private final ShardConfigRepository shardConfigRepository;
  private final Environment environment;

  private volatile ConcurrentHashMap<String, DataSource> dataSources = new ConcurrentHashMap<>();

  public DefaultShardRegistry(
      DataSource primaryDataSource,
      ShardConfigRepository shardConfigRepository,
      Environment environment) {
    this.primaryDataSource = primaryDataSource;
    this.shardConfigRepository = shardConfigRepository;
    this.environment = environment;
    // Pre-seed with primary shard so getAnyConnection() works during EMF bootstrap
    this.dataSources.put(PRIMARY_SHARD_ID, primaryDataSource);
  }

  @Override
  public void afterSingletonsInstantiated() {
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
    // Build a new map locally, then atomically swap the reference to avoid a window
    // where concurrent getDataSource() calls see an empty/incomplete map.
    var newDataSources = new ConcurrentHashMap<String, DataSource>();

    // Primary shard always present
    newDataSources.put(PRIMARY_SHARD_ID, primaryDataSource);

    var activeShards = shardConfigRepository.findByActiveTrue();
    for (var shard : activeShards) {
      if (PRIMARY_SHARD_ID.equals(shard.getShardId())) {
        continue; // Primary already registered
      }

      DataSource ds = createSecondaryDataSource(shard);
      if (ds != null) {
        newDataSources.put(shard.getShardId(), ds);
        log.info(
            "Registered secondary shard: {} (poolSize={})",
            shard.getShardId(),
            shard.getPoolSize());
      }
    }

    // Atomically swap the live map, then close stale secondary DataSources from the old map
    var oldDataSources = this.dataSources;
    this.dataSources = newDataSources;
    closeSecondaryDataSources(oldDataSources);

    log.info(
        "ShardRegistry initialized with {} active shard(s): {}",
        dataSources.size(),
        dataSources.keySet());
  }

  @PreDestroy
  void shutdown() {
    closeSecondaryDataSources(this.dataSources);
  }

  private DataSource createSecondaryDataSource(ShardConfig shard) {
    String shardIdUpper = shard.getShardId().toUpperCase(Locale.ROOT);
    String envPrefix = "KAZI_SHARD_" + shardIdUpper;

    String url = environment.getProperty(envPrefix + "_URL");
    String username = environment.getProperty(envPrefix + "_USERNAME");
    String password = environment.getProperty(envPrefix + "_PASSWORD");

    if (url == null || username == null || password == null) {
      if (shard.isActive()) {
        throw new IllegalStateException(
            "Active shard '%s' is missing required environment variables. Expected: %s_URL, %s_USERNAME, %s_PASSWORD"
                .formatted(shard.getShardId(), envPrefix, envPrefix, envPrefix));
      }
      log.warn(
          "Missing environment variables for inactive shard '{}'. Expected: {}_URL, {}_USERNAME, {}_PASSWORD. Skipping.",
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
    ds.setMaximumPoolSize(Math.min(shard.getPoolSize(), 10));
    ds.setMaxLifetime(1_680_000); // 28 min, matches app datasource (under Neon 30-min timeout)
    ds.setConnectionTimeout(10_000); // 10 sec, accommodates Neon cold starts
    ds.setConnectionInitSql("SET search_path TO public");
    ds.setPoolName("shard-" + shard.getShardId());
    ds.setReadOnly(shard.isReadOnly());

    return ds;
  }

  private void closeSecondaryDataSources(ConcurrentHashMap<String, DataSource> map) {
    for (var entry : map.entrySet()) {
      if (!PRIMARY_SHARD_ID.equals(entry.getKey())
          && entry.getValue() instanceof HikariDataSource hikari) {
        try {
          hikari.close();
        } catch (Exception e) {
          log.warn("Failed to close DataSource for shard '{}': {}", entry.getKey(), e.getMessage());
        }
      }
    }
    map.clear();
  }
}
