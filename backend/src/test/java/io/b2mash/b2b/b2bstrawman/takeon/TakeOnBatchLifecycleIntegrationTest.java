package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.addSource;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.auditEvents;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.cancel;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.create;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.detail;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.mapTargets;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.position;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rowContent;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rows;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.sheet;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.validate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 93 653A: the take-on batch lifecycle through the endpoints the wizard uses (C-15: the
 * transitions unreachable here — commit claim, completion — are covered by {@code
 * TakeOnBatchTest}). Upload → mapping → dry run → re-map, sources added / replaced / removed, the
 * sheet picker, cancel and the 30-day abandoned purge with SKIP LOCKED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnBatchLifecycleIntegrationTest {

  private static final String ORG_ID = "org_takeon_lifecycle_test";
  private static final String OWNER = "user_tol_owner";
  private static final String CLIENTS_CSV =
      """
      Client name,Email,Client type,CDD completed date,Beneficial ownership,Screening,CDD records location
      Thandi Example,thandi@example.test,Individual,01/02/2024,Done,Screened clear,Box 1
      Sipho Example,sipho@example.test,Company,02/02/2024,N/A,Not screened,Box 2
      """;
  private static final Map<String, String> CLIENT_TARGETS =
      Map.of(
          "name", "Client name",
          "email", "Email",
          "ficaClientType", "Client type",
          "cddCompletedDate", "CDD completed date",
          "beneficialOwnershipStatus", "Beneficial ownership",
          "screeningStatus", "Screening",
          "cddRecordsLocation", "CDD records location");
  private static final String DEBTORS_CSV =
      """
      Client reference,Fee note number,Issue date,Outstanding amount
      C-1,FN-1,01/09/2026,1500.00
      """;
  private static final Map<String, String> DEBTOR_TARGETS =
      Map.of(
          "clientRef", "Client reference",
          "feeNoteNumber", "Fee note number",
          "issueDate", "Issue date",
          "outstandingAmount", "Outstanding amount");
  private static final String LEDGER_CSV =
      """
      Client reference,Matter reference,Balance at cutover
      C-1,M-1,1000.00
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private StorageService storageService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnAbandonedBatchPurgeJob purgeJob;
  @Autowired private TakeOnBatchRepository batchRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private String schema;
  private String trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService.provisionTenant(ORG_ID, "Take-on Lifecycle", "legal-za").schemaName();
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER, "tol_owner@test.com", "Take-on Owner", "owner");
    var account =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"accountName": "Take-on Trust", "bankName": "First National Bank",
                         "branchCode": "250655", "accountNumber": "62000009653",
                         "accountType": "GENERAL", "isPrimary": false,
                         "requireDualApproval": false, "openedDate": "2025-01-01"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = TestEntityHelper.extractId(account);
  }

  @Test
  void createStoresTheSourceOnePendingRowPerDataRowAndArchivesTheFile() throws Exception {
    var result =
        TakeOnTestApi.upload(
                mockMvc,
                owner(),
                "CLIENTS",
                "clients.csv",
                TakeOnTestApi.CSV,
                TakeOnTestFiles.csv(CLIENTS_CSV))
            .andExpect(status().isCreated())
            .andExpect(
                header()
                    .string("Location", org.hamcrest.Matchers.startsWith("/api/take-on/batches/")))
            .andExpect(jsonPath("$.batch.status").value("UPLOADED"))
            .andExpect(jsonPath("$.batch.dataset").value("CLIENTS"))
            .andExpect(jsonPath("$.batch.sources[0].section").value("MAIN"))
            .andExpect(jsonPath("$.batch.sources[0].fileFormat").value("CSV"))
            .andExpect(jsonPath("$.batch.sources[0].rowCount").value(2))
            .andExpect(jsonPath("$.batch.sources[0].mapped").value(false))
            .andExpect(jsonPath("$.headers[0]").value("Client name"))
            .andExpect(
                jsonPath("$.batch.allowedActions")
                    .value(org.hamcrest.Matchers.hasItems("REMOVE_SOURCE", "MAP", "CANCEL")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID batchId = UUID.fromString(JsonPath.read(result, "$.batch.id"));
    UUID sourceId = UUID.fromString(JsonPath.read(result, "$.sourceId"));

    var page = rows(mockMvc, owner(), batchId, null).andExpect(status().isOk()).andReturn();
    var content = rowContent(page.getResponse().getContentAsString());
    assertThat(content).hasSize(2);
    assertThat(content).extracting(r -> r.get("status")).containsOnly("PENDING");
    assertThat(content).extracting(r -> r.get("rowNumber")).containsExactly(2, 3);

    var keys = storageService.listKeys(TakeOnStorageKeys.batchPrefix(schema, batchId));
    assertThat(keys).hasSize(1);
    assertThat(keys.getFirst())
        .matches("take-on/" + schema + "/" + batchId + "/[0-9a-f-]{36}/clients\\.csv");

    var batchAudit = auditEvents(jdbc, schema, batchId);
    assertThat(batchAudit)
        .extracting(TakeOnTestApi.AuditRow::eventType)
        .containsExactly("takeon.batch.created");
    assertThat(batchAudit.getFirst().details())
        .containsOnlyKeys("batch_id", "dataset")
        .containsEntry("dataset", "CLIENTS");
    var sourceAudit = auditEvents(jdbc, schema, sourceId);
    assertThat(sourceAudit)
        .extracting(TakeOnTestApi.AuditRow::eventType)
        .containsExactly("takeon.source.uploaded");
    assertThat(sourceAudit.getFirst().details())
        .containsOnlyKeys(
            "batch_id",
            "source_id",
            "section",
            "file_format",
            "file_size_kb",
            "row_count",
            "malformed_count")
        .containsEntry("row_count", 2)
        .containsEntry("file_format", "CSV");
  }

  @Test
  void mapThenValidateThenRemapWalksUploadedMappedValidatedMapped() throws Exception {
    UUID[] ids =
        create(mockMvc, owner(), "CLIENTS", "clients.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    mapTargets(mockMvc, owner(), ids[0], ids[1], CLIENT_TARGETS)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MAPPED"))
        .andExpect(jsonPath("$.sources[0].mapped").value(true))
        .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.hasItem("VALIDATE")));
    validate(mockMvc, owner(), ids[0])
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VALIDATED"))
        .andExpect(jsonPath("$.counts.total").value(2))
        .andExpect(jsonPath("$.counts.valid").value(2))
        .andExpect(
            jsonPath("$.validationHash")
                .value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
        .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.hasItem("REVALIDATE")));
    validate(mockMvc, owner(), ids[0]).andExpect(jsonPath("$.status").value("VALIDATED"));
    mapTargets(mockMvc, owner(), ids[0], ids[1], CLIENT_TARGETS)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MAPPED"));
    var mappedAudit =
        auditEvents(jdbc, schema, ids[1]).stream()
            .filter(a -> a.eventType().equals("takeon.source.mapped"))
            .toList();
    assertThat(mappedAudit).hasSize(2);
    assertThat(mappedAudit.getFirst().details())
        .containsOnlyKeys(
            "batch_id",
            "source_id",
            "mapped_field_count",
            "custom_field_count",
            "parked_column_count")
        .containsEntry("mapped_field_count", 7);
    var validated =
        auditEvents(jdbc, schema, ids[0]).stream()
            .filter(a -> a.eventType().equals("takeon.batch.validated"))
            .toList();
    assertThat(validated).hasSize(2);
    assertThat(validated.getFirst().details())
        .containsOnlyKeys(
            "batch_id",
            "dataset",
            "total_rows",
            "valid_rows",
            "error_rows",
            "duplicate_rows",
            "block_count")
        .containsEntry("total_rows", 2);
  }

  @Test
  void validateFromUploadedIsAWrongStateConflict() throws Exception {
    UUID[] ids =
        create(mockMvc, owner(), "CLIENTS", "clients.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    validate(mockMvc, owner(), ids[0])
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Take-on batch not in the right state"))
        .andExpect(
            jsonPath("$.detail")
                .value("Only a mapped or validated take-on batch can be validated."))
        .andExpect(jsonPath("$.code").value("TAKE_ON_WRONG_STATE"));
  }

  @Test
  void debtorBatchNeedsItsCutoverDateAndAChangedDateReturnsItToMapped() throws Exception {
    UUID[] ids =
        create(
            mockMvc, owner(), "DEBTOR_BALANCES", "debtors.csv", TakeOnTestFiles.csv(DEBTORS_CSV));
    mapTargets(mockMvc, owner(), ids[0], ids[1], DEBTOR_TARGETS).andExpect(status().isOk());
    validate(mockMvc, owner(), ids[0])
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Cutover date required"))
        .andExpect(jsonPath("$.detail").value("Enter the cutover date before validating."))
        .andExpect(jsonPath("$.code").value("TAKE_ON_CUTOVER_DATE_REQUIRED"));

    position(mockMvc, owner(), ids[0], "2026-10-31")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cutoverDate").value("2026-10-31"));
    validate(mockMvc, owner(), ids[0]).andExpect(jsonPath("$.status").value("VALIDATED"));
    position(mockMvc, owner(), ids[0], "2026-10-31")
        .andExpect(jsonPath("$.status").value("VALIDATED"));
    position(mockMvc, owner(), ids[0], "2026-11-30")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MAPPED"));
  }

  @Test
  void aCutoverDateDoesNotApplyToAClientsTakeOn() throws Exception {
    UUID[] ids =
        create(mockMvc, owner(), "CLIENTS", "clients.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    position(mockMvc, owner(), ids[0], "2026-10-31")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail").value("A cutover date applies only to trust and debtor take-ons."))
        .andExpect(jsonPath("$.code").value("TAKE_ON_POSITION_NOT_APPLICABLE"));
  }

  @Test
  void trustBatchTakesOneFilePerSectionAndRemovingTheUnmappedOneLeavesItMapped() throws Exception {
    UUID[] ids =
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
            ids[0],
            ids[1],
            Map.of(
                "clientRef", "Client reference",
                "matterRef", "Matter reference",
                "balance", "Balance at cutover"))
        .andExpect(jsonPath("$.status").value("MAPPED"));

    var added =
        addSource(
                mockMvc,
                owner(),
                ids[0],
                "UNIDENTIFIED_DEPOSITS",
                "unidentified.csv",
                TakeOnTestFiles.csv("Date received,Amount\n01/10/2026,250.00\n"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.batch.status").value("UPLOADED"))
            .andExpect(jsonPath("$.batch.sources.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID second = UUID.fromString(JsonPath.read(added, "$.sourceId"));

    addSource(
            mockMvc,
            owner(),
            ids[0],
            "UNIDENTIFIED_DEPOSITS",
            "again.csv",
            TakeOnTestFiles.csv("Date received,Amount\n01/10/2026,250.00\n"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_SECTION_EXISTS"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "This take-on already has a file for unidentified deposits. Remove it to"
                        + " upload another."));

    mockMvc
        .perform(delete("/api/take-on/batches/" + ids[0] + "/sources/" + second).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MAPPED"))
        .andExpect(jsonPath("$.sources.length()").value(1));
  }

  @Test
  void aSingleFileTakeOnRefusesASecondFileUntilTheFirstIsRemoved() throws Exception {
    UUID[] ids =
        create(mockMvc, owner(), "CLIENTS", "clients.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    addSource(mockMvc, owner(), ids[0], null, "more.csv", TakeOnTestFiles.csv(CLIENTS_CSV))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_SINGLE_SOURCE"))
        .andExpect(
            jsonPath("$.detail").value("This take-on has one file. Remove it to upload another."));

    mockMvc
        .perform(delete("/api/take-on/batches/" + ids[0] + "/sources/" + ids[1]).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UPLOADED"))
        .andExpect(jsonPath("$.sources.length()").value(0));
    assertThat(storageService.listKeys(TakeOnStorageKeys.batchPrefix(schema, ids[0]))).isEmpty();
    rows(mockMvc, owner(), ids[0], null).andExpect(jsonPath("$.page.totalElements").value(0));

    addSource(mockMvc, owner(), ids[0], null, "replacement.csv", TakeOnTestFiles.csv(CLIENTS_CSV))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.batch.sources[0].fileName").value("replacement.csv"));
  }

  @Test
  void aWorkbookListsItsHiddenSheetAndPickingASheetReReadsTheRows() throws Exception {
    byte[] workbook =
        TakeOnTestFiles.xlsx(
            wb -> {
              Sheet visible = wb.createSheet("Clients");
              TakeOnTestFiles.writeRow(visible.createRow(0), List.of("Client name", "Email"));
              TakeOnTestFiles.writeRow(visible.createRow(1), List.of("A", "a@example.test"));
              Sheet hidden = wb.createSheet("Archive");
              TakeOnTestFiles.writeRow(hidden.createRow(0), List.of("Exported from the old books"));
              TakeOnTestFiles.writeRow(hidden.createRow(1), List.of("Client name", "Email"));
              TakeOnTestFiles.writeRow(hidden.createRow(2), List.of("B", "b@example.test"));
              TakeOnTestFiles.writeRow(hidden.createRow(3), List.of("C", "c@example.test"));
              TakeOnTestFiles.writeRow(hidden.createRow(4), List.of("D", "d@example.test"));
              wb.setSheetHidden(1, true);
            });
    UUID[] ids = create(mockMvc, owner(), "CLIENTS", "clients.xlsx", workbook);
    detail(mockMvc, owner(), ids[0])
        .andExpect(jsonPath("$.sources[0].sheetNames[0].name").value("Clients"))
        .andExpect(jsonPath("$.sources[0].sheetNames[0].hidden").value(false))
        .andExpect(jsonPath("$.sources[0].sheetNames[1].name").value("Archive"))
        .andExpect(jsonPath("$.sources[0].sheetNames[1].hidden").value(true))
        .andExpect(jsonPath("$.sources[0].sheetName").value("Clients"))
        .andExpect(jsonPath("$.sources[0].rowCount").value(1));
    var before =
        rowContent(
            rows(mockMvc, owner(), ids[0], null).andReturn().getResponse().getContentAsString());
    TakeOnTestApi.map(
            mockMvc,
            owner(),
            ids[0],
            ids[1],
            Map.of(
                "targets",
                Map.of("name", "Client name", "email", "Email"),
                "defaults",
                Map.of(
                    "ficaClientType", "Individual",
                    "cddCompletedDate", "01/01/2024",
                    "beneficialOwnershipStatus", "Done",
                    "screeningStatus", "Not screened",
                    "cddRecordsLocation", "Box 9")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MAPPED"))
        .andExpect(jsonPath("$.sources[0].mapping.defaults.screeningStatus").value("Not screened"));

    sheet(mockMvc, owner(), ids[0], ids[1], "Archive", 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.batch.status").value("UPLOADED"))
        .andExpect(jsonPath("$.batch.sources[0].sheetName").value("Archive"))
        .andExpect(jsonPath("$.batch.sources[0].headerRow").value(2))
        .andExpect(jsonPath("$.batch.sources[0].rowCount").value(3))
        .andExpect(jsonPath("$.batch.sources[0].mapped").value(false))
        .andExpect(jsonPath("$.headers[1]").value("Email"));
    var after =
        rowContent(
            rows(mockMvc, owner(), ids[0], null).andReturn().getResponse().getContentAsString());
    assertThat(after).hasSize(3);
    assertThat(after).extracting(r -> r.get("rowNumber")).containsExactly(3, 4, 5);
    assertThat(after)
        .extracting(r -> r.get("id"))
        .doesNotContainAnyElementsOf(before.stream().map(r -> r.get("id")).toList());

    sheet(mockMvc, owner(), ids[0], ids[1], "Nope", 1)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("TAKE_ON_SHEET_NOT_FOUND"));
  }

  @Test
  void cancelPurgesTheFileAndTheValuesAndKeepsTheShells() throws Exception {
    UUID[] ids =
        create(mockMvc, owner(), "CLIENTS", "clients.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    mapTargets(mockMvc, owner(), ids[0], ids[1], CLIENT_TARGETS);
    validate(mockMvc, owner(), ids[0]).andExpect(jsonPath("$.status").value("VALIDATED"));
    assertThat(storageService.listKeys(TakeOnStorageKeys.batchPrefix(schema, ids[0]))).hasSize(1);
    assertThat(rowsWithIdentifiers(ids[0])).as("dedup keys and source refs before").isEqualTo(2);

    cancel(mockMvc, owner(), ids[0])
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
        .andExpect(jsonPath("$.allowedActions.length()").value(0))
        .andExpect(jsonPath("$.sources[0].purged").value(true))
        .andExpect(jsonPath("$.counts.total").value(2));

    assertThat(storageService.listKeys(TakeOnStorageKeys.batchPrefix(schema, ids[0]))).isEmpty();
    var content =
        rowContent(
            rows(mockMvc, owner(), ids[0], null).andReturn().getResponse().getContentAsString());
    assertThat(content).hasSize(2);
    assertThat(content).extracting(r -> r.get("values")).containsOnly(Map.of("purged", true));
    assertThat(content).extracting(r -> r.get("status")).containsOnly("VALIDATED");
    assertNoRowPii(ids[0]);
    assertThat(storageKey(ids[1])).as("the key is forgotten once the delete succeeded").isNull();

    var cancelled =
        auditEvents(jdbc, schema, ids[0]).stream()
            .filter(a -> a.eventType().equals("takeon.batch.cancelled"))
            .toList();
    assertThat(cancelled).hasSize(1);
    assertThat(cancelled.getFirst().details())
        .containsOnlyKeys("batch_id", "dataset", "purged_sources", "reason_code")
        .containsEntry("reason_code", "USER")
        .containsEntry("purged_sources", 1);

    cancel(mockMvc, owner(), ids[0])
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_WRONG_STATE"))
        .andExpect(jsonPath("$.detail").value("Only an open take-on batch can be cancelled."));
    mapTargets(mockMvc, owner(), ids[0], ids[1], CLIENT_TARGETS)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_WRONG_STATE"));
    validate(mockMvc, owner(), ids[0])
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_WRONG_STATE"));
    sheet(mockMvc, owner(), ids[0], ids[1], "CSV", 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_WRONG_STATE"));
    mockMvc
        .perform(
            get("/api/take-on/batches/" + ids[0] + "/sources/" + ids[1] + "/file").with(owner()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TAKE_ON_FILE_PURGED"));
  }

  @Test
  void theRawFileDownloadsThroughTheBackendAndIsAudited() throws Exception {
    byte[] bytes = TakeOnTestFiles.csv(CLIENTS_CSV);
    UUID[] ids = create(mockMvc, owner(), "CLIENTS", "clients.csv", bytes);
    var body =
        mockMvc
            .perform(
                get("/api/take-on/batches/" + ids[0] + "/sources/" + ids[1] + "/file")
                    .with(owner()))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Disposition", org.hamcrest.Matchers.containsString("clients.csv")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(body).isEqualTo(bytes);
    assertThat(auditEvents(jdbc, schema, ids[1]))
        .filteredOn(a -> a.eventType().equals("takeon.file.downloaded"))
        .singleElement()
        .satisfies(a -> assertThat(a.details()).containsOnlyKeys("batch_id", "source_id"));
  }

  @Test
  void templatesAndTheDatasetCatalogueNeedNoData() throws Exception {
    var csv =
        mockMvc
            .perform(get("/api/take-on/templates/CLIENTS").with(owner()))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("take-on-clients-template.csv")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(csv[0]).isEqualTo((byte) 0xEF);
    String text = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
    assertThat(text).startsWith("Client name,Email,Phone,");
    mockMvc
        .perform(get("/api/take-on/templates/TRUST_POSITION").with(owner()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/take-on/templates/TRUST_POSITION?section=INVESTMENTS").with(owner()))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    org.hamcrest.Matchers.containsString(
                        "take-on-trust-position-investments-template.csv")));

    mockMvc
        .perform(get("/api/take-on/datasets").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].dataset").value("CLIENTS"))
        .andExpect(jsonPath("$[0].label").value("Clients"))
        .andExpect(jsonPath("$[0].customFieldEntityType").value("CUSTOMER"))
        .andExpect(jsonPath("$[0].sections[0].fields[0].key").value("name"))
        .andExpect(jsonPath("$[0].sections[0].fields[0].required").value(true))
        .andExpect(jsonPath("$[2].dataset").value("TRUST_POSITION"))
        .andExpect(jsonPath("$[2].requiresTrustAccount").value(true))
        .andExpect(jsonPath("$[2].sections.length()").value(4))
        .andExpect(jsonPath("$[2].sections[0].label").value("Client and matter balances"))
        .andExpect(jsonPath("$[3].dataset").value("DEBTOR_BALANCES"))
        .andExpect(jsonPath("$[3].requiresCutoverDate").value(true));
  }

  @Test
  void cancelClearsTheRowMessageTextsButKeepsTheirCodes() throws Exception {
    String csv =
        CLIENTS_CSV.replace("thandi@example.test", "not-an-email-thandi")
            + "Thandi Again,THANDI2@example.test,Individual,01/02/2024,Done,Screened clear,Box 3\n";
    UUID[] ids = create(mockMvc, owner(), "CLIENTS", "messages.csv", TakeOnTestFiles.csv(csv));
    mapTargets(mockMvc, owner(), ids[0], ids[1], CLIENT_TARGETS);
    validate(mockMvc, owner(), ids[0]).andExpect(jsonPath("$.counts.error").value(1));
    Integer quoting =
        jdbc.queryForObject(
            "SELECT count(*) FROM \"%s\".take_on_rows WHERE batch_id = ? AND errors::text LIKE ?"
                .formatted(schema),
            Integer.class,
            ids[0],
            "%not-an-email-thandi%");
    assertThat(quoting).as("the error message quotes the cell before the purge").isEqualTo(1);

    cancel(mockMvc, owner(), ids[0]).andExpect(status().isOk());

    assertNoRowPii(ids[0]);
    var content =
        rowContent(
            rows(mockMvc, owner(), ids[0], null).andReturn().getResponse().getContentAsString());
    assertThat(content)
        .filteredOn(r -> "ERROR".equals(r.get("status")))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.get("sourceRef")).isNull();
              @SuppressWarnings("unchecked")
              var errors = (List<Map<String, Object>>) r.get("errors");
              assertThat(errors)
                  .singleElement()
                  .satisfies(
                      e -> {
                        assertThat(e.get("code")).isEqualTo("EMAIL_INVALID");
                        assertThat(e.get("field")).isEqualTo("email");
                        assertThat(e.get("message")).isNull();
                      });
            });
  }

  @Test
  void aPurgedFileWhoseDeleteFailedKeepsItsKeyAndTheNextPurgeRunDeletesIt() throws Exception {
    UUID[] ids = create(mockMvc, owner(), "CLIENTS", "retry.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    String key = storageKey(ids[1]);
    byte[] bytes = storageService.download(key);
    cancel(mockMvc, owner(), ids[0]).andExpect(status().isOk());
    assertThat(storageKey(ids[1])).isNull();

    // A delete that failed after commit: the object is still there and the source keeps its key.
    storageService.upload(key, bytes, "text/csv");
    jdbc.update(
        "UPDATE \"%s\".take_on_sources SET s3_key = ? WHERE id = ?".formatted(schema), key, ids[1]);
    mockMvc
        .perform(
            get("/api/take-on/batches/" + ids[0] + "/sources/" + ids[1] + "/file").with(owner()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TAKE_ON_FILE_PURGED"));
    detail(mockMvc, owner(), ids[0]).andExpect(jsonPath("$.sources[0].purged").value(true));

    purge(); // the daily job retries the delete

    assertThat(storageService.listKeys(TakeOnStorageKeys.batchPrefix(schema, ids[0]))).isEmpty();
    assertThat(storageKey(ids[1])).isNull();
  }

  private String storageKey(UUID sourceId) {
    return jdbc.queryForObject(
        "SELECT s3_key FROM \"%s\".take_on_sources WHERE id = ?".formatted(schema),
        String.class,
        sourceId);
  }

  private int rowsWithIdentifiers(UUID batchId) {
    return jdbc.queryForObject(
        ("SELECT count(*) FROM \"%s\".take_on_rows WHERE batch_id = ?"
                + " AND (source_ref IS NOT NULL OR dedup_key IS NOT NULL)")
            .formatted(schema),
        Integer.class,
        batchId);
  }

  /** §17.10 X4: after a purge no row keeps a value, an identifier or a message text. */
  private void assertNoRowPii(UUID batchId) {
    Integer leaking =
        jdbc.queryForObject(
            ("SELECT count(*) FROM \"%s\".take_on_rows WHERE batch_id = ? AND ("
                    + " raw_values <> '{\"purged\": true}'::jsonb"
                    + " OR mapped_values <> '{\"purged\": true}'::jsonb"
                    + " OR source_ref IS NOT NULL OR dedup_key IS NOT NULL"
                    + " OR jsonb_path_exists(errors, '$[*].message')"
                    + " OR jsonb_path_exists(warnings, '$[*].message'))")
                .formatted(schema),
            Integer.class,
            batchId);
    assertThat(leaking).isZero();
  }

  // --- abandoned-batch purge (FD-24, X5) ---

  @Test
  void theAbandonedPurgeCancelsOnlyOpenBatchesOlderThanThirtyDays() throws Exception {
    UUID[] old = create(mockMvc, owner(), "CLIENTS", "old.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    UUID[] fresh =
        create(mockMvc, owner(), "CLIENTS", "fresh.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    UUID[] cancelledOld =
        create(mockMvc, owner(), "CLIENTS", "gone.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    cancel(mockMvc, owner(), cancelledOld[0]).andExpect(status().isOk());
    TakeOnTestApi.backdate(jdbc, schema, old[0], 31);
    TakeOnTestApi.backdate(jdbc, schema, cancelledOld[0], 31);

    purge();

    detail(mockMvc, owner(), old[0]).andExpect(jsonPath("$.status").value("CANCELLED"));
    detail(mockMvc, owner(), fresh[0]).andExpect(jsonPath("$.status").value("UPLOADED"));
    assertNoRowPii(old[0]);
    assertThat(storageService.listKeys(TakeOnStorageKeys.batchPrefix(schema, old[0]))).isEmpty();
    assertThat(storageService.listKeys(TakeOnStorageKeys.batchPrefix(schema, fresh[0]))).hasSize(1);
    var abandoned =
        auditEvents(jdbc, schema, old[0]).stream()
            .filter(a -> a.eventType().equals("takeon.batch.cancelled"))
            .toList();
    assertThat(abandoned)
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.details()).containsEntry("reason_code", "ABANDONED");
              assertThat(a.actorType()).isEqualTo("SYSTEM");
            });
    // the user-cancelled batch is never selected again (status re-check): still one cancel event
    assertThat(auditEvents(jdbc, schema, cancelledOld[0]))
        .filteredOn(a -> a.eventType().equals("takeon.batch.cancelled"))
        .singleElement()
        .satisfies(a -> assertThat(a.details()).containsEntry("reason_code", "USER"));
  }

  @Test
  void theAbandonedPurgeSkipsABatchAnotherTransactionHoldsAndPurgesItOnTheNextRun()
      throws Exception {
    UUID[] held = create(mockMvc, owner(), "CLIENTS", "held.csv", TakeOnTestFiles.csv(CLIENTS_CSV));
    TakeOnTestApi.backdate(jdbc, schema, held[0], 31);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var holder =
        new Thread(
            () ->
                RequestScopes.runForTenant(
                    schema,
                    ORG_ID,
                    () ->
                        new TransactionTemplate(transactionManager)
                            .executeWithoutResult(
                                tx -> {
                                  batchRepository.findByIdForUpdate(held[0]).orElseThrow();
                                  locked.countDown();
                                  try {
                                    release.await(30, TimeUnit.SECONDS);
                                  } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                  }
                                })));
    holder.start();
    try {
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      Instant started = Instant.now();
      purge();
      assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(10));
      detail(mockMvc, owner(), held[0]).andExpect(jsonPath("$.status").value("UPLOADED"));
    } finally {
      release.countDown();
      holder.join(10_000);
    }
    purge();
    detail(mockMvc, owner(), held[0]).andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  private void purge() {
    Instant cutoff = Instant.now().minus(TakeOnAbandonedBatchPurgeJob.RETENTION);
    RequestScopes.runForTenant(schema, ORG_ID, () -> purgeJob.purgeAbandonedInTenant(cutoff));
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
