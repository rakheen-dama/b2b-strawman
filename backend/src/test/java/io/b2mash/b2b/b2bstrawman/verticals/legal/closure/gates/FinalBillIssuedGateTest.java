package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.UnbilledTimeSummaryProjection;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinalBillIssuedGateTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private TimeEntryRepository timeEntryRepository;
  @Mock private DisbursementRepository disbursementRepository;

  @Mock private Project project;
  @Mock private Invoice sentInvoice;
  @Mock private Invoice draftInvoice;
  @Mock private UnbilledTimeSummaryProjection unbilledZero;
  @Mock private UnbilledTimeSummaryProjection unbilledFive;

  private FinalBillIssuedGate gate;

  @BeforeEach
  void setUp() {
    gate = new FinalBillIssuedGate(invoiceRepository, timeEntryRepository, disbursementRepository);
    org.mockito.Mockito.lenient().when(project.getId()).thenReturn(PROJECT_ID);
  }

  @Test
  void passesWhenBillSentAndNoUnbilledItems() {
    when(sentInvoice.getStatus()).thenReturn(InvoiceStatus.SENT);
    when(invoiceRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(sentInvoice));
    when(unbilledZero.getTotalHours()).thenReturn(0.0);
    when(timeEntryRepository.countUnbilledByProjectId(PROJECT_ID)).thenReturn(unbilledZero);
    when(disbursementRepository.countByProjectIdAndApprovalStatusAndBillingStatus(
            PROJECT_ID, "APPROVED", "UNBILLED"))
        .thenReturn(0L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isTrue();
    assertThat(result.code()).isEqualTo("FINAL_BILL_ISSUED");
  }

  @Test
  void failsWhenNoFinalBillWithUnbilledItems() {
    when(draftInvoice.getStatus()).thenReturn(InvoiceStatus.DRAFT);
    when(invoiceRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(draftInvoice));
    when(unbilledFive.getTotalHours()).thenReturn(5.0);
    when(timeEntryRepository.countUnbilledByProjectId(PROJECT_ID)).thenReturn(unbilledFive);
    when(disbursementRepository.countByProjectIdAndApprovalStatusAndBillingStatus(
            PROJECT_ID, "APPROVED", "UNBILLED"))
        .thenReturn(2L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isFalse();
    assertThat(result.message()).contains("No final bill issued");
    assertThat(result.message()).contains("5h");
    assertThat(result.message()).contains("2 unbilled approved disbursements");
    assertThat(result.detail()).containsEntry("finalBillIssued", false);
    assertThat(result.detail()).containsEntry("unbilledHours", 5.0);
    assertThat(result.detail()).containsEntry("unbilledDisbursements", 2L);
  }

  @Test
  void failsWhenFinalBillIssuedButUnbilledTimeRemains() {
    when(sentInvoice.getStatus()).thenReturn(InvoiceStatus.PAID);
    when(invoiceRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(sentInvoice));
    when(unbilledFive.getTotalHours()).thenReturn(1.5);
    when(timeEntryRepository.countUnbilledByProjectId(PROJECT_ID)).thenReturn(unbilledFive);
    when(disbursementRepository.countByProjectIdAndApprovalStatusAndBillingStatus(
            PROJECT_ID, "APPROVED", "UNBILLED"))
        .thenReturn(0L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isFalse();
    assertThat(result.message()).contains("Final bill issued but");
    assertThat(result.message()).contains("1.50h");
  }

  @Test
  void orderIsFour() {
    assertThat(gate.order()).isEqualTo(4);
  }
}
