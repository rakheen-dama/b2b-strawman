package io.b2mash.b2b.b2bstrawman.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.report.ReportController.BillingTrendPoint;
import io.b2mash.b2b.b2bstrawman.report.ReportController.BillingTrendResponse;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link BillingTrendService} against the real database with a fixed clock (Phase 90 643B, ADR-372,
 * arch §15.3.8). The SUT is built by hand with {@code Clock.fixed(2026-11-30T22:30:00Z)} — 1
 * December 00:30 SAST — so "today" is 2026-12-01 and the 12-month window is 2026-01 … 2026-12 (no
 * new Spring context; precedent {@code PortalDeadlineBackfillRunnerTest}).
 *
 * <p>Fixtures are native SQL, deliberately: {@code invoices.issue_date} is stamped at approve from
 * the clock bean and {@code payment_events.created_at} from {@code Instant.now()} in
 * {@code @PrePersist}, so no REST or service write can place a row in a past month or at an exact
 * instant. The month bucketing happens in SQL ({@code AT TIME ZONE}), so only a real database
 * proves the SA-midnight boundary. All rows live in this class's own tenant; assertions read only
 * it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingTrendServiceTest {

  private static final String ORG_ID = "org_report_billing_trend_svc";

  /** 2026-12-01 00:30 SAST. */
  private static final Instant FIXED_NOW = Instant.parse("2026-11-30T22:30:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private BillingTrendService sut;
  private int invoiceSeq;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Billing Trend Service Org", null, "ZA")
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_rbts_owner", "rbts_owner@test.com", "RBTS Owner", "owner"));
    sut =
        new BillingTrendService(
            Clock.fixed(FIXED_NOW, KaziTime.ZONE), entityManager, orgSettingsRepository);

    inTenantTx(
        () -> {
          customerId =
              customerRepository
                  .saveAndFlush(
                      TestCustomerFactory.createActiveCustomer(
                          "Billing Trend Svc Client", "rbts_client@test.com", memberId))
                  .getId();

          // Invoices — Expected with today = 2026-12-01, window 2026-01 … 2026-12.
          UUID nov = insertInvoice("PAID", "ZAR", "2000.00", "2026-11-30"); // November
          insertInvoice("SENT", "ZAR", "1000.00", "2026-12-01"); // December
          insertInvoice("APPROVED", "ZAR", "3000.00", "2026-01-01"); // January (inclusive)
          insertInvoice("PARTIALLY_PAID", "ZAR", "900.00", "2026-06-10"); // June
          insertInvoice("SENT", "ZAR", "4000.00", "2025-12-31"); // before fromDate
          insertInvoice("SENT", "ZAR", "5000.00", "2026-12-02"); // after today
          insertInvoice("DRAFT", "ZAR", "6000.00", "2026-06-10"); // status
          insertInvoice("VOID", "ZAR", "7000.00", "2026-06-10"); // status
          insertInvoice("SENT", "USD", "8000.00", "2026-06-10"); // currency

          // Payment events.
          insertPayment(nov, "COMPLETED", "ZAR", "100.00", "2026-11-30T22:30:00Z"); // 1 Dec 00:30
          insertPayment(nov, "COMPLETED", "ZAR", "200.00", "2026-11-30T21:30:00Z"); // 30 Nov 23:30
          insertPayment(nov, "COMPLETED", "ZAR", "300.00", "2025-12-31T22:00:00Z"); // = fromInstant
          insertPayment(nov, "COMPLETED", "ZAR", "400.00", "2025-12-31T21:59:59Z"); // before
          insertPayment(nov, "COMPLETED", "ZAR", "500.00", "2026-12-01T22:00:00Z"); // = toExclusive
          insertPayment(nov, "FAILED", "ZAR", "600.00", "2026-06-15T10:00:00Z"); // status
          insertPayment(nov, "COMPLETED", "USD", "700.00", "2026-06-15T10:00:00Z"); // currency
        });
  }

  @Test
  void paymentAt2230ZOnNovember30BucketsIntoDecember() {
    var trend = trend(12);
    BillingTrendPoint december = trend.months().get(11);
    assertThat(december.month()).isEqualTo("2026-12");
    assertThat(december.collected()).isEqualByComparingTo("100.00");
    assertThat(december.collectedCount()).isEqualTo(1);

    BillingTrendPoint november = trend.months().get(10);
    assertThat(november.month()).isEqualTo("2026-11");
    assertThat(november.collected()).isEqualByComparingTo("200.00");
    assertThat(november.collectedCount()).isEqualTo(1);
    assertThat(november.billed()).isEqualByComparingTo("2000.00");
    assertThat(december.billed()).isEqualByComparingTo("1000.00");
  }

  @Test
  void windowBoundsAreSaMidnights() {
    var trend = trend(12);
    BillingTrendPoint january = trend.months().get(0);
    assertThat(january.month()).isEqualTo("2026-01");
    assertThat(january.collected()).isEqualByComparingTo("300.00");
    assertThat(january.billed()).isEqualByComparingTo("3000.00");

    // Pre-window, post-today, FAILED, DRAFT, VOID and USD rows are absent from every point.
    BigDecimal collected =
        trend.months().stream()
            .map(BillingTrendPoint::collected)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal billed =
        trend.months().stream()
            .map(BillingTrendPoint::billed)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(collected).isEqualByComparingTo("600.00");
    assertThat(billed).isEqualByComparingTo("6900.00");
    assertThat(trend.months().get(5).month()).isEqualTo("2026-06");
    assertThat(trend.months().get(5).billed()).isEqualByComparingTo("900.00");
    assertThat(trend.months().get(5).billedCount()).isEqualTo(1);
    assertThat(trend.months().get(5).collected()).isEqualByComparingTo("0.00");
  }

  @Test
  void twelvePointsOldestFirstZeroFilled() {
    var trend = trend(12);
    assertThat(trend.from()).isEqualTo("2026-01");
    assertThat(trend.to()).isEqualTo("2026-12");
    assertThat(trend.asOf()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(trend.months())
        .extracting(BillingTrendPoint::month)
        .containsExactly(
            "2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06", "2026-07", "2026-08",
            "2026-09", "2026-10", "2026-11", "2026-12");
    BillingTrendPoint february = trend.months().get(1);
    assertThat(february.billed()).isEqualTo(new BigDecimal("0.00"));
    assertThat(february.billedCount()).isZero();
    assertThat(february.collected()).isEqualTo(new BigDecimal("0.00"));
    assertThat(february.collectedCount()).isZero();
  }

  @Test
  void partialOnlyOnTheCurrentMonth() {
    List<BillingTrendPoint> months = trend(12).months();
    for (int i = 0; i < 12; i++) {
      assertThat(months.get(i).partial()).as("month %d", i).isEqualTo(i == 11);
    }
  }

  @Test
  void singleMonthWindowIsDecember() {
    var trend = trend(1);
    assertThat(trend.from()).isEqualTo("2026-12");
    assertThat(trend.months()).hasSize(1);
    BillingTrendPoint only = trend.months().getFirst();
    assertThat(only.month()).isEqualTo("2026-12");
    assertThat(only.partial()).isTrue();
    // The November invoice and the 30 Nov 23:30 SAST payment fall before the window.
    assertThat(only.billed()).isEqualByComparingTo("1000.00");
    assertThat(only.collected()).isEqualByComparingTo("100.00");
    assertThat(only.collectedCount()).isEqualTo(1);
  }

  @Test
  void monthsOutsideOneToTwentyFourThrows() {
    for (int months : new int[] {0, 25}) {
      assertThatThrownBy(() -> trend(months))
          .isInstanceOf(InvalidStateException.class)
          .hasMessageContaining("'months' must be between 1 and 24");
    }
  }

  @Test
  void currencyIsTheOrgDefault() {
    assertThat(trend(12).currency()).isEqualTo("ZAR");
  }

  // --- helpers ---

  private BillingTrendResponse trend(int months) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> sut.getBillingTrend(months)));
  }

  private void inTenantTx(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> action.run()));
  }

  private UUID insertInvoice(String status, String currency, String total, String issueDate) {
    UUID id = UUID.randomUUID();
    entityManager
        .createNativeQuery(
            """
            INSERT INTO invoices (id, customer_id, invoice_number, status, currency,
                subtotal, tax_amount, total, issue_date, due_date,
                customer_name, org_name, payment_destination, created_by, created_at, updated_at)
            VALUES (:id, :customerId, :invoiceNumber, :status, :currency,
                :total, 0, :total, :issueDate, :issueDate,
                'Billing Trend Svc Client', 'Test Org', 'OPERATING', :memberId, now(), now())
            """)
        .setParameter("id", id)
        .setParameter("customerId", customerId)
        .setParameter("invoiceNumber", "RBTS-%03d".formatted(++invoiceSeq))
        .setParameter("status", status)
        .setParameter("currency", currency)
        .setParameter("total", new BigDecimal(total))
        .setParameter("issueDate", LocalDate.parse(issueDate))
        .setParameter("memberId", memberId)
        .executeUpdate();
    return id;
  }

  private void insertPayment(
      UUID invoiceId, String status, String currency, String amount, String createdAt) {
    Timestamp at = Timestamp.from(Instant.parse(createdAt));
    entityManager
        .createNativeQuery(
            """
            INSERT INTO payment_events (id, invoice_id, provider_slug, status, amount, currency,
                payment_destination, created_at, updated_at)
            VALUES (gen_random_uuid(), :invoiceId, 'manual', :status, :amount, :currency,
                'OPERATING', :createdAt, :createdAt)
            """)
        .setParameter("invoiceId", invoiceId)
        .setParameter("status", status)
        .setParameter("amount", new BigDecimal(amount))
        .setParameter("currency", currency)
        .setParameter("createdAt", at)
        .executeUpdate();
  }
}
