package io.b2mash.b2b.b2bstrawman.packs;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates pack install and uninstall operations. Delegates content creation/deletion to
 * type-specific {@link PackInstaller} implementations and handles cross-cutting concerns: audit
 * events, notifications, OrgSettings legacy shim updates, and profile affinity enforcement.
 */
@Service
public class PackInstallService {

  private static final Logger log = LoggerFactory.getLogger(PackInstallService.class);

  private final PackCatalogService packCatalogService;
  private final PackInstallRepository packInstallRepository;
  private final Map<PackType, PackInstaller> installersByType;
  private final AuditService auditService;
  private final NotificationService notificationService;
  private final OrgSettingsService orgSettingsService;
  private final TransactionTemplate transactionTemplate;

  public PackInstallService(
      PackCatalogService packCatalogService,
      PackInstallRepository packInstallRepository,
      AuditService auditService,
      NotificationService notificationService,
      OrgSettingsService orgSettingsService,
      TransactionTemplate transactionTemplate) {
    this.packCatalogService = packCatalogService;
    this.packInstallRepository = packInstallRepository;
    this.installersByType = packCatalogService.getInstallersByType();
    this.auditService = auditService;
    this.notificationService = notificationService;
    this.orgSettingsService = orgSettingsService;
    this.transactionTemplate = transactionTemplate;
  }

  /**
   * Installs a pack for the current tenant (HTTP request path). Reads tenantId from {@link
   * RequestScopes#TENANT_ID} and enforces profile affinity.
   *
   * @param packId the unique pack identifier
   * @param memberId the ID of the member performing the install
   * @return the PackInstall tracking row (new or existing if already installed)
   */
  @Transactional
  public PackInstall install(String packId, String memberId) {
    String tenantId = RequestScopes.requireTenantId();

    // 1. Resolve catalog entry
    PackCatalogEntry catalogEntry = packCatalogService.findCatalogEntry(packId);
    if (catalogEntry == null) {
      throw new ResourceNotFoundException("Pack", packId);
    }

    // 2. Profile affinity check
    enforceProfileAffinity(catalogEntry);

    // 3. Idempotency check
    var existing = packInstallRepository.findByPackId(packId);
    if (existing.isPresent()) {
      log.info("Pack {} already installed, returning existing install", packId);
      return existing.get();
    }

    // 4. Resolve installer
    PackInstaller installer = resolveInstaller(catalogEntry.type());

    // 5. Delegate to installer
    installer.install(packId, tenantId, memberId);

    // 6. Retrieve the created PackInstall
    PackInstall install =
        packInstallRepository
            .findByPackId(packId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "PackInstall not found after installation of " + packId));

    // 7. Update legacy OrgSettings shim
    updateOrgSettingsOnInstall(catalogEntry);

    // 8. Emit audit event
    emitInstallAuditEvent(install, memberId);

    // 9. Emit notification
    if (memberId != null) {
      emitInstallNotification(install, UUID.fromString(memberId));
    }

    return install;
  }

  /**
   * Installs a pack for an explicit tenant (system/provisioning path). No profile affinity check.
   * No notification (no member to notify).
   *
   * <p>Binds the tenant scope via {@link ScopedValue} so that all JPA operations (repository
   * queries, OrgSettings mutations) resolve to the correct tenant schema, even when called from a
   * background/provisioning thread with no HTTP request context. Uses {@link TransactionTemplate}
   * for programmatic transaction management to avoid Spring proxy self-invocation issues.
   *
   * @param packId the unique pack identifier
   * @param tenantId the tenant schema name
   */
  public void internalInstall(String packId, String tenantId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // 1. Resolve catalog entry
                      PackCatalogEntry catalogEntry = packCatalogService.findCatalogEntry(packId);
                      if (catalogEntry == null) {
                        throw new ResourceNotFoundException("Pack", packId);
                      }

                      // 2. No profile affinity check for internal installs

                      // 3. Idempotency check
                      var existing = packInstallRepository.findByPackId(packId);
                      if (existing.isPresent()) {
                        log.info("Pack {} already installed (internal path), skipping", packId);
                        return;
                      }

                      // 4. Resolve installer
                      PackInstaller installer = resolveInstaller(catalogEntry.type());

                      // 5. Delegate to installer (memberId is null for internal installs)
                      installer.install(packId, tenantId, null);

                      // 6. Retrieve the created PackInstall
                      PackInstall install =
                          packInstallRepository
                              .findByPackId(packId)
                              .orElseThrow(
                                  () ->
                                      new IllegalStateException(
                                          "PackInstall not found after internal installation of "
                                              + packId));

                      // 7. Update legacy OrgSettings shim
                      updateOrgSettingsOnInstall(catalogEntry);

                      // 8. Emit audit event (system actor, no member)
                      emitInstallAuditEvent(install, null);

                      // 9. No notification for internal installs (no member to notify)

                      log.info(
                          "Internal install of pack {} completed for tenant {}", packId, tenantId);
                    }));
  }

  /**
   * Uninstalls a pack for the current tenant. Checks uninstall gates first.
   *
   * @param packId the unique pack identifier
   * @param memberId the ID of the member performing the uninstall
   */
  @Transactional
  public void uninstall(String packId, String memberId) {
    String tenantId = RequestScopes.requireTenantId();

    // 1. Find existing install
    PackInstall install =
        packInstallRepository
            .findByPackId(packId)
            .orElseThrow(() -> new ResourceNotFoundException("PackInstall", packId));

    // 2. Resolve installer
    PackInstaller installer = resolveInstaller(install.getPackType());

    // 3+4. Delegate to installer — it enforces uninstall gates internally and throws
    //       ResourceConflictException if blocked. We catch the exception to emit audit.
    try {
      installer.uninstall(packId, tenantId, memberId);
    } catch (ResourceConflictException e) {
      emitUninstallBlockedAuditEvent(install, e.getBody().getDetail());
      throw e;
    }

    // 5. Remove from legacy OrgSettings shim (use install entity data, not catalog lookup,
    //    so cleanup works even if the pack definition has been removed from classpath)
    updateOrgSettingsOnUninstall(install.getPackType(), install.getPackId());

    // 6. Emit audit event
    emitUninstallAuditEvent(install);

    // 7. Emit notification
    if (memberId != null) {
      emitUninstallNotification(install, UUID.fromString(memberId));
    }
  }

  /**
   * Checks whether a pack can be safely uninstalled.
   *
   * @param packId the unique pack identifier
   * @return uninstall check result
   */
  @Transactional(readOnly = true)
  public UninstallCheck checkUninstallable(String packId) {
    String tenantId = RequestScopes.requireTenantId();

    PackInstall install =
        packInstallRepository
            .findByPackId(packId)
            .orElseThrow(() -> new ResourceNotFoundException("PackInstall", packId));

    PackInstaller installer = resolveInstaller(install.getPackType());
    return installer.checkUninstallable(packId, tenantId);
  }

  // --- Private helpers ---

  private void enforceProfileAffinity(PackCatalogEntry entry) {
    if (entry.verticalProfile() == null) {
      return; // Universal pack, always allowed
    }
    String tenantProfile = orgSettingsService.getOrCreateForCurrentTenant().getVerticalProfile();
    if (!entry.verticalProfile().equals(tenantProfile)) {
      throw new InvalidStateException(
          "Profile mismatch",
          "Pack '"
              + entry.packId()
              + "' requires profile '"
              + entry.verticalProfile()
              + "' but tenant has profile '"
              + (tenantProfile != null ? tenantProfile : "none")
              + "'");
    }
  }

  private PackInstaller resolveInstaller(PackType type) {
    PackInstaller installer = installersByType.get(type);
    if (installer == null) {
      throw new IllegalStateException("No PackInstaller registered for type " + type);
    }
    return installer;
  }

  private void updateOrgSettingsOnInstall(PackCatalogEntry entry) {
    OrgSettings settings = orgSettingsService.getOrCreateForCurrentTenant();
    int version = parseVersion(entry.version());

    switch (entry.type()) {
      case DOCUMENT_TEMPLATE -> settings.recordTemplatePackApplication(entry.packId(), version);
      case AUTOMATION_TEMPLATE -> settings.recordAutomationPackApplication(entry.packId(), version);
    }
  }

  private void updateOrgSettingsOnUninstall(PackType type, String packId) {
    OrgSettings settings = orgSettingsService.getOrCreateForCurrentTenant();

    switch (type) {
      case DOCUMENT_TEMPLATE -> settings.removeTemplatePackEntry(packId);
      case AUTOMATION_TEMPLATE -> settings.removeAutomationPackEntry(packId);
    }
  }

  private int parseVersion(String version) {
    try {
      return Integer.parseInt(version);
    } catch (NumberFormatException e) {
      return 1;
    }
  }

  private void emitInstallAuditEvent(PackInstall install, String memberId) {
    var builder =
        AuditEventBuilder.builder()
            .eventType("pack.installed")
            .entityType("pack_install")
            .entityId(install.getId())
            .details(
                Map.of(
                    "packId", install.getPackId(),
                    "packType", install.getPackType().name(),
                    "itemCount", install.getItemCount()));

    if (memberId != null) {
      builder.actorId(UUID.fromString(memberId));
    } else {
      builder.actorType("SYSTEM").source("INTERNAL");
    }

    auditService.log(builder.build());
  }

  private void emitUninstallAuditEvent(PackInstall install) {
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("pack.uninstalled")
            .entityType("pack_install")
            .entityId(install.getId())
            .details(
                Map.of(
                    "packId", install.getPackId(),
                    "packType", install.getPackType().name()))
            .build());
  }

  private void emitUninstallBlockedAuditEvent(PackInstall install, String blockingReason) {
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("pack.uninstall_blocked")
            .entityType("pack_install")
            .entityId(install.getId())
            .details(Map.of("packId", install.getPackId(), "blockingReason", blockingReason))
            .build());
  }

  private void emitInstallNotification(PackInstall install, UUID memberId) {
    notificationService.createNotification(
        memberId,
        "PACK_INSTALLED",
        "Pack installed",
        "Content pack '" + install.getPackName() + "' has been installed successfully.",
        "pack_install",
        install.getId(),
        null);
  }

  private void emitUninstallNotification(PackInstall install, UUID memberId) {
    notificationService.createNotification(
        memberId,
        "PACK_UNINSTALLED",
        "Pack uninstalled",
        "Content pack '" + install.getPackName() + "' has been uninstalled.",
        "pack_install",
        install.getId(),
        null);
  }
}
