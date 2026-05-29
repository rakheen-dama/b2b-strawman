package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test handler that creates a dedicated marker table and writes a row into it via the
 * Hibernate-managed connection. Because the write goes through the EntityManager (not a raw
 * DataSource), it is routed by Hibernate's shard-aware connection provider using the {@code
 * SHARD_ID} bound by {@link JobWorker}, and the unqualified DDL/DML lands in the schema on the
 * connection's {@code search_path}. This lets {@code EndToEndMultiShardTest} prove that a job for a
 * secondary-shard tenant executes against the correct physical database (regression guard for D1/D2
 * — see kazi-infra-review-scheduling-sharding.md).
 *
 * <p>A self-created marker table is used (rather than a domain table) so the assertion is immune to
 * domain-schema drift and needs no pre-seeded rows. If {@code SHARD_ID} is unbound (the D1 bug),
 * routing falls back to primary where the secondary-shard tenant schema does not exist, so the
 * unqualified {@code CREATE TABLE} fails — which is exactly what this handler surfaces.
 *
 * <p>If {@code SHARD_ID} is not bound (the D1 bug), routing falls back to the primary database,
 * where the secondary-shard tenant schema does not exist — the INSERT then fails with a
 * relation-not-found error and the marker never lands, which is exactly what this handler surfaces.
 */
@Component
@Profile("test")
public class ShardRoutingTestJobHandler implements JobHandler {

  public static final String JOB_TYPE = "shard_routing_test_job";
  public static final String MARKER_TABLE = "shard_routing_marker";

  private final EntityManager entityManager;

  public ShardRoutingTestJobHandler(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public String jobType() {
    return JOB_TYPE;
  }

  @Override
  @Transactional
  public void execute(@Nullable JsonNode payload) {
    entityManager
        .createNativeQuery("CREATE TABLE IF NOT EXISTS " + MARKER_TABLE + " (id integer NOT NULL)")
        .executeUpdate();
    entityManager
        .createNativeQuery("INSERT INTO " + MARKER_TABLE + " (id) VALUES (1)")
        .executeUpdate();
  }
}
