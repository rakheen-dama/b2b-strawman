package io.b2mash.b2b.b2bstrawman.informationrequest;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestItemRepository extends JpaRepository<RequestItem, UUID> {

  List<RequestItem> findByRequestId(UUID requestId);

  List<RequestItem> findByRequestIdOrderBySortOrder(UUID requestId);

  long countByRequestIdAndStatus(UUID requestId, ItemStatus status);

  long countByStatus(ItemStatus status);

  @Query(
      "SELECT COUNT(ri) FROM RequestItem ri WHERE ri.requestId IN :requestIds AND ri.status = :status")
  long countByRequestIdInAndStatus(
      @Param("requestIds") List<UUID> requestIds, @Param("status") ItemStatus status);
}
