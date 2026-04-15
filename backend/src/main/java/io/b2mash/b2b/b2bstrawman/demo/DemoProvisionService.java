package io.b2mash.b2b.b2bstrawman.demo;

import io.b2mash.b2b.b2bstrawman.billing.BillingMethod;
import io.b2mash.b2b.b2bstrawman.billing.Subscription;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionRepository;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionStatusCache;
import io.b2mash.b2b.b2bstrawman.demo.DemoDtos.DemoProvisionRequest;
import io.b2mash.b2b.b2bstrawman.demo.DemoDtos.DemoProvisionResponse;
import io.b2mash.b2b.b2bstrawman.demo.DemoDtos.DemoReseedResponse;
import io.b2mash.b2b.b2bstrawman.demo.seed.DemoDataSeeder;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
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
  private final DemoDataSeeder demoDataSeeder;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final TenantTransactionHelper tenantTransactionHelper;
  private final VerticalProfileRegistry verticalProfileRegistry;
  private final DemoWelcomeEmailService demoWelcomeEmailService;
  private final JdbcTemplate jdbcTemplate;
  private final String baseUrl;

  public DemoProvisionService(
      @Nullable KeycloakAdminClient keycloakAdminClient,
      TenantProvisioningService tenantProvisioningService,
      OrganizationRepository organizationRepository,
      SubscriptionRepository subscriptionRepository,
      SubscriptionStatusCache subscriptionStatusCache,
      TransactionTemplate txTemplate,
      DemoDataSeeder demoDataSeeder,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      TenantTransactionHelper tenantTransactionHelper,
      VerticalProfileRegistry verticalProfileRegistry,
      DemoWelcomeEmailService demoWelcomeEmailService,
      JdbcTemplate jdbcTemplate,
      @Value("${app.base-url:http://localhost:3000}") String baseUrl) {
    this.keycloakAdminClient = keycloakAdminClient;
    this.tenantProvisioningService = tenantProvisioningService;
    this.organizationRepository = organizationRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.subscriptionStatusCache = subscriptionStatusCache;
    this.txTemplate = txTemplate;
    this.demoDataSeeder = demoDataSeeder;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.verticalProfileRegistry = verticalProfileRegistry;
    this.demoWelcomeEmailService = demoWelcomeEmailService;
    this.jdbcTemplate = jdbcTemplate;
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

    // Validate vertical profile exists in registry
    if (!verticalProfileRegistry.exists(verticalProfile)) {
      throw new InvalidStateException(
          "Unknown vertical profile",
          "Vertical profile '%s' is not registered. Available profiles: %s"
              .formatted(
                  verticalProfile,
                  verticalProfileRegistry.getAllProfiles().stream()
                      .map(p -> p.profileId())
                      .toList()));
    }

    log.info(
        "Provisioning demo tenant '{}' (slug: {}, profile: {}) for admin {}",
        name,
        slug,
        verticalProfile,
        adminEmail);

    // Guard: reject if an org with this slug already exists (prevents corrupting real tenants)
    if (organizationRepository.findByExternalOrgId(slug).isPresent()) {
      throw new ResourceConflictException(
          "Organization already exists",
          "An organization with slug '%s' already exists. Choose a different name."
              .formatted(slug));
    }

    // Step 1: Find or create Keycloak user
    String tempPassword = generateTempPassword();
    String userId =
        keycloakAdminClient
            .findUserByEmail(adminEmail)
            .orElseGet(
                () -> {
                  log.info("Creating Keycloak user for {}", adminEmail);
                  return keycloakAdminClient.createUser(adminEmail, "Demo", "Admin", tempPassword);
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
    var provisioningResult = tenantProvisioningService.provisionTenant(slug, name, verticalProfile);
    if (provisioningResult.alreadyProvisioned()) {
      throw new ResourceConflictException(
          "Tenant already provisioned",
          "Tenant schema for slug '%s' was already provisioned. Cannot override existing tenant."
              .formatted(slug));
    }

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

    // Compute login URL (needed for welcome email and response)
    String loginUrl = baseUrl + "/org/" + slug;

    // Step 5b: Send welcome email (non-fatal — provisioning succeeds even if email fails)
    demoWelcomeEmailService.sendWelcomeEmail(
        adminEmail, name, slug, verticalProfile, loginUrl, tempPassword);

    // Step 6: Demo data seeding (non-fatal — tenant is usable without demo data)
    boolean demoDataSeeded = false;
    if (request.seedDemoData()) {
      try {
        demoDataSeeder.seed(provisioningResult.schemaName(), org.getId(), verticalProfile);
        demoDataSeeded = true;
      } catch (Exception e) {
        log.error(
            "Demo data seeding failed for org {} (schema {}). Tenant is provisioned but has no demo data.",
            org.getId(),
            provisioningResult.schemaName(),
            e);
      }
    }

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

    return new DemoProvisionResponse(
        org.getId(),
        slug,
        name,
        verticalProfile,
        loginUrl,
        demoDataSeeded,
        adminNote,
        tempPassword);
  }

  /**
   * Reseeds demo data for an existing tenant. Clears all transactional data and re-runs the
   * profile-specific seeder. Only PILOT and COMPLIMENTARY billing methods are eligible.
   */
  public DemoReseedResponse reseed(UUID orgId, String adminUserId) {
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
          "Reseed not allowed",
          "Reseed is only allowed for PILOT or COMPLIMENTARY tenants. Current billing method: "
              + sub.getBillingMethod());
    }

    var mapping =
        orgSchemaMappingRepository
            .findByExternalOrgId(org.getExternalOrgId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Schema mapping not found",
                        "No schema mapping found for org: " + org.getExternalOrgId()));

    String schemaName = mapping.getSchemaName();
    String verticalProfile = parseVerticalProfile(sub.getAdminNote());

    log.info(
        "Reseeding demo tenant '{}' (schema: {}, profile: {})",
        org.getName(),
        schemaName,
        verticalProfile);

    tenantTransactionHelper.executeInTenantTransaction(
        schemaName,
        orgId.toString(),
        t -> {
          truncateTransactionalTables(schemaName);
          log.info("Truncated transactional tables for schema {}", schemaName);
        });

    demoDataSeeder.seed(schemaName, orgId, verticalProfile);

    log.info(
        "AUDIT: Demo tenant reseeded for org {}: schema={}, profile={}, admin={}",
        orgId,
        schemaName,
        verticalProfile,
        adminUserId);

    return new DemoReseedResponse(org.getId(), org.getName(), true, verticalProfile, null);
  }

  private void truncateTransactionalTables(String schemaName) {
    // Defense-in-depth: validate schema name format to prevent SQL injection.
    // Schema names are always "tenant_" + 12 hex chars (generated by provisioning).
    if (!schemaName.matches("^tenant_[0-9a-f]{12}$")) {
      throw new IllegalArgumentException("Invalid schema name format: " + schemaName);
    }
    // NOTE: JdbcTemplate uses a separate connection from EntityManager, so TRUNCATE executes
    // outside the Spring-managed transaction. If seeding fails after truncation, the tenant
    // will be left with empty tables. This is acceptable for demo tenants — a retry of the
    // reseed operation will re-populate the data.
    jdbcTemplate.execute("SET search_path TO " + schemaName);
    jdbcTemplate.execute("TRUNCATE TABLE notifications CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE comments CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE invoice_lines CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE invoices CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE time_entries CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE task_items CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE tasks CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE project_members CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE customer_projects CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE projects CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE customers CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE members CASCADE");
    jdbcTemplate.execute("SET search_path TO public");
  }

  static String parseVerticalProfile(String adminNote) {
    if (adminNote == null) return "generic";
    // Admin note format: "Demo tenant provisioned by admin <userId>. Profile: <profile>"
    int idx = adminNote.indexOf("Profile: ");
    if (idx < 0) return "generic";
    return adminNote.substring(idx + 9).trim().split("[\\s.]")[0].toLowerCase();
  }

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String PASSWORD_CHARS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*";

  static String generateTempPassword() {
    var sb = new StringBuilder(12);
    for (int i = 0; i < 12; i++) {
      sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
    }
    return sb.toString();
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
