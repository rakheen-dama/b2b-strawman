package io.b2mash.b2b.b2bstrawman.demo;

import io.b2mash.b2b.b2bstrawman.billing.BillingMethod;
import io.b2mash.b2b.b2bstrawman.billing.Subscription;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionRepository;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionStatusCache;
import io.b2mash.b2b.b2bstrawman.demo.DemoDtos.DemoProvisionRequest;
import io.b2mash.b2b.b2bstrawman.demo.DemoDtos.DemoProvisionResponse;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DemoProvisionService {

  private static final Logger log = LoggerFactory.getLogger(DemoProvisionService.class);

  private final KeycloakAdminClient keycloakAdminClient;
  private final TenantProvisioningService tenantProvisioningService;
  private final OrganizationRepository organizationRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionStatusCache subscriptionStatusCache;
  private final TransactionTemplate txTemplate;
  private final String baseUrl;

  public DemoProvisionService(
      @Nullable KeycloakAdminClient keycloakAdminClient,
      TenantProvisioningService tenantProvisioningService,
      OrganizationRepository organizationRepository,
      SubscriptionRepository subscriptionRepository,
      SubscriptionStatusCache subscriptionStatusCache,
      TransactionTemplate txTemplate,
      @Value("${app.base-url:http://localhost:3000}") String baseUrl) {
    this.keycloakAdminClient = keycloakAdminClient;
    this.tenantProvisioningService = tenantProvisioningService;
    this.organizationRepository = organizationRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.subscriptionStatusCache = subscriptionStatusCache;
    this.txTemplate = txTemplate;
    this.baseUrl = baseUrl;
  }

  /**
   * Provisions a demo tenant end-to-end: Keycloak org + user, tenant schema, subscription override
   * to ACTIVE/PILOT.
   *
   * <p>This method intentionally does NOT use @Transactional because
   * tenantProvisioningService.provisionTenant() manages its own DB connections with tenant-scoped
   * search_path. An outer transaction would force seeders to reuse the wrong connection.
   */
  public DemoProvisionResponse provisionDemo(DemoProvisionRequest request, String adminUserId) {
    if (keycloakAdminClient == null) {
      throw new InvalidStateException(
          "Keycloak not configured", "Demo provisioning requires Keycloak Admin to be configured");
    }

    String name = request.organizationName().trim();
    String slug = toSlug(name);
    String verticalProfile = request.verticalProfile();
    String adminEmail = request.adminEmail();

    if (slug.isBlank()) {
      throw new InvalidStateException(
          "Invalid organization name",
          "Organization name must contain at least one alphanumeric character");
    }

    log.info(
        "Provisioning demo tenant '{}' (slug: {}, profile: {}) for admin {}",
        name,
        slug,
        verticalProfile,
        adminEmail);

    // Step 1: Find or create Keycloak user
    String userId =
        keycloakAdminClient
            .findUserByEmail(adminEmail)
            .orElseGet(
                () -> {
                  log.info("Creating Keycloak user for {}", adminEmail);
                  return keycloakAdminClient.createUser(
                      adminEmail, "Demo", "Admin", "demo-password-123");
                });

    // Step 2: Create Keycloak organization
    String kcOrgId;
    try {
      kcOrgId = keycloakAdminClient.createOrganization(name, slug, userId);
    } catch (Exception e) {
      log.error("Failed to create Keycloak org '{}': {}", name, e.getMessage());
      throw new InvalidStateException(
          "Organization creation failed", "Failed to create Keycloak organization: " + slug);
    }

    if (kcOrgId == null) {
      var org = keycloakAdminClient.findOrganizationByAlias(slug);
      kcOrgId = org != null ? (String) org.get("id") : null;
    }

    if (kcOrgId == null) {
      throw new InvalidStateException(
          "Organization creation failed",
          "Keycloak organization creation succeeded but no ID was returned");
    }

    // Step 3: Add user to org as owner
    keycloakAdminClient.addMember(kcOrgId, userId);
    keycloakAdminClient.updateMemberRole(kcOrgId, userId, "owner");

    // Step 4: Provision tenant schema (NO outer transaction — seeders manage their own)
    tenantProvisioningService.provisionTenant(slug, name, verticalProfile);

    // Step 5: Override subscription to ACTIVE/PILOT (short transaction)
    var org =
        organizationRepository
            .findByExternalOrgId(slug)
            .orElseThrow(
                () ->
                    new InvalidStateException(
                        "Organization not found",
                        "Organization record not found after provisioning for slug: " + slug));

    String adminNote =
        "Demo tenant provisioned by admin %s. Profile: %s".formatted(adminUserId, verticalProfile);

    txTemplate.executeWithoutResult(
        tx -> {
          Subscription sub =
              subscriptionRepository
                  .findByOrganizationId(org.getId())
                  .orElseThrow(
                      () ->
                          new InvalidStateException(
                              "Subscription not found",
                              "Subscription not found after provisioning for org: " + org.getId()));
          sub.adminTransitionTo(Subscription.SubscriptionStatus.ACTIVE);
          sub.setBillingMethod(BillingMethod.PILOT);
          sub.setAdminNote(adminNote);
          subscriptionRepository.save(sub);
          subscriptionStatusCache.evict(org.getId());
        });

    // Step 6: Demo data seeding (DemoDataSeeder does not exist yet — Epic 430)
    boolean demoDataSeeded = false;

    // Step 7: Audit event — platform admin context has no tenant, so use structured logging
    var auditDetails =
        Map.of(
            "event_type", "demo_tenant.provisioned",
            "entity_type", "organization",
            "entity_id", org.getId().toString(),
            "org_name", name,
            "org_slug", slug,
            "vertical_profile", verticalProfile,
            "admin_email", adminEmail,
            "admin_user_id", adminUserId,
            "demo_data_seeded", String.valueOf(demoDataSeeded));
    log.info("AUDIT: Demo tenant provisioned for org {}: {}", org.getId(), auditDetails);

    String loginUrl = baseUrl + "/org/" + slug;

    return new DemoProvisionResponse(
        org.getId(), slug, name, verticalProfile, loginUrl, demoDataSeeded, adminNote);
  }

  static String toSlug(String name) {
    return name.toLowerCase()
        .trim()
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("[\\s]+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }
}
