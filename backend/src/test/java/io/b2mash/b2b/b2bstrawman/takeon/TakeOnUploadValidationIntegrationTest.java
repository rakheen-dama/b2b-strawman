package io.b2mash.b2b.b2bstrawman.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Phase 93 653A (§17.6.2, C-10): upload refusals are 422s decided before anything is stored — no
 * batch row and no S3 object. MockMvc bypasses the servlet multipart limit, so the service's own 10
 * MB check is what answers here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnUploadValidationIntegrationTest {

  private static final String ORG_ID = "org_takeon_upload_test";
  private static final String OWNER = "user_tou_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private StorageService storageService;

  private String schema;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService.provisionTenant(ORG_ID, "Take-on Uploads", "legal-za").schemaName();
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER, "tou_owner@test.com", "Upload Owner", "owner");
  }

  @Test
  void otherSpreadsheetFormatsAreRefused() throws Exception {
    refused(
        "old.xls",
        "application/vnd.ms-excel",
        TakeOnTestFiles.xlsOle2(),
        "TAKE_ON_FILE_UNSUPPORTED");
    refused(
        "sheet.ods",
        "application/vnd.oasis.opendocument.spreadsheet",
        TakeOnTestFiles.csv("a,b\n1,2\n"),
        "TAKE_ON_FILE_UNSUPPORTED");
    refused(
        "macro.xlsm",
        TakeOnTestApi.XLSX,
        TakeOnTestFiles.xlsxTable("S", java.util.List.of("A"), java.util.List.of()),
        "TAKE_ON_FILE_UNSUPPORTED");
  }

  @Test
  void contentThatDoesNotMatchTheExtensionIsRefused() throws Exception {
    var result =
        refused(
            "clients.xlsx",
            TakeOnTestApi.XLSX,
            TakeOnTestFiles.csv("Client name,Email\nA,a@example.test\n"),
            "TAKE_ON_FILE_UNSUPPORTED");
    result
        .andExpect(jsonPath("$.title").value("File not supported"))
        .andExpect(
            jsonPath("$.detail").value("Save the file as .xlsx or .csv and upload it again."));
    byte[] withNul = "Client name,Email\nA,a@example.test\n".getBytes(StandardCharsets.UTF_8);
    withNul[3] = 0;
    refused("clients.csv", TakeOnTestApi.CSV, withNul, "TAKE_ON_FILE_UNSUPPORTED");
    refused(
        "clients.csv",
        TakeOnTestApi.CSV,
        TakeOnTestFiles.xlsxTable("S", java.util.List.of("A"), java.util.List.of()),
        "TAKE_ON_FILE_UNSUPPORTED");
  }

  @Test
  void aFileOverTenMegabytesIsRefused() throws Exception {
    byte[] big = new byte[10_485_761];
    Arrays.fill(big, (byte) 'a');
    refused("big.csv", TakeOnTestApi.CSV, big, "TAKE_ON_FILE_TOO_LARGE")
        .andExpect(
            jsonPath("$.detail")
                .value("This file is larger than 10 MB. Split it into smaller files."));
  }

  @Test
  void anEmptyFileIsRefused() throws Exception {
    refused("empty.csv", TakeOnTestApi.CSV, new byte[0], "TAKE_ON_FILE_EMPTY");
  }

  @Test
  void moreThanTenThousandRowsOrOneHundredColumnsIsRefused() throws Exception {
    refused(
        "rows.csv",
        TakeOnTestApi.CSV,
        TakeOnTestFiles.csvOfSize(10_001, 2),
        "TAKE_ON_TOO_MANY_ROWS");
    refused(
        "cols.csv",
        TakeOnTestApi.CSV,
        TakeOnTestFiles.csvOfSize(2, 101),
        "TAKE_ON_TOO_MANY_COLUMNS");
  }

  @Test
  void aTrustUploadNeedsItsSectionAndABankingAccount() throws Exception {
    TakeOnTestApi.upload(
            mockMvc,
            owner(),
            "TRUST_POSITION",
            "ledger.csv",
            TakeOnTestApi.CSV,
            TakeOnTestFiles.csv("Client reference,Matter reference,Balance at cutover\nC,M,1\n"),
            "trustAccountId",
            java.util.UUID.randomUUID().toString())
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("Choose which part of the trust position this file holds."));
    TakeOnTestApi.upload(
            mockMvc,
            owner(),
            "TRUST_POSITION",
            "ledger.csv",
            TakeOnTestApi.CSV,
            TakeOnTestFiles.csv("Client reference,Matter reference,Balance at cutover\nC,M,1\n"),
            "section",
            "CLIENT_LEDGER",
            "trustAccountId",
            java.util.UUID.randomUUID().toString())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Trust account not found"));
    TakeOnTestApi.upload(
            mockMvc,
            owner(),
            "CLIENTS",
            "c.csv",
            TakeOnTestApi.CSV,
            TakeOnTestFiles.csv("Client name\nA\n"),
            "trustAccountId",
            java.util.UUID.randomUUID().toString())
        .andExpect(status().isBadRequest());
  }

  private ResultActions refused(String fileName, String contentType, byte[] bytes, String code)
      throws Exception {
    long before = history();
    int keysBefore = storageService.listKeys("take-on/" + schema + "/").size();
    var result =
        TakeOnTestApi.upload(mockMvc, owner(), "CLIENTS", fileName, contentType, bytes)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value(code));
    assertThat(history()).isEqualTo(before);
    assertThat(storageService.listKeys("take-on/" + schema + "/")).hasSize(keysBefore);
    return result;
  }

  private long history() throws Exception {
    String json =
        mockMvc
            .perform(get("/api/take-on/batches?dataset=CLIENTS").with(owner()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(json, "$.page.totalElements")).longValue();
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
