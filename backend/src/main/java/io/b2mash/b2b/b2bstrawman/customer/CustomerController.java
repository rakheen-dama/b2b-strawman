package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService;
import io.b2mash.b2b.b2bstrawman.customer.dto.CustomerDtos.CreateCustomerRequest;
import io.b2mash.b2b.b2bstrawman.customer.dto.CustomerDtos.CustomerProjectResponse;
import io.b2mash.b2b.b2bstrawman.customer.dto.CustomerDtos.CustomerResponse;
import io.b2mash.b2b.b2bstrawman.customer.dto.CustomerDtos.DormancyCheckResult;
import io.b2mash.b2b.b2bstrawman.customer.dto.CustomerDtos.LinkedProjectResponse;
import io.b2mash.b2b.b2bstrawman.customer.dto.CustomerDtos.TransitionRequest;
import io.b2mash.b2b.b2bstrawman.customer.dto.CustomerDtos.TransitionResponse;
import io.b2mash.b2b.b2bstrawman.customer.dto.CustomerDtos.UpdateCustomerRequest;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UnbilledTimeResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactSummary;
import io.b2mash.b2b.b2bstrawman.setupstatus.AggregatedCompletenessResponse;
import io.b2mash.b2b.b2bstrawman.setupstatus.CompletenessScore;
import io.b2mash.b2b.b2bstrawman.setupstatus.CustomerReadiness;
import io.b2mash.b2b.b2bstrawman.setupstatus.CustomerReadinessService;
import io.b2mash.b2b.b2bstrawman.setupstatus.UnbilledTimeSummary;
import io.b2mash.b2b.b2bstrawman.setupstatus.UnbilledTimeSummaryService;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.tag.TagFilterUtil;
import io.b2mash.b2b.b2bstrawman.tag.dto.SetEntityTagsRequest;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.view.CustomFieldFilterUtil;
import io.b2mash.b2b.b2bstrawman.view.ViewFilterHelper;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
  private final ViewFilterHelper viewFilterHelper;
  private final CustomerLifecycleService customerLifecycleService;
  private final UnbilledTimeSummaryService unbilledTimeSummaryService;
  private final CustomerReadinessService customerReadinessService;
  private final PortalContactService portalContactService;

  public CustomerController(
      CustomerService customerService,
      CustomerProjectService customerProjectService,
      InvoiceService invoiceService,
      EntityTagService entityTagService,
      ViewFilterHelper viewFilterHelper,
      CustomerLifecycleService customerLifecycleService,
      UnbilledTimeSummaryService unbilledTimeSummaryService,
      CustomerReadinessService customerReadinessService,
      PortalContactService portalContactService) {
    this.customerService = customerService;
    this.customerProjectService = customerProjectService;
    this.invoiceService = invoiceService;
    this.entityTagService = entityTagService;
    this.viewFilterHelper = viewFilterHelper;
    this.customerLifecycleService = customerLifecycleService;
    this.unbilledTimeSummaryService = unbilledTimeSummaryService;
    this.customerReadinessService = customerReadinessService;
    this.portalContactService = portalContactService;
  }

  @GetMapping
  public ResponseEntity<List<CustomerResponse>> listCustomers(
      @RequestParam(required = false) UUID view,
      @RequestParam(required = false) LifecycleStatus lifecycleStatus,
      @RequestParam(required = false) Map<String, String> allParams) {

    // --- View-based filtering (server-side SQL) ---
    if (view != null) {
      List<Customer> filtered =
          viewFilterHelper.applyViewFilter(
              view, "CUSTOMER", "customers", Customer.class, null, null);

      if (filtered != null) {
        var customerIds = filtered.stream().map(Customer::getId).toList();
        var tagsByEntityId = entityTagService.getEntityTagsBatch("CUSTOMER", customerIds);
        var memberNames = customerService.resolveCustomerMemberNames(filtered);

        var responses =
            filtered.stream()
                .map(
                    c ->
                        CustomerResponse.from(
                            c, tagsByEntityId.getOrDefault(c.getId(), List.of()), memberNames))
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
    var memberNames = customerService.resolveCustomerMemberNames(customerEntities);

    var customers =
        customerEntities.stream()
            .map(
                c ->
                    CustomerResponse.from(
                        c, tagsByEntityId.getOrDefault(c.getId(), List.of()), memberNames))
            .toList();

    // Apply custom field filtering if present
    Map<String, String> customFieldFilters =
        CustomFieldFilterUtil.extractCustomFieldFilters(allParams);
    if (!customFieldFilters.isEmpty()) {
      customers =
          customers.stream()
              .filter(
                  c ->
                      CustomFieldFilterUtil.matchesCustomFieldFilters(
                          c.customFields(), customFieldFilters))
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
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<Map<String, Long>> getLifecycleSummary() {
    return ResponseEntity.ok(customerService.getLifecycleSummary());
  }

  @GetMapping("/completeness-summary")
  public ResponseEntity<Map<UUID, CompletenessScore>> getCompletenessSummary(
      @RequestParam List<UUID> customerIds) {
    return ResponseEntity.ok(customerReadinessService.batchComputeCompleteness(customerIds));
  }

  @GetMapping("/completeness-summary/aggregated")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<AggregatedCompletenessResponse> getAggregatedCompletenessSummary() {
    return ResponseEntity.ok(customerReadinessService.getAggregatedSummary(10));
  }

  @GetMapping("/{id}")
  public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID id) {
    var customer = customerService.getCustomer(id);
    var tags = entityTagService.getEntityTags("CUSTOMER", id);
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.ok(CustomerResponse.from(customer, tags, memberNames));
  }

  @PostMapping
  @RequiresCapability("CUSTOMER_MANAGEMENT")
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
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.created(URI.create("/api/customers/" + customer.getId()))
        .body(CustomerResponse.from(customer, List.of(), memberNames));
  }

  @PutMapping("/{id}")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
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
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.ok(CustomerResponse.from(customer, tags, memberNames));
  }

  @DeleteMapping("/{id}")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<CustomerResponse> archiveCustomer(@PathVariable UUID id) {
    var customer = customerService.archiveCustomer(id);
    var tags = entityTagService.getEntityTags("CUSTOMER", id);
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.ok(CustomerResponse.from(customer, tags, memberNames));
  }

  @PostMapping("/{id}/unarchive")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<CustomerResponse> unarchiveCustomer(@PathVariable UUID id) {
    var customer = customerService.unarchiveCustomer(id);
    var tags = entityTagService.getEntityTags("CUSTOMER", id);
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.ok(CustomerResponse.from(customer, tags, memberNames));
  }

  // --- Customer-Project linking endpoints ---

  @PostMapping("/{id}/projects/{projectId}")
  public ResponseEntity<CustomerProjectResponse> linkProject(
      @PathVariable UUID id, @PathVariable UUID projectId, ActorContext actor) {

    var link = customerProjectService.linkCustomerToProject(id, projectId, actor.memberId(), actor);
    return ResponseEntity.created(URI.create("/api/customers/" + id + "/projects/" + projectId))
        .body(CustomerProjectResponse.from(link));
  }

  @DeleteMapping("/{id}/projects/{projectId}")
  public ResponseEntity<Void> unlinkProject(
      @PathVariable UUID id, @PathVariable UUID projectId, ActorContext actor) {

    customerProjectService.unlinkCustomerFromProject(id, projectId, actor);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/projects")
  public ResponseEntity<List<LinkedProjectResponse>> listProjectsForCustomer(
      @PathVariable UUID id, ActorContext actor) {
    var projects = customerProjectService.listProjectsForCustomer(id, actor);
    return ResponseEntity.ok(projects.stream().map(LinkedProjectResponse::from).toList());
  }

  @GetMapping("/{id}/unbilled-summary")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<UnbilledTimeSummary> getUnbilledSummary(@PathVariable UUID id) {
    return ResponseEntity.ok(unbilledTimeSummaryService.getCustomerUnbilledSummary(id));
  }

  @GetMapping("/{id}/readiness")
  public ResponseEntity<CustomerReadiness> getReadiness(@PathVariable UUID id) {
    return ResponseEntity.ok(customerReadinessService.getReadiness(id));
  }

  @GetMapping("/{id}/unbilled-time")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<UnbilledTimeResponse> getUnbilledTime(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(invoiceService.getUnbilledTime(id, from, to));
  }

  @PutMapping("/{id}/field-groups")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<List<FieldDefinitionResponse>> setFieldGroups(
      @PathVariable UUID id, @Valid @RequestBody SetFieldGroupsRequest request) {
    var fieldDefs = customerService.setFieldGroups(id, request.appliedFieldGroups());
    return ResponseEntity.ok(fieldDefs);
  }

  @PostMapping("/{id}/tags")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<List<TagResponse>> setCustomerTags(
      @PathVariable UUID id, @Valid @RequestBody SetEntityTagsRequest request) {
    // Verify customer exists
    customerService.getCustomer(id);
    var tags = entityTagService.setEntityTags("CUSTOMER", id, request.tagIds());
    return ResponseEntity.ok(tags);
  }

  @GetMapping("/{id}/tags")
  public ResponseEntity<List<TagResponse>> getCustomerTags(@PathVariable UUID id) {
    // Verify customer exists
    customerService.getCustomer(id);
    var tags = entityTagService.getEntityTags("CUSTOMER", id);
    return ResponseEntity.ok(tags);
  }

  // --- Portal contacts endpoint ---

  @GetMapping("/{id}/portal-contacts")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<List<PortalContactSummary>> getPortalContacts(@PathVariable UUID id) {
    return ResponseEntity.ok(portalContactService.listPortalContactSummaries(id));
  }

  // --- Lifecycle endpoints ---

  @PostMapping("/{id}/transition")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<TransitionResponse> transitionLifecycle(
      @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
    UUID actorId = RequestScopes.requireMemberId();
    var customer =
        customerLifecycleService.transition(id, request.targetStatus(), request.notes(), actorId);
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.ok(TransitionResponse.from(customer, memberNames));
  }

  @GetMapping("/{id}/lifecycle")
  public ResponseEntity<List<AuditEvent>> getLifecycleHistory(@PathVariable UUID id) {
    customerService.getCustomer(id);
    var history = customerLifecycleService.getLifecycleHistory(id);
    return ResponseEntity.ok(history);
  }

  @PostMapping("/dormancy-check")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<DormancyCheckResult> runDormancyCheck() {
    return ResponseEntity.ok(customerLifecycleService.runDormancyCheck());
  }
}
