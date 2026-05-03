package io.b2mash.b2b.b2bstrawman.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link PlanTierResolver}. Critical guard: PAST_DUE must NOT map to PRO. A failed-
 * payment tenant retains general write access during the dunning grace period (per {@code
 * SubscriptionStatus#isWriteEnabled()}), but PRO-gated specialist features must be revoked.
 */
@ExtendWith(MockitoExtension.class)
class PlanTierResolverTest {

  @Mock private SubscriptionStatusCache cache;
  @Mock private OrganizationRepository organizationRepository;

  @Test
  void trialingMapsToPro() {
    when(cache.getStatus(any())).thenReturn(Subscription.SubscriptionStatus.TRIALING);
    var resolver = new PlanTierResolver(cache, organizationRepository);
    assertThat(resolver.resolveForOrganization(UUID.randomUUID())).isEqualTo(PlanTier.PRO);
  }

  @Test
  void activeMapsToPro() {
    when(cache.getStatus(any())).thenReturn(Subscription.SubscriptionStatus.ACTIVE);
    var resolver = new PlanTierResolver(cache, organizationRepository);
    assertThat(resolver.resolveForOrganization(UUID.randomUUID())).isEqualTo(PlanTier.PRO);
  }

  @Test
  void pendingCancellationMapsToPro() {
    when(cache.getStatus(any())).thenReturn(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
    var resolver = new PlanTierResolver(cache, organizationRepository);
    assertThat(resolver.resolveForOrganization(UUID.randomUUID())).isEqualTo(PlanTier.PRO);
  }

  @Test
  void pastDueMapsToStarter() {
    when(cache.getStatus(any())).thenReturn(Subscription.SubscriptionStatus.PAST_DUE);
    var resolver = new PlanTierResolver(cache, organizationRepository);
    assertThat(resolver.resolveForOrganization(UUID.randomUUID())).isEqualTo(PlanTier.STARTER);
  }

  @Test
  void expiredMapsToStarter() {
    when(cache.getStatus(any())).thenReturn(Subscription.SubscriptionStatus.EXPIRED);
    var resolver = new PlanTierResolver(cache, organizationRepository);
    assertThat(resolver.resolveForOrganization(UUID.randomUUID())).isEqualTo(PlanTier.STARTER);
  }

  @Test
  void suspendedMapsToStarter() {
    when(cache.getStatus(any())).thenReturn(Subscription.SubscriptionStatus.SUSPENDED);
    var resolver = new PlanTierResolver(cache, organizationRepository);
    assertThat(resolver.resolveForOrganization(UUID.randomUUID())).isEqualTo(PlanTier.STARTER);
  }
}
