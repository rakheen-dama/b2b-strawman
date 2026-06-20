package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.UnbilledTimeService;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpCapabilityGuard;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpInvoiceDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpUnbilledSummaryItem;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpUnbilledSummaryItem.McpUnbilledMatterSummary;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.setupstatus.UnbilledTimeSummaryService;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only billing MCP tools (Epic 564A): {@code list_invoices}, {@code get_invoice} and {@code
 * get_unbilled_time}. All three require the {@code INVOICING} capability, checked inline and
 * returned as {@link McpError#forbidden()} (never thrown — throwing would leak as "Error invoking
 * method"). Money is carried as minor units (long); invoice/unbilled DTOs already carry a currency.
 */
@Component
public class BillingTools {

  private static final String CAP_INVOICING = "INVOICING";

  private final InvoiceService invoiceService;
  private final UnbilledTimeService unbilledTimeService;
  private final UnbilledTimeSummaryService unbilledTimeSummaryService;
  private final OrgSettingsService orgSettingsService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final McpEnablementService enablement;
  private final McpMetrics metrics;

  public BillingTools(
      InvoiceService invoiceService,
      UnbilledTimeService unbilledTimeService,
      UnbilledTimeSummaryService unbilledTimeSummaryService,
      OrgSettingsService orgSettingsService,
      AuditService auditService,
      ObjectMapper objectMapper,
      McpEnablementService enablement,
      McpMetrics metrics) {
    this.invoiceService = invoiceService;
    this.unbilledTimeService = unbilledTimeService;
    this.unbilledTimeSummaryService = unbilledTimeSummaryService;
    this.orgSettingsService = orgSettingsService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.enablement = enablement;
    this.metrics = metrics;
  }

  @McpTool(
      name = "list_invoices",
      description =
          "List the firm's invoices, optionally filtered by clientId, status (DRAFT, APPROVED,"
              + " SENT, PAID, VOID) and/or matterId. Money is returned as minor units plus a"
              + " currency code. Paginated — page size capped at 50. Requires the INVOICING"
              + " capability.")
  public Object listInvoices(
      @McpToolParam(required = false, description = "Filter by client (customer) id.")
          UUID customerId,
      @McpToolParam(
              required = false,
              description = "Filter by status: DRAFT, APPROVED, SENT, PAID, VOID.")
          String status,
      @McpToolParam(required = false, description = "Filter by matter (project) id.")
          UUID projectId,
      @McpToolParam(required = false, description = "Zero-based page index (default 0).")
          Integer page,
      @McpToolParam(required = false, description = "Page size, capped at 50 (default 50).")
          Integer size) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    return McpCapabilityGuard.gatedTool(
        CAP_INVOICING,
        "list_invoices",
        auditService,
        metrics,
        objectMapper,
        startNanos -> {
          InvoiceStatus parsed;
          try {
            parsed =
                status == null
                    ? null
                    : InvoiceStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
          } catch (IllegalArgumentException e) {
            return McpToolErrors.asResult(
                McpError.invalidRequest("Unknown status. See the tool description for values."),
                objectMapper);
          }

          var invoices =
              invoiceService.findAll(customerId, parsed, projectId).stream()
                  .map(McpInvoiceDto::listItem)
                  .toList();
          if (McpPagination.exceedsResponseCeiling(invoices.size())) {
            return McpToolErrors.asResult(McpError.responseTooLarge(), objectMapper);
          }
          McpPage<McpInvoiceDto> result =
              McpPagination.paginate(invoices, page, size, McpPagination.DEFAULT_MAX_SIZE);
          var meta =
              McpAuditMetadata.builder()
                  .rowCount(result.items().size())
                  .param("customerId", customerId)
                  .param("status", parsed == null ? null : parsed.name())
                  .param("projectId", projectId)
                  .build();
          McpToolAudit.emitInvoked(
              "list_invoices", meta, auditService, metrics, McpToolAudit.elapsed(startNanos));
          return result;
        });
  }

  @McpTool(
      name = "get_invoice",
      description =
          "Fetch one invoice by id, including its line items and payment events. Line items and"
              + " payments are capped at 50 each; the 'truncated' flag is set when either was"
              + " clipped. Money is returned as minor units plus a currency code. Returns a"
              + " non-leaking not-found error if the invoice does not exist. Requires the INVOICING"
              + " capability.")
  public Object getInvoice(@McpToolParam(description = "Invoice id.") UUID invoiceId) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    return McpCapabilityGuard.gatedTool(
        CAP_INVOICING,
        "get_invoice",
        auditService,
        metrics,
        objectMapper,
        startNanos -> {
          try {
            var invoice = invoiceService.findById(invoiceId);
            var payments = invoiceService.getPaymentEvents(invoiceId);
            var dto = McpInvoiceDto.detail(invoice, payments, McpPagination.DEFAULT_MAX_SIZE);
            var meta = McpAuditMetadata.builder().rowCount(1).entityRef(invoiceId).build();
            McpToolAudit.emitInvoked(
                "get_invoice", meta, auditService, metrics, McpToolAudit.elapsed(startNanos));
            return dto;
          } catch (ResourceNotFoundException e) {
            return McpToolErrors.asResult(McpError.notFound("invoice"), objectMapper);
          }
        });
  }

  @McpTool(
      name = "get_unbilled_time",
      description =
          "Summarise unbilled work. Without a matterId, returns a firm-wide list of up to the first"
              + " 50 clients with unbilled work (optionally bounded by periodFrom/periodTo and"
              + " currency); if `truncated` is true, narrow the result using the period/currency"
              + " filters. With a matterId, returns a single per-matter unbilled summary. Money is"
              + " returned as minor units plus a currency code. Requires the INVOICING capability.")
  public Object getUnbilledTime(
      @McpToolParam(required = false, description = "Period start (inclusive), ISO date.")
          LocalDate periodFrom,
      @McpToolParam(required = false, description = "Period end (exclusive), ISO date.")
          LocalDate periodTo,
      @McpToolParam(required = false, description = "Currency code filter (3-letter).")
          String currency,
      @McpToolParam(
              required = false,
              description = "Matter (project) id — switches to the per-matter summary.")
          UUID projectId) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    return McpCapabilityGuard.gatedTool(
        CAP_INVOICING,
        "get_unbilled_time",
        auditService,
        metrics,
        objectMapper,
        startNanos -> {
          if (projectId != null) {
            try {
              var summary = unbilledTimeSummaryService.getProjectUnbilledSummary(projectId);
              var meta = McpAuditMetadata.builder().rowCount(1).entityRef(projectId).build();
              McpToolAudit.emitInvoked(
                  "get_unbilled_time",
                  meta,
                  auditService,
                  metrics,
                  McpToolAudit.elapsed(startNanos));
              return McpUnbilledMatterSummary.from(projectId, summary);
            } catch (ResourceNotFoundException e) {
              return McpToolErrors.asResult(McpError.notFound("matter"), objectMapper);
            }
          }

          String resolvedCurrency =
              (currency == null || currency.isBlank())
                  ? orgSettingsService.getDefaultCurrency()
                  : currency.trim().toUpperCase(Locale.ROOT);
          var rows =
              unbilledTimeService
                  .getUnbilledSummary(periodFrom, periodTo, resolvedCurrency)
                  .stream()
                  .map(row -> McpUnbilledSummaryItem.from(row, resolvedCurrency))
                  .toList();
          if (McpPagination.exceedsResponseCeiling(rows.size())) {
            return McpToolErrors.asResult(McpError.responseTooLarge(), objectMapper);
          }
          McpPage<McpUnbilledSummaryItem> result =
              McpPagination.paginate(
                  rows, 0, McpPagination.DEFAULT_MAX_SIZE, McpPagination.DEFAULT_MAX_SIZE);
          var meta =
              McpAuditMetadata.builder()
                  .rowCount(result.items().size())
                  .param("currency", currency)
                  .build();
          McpToolAudit.emitInvoked(
              "get_unbilled_time", meta, auditService, metrics, McpToolAudit.elapsed(startNanos));
          return result;
        });
  }
}
