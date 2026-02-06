package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.webhook.WebhookIdempotencyService;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles organization provisioning requests from the webhook handler. In Epic 4 this is a stub
 * that persists org metadata. Full schema creation and Flyway migration added in Epic 5.
 */
@Service
public class ProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(ProvisioningService.class);

  private final OrganizationRepository organizationRepository;
  private final WebhookIdempotencyService idempotencyService;

  public ProvisioningService(
      OrganizationRepository organizationRepository, WebhookIdempotencyService idempotencyService) {
    this.organizationRepository = organizationRepository;
    this.idempotencyService = idempotencyService;
  }

  /**
   * Provision a new organization. Idempotent: returns existing org if already provisioned.
   *
   * @return the provisioning result
   */
  @Transactional
  public ProvisionResult provisionOrganization(
      String clerkOrgId, String orgName, String slug, String svixId) {
    // Check webhook idempotency
    if (svixId != null && idempotencyService.isAlreadyProcessed(svixId)) {
      log.info("Webhook {} already processed, returning existing org for {}", svixId, clerkOrgId);
      Optional<Organization> existing = organizationRepository.findByClerkOrgId(clerkOrgId);
      if (existing.isPresent()) {
        return ProvisionResult.alreadyExists(existing.get());
      }
    }

    // Check if org already exists (idempotent provisioning)
    Optional<Organization> existing = organizationRepository.findByClerkOrgId(clerkOrgId);
    if (existing.isPresent()) {
      log.info("Organization {} already provisioned", clerkOrgId);
      if (svixId != null) {
        idempotencyService.markProcessed(svixId, "organization.created");
      }
      return ProvisionResult.alreadyExists(existing.get());
    }

    // Create new organization record
    Organization org = new Organization(clerkOrgId, orgName, slug);
    org.setProvisioningStatus("PENDING");
    organizationRepository.save(org);
    log.info("Organization {} created with status PENDING", clerkOrgId);

    // TODO (Epic 5): Generate schema name, create schema, run Flyway migrations,
    // insert org_schema_mapping, update status to COMPLETED

    // Mark webhook as processed
    if (svixId != null) {
      idempotencyService.markProcessed(svixId, "organization.created");
    }

    return ProvisionResult.created(org);
  }

  /**
   * Update organization metadata. Compares updated_at to handle out-of-order events.
   *
   * @return true if the update was applied, false if skipped (stale event)
   */
  @Transactional
  public boolean updateOrganization(
      String clerkOrgId, String orgName, String slug, Instant updatedAt, String svixId) {
    if (svixId != null && idempotencyService.isAlreadyProcessed(svixId)) {
      log.info("Webhook {} already processed, skipping update for {}", svixId, clerkOrgId);
      return false;
    }

    Optional<Organization> existing = organizationRepository.findByClerkOrgId(clerkOrgId);
    if (existing.isEmpty()) {
      log.warn("Organization {} not found for update, skipping", clerkOrgId);
      if (svixId != null) {
        idempotencyService.markProcessed(svixId, "organization.updated");
      }
      return false;
    }

    Organization org = existing.get();

    // Discard out-of-order events
    if (updatedAt != null && org.getUpdatedAt().isAfter(updatedAt)) {
      log.info(
          "Stale update for {}: event updatedAt={} < stored updatedAt={}",
          clerkOrgId,
          updatedAt,
          org.getUpdatedAt());
      if (svixId != null) {
        idempotencyService.markProcessed(svixId, "organization.updated");
      }
      return false;
    }

    org.setName(orgName);
    org.setSlug(slug);
    org.setUpdatedAt(updatedAt != null ? updatedAt : Instant.now());
    organizationRepository.save(org);
    log.info("Organization {} updated: name={}, slug={}", clerkOrgId, orgName, slug);

    if (svixId != null) {
      idempotencyService.markProcessed(svixId, "organization.updated");
    }
    return true;
  }

  public record ProvisionResult(Organization organization, boolean created) {
    public static ProvisionResult created(Organization org) {
      return new ProvisionResult(org, true);
    }

    public static ProvisionResult alreadyExists(Organization org) {
      return new ProvisionResult(org, false);
    }
  }
}
