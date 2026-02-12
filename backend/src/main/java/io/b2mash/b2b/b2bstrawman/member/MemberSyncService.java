package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.PlanLimitExceededException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanLimits;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MemberSyncService {

  private static final Logger log = LoggerFactory.getLogger(MemberSyncService.class);

  private final MemberRepository memberRepository;
  private final OrgSchemaMappingRepository mappingRepository;
  private final OrganizationRepository organizationRepository;
  private final MemberFilter memberFilter;
  private final TransactionTemplate txTemplate;
  private final AuditService auditService;

  public MemberSyncService(
      MemberRepository memberRepository,
      OrgSchemaMappingRepository mappingRepository,
      OrganizationRepository organizationRepository,
      MemberFilter memberFilter,
      PlatformTransactionManager txManager,
      AuditService auditService) {
    this.memberRepository = memberRepository;
    this.mappingRepository = mappingRepository;
    this.organizationRepository = organizationRepository;
    this.memberFilter = memberFilter;
    this.txTemplate = new TransactionTemplate(txManager);
    this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.auditService = auditService;
  }

  public SyncResult syncMember(
      String clerkOrgId,
      String clerkUserId,
      String email,
      String name,
      String avatarUrl,
      String orgRole) {
    String schemaName = resolveSchema(clerkOrgId);
    return ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, clerkOrgId)
        .call(
            () ->
                txTemplate.execute(
                    status -> {
                      var existing = memberRepository.findByClerkUserId(clerkUserId);
                      if (existing.isPresent()) {
                        var member = existing.get();

                        // Capture old role before mutation
                        String oldRole = member.getOrgRole();

                        member.updateFrom(email, name, avatarUrl, orgRole);
                        memberRepository.save(member);
                        log.info("Updated member {} in tenant {}", clerkUserId, schemaName);

                        // Only emit role_changed if role actually changed
                        if (!oldRole.equals(orgRole)) {
                          auditService.log(
                              AuditEventBuilder.builder()
                                  .eventType("member.role_changed")
                                  .entityType("member")
                                  .entityId(member.getId())
                                  .actorType("WEBHOOK")
                                  .source("WEBHOOK")
                                  .details(
                                      Map.of("org_role", Map.of("from", oldRole, "to", orgRole)))
                                  .build());
                        }

                        return new SyncResult(member.getId(), false);
                      }

                      enforceMemberLimit(clerkOrgId);

                      var member = new Member(clerkUserId, email, name, avatarUrl, orgRole);
                      memberRepository.save(member);
                      log.info("Created member {} in tenant {}", clerkUserId, schemaName);

                      auditService.log(
                          AuditEventBuilder.builder()
                              .eventType("member.synced")
                              .entityType("member")
                              .entityId(member.getId())
                              .actorType("WEBHOOK")
                              .source("WEBHOOK")
                              .details(Map.of("action", "added", "email", email))
                              .build());

                      return new SyncResult(member.getId(), true);
                    }));
  }

  public void deleteMember(String clerkOrgId, String clerkUserId) {
    String schemaName = resolveSchema(clerkOrgId);
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, clerkOrgId)
        .call(
            () -> {
              txTemplate.executeWithoutResult(
                  status -> {
                    var member =
                        memberRepository
                            .findByClerkUserId(clerkUserId)
                            .orElseThrow(
                                () ->
                                    ResourceNotFoundException.withDetail(
                                        "Member not found",
                                        "No member found with clerkUserId: " + clerkUserId));
                    UUID memberId = member.getId();
                    memberRepository.deleteByClerkUserId(clerkUserId);
                    log.info("Deleted member {} from tenant {}", clerkUserId, schemaName);

                    auditService.log(
                        AuditEventBuilder.builder()
                            .eventType("member.removed")
                            .entityType("member")
                            .entityId(memberId)
                            .actorType("WEBHOOK")
                            .source("WEBHOOK")
                            .details(Map.of("clerk_user_id", clerkUserId))
                            .build());
                  });
              memberFilter.evictFromCache(schemaName, clerkUserId);
              return null;
            });
  }

  private void enforceMemberLimit(String clerkOrgId) {
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseThrow(
                () -> new IllegalArgumentException("No organization found for org: " + clerkOrgId));
    int limit = PlanLimits.maxMembers(org.getTier());
    long currentCount = memberRepository.count();
    if (currentCount >= limit) {
      throw new PlanLimitExceededException(
          "Member limit reached (%d/%d). Upgrade to add more members."
              .formatted(currentCount, limit));
    }
  }

  private String resolveSchema(String clerkOrgId) {
    return mappingRepository
        .findByClerkOrgId(clerkOrgId)
        .orElseThrow(
            () -> new IllegalArgumentException("No tenant provisioned for org: " + clerkOrgId))
        .getSchemaName();
  }

  public record SyncResult(UUID memberId, boolean created) {}
}
