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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Sheet;
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
  void aFileOverThreeMegabytesIsRefused() throws Exception {
    // FR-3: 3 MB (3 000 000 bytes), so the file fits the 3 MiB server-action body with headroom
    byte[] big = new byte[3_000_001];
    Arrays.fill(big, (byte) 'a');
    refused("big.csv", TakeOnTestApi.CSV, big, "TAKE_ON_FILE_TOO_LARGE")
        .andExpect(
            jsonPath("$.detail")
                .value("This file is larger than 3 MB. Split it into smaller files."));
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

  // --- OBS F-1: a header that is not on row 1 ---

  private static final String HEADER_ROW_WARNING =
      "No row could be read with row 1 as the header row. Choose the sheet and the row that holds"
          + " the column names.";
  private static final List<String> DEBTOR_HEADER =
      List.of(
          "Client code",
          "Matter",
          "Fee note",
          "Issue date",
          "Due date",
          "Outstanding",
          "Description");
  private static final Map<String, String> DEBTOR_TARGETS =
      Map.of(
          "clientRef", "Client code",
          "matterRef", "Matter",
          "feeNoteNumber", "Fee note",
          "issueDate", "Issue date",
          "dueDate", "Due date",
          "outstandingAmount", "Outstanding",
          "description", "Description");

  @Test
  void aWorkbookWhoseHeaderIsNotOnRowOneUploadsAndIsReadFromTheChosenHeaderRow() throws Exception {
    byte[] workbook =
        TakeOnTestFiles.xlsx(
            wb -> {
              wb.createSheet("Old notes").createRow(0).createCell(0).setCellValue("Scratch");
              Sheet sheet = wb.createSheet("Debtors");
              sheet
                  .createRow(0)
                  .createCell(0)
                  .setCellValue("Debtors at cutover - exported 2026-10-31");
              TakeOnTestFiles.writeRow(sheet.createRow(2), DEBTOR_HEADER);
              TakeOnTestFiles.writeRow(
                  sheet.createRow(3),
                  List.of(
                      "CL-001", "MAT-01", "FN-1001", "2026-09-01", "2026-10-01", "", "Consult"));
              sheet.getRow(3).createCell(5).setCellValue(1500.5);
              TakeOnTestFiles.writeRow(
                  sheet.createRow(4),
                  List.of(
                      "CL-002", "MAT-02", "FN-1002", "2026-09-15", "2026-10-15", "1,234", "Draft"));
              TakeOnTestFiles.writeRow(
                  sheet.createRow(5),
                  List.of("CL-003", "MAT-03", "FN-1003", "2026-10-01", "2026-10-31", "", "Broken"));
              sheet.getRow(5).createCell(5).setCellErrorValue(FormulaError.REF.getCode());
              TakeOnTestFiles.writeRow(
                  sheet.createRow(6),
                  List.of("CL-004", "MAT-04", "FN-1004", "2026-10-05", "2026-11-05", "", "Court"));
              sheet.getRow(6).createCell(5).setCellValue(980);
              wb.setActiveSheet(1);
              wb.setFirstVisibleTab(1);
              wb.setSheetHidden(0, true);
            });

    UUID[] ids = uploadNeedingHeaderRow("debtors.xlsx", TakeOnTestApi.XLSX, workbook, "Debtors");
    sheetThenValidate(ids, "Debtors");
  }

  @Test
  void aCsvWithATitleLineUploadsAndIsReadFromTheChosenHeaderRow() throws Exception {
    byte[] csv =
        TakeOnTestFiles.csv(
            "Debtors at cutover - exported 2026-10-31\n\n"
                + String.join(",", DEBTOR_HEADER)
                + "\n"
                + "CL-001,MAT-01,FN-1001,2026-09-01,2026-10-01,1500.50,Consult\n"
                + "CL-002,MAT-02,FN-1002,2026-09-15,2026-10-15,\"1,234\",Draft\n"
                + "CL-003,MAT-03,FN-1003,2026-10-01,2026-10-31,350.00,Advice\n"
                + "CL-004,MAT-04,FN-1004,2026-10-05,2026-11-05,980.00,Court\n");

    UUID[] ids = uploadNeedingHeaderRow("debtors.csv", TakeOnTestApi.CSV, csv, "CSV");
    TakeOnTestApi.sheet(mockMvc, owner(), ids[0], ids[1], null, 3)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.batch.sources[0].rowCount").value(4))
        .andExpect(jsonPath("$.batch.sources[0].headerRow").value(3));
    TakeOnTestApi.mapTargets(mockMvc, owner(), ids[0], ids[1], DEBTOR_TARGETS)
        .andExpect(status().isOk());
    TakeOnTestApi.position(mockMvc, owner(), ids[0], "2026-10-31").andExpect(status().isOk());
    TakeOnTestApi.validate(mockMvc, owner(), ids[0])
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counts.total").value(4));
    var byRow = rowsByNumber(ids[0]);
    assertRowError(byRow.get(5), "AMOUNT_AMBIGUOUS");
    assertThat(rowsWithReaderErrors(byRow)).containsExactly(5);
  }

  @Test
  void aCsvWhoseFirstRowIsBlankUploadsAndIsReadFromTheChosenHeaderRow() throws Exception {
    byte[] csv =
        TakeOnTestFiles.csv(
            "\nDebtors at cutover - exported 2026-10-31\n\n"
                + String.join(",", DEBTOR_HEADER)
                + "\n"
                + "CL-001,MAT-01,FN-1001,2026-09-01,2026-10-01,1500.50,Consult\n"
                + "CL-002,MAT-02,FN-1002,2026-09-15,2026-10-15,350.00,Draft\n");

    UUID[] ids = uploadNeedingHeaderRow("debtors-blank-first.csv", TakeOnTestApi.CSV, csv, "CSV");
    TakeOnTestApi.sheet(mockMvc, owner(), ids[0], ids[1], null, 1)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("TAKE_ON_HEADER_ROW_BLANK"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "The chosen header row is blank. Choose the row that holds the column"
                        + " names."));
    TakeOnTestApi.sheet(mockMvc, owner(), ids[0], ids[1], null, 4)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headers[0]").value("Client code"))
        .andExpect(jsonPath("$.batch.sources[0].rowCount").value(2))
        .andExpect(jsonPath("$.batch.sources[0].headerRow").value(4));
  }

  @Test
  void aSingleSheetWorkbookWhoseFirstRowIsBlankUploadsAndIsReadFromTheChosenHeaderRow()
      throws Exception {
    byte[] workbook =
        TakeOnTestFiles.xlsx(
            wb -> {
              Sheet sheet = wb.createSheet("Debtors");
              sheet
                  .createRow(1)
                  .createCell(0)
                  .setCellValue("Debtors at cutover - exported 2026-10-31");
              TakeOnTestFiles.writeRow(sheet.createRow(3), DEBTOR_HEADER);
              TakeOnTestFiles.writeRow(
                  sheet.createRow(4),
                  List.of(
                      "CL-001", "MAT-01", "FN-1001", "2026-09-01", "2026-10-01", "1500.50", "A"));
              TakeOnTestFiles.writeRow(
                  sheet.createRow(5),
                  List.of(
                      "CL-002", "MAT-02", "FN-1002", "2026-09-15", "2026-10-15", "350.00", "B"));
            });

    UUID[] ids =
        uploadNeedingHeaderRow("debtors-blank-first.xlsx", TakeOnTestApi.XLSX, workbook, "Debtors");
    TakeOnTestApi.sheet(mockMvc, owner(), ids[0], ids[1], "Debtors", 4)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headers[0]").value("Client code"))
        .andExpect(jsonPath("$.batch.sources[0].rowCount").value(2));
  }

  @Test
  void anEmptySingleSheetWorkbookIsStillRefused() throws Exception {
    byte[] workbook = TakeOnTestFiles.xlsx(wb -> wb.createSheet("Clients"));
    refused("empty-sheet.xlsx", TakeOnTestApi.XLSX, workbook, "TAKE_ON_NO_HEADER");
  }

  @Test
  void aSemicolonCsvWithATitleLineKeepsItsDetectedOptionsOnceTheHeaderRowIsChosen()
      throws Exception {
    byte[] csv =
        TakeOnTestFiles.csv(
            "Debtors at cutover;exported 2026-10-31\n\n"
                + String.join(";", DEBTOR_HEADER)
                + "\n"
                + "CL-001;MAT-01;FN-1001;2026-09-01;2026-10-01;1500,50;Consult\n"
                + "CL-002;MAT-02;FN-1002;2026-09-15;2026-10-15;350,00;Draft\n");

    UUID[] ids = uploadNeedingHeaderRow("debtors-semicolon.csv", TakeOnTestApi.CSV, csv, "CSV");
    TakeOnTestApi.sheet(mockMvc, owner(), ids[0], ids[1], null, 3)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.batch.sources[0].rowCount").value(2))
        .andExpect(jsonPath("$.batch.sources[0].parseOptions.delimiter").value(";"))
        .andExpect(jsonPath("$.batch.sources[0].parseOptions.decimalSeparator").value("COMMA"));
  }

  /**
   * Uploads a readable file whose header row 1 yields no row: stored (file kept, no rows) with the
   * header-row warning; the preview still answers; mapping and the dry run are refused until the
   * header row is chosen.
   */
  private UUID[] uploadNeedingHeaderRow(
      String fileName, String contentType, byte[] bytes, String expectedSheet) throws Exception {
    String json =
        TakeOnTestApi.upload(mockMvc, owner(), "DEBTOR_BALANCES", fileName, contentType, bytes)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.warnings[0]").value(HEADER_ROW_WARNING))
            .andExpect(jsonPath("$.batch.sources[0].rowCount").value(0))
            .andExpect(jsonPath("$.batch.sources[0].sheetName").value(expectedSheet))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID[] ids = {
      UUID.fromString(JsonPath.read(json, "$.batch.id")),
      UUID.fromString(JsonPath.read(json, "$.sourceId"))
    };
    assertThat(storageService.listKeys("take-on/" + schema + "/" + ids[0] + "/")).hasSize(1);
    mockMvc
        .perform(
            get("/api/take-on/batches/" + ids[0] + "/sources/" + ids[1] + "/preview").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows").isEmpty())
        .andExpect(jsonPath("$.warnings[0]").value(HEADER_ROW_WARNING));
    TakeOnTestApi.mapTargets(mockMvc, owner(), ids[0], ids[1], DEBTOR_TARGETS)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_NO_ROWS"));
    TakeOnTestApi.position(mockMvc, owner(), ids[0], "2026-10-31").andExpect(status().isOk());
    TakeOnTestApi.validate(mockMvc, owner(), ids[0])
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_WRONG_STATE"));
    return ids;
  }

  private void sheetThenValidate(UUID[] ids, String sheetName) throws Exception {
    TakeOnTestApi.sheet(mockMvc, owner(), ids[0], ids[1], sheetName, 3)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headers[0]").value("Client code"))
        .andExpect(jsonPath("$.batch.sources[0].rowCount").value(4))
        .andExpect(jsonPath("$.batch.sources[0].malformedCount").value(1));
    TakeOnTestApi.mapTargets(mockMvc, owner(), ids[0], ids[1], DEBTOR_TARGETS)
        .andExpect(status().isOk());
    TakeOnTestApi.validate(mockMvc, owner(), ids[0])
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VALIDATED"))
        .andExpect(jsonPath("$.counts.total").value(4));
    var byRow = rowsByNumber(ids[0]);
    assertThat(byRow.keySet()).containsExactlyInAnyOrder(4, 5, 6, 7);
    assertRowError(byRow.get(5), "AMOUNT_AMBIGUOUS");
    assertRowError(byRow.get(6), "CELL_ERROR");
    assertThat(rowsWithReaderErrors(byRow)).containsExactlyInAnyOrder(5, 6);
  }

  /**
   * The debtors dataset carries these reader tests. Since 658A it has its own rules, so its client
   * and matter references ("CL-001", "MAT-01" — none in this tenant) are row errors too; the
   * reader's outcome is the rows with any other error.
   */
  private static final Set<String> DEBTOR_RULE_CODES =
      Set.of("CLIENT_UNRESOLVED", "MATTER_UNRESOLVED");

  @SuppressWarnings("unchecked")
  private static List<Integer> rowsWithReaderErrors(Map<Integer, Map<String, Object>> byRow) {
    return byRow.entrySet().stream()
        .filter(
            e ->
                ((List<Map<String, Object>>) e.getValue().get("errors"))
                    .stream().anyMatch(err -> !DEBTOR_RULE_CODES.contains(err.get("code"))))
        .map(Map.Entry::getKey)
        .sorted()
        .toList();
  }

  private Map<Integer, Map<String, Object>> rowsByNumber(UUID batchId) throws Exception {
    var result = new HashMap<Integer, Map<String, Object>>();
    for (var row :
        TakeOnTestApi.rowContent(
            TakeOnTestApi.rows(mockMvc, owner(), batchId, "size=200")
                .andReturn()
                .getResponse()
                .getContentAsString())) {
      result.put((Integer) row.get("rowNumber"), row);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static void assertRowError(Map<String, Object> row, String code) {
    assertThat(row.get("status")).isEqualTo("ERROR");
    assertThat((List<Map<String, Object>>) row.get("errors"))
        .anySatisfy(e -> assertThat(e.get("code")).isEqualTo(code));
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
