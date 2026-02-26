package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceAgingReportQueryTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_iaq_test";

  @Autowired private InvoiceAgingReportQuery invoiceAgingReportQuery;
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId1;
  private UUID customerId2;

  // asOfDate for all tests: 2025-03-01
  private static final LocalDate AS_OF_DATE = LocalDate.of(2025, 3, 1);

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Aging Query Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberId =
        UUID.fromString(syncMember("user_iaq_owner", "iaq_owner@test.com", "Jane Smith", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Create customers
                      var c1 =
                          TestCustomerFactory.createActiveCustomer(
                              "Acme Corp", "acme@test.com", memberId);
                      c1 = customerRepository.save(c1);
                      customerId1 = c1.getId();

                      var c2 =
                          TestCustomerFactory.createActiveCustomer(
                              "Beta Inc", "beta@test.com", memberId);
                      c2 = customerRepository.save(c2);
                      customerId2 = c2.getId();

                      // Invoice 1: CURRENT bucket (due in future: 2025-03-15, asOf=2025-03-01)
                      createSentInvoice(
                          customerId1,
                          "Acme Corp",
                          "INV-001",
                          LocalDate.of(2025, 2, 15),
                          LocalDate.of(2025, 3, 15),
                          new BigDecimal("1000.00"));

                      // Invoice 2: 1-30 bucket (due 2025-02-14, 15 days overdue)
                      createSentInvoice(
                          customerId1,
                          "Acme Corp",
                          "INV-002",
                          LocalDate.of(2025, 1, 15),
                          LocalDate.of(2025, 2, 14),
                          new BigDecimal("2000.00"));

                      // Invoice 3: 31-60 bucket (due 2025-01-15, 45 days overdue)
                      createSentInvoice(
                          customerId1,
                          "Acme Corp",
                          "INV-003",
                          LocalDate.of(2024, 12, 15),
                          LocalDate.of(2025, 1, 15),
                          new BigDecimal("3000.00"));

                      // Invoice 4: 61-90 bucket (due 2024-12-16, 75 days overdue)
                      createSentInvoice(
                          customerId2,
                          "Beta Inc",
                          "INV-004",
                          LocalDate.of(2024, 11, 16),
                          LocalDate.of(2024, 12, 16),
                          new BigDecimal("4000.00"));

                      // Invoice 5: 90+ bucket (due 2024-11-21, 100 days overdue)
                      createSentInvoice(
                          customerId2,
                          "Beta Inc",
                          "INV-005",
                          LocalDate.of(2024, 10, 21),
                          LocalDate.of(2024, 11, 21),
                          new BigDecimal("5000.00"));

                      // Invoice 6: DRAFT status (should be excluded)
                      var draftInvoice =
                          new Invoice(
                              customerId1,
                              "ZAR",
                              "Acme Corp",
                              "acme@test.com",
                              null,
                              "Test Org",
                              memberId);
                      draftInvoice.updateDraft(
                          LocalDate.of(2025, 2, 1), null, null, BigDecimal.ZERO);
                      draftInvoice.recalculateTotals(new BigDecimal("500.00"), List.of(), false);
                      invoiceRepository.save(draftInvoice);

                      // Invoice 7: PAID status (should be excluded)
                      var paidInvoice =
                          createSentInvoice(
                              customerId1,
                              "Acme Corp",
                              "INV-006",
                              LocalDate.of(2025, 1, 1),
                              LocalDate.of(2025, 2, 1),
                              new BigDecimal("600.00"));
                      paidInvoice.recordPayment("PAY-001");
                      invoiceRepository.save(paidInvoice);
                    }));
  }

  @Test
  void currentBucketForFutureDueDate() {
    runInTenant(
        () -> {
          var params = agingParams(AS_OF_DATE);
          var result = invoiceAgingReportQuery.executeAll(params);

          var currentRows =
              result.rows().stream().filter(r -> "CURRENT".equals(r.get("ageBucket"))).toList();
          assertThat(currentRows).hasSize(1);
          assertThat(currentRows.getFirst().get("invoiceNumber")).isEqualTo("INV-001");
          assertThat(currentRows.getFirst().get("ageBucketLabel")).isEqualTo("Current");
          assertThat(((Number) currentRows.getFirst().get("daysOverdue")).intValue())
              .isLessThanOrEqualTo(0);
        });
  }

  @Test
  void bucket1To30DaysOverdue() {
    runInTenant(
        () -> {
          var params = agingParams(AS_OF_DATE);
          var result = invoiceAgingReportQuery.executeAll(params);

          var bucket1_30 =
              result.rows().stream().filter(r -> "1_30".equals(r.get("ageBucket"))).toList();
          assertThat(bucket1_30).hasSize(1);
          assertThat(bucket1_30.getFirst().get("invoiceNumber")).isEqualTo("INV-002");
          assertThat(bucket1_30.getFirst().get("ageBucketLabel")).isEqualTo("1-30 Days");
        });
  }

  @Test
  void bucket31To60DaysOverdue() {
    runInTenant(
        () -> {
          var params = agingParams(AS_OF_DATE);
          var result = invoiceAgingReportQuery.executeAll(params);

          var bucket31_60 =
              result.rows().stream().filter(r -> "31_60".equals(r.get("ageBucket"))).toList();
          assertThat(bucket31_60).hasSize(1);
          assertThat(bucket31_60.getFirst().get("invoiceNumber")).isEqualTo("INV-003");
          assertThat(bucket31_60.getFirst().get("ageBucketLabel")).isEqualTo("31-60 Days");
        });
  }

  @Test
  void bucket61To90DaysOverdue() {
    runInTenant(
        () -> {
          var params = agingParams(AS_OF_DATE);
          var result = invoiceAgingReportQuery.executeAll(params);

          var bucket61_90 =
              result.rows().stream().filter(r -> "61_90".equals(r.get("ageBucket"))).toList();
          assertThat(bucket61_90).hasSize(1);
          assertThat(bucket61_90.getFirst().get("invoiceNumber")).isEqualTo("INV-004");
          assertThat(bucket61_90.getFirst().get("ageBucketLabel")).isEqualTo("61-90 Days");
        });
  }

  @Test
  void bucket90PlusDaysOverdue() {
    runInTenant(
        () -> {
          var params = agingParams(AS_OF_DATE);
          var result = invoiceAgingReportQuery.executeAll(params);

          var bucket90Plus =
              result.rows().stream().filter(r -> "90_PLUS".equals(r.get("ageBucket"))).toList();
          assertThat(bucket90Plus).hasSize(1);
          assertThat(bucket90Plus.getFirst().get("invoiceNumber")).isEqualTo("INV-005");
          assertThat(bucket90Plus.getFirst().get("ageBucketLabel")).isEqualTo("90+ Days");
        });
  }

  @Test
  void summaryCountsAndAmountsCorrect() {
    runInTenant(
        () -> {
          var params = agingParams(AS_OF_DATE);
          var result = invoiceAgingReportQuery.executeAll(params);

          var summary = result.summary();
          assertThat(((Number) summary.get("currentCount")).intValue()).isEqualTo(1);
          assertThat(compareBd(summary.get("currentAmount"), "1000.00")).isTrue();
          assertThat(((Number) summary.get("bucket1_30Count")).intValue()).isEqualTo(1);
          assertThat(compareBd(summary.get("bucket1_30Amount"), "2000.00")).isTrue();
          assertThat(((Number) summary.get("bucket31_60Count")).intValue()).isEqualTo(1);
          assertThat(compareBd(summary.get("bucket31_60Amount"), "3000.00")).isTrue();
          assertThat(((Number) summary.get("bucket61_90Count")).intValue()).isEqualTo(1);
          assertThat(compareBd(summary.get("bucket61_90Amount"), "4000.00")).isTrue();
          assertThat(((Number) summary.get("bucket90PlusCount")).intValue()).isEqualTo(1);
          assertThat(compareBd(summary.get("bucket90PlusAmount"), "5000.00")).isTrue();
          assertThat(((Number) summary.get("totalCount")).intValue()).isEqualTo(5);
          assertThat(compareBd(summary.get("totalAmount"), "15000.00")).isTrue();
        });
  }

  @Test
  void customerFilterReturnsOnlyThatCustomer() {
    runInTenant(
        () -> {
          var params = agingParams(AS_OF_DATE);
          params.put("customerId", customerId2.toString());

          var result = invoiceAgingReportQuery.executeAll(params);

          // Customer 2 has INV-004 and INV-005
          assertThat(result.rows()).hasSize(2);
          assertThat(result.rows().stream().map(r -> r.get("customerName")).distinct().toList())
              .containsExactly("Beta Inc");
        });
  }

  @Test
  void draftAndPaidInvoicesExcluded() {
    runInTenant(
        () -> {
          var params = agingParams(AS_OF_DATE);
          var result = invoiceAgingReportQuery.executeAll(params);

          // Only 5 SENT invoices, not the DRAFT or PAID ones
          assertThat(result.rows()).hasSize(5);
          var statuses = result.rows().stream().map(r -> r.get("status")).distinct().toList();
          assertThat(statuses).containsExactly("SENT");
        });
  }

  @Test
  void emptyResultReturnsZeroedSummary() {
    runInTenant(
        () -> {
          // Use a customerId that has no SENT invoices
          var params = agingParams(AS_OF_DATE);
          params.put("customerId", UUID.randomUUID().toString());

          var result = invoiceAgingReportQuery.executeAll(params);

          assertThat(result.rows()).isEmpty();
          var summary = result.summary();
          assertThat(((Number) summary.get("totalCount")).intValue()).isEqualTo(0);
          assertThat(compareBd(summary.get("totalAmount"), "0")).isTrue();
        });
  }

  // --- Helpers ---

  private Map<String, Object> agingParams(LocalDate asOfDate) {
    var params = new HashMap<String, Object>();
    params.put("asOfDate", asOfDate.toString());
    return params;
  }

  private boolean compareBd(Object value, String expected) {
    if (value instanceof BigDecimal bd) {
      return bd.compareTo(new BigDecimal(expected)) == 0;
    }
    return false;
  }

  private Invoice createSentInvoice(
      UUID customerId,
      String customerName,
      String invoiceNumber,
      LocalDate issueDate,
      LocalDate dueDate,
      BigDecimal subtotal) {
    var invoice = new Invoice(customerId, "ZAR", customerName, null, null, "Test Org", memberId);
    invoice.updateDraft(dueDate, null, null, BigDecimal.ZERO);
    invoice.recalculateTotals(subtotal, List.of(), false);
    invoice.approve(invoiceNumber, memberId);
    invoice.markSent();
    return invoiceRepository.save(invoice);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> action.run()));
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
