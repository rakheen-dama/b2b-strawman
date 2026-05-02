package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

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
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditServiceActorDisplayIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_audit_actor_display_test";

  // Constructor injection per project guideline — never @Autowired on fields. JUnit 5 +
  // SpringExtension resolves these via the Spring application context at @TestInstance(PER_CLASS)
  // construction time.
  private final AuditService auditService;
  private final MemberRepository memberRepository;
  private final TenantProvisioningService provisioningSvc;

  AuditServiceActorDisplayIntegrationTest(
      AuditService auditService,
      MemberRepository memberRepository,
      TenantProvisioningService provisioningSvc) {
    this.auditService = auditService;
    this.memberRepository = memberRepository;
    this.provisioningSvc = provisioningSvc;
  }

  private String schemaName;
  private UUID liveMemberId;
  private UUID formerMemberId;
  private UUID blankNameMemberId;

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

    // Seed a member with a blank name so we can verify the blank-name fallback path. Member has
    // no setName() in production code (names flow through Clerk sync); we reflectively blank the
    // field, then save through the JPA repository inside the tenant scope so the row is persisted
    // with name = "". The "Former member ({uuid})" fallback must fire for this case too.
    var blankNameMemberIdStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_audit_actor_display_blank",
            "audit_actor_display_blank@test.com",
            "Will Be Blanked",
            "member");
    blankNameMemberId = UUID.fromString(blankNameMemberIdStr);
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findById(blankNameMemberId).orElseThrow();
              try {
                Field nameField = Member.class.getDeclaredField("name");
                nameField.setAccessible(true);
                nameField.set(member, "");
              } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException("Failed to blank member name reflectively", e);
              }
              memberRepository.save(member);
            });
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
  void blankMemberNameResolvesToFormerMemberFallback() {
    // A Member row exists but its name is blank — the resolver must drop the blank and fall back
    // to the "Former member ({uuid})" branch in both the single and batch code paths so the UI
    // never renders an empty actor cell.
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var name = auditService.resolveActorDisplay(blankNameMemberId, "USER");
              assertThat(name).isEqualTo("Former member (" + blankNameMemberId + ")");

              // Batch path must also exclude blank-name members so callers can apply the same
              // fallback uniformly.
              var batch = auditService.resolveActorDisplayNames(List.of(blankNameMemberId));
              assertThat(batch).doesNotContainKey(blankNameMemberId);
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
