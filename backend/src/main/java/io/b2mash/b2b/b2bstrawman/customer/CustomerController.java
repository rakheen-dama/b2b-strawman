package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService;
import io.b2mash.b2b.b2bstrawman.customer.dto.ClientDtos.ClientMatterResponse;
import io.b2mash.b2b.b2bstrawman.customer.dto.ClientDtos.ClientResponse;
import io.b2mash.b2b.b2bstrawman.customer.dto.ClientDtos.CreateClientRequest;
import io.b2mash.b2b.b2bstrawman.customer.dto.ClientDtos.DormancyCheckResult;
import io.b2mash.b2b.b2bstrawman.customer.dto.ClientDtos.LinkedMatterResponse;
import io.b2mash.b2b.b2bstrawman.customer.dto.ClientDtos.TransitionRequest;
import io.b2mash.b2b.b2bstrawman.customer.dto.ClientDtos.TransitionResponse;
import io.b2mash.b2b.b2bstrawman.customer.dto.ClientDtos.UpdateClientRequest;
import io.b2mash.b2b.b2bstrawman.customer.dto.CollectionsExemptionRequest;
import io.b2mash.b2b.b2bstrawman.customer.dto.CollectionsExemptionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestService;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.FicaStatusResponse;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UnbilledTimeResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactSummary;
import io.b2mash.b2b.b2bstrawman.setupstatus.AggregatedCompletenessResponse;
import io.b2mash.b2b.b2bstrawman.setupstatus.ClientReadiness;
import io.b2mash.b2b.b2bstrawman.setupstatus.CompletenessScore;
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
@RequestMapping("/api/clients")
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
  private final InformationRequestService informationRequestService;

  public CustomerController(
      CustomerService customerService,
      CustomerProjectService customerProjectService,
      InvoiceService invoiceService,
      EntityTagService entityTagService,
      ViewFilterHelper viewFilterHelper,
      CustomerLifecycleService customerLifecycleService,
      UnbilledTimeSummaryService unbilledTimeSummaryService,
      CustomerReadinessService customerReadinessService,
      PortalContactService portalContactService,
      InformationRequestService informationRequestService) {
    this.customerService = customerService;
    this.customerProjectService = customerProjectService;
    this.invoiceService = invoiceService;
    this.entityTagService = entityTagService;
    this.viewFilterHelper = viewFilterHelper;
    this.customerLifecycleService = customerLifecycleService;
    this.unbilledTimeSummaryService = unbilledTimeSummaryService;
    this.customerReadinessService = customerReadinessService;
    this.portalContactService = portalContactService;
    this.informationRequestService = informationRequestService;
  }

  @GetMapping
  public ResponseEntity<List<ClientResponse>> listCustomers(
      @RequestParam(required = false) UUID view,
      @RequestParam(required = false) LifecycleStatus lifecycleStatus,
      @RequestParam(required = false) Map<String, String> allParams) {

    // --- View-based filtering (server-side SQL) ---
    if (view != null) {
      List<Customer> filtered =
          viewFilterHelper.applyViewFilter(
              // ADR-342: storage key, do not rename
              view, "CUSTOMER", "customers", Customer.class, null, null);

      if (filtered != null) {
        var customerIds = filtered.stream().map(Customer::getId).toList();
        // ADR-342: storage key, do not rename
        var tagsByEntityId = entityTagService.getEntityTagsBatch("CUSTOMER", customerIds);
        var memberNames = customerService.resolveCustomerMemberNames(filtered);

        var responses =
            filtered.stream()
                .map(
                    c ->
                        ClientResponse.from(
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
    // ADR-342: storage key, do not rename
    var tagsByEntityId = entityTagService.getEntityTagsBatch("CUSTOMER", customerIds);
    var memberNames = customerService.resolveCustomerMemberNames(customerEntities);

    var customers =
        customerEntities.stream()
            .map(
                c ->
                    ClientResponse.from(
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
      @RequestParam List<UUID> clientIds) {
    return ResponseEntity.ok(customerReadinessService.batchComputeCompleteness(clientIds));
  }

  @GetMapping("/completeness-summary/aggregated")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<AggregatedCompletenessResponse> getAggregatedCompletenessSummary() {
    return ResponseEntity.ok(customerReadinessService.getAggregatedSummary(10));
  }

  @GetMapping("/{clientId}")
  public ResponseEntity<ClientResponse> getCustomer(@PathVariable UUID clientId) {
    var customer = customerService.getCustomer(clientId);
    // ADR-342: storage key, do not rename
    var tags = entityTagService.getEntityTags("CUSTOMER", clientId);
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.ok(ClientResponse.from(customer, tags, memberNames));
  }

  @PostMapping
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<ClientResponse> createCustomer(
      @Valid @RequestBody CreateClientRequest request) {
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
            request.clientType(),
            request.registrationNumber(),
            request.addressLine1(),
            request.addressLine2(),
            request.city(),
            request.stateProvince(),
            request.postalCode(),
            request.country(),
            request.taxNumber(),
            request.contactName(),
            request.contactEmail(),
            request.contactPhone(),
            request.entityType(),
            request.financialYearEnd());
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.created(URI.create("/api/clients/" + customer.getId()))
        .body(ClientResponse.from(customer, List.of(), memberNames));
  }

  @PutMapping("/{clientId}")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<ClientResponse> updateCustomer(
      @PathVariable UUID clientId, @Valid @RequestBody UpdateClientRequest request) {
    var customer =
        customerService.updateCustomer(
            clientId,
            request.name(),
            request.email(),
            request.phone(),
            request.idNumber(),
            request.notes(),
            request.customFields(),
            request.appliedFieldGroups(),
            request.registrationNumber(),
            request.addressLine1(),
            request.addressLine2(),
            request.city(),
            request.stateProvince(),
            request.postalCode(),
            request.country(),
            request.taxNumber(),
            request.contactName(),
            request.contactEmail(),
            request.contactPhone(),
            request.entityType(),
            request.financialYearEnd());
    // ADR-342: storage key, do not rename
    var tags = entityTagService.getEntityTags("CUSTOMER", clientId);
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.ok(ClientResponse.from(customer, tags, memberNames));
  }

  @DeleteMapping("/{clientId}")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<ClientResponse> archiveCustomer(@PathVariable UUID clientId) {
    var customer = customerService.archiveCustomer(clientId);
    // ADR-342: storage key, do not rename
    var tags = entityTagService.getEntityTags("CUSTOMER", clientId);
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.ok(ClientResponse.from(customer, tags, memberNames));
  }

  @PostMapping("/{clientId}/unarchive")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<ClientResponse> unarchiveCustomer(@PathVariable UUID clientId) {
    var customer = customerService.unarchiveCustomer(clientId);
    // ADR-342: storage key, do not rename
    var tags = entityTagService.getEntityTags("CUSTOMER", clientId);
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.ok(ClientResponse.from(customer, tags, memberNames));
  }

  // --- Customer-Project linking endpoints ---

  @PostMapping("/{clientId}/matters/{matterId}")
  public ResponseEntity<ClientMatterResponse> linkProject(
      @PathVariable UUID clientId, @PathVariable UUID matterId, ActorContext actor) {

    var link =
        customerProjectService.linkCustomerToProject(clientId, matterId, actor.memberId(), actor);
    return ResponseEntity.created(URI.create("/api/clients/" + clientId + "/matters/" + matterId))
        .body(ClientMatterResponse.from(link));
  }

  @DeleteMapping("/{clientId}/matters/{matterId}")
  public ResponseEntity<Void> unlinkProject(
      @PathVariable UUID clientId, @PathVariable UUID matterId, ActorContext actor) {

    customerProjectService.unlinkCustomerFromProject(clientId, matterId, actor);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{clientId}/matters")
  public ResponseEntity<List<LinkedMatterResponse>> listProjectsForCustomer(
      @PathVariable UUID clientId, ActorContext actor) {
    var projects = customerProjectService.listProjectsForCustomer(clientId, actor);
    return ResponseEntity.ok(projects.stream().map(LinkedMatterResponse::from).toList());
  }

  @GetMapping("/{clientId}/unbilled-summary")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<UnbilledTimeSummary> getUnbilledSummary(@PathVariable UUID clientId) {
    return ResponseEntity.ok(unbilledTimeSummaryService.getCustomerUnbilledSummary(clientId));
  }

  @GetMapping("/{clientId}/readiness")
  public ResponseEntity<ClientReadiness> getReadiness(@PathVariable UUID clientId) {
    return ResponseEntity.ok(customerReadinessService.getReadiness(clientId));
  }

  /**
   * FICA onboarding status projection for a customer (GAP-L-46). Derived from the customer's {@code
   * fica-onboarding-pack} information requests. Returns {@code {status:
   * NOT_STARTED|IN_PROGRESS|DONE, lastVerifiedAt, requestId}}. Info-request-only signal — no
   * KYC-adapter coupling.
   */
  @GetMapping("/{clientId}/fica-status")
  public ResponseEntity<FicaStatusResponse> getFicaStatus(@PathVariable UUID clientId) {
    return ResponseEntity.ok(informationRequestService.getFicaStatus(clientId));
  }

  @GetMapping("/{clientId}/unbilled-time")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<UnbilledTimeResponse> getUnbilledTime(
      @PathVariable UUID clientId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(invoiceService.getUnbilledTime(clientId, from, to));
  }

  @PutMapping("/{clientId}/field-groups")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<List<FieldDefinitionResponse>> setFieldGroups(
      @PathVariable UUID clientId, @Valid @RequestBody SetFieldGroupsRequest request) {
    var fieldDefs = customerService.setFieldGroups(clientId, request.appliedFieldGroups());
    return ResponseEntity.ok(fieldDefs);
  }

  // Collections exemption (Phase 83, §2.3). CUSTOMER_MANAGEMENT gates the endpoint; the service
  // additionally enforces admin/owner-only (that capability is grantable to custom member roles).
  @PutMapping("/{clientId}/collections-exemption")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<CollectionsExemptionResponse> setCollectionsExemption(
      @PathVariable UUID clientId,
      @Valid @RequestBody CollectionsExemptionRequest request,
      ActorContext actor) {
    return ResponseEntity.ok(
        customerService.setCollectionsExemption(clientId, request.collectionsExempt(), actor));
  }

  @PostMapping("/{clientId}/tags")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<List<TagResponse>> setCustomerTags(
      @PathVariable UUID clientId, @Valid @RequestBody SetEntityTagsRequest request) {
    // Verify customer exists
    customerService.getCustomer(clientId);
    // ADR-342: storage key, do not rename
    var tags = entityTagService.setEntityTags("CUSTOMER", clientId, request.tagIds());
    return ResponseEntity.ok(tags);
  }

  @GetMapping("/{clientId}/tags")
  public ResponseEntity<List<TagResponse>> getCustomerTags(@PathVariable UUID clientId) {
    // Verify customer exists
    customerService.getCustomer(clientId);
    // ADR-342: storage key, do not rename
    var tags = entityTagService.getEntityTags("CUSTOMER", clientId);
    return ResponseEntity.ok(tags);
  }

  // --- Portal contacts endpoint ---

  @GetMapping("/{clientId}/portal-contacts")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<List<PortalContactSummary>> getPortalContacts(@PathVariable UUID clientId) {
    return ResponseEntity.ok(portalContactService.listPortalContactSummaries(clientId));
  }

  // --- Lifecycle endpoints ---

  @PostMapping("/{clientId}/transition")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<TransitionResponse> transitionLifecycle(
      @PathVariable UUID clientId, @Valid @RequestBody TransitionRequest request) {
    UUID actorId = RequestScopes.requireMemberId();
    var customer =
        customerLifecycleService.transition(
            clientId, request.targetStatus(), request.notes(), actorId);
    var memberNames = customerService.resolveCustomerMemberNames(List.of(customer));
    return ResponseEntity.ok(TransitionResponse.from(customer, memberNames));
  }

  @GetMapping("/{clientId}/lifecycle")
  public ResponseEntity<List<AuditEvent>> getLifecycleHistory(@PathVariable UUID clientId) {
    customerService.getCustomer(clientId);
    var history = customerLifecycleService.getLifecycleHistory(clientId);
    return ResponseEntity.ok(history);
  }

  @PostMapping("/dormancy-check")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<DormancyCheckResult> runDormancyCheck() {
    return ResponseEntity.ok(customerLifecycleService.runDormancyCheck());
  }
}
