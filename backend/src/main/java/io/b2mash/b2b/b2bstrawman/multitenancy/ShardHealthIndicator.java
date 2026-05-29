package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for shard connectivity. Validates each active shard by executing {@code SELECT
 * 1} and reports UP only if all shards are reachable.
 */
@Component
@ConditionalOnProperty(name = "kazi.sharding.enabled", havingValue = "true")
public class ShardHealthIndicator implements HealthIndicator {

  private final ShardRegistry shardRegistry;

  public ShardHealthIndicator(ShardRegistry shardRegistry) {
    this.shardRegistry = shardRegistry;
  }

  @Override
  public Health health() {
    var builder = Health.up();
    boolean allHealthy = true;

    for (String shardId : shardRegistry.getActiveShardIds()) {
      try {
        DataSource ds = shardRegistry.getDataSource(shardId);
        try (Connection conn = ds.getConnection();
            Statement stmt = conn.createStatement()) {
          stmt.execute("SELECT 1");
        }
        builder.withDetail("shard_" + shardId, "UP");
      } catch (Exception e) {
        builder.withDetail("shard_" + shardId, "DOWN: " + e.getMessage());
        allHealthy = false;
      }
    }

    builder.withDetail("activeShards", shardRegistry.getActiveShardIds().size());

    if (!allHealthy) {
      return Health.down()
          .withDetail("reason", "one or more shards unreachable")
          .withDetail("activeShards", shardRegistry.getActiveShardIds().size())
          .build();
    }

    return builder.build();
  }
}
