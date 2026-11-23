package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.create;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.map;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.mapTargets;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.position;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rowContent;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rows;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.sheet;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.validate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Phase 93 653A: the dry run (§17.3.2) — generic checks, in-file duplicates, the summary counts
 * agreeing with the row table (X2), the validation hash, and re-validation from scratch. Nothing
 * outside the take-on rows is created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnValidationIntegrationTest {

  private static final String ORG_ID = "org_takeon_validation_test";
  private static final String OWNER = "user_tov_owner";
  private static final String HEADER =
      "Client name,Email,Client type,CDD completed date,Beneficial ownership,Screening,Records,Rating,Branch\n";
  private static final Map<String, String> TARGETS =
      Map.of(
          "name", "Client name",
          "email", "Email",
          "ficaClientType", "Client type",
          "cddCompletedDate", "CDD completed date",
          "beneficialOwnershipStatus", "Beneficial ownership",
          "screeningStatus", "Screening",
          "cddRecordsLocation", "Records");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;

  private String schema;
  private String trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService.provisionTenant(ORG_ID, "Take-on Validation", "legal-za").schemaName();
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER, "tov_owner@test.com", "Validation Owner", "owner");
    var account =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"accountName": "Validation Trust", "bankName": "First National Bank",
                         "branchCode": "250655", "accountNumber": "62000019653",
                         "accountType": "GENERAL", "isPrimary": false,
                         "requireDualApproval": false, "openedDate": "2025-01-01"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = TestEntityHelper.extractId(account);
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityType": "CUSTOMER", "name": "Take-on rating", "slug": "takeon_rating",
                     "fieldType": "NUMBER", "required": false, "sortOrder": 0,
                     "validation": {"max": 10}}
                    """))
        .andExpect(status().isCreated());
  }

  @Test
  void genericChecksMarkEachBadRowWithItsFieldAndCode() throws Exception {
    String csv =
        HEADER
            + "Good Client,good.client@example.test,Individual,01/02/2024,Done,Screened clear,Box 1,5,Main\n"
            + ",blank.name@example.test,Individual,01/02/2024,Done,Screened clear,Box 2,,\n"
            + "Bad Date,bad.date@example.test,Individual,2024-31-31,Done,Screened clear,Box 3,,\n"
            + "Serial Date,serial@example.test,Individual,45000,Done,Screened clear,Box 4,,\n"
            + "Bad Type,bad.type@example.test,Alien,01/02/2024,Done,Screened clear,Box 5,,\n"
            + "Bad Rating,bad.rating@example.test,Individual,01/02/2024,Done,Screened clear,Box 6,50,\n";
    UUID[] ids = create(mockMvc, owner(), "CLIENTS", "checks.csv", TakeOnTestFiles.csv(csv));
    var body = new HashMap<String, Object>();
    body.put("targets", TARGETS);
    body.put("customFields", Map.of("takeon_rating", "Rating"));
    body.put("parked", List.of("Branch"));
    map(mockMvc, owner(), ids[0], ids[1], body).andExpect(status().isOk());
    validate(mockMvc, owner(), ids[0])
        .andExpect(jsonPath("$.status").value("VALIDATED"))
        .andExpect(jsonPath("$.counts.total").value(6))
        .andExpect(jsonPath("$.counts.valid").value(1))
        .andExpect(jsonPath("$.counts.error").value(5));

    var byRow = rowsByNumber(ids[0]);
    var good = byRow.get(2);
    assertThat(good.get("status")).isEqualTo("VALIDATED");
    assertThat(good.get("sourceRef")).isEqualTo("row:2");
    @SuppressWarnings("unchecked")
    var values = (Map<String, Object>) good.get("values");
    assertThat(values)
        .containsEntry("name", "Good Client")
        .containsEntry("ficaClientType", "NATURAL_PERSON")
        .containsEntry("cddCompletedDate", "2024-02-01")
        .containsEntry("customFields", Map.of("takeon_rating", 5))
        .containsEntry("takeon_parked", Map.of("Branch", "Main"));
    assertError(byRow.get(3), "REQUIRED", "name", "Client name is required.");
    assertError(byRow.get(4), "DATE_INVALID", "cddCompletedDate", null);
    assertError(
        byRow.get(5),
        "NUMERIC_IN_DATE",
        "cddCompletedDate",
        "Format this column as a date in Excel, or as text dd/mm/yyyy");
    assertError(byRow.get(6), "ENUM_INVALID", "ficaClientType", null);
    assertError(byRow.get(7), "CUSTOM_FIELD_INVALID", "takeon_rating", null);

    // validation creates nothing outside the take-on rows
    Integer clients =
        jdbc.queryForObject(
            "SELECT count(*) FROM \"%s\".customers WHERE email = ?".formatted(schema),
            Integer.class,
            "good.client@example.test");
    assertThat(clients).isZero();
  }

  @Test
  void anXlsxBlankRequiredCellAndAnErrorCellCarryTheExcelSentences() throws Exception {
    byte[] workbook =
        TakeOnTestFiles.xlsx(
            wb -> {
              Sheet sheet = wb.createSheet("Clients");
              TakeOnTestFiles.writeRow(
                  sheet.createRow(0),
                  List.of(
                      "Client name",
                      "Email",
                      "Client type",
                      "CDD completed date",
                      "Beneficial ownership",
                      "Screening",
                      "Records"));
              Row blank = sheet.createRow(1);
              TakeOnTestFiles.writeRow(
                  blank,
                  List.of(
                      "",
                      "x1@example.test",
                      "Individual",
                      "01/02/2024",
                      "Done",
                      "Not screened",
                      "B"));
              Row error = sheet.createRow(2);
              TakeOnTestFiles.writeRow(
                  error,
                  List.of(
                      "Err",
                      "x2@example.test",
                      "Individual",
                      "01/02/2024",
                      "Done",
                      "Not screened",
                      "B"));
              error.createCell(6).setCellErrorValue(FormulaError.REF.getCode());
            });
    UUID[] ids = create(mockMvc, owner(), "CLIENTS", "clients.xlsx", workbook);
    mapTargets(mockMvc, owner(), ids[0], ids[1], TARGETS).andExpect(status().isOk());
    validate(mockMvc, owner(), ids[0]).andExpect(jsonPath("$.counts.error").value(2));

    var byRow = rowsByNumber(ids[0]);
    assertError(
        byRow.get(2),
        "REQUIRED",
        "name",
        "Client name is required. If this cell holds a formula, open the file in Excel, save it,"
            + " and upload it again.");
    assertError(
        byRow.get(3),
        "CELL_ERROR",
        "Records",
        "This cell holds an error (#REF!). Correct it in Excel and upload again.");
  }

  @Test
  void anAmbiguousAmountUnderPointIsAnErrorAndNotAGuess() throws Exception {
    UUID[] ids =
        create(
            mockMvc,
            owner(),
            "DEBTOR_BALANCES",
            "debtors.csv",
            TakeOnTestFiles.csv(
                "Client reference,Fee note number,Issue date,Outstanding amount\n"
                    + "C-1,FN-1,01/09/2026,\"1,234\"\n"
                    + "C-2,FN-2,01/09/2026,\"(1 234.56)\"\n"));
    mapTargets(
        mockMvc,
        owner(),
        ids[0],
        ids[1],
        Map.of(
            "clientRef", "Client reference",
            "feeNoteNumber", "Fee note number",
            "issueDate", "Issue date",
            "outstandingAmount", "Outstanding amount"));
    position(mockMvc, owner(), ids[0], "2026-10-31");
    validate(mockMvc, owner(), ids[0]).andExpect(jsonPath("$.status").value("VALIDATED"));
    var byRow = rowsByNumber(ids[0]);
    assertError(byRow.get(2), "AMOUNT_AMBIGUOUS", "outstandingAmount", null);
    assertThat(byRow.get(3).get("status")).isEqualTo("VALIDATED");
    assertThat(((Map<?, ?>) byRow.get(3).get("values")).get("outstandingAmount"))
        .isEqualTo("-1234.56");
  }

  @Test
  void anInvestmentBasisAliasIsAccepted() throws Exception {
    UUID[] ids =
        create(
            mockMvc,
            owner(),
            "TRUST_POSITION",
            "investments.csv",
            TakeOnTestFiles.csv(
                "Account,Basis,Institution,Number,Principal,Rate,Deposited,Confirmed\n"
                    + "INV-1,S86(4),Example Bank,0001,10000.00,7.25%,01/03/2026,10050.00\n"),
            "section",
            "INVESTMENTS",
            "trustAccountId",
            trustAccountId);
    mapTargets(
        mockMvc,
        owner(),
        ids[0],
        ids[1],
        Map.of(
            "investmentAccount", "Account",
            "basis", "Basis",
            "institution", "Institution",
            "accountNumber", "Number",
            "principal", "Principal",
            "interestRate", "Rate",
            "depositDate", "Deposited",
            "balancePerConfirmation", "Confirmed"));
    position(mockMvc, owner(), ids[0], "2026-10-31");
    validate(mockMvc, owner(), ids[0]).andExpect(jsonPath("$.counts.valid").value(1));
    var row = rowsByNumber(ids[0]).get(2);
    assertThat(values(row))
        .containsEntry("basis", "CLIENT_INSTRUCTION")
        .containsEntry("interestRate", "7.25")
        .containsEntry("principal", "10000.00");
  }

  @Test
  void aRepeatedEmailInTheFileIsAnErrorOnTheLaterRow() throws Exception {
    String csv =
        HEADER
            + "First,same@example.test,Individual,01/02/2024,Done,Screened clear,Box,,\n"
            + "Second,other@example.test,Individual,01/02/2024,Done,Screened clear,Box,,\n"
            + "Third,SAME@example.test,Individual,01/02/2024,Done,Screened clear,Box,,\n";
    UUID[] ids = create(mockMvc, owner(), "CLIENTS", "dups.csv", TakeOnTestFiles.csv(csv));
    mapTargets(mockMvc, owner(), ids[0], ids[1], TARGETS);
    validate(mockMvc, owner(), ids[0]).andExpect(jsonPath("$.counts.error").value(1));
    var byRow = rowsByNumber(ids[0]);
    assertThat(byRow.get(2).get("status")).isEqualTo("VALIDATED");
    assertError(byRow.get(4), "DUPLICATE_IN_FILE", "email", "Duplicate of row 2 in this file");
  }

  @Test
  void theSummaryCountsEqualTheRowTableByStatus() throws Exception {
    String csv =
        HEADER
            + "A,a@example.test,Individual,01/02/2024,Done,Screened clear,Box,,\n"
            + "B,b@example.test,Individual,01/02/2024,Done,Screened clear,Box,,\n"
            + ",c@example.test,Individual,01/02/2024,Done,Screened clear,Box,,\n"
            + "D,a@example.test,Individual,01/02/2024,Done,Screened clear,Box,,\n"
            + "E,e@example.test,Individual,31/02/2024,Done,Screened clear,Box,,\n";
    UUID[] ids = create(mockMvc, owner(), "CLIENTS", "cards.csv", TakeOnTestFiles.csv(csv));
    mapTargets(mockMvc, owner(), ids[0], ids[1], TARGETS);
    String batch =
        validate(mockMvc, owner(), ids[0]).andReturn().getResponse().getContentAsString();
    int total = JsonPath.read(batch, "$.counts.total");
    int valid = JsonPath.read(batch, "$.counts.valid");
    int error = JsonPath.read(batch, "$.counts.error");
    int duplicate = JsonPath.read(batch, "$.counts.duplicate");
    int blocks = JsonPath.read(batch, "$.counts.blocks");

    assertThat(total).isEqualTo(5).isEqualTo(pageTotal(ids[0], null));
    assertThat(valid).isEqualTo(2).isEqualTo(pageTotal(ids[0], "status=VALIDATED"));
    assertThat(error).isEqualTo(3).isEqualTo(pageTotal(ids[0], "status=ERROR"));
    assertThat(blocks).isZero().isEqualTo(pageTotal(ids[0], "blocksOnly=true"));
    assertThat(duplicate).isZero();
    assertThat(pageTotal(ids[0], "status=PENDING")).isZero();
  }

  @Test
  void theHashChangesWithEveryInputAndOnlyThen() throws Exception {
    byte[] workbook =
        TakeOnTestFiles.xlsx(
            wb -> {
              Sheet first = wb.createSheet("First");
              TakeOnTestFiles.writeRow(
                  first.createRow(0),
                  List.of("Client reference", "Fee note number", "Issue date", "Amount", "Note"));
              TakeOnTestFiles.writeRow(
                  first.createRow(1), List.of("C-1", "FN-10", "01/09/2026", "100.00", "n"));
              Sheet second = wb.createSheet("Second");
              TakeOnTestFiles.writeRow(
                  second.createRow(0),
                  List.of("Client reference", "Fee note number", "Issue date", "Amount", "Note"));
              TakeOnTestFiles.writeRow(
                  second.createRow(1), List.of("C-2", "FN-20", "01/09/2026", "200.00", "m"));
            });
    UUID[] ids = create(mockMvc, owner(), "DEBTOR_BALANCES", "hash.xlsx", workbook);
    var targets =
        Map.of(
            "clientRef", "Client reference",
            "feeNoteNumber", "Fee note number",
            "issueDate", "Issue date",
            "outstandingAmount", "Amount");
    mapTargets(mockMvc, owner(), ids[0], ids[1], targets);
    position(mockMvc, owner(), ids[0], "2026-10-31");
    String h1 = hash(ids[0]);
    assertThat(hash(ids[0])).as("re-validation of unchanged input").isEqualTo(h1);

    var withParked = new HashMap<String, Object>();
    withParked.put("targets", targets);
    withParked.put("parked", List.of("Note"));
    map(mockMvc, owner(), ids[0], ids[1], withParked);
    String h2 = hash(ids[0]);
    assertThat(h2).as("mapping changed").isNotEqualTo(h1);

    var withFormat = new HashMap<String, Object>(withParked);
    withFormat.put("parseOptions", Map.of("dateFormat", "MM/dd/yyyy", "decimalSeparator", "POINT"));
    map(mockMvc, owner(), ids[0], ids[1], withFormat);
    String h3 = hash(ids[0]);
    assertThat(h3).as("parse options changed").isNotEqualTo(h2);

    position(mockMvc, owner(), ids[0], "2026-11-30");
    String h4 = hash(ids[0]);
    assertThat(h4).as("cutover date changed").isNotEqualTo(h3);

    sheet(mockMvc, owner(), ids[0], ids[1], "Second", 1).andExpect(status().isOk());
    map(mockMvc, owner(), ids[0], ids[1], withFormat);
    String h5 = hash(ids[0]);
    assertThat(h5).as("file re-read from another sheet").isNotEqualTo(h4);
    assertThat(hash(ids[0])).isEqualTo(h5);
  }

  @Test
  void reValidationStartsFromScratchSoAFixedMappingClearsTheErrors() throws Exception {
    String csv =
        "Client name,Email,Client type,CDD completed date,Beneficial ownership,Screening,Records\n"
            + "A,ra@example.test,Individual,01/02/2024,Done,Screened clear,\n"
            + "B,rb@example.test,Individual,01/02/2024,Done,Screened clear,\n";
    UUID[] ids = create(mockMvc, owner(), "CLIENTS", "fix.csv", TakeOnTestFiles.csv(csv));
    mapTargets(mockMvc, owner(), ids[0], ids[1], TARGETS);
    validate(mockMvc, owner(), ids[0]).andExpect(jsonPath("$.counts.error").value(2));

    var fixed = new HashMap<String, Object>();
    fixed.put("targets", TARGETS);
    fixed.put("defaults", Map.of("cddRecordsLocation", "Previous system archive"));
    map(mockMvc, owner(), ids[0], ids[1], fixed).andExpect(status().isOk());
    validate(mockMvc, owner(), ids[0])
        .andExpect(jsonPath("$.counts.error").value(0))
        .andExpect(jsonPath("$.counts.valid").value(2));
    var byRow = rowsByNumber(ids[0]);
    assertThat(byRow.get(2).get("errors")).isEqualTo(List.of());
    assertThat(values(byRow.get(2))).containsEntry("cddRecordsLocation", "Previous system archive");
  }

  @Test
  void aMappingThatLeavesARequiredFieldOutIsRefused() throws Exception {
    UUID[] ids =
        create(
            mockMvc,
            owner(),
            "CLIENTS",
            "refuse.csv",
            TakeOnTestFiles.csv(
                HEADER + "A,a@x.test,Individual,01/02/2024,Done,Screened clear,B,,\n"));
    var missing = new HashMap<>(TARGETS);
    missing.remove("cddRecordsLocation");
    mapTargets(mockMvc, owner(), ids[0], ids[1], missing)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Mapping not saved"))
        .andExpect(
            jsonPath("$.detail")
                .value("Map CDD records location to a column, or give it a default value."));
    var twice = new HashMap<>(TARGETS);
    twice.put("phone", "Email");
    mapTargets(mockMvc, owner(), ids[0], ids[1], twice)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("The column \"Email\" is mapped more than once."));
    var unknownColumn = new HashMap<>(TARGETS);
    unknownColumn.put("phone", "Telephone");
    mapTargets(mockMvc, owner(), ids[0], ids[1], unknownColumn)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("The file has no column \"Telephone\"."));
    var body = new HashMap<String, Object>();
    body.put("targets", TARGETS);
    body.put("customFields", Map.of("no_such_field", "Rating"));
    map(mockMvc, owner(), ids[0], ids[1], body)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("There is no active custom field \"no_such_field\"."));
  }

  // --- helpers ---

  private String hash(UUID batchId) throws Exception {
    String json =
        validate(mockMvc, owner(), batchId).andReturn().getResponse().getContentAsString();
    assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("VALIDATED");
    return JsonPath.read(json, "$.validationHash");
  }

  private int pageTotal(UUID batchId, String query) throws Exception {
    String json =
        rows(mockMvc, owner(), batchId, query).andReturn().getResponse().getContentAsString();
    return JsonPath.read(json, "$.page.totalElements");
  }

  private Map<Integer, Map<String, Object>> rowsByNumber(UUID batchId) throws Exception {
    var result = new HashMap<Integer, Map<String, Object>>();
    for (var row :
        rowContent(
            rows(mockMvc, owner(), batchId, "size=200")
                .andReturn()
                .getResponse()
                .getContentAsString())) {
      result.put((Integer) row.get("rowNumber"), row);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> values(Map<String, Object> row) {
    return (Map<String, Object>) row.get("values");
  }

  @SuppressWarnings("unchecked")
  private static void assertError(
      Map<String, Object> row, String code, String field, String message) {
    assertThat(row.get("status")).isEqualTo("ERROR");
    var errors = (List<Map<String, Object>>) row.get("errors");
    assertThat(errors)
        .anySatisfy(
            e -> {
              assertThat(e.get("code")).isEqualTo(code);
              assertThat(e.get("field")).isEqualTo(field);
              if (message != null) {
                assertThat(e.get("message")).isEqualTo(message);
              }
            });
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
