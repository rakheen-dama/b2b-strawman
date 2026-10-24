package io.b2mash.b2b.b2bstrawman.portal.directory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Global email → firm routing table (Phase 88 U11, ADR-351) on the portal datasource. JdbcClient,
 * no JPA on the portal side (ADR-253). Statements are tenant-scoped by {@code org_id} except the
 * email-keyed login lookup and the backfill marker (justified in {@code
 * PortalReadModelOrgScopingGuardTest}).
 */
@Repository
public class PortalContactDirectoryRepository {

  private final JdbcClient jdbc;

  public PortalContactDirectoryRepository(@Qualifier("portalJdbcClient") JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Serialises directory writes for one org until the caller's portal transaction ends ({@code
   * pg_advisory_xact_lock}). Must run inside a {@code portalTransactionManager} transaction.
   */
  public void lockOrg(String orgId) {
    jdbc.sql(
            """
            SELECT pg_advisory_xact_lock(hashtext('portal_contact_directory:' || ?))
            """)
        .param(orgId)
        .query()
        .singleRow();
  }

  public void upsert(String emailLower, String orgId, String tenantSchema, String orgName) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_contact_directory
                (email_lower, org_id, tenant_schema, org_name, updated_at)
            VALUES (?, ?, ?, ?, now())
            ON CONFLICT (email_lower, org_id)
            DO UPDATE SET tenant_schema = EXCLUDED.tenant_schema,
                          org_name = EXCLUDED.org_name,
                          updated_at = now()
            """)
        .params(emailLower, orgId, tenantSchema, orgName)
        .update();
  }

  public void deleteByEmailLowerAndOrgId(String emailLower, String orgId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_contact_directory
            WHERE email_lower = ? AND org_id = ?
            """)
        .params(emailLower, orgId)
        .update();
  }

  /**
   * Deletes the org's rows whose email is not retained; an empty set deletes all the org's rows.
   */
  public int deleteByOrgIdNotIn(String orgId, Collection<String> retainedEmailsLower) {
    if (retainedEmailsLower.isEmpty()) {
      return jdbc.sql(
              """
              DELETE FROM portal.portal_contact_directory
              WHERE org_id = ?
              """)
          .params(orgId)
          .update();
    }
    return jdbc.sql(
            """
            DELETE FROM portal.portal_contact_directory
            WHERE org_id = ? AND NOT (email_lower = ANY (?))
            """)
        .params(orgId, retainedEmailsLower.toArray(new String[0]))
        .update();
  }

  /** Login lookup (consumer: 624B) — every firm that routes this email, by firm name. */
  public List<PortalContactDirectoryEntry> findAll(String emailLower) {
    return jdbc.sql(
            """
            SELECT email_lower, org_id, tenant_schema, org_name, updated_at
            FROM portal.portal_contact_directory
            WHERE email_lower = ?
            ORDER BY org_name, org_id
            """)
        .params(emailLower)
        .query(PortalContactDirectoryEntry.class)
        .list();
  }

  public Optional<PortalContactDirectoryEntry> find(String emailLower, String orgId) {
    return jdbc.sql(
            """
            SELECT email_lower, org_id, tenant_schema, org_name, updated_at
            FROM portal.portal_contact_directory
            WHERE email_lower = ? AND org_id = ?
            """)
        .params(emailLower, orgId)
        .query(PortalContactDirectoryEntry.class)
        .optional();
  }

  public boolean hasBackfillMarker(String tenantSchema) {
    return jdbc.sql(
            """
            SELECT EXISTS (SELECT 1 FROM portal.portal_contact_directory_backfill
                           WHERE tenant_schema = ?)
            """)
        .params(tenantSchema)
        .query(Boolean.class)
        .single();
  }

  public void insertBackfillMarker(String tenantSchema, int rowCount) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_contact_directory_backfill
                (tenant_schema, backfilled_at, row_count)
            VALUES (?, now(), ?)
            ON CONFLICT (tenant_schema) DO NOTHING
            """)
        .params(tenantSchema, rowCount)
        .update();
  }
}
