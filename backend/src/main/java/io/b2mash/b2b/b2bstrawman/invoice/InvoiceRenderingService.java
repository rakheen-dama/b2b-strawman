package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceLineResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxCalculationService;
import io.b2mash.b2b.b2bstrawman.tax.dto.TaxBreakdownEntry;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffItem;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffItemRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Handles invoice rendering: entity-to-DTO conversion and Thymeleaf HTML preview generation.
 * Extracted from InvoiceService as a focused collaborator.
 */
@Service
public class InvoiceRenderingService {

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository lineRepository;
  private final ProjectRepository projectRepository;
  private final MemberNameResolver memberNameResolver;
  private final ITemplateEngine templateEngine;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TaxCalculationService taxCalculationService;
  private final TariffItemRepository tariffItemRepository;

  public InvoiceRenderingService(
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository lineRepository,
      ProjectRepository projectRepository,
      MemberNameResolver memberNameResolver,
      ITemplateEngine templateEngine,
      OrgSettingsRepository orgSettingsRepository,
      TaxCalculationService taxCalculationService,
      TariffItemRepository tariffItemRepository) {
    this.invoiceRepository = invoiceRepository;
    this.lineRepository = lineRepository;
    this.projectRepository = projectRepository;
    this.memberNameResolver = memberNameResolver;
    this.templateEngine = templateEngine;
    this.orgSettingsRepository = orgSettingsRepository;
    this.taxCalculationService = taxCalculationService;
    this.tariffItemRepository = tariffItemRepository;
  }

  @Transactional(readOnly = true)
  public String renderPreview(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    var projectNames = resolveProjectNames(lines);

    var groupedLines = new LinkedHashMap<String, List<InvoiceLine>>();
    var grouped =
        lines.stream()
            .collect(
                Collectors.groupingBy(
                    line -> {
                      if (line.getProjectId() == null) {
                        return "Other Items";
                      }
                      return projectNames.getOrDefault(line.getProjectId(), "Unknown Project");
                    },
                    LinkedHashMap::new,
                    Collectors.toList()));

    if (grouped.containsKey("Other Items")) {
      var otherItems = grouped.remove("Other Items");
      groupedLines.putAll(grouped);
      groupedLines.put("Other Items", otherItems);
    } else {
      groupedLines.putAll(grouped);
    }

    var groupSubtotals = new LinkedHashMap<String, BigDecimal>();
    for (var entry : groupedLines.entrySet()) {
      BigDecimal subtotal =
          entry.getValue().stream()
              .map(InvoiceLine::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      groupSubtotals.put(entry.getKey(), subtotal);
    }

    boolean hasPerLineTax = taxCalculationService.hasPerLineTax(lines);
    var taxBreakdown = taxCalculationService.buildTaxBreakdown(lines);

    var orgSettings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    String taxRegistrationNumber =
        orgSettings != null ? orgSettings.getTaxRegistrationNumber() : null;
    String taxRegistrationLabel =
        orgSettings != null ? orgSettings.getTaxRegistrationLabel() : "Tax Number";
    String taxLabel = orgSettings != null ? orgSettings.getTaxLabel() : "Tax";
    boolean taxInclusive = orgSettings != null && orgSettings.isTaxInclusive();

    Context ctx = new Context();
    ctx.setVariable("invoice", invoice);
    ctx.setVariable("groupedLines", groupedLines);
    ctx.setVariable("groupSubtotals", groupSubtotals);
    ctx.setVariable("hasPerLineTax", hasPerLineTax);
    ctx.setVariable("taxBreakdown", taxBreakdown);
    ctx.setVariable("taxRegistrationNumber", taxRegistrationNumber);
    ctx.setVariable("taxRegistrationLabel", taxRegistrationLabel);
    ctx.setVariable("taxLabel", taxLabel);
    ctx.setVariable("taxInclusive", taxInclusive);

    return templateEngine.process("invoice-preview", ctx);
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
        orgSettingsRepository.findForCurrentTenant().map(s -> s.isTaxInclusive()).orElse(false);

    var memberNames = resolveMemberNames(invoice);
    return InvoiceResponse.from(
        invoice, lineResponses, memberNames, taxBreakdown, taxInclusive, hasPerLineTax, warnings);
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
