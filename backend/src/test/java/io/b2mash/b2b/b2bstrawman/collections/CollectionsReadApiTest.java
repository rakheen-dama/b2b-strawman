package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.BillingSettings;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustBalanceFactory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
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

/**
 * 591A.2 / 591A.4 read-API coverage. Debtor-book aggregation over a seeded book (totals, oldest
 * days overdue, the 4-bucket §4.1 split, the {@code collectionsExempt} flag surfacing, the {@code
 * current} bucket including not-yet-overdue SENT invoices), the per-customer drill-in (outstanding
 * invoices + paged activities), the per-invoice activity ledger exposing {@code status}+{@code
 * reason} verbatim (591A.4), and the invoice-area guard mirror (plain member → 403). 620.6 adds a
 * real-write-path case (create → approve → send through the API, in a dedicated tenant) asserting
 * the debtor-book row and {@code GET /api/invoices/outstanding-summary}, plus its 403 gates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionsReadApiTest {

  private static final String ORG_ID = "org_collections_read_test";

  /**
   * Dedicated tenant for the real-write-path case (620.6). The shared seeded book ({@link #ORG_ID})
   * pins exact debtor counts/order, so a fee note sent through the API must not land there; only
   * {@link #realPath_feeNoteApprovedAndSentAppearsInDebtorBookAndSummary} writes to this tenant, so
   * its exact totals are safe (no count-bleed). Country "ZA" → org default currency ZAR.
   */
  private static final String RP_ORG_ID = "org_collections_read_realpath";

  /**
   * 622D: dedicated tenant for the balance case — only {@link
   * #partiallyPaidFeeNoteCountsAtItsBalance_paidExcluded} writes here, so its tenant-wide summary
   * totals are exact (count-bleed rule).
   */
  private static final String BAL_ORG_ID = "org_collections_read_balance";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private CollectionActivityRepository activityRepository;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private ClientLedgerCardRepository clientLedgerCardRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;

  private String tenantSchema;
  private UUID ownerMemberId;

  private UUID custAlpha;
  private UUID invA3;
  private UUID custDelta;
  private UUID custEcho;

  private String rpSchema;
  private UUID rpOwnerId;
  private UUID rpClientId;
  private UUID rpMatterId;

  private UUID balClientId;
  private UUID balMatterId;

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

          // Echo: trust-funded debtor (592B) — small total so it sorts LAST and leaves the
          // index-based assertions on rows 0-2 untouched.
          custEcho = seedCustomer("Echo Trust Co", "echo@test.com", false);
          seedSentInvoice(custEcho, "Echo Trust Co", "INV-E1", 1000, 20);
          TestTrustBalanceFactory.seedTrustBalance(
              trustAccountRepository,
              clientLedgerCardRepository,
              custEcho,
              BigDecimal.valueOf(84200));

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

    // D8 (620B): a plain member granted +INVOICING (debtor book access) but NOT
    // FINANCIAL_VISIBILITY — the outstanding summary must still deny it.
    UUID invoicerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_read_invoicer",
                "read_invoicer@test.com",
                "Invoicer",
                "member"));
    runInTenant(
        () -> {
          var invoicer = memberRepository.findById(invoicerId).orElseThrow();
          invoicer.setCapabilityOverrides(Set.of("+INVOICING"));
          memberRepository.save(invoicer);
        });

    provisioningService.provisionTenant(RP_ORG_ID, "Collections Real Path Org", null, "ZA");
    rpOwnerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, RP_ORG_ID, "user_rp_owner", "rp_owner@test.com", "RP Owner", "owner"));
    rpSchema = orgSchemaMappingRepository.findByClerkOrgId(RP_ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, rpSchema)
        .where(RequestScopes.ORG_ID, RP_ORG_ID)
        .where(RequestScopes.MEMBER_ID, rpOwnerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              rpClientId =
                  customerRepository
                      .save(
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Real Path Client", "realpath@test.com", rpOwnerId))
                      .getId();
              rpMatterId =
                  projectRepository
                      .save(new Project("Real Path Matter", "620.6 real-path debtor", rpOwnerId))
                      .getId();
              customerProjectRepository.save(
                  new CustomerProject(rpClientId, rpMatterId, rpOwnerId));
            });

    provisioningService.provisionTenant(BAL_ORG_ID, "Collections Balance Org", null, "ZA");
    UUID balOwnerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, BAL_ORG_ID, "user_bal_owner", "bal_owner@test.com", "Bal Owner", "owner"));
    String balSchema =
        orgSchemaMappingRepository.findByClerkOrgId(BAL_ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, balSchema)
        .where(RequestScopes.ORG_ID, BAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, balOwnerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              balClientId =
                  customerRepository
                      .save(
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Balance Client", "balance@test.com", balOwnerId))
                      .getId();
              balMatterId =
                  projectRepository
                      .save(new Project("Balance Matter", "622D balance", balOwnerId))
                      .getId();
              customerProjectRepository.save(
                  new CustomerProject(balClientId, balMatterId, balOwnerId));
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
    invoice.approve(invoiceNumber, ownerMemberId, BillingSettings.DEFAULT_PAYMENT_TERMS_DAYS);
    invoice.markSent();
    return invoiceRepository.save(invoice).getId();
  }

  /** POST /api/invoices (→ InvoiceCreationService.createDraft) + one manual line on the matter. */
  private String rpCreateDraftWithLine(LocalDate dueDate) throws Exception {
    return createDraftWithLine(RP_ORG_ID, "user_rp_owner", rpClientId, rpMatterId, dueDate);
  }

  private JsonNode rpPost(String path) throws Exception {
    return postAs(RP_ORG_ID, "user_rp_owner", path, null);
  }

  private String createDraftWithLine(
      String orgId, String subject, UUID clientId, UUID matterId, LocalDate dueDate)
      throws Exception {
    String body =
        dueDate == null
            ? "{\"clientId\": \"%s\", \"currency\": \"ZAR\"}".formatted(clientId)
            : "{\"clientId\": \"%s\", \"currency\": \"ZAR\", \"dueDate\": \"%s\"}"
                .formatted(clientId, dueDate);
    String id =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/invoices")
                            .with(TestJwtFactory.ownerJwt(orgId, subject))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();
    mockMvc
        .perform(
            post("/api/invoices/" + id + "/lines")
                .with(TestJwtFactory.ownerJwt(orgId, subject))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"matterId": "%s", "description": "Consultation", "quantity": 1.0000,
                     "unitPrice": 5000.00, "sortOrder": 0}
                    """
                        .formatted(matterId)))
        .andExpect(status().isCreated());
    return id;
  }

  /** POST as the given owner (optional JSON body), expecting 200; returns the response body. */
  private JsonNode postAs(String orgId, String subject, String path, String jsonBody)
      throws Exception {
    var request = post(path).with(TestJwtFactory.ownerJwt(orgId, subject));
    if (jsonBody != null) {
      request = request.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
    }
    return objectMapper.readTree(
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode getAs(String orgId, String subject, String path) throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(get(path).with(TestJwtFactory.ownerJwt(orgId, subject)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void debtors_aggregatesSeededBook_bucketsTotalsExemptFlag() throws Exception {
    var response =
        mockMvc
            .perform(
                get("/api/collections/debtors")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_read_owner")))
            .andExpect(status().isOk())
            // 4 single-currency debtors (Alpha, Bravo, Charlie, Echo) + Delta split into 2 currency
            // rows = 6 groups. Echo (592B) is the trust-funded debtor added below.
            .andExpect(jsonPath("$.page.totalElements").value(6))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode content = objectMapper.readTree(response).get("content");
    assertThat(content).hasSize(6);

    // Ordered by outstandingTotal DESC: Alpha (412000) > Bravo (50000) > Charlie (30000) …
    // > Echo (1000) last.
    JsonNode alpha = content.get(0);
    assertThat(alpha.get("clientName").asText()).isEqualTo("Alpha Naidoo Co");
    assertThat(alpha.get("clientId").asText()).isEqualTo(custAlpha.toString());
    assertThat(alpha.get("outstandingTotal").decimalValue())
        .isEqualByComparingTo(new BigDecimal("412000.00"));
    assertThat(alpha.get("currency").asText()).isEqualTo("ZAR");
    assertThat(alpha.get("invoiceCount").asInt()).isEqualTo(3);
    assertThat(alpha.get("oldestDaysOverdue").asInt()).isEqualTo(62);
    assertThat(alpha.get("collectionsExempt").asBoolean()).isFalse();
    assertThat(alpha.get("signals")).isEmpty();
    // signalDetails (592B) is ALWAYS present, never null — empty object when no advice.
    assertThat(alpha.get("signalDetails").isObject()).isTrue();
    assertThat(alpha.get("signalDetails")).isEmpty();
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
    assertThat(bravo.get("clientName").asText()).isEqualTo("Bravo Exempt Co");
    assertThat(bravo.get("collectionsExempt").asBoolean()).isTrue();
    assertThat(bravo.get("lastActivity").isNull()).isTrue();

    // Charlie: not-yet-overdue SENT invoice lands in the `current` bucket.
    JsonNode charlie = content.get(2);
    assertThat(charlie.get("clientName").asText()).isEqualTo("Charlie Current Co");
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
      if (!custDelta.toString().equals(row.get("clientId").asText())) {
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
  void debtors_signalDetailsCarriesTrustAdvice() throws Exception {
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
    JsonNode echo = null;
    for (JsonNode row : content) {
      if (custEcho.toString().equals(row.get("clientId").asText())) {
        echo = row;
      }
    }
    assertThat(echo).as("Echo Trust Co debtor row present").isNotNull();
    // Advisor-contributed signal name in the list…
    assertThat(echo.get("signals")).hasSize(1);
    assertThat(echo.get("signals").get(0).asText()).isEqualTo("TRUST_FUNDS_AVAILABLE");
    // …and its ADR-329 detail string in the additive map, verbatim ("held in trust").
    JsonNode details = echo.get("signalDetails");
    assertThat(details.size()).isEqualTo(1);
    assertThat(details.get("TRUST_FUNDS_AVAILABLE").asText())
        .isEqualTo("R 84 200,00 held in trust");
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
        .andExpect(jsonPath("$.title").value("Fee note not found"))
        .andExpect(jsonPath("$.detail").value("No fee note found with id " + unknownInvoiceId));
  }

  @Test
  void debtorDrillIn_outstandingInvoicesAndPagedActivities() throws Exception {
    var response =
        mockMvc
            .perform(
                get("/api/collections/debtors/{clientId}", custAlpha)
                    .param("size", "1")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_read_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientId").value(custAlpha.toString()))
            .andExpect(jsonPath("$.clientName").value("Alpha Naidoo Co"))
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

  // 620.6 (ADR-347): a fee note created → approved → sent through the REAL write path (no entity
  // markSent, no hand-set due date) lands in the debtor book and the outstanding summary. Approve
  // stamps due = issue + 30 (current bucket); a draft-typed past due date is preserved (overdue);
  // an APPROVED-unsent fee note and a DRAFT stay out of the debtor book.
  @Test
  void realPath_feeNoteApprovedAndSentAppearsInDebtorBookAndSummary() throws Exception {
    String current = rpCreateDraftWithLine(null);
    rpPost("/api/invoices/" + current + "/approve");
    JsonNode currentSent = rpPost("/api/invoices/" + current + "/send");

    String overdue = rpCreateDraftWithLine(LocalDate.now().minusDays(10));
    rpPost("/api/invoices/" + overdue + "/approve");
    JsonNode overdueSent = rpPost("/api/invoices/" + overdue + "/send");

    JsonNode approvedOnly = rpPost("/api/invoices/" + rpCreateDraftWithLine(null) + "/approve");
    rpCreateDraftWithLine(null); // DRAFT — excluded everywhere

    assertThat(currentSent.get("status").asText()).isEqualTo("SENT");
    assertThat(currentSent.hasNonNull("dueDate")).as("approve stamped a due date").isTrue();
    assertThat(LocalDate.parse(currentSent.get("dueDate").asText())).isAfter(LocalDate.now());
    BigDecimal currentTotal = currentSent.get("total").decimalValue();
    BigDecimal overdueTotal = overdueSent.get("total").decimalValue();
    BigDecimal approvedTotal = approvedOnly.get("total").decimalValue();

    // Debtor book: one row for the client, both SENT fee notes, bucketed by the stamped/typed date.
    JsonNode debtors =
        objectMapper.readTree(
            mockMvc
                .perform(
                    get("/api/collections/debtors")
                        .with(TestJwtFactory.ownerJwt(RP_ORG_ID, "user_rp_owner")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode row = null;
    for (JsonNode r : debtors.get("content")) {
      if (rpClientId.toString().equals(r.get("clientId").asText())) {
        row = r;
      }
    }
    assertThat(row).as("real-path client in the debtor book").isNotNull();
    assertThat(row.get("currency").asText()).isEqualTo("ZAR");
    assertThat(row.get("invoiceCount").asInt()).isEqualTo(2);
    assertThat(row.get("outstandingTotal").decimalValue())
        .isEqualByComparingTo(currentTotal.add(overdueTotal));
    assertThat(row.get("buckets").get("current").decimalValue()).isEqualByComparingTo(currentTotal);
    assertThat(row.get("buckets").get("d30").decimalValue()).isEqualByComparingTo(overdueTotal);

    // Outstanding summary: same definition, plus approved-unsent reported separately.
    mockMvc
        .perform(
            get("/api/invoices/outstanding-summary")
                .with(TestJwtFactory.ownerJwt(RP_ORG_ID, "user_rp_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("ZAR"))
        .andExpect(jsonPath("$.outstandingCount").value(2))
        .andExpect(jsonPath("$.overdueCount").value(1))
        .andExpect(jsonPath("$.approvedUnsentCount").value(1))
        .andDo(
            r -> {
              JsonNode s = objectMapper.readTree(r.getResponse().getContentAsString());
              assertThat(s.get("outstandingTotal").decimalValue())
                  .isEqualByComparingTo(currentTotal.add(overdueTotal));
              assertThat(s.get("overdueTotal").decimalValue()).isEqualByComparingTo(overdueTotal);
              assertThat(s.get("approvedUnsentTotal").decimalValue())
                  .isEqualByComparingTo(approvedTotal);
            });
  }

  // 622D (ADR-347 A3): a PARTIALLY_PAID fee note stays in the debtor book at its balance due
  // (total − amount_paid); a fully PAID one leaves it. Real write path: create → approve → send →
  // POST /payment, in a dedicated tenant so the tenant-wide summary totals are exact.
  @Test
  void partiallyPaidFeeNoteCountsAtItsBalance_paidExcluded() throws Exception {
    String sent = balCreateDraftWithLine();
    balPost("/api/invoices/" + sent + "/approve", null);
    BigDecimal sentTotal =
        balPost("/api/invoices/" + sent + "/send", null).get("total").decimalValue();

    String part = balCreateDraftWithLine();
    balPost("/api/invoices/" + part + "/approve", null);
    BigDecimal partTotal =
        balPost("/api/invoices/" + part + "/send", null).get("total").decimalValue();
    JsonNode partPaid =
        balPost(
            "/api/invoices/" + part + "/payment",
            "{\"amount\": 1000.00, \"paymentMethod\": \"EFT\", \"paymentReference\": \"EFT-622D\"}");
    assertThat(partPaid.get("status").asText()).isEqualTo("PARTIALLY_PAID");
    BigDecimal partBalance = partTotal.subtract(new BigDecimal("1000.00"));
    assertThat(partPaid.get("balanceDue").decimalValue()).isEqualByComparingTo(partBalance);

    String paid = balCreateDraftWithLine();
    balPost("/api/invoices/" + paid + "/approve", null);
    BigDecimal paidTotal =
        balPost("/api/invoices/" + paid + "/send", null).get("total").decimalValue();
    JsonNode settled =
        balPost(
            "/api/invoices/" + paid + "/payment",
            "{\"amount\": %s, \"paymentMethod\": \"EFT\"}".formatted(paidTotal.toPlainString()));
    assertThat(settled.get("status").asText()).isEqualTo("PAID");

    // Debtor book: one row — SENT at its total + PARTIALLY_PAID at its balance; PAID excluded.
    JsonNode debtors = getAs(BAL_ORG_ID, "user_bal_owner", "/api/collections/debtors");
    JsonNode row = null;
    for (JsonNode r : debtors.get("content")) {
      if (balClientId.toString().equals(r.get("clientId").asText())) {
        row = r;
      }
    }
    assertThat(row).as("balance client in the debtor book").isNotNull();
    assertThat(debtors.get("page").get("totalElements").asInt()).isEqualTo(1);
    assertThat(row.get("invoiceCount").asInt()).isEqualTo(2);
    BigDecimal owed = sentTotal.add(partBalance);
    assertThat(row.get("outstandingTotal").decimalValue()).isEqualByComparingTo(owed);
    assertThat(row.get("buckets").get("current").decimalValue()).isEqualByComparingTo(owed);

    // Drill-in: each outstanding fee note carries its gross total AND its balance due.
    JsonNode detail =
        getAs(BAL_ORG_ID, "user_bal_owner", "/api/collections/debtors/" + balClientId);
    JsonNode invoices = detail.get("outstandingInvoices");
    assertThat(invoices).hasSize(2);
    JsonNode partRow = null;
    JsonNode sentRow = null;
    for (JsonNode inv : invoices) {
      if (part.equals(inv.get("invoiceId").asText())) {
        partRow = inv;
      } else if (sent.equals(inv.get("invoiceId").asText())) {
        sentRow = inv;
      }
    }
    assertThat(partRow).as("part-paid fee note in the drill-in").isNotNull();
    assertThat(sentRow).as("sent fee note in the drill-in").isNotNull();
    assertThat(partRow.get("total").decimalValue()).isEqualByComparingTo(partTotal);
    assertThat(partRow.get("balanceDue").decimalValue()).isEqualByComparingTo(partBalance);
    assertThat(sentRow.get("total").decimalValue()).isEqualByComparingTo(sentTotal);
    assertThat(sentRow.get("balanceDue").decimalValue()).isEqualByComparingTo(sentTotal);

    // Outstanding summary: the same definition (approve stamps due = issue + 30 → none overdue).
    JsonNode summary = getAs(BAL_ORG_ID, "user_bal_owner", "/api/invoices/outstanding-summary");
    assertThat(summary.get("outstandingCount").asInt()).isEqualTo(2);
    assertThat(summary.get("outstandingTotal").decimalValue()).isEqualByComparingTo(owed);
    assertThat(summary.get("overdueCount").asInt()).isZero();
    assertThat(summary.get("approvedUnsentCount").asInt()).isZero();
  }

  private String balCreateDraftWithLine() throws Exception {
    return createDraftWithLine(BAL_ORG_ID, "user_bal_owner", balClientId, balMatterId, null);
  }

  private JsonNode balPost(String path, String jsonBody) throws Exception {
    return postAs(BAL_ORG_ID, "user_bal_owner", path, jsonBody);
  }

  @Test
  void outstandingSummary_forbiddenForPlainMember() throws Exception {
    mockMvc
        .perform(
            get("/api/invoices/outstanding-summary")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_read_member")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void outstandingSummary_forbiddenForInvoicingOnlyMember() throws Exception {
    // The override grants the debtor book (INVOICING) — proves the member is otherwise authorised…
    mockMvc
        .perform(
            get("/api/collections/debtors")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_read_invoicer")))
        .andExpect(status().isOk());
    // …but the summary is gated on FINANCIAL_VISIBILITY (§13.9.3), which the member lacks.
    mockMvc
        .perform(
            get("/api/invoices/outstanding-summary")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_read_invoicer")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }
}
