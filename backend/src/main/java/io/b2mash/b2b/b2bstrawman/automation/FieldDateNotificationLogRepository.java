package io.b2mash.b2b.b2bstrawman.automation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldDateNotificationLogRepository
    extends JpaRepository<FieldDateNotificationLog, UUID> {

  boolean existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
      String entityType, UUID entityId, String fieldName, int daysUntil);
}
