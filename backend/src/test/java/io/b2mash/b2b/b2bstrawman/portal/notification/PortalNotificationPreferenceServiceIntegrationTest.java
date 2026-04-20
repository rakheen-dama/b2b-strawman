package io.b2mash.b2b.b2bstrawman.portal.notification;

import static org.assertj.core.api.Assertions.*;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.notification.PortalNotificationPreferenceService.PortalNotificationPreferenceUpdate;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.settings.PortalDigestCadence;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for Epic 498A scaffolding:
 *
 * <ol>
 *   <li>{@code getOrCreate} on first call persists a row with all five toggles defaulted to {@code
 *       true}; repeat calls return the same row (idempotent, no duplicates).
 *   <li>{@code update} persists toggle changes.
 *   <li>{@code portal_digest_cadence} defaults to {@link PortalDigestCadence#WEEKLY} and
 *       round-trips through {@link OrgSettingsService#updatePortalDigestCadence}.
 * </ol>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalNotificationPreferenceServiceIntegrationTest {

  private static final String ORG_ID = "org_portal_notif_pref_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private PortalNotificationPreferenceService preferenceService;
  @Autowired private PortalNotificationPreferenceRepository preferenceRepository;
  @Autowired private OrgSettingsService orgSettingsService;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Portal Notification Pref Test Org", null)
            .schemaName();

    ownerMemberId =
        memberSyncService
            .syncMember(ORG_ID, "user_pnp_owner", "pnp_owner@test.com", "Pnp Owner", null, "owner")
            .memberId();
  }

  @Test
  void getOrCreate_firstCall_createsRowWithAllDefaultsTrue() {
    UUID contactId = UUID.randomUUID();

    var created = runInTenantReturning(() -> preferenceService.getOrCreate(contactId));

    assertThat(created.getPortalContactId()).isEqualTo(contactId);
    assertThat(created.isDigestEnabled()).isTrue();
    assertThat(created.isTrustActivityEnabled()).isTrue();
    assertThat(created.isRetainerUpdatesEnabled()).isTrue();
    assertThat(created.isDeadlineRemindersEnabled()).isTrue();
    assertThat(created.isActionRequiredEnabled()).isTrue();
    assertThat(created.getLastUpdatedAt()).isNotNull();

    // Second call must not create a duplicate row — same PK is re-read.
    long countAfterFirstRead = runInTenantReturning(() -> preferenceRepository.count());
    var reloaded = runInTenantReturning(() -> preferenceService.getOrCreate(contactId));
    assertThat(reloaded.getPortalContactId()).isEqualTo(contactId);
    assertThat(reloaded.getLastUpdatedAt()).isEqualTo(created.getLastUpdatedAt());

    long countAfterSecondRead = runInTenantReturning(() -> preferenceRepository.count());
    assertThat(countAfterSecondRead).isEqualTo(countAfterFirstRead);
  }

  @Test
  void update_persistsToggleChanges() {
    UUID contactId = UUID.randomUUID();

    runInTenant(() -> preferenceService.getOrCreate(contactId));

    var updated =
        runInTenantReturning(
            () ->
                preferenceService.update(
                    contactId,
                    new PortalNotificationPreferenceUpdate(false, false, true, false, true)));

    assertThat(updated.isDigestEnabled()).isFalse();
    assertThat(updated.isTrustActivityEnabled()).isFalse();
    assertThat(updated.isRetainerUpdatesEnabled()).isTrue();
    assertThat(updated.isDeadlineRemindersEnabled()).isFalse();
    assertThat(updated.isActionRequiredEnabled()).isTrue();

    // Round-trip via a fresh read.
    var reloaded = runInTenantReturning(() -> preferenceService.getOrCreate(contactId));
    assertThat(reloaded.isDigestEnabled()).isFalse();
    assertThat(reloaded.isTrustActivityEnabled()).isFalse();
    assertThat(reloaded.isRetainerUpdatesEnabled()).isTrue();
    assertThat(reloaded.isDeadlineRemindersEnabled()).isFalse();
    assertThat(reloaded.isActionRequiredEnabled()).isTrue();
  }

  @Test
  void portalDigestCadence_defaultsToWeekly_andRoundTrips() {
    // Default on a freshly provisioned tenant (no org_settings row yet).
    var defaultCadence = runInTenantReturning(() -> orgSettingsService.getPortalDigestCadence());
    assertThat(defaultCadence).isEqualTo(PortalDigestCadence.WEEKLY);

    runInTenant(
        () -> {
          var actor = new ActorContext(ownerMemberId, "owner");
          orgSettingsService.updatePortalDigestCadence(PortalDigestCadence.BIWEEKLY, actor);
        });

    var readBack = runInTenantReturning(() -> orgSettingsService.getPortalDigestCadence());
    assertThat(readBack).isEqualTo(PortalDigestCadence.BIWEEKLY);
  }

  // --- helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private <T> T runInTenantReturning(Supplier<T> supplier) {
    final Object[] holder = new Object[1];
    runInTenant(() -> holder[0] = supplier.get());
    @SuppressWarnings("unchecked")
    T result = (T) holder[0];
    return result;
  }
}
