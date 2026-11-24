package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.cancel;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.create;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.detail;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.mapTargets;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rows;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.validate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 93 653A (§17.9, §17.10 X1): {@code MANAGE_TAKE_ON} gates every endpoint and the dataset's
 * own capability gates its batches — a custom role holding {@code MANAGE_TAKE_ON} without {@code
 * VIEW_TRUST} cannot read trust balances from the row table, nor debtor amounts without {@code
 * INVOICING}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnAccessPolicyIntegrationTest {

  private static final String ORG_ID = "org_takeon_access_test";
  private static final String OWNER = "user_toa_owner";
  private static final String ADMIN = "user_toa_admin";
  private static final String CAROL = "user_toa_carol";
  private static final String CLIENTS_ONLY = "user_toa_clients";
  private static final String TRUST_VIEWER = "user_toa_trustview";
  private static final String TRUST_MANAGER = "user_toa_trustmanage";
  private static final String TRUST_READER = "user_toa_trustread";
  private static final String ACCESS_DENIED = "Access denied";
  private static final String CLIENTS_CSV =
      "Client name,Email,Client type,CDD completed date,Beneficial ownership,Screening,Records\n"
          + "A,a@example.test,Individual,01/02/2024,Done,Not screened,Box\n";
  private static final String LEDGER_CSV =
      "Client reference,Matter reference,Balance at cutover\nC-1,M-1,1000.00\n";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private UUID[] clientsBatch;
  private UUID[] trustBatch;
  private UUID[] debtorBatch;
  private String trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Take-on Access", "legal-za");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "toa_owner@test.com", "TOA Owner", "owner");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, ADMIN, "toa_admin@test.com", "TOA Admin", "admin");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, CAROL, "toa_carol@test.com", "TOA Carol", "member");
    UUID clientsOnly =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, CLIENTS_ONLY, "toa_clients@test.com", "TOA Clients", "member"));
    UUID trustViewer =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, TRUST_VIEWER, "toa_trust@test.com", "TOA Trust", "member"));
    assignRole(clientsOnly, createRole("Clients take-on", "MANAGE_TAKE_ON", "CUSTOMER_MANAGEMENT"));
    assignRole(trustViewer, createRole("Trust take-on viewer", "MANAGE_TAKE_ON", "VIEW_TRUST"));
    UUID trustManager =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, TRUST_MANAGER, "toa_trustm@test.com", "TOA TrustM", "member"));
    assignRole(trustManager, createRole("Trust take-on writer", "MANAGE_TAKE_ON", "MANAGE_TRUST"));
    UUID trustReader =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, TRUST_READER, "toa_trustr@test.com", "TOA TrustR", "member"));
    assignRole(trustReader, createRole("Trust reader", "VIEW_TRUST"));
    var account =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"accountName": "Access Trust", "bankName": "First National Bank",
                         "branchCode": "250655", "accountNumber": "62000029653",
                         "accountType": "GENERAL", "isPrimary": false,
                         "requireDualApproval": false, "openedDate": "2025-01-01"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = TestEntityHelper.extractId(account);

    clientsBatch =
        create(mockMvc, owner(), "CLIENTS", "clients.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    trustBatch =
        create(
            mockMvc,
            owner(),
            "TRUST_POSITION",
            "ledger.csv",
            TakeOnTestFiles.csv(LEDGER_CSV),
            "section",
            "CLIENT_LEDGER",
            "trustAccountId",
            trustAccountId);
    mapTargets(
        mockMvc,
        owner(),
        trustBatch[0],
        trustBatch[1],
        Map.of(
            "clientRef", "Client reference",
            "matterRef", "Matter reference",
            "balance", "Balance at cutover"));
    debtorBatch =
        create(
            mockMvc,
            owner(),
            "DEBTOR_BALANCES",
            "debtors.csv",
            TakeOnTestFiles.csv(
                "Client reference,Fee note number,Issue date,Outstanding amount\n"
                    + "C-1,FN-1,01/09/2026,10.00\n"));
  }

  @Test
  void aClientsRoleCreatesAndReadsAClientsBatch() throws Exception {
    UUID[] own =
        create(mockMvc, clientsOnly(), "CLIENTS", "mine.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    detail(mockMvc, clientsOnly(), own[0]).andExpect(status().isOk());
    rows(mockMvc, clientsOnly(), clientsBatch[0], null).andExpect(status().isOk());
  }

  @Test
  void aRoleWithoutViewTrustCannotReadATrustBatch() throws Exception {
    denied(detail(mockMvc, clientsOnly(), trustBatch[0]));
    denied(rows(mockMvc, clientsOnly(), trustBatch[0], null));
    denied(
        mockMvc.perform(
            get("/api/take-on/batches/" + trustBatch[0] + "/sources/" + trustBatch[1] + "/preview")
                .with(clientsOnly())));
    denied(
        mockMvc.perform(
            get("/api/take-on/batches/" + trustBatch[0] + "/sources/" + trustBatch[1] + "/file")
                .with(clientsOnly())));
  }

  @Test
  void aRoleWithoutManageTrustCannotCreateATrustBatch() throws Exception {
    denied(
        TakeOnTestApi.upload(
            mockMvc,
            clientsOnly(),
            "TRUST_POSITION",
            "ledger.csv",
            TakeOnTestApi.CSV,
            TakeOnTestFiles.csv(LEDGER_CSV),
            "section",
            "CLIENT_LEDGER",
            "trustAccountId",
            trustAccountId));
  }

  @Test
  void debtorRowsNeedInvoicing() throws Exception {
    denied(rows(mockMvc, clientsOnly(), debtorBatch[0], null));
    rows(mockMvc, admin(), debtorBatch[0], null).andExpect(status().isOk());
  }

  @Test
  void theHistoryListsOnlyDatasetsTheCallerMayRead() throws Exception {
    List<String> ids = historyIds(clientsOnly());
    assertThat(ids)
        .contains(clientsBatch[0].toString())
        .doesNotContain(trustBatch[0].toString(), debtorBatch[0].toString());
    assertThat(historyIds(owner()))
        .contains(clientsBatch[0].toString(), trustBatch[0].toString(), debtorBatch[0].toString());
    assertThat(historyIds(trustViewer()))
        .contains(trustBatch[0].toString())
        .doesNotContain(clientsBatch[0].toString(), debtorBatch[0].toString());
  }

  @Test
  void viewTrustReadsTrustRowsButCannotValidateOrCancel() throws Exception {
    rows(mockMvc, trustViewer(), trustBatch[0], null).andExpect(status().isOk());
    detail(mockMvc, trustViewer(), trustBatch[0]).andExpect(status().isOk());
    denied(validate(mockMvc, trustViewer(), trustBatch[0]));
    denied(cancel(mockMvc, trustViewer(), trustBatch[0]));
    denied(
        mockMvc.perform(
            put("/api/take-on/batches/" + trustBatch[0] + "/position")
                .with(trustViewer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cutoverDate\":\"2026-10-31\"}")));
    detail(mockMvc, owner(), trustBatch[0]).andExpect(jsonPath("$.status").value("MAPPED"));
  }

  @Test
  void manageTrustWithoutViewTrustCannotWriteATrustBatch() throws Exception {
    // 653A review (decision D-44): a trust writer must also read trust — the upload step lists
    // the trust accounts (VIEW_TRUST) and every write returns the batch.
    denied(
        TakeOnTestApi.upload(
            mockMvc,
            trustManager(),
            "TRUST_POSITION",
            "ledger.csv",
            TakeOnTestApi.CSV,
            TakeOnTestFiles.csv(LEDGER_CSV),
            "section",
            "CLIENT_LEDGER",
            "trustAccountId",
            trustAccountId));
    denied(validate(mockMvc, trustManager(), trustBatch[0]));
    denied(cancel(mockMvc, trustManager(), trustBatch[0]));
    denied(rows(mockMvc, trustManager(), trustBatch[0], null));
    detail(mockMvc, owner(), trustBatch[0]).andExpect(jsonPath("$.status").value("MAPPED"));
  }

  @Test
  void aSystemMemberIsRefusedEveryTakeOnEndpoint() throws Exception {
    denied(mockMvc.perform(get("/api/take-on/datasets").with(carol())));
    denied(mockMvc.perform(get("/api/take-on/templates/CLIENTS").with(carol())));
    denied(mockMvc.perform(get("/api/take-on/batches").with(carol())));
    denied(detail(mockMvc, carol(), clientsBatch[0]));
    denied(rows(mockMvc, carol(), clientsBatch[0], null));
    denied(
        TakeOnTestApi.upload(
            mockMvc,
            carol(),
            "CLIENTS",
            "c.csv",
            TakeOnTestApi.CSV,
            TakeOnTestFiles.csv(CLIENTS_CSV)));
    denied(cancel(mockMvc, carol(), clientsBatch[0]));
  }

  @Test
  void anAdminHoldsEveryDatasetCapability() throws Exception {
    mockMvc.perform(get("/api/take-on/datasets").with(admin())).andExpect(status().isOk());
    detail(mockMvc, admin(), trustBatch[0]).andExpect(status().isOk());
    rows(mockMvc, admin(), clientsBatch[0], null).andExpect(status().isOk());
  }

  @Test
  void templatesAndTheCatalogueNeedOnlyManageTakeOn() throws Exception {
    mockMvc.perform(get("/api/take-on/datasets").with(trustViewer())).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/take-on/templates/DEBTOR_BALANCES").with(trustViewer()))
        .andExpect(status().isOk());
  }

  @Test
  void aMissingBatchIsNotFoundBeforeItIsForbidden() throws Exception {
    detail(mockMvc, clientsOnly(), UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Take-on batch not found"));
  }

  // --- helpers ---

  // 657C (§17.9, X1): the take-on reports are VIEW_TRUST — never MANAGE_TAKE_ON alone, and never
  // FINANCIAL_VISIBILITY (ReportingController)
  @Test
  void theTakeOnReportsNeedViewTrust() throws Exception {
    TakeOnTestApi.reports(mockMvc, trustViewer(), null).andExpect(status().isOk());
    TakeOnTestApi.reports(mockMvc, trustViewer(), UUID.fromString(trustAccountId))
        .andExpect(status().isOk());
    TakeOnTestApi.report(mockMvc, trustViewer(), UUID.randomUUID())
        .andExpect(status().isNotFound());
    // VIEW_TRUST alone, without MANAGE_TAKE_ON: the list is read; an unknown report is not found,
    // never forbidden (a real report's 200 is TakeOnReportIntegrationTest's)
    TakeOnTestApi.reports(mockMvc, trustReader(), null).andExpect(status().isOk());
    TakeOnTestApi.reports(mockMvc, trustReader(), UUID.fromString(trustAccountId))
        .andExpect(status().isOk());
    TakeOnTestApi.report(mockMvc, trustReader(), UUID.randomUUID())
        .andExpect(status().isNotFound());
    denied(TakeOnTestApi.reports(mockMvc, trustManager(), null));
    denied(TakeOnTestApi.report(mockMvc, trustManager(), UUID.randomUUID()));
    // 657D: the PDF, the CSV and "Try again" are VIEW_TRUST too
    for (String format : List.of("pdf", "csv")) {
      TakeOnTestApi.reportFile(mockMvc, trustReader(), UUID.randomUUID(), format)
          .andExpect(status().isNotFound());
      denied(TakeOnTestApi.reportFile(mockMvc, trustManager(), UUID.randomUUID(), format));
      denied(TakeOnTestApi.reportFile(mockMvc, clientsOnly(), UUID.randomUUID(), format));
    }
    TakeOnTestApi.renderReport(mockMvc, trustReader(), UUID.randomUUID())
        .andExpect(status().isNotFound());
    denied(TakeOnTestApi.renderReport(mockMvc, trustManager(), UUID.randomUUID()));
    denied(TakeOnTestApi.reports(mockMvc, clientsOnly(), null));
    denied(TakeOnTestApi.signOffQueue(mockMvc, trustViewer())); // SIGN_OFF_TRUST_TAKE_ON only
  }

  private List<String> historyIds(RequestPostProcessor jwt) throws Exception {
    String json =
        mockMvc
            .perform(get("/api/take-on/batches?size=200").with(jwt))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$.content[*].id");
  }

  private static void denied(ResultActions result) throws Exception {
    result.andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value(ACCESS_DENIED));
  }

  private String createRole(String name, String... capabilities) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":%s}"""
                            .formatted(
                                name,
                                JsonMapper.builder()
                                    .build()
                                    .writeValueAsString(List.of(capabilities)))))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(UUID memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private RequestPostProcessor admin() {
    return TestJwtFactory.adminJwt(ORG_ID, ADMIN);
  }

  private RequestPostProcessor carol() {
    return TestJwtFactory.memberJwt(ORG_ID, CAROL);
  }

  private RequestPostProcessor clientsOnly() {
    return TestJwtFactory.customRoleJwt(ORG_ID, CLIENTS_ONLY);
  }

  private RequestPostProcessor trustManager() {
    return TestJwtFactory.customRoleJwt(ORG_ID, TRUST_MANAGER);
  }

  private RequestPostProcessor trustReader() {
    return TestJwtFactory.customRoleJwt(ORG_ID, TRUST_READER);
  }

  private RequestPostProcessor trustViewer() {
    return TestJwtFactory.customRoleJwt(ORG_ID, TRUST_VIEWER);
  }
}
