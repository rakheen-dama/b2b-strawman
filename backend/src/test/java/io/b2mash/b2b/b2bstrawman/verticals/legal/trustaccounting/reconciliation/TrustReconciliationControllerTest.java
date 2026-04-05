package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReconciliationControllerTest {

  private static final String ORG_ID = "org_trust_recon_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageService storageService;

  private String tenantSchema;
  private String trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Reconciliation Test Org", null)
            .schemaName();

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_recon_owner", "recon_owner@test.com", "Recon Owner", "owner");

    // Enable the trust_accounting module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create a trust account
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_recon_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Reconciliation Test Trust Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000001",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = TestEntityHelper.extractId(accountResult);
  }

  @Test
  void importFnbCsv_returns201WithStatementAndLines() throws Exception {
    var fnbCsv =
        """
        FNB Trust Account Statement - Account 62012345678
        Date,Description,Amount,Balance,Reference
        01/03/2026,Opening Balance,0.00,150000.00,
        03/03/2026,Deposit from Smith & Associates,25000.00,175000.00,REF-SM-001
        05/03/2026,Transfer to Sheriff - Case 2026/1234,-8500.00,166500.00,SHERIFF-1234
        """;

    var csvFile =
        new MockMultipartFile(
            "file", "fnb-march-2026.csv", "text/csv", fnbCsv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/trust-accounts/{accountId}/bank-statements", trustAccountId)
                .file(csvFile)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_recon_owner")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.trustAccountId").value(trustAccountId))
        .andExpect(jsonPath("$.format").value("CSV"))
        .andExpect(jsonPath("$.status").value("IMPORTED"))
        .andExpect(jsonPath("$.lineCount").value(3))
        .andExpect(jsonPath("$.matchedCount").value(0))
        .andExpect(jsonPath("$.fileName").value("fnb-march-2026.csv"))
        .andExpect(jsonPath("$.lines.length()").value(3))
        .andExpect(jsonPath("$.lines[0].matchStatus").value("UNMATCHED"));
  }

  @Test
  void getBankStatementDetail_includesAllParsedLines() throws Exception {
    var fnbCsv =
        """
        FNB Trust Account Statement - Account 62012345678
        Date,Description,Amount,Balance,Reference
        10/03/2026,EFT from Johnson Estate,45000.00,211500.00,REF-JE-002
        15/03/2026,Payment to SARS,-12500.00,199000.00,SARS-TD-5678
        """;

    var csvFile =
        new MockMultipartFile(
            "file", "fnb-detail-test.csv", "text/csv", fnbCsv.getBytes(StandardCharsets.UTF_8));

    var importResult =
        mockMvc
            .perform(
                multipart("/api/trust-accounts/{accountId}/bank-statements", trustAccountId)
                    .file(csvFile)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_recon_owner")))
            .andExpect(status().isCreated())
            .andReturn();

    String statementId =
        JsonPath.read(importResult.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(
            get("/api/bank-statements/{statementId}", statementId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_recon_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(statementId))
        .andExpect(jsonPath("$.lines.length()").value(2))
        .andExpect(jsonPath("$.lines[0].lineNumber").value(1))
        .andExpect(jsonPath("$.lines[0].description").value("EFT from Johnson Estate"))
        .andExpect(jsonPath("$.lines[1].lineNumber").value(2))
        .andExpect(jsonPath("$.lines[1].description").value("Payment to SARS"));
  }

  @Test
  void listBankStatements_returnsNonEmptyListAfterUpload() throws Exception {
    var csv =
        """
        FNB Trust Account Statement - Account 62012345678
        Date,Description,Amount,Balance,Reference
        20/03/2026,Transfer received,10000.00,100000.00,REF-001
        """;

    var csvFile =
        new MockMultipartFile(
            "file", "fnb-list-test.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/trust-accounts/{accountId}/bank-statements", trustAccountId)
                .file(csvFile)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_recon_owner")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/trust-accounts/{accountId}/bank-statements", trustAccountId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_recon_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  void importBankStatement_storesFileInS3AtCorrectPath() throws Exception {
    var csv =
        """
        FNB Trust Account Statement - Account 62012345678
        Date,Description,Amount,Balance,Reference
        25/03/2026,Interest earned,156.75,223956.75,INT-MAR-2026
        """;

    var csvFile =
        new MockMultipartFile(
            "file", "fnb-s3-test.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/trust-accounts/{accountId}/bank-statements", trustAccountId)
                .file(csvFile)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_recon_owner")))
        .andExpect(status().isCreated());

    // Verify the file exists in S3 at the expected path pattern
    String expectedPrefix = "trust-statements/" + tenantSchema + "/" + trustAccountId + "/";
    var keys = storageService.listKeys(expectedPrefix);
    assertThat(keys).anyMatch(key -> key.contains("fnb-s3-test.csv"));
  }

  @Test
  void importGenericCsv_usesGenericParserAsFallback() throws Exception {
    var genericCsv =
        """
        Date,Description,Amount,Balance
        01/03/2026,Opening Balance,0.00,120000.00
        06/03/2026,Client deposit - Matter A,15000.00,135000.00
        11/03/2026,Payment to third party,-7500.00,127500.00
        """;

    var csvFile =
        new MockMultipartFile(
            "file",
            "generic-statement.csv",
            "text/csv",
            genericCsv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/trust-accounts/{accountId}/bank-statements", trustAccountId)
                .file(csvFile)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_recon_owner")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.format").value("CSV"))
        .andExpect(jsonPath("$.lineCount").value(3))
        .andExpect(jsonPath("$.lines.length()").value(3))
        .andExpect(jsonPath("$.lines[0].description").value("Opening Balance"))
        .andExpect(jsonPath("$.lines[1].description").value("Client deposit - Matter A"));
  }
}
