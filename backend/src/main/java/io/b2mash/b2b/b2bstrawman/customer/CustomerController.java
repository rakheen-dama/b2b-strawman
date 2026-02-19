package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UnbilledTimeResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.setupstatus.CustomerReadiness;
import io.b2mash.b2b.b2bstrawman.setupstatus.CustomerReadinessService;
import io.b2mash.b2b.b2bstrawman.setupstatus.UnbilledTimeSummary;
import io.b2mash.b2b.b2bstrawman.setupstatus.UnbilledTimeSummaryService;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.tag.TagFilterUtil;
import io.b2mash.b2b.b2bstrawman.tag.dto.SetEntityTagsRequest;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.view.SavedViewRepository;
import io.b2mash.b2b.b2bstrawman.view.ViewFilterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

  private final CustomerService customerService;
  private final CustomerProjectService customerProjectService;
  private final InvoiceService invoiceService;
  private final EntityTagService entityTagService;
  private final SavedViewRepository savedViewRepository;
  private final ViewFilterService viewFilterService;
  private final CustomerLifecycleService customerLifecycleService;
  private final UnbilledTimeSummaryService unbilledTimeSummaryService;
  private final CustomerReadinessService customerReadinessService;

  public CustomerController(
      CustomerService customerService,
      CustomerProjectService customerProjectService,
      InvoiceService invoiceService,
      EntityTagService entityTagService,
      SavedViewRepository savedViewRepository,
      ViewFilterService viewFilterService,
      CustomerLifecycleService customerLifecycleService,
      UnbilledTimeSummaryService unbilledTimeSummaryService,
      CustomerReadinessService customerReadinessService) {
    this.customerService = customerService;
    this.customerProjectService = customerProjectService;
    this.invoiceService = invoiceService;
    this.entityTagService = entityTagService;
    this.savedViewRepository = savedViewRepository;
    this.viewFilterService = viewFilterService;
    this.customerLifecycleService = customerLifecycleService;
    this.unbilledTimeSummaryService = unbilledTimeSummaryService;
    this.customerReadinessService = customerReadinessService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<CustomerResponse>> listCustomers(
      @RequestParam(required = false) UUID view,
      @RequestParam(required = false) LifecycleStatus lifecycleStatus,
      @RequestParam(required = false) Map<String, String> allParams) {

    // --- View-based filtering (server-side SQL) ---
    if (view != null) {
      var savedView =
          savedViewRepository
              .findById(view)
              .orElseThrow(() -> new ResourceNotFoundException("SavedView", view));

      if (!"CUSTOMER".equals(savedView.getEntityType())) {
        throw new InvalidStateException(
            "View type mismatch", "Expected CUSTOMER view but got " + savedView.getEntityType());
      }

      List<Customer> filtered =
          viewFilterService.executeFilterQuery(
              "customers", Customer.class, savedView.getFilters(), "CUSTOMER");

      if (filtered != null) {
        var customerIds = filtered.stream().map(Customer::getId).toList();
        var tagsByEntityId = entityTagService.getEntityTagsBatch("CUSTOMER", customerIds);

        var responses =
            filtered.stream()
                .map(
                    c ->
                        CustomerResponse.from(c, tagsByEntityId.getOrDefault(c.getId(), List.of())))
                .toList();
        return ResponseEntity.ok(responses);
      }
    }

    // --- Fallback: existing in-memory filtering ---
    var customerEntities =
        lifecycleStatus != null
            ? customerService.listCustomersByLifecycleStatus(lifecycleStatus)
            : customerService.listCustomers();

    // Batch-load tags for all customers (2 queries instead of 2N)
    var customerIds = customerEntities.stream().map(Customer::getId).toList();
    var tagsByEntityId = entityTagService.getEntityTagsBatch("CUSTOMER", customerIds);

    var customers =
        customerEntities.stream()
            .map(c -> CustomerResponse.from(c, tagsByEntityId.getOrDefault(c.getId(), List.of())))
            .toList();

    // Apply custom field filtering if present
    Map<String, String> customFieldFilters = extractCustomFieldFilters(allParams);
    if (!customFieldFilters.isEmpty()) {
      customers =
          customers.stream()
              .filter(c -> matchesCustomFieldFilters(c.customFields(), customFieldFilters))
              .toList();
    }

    // Apply tag filtering if present
    List<String> tagSlugs = TagFilterUtil.extractTagSlugs(allParams);
    if (!tagSlugs.isEmpty()) {
      customers =
          customers.stream()
              .filter(c -> TagFilterUtil.matchesTagFilter(c.tags(), tagSlugs))
              .toList();
    }

    return ResponseEntity.ok(customers);
  }

  @GetMapping("/lifecycle-summary")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Map<String, Long>> getLifecycleSummary() {
    return ResponseEntity.ok(customerService.getLifecycleSummary());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID id) {
    var customer = customerService.getCustomer(id);
    var tags = entityTagService.getEntityTags("CUSTOMER", id);
    return ResponseEntity.ok(CustomerResponse.from(customer, tags));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerResponse> createCustomer(
      @Valid @RequestBody CreateCustomerRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    var customer =
        customerService.createCustomer(
            request.name(),
            request.email(),
            request.phone(),
            request.idNumber(),
            request.notes(),
            createdBy,
            request.customFields(),
            request.appliedFieldGroups(),
            request.customerType());
    return ResponseEntity.created(URI.create("/api/customers/" + customer.getId()))
        .body(CustomerResponse.from(customer, List.of()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerResponse> updateCustomer(
      @PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
    var customer =
        customerService.updateCustomer(
            id,
            request.name(),
            request.email(),
            request.phone(),
            request.idNumber(),
            request.notes(),
            request.customFields(),
            request.appliedFieldGroups());
    var tags = entityTagService.getEntityTags("CUSTOMER", id);
    return ResponseEntity.ok(CustomerResponse.from(customer, tags));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerResponse> archiveCustomer(@PathVariable UUID id) {
    var customer = customerService.archiveCustomer(id);
    var tags = entityTagService.getEntityTags("CUSTOMER", id);
    return ResponseEntity.ok(CustomerResponse.from(customer, tags));
  }

  // --- Customer-Project linking endpoints ---

  @PostMapping("/{id}/projects/{projectId}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerProjectResponse> linkProject(
      @PathVariable UUID id, @PathVariable UUID projectId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var link =
        customerProjectService.linkCustomerToProject(id, projectId, memberId, memberId, orgRole);
    return ResponseEntity.created(URI.create("/api/customers/" + id + "/projects/" + projectId))
        .body(CustomerProjectResponse.from(link));
  }

  @DeleteMapping("/{id}/projects/{projectId}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> unlinkProject(@PathVariable UUID id, @PathVariable UUID projectId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    customerProjectService.unlinkCustomerFromProject(id, projectId, memberId, orgRole);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/projects")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<LinkedProjectResponse>> listProjectsForCustomer(
      @PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var projects = customerProjectService.listProjectsForCustomer(id, memberId, orgRole);
    return ResponseEntity.ok(projects.stream().map(LinkedProjectResponse::from).toList());
  }

  @GetMapping("/{id}/unbilled-summary")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<UnbilledTimeSummary> getUnbilledSummary(@PathVariable UUID id) {
    return ResponseEntity.ok(unbilledTimeSummaryService.getCustomerUnbilledSummary(id));
  }

  @GetMapping("/{id}/readiness")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<CustomerReadiness> getReadiness(@PathVariable UUID id) {
    return ResponseEntity.ok(customerReadinessService.getReadiness(id));
  }

  @GetMapping("/{id}/unbilled-time")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<UnbilledTimeResponse> getUnbilledTime(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(invoiceService.getUnbilledTime(id, from, to));
  }

  @PutMapping("/{id}/field-groups")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<FieldDefinitionResponse>> setFieldGroups(
      @PathVariable UUID id, @Valid @RequestBody SetFieldGroupsRequest request) {
    var fieldDefs = customerService.setFieldGroups(id, request.appliedFieldGroups());
    return ResponseEntity.ok(fieldDefs);
  }

  @PostMapping("/{id}/tags")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TagResponse>> setCustomerTags(
      @PathVariable UUID id, @Valid @RequestBody SetEntityTagsRequest request) {
    // Verify customer exists
    customerService.getCustomer(id);
    var tags = entityTagService.setEntityTags("CUSTOMER", id, request.tagIds());
    return ResponseEntity.ok(tags);
  }

  @GetMapping("/{id}/tags")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TagResponse>> getCustomerTags(@PathVariable UUID id) {
    // Verify customer exists
    customerService.getCustomer(id);
    var tags = entityTagService.getEntityTags("CUSTOMER", id);
    return ResponseEntity.ok(tags);
  }

  // --- Lifecycle endpoints ---

  @PostMapping("/{id}/transition")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TransitionResponse> transitionLifecycle(
      @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
    UUID actorId = RequestScopes.requireMemberId();
    var customer =
        customerLifecycleService.transition(id, request.targetStatus(), request.notes(), actorId);
    return ResponseEntity.ok(TransitionResponse.from(customer));
  }

  @GetMapping("/{id}/lifecycle")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<AuditEvent>> getLifecycleHistory(@PathVariable UUID id) {
    customerService.getCustomer(id);
    var history = customerLifecycleService.getLifecycleHistory(id);
    return ResponseEntity.ok(history);
  }

  @PostMapping("/dormancy-check")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<DormancyCheckResult> runDormancyCheck() {
    return ResponseEntity.ok(customerLifecycleService.runDormancyCheck());
  }

  private Map<String, String> extractCustomFieldFilters(Map<String, String> allParams) {
    var filters = new HashMap<String, String>();
    if (allParams != null) {
      allParams.forEach(
          (key, value) -> {
            if (key.startsWith("customField[") && key.endsWith("]")) {
              String slug = key.substring("customField[".length(), key.length() - 1);
              filters.put(slug, value);
            }
          });
    }
    return filters;
  }

  private boolean matchesCustomFieldFilters(
      Map<String, Object> customFields, Map<String, String> filters) {
    if (customFields == null) {
      return filters.isEmpty();
    }
    for (var entry : filters.entrySet()) {
      Object fieldValue = customFields.get(entry.getKey());
      if (fieldValue == null || !fieldValue.toString().equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  // --- DTOs ---

  public record CreateCustomerRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @NotBlank(message = "email is required")
          @Email(message = "email must be a valid email address")
          @Size(max = 255, message = "email must be at most 255 characters")
          String email,
      @Size(max = 50, message = "phone must be at most 50 characters") String phone,
      @Size(max = 100, message = "idNumber must be at most 100 characters") String idNumber,
      String notes,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      CustomerType customerType) {}

  public record UpdateCustomerRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @NotBlank(message = "email is required")
          @Email(message = "email must be a valid email address")
          @Size(max = 255, message = "email must be at most 255 characters")
          String email,
      @Size(max = 50, message = "phone must be at most 50 characters") String phone,
      @Size(max = 100, message = "idNumber must be at most 100 characters") String idNumber,
      String notes,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {}

  public record CustomerResponse(
      UUID id,
      String name,
      String email,
      String phone,
      String idNumber,
      String status,
      String notes,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      List<TagResponse> tags,
      LifecycleStatus lifecycleStatus,
      CustomerType customerType,
      Instant lifecycleStatusChangedAt) {

    public static CustomerResponse from(Customer customer) {
      return new CustomerResponse(
          customer.getId(),
          customer.getName(),
          customer.getEmail(),
          customer.getPhone(),
          customer.getIdNumber(),
          customer.getStatus(),
          customer.getNotes(),
          customer.getCreatedBy(),
          customer.getCreatedAt(),
          customer.getUpdatedAt(),
          customer.getCustomFields(),
          customer.getAppliedFieldGroups(),
          List.of(),
          customer.getLifecycleStatus(),
          customer.getCustomerType(),
          customer.getLifecycleStatusChangedAt());
    }

    public static CustomerResponse from(Customer customer, List<TagResponse> tags) {
      return new CustomerResponse(
          customer.getId(),
          customer.getName(),
          customer.getEmail(),
          customer.getPhone(),
          customer.getIdNumber(),
          customer.getStatus(),
          customer.getNotes(),
          customer.getCreatedBy(),
          customer.getCreatedAt(),
          customer.getUpdatedAt(),
          customer.getCustomFields(),
          customer.getAppliedFieldGroups(),
          tags,
          customer.getLifecycleStatus(),
          customer.getCustomerType(),
          customer.getLifecycleStatusChangedAt());
    }
  }

  public record CustomerProjectResponse(
      UUID customerId, UUID projectId, UUID linkedBy, Instant createdAt) {

    public static CustomerProjectResponse from(CustomerProject link) {
      return new CustomerProjectResponse(
          link.getCustomerId(), link.getProjectId(), link.getLinkedBy(), link.getCreatedAt());
    }
  }

  public record LinkedProjectResponse(UUID id, String name, String description, Instant createdAt) {

    public static LinkedProjectResponse from(Project project) {
      return new LinkedProjectResponse(
          project.getId(), project.getName(), project.getDescription(), project.getCreatedAt());
    }
  }

  // --- Lifecycle DTOs ---

  public record TransitionRequest(@NotBlank String targetStatus, String notes) {}

  public record TransitionResponse(
      UUID id,
      String name,
      String lifecycleStatus,
      Instant lifecycleStatusChangedAt,
      UUID lifecycleStatusChangedBy) {

    public static TransitionResponse from(Customer customer) {
      return new TransitionResponse(
          customer.getId(),
          customer.getName(),
          customer.getLifecycleStatus().name(),
          customer.getLifecycleStatusChangedAt(),
          customer.getLifecycleStatusChangedBy());
    }
  }

  public record DormancyCheckResult(int thresholdDays, List<DormancyCandidate> candidates) {}

  public record DormancyCandidate(
      UUID customerId, String customerName, Instant lastActivityDate, long daysSinceActivity) {}
}
