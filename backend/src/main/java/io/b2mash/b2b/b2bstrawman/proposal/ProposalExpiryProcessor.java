package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled job that runs hourly to expire overdue proposals across all tenant schemas. For each
 * SENT proposal past its expiresAt deadline, transitions to EXPIRED, logs an audit event, and
 * publishes a {@link ProposalExpiredEvent} for post-commit notifications and portal sync.
 */
@Component
public class ProposalExpiryProcessor {

  private static final Logger log = LoggerFactory.getLogger(ProposalExpiryProcessor.class);

  private final OrgSchemaMappingRepository mappingRepository;
  private final ProposalRepository proposalRepository;
  private final CustomerRepository customerRepository;
  private final PortalContactRepository portalContactRepository;
  private final OrganizationRepository organizationRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate transactionTemplate;

  public ProposalExpiryProcessor(
      OrgSchemaMappingRepository mappingRepository,
      ProposalRepository proposalRepository,
      CustomerRepository customerRepository,
      PortalContactRepository portalContactRepository,
      OrganizationRepository organizationRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      TransactionTemplate transactionTemplate) {
    this.mappingRepository = mappingRepository;
    this.proposalRepository = proposalRepository;
    this.customerRepository = customerRepository;
    this.portalContactRepository = portalContactRepository;
    this.organizationRepository = organizationRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.transactionTemplate = transactionTemplate;
  }

  @Scheduled(fixedRateString = "${proposal.expiry.interval:3600000}")
  public void processExpiredProposals() {
    log.info("Proposal expiry processor started");
    var mappings = mappingRepository.findAll();
    int totalExpired = 0;

    for (var mapping : mappings) {
      try {
        int expired =
            ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
                .where(RequestScopes.ORG_ID, mapping.getClerkOrgId())
                .call(() -> processExpiredForTenant(mapping.getClerkOrgId()));
        totalExpired += expired;
      } catch (Exception e) {
        log.error("Proposal expiry processor failed for schema {}", mapping.getSchemaName(), e);
      }
    }

    if (totalExpired > 0) {
      log.info(
          "Proposal expiry processor completed: {} proposals expired across {} tenants",
          totalExpired,
          mappings.size());
    } else {
      log.info("Proposal expiry processor completed: no proposals expired");
    }
  }

  private int processExpiredForTenant(String orgId) {
    Integer result =
        transactionTemplate.execute(
            status -> {
              var expired =
                  proposalRepository.findByStatusAndExpiresAtBefore(
                      ProposalStatus.SENT, Instant.now());

              for (var proposal : expired) {
                proposal.markExpired();
                proposalRepository.save(proposal);

                // Audit: proposal.expired
                auditService.log(
                    AuditEventBuilder.builder()
                        .eventType("proposal.expired")
                        .entityType("proposal")
                        .entityId(proposal.getId())
                        .actorType("SYSTEM")
                        .source("INTERNAL")
                        .details(
                            Map.of(
                                "proposal_number",
                                proposal.getProposalNumber(),
                                "expired_at",
                                proposal.getExpiresAt().toString()))
                        .build());

                // Resolve names for the event
                String customerName =
                    customerRepository
                        .findById(proposal.getCustomerId())
                        .map(c -> c.getName())
                        .orElse("Unknown");

                String portalContactEmail =
                    proposal.getPortalContactId() != null
                        ? portalContactRepository
                            .findById(proposal.getPortalContactId())
                            .map(c -> c.getEmail())
                            .orElse(null)
                        : null;

                String orgName =
                    organizationRepository
                        .findByClerkOrgId(orgId)
                        .map(o -> o.getName())
                        .orElse("Unknown");

                eventPublisher.publishEvent(
                    new ProposalExpiredEvent(
                        proposal.getId(),
                        proposal.getProposalNumber(),
                        customerName,
                        proposal.getCreatedById(),
                        portalContactEmail,
                        orgName,
                        RequestScopes.getTenantIdOrNull(),
                        RequestScopes.getOrgIdOrNull()));
              }

              return expired.size();
            });
    return result != null ? result : 0;
  }
}
