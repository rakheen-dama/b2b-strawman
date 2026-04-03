package io.b2mash.b2b.b2bstrawman.billing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  Optional<Subscription> findByOrganizationId(UUID organizationId);

  List<Subscription> findBySubscriptionStatusAndTrialEndsAtBefore(
      Subscription.SubscriptionStatus status, Instant cutoff);

  List<Subscription> findBySubscriptionStatusInAndGraceEndsAtBefore(
      List<Subscription.SubscriptionStatus> statuses, Instant cutoff);

  List<Subscription> findBySubscriptionStatusAndCurrentPeriodEndBefore(
      Subscription.SubscriptionStatus status, Instant cutoff);

  List<Subscription> findBySubscriptionStatusAndBillingMethodInAndTrialEndsAtBefore(
      Subscription.SubscriptionStatus status, List<BillingMethod> methods, Instant cutoff);
}
