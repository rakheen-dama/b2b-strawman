package io.b2mash.b2b.b2bstrawman.setupstatus;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldUtils;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
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
                  boolean filled = CustomFieldUtils.isFieldValueFilled(fd, value);
                  return new FieldStatus(fd.getName(), fd.getSlug(), filled);
                })
            .toList();

    int filled = (int) fieldStatuses.stream().filter(FieldStatus::filled).count();
    return new RequiredFieldStatus(filled, fieldStatuses.size(), fieldStatuses);
  }

  /**
   * Computes per-context readiness breakdown for a single customer. Returns a map keyed by
   * PrerequisiteContext with the field fill status for each context. Only includes contexts that
   * have at least one active field definition.
   */
  @Transactional(readOnly = true)
  public Map<PrerequisiteContext, RequiredFieldStatus> computeReadinessByContext(UUID customerId) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    Map<String, Object> customFields = customer.getCustomFields();

    var allDefs =
        fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.CUSTOMER);

    Map<PrerequisiteContext, List<FieldStatus>> byContext =
        new EnumMap<>(PrerequisiteContext.class);

    for (var fd : allDefs) {
      var contexts = fd.getRequiredForContexts();
      if (contexts == null || contexts.isEmpty()) {
        continue;
      }
      Object value = customFields != null ? customFields.get(fd.getSlug()) : null;
      boolean filled = CustomFieldUtils.isFieldValueFilled(fd, value);
      var fieldStatus = new FieldStatus(fd.getName(), fd.getSlug(), filled);

      for (String contextName : contexts) {
        PrerequisiteContext ctx = PrerequisiteContext.valueOf(contextName);
        byContext.computeIfAbsent(ctx, k -> new ArrayList<>()).add(fieldStatus);
      }
    }

    Map<PrerequisiteContext, RequiredFieldStatus> result = new EnumMap<>(PrerequisiteContext.class);
    for (var entry : byContext.entrySet()) {
      var statuses = entry.getValue();
      int filledCount = (int) statuses.stream().filter(FieldStatus::filled).count();
      result.put(entry.getKey(), new RequiredFieldStatus(filledCount, statuses.size(), statuses));
    }
    return result;
  }

  /**
   * Batch-computes completeness scores for a list of customer IDs. Loads all required field
   * definitions in one query and all customers in one query — avoids N+1.
   */
  @Transactional(readOnly = true)
  public Map<UUID, CompletenessScore> batchComputeCompleteness(List<UUID> customerIds) {
    if (customerIds == null || customerIds.isEmpty()) {
      return Map.of();
    }

    var requiredDefs =
        fieldDefinitionRepository
            .findByEntityTypeAndActiveTrueOrderBySortOrder(EntityType.CUSTOMER)
            .stream()
            .filter(
                fd -> fd.getRequiredForContexts() != null && !fd.getRequiredForContexts().isEmpty())
            .toList();

    var customers = customerRepository.findAllById(customerIds);

    Map<UUID, CompletenessScore> result = new HashMap<>();
    for (var customer : customers) {
      Map<String, Object> customFields = customer.getCustomFields();
      int total = requiredDefs.size();
      int filled = 0;
      for (var fd : requiredDefs) {
        Object value = customFields != null ? customFields.get(fd.getSlug()) : null;
        if (CustomFieldUtils.isFieldValueFilled(fd, value)) {
          filled++;
        }
      }
      int percentage = total == 0 ? 100 : (int) Math.round((filled * 100.0) / total);
      result.put(customer.getId(), new CompletenessScore(total, filled, percentage));
    }
    return result;
  }

  /**
   * Returns the top-N most common missing required fields across all customers. Used by the
   * dashboard "Incomplete Customer Profiles" widget.
   */
  @Transactional(readOnly = true)
  public List<MissingFieldSummary> getTopMissingFields(int limit) {
    var requiredDefs =
        fieldDefinitionRepository
            .findByEntityTypeAndActiveTrueOrderBySortOrder(EntityType.CUSTOMER)
            .stream()
            .filter(
                fd -> fd.getRequiredForContexts() != null && !fd.getRequiredForContexts().isEmpty())
            .toList();

    if (requiredDefs.isEmpty()) {
      return List.of();
    }

    var allCustomers = customerRepository.findAll();

    Map<String, String> slugToName = new HashMap<>();
    Map<String, Integer> slugToCount = new HashMap<>();
    for (var fd : requiredDefs) {
      slugToName.put(fd.getSlug(), fd.getName());
      slugToCount.put(fd.getSlug(), 0);
    }

    for (var customer : allCustomers) {
      Map<String, Object> customFields = customer.getCustomFields();
      for (var fd : requiredDefs) {
        Object value = customFields != null ? customFields.get(fd.getSlug()) : null;
        if (!CustomFieldUtils.isFieldValueFilled(fd, value)) {
          slugToCount.merge(fd.getSlug(), 1, Integer::sum);
        }
      }
    }

    return slugToCount.entrySet().stream()
        .filter(e -> e.getValue() > 0)
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(limit)
        .map(e -> new MissingFieldSummary(slugToName.get(e.getKey()), e.getKey(), e.getValue()))
        .toList();
  }

  /**
   * Returns aggregated completeness statistics for the dashboard widget. Combines top missing
   * fields and incomplete/total customer counts in a single pass.
   */
  @Transactional(readOnly = true)
  public AggregatedCompletenessResponse getAggregatedSummary(int topN) {
    var requiredDefs =
        fieldDefinitionRepository
            .findByEntityTypeAndActiveTrueOrderBySortOrder(EntityType.CUSTOMER)
            .stream()
            .filter(
                fd -> fd.getRequiredForContexts() != null && !fd.getRequiredForContexts().isEmpty())
            .toList();

    var allCustomers = customerRepository.findAll();
    long totalCount = allCustomers.size();
    long incompleteCount = 0;

    Map<String, String> slugToName = new HashMap<>();
    Map<String, Integer> slugToCount = new HashMap<>();
    for (var fd : requiredDefs) {
      slugToName.put(fd.getSlug(), fd.getName());
      slugToCount.put(fd.getSlug(), 0);
    }

    for (var customer : allCustomers) {
      Map<String, Object> customFields = customer.getCustomFields();
      int total = requiredDefs.size();
      int filled = 0;
      for (var fd : requiredDefs) {
        Object value = customFields != null ? customFields.get(fd.getSlug()) : null;
        if (CustomFieldUtils.isFieldValueFilled(fd, value)) {
          filled++;
        } else {
          slugToCount.merge(fd.getSlug(), 1, Integer::sum);
        }
      }
      int percentage = total == 0 ? 100 : (int) Math.round((filled * 100.0) / total);
      if (percentage < 100) {
        incompleteCount++;
      }
    }

    var topMissing =
        slugToCount.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(topN)
            .map(e -> new MissingFieldSummary(slugToName.get(e.getKey()), e.getKey(), e.getValue()))
            .toList();

    return new AggregatedCompletenessResponse(topMissing, incompleteCount, totalCount);
  }
}
