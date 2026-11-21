package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * C5 (arch §14.3.4, Phase 89 slice 630C): a well-formed bank-statement upload whose content cannot
 * be parsed is a 422 ProblemDetail ("Bank statement could not be parsed" + the parser's user-facing
 * message), not a generic 500 — and, per #1717 / G88-F33, it leaves no object in storage. The
 * import route is {@code POST /api/trust-accounts/{accountId}/bank-statements} (multipart part
 * {@code file}); the spec's {@code …/reconciliations/import} does not exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BankStatementImportErrorIntegrationTest {

  private static final String ORG_ID = "org_bank_stmt_import_error";
  private static final String OWNER = "user_bank_stmt_err_owner";

  private static final String MALFORMED_CSV =
      """
      Date,Description,Amount,Balance
      not-a-date,Garbage row,not-a-number,also-not-a-number
      still-not-a-date,Another garbage row,xyz,abc
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageService storageService;
  @Autowired private BankStatementRepository bankStatementRepository;
  @Autowired private Clock clock;

  private String tenantSchema;
  private int accountCounter = 0;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Bank Statement Import Error Org", null)
            .schemaName();

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER, "bank_stmt_err_owner@test.com", "Stmt Err Owner", "owner");

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

  @Test
  void malformedCsv_returns422ProblemDetail_andStoresNothing() throws Exception {
    String accountId = createIsolatedTrustAccount();
    var csvFile =
        new MockMultipartFile(
            "file", "malformed.csv", "text/csv", MALFORMED_CSV.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/trust-accounts/{accountId}/bank-statements", accountId)
                .file(csvFile)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.title").value("Bank statement could not be parsed"))
        .andExpect(
            jsonPath("$.detail")
                .value("All 2 data lines were malformed; no valid transactions parsed"))
        .andExpect(jsonPath("$.detail", not(containsString("Exception"))))
        .andExpect(jsonPath("$.detail", not(containsString("at io."))));

    // #1717 / G88-F33: the raw file is uploaded only after a successful parse.
    assertThat(storageService.listKeys("trust-statements/" + tenantSchema + "/" + accountId + "/"))
        .isEmpty();
    List<BankStatement> statements =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    transactionTemplate.execute(
                        tx ->
                            bankStatementRepository.findByTrustAccountIdOrderByPeriodEndDesc(
                                UUID.fromString(accountId))));
    assertThat(statements).isEmpty();
  }

  @Test
  void validCsv_stillImports201() throws Exception {
    String accountId = createIsolatedTrustAccount();
    var validCsv =
        """
        Date,Description,Amount,Balance,Reference
        01/03/2026,Opening Balance,0.00,1000.00,
        03/03/2026,Deposit from client,250.00,1250.00,REF-IMP-001
        """;
    var csvFile =
        new MockMultipartFile(
            "file", "valid.csv", "text/csv", validCsv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/trust-accounts/{accountId}/bank-statements", accountId)
                .file(csvFile)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isCreated());

    assertThat(storageService.listKeys("trust-statements/" + tenantSchema + "/" + accountId + "/"))
        .hasSize(1);
  }

  /**
   * FU-6a (ruling F-647C): a statement with a line dated after today is refused whole — 400 naming
   * the first such line's date — and leaves no statement, no line and no stored file. No line is
   * silently dropped.
   */
  @Test
  void aLineDatedAfterToday_refusesTheWholeImport_andStoresNothing() throws Exception {
    String accountId = createIsolatedTrustAccount();
    var today = LocalDate.now(clock);
    var tomorrow = today.plusDays(1);
    var csv =
        """
        Date,Description,Amount,Balance,Reference
        %s,Deposit from client,250.00,1250.00,REF-FUT-001
        %s,Value-dated interest,12.00,1262.00,REF-FUT-002
        %s,Later value-dated credit,5.00,1267.00,REF-FUT-003
        """
            .formatted(day(today.minusDays(2)), day(tomorrow), day(tomorrow.plusDays(3)));
    var csvFile =
        new MockMultipartFile(
            "file", "future.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/trust-accounts/{accountId}/bank-statements", accountId)
                .file(csvFile)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Cannot import bank statement"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "This statement has a line dated "
                        + VariableFormatter.formatDateText(tomorrow)
                        + ", which is after today. Import the statement once the bank has"
                        + " processed that line."));

    assertThat(storageService.listKeys("trust-statements/" + tenantSchema + "/" + accountId + "/"))
        .isEmpty();
    List<BankStatement> statements =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    transactionTemplate.execute(
                        tx ->
                            bankStatementRepository.findByTrustAccountIdOrderByPeriodEndDesc(
                                UUID.fromString(accountId))));
    assertThat(statements).isEmpty();
  }

  /** FU-6a boundary: a line dated today is not in the future, so the statement imports. */
  @Test
  void aLineDatedToday_imports201() throws Exception {
    String accountId = createIsolatedTrustAccount();
    var today = LocalDate.now(clock);
    var csv =
        """
        Date,Description,Amount,Balance,Reference
        %s,Deposit from client,250.00,1250.00,REF-TODAY-001
        %s,Interest credited today,12.00,1262.00,REF-TODAY-002
        """
            .formatted(day(today.minusDays(1)), day(today));
    var csvFile =
        new MockMultipartFile(
            "file", "today.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/trust-accounts/{accountId}/bank-statements", accountId)
                .file(csvFile)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lineCount").value(2))
        .andExpect(jsonPath("$.periodEnd").value(today.toString()));
  }

  private static String day(LocalDate date) {
    return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
  }

  private String createIsolatedTrustAccount() throws Exception {
    accountCounter++;
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Import Error Trust Account %d",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62300000%03d",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """
                            .formatted(accountCounter, accountCounter)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(accountResult);
  }
}
