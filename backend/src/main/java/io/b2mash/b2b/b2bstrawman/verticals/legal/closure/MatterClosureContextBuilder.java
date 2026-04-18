package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateContextHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementApprovalStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementBillingStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Assembles the rendering context for the {@code matter-closure-letter} Tiptap template (Phase 67,
 * Epic 489B, task 489.15). Layers closure-specific {@code closure.*} and {@code matter.*} variables
 * on top of the usual {@code project.*}, {@code customer.*}, {@code org.*} shape.
 *
 * <p>This class is a standalone utility — it does NOT implement {@code TemplateContextBuilder} (the
 * auto-dispatched PDF pipeline handles project-entity context via {@code ProjectContextBuilder}).
 * It is kept as an explicit component for preview/testing of the closure-specific variable shape,
 * and as a single source of truth for the "fees billed / disbursements / duration" aggregates
 * referenced by architecture §67.9.1.
 */
@Component
public class MatterClosureContextBuilder {

  private static final Set<InvoiceStatus> BILLED_INVOICE_STATUSES =
      Set.of(InvoiceStatus.SENT, InvoiceStatus.PAID);

  private final ProjectRepository projectRepository;
  private final CustomerRepository customerRepository;
  private final InvoiceRepository invoiceRepository;
  private final DisbursementRepository disbursementRepository;
  private final TemplateContextHelper templateContextHelper;

  public MatterClosureContextBuilder(
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      InvoiceRepository invoiceRepository,
      DisbursementRepository disbursementRepository,
      TemplateContextHelper templateContextHelper) {
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
    this.invoiceRepository = invoiceRepository;
    this.disbursementRepository = disbursementRepository;
    this.templateContextHelper = templateContextHelper;
  }

  /**
   * Returns a map with {@code project.*}, {@code customer.*}, {@code closure.*}, {@code matter.*},
   * and {@code org.*} keys suitable for Tiptap variable substitution.
   */
  public Map<String, Object> build(UUID projectId, ClosureRequest req) {
    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    Map<String, Object> context = new HashMap<>();

    // project.*
    var projectMap = new LinkedHashMap<String, Object>();
    projectMap.put("id", project.getId());
    projectMap.put("name", project.getName());
    projectMap.put("description", project.getDescription());
    context.put("project", projectMap);

    // customer.*
    if (project.getCustomerId() != null) {
      customerRepository
          .findById(project.getCustomerId())
          .ifPresent(
              customer -> {
                var customerMap = new LinkedHashMap<String, Object>();
                customerMap.put("id", customer.getId());
                customerMap.put("name", customer.getName());
                customerMap.put("email", customer.getEmail());
                context.put("customer", customerMap);
              });
    }

    // closure.*
    var closureMap = new LinkedHashMap<String, Object>();
    closureMap.put("reason", req.reason().name());
    closureMap.put("date", LocalDate.now(ZoneOffset.UTC).toString());
    closureMap.put("notes", req.notes() != null ? req.notes() : "");
    context.put("closure", closureMap);

    // matter.*
    var matterMap = new LinkedHashMap<String, Object>();
    matterMap.put("total_fees_billed", totalFeesBilled(projectId).toPlainString());
    matterMap.put("total_disbursements", totalDisbursementsBilled(projectId).toPlainString());
    matterMap.put("duration_months", durationMonths(project.getCreatedAt()));
    context.put("matter", matterMap);

    // org.* — reuse the shared org context helper so branding/logo/name come through consistently.
    // The `principal_attorney` variable referenced by the template has no first-class OrgSettings
    // field yet — we emit an empty string so the renderer doesn't blow up on a missing key (task
    // 489.15 pins the variable name; future phases can back it with a real field).
    var orgMap = new LinkedHashMap<String, Object>(templateContextHelper.buildOrgContext());
    orgMap.putIfAbsent("principal_attorney", "");
    context.put("org", orgMap);

    return context;
  }

  private BigDecimal totalFeesBilled(UUID projectId) {
    List<Invoice> invoices = invoiceRepository.findByProjectId(projectId);
    return invoices.stream()
        .filter(i -> BILLED_INVOICE_STATUSES.contains(i.getStatus()))
        .map(Invoice::getTotal)
        .filter(t -> t != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal totalDisbursementsBilled(UUID projectId) {
    List<LegalDisbursement> billed =
        disbursementRepository.findByProjectIdAndApprovalStatusAndBillingStatus(
            projectId,
            DisbursementApprovalStatus.APPROVED.name(),
            DisbursementBillingStatus.BILLED.name());
    return billed.stream()
        .map(
            d -> {
              BigDecimal amt = d.getAmount() != null ? d.getAmount() : BigDecimal.ZERO;
              BigDecimal vat = d.getVatAmount() != null ? d.getVatAmount() : BigDecimal.ZERO;
              return amt.add(vat);
            })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private long durationMonths(Instant createdAt) {
    if (createdAt == null) {
      return 0;
    }
    LocalDate start = createdAt.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate end = LocalDate.now(ZoneOffset.UTC);
    Period between = Period.between(start, end);
    return (long) between.getYears() * 12L + between.getMonths();
  }
}
