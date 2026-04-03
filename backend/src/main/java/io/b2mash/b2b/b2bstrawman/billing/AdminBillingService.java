package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.billing.AdminBillingDtos.AdminBillingOverrideRequest;
import io.b2mash.b2b.b2bstrawman.billing.AdminBillingDtos.AdminTenantBillingResponse;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBillingService {

  private static final Logger log = LoggerFactory.getLogger(AdminBillingService.class);

  private final SubscriptionRepository subscriptionRepository;
  private final OrganizationRepository organizationRepository;
  private final SubscriptionStatusCache statusCache;

  public AdminBillingService(
      SubscriptionRepository subscriptionRepository,
      OrganizationRepository organizationRepository,
      SubscriptionStatusCache statusCache) {
    this.subscriptionRepository = subscriptionRepository;
    this.organizationRepository = organizationRepository;
    this.statusCache = statusCache;
  }

  @Transactional(readOnly = true)
  public List<AdminTenantBillingResponse> listTenants(
      String statusFilter, String billingMethodFilter, String profileFilter, String search) {
    var subscriptions = subscriptionRepository.findAll();
    var orgIds = subscriptions.stream().map(Subscription::getOrganizationId).toList();
    var orgs = organizationRepository.findAllById(orgIds);
    var orgMap = new HashMap<UUID, Organization>();
    for (var org : orgs) {
      orgMap.put(org.getId(), org);
    }

    return subscriptions.stream()
        .filter(
            sub -> {
              if (statusFilter != null
                  && !statusFilter.isBlank()
                  && !sub.getSubscriptionStatus().name().equals(statusFilter)) {
                return false;
              }
              if (billingMethodFilter != null
                  && !billingMethodFilter.isBlank()
                  && !sub.getBillingMethod().name().equals(billingMethodFilter)) {
                return false;
              }
              if (search != null && !search.isBlank()) {
                var org = orgMap.get(sub.getOrganizationId());
                if (org == null) return false;
                return org.getName().toLowerCase().contains(search.toLowerCase());
              }
              return true;
            })
        .map(
            sub -> {
              var org = orgMap.get(sub.getOrganizationId());
              if (org == null) return null;
              return AdminTenantBillingResponse.from(org, sub, 0, null);
            })
        .filter(r -> r != null)
        .toList();
  }

  @Transactional(readOnly = true)
  public AdminTenantBillingResponse getTenant(UUID orgId) {
    var org =
        organizationRepository
            .findById(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));
    var sub =
        subscriptionRepository
            .findByOrganizationId(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription", orgId));
    return AdminTenantBillingResponse.from(org, sub, 0, null);
  }

  @Transactional
  public AdminTenantBillingResponse overrideBilling(
      UUID orgId, AdminBillingOverrideRequest request) {
    var org =
        organizationRepository
            .findById(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));
    var sub =
        subscriptionRepository
            .findByOrganizationId(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription", orgId));

    // Build audit details before making changes
    var auditDetails = new HashMap<String, Object>();
    auditDetails.put("organization_id", orgId.toString());
    auditDetails.put("admin_note", request.adminNote());

    // Apply status change if requested
    if (request.status() != null && !request.status().isBlank()) {
      var newStatus = Subscription.SubscriptionStatus.valueOf(request.status());
      auditDetails.put("previous_status", sub.getSubscriptionStatus().name());
      auditDetails.put("new_status", newStatus.name());
      sub.adminTransitionTo(newStatus);
    }

    // Apply billing method change if requested
    if (request.billingMethod() != null && !request.billingMethod().isBlank()) {
      var newMethod = BillingMethod.valueOf(request.billingMethod());
      // Validate: cannot set PAYFAST without existing PayFast token
      if (newMethod == BillingMethod.PAYFAST && sub.getPayfastToken() == null) {
        throw new InvalidStateException(
            "Invalid billing method",
            "Cannot set billing method to PAYFAST without an existing PayFast token");
      }
      auditDetails.put("previous_billing_method", sub.getBillingMethod().name());
      auditDetails.put("new_billing_method", newMethod.name());
      sub.setBillingMethod(newMethod);
    }

    // Apply period end if provided
    if (request.currentPeriodEnd() != null) {
      sub.setCurrentPeriodEnd(request.currentPeriodEnd());
    }

    sub.setAdminNote(request.adminNote());
    subscriptionRepository.save(sub);
    statusCache.evict(orgId);

    // Emit audit event — note: platform admin endpoints run without tenant context,
    // so AuditService.log() (which writes to tenant schema) cannot be used directly.
    // Use a log-based audit for now. If the architecture requires DB audit,
    // a SYSTEM audit service for the public schema would be needed.
    log.info("Admin billing override for org {}: {}", orgId, auditDetails);

    return AdminTenantBillingResponse.from(org, sub, 0, null);
  }

  @Transactional
  public AdminTenantBillingResponse extendTrial(UUID orgId, int days) {
    var org =
        organizationRepository
            .findById(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));
    var sub =
        subscriptionRepository
            .findByOrganizationId(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription", orgId));

    if (sub.getSubscriptionStatus() != Subscription.SubscriptionStatus.TRIALING) {
      throw new InvalidStateException(
          "Cannot extend trial",
          "Subscription must be in TRIALING status, current: " + sub.getSubscriptionStatus());
    }

    var current = sub.getTrialEndsAt();
    var extended =
        current != null
            ? current.plus(Duration.ofDays(days))
            : Instant.now().plus(Duration.ofDays(days));
    sub.setTrialEndsAt(extended);
    subscriptionRepository.save(sub);
    statusCache.evict(orgId);

    log.info("Admin extended trial for org {} by {} days, new end: {}", orgId, days, extended);

    return AdminTenantBillingResponse.from(org, sub, 0, null);
  }
}
