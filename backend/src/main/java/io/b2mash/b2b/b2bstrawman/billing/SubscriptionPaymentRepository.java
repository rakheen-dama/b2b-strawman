package io.b2mash.b2b.b2bstrawman.billing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {

  List<SubscriptionPayment> findBySubscriptionIdOrderByPaymentDateDesc(UUID subscriptionId);

  boolean existsByPayfastPaymentId(String payfastPaymentId);

  Page<SubscriptionPayment> findBySubscriptionId(UUID subscriptionId, Pageable pageable);
}
