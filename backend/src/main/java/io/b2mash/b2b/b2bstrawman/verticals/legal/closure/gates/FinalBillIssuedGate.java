package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.UnbilledTimeSummaryProjection;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.ClosureGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.GateResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Gate 4 — a final bill must have been issued (at least one invoice SENT or PAID on this matter)
 * AND there must be no unbilled billable time AND no approved-unbilled disbursements (Phase 67
 * §67.3.4 gate 4).
 *
 * <p>This is the most composite gate — it spans invoices + time entries + disbursements. The
 * message interpolates both the unbilled hours and the unbilled disbursement count so the user can
 * see exactly what still needs to be rolled into a final invoice.
 */
@Component
public class FinalBillIssuedGate implements ClosureGate {

  static final String CODE = "FINAL_BILL_ISSUED";

  private static final Set<InvoiceStatus> FINAL_BILL_STATUSES =
      Set.of(InvoiceStatus.SENT, InvoiceStatus.PAID);

  private final InvoiceRepository invoiceRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final DisbursementRepository disbursementRepository;

  public FinalBillIssuedGate(
      InvoiceRepository invoiceRepository,
      TimeEntryRepository timeEntryRepository,
      DisbursementRepository disbursementRepository) {
    this.invoiceRepository = invoiceRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.disbursementRepository = disbursementRepository;
  }

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public int order() {
    return 4;
  }

  @Override
  public GateResult evaluate(Project project) {
    var projectId = project.getId();

    boolean finalBillIssued =
        invoiceRepository.findByProjectId(projectId).stream()
            .anyMatch(i -> FINAL_BILL_STATUSES.contains(i.getStatus()));

    UnbilledTimeSummaryProjection unbilledTime =
        timeEntryRepository.countUnbilledByProjectId(projectId);
    double unbilledHours = unbilledTime == null ? 0.0 : unbilledTime.getTotalHours();

    long unbilledDisbursements =
        disbursementRepository.countByProjectIdAndApprovalStatusAndBillingStatus(
            projectId, "APPROVED", "UNBILLED");

    boolean noUnbilledTime = unbilledHours == 0.0;
    boolean noUnbilledDisbursements = unbilledDisbursements == 0;

    if (finalBillIssued && noUnbilledTime && noUnbilledDisbursements) {
      return new GateResult(true, CODE, "Final bill issued with no unbilled items.", Map.of());
    }

    String message;
    if (!finalBillIssued) {
      message =
          "No final bill issued. %sh of unbilled time + %d unbilled approved disbursements remain."
              .formatted(formatHours(unbilledHours), unbilledDisbursements);
    } else {
      message =
          "Final bill issued but %sh of unbilled time + %d unbilled approved disbursements remain."
              .formatted(formatHours(unbilledHours), unbilledDisbursements);
    }

    return new GateResult(
        false,
        CODE,
        message,
        Map.of(
            "finalBillIssued", finalBillIssued,
            "unbilledHours", unbilledHours,
            "unbilledDisbursements", unbilledDisbursements));
  }

  private static String formatHours(double hours) {
    if (hours == Math.floor(hours)) {
      return Integer.toString((int) hours);
    }
    return "%.2f".formatted(hours);
  }
}
