package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.audit.AuditDeltaBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.customer.dto.ClientListRow;
import io.b2mash.b2b.b2bstrawman.customer.dto.CollectionsExemptionResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.exception.DeleteGuard;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
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
import io.b2mash.b2b.b2bstrawman.informationrequest.FicaStatusProjector;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestService;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.FicaStatusResponse;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.setupstatus.CompletenessScore;
import io.b2mash.b2b.b2bstrawman.setupstatus.CustomerReadinessService;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.tag.TagFilterUtil;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.view.CustomFieldFilterUtil;
import io.b2mash.b2b.b2bstrawman.view.ViewFilterHelper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  // Module id and ficaStatus filter values read by the Clients list projection (Epic 615B).
  private static final String TRUST_ACCOUNTING_MODULE = "trust_accounting";
  private static final String FICA_FILTER_OUTSTANDING = "OUTSTANDING";
  private static final String FICA_FILTER_DONE = "DONE";

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
  private final io.b2mash.b2b.b2bstrawman.member.MemberNameResolver memberNameResolver;
  private final ChecklistInstanceService checklistInstanceService;
  private final ViewFilterHelper viewFilterHelper;
  private final EntityTagService entityTagService;
  private final InformationRequestService informationRequestService;
  private final CustomerReadinessService customerReadinessService;
  private final ClientListRowRepository clientListRowRepository;
  private final ClientLedgerCardRepository clientLedgerCardRepository;
  private final OrgSettingsRepository orgSettingsRepository;

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
      io.b2mash.b2b.b2bstrawman.member.MemberNameResolver memberNameResolver,
      ChecklistInstanceService checklistInstanceService,
      ViewFilterHelper viewFilterHelper,
      EntityTagService entityTagService,
      InformationRequestService informationRequestService,
      CustomerReadinessService customerReadinessService,
      ClientListRowRepository clientListRowRepository,
      ClientLedgerCardRepository clientLedgerCardRepository,
      OrgSettingsRepository orgSettingsRepository) {
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
    this.memberNameResolver = memberNameResolver;
    this.checklistInstanceService = checklistInstanceService;
    this.viewFilterHelper = viewFilterHelper;
    this.entityTagService = entityTagService;
    this.informationRequestService = informationRequestService;
    this.customerReadinessService = customerReadinessService;
    this.clientListRowRepository = clientListRowRepository;
    this.clientLedgerCardRepository = clientLedgerCardRepository;
    this.orgSettingsRepository = orgSettingsRepository;
  }

  @Transactional(readOnly = true)
  public List<Customer> listCustomers() {
    return repository.findAll();
  }

  @Transactional(readOnly = true)
  public List<Customer> listCustomersByLifecycleStatus(LifecycleStatus lifecycleStatus) {
    return repository.findByLifecycleStatus(lifecycleStatus);
  }

  // --- Clients list projection (Phase 87, Epic 615B — arch §12.2.6, §12.3.4) ---

  /**
   * Base rows plus whether a saved view produced them (custom-field/tag params apply otherwise).
   */
  private record ClientBase(List<Customer> clients, boolean viewApplied) {}

  /**
   * {@code GET /api/clients} rows. The filtering that used to live in {@code
   * CustomerController.listCustomers} moved here with unchanged semantics: a saved view (ADR-055;
   * lifecycleStatus and the custom-field/tag params are ignored on that path, exactly as before),
   * else all clients or those in {@code lifecycleStatus}, then the custom-field and tag params. The
   * new params apply on both paths, after the view: {@code hasTrustBalance}, {@code
   * showIncomplete}, {@code ficaStatus}. The surviving rows are enriched by batched aggregates
   * merged in Java. Cheap filters run before the aggregate fan-out; tags run last because they need
   * the tag batch.
   *
   * <p>Statement budget on the default path (no view): base 1 + org settings 1 + trust 0–1 +
   * completeness field definitions 1 + FICA 2–3 (onboarding checklist instances 1 + FICA requests 1
   * + items of COMPLETED requests 0–1) + client aggregates 1 + tags 1–2 = at most 10, independent
   * of page size (pinned by {@code ClientListRowIntegrationTest}). A saved view adds 1 (the view
   * lookup; the view query replaces the base query).
   *
   * <p>Redaction: {@code outstandingFeeNoteTotal} needs FINANCIAL_VISIBILITY (owner/admin always —
   * the 614A home rule); {@code trustBalance} needs VIEW_TRUST and the trust_accounting module.
   * Counts and currency are never redacted. {@code hasTrustBalance} without trust visibility
   * returns no rows (filtering by a redacted column would leak it).
   *
   * @throws InvalidStateException (400) when {@code ficaStatus} is not OUTSTANDING or DONE
   */
  @Transactional(readOnly = true)
  public List<ClientListRow> listClientRows(ClientListQuery q, ActorContext actor) {
    String ficaFilter = parseFicaFilter(q.ficaStatus()); // 400 before any query
    ClientBase base = selectBase(q);
    List<Customer> clients = base.clients();

    // custom-field params: non-view path only, exactly as the controller did
    if (!base.viewApplied()) {
      Map<String, String> customFieldFilters =
          CustomFieldFilterUtil.extractCustomFieldFilters(q.allParams());
      if (!customFieldFilters.isEmpty()) {
        clients =
            clients.stream()
                .filter(
                    c ->
                        CustomFieldFilterUtil.matchesCustomFieldFilters(
                            c.getCustomFields(), customFieldFilters))
                .toList();
      }
    }
    if (clients.isEmpty()) {
      return List.of();
    }

    // ONE settings read for currency + module flags (getDefaultCurrency()/isModuleEnabled() each
    // re-run the settings query).
    Optional<OrgSettings> settings = orgSettingsRepository.findForCurrentTenant();
    String currency =
        settings.map(OrgSettings::getDefaultCurrency).orElse(OrgSettingsService.DEFAULT_CURRENCY);
    Set<String> modules = settings.map(s -> Set.copyOf(s.getEnabledModules())).orElse(Set.of());
    boolean seesFeeNoteTotal =
        actor.isOwnerOrAdmin() || RequestScopes.hasCapability("FINANCIAL_VISIBILITY");
    boolean seesTrust =
        modules.contains(TRUST_ACCOUNTING_MODULE) && RequestScopes.hasCapability("VIEW_TRUST");

    // hasTrustBalance: a caller who cannot see trust cannot filter by it — no row qualifies
    if (q.hasTrustBalance() && !seesTrust) {
      return List.of();
    }
    Map<UUID, BigDecimal> trust = seesTrust ? trustByClient(idsOf(clients)) : Map.of();
    if (q.hasTrustBalance()) {
      clients =
          clients.stream()
              .filter(c -> trust.getOrDefault(c.getId(), BigDecimal.ZERO).signum() > 0)
              .toList();
    }

    Map<UUID, CompletenessScore> completeness =
        customerReadinessService.computeCompleteness(clients);
    if (q.showIncomplete()) {
      clients = clients.stream().filter(c -> isIncomplete(completeness.get(c.getId()))).toList();
    }

    Map<UUID, FicaStatusResponse> fica = informationRequestService.getFicaStatuses(idsOf(clients));
    if (ficaFilter != null) {
      clients = clients.stream().filter(c -> matchesFica(fica.get(c.getId()), ficaFilter)).toList();
    }
    if (clients.isEmpty()) {
      return List.of();
    }

    List<UUID> ids = idsOf(clients);
    Map<UUID, ClientListAggregateProjection> aggregates = new HashMap<>();
    for (var a : clientListRowRepository.findAggregates(ids, currency)) {
      aggregates.put(a.getClientId(), a);
    }
    // ADR-342: storage key, do not rename
    var tagsByEntityId = entityTagService.getEntityTagsBatch("CUSTOMER", ids);

    List<ClientListRow> rows =
        clients.stream()
            .map(
                c ->
                    toRow(
                        c,
                        aggregates.get(c.getId()),
                        fica.get(c.getId()),
                        completeness.get(c.getId()),
                        trust,
                        tagsByEntityId.getOrDefault(c.getId(), List.of()),
                        currency,
                        seesFeeNoteTotal,
                        seesTrust))
            .toList();

    // tag params: non-view path only, exactly as the controller did (needs the tag batch above)
    if (!base.viewApplied()) {
      List<String> tagSlugs = TagFilterUtil.extractTagSlugs(q.allParams());
      if (!tagSlugs.isEmpty()) {
        rows =
            rows.stream().filter(r -> TagFilterUtil.matchesTagFilter(r.tags(), tagSlugs)).toList();
      }
    }
    return rows;
  }

  /**
   * A saved view (ADR-055, ViewFilterHelper untouched; no member scoping for clients), else all
   * clients or those in {@code lifecycleStatus}. A view with no WHERE clause falls back.
   */
  private ClientBase selectBase(ClientListQuery q) {
    if (q.view() != null) {
      List<Customer> filtered =
          viewFilterHelper.applyViewFilter(
              // ADR-342: storage key, do not rename
              q.view(), "CUSTOMER", "customers", Customer.class, null, null);
      if (filtered != null) {
        return new ClientBase(filtered, true);
      }
    }
    return new ClientBase(
        q.lifecycleStatus() != null
            ? repository.findByLifecycleStatus(q.lifecycleStatus())
            : repository.findAll(),
        false);
  }

  /** {@code OUTSTANDING} / {@code DONE} (case-insensitive), null when absent; else a 400. */
  private static String parseFicaFilter(String ficaStatus) {
    if (ficaStatus == null || ficaStatus.isBlank()) {
      return null;
    }
    String normalized = ficaStatus.trim().toUpperCase(Locale.ROOT);
    if (FICA_FILTER_OUTSTANDING.equals(normalized) || FICA_FILTER_DONE.equals(normalized)) {
      return normalized;
    }
    throw new InvalidStateException(
        "Invalid FICA status filter",
        "Invalid ficaStatus: '"
            + ficaStatus
            + "'. Valid values: "
            + FICA_FILTER_OUTSTANDING
            + ", "
            + FICA_FILTER_DONE);
  }

  /** DONE = the FICA rule's DONE; OUTSTANDING = anything else (NOT_STARTED or IN_PROGRESS). */
  private static boolean matchesFica(FicaStatusResponse fica, String filter) {
    boolean done = fica != null && FicaStatusProjector.DONE.equals(fica.status());
    return FICA_FILTER_DONE.equals(filter) == done;
  }

  /** Below 100% with at least one required field; clients with none are N/A, never incomplete. */
  private static boolean isIncomplete(CompletenessScore score) {
    return score != null && score.totalRequired() > 0 && score.percentage() < 100;
  }

  private static List<UUID> idsOf(List<Customer> clients) {
    return clients.stream().map(Customer::getId).toList();
  }

  private Map<UUID, BigDecimal> trustByClient(Collection<UUID> clientIds) {
    if (clientIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, BigDecimal> balances = new HashMap<>();
    for (var row : clientLedgerCardRepository.sumBalancesByCustomerIdIn(clientIds)) {
      balances.put(row.getCustomerId(), row.getBalance());
    }
    return balances;
  }

  private static ClientListRow toRow(
      Customer c,
      ClientListAggregateProjection agg,
      FicaStatusResponse fica,
      CompletenessScore completeness,
      Map<UUID, BigDecimal> trust,
      List<TagResponse> tags,
      String orgCurrency,
      boolean seesFeeNoteTotal,
      boolean seesTrust) {
    BigDecimal outstanding = null;
    if (seesFeeNoteTotal) {
      outstanding =
          (agg == null || agg.getOutstandingFeeNoteTotal() == null
                  ? BigDecimal.ZERO
                  : agg.getOutstandingFeeNoteTotal())
              .setScale(2, RoundingMode.HALF_UP);
    }
    return new ClientListRow(
        c.getId(),
        c.getName(),
        c.getEmail(),
        c.getPhone(),
        c.getCustomerType(),
        c.getLifecycleStatus(),
        c.getStatus(),
        c.getCreatedAt(),
        c.getLifecycleStatusChangedAt(),
        fica == null ? FicaStatusProjector.NOT_STARTED : fica.status(),
        fica == null ? null : fica.lastVerifiedAt(),
        outstanding,
        agg == null || agg.getOutstandingFeeNoteCount() == null
            ? 0
            : agg.getOutstandingFeeNoteCount().intValue(),
        agg != null && agg.getFeeNoteCurrency() != null ? agg.getFeeNoteCurrency() : orgCurrency,
        seesTrust ? trust.getOrDefault(c.getId(), BigDecimal.ZERO) : null,
        agg == null || agg.getOpenMatterCount() == null ? 0 : agg.getOpenMatterCount().intValue(),
        completeness == null || completeness.totalRequired() == 0
            ? null
            : completeness.percentage(),
        tags,
        c.getCustomFields() != null ? c.getCustomFields() : Map.of());
  }

  @Transactional(readOnly = true)
  public Customer getCustomer(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.CLIENT, id));
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
    // OBS-5004: pass entity type value so groups scoped to an entity type (e.g. TRUST) are only
    // auto-applied to customers of that entity type.
    var autoApplyIds =
        fieldGroupService.resolveAutoApplyGroupIds(EntityType.CUSTOMER, null, entityType);
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
            // ADR-342: storage key, do not rename
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
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.CLIENT, id));

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
            // ADR-342: storage key, do not rename
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
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.CLIENT, id));

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

  /**
   * Sets or clears the per-customer collections exemption (Phase 83, §2.3). Admin/owner only per
   * architecture §9 — enforced here in addition to the controller's {@code CUSTOMER_MANAGEMENT}
   * capability, because that capability is grantable to custom member roles.
   *
   * <p>No audit event: the architecture registers no audit type for exemption changes (the 41-entry
   * catalogue pin is deliberate); the flag is visible on the customer row.
   */
  @Transactional
  public CollectionsExemptionResponse setCollectionsExemption(
      UUID customerId, boolean exempt, ActorContext actor) {
    if (!actor.isOwnerOrAdmin()) {
      throw new ForbiddenException(
          "Insufficient permissions", "Only admins and owners can change collections exemption");
    }
    var customer =
        repository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.CLIENT, customerId));
    customer.setCollectionsExempt(exempt);
    var saved = repository.save(customer);
    return new CollectionsExemptionResponse(saved.getId(), saved.isCollectionsExempt());
  }

  @Transactional
  public Customer archiveCustomer(UUID id) {
    var customer =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.CLIENT, id));

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
            // ADR-342: storage key, do not rename
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
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.CLIENT, id));

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
            // ADR-342: storage key, do not rename
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
