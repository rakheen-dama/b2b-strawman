package io.b2mash.b2b.b2bstrawman.verticals.legal.tariff;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TariffItemRepository extends JpaRepository<TariffItem, UUID> {

  List<TariffItem> findByScheduleIdOrderBySortOrderAsc(UUID scheduleId);

  @Query(
      value =
          "SELECT * FROM tariff_items"
              + " WHERE schedule_id = :scheduleId"
              + " AND public.similarity(lower(description), lower(:search)) > 0.1"
              + " ORDER BY public.similarity(lower(description), lower(:search)) DESC",
      nativeQuery = true)
  List<TariffItem> searchByDescription(
      @Param("scheduleId") UUID scheduleId, @Param("search") String search);

  List<TariffItem> findByScheduleIdAndSectionOrderBySortOrderAsc(UUID scheduleId, String section);
}
