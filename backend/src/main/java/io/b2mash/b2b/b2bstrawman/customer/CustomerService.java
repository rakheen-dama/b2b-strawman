package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMemberRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupService;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

  private final CustomerRepository repository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final CustomFieldValidator customFieldValidator;
  private final FieldGroupRepository fieldGroupRepository;
  private final FieldGroupMemberRepository fieldGroupMemberRepository;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final FieldGroupService fieldGroupService;

  public CustomerService(
      CustomerRepository repository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      CustomFieldValidator customFieldValidator,
      FieldGroupRepository fieldGroupRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository,
      FieldDefinitionRepository fieldDefinitionRepository,
      FieldGroupService fieldGroupService) {
    this.repository = repository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.customFieldValidator = customFieldValidator;
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.fieldGroupService = fieldGroupService;
  }

  @Transactional(readOnly = true)
  public List<Customer> listCustomers() {
    return repository.findAll();
  }

  @Transactional(readOnly = true)
  public List<Customer> listCustomersByLifecycleStatus(LifecycleStatus lifecycleStatus) {
    return repository.findByLifecycleStatus(lifecycleStatus);
  }

  @Transactional(readOnly = true)
  public Customer getCustomer(UUID id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
  }

  @Transactional
  public Customer createCustomer(
      String name, String email, String phone, String idNumber, String notes, UUID createdBy) {
    return createCustomer(name, email, phone, idNumber, notes, createdBy, null, null);
  }

  @Transactional
  public Customer createCustomer(
      String name,
      String email,
      String phone,
      String idNumber,
      String notes,
      UUID createdBy,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {
    return createCustomer(
        name, email, phone, idNumber, notes, createdBy, customFields, appliedFieldGroups, null);
  }

  @Transactional
  public Customer createCustomer(
      String name,
      String email,
      String phone,
      String idNumber,
      String notes,
      UUID createdBy,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      CustomerType customerType) {
    if (repository.existsByEmail(email)) {
      throw new ResourceConflictException(
          "Customer email conflict", "A customer with email " + email + " already exists");
    }

    // Validate custom fields
    Map<String, Object> validatedFields =
        customFieldValidator.validate(
            EntityType.CUSTOMER,
            customFields != null ? customFields : new HashMap<>(),
            appliedFieldGroups);

    var customer = new Customer(name, email, phone, idNumber, notes, createdBy, customerType);
    customer.setCustomFields(validatedFields);
    if (appliedFieldGroups != null) {
      customer.setAppliedFieldGroups(appliedFieldGroups);
    }
    customer = repository.save(customer);

    // Auto-apply field groups
    var autoApplyIds = fieldGroupService.resolveAutoApplyGroupIds(EntityType.CUSTOMER);
    if (!autoApplyIds.isEmpty()) {
      var merged =
          new ArrayList<>(
              customer.getAppliedFieldGroups() != null
                  ? customer.getAppliedFieldGroups()
                  : List.of());
      for (UUID id : autoApplyIds) {
        if (!merged.contains(id)) {
          merged.add(id);
        }
      }
      customer.setAppliedFieldGroups(merged);
      customer = repository.save(customer);
    }

    log.info("Created customer {} with email {}", customer.getId(), email);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.created")
            .entityType("customer")
            .entityId(customer.getId())
            .details(Map.of("name", customer.getName(), "email", customer.getEmail()))
            .build());

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CustomerCreatedEvent(
            customer.getId(), customer.getName(), customer.getEmail(), orgId, tenantId));

    return customer;
  }

  @Transactional
  public Customer updateCustomer(
      UUID id, String name, String email, String phone, String idNumber, String notes) {
    return updateCustomer(id, name, email, phone, idNumber, notes, null, null);
  }

  @Transactional
  public Customer updateCustomer(
      UUID id,
      String name,
      String email,
      String phone,
      String idNumber,
      String notes,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {
    var customer =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));

    // Check email uniqueness if changed
    if (!customer.getEmail().equals(email) && repository.existsByEmail(email)) {
      throw new ResourceConflictException(
          "Customer email conflict", "A customer with email " + email + " already exists");
    }

    // Validate and set custom fields
    if (customFields != null) {
      Map<String, Object> validatedFields =
          customFieldValidator.validate(
              EntityType.CUSTOMER,
              customFields,
              appliedFieldGroups != null ? appliedFieldGroups : customer.getAppliedFieldGroups());
      customer.setCustomFields(validatedFields);
    }
    if (appliedFieldGroups != null) {
      customer.setAppliedFieldGroups(appliedFieldGroups);
    }

    // Capture old values before mutation
    String oldName = customer.getName();
    String oldEmail = customer.getEmail();
    String oldPhone = customer.getPhone();
    String oldIdNumber = customer.getIdNumber();
    String oldNotes = customer.getNotes();

    customer.update(name, email, phone, idNumber, notes);
    var saved = repository.save(customer);

    // Build delta map -- only include changed fields
    var details = new LinkedHashMap<String, Object>();
    if (!Objects.equals(oldName, name)) {
      details.put("name", Map.of("from", oldName, "to", name));
    }
    if (!Objects.equals(oldEmail, email)) {
      details.put("email", Map.of("from", oldEmail, "to", email));
    }
    if (!Objects.equals(oldPhone, phone)) {
      details.put("phone", Map.of("from", String.valueOf(oldPhone), "to", String.valueOf(phone)));
    }
    if (!Objects.equals(oldIdNumber, idNumber)) {
      details.put(
          "id_number", Map.of("from", String.valueOf(oldIdNumber), "to", String.valueOf(idNumber)));
    }
    if (!Objects.equals(oldNotes, notes)) {
      details.put("notes", Map.of("from", String.valueOf(oldNotes), "to", String.valueOf(notes)));
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.updated")
            .entityType("customer")
            .entityId(saved.getId())
            .details(details.isEmpty() ? null : details)
            .build());

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CustomerUpdatedEvent(
            saved.getId(), saved.getName(), saved.getEmail(), saved.getStatus(), orgId, tenantId));

    return saved;
  }

  @Transactional
  public List<FieldDefinitionResponse> setFieldGroups(UUID id, List<UUID> appliedFieldGroups) {
    var customer =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));

    // Validate all field groups exist and match entity type
    for (UUID groupId : appliedFieldGroups) {
      var group =
          fieldGroupRepository
              .findById(groupId)
              .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", groupId));
      if (group.getEntityType() != EntityType.CUSTOMER) {
        throw new InvalidStateException(
            "Invalid field group", "Field group " + groupId + " is not for entity type CUSTOMER");
      }
    }

    customer.setAppliedFieldGroups(appliedFieldGroups);
    repository.save(customer);

    // Collect field definition IDs from applied groups
    var fieldDefIds = new ArrayList<UUID>();
    for (UUID groupId : appliedFieldGroups) {
      var members = fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(groupId);
      for (var member : members) {
        fieldDefIds.add(member.getFieldDefinitionId());
      }
    }

    return fieldDefIds.stream()
        .distinct()
        .map(fdId -> fieldDefinitionRepository.findById(fdId))
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .map(FieldDefinitionResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Long> getLifecycleSummary() {
    var rows = repository.countByLifecycleStatus();
    var result = new LinkedHashMap<String, Long>();
    for (var row : rows) {
      result.put((String) row[0], ((Number) row[1]).longValue());
    }
    return result;
  }

  @Transactional
  public Customer archiveCustomer(UUID id) {
    var customer =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    customer.archive();

    // Align lifecycle: set to OFFBOARDED unless already in a terminal state
    if (customer.getLifecycleStatus() != LifecycleStatus.OFFBOARDING
        && customer.getLifecycleStatus() != LifecycleStatus.OFFBOARDED) {
      customer.setLifecycleStatus(
          LifecycleStatus.OFFBOARDED,
          RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null);
      customer.setOffboardedAt(Instant.now());
    }

    var saved = repository.save(customer);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.archived")
            .entityType("customer")
            .entityId(saved.getId())
            .details(Map.of("lifecycleStatus", saved.getLifecycleStatus().name()))
            .build());

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CustomerUpdatedEvent(
            saved.getId(), saved.getName(), saved.getEmail(), saved.getStatus(), orgId, tenantId));

    return saved;
  }

  @Transactional
  public Customer unarchiveCustomer(UUID id) {
    var customer =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));

    if (!"ARCHIVED".equals(customer.getStatus())) {
      throw new InvalidStateException(
          "Cannot unarchive",
          "Customer is not archived (current status: " + customer.getStatus() + ")");
    }

    customer.unarchive();
    customer.setLifecycleStatus(
        LifecycleStatus.DORMANT,
        RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null);
    customer.setOffboardedAt(null);
    var saved = repository.save(customer);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.unarchived")
            .entityType("customer")
            .entityId(saved.getId())
            .details(Map.of("lifecycleStatus", saved.getLifecycleStatus().name()))
            .build());

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CustomerUpdatedEvent(
            saved.getId(), saved.getName(), saved.getEmail(), saved.getStatus(), orgId, tenantId));

    return saved;
  }
}
