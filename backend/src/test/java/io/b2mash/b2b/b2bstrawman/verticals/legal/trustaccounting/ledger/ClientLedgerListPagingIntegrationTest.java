package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * F-667A-10: the client-ledgers list pages and filters on the server. One trust account in its own
 * tenant holds 25 client ledgers — 22 non-zero (a tie group straddles the page-1/page-2 boundary)
 * and 3 settled to zero — so every count below is scoped to that account and cannot bleed in from
 * other tests. {@code nonZeroOnly} and {@code search} must narrow the whole set, and the page
 * metadata must describe the narrowed set.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientLedgerListPagingIntegrationTest {
  private static final String ORG_ID = "org_client_ledger_paging_test";
  private static final String OWNER_SUB = "user_ledger_paging_owner";
  private static final String APPROVER_SUB = "user_ledger_paging_approver";
  private static final String ZEBRA = "ZEBRA Pager Holdings";

  @Autowired private MockMvc mockMvc;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String trustAccountId;

  /** Client id → name, every seeded client (25). */
  private final Map<String, String> clients = new LinkedHashMap<>();

  private final Set<String> zeroBalanceIds = new HashSet<>();
  private String zebraId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Client Ledger Paging Test Org", null)
            .schemaName();
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER_SUB, "ledger_paging_owner@test.com", "Paging Owner", "owner");
    // Single-approval mode refuses self-approval: a second owner approves the payments
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        APPROVER_SUB,
        "ledger_paging_approver@test.com",
        "Paging Approver",
        "owner");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB);
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Ledger Paging Test Trust Account",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000091",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = TestEntityHelper.extractId(accountResult);

    // Ranks 0-14: distinct balances 2150..2010
    for (int i = 1; i <= 15; i++) {
      seedClient("Pager Client %02d".formatted(i), new BigDecimal(2000 + (16 - i) * 10), false);
    }
    // Ranks 15-20: one tie group (R1000) across the page boundary at index 19/20
    for (int i = 16; i <= 21; i++) {
      seedClient("Pager Client %02d".formatted(i), new BigDecimal("1000.00"), false);
    }
    // Rank 21: the lowest non-zero balance, on page 2 of the unfiltered list
    zebraId = seedClient(ZEBRA, new BigDecimal("10.00"), false);
    // Ranks 22-24: settled to zero (deposit, then an approved payment of the same amount)
    for (int i = 1; i <= 3; i++) {
      zeroBalanceIds.add(
          seedClient("Pager Settled %02d".formatted(i), new BigDecimal("500.00"), true));
    }
    assertThat(clients).hasSize(25);
  }

  @Test
  void unfilteredList_pagesOnTheServer_withoutDroppingOrDuplicatingTiedRows() throws Exception {
    var page0 = list(0, Map.of());
    assertThat(page0.totalElements()).isEqualTo(25);
    assertThat(page0.totalPages()).isEqualTo(2);
    assertThat(page0.ids()).hasSize(20);

    var page1 = list(1, Map.of());
    assertThat(page1.totalElements()).isEqualTo(25);
    assertThat(page1.ids()).hasSize(5);
    assertThat(page1.ids()).contains(zebraId).containsAll(zeroBalanceIds);

    var all = new ArrayList<>(page0.ids());
    all.addAll(page1.ids());
    assertThat(all).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(clients.keySet());
  }

  @Test
  void nonZeroOnly_filtersEveryLedger_andTheTotalsDescribeTheFilteredSet() throws Exception {
    var page0 = list(0, Map.of("nonZeroOnly", "true"));
    assertThat(page0.totalElements()).isEqualTo(22);
    assertThat(page0.totalPages()).isEqualTo(2);
    assertThat(page0.ids()).hasSize(20).doesNotContainAnyElementsOf(zeroBalanceIds);

    var page1 = list(1, Map.of("nonZeroOnly", "true"));
    assertThat(page1.ids())
        .hasSize(2)
        .contains(zebraId)
        .doesNotContainAnyElementsOf(zeroBalanceIds);
    assertThat(page1.balances()).allSatisfy(b -> assertThat(b).isNotZero());
  }

  @Test
  void search_isCaseInsensitiveContainsOnClientName_acrossAllPages() throws Exception {
    // Zebra sits on page 2 of the unfiltered list; the search finds it on page 1
    var zebra = list(0, Map.of("search", "zebra"));
    assertThat(zebra.totalElements()).isEqualTo(1);
    assertThat(zebra.totalPages()).isEqualTo(1);
    assertThat(zebra.ids()).containsExactly(zebraId);

    var settled = list(0, Map.of("search", "pager SETTLED"));
    assertThat(settled.totalElements()).isEqualTo(3);
    assertThat(settled.ids()).containsExactlyInAnyOrderElementsOf(zeroBalanceIds);

    var middle = list(0, Map.of("search", "client 2"));
    assertThat(middle.totalElements()).isEqualTo(2); // Pager Client 20, 21
  }

  @Test
  void searchAndNonZeroOnly_combine() throws Exception {
    assertThat(list(0, Map.of("search", "settled", "nonZeroOnly", "true")).totalElements())
        .isZero();
    var pager = list(0, Map.of("search", "pager", "nonZeroOnly", "true"));
    assertThat(pager.totalElements()).isEqualTo(22);
    assertThat(pager.totalPages()).isEqualTo(2);
  }

  @Test
  void search_treatsLikeWildcardsLiterally_andBlankMeansNoFilter() throws Exception {
    assertThat(list(0, Map.of("search", "%")).totalElements()).isZero();
    assertThat(list(0, Map.of("search", "_")).totalElements()).isZero();
    assertThat(list(0, Map.of("search", "")).totalElements()).isEqualTo(25);
    assertThat(list(0, Map.of("nonZeroOnly", "false")).totalElements()).isEqualTo(25);
  }

  // --- helpers ---

  private record ListPage(
      long totalElements, int totalPages, List<String> ids, List<BigDecimal> balances) {}

  private ListPage list(int page, Map<String, String> params) throws Exception {
    MockHttpServletRequestBuilder request =
        get("/api/trust-accounts/" + trustAccountId + "/client-ledgers")
            .param("page", String.valueOf(page))
            .param("size", "20")
            // The UI's sort param (actions.ts) is still sent and still ignored by the native query
            .param("sort", "balance,desc")
            .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB));
    params.forEach(request::param);
    String body =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(body, "$.content[*].clientId");
    List<Number> balances = JsonPath.read(body, "$.content[*].balance");
    return new ListPage(
        ((Number) JsonPath.read(body, "$.page.totalElements")).longValue(),
        JsonPath.read(body, "$.page.totalPages"),
        ids,
        balances.stream().map(n -> new BigDecimal(n.toString())).toList());
  }

  private String seedClient(String name, BigDecimal amount, boolean settle) throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB);
    String email = name.toLowerCase().replaceAll("[^a-z0-9]", "_") + "@paging.test";
    String clientId = TestEntityHelper.createCustomer(mockMvc, ownerJwt, name, email);
    UUID matterId =
        TestTrustMatterHelper.openClientMatter(
            tenantSchema,
            transactionTemplate,
            projectRepository,
            customerRepository,
            UUID.fromString(clientId),
            "Trust matter " + name);
    String body =
        """
        {
          "clientId": "%s",
          "matterId": "%s",
          "amount": %s,
          "reference": "%s",
          "description": "Paging test",
          "transactionDate": "2026-03-01"
        }
        """;
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body.formatted(clientId, matterId, amount.toPlainString(), "DEP-" + email)))
        .andExpect(status().isCreated());
    if (settle) {
      var payment =
          mockMvc
              .perform(
                  post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                      .with(ownerJwt)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          body.formatted(
                              clientId, matterId, amount.toPlainString(), "PAY-" + email)))
              .andExpect(status().isCreated())
              .andReturn();
      String paymentId = JsonPath.read(payment.getResponse().getContentAsString(), "$.id");
      mockMvc
          .perform(
              post("/api/trust-transactions/" + paymentId + "/approve")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB)))
          .andExpect(status().isOk());
    }
    clients.put(clientId, name);
    return clientId;
  }
}
