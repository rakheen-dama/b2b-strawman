package io.b2mash.b2b.b2bstrawman.informationrequest;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestTemplateItemRepository extends JpaRepository<RequestTemplateItem, UUID> {

  List<RequestTemplateItem> findByTemplateIdOrderBySortOrder(UUID templateId);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM RequestTemplateItem i WHERE i.templateId = :templateId")
  void deleteByTemplateId(@Param("templateId") UUID templateId);
}
