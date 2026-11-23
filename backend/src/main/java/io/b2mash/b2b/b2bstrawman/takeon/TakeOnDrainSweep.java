package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ShardRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute, enqueues {@code take_on_drain} for each tenant that has take-on work (Phase 93
 * 655A, §17.3.3 step 4) — not a fan-out to every tenant, which would write a job row per tenant
 * every run. Catches a commit whose enqueue was deduplicated against a finishing drain, a drain
 * that stopped at its two-minute budget (it cannot re-enqueue itself — see {@link
 * TakeOnDrainJobHandler}), and one that died with its pod: a stranded batch waits at most a minute.
 * The queue's dedup makes a tenant with a drain already queued a no-op. Cheap when idle: per shard
 * and chunk of 200 schemas, one catalog read and one {@code EXISTS} statement.
 *
 * <p>Reads the tenants from {@code public.org_schema_mapping} and asks, per chunk, one native
 * statement built only from mapping schema names — each validated against the codebase's schema
 * pattern ({@code ^tenant_[0-9a-f]{12}$}, C-11) and quoted; never request input. A schema without a
 * {@code take_on_batches} table (a tenant whose boot-time migration failed) is left out first, and
 * if the chunk's statement still fails each schema is asked on its own, so one broken schema never
 * hides the others' work. 657D adds the report-file clause.
 */
@Component
public class TakeOnDrainSweep {

  static final Pattern SCHEMA_NAME = Pattern.compile("^tenant_[0-9a-f]{12}$");
  static final int CHUNK = 200;

  private static final Logger log = LoggerFactory.getLogger(TakeOnDrainSweep.class);
  private static final String PRIMARY_SHARD = "primary";

  private final OrgSchemaMappingRepository mappings;
  private final JobEnqueuer jobEnqueuer;
  private final DataSource primaryDataSource;
  private final ShardRegistry shardRegistry;

  public TakeOnDrainSweep(
      OrgSchemaMappingRepository mappings,
      JobEnqueuer jobEnqueuer,
      DataSource primaryDataSource,
      ObjectProvider<ShardRegistry> shardRegistryProvider) {
    this.mappings = mappings;
    this.jobEnqueuer = jobEnqueuer;
    this.primaryDataSource = primaryDataSource;
    this.shardRegistry = shardRegistryProvider.getIfAvailable();
  }

  @SchedulerLock(name = "take_on_drain_sweep", lockAtLeastFor = "30s")
  @Scheduled(cron = "0 * * * * *", zone = KaziTime.ZONE_ID)
  public void sweep() {
    int enqueued = 0;
    for (var mapping : tenantsWithWork(mappings.findAll())) {
      try {
        jobEnqueuer.enqueue(
            TakeOnDrainJobHandler.JOB_TYPE,
            mapping.getSchemaName(),
            mapping.getExternalOrgId(),
            mapping.getShardId(),
            null);
        enqueued++;
      } catch (RuntimeException e) {
        log.warn(
            "Take-on drain sweep: enqueue failed for {} ({})",
            mapping.getSchemaName(),
            e.getClass().getSimpleName());
      }
    }
    if (enqueued > 0) {
      log.info("Take-on drain sweep: {} tenants with take-on work enqueued", enqueued);
    }
  }

  /**
   * The mappings whose schema has a {@code COMMITTING} take-on batch. Schema names that do not
   * match the tenant pattern are skipped. Public for tests (scheduling is off in the test profile).
   */
  public List<OrgSchemaMapping> tenantsWithWork(List<OrgSchemaMapping> candidates) {
    Map<String, List<OrgSchemaMapping>> byShard = new LinkedHashMap<>();
    for (var mapping : candidates) {
      if (mapping.getSchemaName() != null
          && SCHEMA_NAME.matcher(mapping.getSchemaName()).matches()) {
        byShard
            .computeIfAbsent(
                mapping.getShardId() == null ? PRIMARY_SHARD : mapping.getShardId(),
                k -> new ArrayList<>())
            .add(mapping);
      }
    }
    var hits = new ArrayList<OrgSchemaMapping>();
    for (var entry : byShard.entrySet()) {
      var jdbc = new JdbcTemplate(dataSourceFor(entry.getKey()));
      var shardMappings = entry.getValue();
      for (int from = 0; from < shardMappings.size(); from += CHUNK) {
        var chunk = shardMappings.subList(from, Math.min(from + CHUNK, shardMappings.size()));
        try {
          hits.addAll(withWork(jdbc, withTakeOnTable(jdbc, chunk)));
        } catch (RuntimeException e) {
          // the shard itself is unreachable: the next shard still runs
          log.warn(
              "Take-on drain sweep: a chunk of {} schemas on shard {} failed ({})",
              chunk.size(),
              entry.getKey(),
              e.getClass().getSimpleName());
        }
      }
    }
    return hits;
  }

  /** The chunk's schemas that have a {@code take_on_batches} table (one catalog read). */
  private static List<OrgSchemaMapping> withTakeOnTable(
      JdbcTemplate jdbc, List<OrgSchemaMapping> chunk) {
    Set<String> present = Set.copyOf(jdbc.queryForList(tablesSql(chunk), String.class));
    return chunk.stream().filter(m -> present.contains(m.getSchemaName())).toList();
  }

  /** One statement for the chunk; if it fails, each schema on its own (isolates a bad schema). */
  private static List<OrgSchemaMapping> withWork(JdbcTemplate jdbc, List<OrgSchemaMapping> chunk) {
    if (chunk.isEmpty()) {
      return List.of();
    }
    try {
      Set<String> withWork = Set.copyOf(jdbc.queryForList(sql(chunk), String.class));
      return chunk.stream().filter(m -> withWork.contains(m.getSchemaName())).toList();
    } catch (RuntimeException chunkFailure) {
      var hits = new ArrayList<OrgSchemaMapping>();
      for (var mapping : chunk) {
        try {
          if (!jdbc.queryForList(sql(List.of(mapping)), String.class).isEmpty()) {
            hits.add(mapping);
          }
        } catch (RuntimeException e) {
          log.warn(
              "Take-on drain sweep: schema {} could not be read ({})",
              mapping.getSchemaName(),
              e.getClass().getSimpleName());
        }
      }
      return hits;
    }
  }

  private DataSource dataSourceFor(String shardId) {
    return shardRegistry == null || PRIMARY_SHARD.equals(shardId)
        ? primaryDataSource
        : shardRegistry.getDataSource(shardId);
  }

  /** The chunk's schemas that hold a {@code take_on_batches} table, from the catalog. */
  static String tablesSql(List<OrgSchemaMapping> chunk) {
    return "SELECT n.nspname FROM pg_catalog.pg_class c"
        + " JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace"
        + " WHERE c.relname = 'take_on_batches' AND c.relkind IN ('r', 'p') AND n.nspname IN ("
        + chunk.stream().map(m -> "'" + m.getSchemaName() + "'").collect(Collectors.joining(", "))
        + ")";
  }

  /** {@code SELECT 'tenant_x' WHERE EXISTS (… "tenant_x".take_on_batches …) UNION ALL …}. */
  static String sql(List<OrgSchemaMapping> chunk) {
    return chunk.stream()
        .map(OrgSchemaMapping::getSchemaName)
        .map(
            schema ->
                "SELECT '%1$s' AS schema_name WHERE EXISTS (SELECT 1 FROM \"%1$s\".take_on_batches"
                        .formatted(schema)
                    + " WHERE status = 'COMMITTING')")
        .collect(Collectors.joining(" UNION ALL "));
  }
}
