package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
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

/**
 * 591A.2 / 591A.4 read-API coverage. Debtor-book aggregation over a seeded book (totals, oldest
 * days overdue, the 4-bucket §4.1 split, the {@code collectionsExempt} flag surfacing, the {@code
 * current} bucket including not-yet-overdue SENT invoices), the per-customer drill-in (outstanding
 * invoices + paged activities), the per-invoice activity ledger exposing {@code status}+{@code
 * reason} verbatim (591A.4), and the invoice-area guard mirror (plain member → 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionsReadApiTest {

  private static final String ORG_ID = "org_collections_read_test";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private CollectionActivityRepository activityRepository;

  private String tenantSchema;
  private UUID ownerMemberId;

  private UUID custAlpha;
  private UUID invA3;
  private UUID custDelta;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Collections Read Test Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_read_owner", "read_owner@test.com", "Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_read_member", "read_member@test.com", "Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () -> {
          // Alpha: 3 SENT invoices staggered across d90plus / d60 / d30.
          custAlpha = seedCustomer("Alpha Naidoo Co", "alpha@test.com", false);
          seedSentInvoice(custAlpha, "Alpha Naidoo Co", "INV-A1", 112000, 62);
          seedSentInvoice(custAlpha, "Alpha Naidoo Co", "INV-A2", 180000, 45);
          invA3 = seedSentInvoice(custAlpha, "Alpha Naidoo Co", "INV-A3", 120000, 20);

          // Bravo: EXEMPT customer, still appears in the debtor book with the flag surfaced.
          UUID custBravo = seedCustomer("Bravo Exempt Co", "bravo@test.com", true);
          seedSentInvoice(custBravo, "Bravo Exempt Co", "INV-B1", 50000, 30);

          // Charlie: a single SENT invoice due in the future → the `current` bucket.
          UUID custCharlie = seedCustomer("Charlie Current Co", "charlie@test.com", false);
          seedFutureSentInvoice(custCharlie, "Charlie Current Co", "INV-C1", 30000, 10);

          // Delta: mixed-currency debtor → splits into one debtor row per currency.
          custDelta = seedCustomer("Delta Mixed Co", "delta@test.com", false);
          seedSentInvoiceCurrency(custDelta, "Delta Mixed Co", "INV-D1", 5000, 40, "ZAR");
          seedSentInvoiceCurrency(custDelta, "Delta Mixed Co", "INV-D2", 3000, 40, "USD");

          // Two activities on Alpha (591A.4 status+reason visibility + drill-in paging).
          activityRepository.save(
              new CollectionActivity(
                  invA3,
                  custAlpha,
                  CollectionStage.STAGE_1,
                  CollectionActivityStatus.SKIPPED,
                  20,
                  "rate_limited"));
          activityRepository.save(
              new CollectionActivity(
                  invA3,
                  custAlpha,
                  CollectionStage.STAGE_2,
                  CollectionActivityStatus.SEND_FAILED,
                  20,
                  "provider_failure"));
        });
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  private UUID seedCustomer(String name, String email, boolean exempt) {
    Customer customer = TestCustomerFactory.createActiveCustomer(name, email, ownerMemberId);
    customer.setCollectionsExempt(exempt);
    return customerRepository.save(customer).getId();
  }

  private UUID seedSentInvoice(
      UUID customerId, String name, String invoiceNumber, long amount, int daysOverdue) {
    return seedInvoice(
        customerId, name, invoiceNumber, amount, LocalDate.now().minusDays(daysOverdue));
  }

  private UUID seedFutureSentInvoice(
      UUID customerId, String name, String invoiceNumber, long amount, int daysAhead) {
    return seedInvoice(
        customerId, name, invoiceNumber, amount, LocalDate.now().plusDays(daysAhead));
  }

  private UUID seedSentInvoiceCurrency(
      UUID customerId,
      String name,
      String invoiceNumber,
      long amount,
      int daysOverdue,
      String currency) {
    return seedInvoice(
        customerId, name, invoiceNumber, amount, LocalDate.now().minusDays(daysOverdue), currency);
  }

  private UUID seedInvoice(
      UUID customerId, String name, String invoiceNumber, long amount, LocalDate dueDate) {
    return seedInvoice(customerId, name, invoiceNumber, amount, dueDate, "ZAR");
  }

  private UUID seedInvoice(
      UUID customerId,
      String name,
      String invoiceNumber,
      long amount,
      LocalDate dueDate,
      String currency) {
    var invoice =
        new Invoice(
            customerId, currency, name, name + "@test.com", null, "Read Org", ownerMemberId);
    invoice.updateDraft(dueDate, null, null, BigDecimal.ZERO);
    invoice.recalculateTotals(BigDecimal.valueOf(amount), false, BigDecimal.ZERO, false);
    invoice.approve(invoiceNumber, ownerMemberId);
    invoice.markSent();
    return invoiceRepository.save(invoice).getId();
  }

  @Test
  void debtors_aggregatesSeededBook_bucketsTotalsExemptFlag() throws Exception {
    var response =
        mockMvc
            .perform(
                get("/api/collections/debtors")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_read_owner")))
            .andExpect(status().isOk())
            // 3 single-currency debtors + Delta split into 2 currency rows = 5 groups.
            .andExpect(jsonPath("$.page.totalElements").value(5))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode content = objectMapper.readTree(response).get("content");
    assertThat(content).hasSize(5);

    // Ordered by outstandingTotal DESC: Alpha (412000) > Bravo (50000) > Charlie (30000).
    JsonNode alpha = content.get(0);
    assertThat(alpha.get("customerName").asText()).isEqualTo("Alpha Naidoo Co");
    assertThat(alpha.get("customerId").asText()).isEqualTo(custAlpha.toString());
    assertThat(alpha.get("outstandingTotal").decimalValue())
        .isEqualByComparingTo(new BigDecimal("412000.00"));
    assertThat(alpha.get("currency").asText()).isEqualTo("ZAR");
    assertThat(alpha.get("invoiceCount").asInt()).isEqualTo(3);
    assertThat(alpha.get("oldestDaysOverdue").asInt()).isEqualTo(62);
    assertThat(alpha.get("collectionsExempt").asBoolean()).isFalse();
    assertThat(alpha.get("signals")).isEmpty();
    JsonNode buckets = alpha.get("buckets");
    assertThat(buckets.get("current").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(buckets.get("d30").decimalValue()).isEqualByComparingTo(new BigDecimal("120000.00"));
    assertThat(buckets.get("d60").decimalValue()).isEqualByComparingTo(new BigDecimal("180000.00"));
    assertThat(buckets.get("d90plus").decimalValue())
        .isEqualByComparingTo(new BigDecimal("112000.00"));
    // lastActivity surfaces from the ledger (Alpha has activities; both STAGE-scoped rows).
    assertThat(alpha.get("lastActivity").isNull()).isFalse();
    assertThat(alpha.get("lastActivity").get("stage").asText()).startsWith("STAGE_");

    // Bravo: exempt flag surfaces; debtor book INCLUDES exempt customers.
    JsonNode bravo = content.get(1);
    assertThat(bravo.get("customerName").asText()).isEqualTo("Bravo Exempt Co");
    assertThat(bravo.get("collectionsExempt").asBoolean()).isTrue();
    assertThat(bravo.get("lastActivity").isNull()).isTrue();

    // Charlie: not-yet-overdue SENT invoice lands in the `current` bucket.
    JsonNode charlie = content.get(2);
    assertThat(charlie.get("customerName").asText()).isEqualTo("Charlie Current Co");
    assertThat(charlie.get("buckets").get("current").decimalValue())
        .isEqualByComparingTo(new BigDecimal("30000.00"));
    assertThat(charlie.get("collectionsExempt").asBoolean()).isFalse();
  }

  @Test
  void debtors_mixedCurrencyCustomerSplitsIntoRowPerCurrency() throws Exception {
    var response =
        mockMvc
            .perform(
                get("/api/collections/debtors")
                    .param("size", "50")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_read_owner")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode content = objectMapper.readTree(response).get("content");
    BigDecimal zarTotal = null;
    BigDecimal usdTotal = null;
    int deltaRows = 0;
    for (JsonNode row : content) {
      if (!custDelta.toString().equals(row.get("customerId").asText())) {
        continue;
      }
      deltaRows++;
      switch (row.get("currency").asText()) {
        case "ZAR" -> zarTotal = row.get("outstandingTotal").decimalValue();
        case "USD" -> usdTotal = row.get("outstandingTotal").decimalValue();
        default -> {
          // unexpected currency — leave totals null so the assertion below fails loudly
        }
      }
    }
    assertThat(deltaRows).as("Delta appears once per currency").isEqualTo(2);
    assertThat(zarTotal).isEqualByComparingTo(new BigDecimal("5000.00"));
    assertThat(usdTotal).isEqualByComparingTo(new BigDecimal("3000.00"));
  }

  @Test
  void activities_notFoundForUnknownInvoice() throws Exception {
    var unknownInvoiceId = UUID.randomUUID();
    mockMvc
        .perform(
            get("/api/collections/activities")
                .param("invoiceId", unknownInvoiceId.toString())
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_read_owner")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Invoice not found"))
        .andExpect(jsonPath("$.detail").value("No invoice found with id " + unknownInvoiceId));
  }

  @Test
  void debtorDrillIn_outstandingInvoicesAndPagedActivities() throws Exception {
    var response =
        mockMvc
            .perform(
                get("/api/collections/debtors/{customerId}", custAlpha)
                    .param("size", "1")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_read_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerId").value(custAlpha.toString()))
            .andExpect(jsonPath("$.customerName").value("Alpha Naidoo Co"))
            .andExpect(jsonPath("$.collectionsExempt").value(false))
            .andExpect(jsonPath("$.outstandingInvoices.length()").value(3))
            // Paged activities: 2 rows total, page size 1 returns 1.
            .andExpect(jsonPath("$.activities.page.totalElements").value(2))
            .andExpect(jsonPath("$.activities.content.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode invoices = objectMapper.readTree(response).get("outstandingInvoices");
    assertThat(invoices).hasSize(3);
    for (JsonNode inv : invoices) {
      assertThat(inv.get("invoiceNumber").asText()).startsWith("INV-A");
      assertThat(inv.get("currency").asText()).isEqualTo("ZAR");
      assertThat(inv.hasNonNull("dueDate")).isTrue();
    }
  }

  @Test
  void activitiesLedger_exposesStatusAndReasonVerbatim() throws Exception {
    var response =
        mockMvc
            .perform(
                get("/api/collections/activities")
                    .param("invoiceId", invA3.toString())
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_read_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode ledger = objectMapper.readTree(response);
    boolean sawRateLimited = false;
    boolean sawProviderFailure = false;
    for (JsonNode a : ledger) {
      assertThat(a.get("invoiceId").asText()).isEqualTo(invA3.toString());
      String status = a.get("status").asText();
      String reason = a.get("reason").asText();
      if ("SKIPPED".equals(status) && "rate_limited".equals(reason)) {
        sawRateLimited = true;
      }
      if ("SEND_FAILED".equals(status) && "provider_failure".equals(reason)) {
        sawProviderFailure = true;
      }
    }
    assertThat(sawRateLimited).as("SKIPPED(rate_limited) row visible").isTrue();
    assertThat(sawProviderFailure).as("SEND_FAILED(provider_failure) row visible").isTrue();
  }

  @Test
  void debtors_forbiddenForPlainMember() throws Exception {
    mockMvc
        .perform(
            get("/api/collections/debtors")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_read_member")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void activities_forbiddenForPlainMember() throws Exception {
    mockMvc
        .perform(
            get("/api/collections/activities")
                .param("invoiceId", invA3.toString())
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_read_member")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }
}
