package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ModuleNotEnabledException;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpTrustBalanceDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpTrustTransactionItem;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only trust-accounting MCP tools (Epic 564A): {@code get_trust_balance} and {@code
 * list_trust_transactions}. Both require the {@code VIEW_TRUST} capability AND the {@code
 * trust_accounting} vertical module. The capability gate is checked inline and {@link
 * McpError#forbidden()} returned (never thrown — throwing would leak as "Error invoking method").
 * For a non-legal tenant the underlying {@link ClientLedgerService} throws {@link
 * ModuleNotEnabledException}; we catch it and return a clean {@link
 * McpError#moduleDisabled(String)} rather than a stack trace.
 *
 * <p>Money: the trust DTOs carry {@link java.math.BigDecimal} major units with no currency; the MCP
 * boundary carries minor units (long) + a currency code resolved from {@link OrgSettingsService}.
 */
@Component
public class TrustTools {

  private static final String CAP_VIEW_TRUST = "VIEW_TRUST";
  private static final String TRUST_MODULE = "trust accounting";

  private final ClientLedgerService clientLedgerService;
  private final OrgSettingsService orgSettingsService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public TrustTools(
      ClientLedgerService clientLedgerService,
      OrgSettingsService orgSettingsService,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.clientLedgerService = clientLedgerService;
    this.orgSettingsService = orgSettingsService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @McpTool(
      name = "get_trust_balance",
      description =
          "Get a trust balance for a trust account. If a clientId is supplied, returns that"
              + " client's ledger-card balance on the account; otherwise returns the account's"
              + " total trust balance. Money is returned as minor units (e.g. cents) plus a"
              + " currency code. Requires the VIEW_TRUST capability and the trust-accounting module"
              + " — a firm without trust accounting receives a clean module_disabled error.")
  public Object getTrustBalance(
      @McpToolParam(description = "Trust account id.") UUID trustAccountId,
      @McpToolParam(
              required = false,
              description = "Client (customer) id — omit for the account total.")
          UUID customerId) {
    if (!RequestScopes.hasCapability(CAP_VIEW_TRUST)) {
      McpToolAudit.emitDenied("get_trust_balance", auditService);
      return McpToolErrors.asResult(McpError.forbidden(), objectMapper);
    }
    try {
      String currency = orgSettingsService.getDefaultCurrency();
      McpTrustBalanceDto dto;
      if (customerId != null) {
        var card = clientLedgerService.getClientLedger(customerId, trustAccountId);
        dto =
            new McpTrustBalanceDto(
                trustAccountId,
                customerId,
                card.balance().movePointRight(2).longValueExact(),
                currency);
      } else {
        var total = clientLedgerService.getTotalTrustBalance(trustAccountId);
        dto =
            new McpTrustBalanceDto(
                trustAccountId, null, total.balance().movePointRight(2).longValueExact(), currency);
      }
      McpToolAudit.emitInvoked("get_trust_balance", auditService);
      return dto;
    } catch (ModuleNotEnabledException e) {
      return McpToolErrors.asResult(McpError.moduleDisabled(TRUST_MODULE), objectMapper);
    }
  }

  @McpTool(
      name = "list_trust_transactions",
      description =
          "List a client's trust transactions on a trust account, newest first. Money is returned"
              + " as minor units plus a currency code. Paginated — page size capped at 50. Requires"
              + " the VIEW_TRUST capability and the trust-accounting module.")
  public Object listTrustTransactions(
      @McpToolParam(description = "Client (customer) id.") UUID customerId,
      @McpToolParam(description = "Trust account id.") UUID trustAccountId,
      @McpToolParam(required = false, description = "Zero-based page index (default 0).") int page,
      @McpToolParam(required = false, description = "Page size, capped at 50 (default 50).")
          int size) {
    if (!RequestScopes.hasCapability(CAP_VIEW_TRUST)) {
      McpToolAudit.emitDenied("list_trust_transactions", auditService);
      return McpToolErrors.asResult(McpError.forbidden(), objectMapper);
    }
    int clampedSize = McpPagination.clampSize(size, McpPagination.DEFAULT_MAX_SIZE);
    int clampedPage = Math.max(page, 0);
    try {
      String currency = orgSettingsService.getDefaultCurrency();
      var txPage =
          clientLedgerService.getClientTransactionHistory(
              customerId, trustAccountId, PageRequest.of(clampedPage, clampedSize));
      var items =
          txPage.getContent().stream()
              .map(tx -> McpTrustTransactionItem.from(tx, currency))
              .toList();
      McpPage<McpTrustTransactionItem> result =
          McpPage.of(
              items,
              txPage.getNumber(),
              txPage.getSize(),
              txPage.getTotalElements(),
              txPage.hasNext());
      McpToolAudit.emitInvoked("list_trust_transactions", auditService);
      return result;
    } catch (ModuleNotEnabledException e) {
      return McpToolErrors.asResult(McpError.moduleDisabled(TRUST_MODULE), objectMapper);
    }
  }
}
