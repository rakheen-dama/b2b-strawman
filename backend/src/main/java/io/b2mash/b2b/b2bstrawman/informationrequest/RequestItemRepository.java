package io.b2mash.b2b.b2bstrawman.informationrequest;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestItemRepository extends JpaRepository<RequestItem, UUID> {

  List<RequestItem> findByRequestId(UUID requestId);

  List<RequestItem> findByRequestIdOrderBySortOrder(UUID requestId);

  long countByRequestIdAndStatus(UUID requestId, ItemStatus status);
}
