package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ModuleNotEnabledException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpCapabilityGuard;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpTrustBalanceDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpTrustTransactionItem;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService.ClientHistoryFilter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
 * McpError#moduleDisabled(String)} rather than a stack trace. An unknown trust account ({@link
 * ResourceNotFoundException} from {@link TrustBooksService#position} or, on the client branch,
 * {@link TrustAccountService#getTrustAccount}) is a clean {@link McpError#notFound(String)} (646D).
 *
 * <p>Money: the trust DTOs carry {@link java.math.BigDecimal} major units with no currency; the MCP
 * boundary carries minor units (long) + a currency code resolved from {@link OrgSettingsService}.
 */
@Component
public class TrustTools {

  private static final String CAP_VIEW_TRUST = "VIEW_TRUST";
  private static final String TRUST_MODULE = "trust accounting";

  private final ClientLedgerService clientLedgerService;
  private final TrustBooksService trustBooksService;
  private final TrustAccountService trustAccountService;
  private final OrgSettingsService orgSettingsService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final McpEnablementService enablement;
  private final McpMetrics metrics;
  private final Clock clock;

  public TrustTools(
      ClientLedgerService clientLedgerService,
      TrustBooksService trustBooksService,
      TrustAccountService trustAccountService,
      OrgSettingsService orgSettingsService,
      AuditService auditService,
      ObjectMapper objectMapper,
      McpEnablementService enablement,
      McpMetrics metrics,
      Clock clock) {
    this.clientLedgerService = clientLedgerService;
    this.trustBooksService = trustBooksService;
    this.trustAccountService = trustAccountService;
    this.orgSettingsService = orgSettingsService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.enablement = enablement;
    this.metrics = metrics;
    this.clock = clock;
  }

  @McpTool(
      name = "get_trust_balance",
      description =
          "Get a trust balance for a trust account. With a customerId, returns that client's"
              + " general trust balance on the account in balanceMinor and, in investedMinor, what"
              + " is held for the client in section 86(4) investments placed from that account —"
              + " the client's total in trust is the sum. Without one, returns the"
              + " account's trust position as at today (asAt): balanceMinor is the total of the"
              + " client ledgers on the account, and beside it investedMinor (section 86(4)"
              + " investments held for clients), fundLedgerMinor (Fund interest and charges),"
              + " unidentifiedDepositsMinor, totalTrustCreditorsMinor (every trust creditor) and"
              + " cashbookMinor (what the bank should hold for the account); the books balance"
              + " when cashbookMinor equals totalTrustCreditorsMinor. Money is returned as minor"
              + " units (e.g. cents) plus a currency code. Requires the VIEW_TRUST capability and"
              + " the trust-accounting module — a firm without trust accounting receives a clean"
              + " module_disabled error.")
  public Object getTrustBalance(
      @McpToolParam(description = "Trust account id.") UUID trustAccountId,
      @McpToolParam(
              required = false,
              description = "Client (customer) id — omit for the account's trust position.")
          UUID customerId) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    return McpCapabilityGuard.gatedTool(
        CAP_VIEW_TRUST,
        "get_trust_balance",
        auditService,
        metrics,
        objectMapper,
        startNanos -> {
          try {
            String currency = orgSettingsService.getDefaultCurrency();
            McpTrustBalanceDto dto;
            if (customerId != null) {
              // the ledger read checks only the client; an unknown account must not read as zero
              trustAccountService.getTrustAccount(trustAccountId);
              var card = clientLedgerService.getClientLedger(customerId, trustAccountId);
              dto =
                  new McpTrustBalanceDto(
                      trustAccountId,
                      customerId,
                      minor(card.balance()),
                      currency,
                      // 648E: the client's section 86(4) invested balance placed from the account
                      minor(card.investedBalance()),
                      null,
                      null,
                      null,
                      null,
                      null);
            } else {
              // EQ-2's terms as at today (§16.6.5, 646D); balanceMinor keeps its meaning
              var position = trustBooksService.position(trustAccountId, LocalDate.now(clock));
              dto =
                  new McpTrustBalanceDto(
                      trustAccountId,
                      null,
                      minor(position.clientLedgers()),
                      currency,
                      minor(position.investmentCreditors()),
                      minor(position.fundLedger()),
                      minor(position.unidentifiedDeposits()),
                      minor(position.creditorTotal()),
                      minor(position.cashbook()),
                      position.asAt());
            }
            var meta =
                McpAuditMetadata.builder()
                    .rowCount(1)
                    .entityRef(trustAccountId)
                    .entityRef(customerId)
                    .build();
            McpToolAudit.emitInvoked(
                "get_trust_balance", meta, auditService, metrics, McpToolAudit.elapsed(startNanos));
            return dto;
          } catch (ModuleNotEnabledException e) {
            return McpToolErrors.asResult(McpError.moduleDisabled(TRUST_MODULE), objectMapper);
          } catch (ResourceNotFoundException e) {
            // with a client the miss may be either id; the non-leaking message must not say which
            String noun = customerId != null ? "trust account or client" : "trust account";
            return McpToolErrors.asResult(McpError.notFound(noun), objectMapper);
          }
        });
  }

  private static long minor(BigDecimal amount) {
    return amount.movePointRight(2).longValueExact();
  }

  @McpTool(
      name = "list_trust_transactions",
      description =
          "List a client's trust transactions on a trust account, newest first. Money is returned"
              + " as minor units plus a currency code. Paginated — page size capped at 50. Requires"
              + " the VIEW_TRUST capability and the trust-accounting module. On a trust banking"
              + " account this includes a section 86(4) placement and its return; on an investment"
              + " account, the client's investment rows (ledger CLIENT_INVESTMENT).")
  public Object listTrustTransactions(
      @McpToolParam(description = "Client (customer) id.") UUID customerId,
      @McpToolParam(description = "Trust account id.") UUID trustAccountId,
      @McpToolParam(required = false, description = "Zero-based page index (default 0).")
          Integer page,
      @McpToolParam(required = false, description = "Page size, capped at 50 (default 50).")
          Integer size) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    return McpCapabilityGuard.gatedTool(
        CAP_VIEW_TRUST,
        "list_trust_transactions",
        auditService,
        metrics,
        objectMapper,
        startNanos -> {
          int clampedSize = McpPagination.clampSize(size, McpPagination.DEFAULT_MAX_SIZE);
          int clampedPage = Math.max(page, 0);
          try {
            String currency = orgSettingsService.getDefaultCurrency();
            var txPage =
                clientLedgerService.getClientTransactionHistory(
                    customerId,
                    trustAccountId,
                    ClientHistoryFilter.NONE,
                    PageRequest.of(clampedPage, clampedSize));
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
            var meta =
                McpAuditMetadata.builder()
                    .rowCount(items.size())
                    .entityRef(trustAccountId)
                    .entityRef(customerId)
                    .build();
            McpToolAudit.emitInvoked(
                "list_trust_transactions",
                meta,
                auditService,
                metrics,
                McpToolAudit.elapsed(startNanos));
            return result;
          } catch (ModuleNotEnabledException e) {
            return McpToolErrors.asResult(McpError.moduleDisabled(TRUST_MODULE), objectMapper);
          }
        });
  }
}
