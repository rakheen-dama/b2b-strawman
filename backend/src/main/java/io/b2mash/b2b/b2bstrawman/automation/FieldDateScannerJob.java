package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.FieldDateApproachingEvent;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled job that scans all tenants for DATE-type custom fields approaching configured
 * thresholds (14, 7, 1 days). For each matching field/entity/threshold combination, inserts a dedup
 * record and publishes a {@link FieldDateApproachingEvent}.
 */
@Component
public class FieldDateScannerJob {

  private static final Logger log = LoggerFactory.getLogger(FieldDateScannerJob.class);
  private static final int[] THRESHOLDS = {14, 7, 1};

  private final OrgSchemaMappingRepository mappingRepository;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final FieldDateNotificationLogRepository notificationLogRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate transactionTemplate;

  public FieldDateScannerJob(
      OrgSchemaMappingRepository mappingRepository,
      FieldDefinitionRepository fieldDefinitionRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      FieldDateNotificationLogRepository notificationLogRepository,
      ApplicationEventPublisher eventPublisher,
      TransactionTemplate transactionTemplate) {
    this.mappingRepository = mappingRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.eventPublisher = eventPublisher;
    this.transactionTemplate = transactionTemplate;
  }

  @Scheduled(cron = "${app.automation.field-date-scan-cron:0 0 6 * * *}")
  public void execute() {
    log.debug("Field date scanner started");
    var mappings = mappingRepository.findAll();
    int totalFired = 0;

    for (var mapping : mappings) {
      try {
        int count =
            ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
                .where(RequestScopes.ORG_ID, mapping.getExternalOrgId())
                .call(() -> scanTenant());
        totalFired += count;
      } catch (Exception e) {
        log.error("Failed to scan field dates for schema {}", mapping.getSchemaName(), e);
      }
    }

    if (totalFired > 0) {
      log.info("Field date scanner completed: {} events fired", totalFired);
    } else {
      log.debug("Field date scanner completed: 0 events fired");
    }
  }

  private int scanTenant() {
    Integer count =
        transactionTemplate.execute(
            tx -> {
              var dateFields =
                  fieldDefinitionRepository.findByFieldTypeAndActiveTrue(FieldType.DATE);

              if (dateFields.isEmpty()) {
                return 0;
              }

              int fired = 0;
              for (var fieldDef : dateFields) {
                fired += scanFieldDefinition(fieldDef);
              }
              return fired;
            });

    return count != null ? count : 0;
  }

  private int scanFieldDefinition(FieldDefinition fieldDef) {
    int fired = 0;

    if (fieldDef.getEntityType() == EntityType.CUSTOMER) {
      var customers = customerRepository.findAll();
      for (var customer : customers) {
        fired +=
            scanEntity(
                fieldDef,
                "customer",
                customer.getId(),
                customer.getName(),
                null,
                customer.getCustomFields());
      }
    } else if (fieldDef.getEntityType() == EntityType.PROJECT) {
      var projects = projectRepository.findAll();
      for (var project : projects) {
        fired +=
            scanEntity(
                fieldDef,
                "project",
                project.getId(),
                project.getName(),
                project.getId(),
                project.getCustomFields());
      }
    }

    return fired;
  }

  private int scanEntity(
      FieldDefinition fieldDef,
      String entityType,
      UUID entityId,
      String entityName,
      UUID projectId,
      Map<String, Object> customFields) {

    if (customFields == null) {
      return 0;
    }

    Object rawValue = customFields.get(fieldDef.getSlug());
    if (rawValue == null) {
      return 0;
    }

    LocalDate fieldDate;
    try {
      fieldDate = LocalDate.parse(rawValue.toString());
    } catch (Exception e) {
      log.warn(
          "Invalid date value '{}' for field '{}' on {} {}",
          rawValue,
          fieldDef.getSlug(),
          entityType,
          entityId);
      return 0;
    }

    long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), fieldDate);
    int fired = 0;

    for (int threshold : THRESHOLDS) {
      if (daysUntil == threshold) {
        boolean alreadyLogged =
            notificationLogRepository.existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                entityType, entityId, fieldDef.getSlug(), threshold);

        if (!alreadyLogged) {
          // Insert dedup record FIRST (before publishing event)
          var logEntry =
              new FieldDateNotificationLog(entityType, entityId, fieldDef.getSlug(), threshold);
          notificationLogRepository.save(logEntry);

          // Then publish the event
          var event =
              new FieldDateApproachingEvent(
                  "field_date.approaching",
                  entityType,
                  entityId,
                  projectId,
                  null,
                  "system",
                  RequestScopes.TENANT_ID.get(),
                  RequestScopes.ORG_ID.get(),
                  Instant.now(),
                  Map.of(
                      "field_name", fieldDef.getSlug(),
                      "field_label", fieldDef.getName(),
                      "field_value", fieldDate.toString(),
                      "days_until", threshold,
                      "entity_name", entityName));
          eventPublisher.publishEvent(event);
          fired++;

          log.info(
              "Field date approaching: {} '{}' on {} {} ({} days)",
              fieldDef.getSlug(),
              fieldDate,
              entityType,
              entityId,
              threshold);
        }
      }
    }

    return fired;
  }
}
