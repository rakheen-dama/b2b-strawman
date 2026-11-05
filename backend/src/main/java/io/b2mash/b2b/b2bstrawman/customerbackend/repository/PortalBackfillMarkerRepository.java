package io.b2mash.b2b.b2bstrawman.customerbackend.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Global per-(backfill, tenant schema) markers for the portal read-model backfill runners (Phase 89
 * 633A, global V3 {@code portal.portal_backfill_markers}, rulings §5). On the portal datasource; no
 * tenant discriminator by design (keyed by schema name) — justified in {@code
 * PortalReadModelOrgScopingGuardTest}. Each statement autocommits.
 */
@Repository
public class PortalBackfillMarkerRepository {

  private final JdbcClient jdbc;

  public PortalBackfillMarkerRepository(@Qualifier("portalJdbcClient") JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public boolean hasMarker(String backfillName, String tenantSchema) {
    return jdbc.sql(
            """
            SELECT EXISTS (SELECT 1 FROM portal.portal_backfill_markers
                           WHERE backfill_name = ? AND tenant_schema = ?)
            """)
        .params(backfillName, tenantSchema)
        .query(Boolean.class)
        .single();
  }

  public void insertMarker(String backfillName, String tenantSchema, int rowCount) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_backfill_markers
                (backfill_name, tenant_schema, backfilled_at, row_count)
            VALUES (?, ?, now(), ?)
            ON CONFLICT (backfill_name, tenant_schema) DO NOTHING
            """)
        .params(backfillName, tenantSchema, rowCount)
        .update();
  }

  /**
   * B3: schema names are deterministic, so provisioning (before {@code createSchema}) removes the
   * schema's markers — a stale marker would otherwise skip the backfill on a re-provisioned tenant.
   * Demo cleanup also calls it after the schema drop, best-effort (a failure there is only logged;
   * the provisioning delete is the guarantee). Returns the number of markers deleted.
   */
  public int deleteForSchema(String tenantSchema) {
    return jdbc.sql("DELETE FROM portal.portal_backfill_markers WHERE tenant_schema = ?")
        .params(tenantSchema)
        .update();
  }
}
