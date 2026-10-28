package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceLineResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxCalculationService;
import io.b2mash.b2b.b2bstrawman.tax.dto.TaxBreakdownEntry;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffItem;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffItemRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Handles invoice rendering: entity-to-DTO conversion. Extracted from InvoiceService as a focused
 * collaborator. The HTML preview is the fee-note Tiptap template rendered by {@code
 * InvoiceService.renderPreview} (Phase 88 626E retired the separate Thymeleaf preview).
 */
@Service
public class InvoiceRenderingService {

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository lineRepository;
  private final ProjectRepository projectRepository;
  private final MemberNameResolver memberNameResolver;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TaxCalculationService taxCalculationService;
  private final TariffItemRepository tariffItemRepository;
  private final PaymentEventRepository paymentEventRepository;

  public InvoiceRenderingService(
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository lineRepository,
      ProjectRepository projectRepository,
      MemberNameResolver memberNameResolver,
      OrgSettingsRepository orgSettingsRepository,
      TaxCalculationService taxCalculationService,
      TariffItemRepository tariffItemRepository,
      PaymentEventRepository paymentEventRepository) {
    this.paymentEventRepository = paymentEventRepository;
    this.invoiceRepository = invoiceRepository;
    this.lineRepository = lineRepository;
    this.projectRepository = projectRepository;
    this.memberNameResolver = memberNameResolver;
    this.orgSettingsRepository = orgSettingsRepository;
    this.taxCalculationService = taxCalculationService;
    this.tariffItemRepository = tariffItemRepository;
  }

  InvoiceResponse buildResponse(Invoice invoice) {
    return buildResponse(invoice, List.of());
  }

  /**
   * Builds a response DTO and attaches the given non-blocking warning codes. Used by {@code
   * InvoiceCreationService.createDraft} to surface soft prerequisites (e.g. {@code
   * tax_number_missing}) without failing the request — see GAP-L-62.
   */
  InvoiceResponse buildResponse(Invoice invoice, List<String> warnings) {
    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
    var projectNames = resolveProjectNames(lines);
    var tariffItemNumbers = resolveTariffItemNumbers(lines);

    var lineResponses =
        lines.stream()
            .map(
                line ->
                    InvoiceLineResponse.from(
                        line,
                        line.getProjectId() != null ? projectNames.get(line.getProjectId()) : null,
                        line.getTariffItemId() != null
                            ? tariffItemNumbers.get(line.getTariffItemId())
                            : null))
            .toList();

    List<TaxBreakdownEntry> taxBreakdown = taxCalculationService.buildTaxBreakdown(lines);
    boolean hasPerLineTax = taxCalculationService.hasPerLineTax(lines);
    boolean taxInclusive =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(s -> s.getTax().isTaxInclusive())
            .orElse(false);

    // Phase 88 U15: payments[] lists the COMPLETED events, newest first (a draft has none).
    List<PaymentEvent> payments =
        invoice.getStatus() == InvoiceStatus.DRAFT
            ? List.of()
            : paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(invoice.getId()).stream()
                .filter(e -> e.getStatus() == PaymentEventStatus.COMPLETED)
                .toList();

    var memberNames = resolveMemberNames(invoice);
    return InvoiceResponse.from(
        invoice,
        lineResponses,
        memberNames,
        taxBreakdown,
        taxInclusive,
        hasPerLineTax,
        payments,
        warnings);
  }

  // --- Private helpers ---

  private Map<UUID, String> resolveProjectNames(List<InvoiceLine> lines) {
    var projectIds =
        lines.stream().map(InvoiceLine::getProjectId).filter(Objects::nonNull).distinct().toList();

    if (!projectIds.isEmpty()) {
      return projectRepository.findAllById(projectIds).stream()
          .collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));
    }
    return Map.of();
  }

  private Map<UUID, String> resolveTariffItemNumbers(List<InvoiceLine> lines) {
    var tariffItemIds =
        lines.stream()
            .map(InvoiceLine::getTariffItemId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    if (!tariffItemIds.isEmpty()) {
      return tariffItemRepository.findAllById(tariffItemIds).stream()
          .collect(Collectors.toMap(TariffItem::getId, TariffItem::getItemNumber));
    }
    return Map.of();
  }

  private Map<UUID, String> resolveMemberNames(Invoice invoice) {
    var ids =
        Stream.of(invoice.getCreatedBy(), invoice.getApprovedBy())
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    return memberNameResolver.resolveNames(ids);
  }
}
