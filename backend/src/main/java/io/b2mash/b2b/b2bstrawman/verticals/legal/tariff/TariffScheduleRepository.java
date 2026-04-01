package io.b2mash.b2b.b2bstrawman.verticals.legal.tariff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TariffScheduleRepository extends JpaRepository<TariffSchedule, UUID> {

  List<TariffSchedule> findByCategoryAndCourtLevelAndIsActiveTrue(
      String category, String courtLevel);

  @EntityGraph(attributePaths = "items")
  Optional<TariffSchedule> findWithItemsById(UUID id);
}
