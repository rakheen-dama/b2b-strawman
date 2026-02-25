package io.b2mash.b2b.b2bstrawman.setupstatus;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerReadinessService {

  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final EntityManager entityManager;

  public CustomerReadinessService(
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      FieldDefinitionRepository fieldDefinitionRepository,
      EntityManager entityManager) {
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public CustomerReadiness getReadiness(UUID customerId) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Null-safe guard for lifecycleStatus (Task 110.9)
    LifecycleStatus lifecycle = customer.getLifecycleStatus();
    if (lifecycle == null) {
      lifecycle = LifecycleStatus.ACTIVE;
    }
    String lifecycleStatusStr = lifecycle.name();

    ChecklistProgress checklistProgress = queryChecklistProgress(customerId);

    RequiredFieldStatus requiredFields =
        computeRequiredFields(EntityType.CUSTOMER, customer.getCustomFields());

    boolean hasLinkedProjects = customerProjectRepository.existsByCustomerId(customerId);

    String overallReadiness =
        computeOverallReadiness(lifecycle, requiredFields, checklistProgress, hasLinkedProjects);

    return new CustomerReadiness(
        customerId,
        lifecycleStatusStr,
        checklistProgress,
        requiredFields,
        hasLinkedProjects,
        overallReadiness);
  }

  private ChecklistProgress queryChecklistProgress(UUID customerId) {
    var query =
        entityManager.createNativeQuery(
            """
            SELECT ci.id, ct.name AS checklist_name,
              COUNT(*) FILTER (WHERE cii.required = true) AS total_required,
              COUNT(*) FILTER (WHERE cii.required = true AND cii.status = 'COMPLETED') AS completed_required
            FROM checklist_instances ci
            JOIN checklist_templates ct ON ct.id = ci.template_id
            JOIN checklist_instance_items cii ON cii.instance_id = ci.id
            WHERE ci.customer_id = :customerId
              AND ci.status = 'IN_PROGRESS'
            GROUP BY ci.id, ct.name
            ORDER BY ci.started_at ASC
            LIMIT 1
            """,
            Tuple.class);
    query.setParameter("customerId", customerId);

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();

    if (results.isEmpty()) {
      return null;
    }

    Tuple row = results.getFirst();
    String checklistName = row.get("checklist_name", String.class);
    int completed = ((Number) row.get("completed_required")).intValue();
    int total = ((Number) row.get("total_required")).intValue();
    int percentComplete = total == 0 ? 0 : (int) Math.round((completed * 100.0) / total);

    return new ChecklistProgress(checklistName, completed, total, percentComplete);
  }

  private String computeOverallReadiness(
      LifecycleStatus lifecycleStatus,
      RequiredFieldStatus requiredFields,
      ChecklistProgress checklistProgress,
      boolean hasLinkedProjects) {

    // "Complete" when ALL conditions met
    boolean requiredFieldsPass =
        requiredFields.total() == 0 || requiredFields.filled() == requiredFields.total();
    boolean checklistPass = checklistProgress == null || checklistProgress.percentComplete() == 100;

    if (lifecycleStatus == LifecycleStatus.ACTIVE
        && requiredFieldsPass
        && checklistPass
        && hasLinkedProjects) {
      return "Complete";
    }

    // "Needs Attention" when ANY condition met
    if (lifecycleStatus == LifecycleStatus.PROSPECT
        || !hasLinkedProjects
        || (requiredFields.total() > 0 && requiredFields.filled() == 0)) {
      return "Needs Attention";
    }

    // "In Progress" otherwise
    return "In Progress";
  }

  private RequiredFieldStatus computeRequiredFields(
      EntityType entityType, Map<String, Object> customFields) {
    var requiredDefs =
        fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(entityType).stream()
            .filter(FieldDefinition::isRequired)
            .toList();

    var fieldStatuses =
        requiredDefs.stream()
            .map(
                fd -> {
                  Object value = customFields != null ? customFields.get(fd.getSlug()) : null;
                  boolean filled = isFieldValueFilled(fd, value);
                  return new FieldStatus(fd.getName(), fd.getSlug(), filled);
                })
            .toList();

    int filled = (int) fieldStatuses.stream().filter(FieldStatus::filled).count();
    return new RequiredFieldStatus(filled, fieldStatuses.size(), fieldStatuses);
  }

  private boolean isFieldValueFilled(FieldDefinition fd, Object value) {
    if (value == null) {
      return false;
    }
    if (fd.getFieldType() == FieldType.CURRENCY) {
      if (!(value instanceof Map<?, ?> map)) {
        return false;
      }
      var amount = map.get("amount");
      var currency = map.get("currency");
      return amount != null && currency != null && !currency.toString().isBlank();
    }
    return !value.toString().isBlank();
  }
}
