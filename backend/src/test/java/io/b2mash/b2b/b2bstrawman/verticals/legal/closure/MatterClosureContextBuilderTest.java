package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureReason;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Phase 88 622D (ADR-347): {@code matter.total_fees_billed} counts every ISSUED fee note — SENT,
 * PARTIALLY_PAID and PAID — and nothing pre-send. Status set only; the excl-VAT / lines arithmetic
 * is owned by 626E.
 */
@ExtendWith(MockitoExtension.class)
class MatterClosureContextBuilderTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();

  @Mock private ProjectRepository projectRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private DisbursementRepository disbursementRepository;
  @Mock private Project project;

  private Invoice invoice(InvoiceStatus status, String total) {
    Invoice invoice = org.mockito.Mockito.mock(Invoice.class);
    lenient().when(invoice.getStatus()).thenReturn(status);
    lenient().when(invoice.getTotal()).thenReturn(new BigDecimal(total));
    return invoice;
  }

  @Test
  void totalFeesBilledCountsSentPartiallyPaidAndPaidFeeNotes() {
    var builder =
        new MatterClosureContextBuilder(
            projectRepository, invoiceRepository, disbursementRepository);
    when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
    lenient().when(project.getClosedAt()).thenReturn(null);
    lenient().when(project.getCreatedAt()).thenReturn(Instant.now());
    // Built before stubbing the repository (Mockito forbids stubbing inside thenReturn args).
    List<Invoice> invoices =
        List.of(
            invoice(InvoiceStatus.SENT, "1000.00"),
            invoice(InvoiceStatus.PARTIALLY_PAID, "2000.00"),
            invoice(InvoiceStatus.PAID, "500.00"),
            invoice(InvoiceStatus.DRAFT, "9999.00"));
    when(invoiceRepository.findByProjectId(PROJECT_ID)).thenReturn(invoices);
    when(disbursementRepository.findByProjectIdAndApprovalStatusAndBillingStatus(
            PROJECT_ID, "APPROVED", "BILLED"))
        .thenReturn(List.of());

    Map<String, Object> context =
        builder.buildClosureContext(
            PROJECT_ID,
            new ClosureRequest(
                ClosureReason.CONCLUDED, null, false, null, false, null, null, null));

    @SuppressWarnings("unchecked")
    var matter = (Map<String, Object>) context.get("matter");
    assertThat(matter).containsEntry("total_fees_billed", "3500.00");
  }
}
