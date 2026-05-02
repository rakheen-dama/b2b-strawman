package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for {@link AuditService#resolveActorDisplayNames(java.util.Collection)} and
 * {@link AuditService#resolveActorDisplay(UUID, String)} (501.6) covering the architecture §12.3.4
 * fallback table:
 *
 * <ul>
 *   <li>live USER member ⇒ {@code Member.name}
 *   <li>USER actor with no surviving member row ⇒ {@code "Former member ({uuid})"}
 *   <li>SYSTEM actor type ⇒ {@code "System"} (regardless of actorId)
 * </ul>
 *
 * <p>Operates inside {@code ScopedValue.where(RequestScopes.TENANT_ID, schemaName).run(...)} so the
 * tenant-scoped {@code search_path} is set for the member lookups.
 */
class AuditServiceActorDisplayIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_audit_actor_display_test";

  @Autowired private AuditService auditService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private TenantProvisioningService provisioningSvc;

  private String schemaName;
  private UUID liveMemberId;
  private UUID formerMemberId;

  @BeforeAll
  void provisionTenantAndSeed() throws Exception {
    schemaName =
        provisioningSvc.provisionTenant(ORG_ID, "Audit Actor Display Test Org", null).schemaName();

    // Seed a live member via the internal sync API (tenant-scoped helper).
    var liveMemberIdStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_audit_actor_display_live",
            "audit_actor_display_live@test.com",
            "Live Member",
            "owner");
    liveMemberId = UUID.fromString(liveMemberIdStr);

    // Seed a "former" member, then delete the row so resolveActorDisplay falls through to the
    // "Former member ({uuid})" branch. Member has no soft-delete column; we hard-delete inside the
    // tenant scope so the LEFT JOIN miss reflects the architecture §12.3.4 fallback intent.
    var formerMemberIdStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_audit_actor_display_former",
            "audit_actor_display_former@test.com",
            "Former Member",
            "member");
    formerMemberId = UUID.fromString(formerMemberIdStr);
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(() -> memberRepository.deleteById(formerMemberId));
  }

  @Test
  void liveMemberResolvesToMemberName() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var name = auditService.resolveActorDisplay(liveMemberId, "USER");
              assertThat(name).isEqualTo("Live Member");

              var batch = auditService.resolveActorDisplayNames(List.of(liveMemberId));
              assertThat(batch).containsEntry(liveMemberId, "Live Member");
            });
  }

  @Test
  void missingMemberRowResolvesToFormerMemberFallback() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var name = auditService.resolveActorDisplay(formerMemberId, "USER");
              assertThat(name).isEqualTo("Former member (" + formerMemberId + ")");

              // Batch lookup: deleted member is absent from the returned map (caller applies the
              // fallback in resolveActorDisplay path; the batch map intentionally only carries
              // entries that resolved).
              var batch = auditService.resolveActorDisplayNames(List.of(formerMemberId));
              assertThat(batch).doesNotContainKey(formerMemberId);
            });
  }

  @Test
  void systemActorTypeResolvesToSystemRegardlessOfActorId() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // No actorId, SYSTEM actor type: must yield "System"
              assertThat(auditService.resolveActorDisplay(null, "SYSTEM")).isEqualTo("System");
              // Even if a stray actorId is provided, SYSTEM type wins.
              assertThat(auditService.resolveActorDisplay(UUID.randomUUID(), "SYSTEM"))
                  .isEqualTo("System");
            });
  }

  @Test
  void otherStaticActorTypesYieldArchitectureLabels() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              assertThat(auditService.resolveActorDisplay(null, "PORTAL_CONTACT"))
                  .isEqualTo("Portal Contact");
              assertThat(auditService.resolveActorDisplay(null, "AUTOMATION"))
                  .isEqualTo("Automation");
              assertThat(auditService.resolveActorDisplay(null, "API_KEY")).isEqualTo("API Key");
              // Unknown actor type defensively maps to "System".
              assertThat(auditService.resolveActorDisplay(null, "WEBHOOK")).isEqualTo("System");
            });
  }

  @Test
  void resolveActorDisplayNamesEmptyOrNullReturnsEmptyMap() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              assertThat(auditService.resolveActorDisplayNames(List.of())).isEmpty();
              assertThat(auditService.resolveActorDisplayNames(null)).isEmpty();
            });
  }
}
