package io.b2mash.b2b.b2bstrawman.portal.directory;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps {@code portal.portal_contact_directory} in step with each tenant's ACTIVE portal contacts
 * (Phase 88 U11, ADR-351, §13.3.9). Tenant-side writers call {@link #reconcileAfterCommit} so a
 * rolled-back contact write never leaves a row; the backfill runner and {@code
 * /internal/portal/resync} call {@link #reconcileOrg}.
 *
 * <p>Presence rule: a row {@code (lower(trim(email)), org_id)} exists iff the tenant has at least
 * one ACTIVE portal contact with that normalised email. Never logs the email (PII).
 *
 * <p>Ordering invariant: every read-then-write runs in one short portal transaction that first
 * takes the org's advisory lock ({@link PortalContactDirectoryRepository#lockOrg}), THEN reads the
 * tenant's contacts, THEN writes. Concurrent reconciles for one org therefore serialise, and the
 * last to take the lock reads the latest committed contacts — an archive of contact X racing a
 * create of contact Y (same email) cannot delete Y's row after it is written, and {@link
 * #reconcileOrg} cannot drop a row added while it scans. Always lock first, then read the tenant.
 */
@Service
public class PortalContactDirectoryService {

  private static final Logger log = LoggerFactory.getLogger(PortalContactDirectoryService.class);

  /** One bounded retry for an after-commit reconcile before it is logged at ERROR. */
  static final int RECONCILE_ATTEMPTS = 2;

  private final PortalContactDirectoryRepository directoryRepository;
  private final PortalContactRepository portalContactRepository;
  private final OrganizationRepository organizationRepository;
  // REQUIRES_NEW: tenant reads run safely after the caller's transaction has committed.
  private final TransactionTemplate tenantReadTx;
  private final TransactionTemplate portalTx;

  public PortalContactDirectoryService(
      PortalContactDirectoryRepository directoryRepository,
      PortalContactRepository portalContactRepository,
      OrganizationRepository organizationRepository,
      PlatformTransactionManager transactionManager,
      @Qualifier("portalTransactionManager") PlatformTransactionManager portalTxManager) {
    this.directoryRepository = directoryRepository;
    this.portalContactRepository = portalContactRepository;
    this.organizationRepository = organizationRepository;
    this.tenantReadTx = new TransactionTemplate(transactionManager);
    this.tenantReadTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.tenantReadTx.setReadOnly(true);
    this.portalTx = new TransactionTemplate(portalTxManager);
  }

  /** {@code lower(trim(email))} — the directory key (§13.2.9). */
  public static String normalise(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Tenant side (tenant scope bound): reconcile (email, org) after the current transaction commits;
   * nothing happens if it rolls back. Without an active transaction, reconciles immediately.
   * Failures are logged, never thrown — the contact write has already committed.
   */
  public void reconcileAfterCommit(String email, String orgId) {
    if (email == null || email.isBlank() || orgId == null) {
      return;
    }
    String emailLower = normalise(email);
    String tenantSchema = RequestScopes.requireTenantId();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              // The ambient tenant ScopedValue binding is still in effect here; do not re-bind.
              reconcileQuietly(emailLower, orgId, tenantSchema);
            }
          });
    } else {
      reconcileQuietly(emailLower, orgId, tenantSchema);
    }
  }

  /**
   * §13.3.9: recompute the (email, org) presence row from the tenant's ACTIVE contacts. Idempotent.
   * Requires the tenant scope to be bound (the ambient after-commit binding, or a caller's {@code
   * callForTenantOnShard}). Holds the org's directory lock across the read and the write.
   */
  public void reconcile(String emailLower, String orgId, String tenantSchema, String orgName) {
    portalTx.executeWithoutResult(
        s -> {
          directoryRepository.lockOrg(orgId);
          boolean present =
              Boolean.TRUE.equals(
                  tenantReadTx.execute(
                      t -> portalContactRepository.existsActiveByEmailLower(emailLower)));
          if (present) {
            directoryRepository.upsert(emailLower, orgId, tenantSchema, orgName);
          } else {
            directoryRepository.deleteByEmailLowerAndOrgId(emailLower, orgId);
          }
        });
  }

  /**
   * Rebuilds one org's rows from its tenant (backfill runner + {@code /internal/portal/resync}):
   * upserts every ACTIVE email and deletes the org's stale rows. Binds the tenant itself. Returns
   * the number of rows present for the org afterwards. Holds the org's directory lock across the
   * tenant scan and the writes.
   */
  public int reconcileOrg(String orgId, String tenantSchema, @Nullable String shardId) {
    String orgName = resolveOrgName(orgId);
    Integer count =
        portalTx.execute(
            s -> {
              directoryRepository.lockOrg(orgId);
              List<String> emails =
                  RequestScopes.callForTenantOnShard(
                      tenantSchema,
                      orgId,
                      shardId,
                      () ->
                          tenantReadTx.execute(
                              t -> portalContactRepository.findDistinctActiveEmailsLower()));
              List<String> active = emails != null ? emails : List.of();
              directoryRepository.deleteByOrgIdNotIn(orgId, active);
              for (String emailLower : active) {
                directoryRepository.upsert(emailLower, orgId, tenantSchema, orgName);
              }
              return active.size();
            });
    return count != null ? count : 0;
  }

  private void reconcileQuietly(String emailLower, String orgId, String tenantSchema) {
    for (int attempt = 1; attempt <= RECONCILE_ATTEMPTS; attempt++) {
      try {
        String orgName = tenantReadTx.execute(s -> resolveOrgName(orgId));
        reconcile(emailLower, orgId, tenantSchema, orgName);
        return;
      } catch (RuntimeException e) {
        if (attempt < RECONCILE_ATTEMPTS) {
          log.warn(
              "Portal contact directory reconcile failed for org {} (tenant {}), retrying",
              orgId,
              tenantSchema,
              e);
        } else {
          // The contact write already committed — never fail it. A stale row (e.g. a real email
          // left after POPIA anonymization) needs /internal/portal/resync/{orgId} to heal.
          log.error(
              "Portal contact directory reconcile failed for org {} (tenant {}) after {} attempts"
                  + " — directory may be stale until /internal/portal/resync/{}",
              orgId,
              tenantSchema,
              RECONCILE_ATTEMPTS,
              orgId,
              e);
        }
      }
    }
  }

  private String resolveOrgName(String orgId) {
    return organizationRepository
        .findByExternalOrgId(orgId)
        .map(Organization::getName)
        .orElse(orgId); // org_name is NOT NULL
  }
}
