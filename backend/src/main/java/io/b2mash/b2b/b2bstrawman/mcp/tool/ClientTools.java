package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpCapabilityGuard;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpClientDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpClientListItem;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpMatterDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import io.b2mash.b2b.b2bstrawman.mcp.dto.ResolveMatterResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.project.Project;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only MCP tools over the firm's clients (customers): {@code list_clients} and {@code
 * get_client} (Epic 563A, §11.4).
 *
 * <p>Access model: <b>org-wide</b> (the whole authenticated tenant sees all clients). The live
 * MCP_ACCESS front-door gate is deferred to 565B, so no capability check is applied here.
 *
 * <p>{@code list_clients} deliberately OMITs the controller's tag/member-name enrichment ({@code
 * CustomerResponse.from}) — the list row is {@code {id, name, type, lifecycleStatus}} only, keeping
 * it token-efficient and avoiding the extra batch queries. Full detail (contacts + linked matters)
 * comes from {@code get_client}, where {@code linkedMatters} is resolved via a SEPARATE {@link
 * CustomerProjectService#listProjectsForCustomer(UUID, ActorContext)} call (not from {@code
 * getCustomer}).
 */
@Component
public class ClientTools {

  private static final String CAP_MCP_ACCESS = "MCP_ACCESS";

  private final CustomerService customerService;
  private final CustomerRepository customerRepository;
  private final CustomerProjectService customerProjectService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final McpEnablementService enablement;
  private final McpMetrics metrics;

  public ClientTools(
      CustomerService customerService,
      CustomerRepository customerRepository,
      CustomerProjectService customerProjectService,
      AuditService auditService,
      ObjectMapper objectMapper,
      McpEnablementService enablement,
      McpMetrics metrics) {
    this.customerService = customerService;
    this.customerRepository = customerRepository;
    this.customerProjectService = customerProjectService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.enablement = enablement;
    this.metrics = metrics;
  }

  @McpTool(
      name = "list_clients",
      description =
          "List the firm's clients (customers), org-wide. Optionally filter by lifecycleStatus"
              + " (PROSPECT, ONBOARDING, ACTIVE, DORMANT, OFFBOARDING, OFFBOARDED). Paginated —"
              + " page size is capped at 50.")
  public Object listClients(
      @McpToolParam(required = false, description = "Zero-based page index (default 0).")
          Integer page,
      @McpToolParam(required = false, description = "Page size, capped at 50 (default 50).")
          Integer size,
      @McpToolParam(
              required = false,
              description = "Filter by lifecycle status short enum name, e.g. ACTIVE.")
          String lifecycleStatus) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    long startNanos = System.nanoTime();
    LifecycleStatus parsed;
    try {
      // Normalize LLM input (lowercase/whitespace) before the strict enum parse.
      parsed =
          lifecycleStatus == null
              ? null
              : LifecycleStatus.valueOf(lifecycleStatus.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return McpToolErrors.asResult(
          McpError.invalidRequest("Unknown lifecycleStatus. See the tool description for values."),
          objectMapper);
    }

    var customers =
        (parsed == null
                ? customerService.listCustomers()
                : customerService.listCustomersByLifecycleStatus(parsed))
            .stream().map(McpClientListItem::from).toList();
    // Guard against an unbounded materialised result set: fail with a structured error rather than
    // ever emitting a truncated page when the full list exceeds the per-call ceiling.
    if (McpPagination.exceedsResponseCeiling(customers.size())) {
      return McpToolErrors.asResult(McpError.responseTooLarge(), objectMapper);
    }
    McpPage<McpClientListItem> pageResult =
        McpPagination.paginate(customers, page, size, McpPagination.DEFAULT_MAX_SIZE);
    var meta =
        McpAuditMetadata.builder()
            .rowCount(pageResult.items().size())
            .entityRefs(pageResult.items().stream().map(McpClientListItem::id).toList())
            .param("lifecycleStatus", parsed == null ? null : parsed.name())
            .build();
    emitInvoked("list_clients", meta, startNanos);
    return pageResult;
  }

  @McpTool(
      name = "get_client",
      description =
          "Fetch one client (customer) by id, including its contact details and the matters linked"
              + " to it. Returns a non-leaking not-found error if the client does not exist.")
  public Object getClient(@McpToolParam(description = "Client (customer) id.") UUID id) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    long startNanos = System.nanoTime();
    var actor = ActorContext.fromRequestScopes();
    try {
      var customer = customerService.getCustomer(id);
      // linkedMatters comes from a SEPARATE service call (drift: spec said CustomerService).
      var linkedProjects = customerProjectService.listProjectsForCustomer(id, actor);
      var dto = McpClientDto.from(customer, linkedProjects);
      var meta = McpAuditMetadata.builder().rowCount(1).entityRef(id).build();
      emitInvoked("get_client", meta, startNanos);
      return dto;
    } catch (ResourceNotFoundException | InvalidStateException e) {
      return McpToolErrors.asResult(McpError.notFound("client"), objectMapper);
    }
  }

  @McpTool(
      name = "resolve_matter_by_email",
      description =
          "Resolve an inbound correspondence email to the firm's client (customer) and ALL of that"
              + " client's matters. Pass the sender email; returns {customer, matters[]} so you can"
              + " pick the right matter before filing. Returns {customer:null, matters:[]} when no"
              + " client matches. subjectHint/reference are optional hints for YOUR disambiguation"
              + " only — Kazi does no server-side fuzzy matching.")
  public Object resolveMatterByEmail(
      @McpToolParam(description = "Sender email address to resolve to a client.") String email,
      @McpToolParam(
              required = false,
              description = "Optional email subject, a hint for your own disambiguation only.")
          String subjectHint,
      @McpToolParam(
              required = false,
              description =
                  "Optional reference/matter number, a hint for your own disambiguation" + " only.")
          String reference) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    return McpCapabilityGuard.gatedTool(
        CAP_MCP_ACCESS,
        "resolve_matter_by_email",
        auditService,
        metrics,
        objectMapper,
        startNanos -> {
          var actor = ActorContext.fromRequestScopes();
          Optional<Customer> customer =
              customerRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
          List<Project> projects =
              customer
                  .map(c -> customerProjectService.listProjectsForCustomer(c.getId(), actor))
                  .orElseGet(List::of);
          List<McpMatterDto> matters =
              projects.stream().map(p -> McpMatterDto.from(p, null)).toList();
          var response =
              new ResolveMatterResponse(
                  customer.map(c -> McpClientDto.from(c, projects)).orElse(null), matters);

          // entityRefs carries matter ids only — never the resolved email/customer name (POPIA).
          var meta =
              McpAuditMetadata.builder()
                  .rowCount(matters.size())
                  .entityRefs(matters.stream().map(McpMatterDto::id).toList())
                  .build();
          McpToolAudit.emitInvoked(
              "resolve_matter_by_email",
              meta,
              auditService,
              metrics,
              McpToolAudit.elapsed(startNanos));
          return response;
        });
  }

  private void emitInvoked(String tool, McpAuditMetadata meta, long startNanos) {
    McpToolAudit.emitInvoked(tool, meta, auditService, metrics, McpToolAudit.elapsed(startNanos));
  }
}
