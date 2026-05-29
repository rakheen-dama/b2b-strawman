package io.b2mash.b2b.b2bstrawman.multitenancy;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-shard Micrometer metrics for HikariCP connection pool state and tenant counts. Pool gauges
 * (active, idle, pending) are read from {@link HikariPoolMXBean} on demand. Tenant counts are
 * refreshed from the database every 60 seconds.
 */
@Component
@ConditionalOnProperty(name = "kazi.sharding.enabled", havingValue = "true")
public class ShardMetrics {

  private static final Logger log = LoggerFactory.getLogger(ShardMetrics.class);

  private final MeterRegistry registry;
  private final ShardRegistry shardRegistry;
  private final OrgSchemaMappingRepository mappingRepository;

  private final ConcurrentHashMap<String, AtomicLong> tenantCounts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> registeredShards = new ConcurrentHashMap<>();

  public ShardMetrics(
      MeterRegistry registry,
      ShardRegistry shardRegistry,
      OrgSchemaMappingRepository mappingRepository) {
    this.registry = registry;
    this.shardRegistry = shardRegistry;
    this.mappingRepository = mappingRepository;

    registerAllShards();
  }

  private void registerAllShards() {
    for (String shardId : shardRegistry.getActiveShardIds()) {
      registerShardMetrics(shardId);
    }
  }

  private void registerShardMetrics(String shardId) {
    if (registeredShards.containsKey(shardId)) {
      return;
    }

    DataSource ds = shardRegistry.getDataSource(shardId);

    if (ds instanceof HikariDataSource hikari) {
      Gauge.builder(
              "kazi_shard_connection_pool_active",
              hikari,
              h -> {
                HikariPoolMXBean pool = h.getHikariPoolMXBean();
                return pool != null ? pool.getActiveConnections() : 0;
              })
          .tag("shard_id", shardId)
          .description("Active connections in HikariCP pool")
          .register(registry);

      Gauge.builder(
              "kazi_shard_connection_pool_idle",
              hikari,
              h -> {
                HikariPoolMXBean pool = h.getHikariPoolMXBean();
                return pool != null ? pool.getIdleConnections() : 0;
              })
          .tag("shard_id", shardId)
          .description("Idle connections in the pool")
          .register(registry);

      Gauge.builder(
              "kazi_shard_connection_pool_pending",
              hikari,
              h -> {
                HikariPoolMXBean pool = h.getHikariPoolMXBean();
                return pool != null ? pool.getThreadsAwaitingConnection() : 0;
              })
          .tag("shard_id", shardId)
          .description("Threads waiting for a connection")
          .register(registry);
    }

    AtomicLong count = new AtomicLong(0);
    tenantCounts.put(shardId, count);

    Gauge.builder("kazi_shard_tenant_count", count, AtomicLong::doubleValue)
        .tag("shard_id", shardId)
        .description("Number of tenant schemas on this shard")
        .register(registry);

    registeredShards.putIfAbsent(shardId, Boolean.TRUE);
  }

  /** Refreshes tenant count gauges from the database. Called every 60 seconds by the scheduler. */
  @Scheduled(fixedRate = 60_000)
  public void refreshTenantCounts() {
    try {
      // Reset all counts to zero, then rebuild from database
      tenantCounts.values().forEach(count -> count.set(0));

      for (var mapping : mappingRepository.findAll()) {
        String shardId = mapping.getShardId();
        AtomicLong count = tenantCounts.get(shardId);
        if (count != null) {
          count.incrementAndGet();
        } else {
          // Shard not registered yet — register it now
          registerShardMetrics(shardId);
          AtomicLong newCount = tenantCounts.get(shardId);
          if (newCount != null) {
            newCount.incrementAndGet();
          }
        }
      }
    } catch (Exception e) {
      log.debug("Failed to refresh tenant counts: {}", e.getMessage());
    }
  }
}
