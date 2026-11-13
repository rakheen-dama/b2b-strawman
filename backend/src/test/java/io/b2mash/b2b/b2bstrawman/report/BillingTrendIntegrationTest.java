package io.b2mash.b2b.b2bstrawman.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET /api/reports/billing-trend} (Phase 90 643B, ADR-372, arch §15.3.8): fees billed (Σ
 * {@code invoices.total} by issue month, APPROVED/SENT/PARTIALLY_PAID/PAID) vs collected (Σ
 * COMPLETED {@code payment_events.amount} by SA payment month) over the last {@code months} SA
 * calendar months, org default currency only, oldest first, zero-filled. Gated like {@code
 * /api/reports/profitability}: {@code FINANCIAL_VISIBILITY} (403); {@code months} outside 1–24 →
 * 400.
 *
 * <p>Seeded through REST only, in this class's own ZA tenant (default currency ZAR). REST can only
 * place rows in the <b>current</b> SA month: {@code issue_date} is stamped at approve from the
 * clock and no request sets it, and {@code payment_events.created_at} is {@code Instant.now()} in
 * {@code @PrePersist}. So the current-month point must equal the sums of the individual fee-note
 * and payment-event reads and every earlier point is zero; multi-month bucketing and the
 * SA-midnight boundary are proven in {@link BillingTrendServiceTest}. Everything is seeded once in
 * {@code @BeforeAll}; every test is read-only (the endpoint aggregates tenant-wide).
 *
 * <p>Known residual risk (as in 643A): a run straddling SA midnight at a month end can put a row in
 * a different month than this class's {@code current}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingTrendIntegrationTest {
  private static final String URL = "/api/reports/billing-trend";

  private static final String ORG_ID = "org_report_billing_trend";
  private static final String OWNER = "user_rbt_owner";

  /** Records the fee transfers so the owner can approve them (self-approval is refused). */
  private static final String ADMIN = "user_rbt_admin";

  /** System member — no FINANCIAL_VISIBILITY. */
  private static final String MEMBER = "user_rbt_member";

  /** Custom role holding FINANCIAL_VISIBILITY only. */
  private static final String FV = "user_rbt_fv";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private final LocalDate today = LocalDate.now(KaziTime.ZONE);
  private final YearMonth current = YearMonth.from(today);

  private String tenantSchema;
  private UUID ownerId;
  private UUID clientId;
  private UUID matterId;
  private String accountId;

  private String fullEft; // N1 — PAID by one EFT
  private String partEft; // N2 — PARTIALLY_PAID, 500.00
  private String approvedUnsent; // N3 — APPROVED only
  private String draft; // N4 — DRAFT
  private String voided; // N5 — VOID after send
  private String trustTransferred; // N6 — PAID by an approved trust fee transfer
  private String reversed; // N7 — fee transfer approved then reversed → SENT
  private String otherCurrency; // N8 — USD, PAID by one EFT

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Billing Trend Org", null, "ZA").schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "rbt_owner@test.com", "RBT Owner", "owner"));
    TestMemberHelper.syncMember(mockMvc, ORG_ID, ADMIN, "rbt_admin@test.com", "RBT Admin", "admin");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER, "rbt_member@test.com", "RBT Member", "member");
    String fvId =
        TestMemberHelper.syncMember(mockMvc, ORG_ID, FV, "rbt_fv@test.com", "RBT FV", "member");
    enableTrustAccounting();

    var owner = owner();
    var admin = TestJwtFactory.adminJwt(ORG_ID, ADMIN);
    assignRole(
        owner, fvId, createCustomRole(owner, "RBT Financial Visibility", "FINANCIAL_VISIBILITY"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      clientId =
                          customerRepository
                              .save(
                                  TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                                      "Billing Trend Client", "rbt_client@test.com", ownerId))
                              .getId();
                      var matter = new Project("Billing Trend Matter", "643B fixture", ownerId);
                      matter.setCustomerId(clientId);
                      matterId = projectRepository.save(matter).getId();
                      customerProjectRepository.save(
                          new CustomerProject(clientId, matterId, ownerId));
                    }));

    accountId = createTrustAccount(owner);
    deposit(owner, "20000.00");

    // N1: full EFT.
    fullEft = feeNote("ZAR", "1000.00");
    approve(fullEft);
    send(fullEft);
    pay(fullEft, balanceDue(fullEft), "EFT-RBT-1");

    // N2: part EFT.
    partEft = feeNote("ZAR", "2000.00");
    approve(partEft);
    send(partEft);
    pay(partEft, new BigDecimal("500.00"), "EFT-RBT-2");

    // N3: approved, never sent.
    approvedUnsent = feeNote("ZAR", "3000.00");
    approve(approvedUnsent);

    // N4: draft.
    draft = feeNote("ZAR", "4000.00");

    // N5: approved, sent, voided.
    voided = feeNote("ZAR", "5000.00");
    approve(voided);
    send(voided);
    postOk(owner, "/api/invoices/" + voided + "/void", null);

    // N6: trust fee transfer (fee note must be SENT for the approval to post the payment).
    trustTransferred = feeNote("ZAR", "1500.00");
    approve(trustTransferred);
    send(trustTransferred);
    approveTrust(feeTransfer(admin, trustTransferred, total(trustTransferred), "FT-RBT-1"));

    // N7: trust fee transfer approved then reversed — the payment event is deleted.
    reversed = feeNote("ZAR", "700.00");
    approve(reversed);
    send(reversed);
    String reversedTxn = feeTransfer(admin, reversed, total(reversed), "FT-RBT-REV");
    approveTrust(reversedTxn);
    reverseTrust(admin, reversedTxn);

    // N8: second currency, fully paid.
    otherCurrency = feeNote("USD", "9000.00");
    approve(otherCurrency);
    send(otherCurrency);
    pay(otherCurrency, balanceDue(otherCurrency), "EFT-RBT-USD");
  }

  @Test
  void defaultWindowIsTwelveSaMonthsOldestFirst() throws Exception {
    String body =
        mockMvc
            .perform(get(URL).with(owner()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.months", hasSize(12)))
            .andExpect(jsonPath("$.from").value(current.minusMonths(11).toString()))
            .andExpect(jsonPath("$.to").value(current.toString()))
            .andExpect(jsonPath("$.asOf").value(today.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> expectedKeys = new ArrayList<>();
    for (YearMonth m = current.minusMonths(11); !m.isAfter(current); m = m.plusMonths(1)) {
      expectedKeys.add(m.toString());
    }
    assertThat(JsonPath.<List<String>>read(body, "$.months[*].month"))
        .containsExactlyElementsOf(expectedKeys);
    for (int i = 0; i < 12; i++) {
      assertThat(JsonPath.<Boolean>read(body, "$.months[%d].partial".formatted(i)))
          .isEqualTo(i == 11);
    }
  }

  @Test
  void currentMonthBilledEqualsTheSumOfTheIndividualFeeNoteReads() throws Exception {
    String body = trend(12);
    BigDecimal expected = BigDecimal.ZERO;
    for (String id : List.of(fullEft, partEft, approvedUnsent, trustTransferred, reversed)) {
      expected = expected.add(total(id));
    }
    assertThat(decimal(body, "$.months[11].billed")).isEqualByComparingTo(expected);
    assertThat(JsonPath.<Integer>read(body, "$.months[11].billedCount")).isEqualTo(5);
  }

  @Test
  void draftAndVoidFeeNotesAreExcluded() throws Exception {
    String draftRead = read(owner(), "/api/invoices/" + draft);
    assertThat(JsonPath.<String>read(draftRead, "$.status")).isEqualTo("DRAFT");
    String voidRead = read(owner(), "/api/invoices/" + voided);
    assertThat(JsonPath.<String>read(voidRead, "$.status")).isEqualTo("VOID");
    // The void note keeps its issue date: the status filter, not a null date, excludes it.
    assertThat(JsonPath.<String>read(voidRead, "$.issueDate")).isEqualTo(today.toString());

    BigDecimal billed = decimal(trend(12), "$.months[11].billed");
    BigDecimal withoutExcluded = BigDecimal.ZERO;
    for (String id : List.of(fullEft, partEft, approvedUnsent, trustTransferred, reversed)) {
      withoutExcluded = withoutExcluded.add(total(id));
    }
    assertThat(billed).isEqualByComparingTo(withoutExcluded);
    assertThat(billed)
        .isNotEqualByComparingTo(withoutExcluded.add(total(draft)))
        .isNotEqualByComparingTo(withoutExcluded.add(total(voided)));
  }

  @Test
  void approvedUnsentFeeNoteCountsAsBilled() throws Exception {
    String read = read(owner(), "/api/invoices/" + approvedUnsent);
    assertThat(JsonPath.<String>read(read, "$.status")).isEqualTo("APPROVED");
    assertThat(JsonPath.<String>read(read, "$.issueDate")).isEqualTo(today.toString());

    BigDecimal others = BigDecimal.ZERO;
    for (String id : List.of(fullEft, partEft, trustTransferred, reversed)) {
      others = others.add(total(id));
    }
    assertThat(decimal(trend(12), "$.months[11].billed").subtract(others))
        .isEqualByComparingTo(total(approvedUnsent));
  }

  @Test
  void currentMonthCollectedEqualsTheSumOfCompletedPaymentEventReads() throws Exception {
    BigDecimal expected = BigDecimal.ZERO;
    int count = 0;
    for (String id :
        List.of(fullEft, partEft, approvedUnsent, draft, voided, trustTransferred, reversed)) {
      for (BigDecimal amount : completedPayments(id)) {
        expected = expected.add(amount);
        count++;
      }
    }
    assertThat(count).isEqualTo(3);

    String body = trend(12);
    assertThat(decimal(body, "$.months[11].collected")).isEqualByComparingTo(expected);
    assertThat(JsonPath.<Integer>read(body, "$.months[11].collectedCount")).isEqualTo(3);
    assertThat(expected)
        .isEqualByComparingTo(
            total(fullEft).add(new BigDecimal("500.00")).add(total(trustTransferred)));
  }

  @Test
  void partPaymentCountsAtItsAmountInItsMonth() throws Exception {
    assertThat(JsonPath.<String>read(read(owner(), "/api/invoices/" + partEft), "$.status"))
        .isEqualTo("PARTIALLY_PAID");
    assertThat(completedPayments(partEft))
        .singleElement()
        .satisfies(amount -> assertThat(amount).isEqualByComparingTo("500.00"));

    BigDecimal collected = decimal(trend(12), "$.months[11].collected");
    assertThat(collected)
        .isEqualByComparingTo(
            total(fullEft).add(new BigDecimal("500.00")).add(total(trustTransferred)));
  }

  @Test
  void trustFeeTransferCountsAsCollected() throws Exception {
    String events = read(owner(), "/api/invoices/" + trustTransferred + "/payment-events");
    assertThat(JsonPath.<List<String>>read(events, "$[*].providerSlug"))
        .containsExactly("trust_fee_transfer");
    assertThat(JsonPath.<List<String>>read(events, "$[*].status")).containsExactly("COMPLETED");
    assertThat(
            JsonPath.<String>read(read(owner(), "/api/invoices/" + trustTransferred), "$.status"))
        .isEqualTo("PAID");

    BigDecimal collected = decimal(trend(12), "$.months[11].collected");
    assertThat(collected.subtract(total(fullEft)).subtract(new BigDecimal("500.00")))
        .isEqualByComparingTo(total(trustTransferred));
  }

  @Test
  void reversedFeeTransferDropsOutOfCollected() throws Exception {
    String events = read(owner(), "/api/invoices/" + reversed + "/payment-events");
    assertThat(JsonPath.<List<Object>>read(events, "$")).isEmpty();
    String read = read(owner(), "/api/invoices/" + reversed);
    assertThat(JsonPath.<String>read(read, "$.status")).isEqualTo("SENT");

    String body = trend(12);
    // Still billed …
    BigDecimal others = BigDecimal.ZERO;
    for (String id : List.of(fullEft, partEft, approvedUnsent, trustTransferred)) {
      others = others.add(total(id));
    }
    assertThat(decimal(body, "$.months[11].billed").subtract(others))
        .isEqualByComparingTo(total(reversed));
    // … but not collected.
    assertThat(decimal(body, "$.months[11].collected"))
        .isEqualByComparingTo(
            total(fullEft).add(new BigDecimal("500.00")).add(total(trustTransferred)));
  }

  @Test
  void otherCurrencyIsExcludedAndCurrencyIsTheOrgDefault() throws Exception {
    var owner = owner();
    String settings = read(owner, "/api/settings");
    String body = trend(12);
    assertThat(JsonPath.<String>read(body, "$.currency"))
        .isEqualTo("ZAR")
        .isEqualTo(JsonPath.<String>read(settings, "$.defaultCurrency"));

    String usd = read(owner, "/api/invoices/" + otherCurrency);
    assertThat(JsonPath.<String>read(usd, "$.status")).isEqualTo("PAID");
    assertThat(JsonPath.<String>read(usd, "$.currency")).isEqualTo("USD");
    String usdEvents = read(owner, "/api/invoices/" + otherCurrency + "/payment-events");
    assertThat(JsonPath.<List<String>>read(usdEvents, "$[*].currency")).containsExactly("USD");

    BigDecimal zarBilled = BigDecimal.ZERO;
    for (String id : List.of(fullEft, partEft, approvedUnsent, trustTransferred, reversed)) {
      zarBilled = zarBilled.add(total(id));
    }
    BigDecimal zarCollected =
        total(fullEft).add(new BigDecimal("500.00")).add(total(trustTransferred));
    assertThat(decimal(body, "$.months[11].billed")).isEqualByComparingTo(zarBilled);
    assertThat(decimal(body, "$.months[11].collected")).isEqualByComparingTo(zarCollected);
    for (int i = 0; i < 11; i++) {
      assertThat(decimal(body, "$.months[%d].billed".formatted(i))).isZero();
      assertThat(decimal(body, "$.months[%d].collected".formatted(i))).isZero();
    }
  }

  @Test
  void earlierMonthsAreZeroFilled() throws Exception {
    String body = trend(12);
    for (int i = 0; i < 11; i++) {
      assertThat(decimal(body, "$.months[%d].billed".formatted(i))).isZero();
      assertThat(JsonPath.<Integer>read(body, "$.months[%d].billedCount".formatted(i))).isZero();
      assertThat(decimal(body, "$.months[%d].collected".formatted(i))).isZero();
      assertThat(JsonPath.<Integer>read(body, "$.months[%d].collectedCount".formatted(i))).isZero();
    }
  }

  @Test
  void singleMonthWindowIsTheCurrentMonth() throws Exception {
    String twelve = trend(12);
    String one = trend(1);
    assertThat(JsonPath.<List<String>>read(one, "$.months[*].month"))
        .containsExactly(current.toString());
    assertThat(JsonPath.<String>read(one, "$.from")).isEqualTo(current.toString());
    assertThat(JsonPath.<Boolean>read(one, "$.months[0].partial")).isTrue();
    assertThat(decimal(one, "$.months[0].billed"))
        .isEqualByComparingTo(decimal(twelve, "$.months[11].billed"));
    assertThat(decimal(one, "$.months[0].collected"))
        .isEqualByComparingTo(decimal(twelve, "$.months[11].collected"));
    assertThat(JsonPath.<Integer>read(one, "$.months[0].billedCount")).isEqualTo(5);
    assertThat(JsonPath.<Integer>read(one, "$.months[0].collectedCount")).isEqualTo(3);
  }

  @Test
  void twentyFourMonthWindow() throws Exception {
    String body = trend(24);
    assertThat(JsonPath.<String>read(body, "$.from")).isEqualTo(current.minusMonths(23).toString());
    assertThat(JsonPath.<String>read(body, "$.months[23].month")).isEqualTo(current.toString());
    assertThat(JsonPath.<Integer>read(body, "$.months[23].billedCount")).isEqualTo(5);
  }

  @Test
  void zeroMonthsIsRejected() throws Exception {
    assertMonthsRejected(0);
  }

  @Test
  void twentyFiveMonthsIsRejected() throws Exception {
    assertMonthsRejected(25);
  }

  @Test
  void memberWithoutFinancialVisibilityIsForbidden() throws Exception {
    mockMvc
        .perform(get(URL).with(TestJwtFactory.memberJwt(ORG_ID, MEMBER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void financialVisibilityCustomRoleSeesTheTrend() throws Exception {
    mockMvc
        .perform(get(URL).with(TestJwtFactory.memberJwt(ORG_ID, FV)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("ZAR"))
        .andExpect(jsonPath("$.months", hasSize(12)));
  }

  // --- helpers ---

  private JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private void assertMonthsRejected(int months) throws Exception {
    mockMvc
        .perform(get(URL).param("months", String.valueOf(months)).with(owner()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid months"))
        .andExpect(jsonPath("$.detail").value("'months' must be between 1 and 24"));
  }

  private String trend(int months) throws Exception {
    return mockMvc
        .perform(get(URL).param("months", String.valueOf(months)).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.months", hasSize(months)))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static BigDecimal decimal(String json, String path) {
    return new BigDecimal(String.valueOf((Object) JsonPath.read(json, path)));
  }

  private String read(JwtRequestPostProcessor jwt, String url) throws Exception {
    return mockMvc
        .perform(get(url).with(jwt))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** POST (optional JSON body) expecting 200; returns the response body. */
  private String postOk(JwtRequestPostProcessor jwt, String url, String jsonBody) throws Exception {
    var request = post(url).with(jwt);
    if (jsonBody != null) {
      request = request.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
    }
    return mockMvc
        .perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private BigDecimal total(String invoiceId) throws Exception {
    return decimal(read(owner(), "/api/invoices/" + invoiceId), "$.total");
  }

  private BigDecimal balanceDue(String invoiceId) throws Exception {
    return decimal(read(owner(), "/api/invoices/" + invoiceId), "$.balanceDue");
  }

  /** Amounts of the fee note's COMPLETED payment events, from {@code /payment-events}. */
  private List<BigDecimal> completedPayments(String invoiceId) throws Exception {
    String events = read(owner(), "/api/invoices/" + invoiceId + "/payment-events");
    List<Object> amounts = JsonPath.read(events, "$[?(@.status == 'COMPLETED')].amount");
    return amounts.stream().map(a -> new BigDecimal(String.valueOf(a))).toList();
  }

  /** A draft fee note with one line on the fixture matter; returns its id. */
  private String feeNote(String currency, String unitPrice) throws Exception {
    var created =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"clientId\": \"%s\", \"currency\": \"%s\"}"
                            .formatted(clientId, currency)))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    mockMvc
        .perform(
            post("/api/invoices/" + id + "/lines")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"matterId": "%s", "description": "Consultation", "quantity": 1.0000,
                     "unitPrice": %s, "sortOrder": 0}
                    """
                        .formatted(matterId, unitPrice)))
        .andExpect(status().isCreated());
    return id;
  }

  private void approve(String invoiceId) throws Exception {
    postOk(owner(), "/api/invoices/" + invoiceId + "/approve", null);
  }

  private void send(String invoiceId) throws Exception {
    postOk(owner(), "/api/invoices/" + invoiceId + "/send", null);
  }

  private void pay(String invoiceId, BigDecimal amount, String reference) throws Exception {
    postOk(
        owner(),
        "/api/invoices/" + invoiceId + "/payment",
        "{\"amount\": %s, \"paymentMethod\": \"EFT\", \"paymentReference\": \"%s\"}"
            .formatted(amount.toPlainString(), reference));
  }

  private String createTrustAccount(JwtRequestPostProcessor jwt) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Billing Trend Trust",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000643",
                          "accountType": "GENERAL",
                          "isPrimary": true,
                          "requireDualApproval": false,
                          "openedDate": "%s"
                        }
                        """
                            .formatted(today.minusYears(2))))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  private void deposit(JwtRequestPostProcessor jwt, String amount) throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + accountId + "/transactions/deposit")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": %s,
                      "reference": "DEP-RBT-1",
                      "description": "Billing trend fixture",
                      "transactionDate": "%s"
                    }
                    """
                        .formatted(clientId, matterId, amount, today)))
        .andExpect(status().isCreated());
  }

  /** Admin records a fee transfer for the whole fee note; returns the trust transaction id. */
  private String feeTransfer(
      JwtRequestPostProcessor jwt, String invoiceId, BigDecimal amount, String reference)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + accountId + "/transactions/fee-transfer")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clientId": "%s", "invoiceId": "%s", "amount": %s, "reference": "%s"}
                        """
                            .formatted(clientId, invoiceId, amount.toPlainString(), reference)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  /** Owner approves a debit the admin recorded (self-approval is refused). */
  private void approveTrust(String transactionId) throws Exception {
    mockMvc
        .perform(post("/api/trust-transactions/" + transactionId + "/approve").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  private void reverseTrust(JwtRequestPostProcessor jwt, String transactionId) throws Exception {
    mockMvc
        .perform(
            post("/api/trust-transactions/" + transactionId + "/reverse")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Filed against the wrong fee note\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("REVERSAL"));
  }

  private String createCustomRole(JwtRequestPostProcessor owner, String name, String capability)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":["%s"]}"""
                            .formatted(name, capability)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(JwtRequestPostProcessor owner, String memberId, String roleId)
      throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  private void enableTrustAccounting() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
  }
}
