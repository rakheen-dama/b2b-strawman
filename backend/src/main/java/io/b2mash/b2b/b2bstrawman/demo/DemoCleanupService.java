package io.b2mash.b2b.b2bstrawman.demo;

import io.b2mash.b2b.b2bstrawman.billing.SubscriptionRepository;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionStatusCache;
import io.b2mash.b2b.b2bstrawman.demo.DemoDtos.DemoCleanupResponse;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class DemoCleanupService {

  private static final Logger log = LoggerFactory.getLogger(DemoCleanupService.class);

  private final KeycloakAdminClient keycloakAdminClient;
  private final OrganizationRepository organizationRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionStatusCache subscriptionStatusCache;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final DataSource migrationDataSource;
  private final JdbcTemplate jdbcTemplate;
  private final StorageService storageService;

  public DemoCleanupService(
      @Nullable KeycloakAdminClient keycloakAdminClient,
      OrganizationRepository organizationRepository,
      SubscriptionRepository subscriptionRepository,
      SubscriptionStatusCache subscriptionStatusCache,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      @Qualifier("migrationDataSource") DataSource migrationDataSource,
      JdbcTemplate jdbcTemplate,
      @Nullable StorageService storageService) {
    this.keycloakAdminClient = keycloakAdminClient;
    this.organizationRepository = organizationRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.subscriptionStatusCache = subscriptionStatusCache;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.migrationDataSource = migrationDataSource;
    this.jdbcTemplate = jdbcTemplate;
    this.storageService = storageService;
  }

  /**
   * Multi-step cleanup of a demo tenant. Only PILOT and COMPLIMENTARY billing methods are eligible.
   * Each step is wrapped in try-catch so partial failures do not prevent remaining steps from
   * running.
   */
  public DemoCleanupResponse cleanup(UUID orgId, String confirmName) {
    var org =
        organizationRepository
            .findById(orgId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Organization not found", "No organization found for id: " + orgId));

    var sub =
        subscriptionRepository
            .findByOrganizationId(orgId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Subscription not found",
                        "No subscription found for organization: " + orgId));

    if (!sub.getBillingMethod().isCleanupEligible()) {
      throw new ForbiddenException(
          "Cleanup not allowed",
          "Cleanup is only allowed for PILOT or COMPLIMENTARY tenants. Current: "
              + sub.getBillingMethod());
    }

    if (!org.getName().equals(confirmName)) {
      throw new InvalidStateException(
          "Name mismatch", "Confirmation name does not match organization name");
    }

    String externalOrgId = org.getExternalOrgId();
    List<String> errors = new ArrayList<>();

    // Step 1: AUDIT — log before any destruction
    var auditDetails =
        Map.of(
            "event_type",
            "demo_tenant.deleted",
            "entity_type",
            "organization",
            "entity_id",
            org.getId().toString(),
            "org_name",
            org.getName(),
            "external_org_id",
            externalOrgId,
            "billing_method",
            sub.getBillingMethod().name());
    log.info("AUDIT: Demo tenant deleted for org {}: {}", org.getId(), auditDetails);

    // Step 2: KEYCLOAK — clean up org members and org
    boolean keycloakCleaned = cleanupKeycloak(externalOrgId, errors);

    // Step 3: SCHEMA — drop tenant schema
    boolean schemaCleaned = cleanupSchema(externalOrgId, errors);

    // Step 4: PUBLIC RECORDS — delete from public schema tables
    boolean publicRecordsCleaned = cleanupPublicRecords(orgId, externalOrgId, sub.getId(), errors);

    // Step 5: CACHE — evict subscription status cache
    try {
      subscriptionStatusCache.evict(orgId);
    } catch (Exception e) {
      log.error("Cache eviction failed for org {}: {}", orgId, e.getMessage());
      errors.add("Cache eviction failed: " + e.getMessage());
    }

    // Step 6: S3 — best-effort delete org files
    boolean s3Cleaned = cleanupS3(externalOrgId, errors);

    return new DemoCleanupResponse(
        org.getId(),
        org.getName(),
        keycloakCleaned,
        schemaCleaned,
        publicRecordsCleaned,
        s3Cleaned,
        errors);
  }

  private boolean cleanupKeycloak(String externalOrgId, List<String> errors) {
    if (keycloakAdminClient == null) {
      log.warn("Keycloak not configured — skipping Keycloak cleanup for org {}", externalOrgId);
      errors.add("Keycloak not configured — skipped");
      return false;
    }

    try {
      // Resolve Keycloak org ID from the alias (externalOrgId is used as the alias)
      String kcOrgId = keycloakAdminClient.resolveOrgId(externalOrgId);

      // List members and handle each one
      List<String> memberIds = keycloakAdminClient.listOrgMemberIds(kcOrgId);
      for (String userId : memberIds) {
        try {
          List<Map<String, Object>> userOrgs = keycloakAdminClient.getUserOrganizations(userId);
          if (userOrgs != null && userOrgs.size() > 1) {
            // User belongs to other orgs — just remove from this org
            keycloakAdminClient.removeOrgMember(kcOrgId, userId);
            log.info("Removed user {} from Keycloak org {}", userId, kcOrgId);
          } else {
            // User only belongs to this org — delete the user entirely
            keycloakAdminClient.deleteUser(userId);
            log.info("Deleted Keycloak user {} (only member of org {})", userId, kcOrgId);
          }
        } catch (Exception e) {
          log.error(
              "Failed to clean up Keycloak user {} in org {}: {}", userId, kcOrgId, e.getMessage());
          errors.add("Keycloak user cleanup failed for " + userId + ": " + e.getMessage());
        }
      }

      // Delete the organization itself
      keycloakAdminClient.deleteOrganization(kcOrgId);
      log.info("Deleted Keycloak organization {}", kcOrgId);
      return true;
    } catch (Exception e) {
      log.error("Keycloak cleanup failed for org {}: {}", externalOrgId, e.getMessage());
      errors.add("Keycloak cleanup failed: " + e.getMessage());
      return false;
    }
  }

  private boolean cleanupSchema(String externalOrgId, List<String> errors) {
    try {
      var mapping = orgSchemaMappingRepository.findByExternalOrgId(externalOrgId);
      if (mapping.isEmpty()) {
        log.warn("No schema mapping found for org {} — skipping schema drop", externalOrgId);
        return true; // Nothing to drop
      }

      String schemaName = mapping.get().getSchemaName();
      dropSchema(schemaName);
      log.info("Dropped schema {} for org {}", schemaName, externalOrgId);
      return true;
    } catch (Exception e) {
      log.error("Schema cleanup failed for org {}: {}", externalOrgId, e.getMessage());
      errors.add("Schema cleanup failed: " + e.getMessage());
      return false;
    }
  }

  private void dropSchema(String schemaName) throws SQLException {
    if (!schemaName.matches("^tenant_[0-9a-f]{12}$")) {
      throw new IllegalArgumentException("Invalid schema name: " + schemaName);
    }
    try (var conn = migrationDataSource.getConnection();
        var stmt = conn.createStatement()) {
      // Schema name is validated to match ^tenant_[0-9a-f]{12}$ — safe to concatenate
      stmt.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
    }
  }

  private boolean cleanupPublicRecords(
      UUID orgId, String externalOrgId, UUID subscriptionId, List<String> errors) {
    try {
      // Order matters due to FK constraints: children before parents
      jdbcTemplate.update(
          "DELETE FROM subscription_payments WHERE subscription_id IN "
              + "(SELECT id FROM subscriptions WHERE organization_id = ?)",
          orgId);
      jdbcTemplate.update("DELETE FROM subscriptions WHERE organization_id = ?", orgId);
      jdbcTemplate.update(
          "DELETE FROM org_schema_mapping WHERE external_org_id = ?", externalOrgId);
      jdbcTemplate.update("DELETE FROM organizations WHERE id = ?", orgId);
      log.info("Deleted public records for org {}", orgId);
      return true;
    } catch (Exception e) {
      log.error("Public records cleanup failed for org {}: {}", orgId, e.getMessage());
      errors.add("Public records cleanup failed: " + e.getMessage());
      return false;
    }
  }

  private boolean cleanupS3(String externalOrgId, List<String> errors) {
    if (storageService == null) {
      log.warn("StorageService not configured — skipping S3 cleanup for org {}", externalOrgId);
      return true; // Nothing to clean
    }

    try {
      String prefix = "org/" + externalOrgId + "/";
      List<String> keys = storageService.listKeys(prefix);
      for (String key : keys) {
        storageService.delete(key);
      }
      log.info("Deleted {} S3 objects for org {}", keys.size(), externalOrgId);
      return true;
    } catch (Exception e) {
      log.error("S3 cleanup failed for org {}: {}", externalOrgId, e.getMessage());
      errors.add("S3 cleanup failed: " + e.getMessage());
      return false;
    }
  }
}
