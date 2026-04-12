package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.audit.AuditDeltaBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.exception.DeleteGuard;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupResolver;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupService;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreementRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final FieldGroupResolver fieldGroupResolver;
  private final FieldGroupService fieldGroupService;
  private final ProjectRepository projectRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final InvoiceRepository invoiceRepository;
  private final RetainerAgreementRepository retainerAgreementRepository;
  private final io.b2mash.b2b.b2bstrawman.member.MemberNameResolver memberNameResolver;
  private final ChecklistInstanceService checklistInstanceService;

  public CustomerService(
      CustomerRepository repository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      CustomFieldValidator customFieldValidator,
      FieldDefinitionRepository fieldDefinitionRepository,
      FieldGroupResolver fieldGroupResolver,
      FieldGroupService fieldGroupService,
      ProjectRepository projectRepository,
      CustomerProjectRepository customerProjectRepository,
      InvoiceRepository invoiceRepository,
      RetainerAgreementRepository retainerAgreementRepository,
      io.b2mash.b2b.b2bstrawman.member.MemberNameResolver memberNameResolver,
      ChecklistInstanceService checklistInstanceService) {
    this.repository = repository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.customFieldValidator = customFieldValidator;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.fieldGroupResolver = fieldGroupResolver;
    this.fieldGroupService = fieldGroupService;
    this.projectRepository = projectRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.invoiceRepository = invoiceRepository;
    this.retainerAgreementRepository = retainerAgreementRepository;
    this.memberNameResolver = memberNameResolver;
    this.checklistInstanceService = checklistInstanceService;
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
    return createCustomer(
        name,
        email,
        phone,
        idNumber,
        notes,
        createdBy,
        customFields,
        appliedFieldGroups,
        customerType,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
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
      CustomerType customerType,
      String registrationNumber,
      String addressLine1,
      String addressLine2,
      String city,
      String stateProvince,
      String postalCode,
      String country,
      String taxNumber,
      String contactName,
      String contactEmail,
      String contactPhone,
      String entityType,
      java.time.LocalDate financialYearEnd) {
    if (repository.existsByEmail(email)) {
      throw new ResourceConflictException(
          "Customer email conflict", "A customer with email " + email + " already exists");
    }

    // Reject unknown field slugs before validation
    if (customFields != null && !customFields.isEmpty()) {
      var activeDefinitions =
          fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
              EntityType.CUSTOMER);
      var knownSlugs =
          activeDefinitions.stream().map(FieldDefinition::getSlug).collect(Collectors.toSet());
      for (String slug : customFields.keySet()) {
        if (!knownSlugs.contains(slug)) {
          throw new InvalidStateException(
              "Unknown custom field",
              "Field slug '" + slug + "' does not exist for entity type CUSTOMER");
        }
      }
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

    // Set structural fields (Phase 63 graduated fields)
    customer.setRegistrationNumber(registrationNumber);
    customer.setAddressLine1(addressLine1);
    customer.setAddressLine2(addressLine2);
    customer.setCity(city);
    customer.setStateProvince(stateProvince);
    customer.setPostalCode(postalCode);
    customer.setCountry(country);
    customer.setTaxNumber(taxNumber);
    customer.setContactName(contactName);
    customer.setContactEmail(contactEmail);
    customer.setContactPhone(contactPhone);
    customer.setEntityType(entityType);
    customer.setFinancialYearEnd(financialYearEnd);

    // Auto-apply field groups before save so audit events capture final state
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
    }
    customer = repository.save(customer);

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
    return updateCustomer(
        id,
        name,
        email,
        phone,
        idNumber,
        notes,
        customFields,
        appliedFieldGroups,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
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
      List<UUID> appliedFieldGroups,
      String registrationNumber,
      String addressLine1,
      String addressLine2,
      String city,
      String stateProvince,
      String postalCode,
      String country,
      String taxNumber,
      String contactName,
      String contactEmail,
      String contactPhone,
      String entityType,
      java.time.LocalDate financialYearEnd) {
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
    String oldRegistrationNumber = customer.getRegistrationNumber();
    String oldAddressLine1 = customer.getAddressLine1();
    String oldAddressLine2 = customer.getAddressLine2();
    String oldCity = customer.getCity();
    String oldStateProvince = customer.getStateProvince();
    String oldPostalCode = customer.getPostalCode();
    String oldCountry = customer.getCountry();
    String oldTaxNumber = customer.getTaxNumber();
    String oldContactName = customer.getContactName();
    String oldContactEmail = customer.getContactEmail();
    String oldContactPhone = customer.getContactPhone();
    String oldEntityType = customer.getEntityType();
    java.time.LocalDate oldFinancialYearEnd = customer.getFinancialYearEnd();

    customer.update(name, email, phone, idNumber, notes);

    // Set structural fields (Phase 63 graduated fields)
    customer.setRegistrationNumber(registrationNumber);
    customer.setAddressLine1(addressLine1);
    customer.setAddressLine2(addressLine2);
    customer.setCity(city);
    customer.setStateProvince(stateProvince);
    customer.setPostalCode(postalCode);
    customer.setCountry(country);
    customer.setTaxNumber(taxNumber);
    customer.setContactName(contactName);
    customer.setContactEmail(contactEmail);
    customer.setContactPhone(contactPhone);
    customer.setEntityType(entityType);
    customer.setFinancialYearEnd(financialYearEnd);

    var saved = repository.save(customer);

    var details =
        new AuditDeltaBuilder()
            .track("name", oldName, name)
            .track("email", oldEmail, email)
            .trackAsString("phone", oldPhone, phone)
            .trackAsString("id_number", oldIdNumber, idNumber)
            .trackAsString("notes", oldNotes, notes)
            .trackAsString("registration_number", oldRegistrationNumber, registrationNumber)
            .trackAsString("address_line1", oldAddressLine1, addressLine1)
            .trackAsString("address_line2", oldAddressLine2, addressLine2)
            .trackAsString("city", oldCity, city)
            .trackAsString("state_province", oldStateProvince, stateProvince)
            .trackAsString("postal_code", oldPostalCode, postalCode)
            .trackAsString("country", oldCountry, country)
            .trackAsString("tax_number", oldTaxNumber, taxNumber)
            .trackAsString("contact_name", oldContactName, contactName)
            .trackAsString("contact_email", oldContactEmail, contactEmail)
            .trackAsString("contact_phone", oldContactPhone, contactPhone)
            .trackAsString("entity_type", oldEntityType, entityType)
            .trackAsString("financial_year_end", oldFinancialYearEnd, financialYearEnd)
            .build();

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.updated")
            .entityType("customer")
            .entityId(saved.getId())
            .details(details)
            .build());

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CustomerUpdatedEvent(
            saved.getId(), saved.getName(), saved.getEmail(), saved.getStatus(), orgId, tenantId));

    // GAP-D2-02: Re-check lifecycle auto-transition when an ONBOARDING customer is updated.
    // If checklists were already complete but a prerequisite field (e.g. tax_number) was missing,
    // adding it via Edit should trigger the ONBOARDING -> ACTIVE transition.
    if (saved.getLifecycleStatus() == LifecycleStatus.ONBOARDING) {
      UUID actorId =
          RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : saved.getCreatedBy();
      checklistInstanceService.recheckLifecycleAdvance(saved.getId(), actorId);
    }

    return saved;
  }

  @Transactional
  public List<FieldDefinitionResponse> setFieldGroups(UUID id, List<UUID> appliedFieldGroups) {
    var customer =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));

    appliedFieldGroups =
        fieldGroupResolver.resolveAndValidate(appliedFieldGroups, EntityType.CUSTOMER);

    customer.setAppliedFieldGroups(appliedFieldGroups);
    repository.save(customer);

    return fieldGroupResolver.collectFieldDefinitions(appliedFieldGroups);
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

    DeleteGuard.forEntity("customer", id, "archive")
        .checkNotExists(
            "linked projects",
            () ->
                projectRepository.countByCustomerId(id) > 0
                    || customerProjectRepository.existsByCustomerId(id),
            "Unlink all projects first.")
        .checkCountZero(
            "invoice(s)",
            invoiceRepository.countByCustomerId(id),
            "Void or delete all invoices first.")
        .checkCountZero(
            "retainer agreement(s)",
            retainerAgreementRepository.countByCustomerId(id),
            "Cancel or delete all retainers first.")
        .execute();

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

  // --- Name Resolution (moved from controller for BE-007) ---

  /**
   * Batch-loads member names for all createdBy and lifecycleStatusChangedBy IDs referenced by the
   * given customers.
   */
  public Map<UUID, String> resolveCustomerMemberNames(List<Customer> customers) {
    var ids =
        customers.stream()
            .flatMap(
                c -> java.util.stream.Stream.of(c.getCreatedBy(), c.getLifecycleStatusChangedBy()))
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();

    if (ids.isEmpty()) {
      return Map.of();
    }

    return memberNameResolver.resolveNames(ids);
  }
}
