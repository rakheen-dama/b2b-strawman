package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnClientsFiles.csv;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnClientsFiles.mapping;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnClientsFiles.row;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.attest;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.detail;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.portalContacts;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.resultCsv;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.revokeSignOff;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rowContent;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.rows;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.validatedClients;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnProblems.TakeOnProblemException;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.Validated;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 93 655A (task 655.8, §17.3.3, §17.3.4): the clients take-on through the endpoints the
 * wizard uses — upload → map → validate → attest → commit ({@link TakeOnTestDrain}: claim, then the
 * drain as the job worker runs it) → results. Imported clients are born ACTIVE with their FICA
 * attestation and nothing else fires: no checklist, no portal contact, no lifecycle event or
 * automation, no accounting push. Every assertion is scoped to this test's own rows (id-scope).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientsTakeOnIntegrationTest {

  private static final String ORG_ID = "org_clients_takeon_test";
  private static final String FIRM = "Clients Take-on Attorneys";
  private static final String OWNER = "user_cto_owner";
  private static final String CLIENTS_ONLY = "user_cto_clients";
  private static final String DEFAULTS_CHANGED =
      "The batch defaults shown are not the ones the batch was validated with. Reload the take-on"
          + " and attest again.";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;
  @Autowired private CustomerRepository customers;
  @Autowired private ChecklistInstanceRepository checklistInstances;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private DataSource dataSource;
  @Autowired private TakeOnRowRepository takeOnRows;

  private String schema;
  private UUID ownerId;

  @BeforeAll
  void setup() throws Exception {
    schema = provisioningService.provisionTenant(ORG_ID, FIRM, "legal-za").schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "cto_owner@test.com", "Clients Owner", "owner"));
    UUID clientsOnly =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, CLIENTS_ONLY, "cto_clients@test.com", "Clients Only", "member"));
    assignRole(clientsOnly, createRole("Clients take-on", "MANAGE_TAKE_ON", "CUSTOMER_MANAGEMENT"));
  }

  // 1
  @Test
  void commitCreatesActiveClientsWithTheirAttestation_andNothingElseFires() throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Lindiwe Mokoena", "lindiwe.mokoena@example.test")
                    .rating("Low")
                    .ref("PREV-101")
                    .branch("Rosebank"),
                row("Pieter Botha", "pieter.botha@example.test").rating("").ref("PREV-102")),
            mapping());
    attestAndCommit(batch);

    var detail = json(detail(mockMvc, owner(), batch.batchId()));
    assertThat((String) JsonPath.read(detail, "$.status")).isEqualTo("COMPLETED");
    assertThat((Integer) JsonPath.read(detail, "$.counts.created")).isEqualTo(2);
    assertThat((Integer) JsonPath.read(detail, "$.counts.skipped")).isZero();
    assertThat((Integer) JsonPath.read(detail, "$.counts.failed")).isZero();
    assertThat((List<String>) JsonPath.read(detail, "$.allowedActions"))
        .containsExactly("DOWNLOAD_RESULT", "CREATE_PORTAL_ACCESS");

    var created = createdRows(batch.batchId());
    assertThat(created).hasSize(2);
    for (var r : created) {
      UUID clientId = UUID.fromString((String) r.get("result_entity_id"));
      var client = inTenant(() -> customers.findById(clientId).orElseThrow());
      assertThat(client.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
      assertThat(client.getTakeOnBatchId()).isEqualTo(batch.batchId());
      assertThat(client.getTakenOnAt()).isNotNull();
      assertThat(client.getLifecycleStatusChangedAt()).isNotNull();
      assertThat(client.getLifecycleStatusChangedBy()).isEqualTo(ownerId);
      assertThat(client.getCreatedBy()).isEqualTo(ownerId);
      assertThat(client.getCountry()).isEqualTo("ZA");
      // no checklist, no portal contact (no CustomerCreatedEvent, no lifecycle transition)
      assertThat(inTenant(() -> checklistInstances.findByCustomerId(clientId))).isEmpty();
      assertThat(inTenant(() -> portalContactRepository.findByCustomerId(clientId))).isEmpty();
      // no accounting push, no automation (no CustomerStatusChangedEvent)
      assertThat(
              count("SELECT count(*) FROM %s.accounting_sync_entry WHERE entity_id = ?", clientId))
          .isZero();
      assertThat(
              count(
                  "SELECT count(*) FROM %s.automation_executions"
                      + " WHERE trigger_event_data::text LIKE '%%' || ? || '%%'",
                  clientId.toString()))
          .isZero();
      // audit: customer.created {source, take_on_batch_id} + customer.fica_attested, no lifecycle
      var audit = TakeOnTestApi.auditEvents(jdbc, schema, clientId);
      assertThat(audit)
          .extracting(TakeOnTestApi.AuditRow::eventType)
          .containsExactly("customer.created", "customer.fica_attested");
      assertThat(audit.getFirst().details())
          .isEqualTo(Map.of("source", "TAKE_ON", "take_on_batch_id", batch.batchId().toString()));
      assertThat(audit.get(1).details())
          .containsOnlyKeys("attestation_id", "take_on_batch_id")
          .containsEntry("take_on_batch_id", batch.batchId().toString());
      assertThat(audit.getFirst().actorType()).isEqualTo("USER");
      assertThat(audit.getFirst().actorName()).isEqualTo("Clients Owner");
    }

    // the FICA attestation rows: dates from the row, intervals from the firm (LOW 36 months)
    var lindiwe = attestationOf(clientIdByRef(batch.batchId(), "PREV-101"));
    assertThat(lindiwe.get("risk_rating")).isEqualTo("LOW");
    assertThat(lindiwe.get("next_review_date").toString()).isEqualTo("2027-02-01");
    assertThat(lindiwe.get("fica_client_type")).isEqualTo("NATURAL_PERSON");
    assertThat(lindiwe.get("cdd_records_location")).isEqualTo("Archive box 1");
    assertThat(lindiwe.get("source_system_ref")).isEqualTo("PREV-101");
    assertThat(lindiwe.get("source_of_funds_status")).isEqualTo("NOT_REQUIRED");
    assertThat(lindiwe.get("status")).isEqualTo("ACTIVE");
    assertThat(lindiwe.get("attested_by")).isEqualTo(ownerId);
    var pieter = attestationOf(clientIdByRef(batch.batchId(), "PREV-102"));
    assertThat(pieter.get("risk_rating")).isEqualTo("UNRATED");
    assertThat(pieter.get("next_review_date")).isNull();

    // the parked column is a note on the record; the row points at its attestation
    var lindiweClient =
        inTenant(
            () -> customers.findById(clientIdByRef(batch.batchId(), "PREV-101")).orElseThrow());
    assertThat(lindiweClient.getCustomFields())
        .containsEntry("takeon_parked", Map.of("Branch", "Rosebank"));
    var lindiweRow =
        created.stream()
            .filter(r -> "PREV-101".equals(r.get("source_ref")))
            .findFirst()
            .orElseThrow();
    assertThat((String) lindiweRow.get("result_refs")).contains(lindiwe.get("id").toString());
  }

  // 2
  @Test
  void dedup_byEmailThenNameAndTaxThenNameAndId_againstExistingClients() throws Exception {
    String byEmail =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt(), "Existing Email", "dup.email@example.test");
    UUID byTax =
        inTenant(
            () -> {
              var c =
                  TestCustomerFactory.createActiveCustomer(
                      "Tax Match Holdings", "tax.match@example.test", ownerId);
              c.setTaxNumber("9001112223");
              return customers.save(c).getId();
            });
    UUID byId =
        inTenant(
            () ->
                customers
                    .save(
                        new io.b2mash.b2b.b2bstrawman.customer.Customer(
                            "Sipho Id Match",
                            "id.match@example.test",
                            null,
                            "8001015009087",
                            null,
                            ownerId,
                            io.b2mash.b2b.b2bstrawman.customer.CustomerType.INDIVIDUAL,
                            LifecycleStatus.ACTIVE))
                    .getId());
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Different Name", "DUP.EMAIL@example.test").ref("D-1"),
                row("tax match holdings", "other.tax@example.test").tax("9001112223").ref("D-2"),
                row("SIPHO ID MATCH", "other.id@example.test").idNumber("8001015009087").ref("D-3"),
                row("Brand New Client", "brand.new@example.test").ref("D-4")),
            mapping());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(4);
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.duplicate")).isEqualTo(3);
    var byRef = rowsByRef(batch.batchId());
    assertThat(byRef.get("D-1").get("duplicateOfId")).isEqualTo(byEmail);
    assertThat(byRef.get("D-2").get("duplicateOfId")).isEqualTo(byTax.toString());
    assertThat(byRef.get("D-3").get("duplicateOfId")).isEqualTo(byId.toString());
    assertThat(byRef.get("D-4").get("duplicateOfId")).isNull();

    attestAndCommit(batch);
    var after = rowsByRef(batch.batchId());
    assertThat(after.get("D-1").get("status")).isEqualTo("SKIPPED_DUPLICATE");
    assertThat(after.get("D-2").get("status")).isEqualTo("SKIPPED_DUPLICATE");
    assertThat(after.get("D-3").get("status")).isEqualTo("SKIPPED_DUPLICATE");
    assertThat(after.get("D-4").get("status")).isEqualTo("CREATED");
    assertThat(resultEntity(batch.batchId(), "D-1")).isEqualTo(UUID.fromString(byEmail));
    assertThat(resultEntity(batch.batchId(), "D-2")).isEqualTo(byTax);
    var detail = json(detail(mockMvc, owner(), batch.batchId()));
    assertThat((Integer) JsonPath.read(detail, "$.counts.created")).isEqualTo(1);
    assertThat((Integer) JsonPath.read(detail, "$.counts.skipped")).isEqualTo(3);
    // the existing UI client is untouched (still PROSPECT, no attestation)
    assertThat(
            inTenant(() -> customers.findById(UUID.fromString(byEmail)).orElseThrow())
                .getLifecycleStatus())
        .isEqualTo(LifecycleStatus.PROSPECT);
  }

  // 3
  @Test
  void inFileDuplicates_byNameAndTaxOrId_areErrorsOnTheLaterRow() throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Same Tax Pty", "same.tax.1@example.test").tax("7001").ref("F-1"),
                row("SAME TAX PTY", "same.tax.2@example.test").tax("7001").ref("F-2"),
                row("Same Id", "same.id.1@example.test").idNumber("9002025009081").ref("F-3"),
                row("same id", "same.id.2@example.test").idNumber("9002025009081").ref("F-4")),
            mapping());
    var byRef = rowsByRef(batch.batchId());
    assertThat(byRef.get("F-1").get("status")).isEqualTo("VALIDATED");
    assertThat(byRef.get("F-3").get("status")).isEqualTo("VALIDATED");
    assertError(
        byRef.get("F-2"), "DUPLICATE_IN_FILE", "taxNumber", "Duplicate of row 2 in this file");
    assertError(
        byRef.get("F-4"), "DUPLICATE_IN_FILE", "idNumber", "Duplicate of row 4 in this file");
  }

  // 4
  @Test
  void clientRules_emailRequiredPlaceholderCountryScreeningDateAndAddressWarning()
      throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("No Email Client", "").ref("R-1"),
                row("Placeholder Client", "noemail@firm.example").ref("R-2"),
                row("Bad Country", "bad.country@example.test")
                    .address("1 Road", "Durban", "Z9")
                    .ref("R-3"),
                row("Screened No Date", "no.date@example.test")
                    .screening("Screened clear", "")
                    .ref("R-4"),
                row("Not Screened", "not.screened@example.test")
                    .screening("Not screened", "")
                    .ref("R-5"),
                row("No Address", "no.address@example.test").address("", "", "").ref("R-6"),
                // two letters, but user-assigned — not a country (CR-4)
                row("Zz Country", "zz.country@example.test")
                    .address("1 Road", "Durban", "ZZ")
                    .ref("R-7"),
                row("Lower Country", "lower.country@example.test")
                    .address("1 Road", "Durban", "za")
                    .ref("R-8")),
            mapping());
    var byRef = rowsByRef(batch.batchId());
    assertError(
        byRef.get("R-7"),
        "COUNTRY_INVALID",
        "country",
        "Enter the country as its two-letter ISO code (for example ZA), or leave it blank.");
    assertThat(byRef.get("R-8").get("status")).isEqualTo("VALIDATED");
    @SuppressWarnings("unchecked")
    var lowerValues = (Map<String, Object>) byRef.get("R-8").get("values");
    assertThat(lowerValues).containsEntry("country", "ZA");
    assertError(
        byRef.get("R-1"),
        "REQUIRED",
        "email",
        "An email address is required to create a client in Kazi");
    assertError(
        byRef.get("R-2"),
        "EMAIL_PLACEHOLDER",
        "email",
        "An email address is required to create a client in Kazi");
    assertError(byRef.get("R-3"), "COUNTRY_INVALID", "country", null);
    assertError(
        byRef.get("R-4"),
        "REQUIRED",
        "screeningDate",
        "Screening date is required unless the client was not screened.");
    assertThat(byRef.get("R-5").get("status")).isEqualTo("VALIDATED");
    assertThat(byRef.get("R-6").get("status")).isEqualTo("VALIDATED");
    @SuppressWarnings("unchecked")
    var warnings = (List<Map<String, Object>>) byRef.get("R-6").get("warnings");
    assertThat(warnings)
        .extracting(w -> w.get("code"), w -> w.get("message"))
        .contains(
            org.assertj.core.groups.Tuple.tuple(
                "ADDRESS_MISSING",
                "This client has no address; fee notes cannot be generated for them until one is"
                    + " added (statements and brought-forward balances are unaffected)"));
    @SuppressWarnings("unchecked")
    var r5 = (Map<String, Object>) byRef.get("R-5").get("values");
    assertThat(r5)
        .containsEntry("riskRating", "LOW")
        .containsEntry("sourceOfFundsStatus", "NOT_REQUIRED");
  }

  // 5
  @Test
  void aClientCreatedInTheUiBetweenValidationAndCommit_failsThatRow_thenSkipsOnARerun()
      throws Exception {
    String file =
        csv(
            row("Race Client", "race.client@example.test").ref("U-1"),
            row("Calm Client", "calm.client@example.test").ref("U-2"));
    var batch = validatedClients(mockMvc, owner(), file, mapping());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.duplicate")).isZero();
    String uiClient =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt(), "Race Client UI", "race.client@example.test");

    attestAndCommit(batch);
    var byRef = rowsByRef(batch.batchId());
    assertThat(byRef.get("U-1").get("status")).isEqualTo("FAILED");
    assertThat(byRef.get("U-1").get("failureReason"))
        .isEqualTo("A client with this email address already exists");
    assertThat(byRef.get("U-2").get("resultEntityId"))
        .isEqualTo(resultEntity(batch.batchId(), "U-2").toString());
    assertThat(failureReason(batch.batchId(), "U-1"))
        .isEqualTo("A client with this email address already exists");
    assertThat(byRef.get("U-2").get("status")).isEqualTo("CREATED"); // isolation: the rest go on
    var detail = json(detail(mockMvc, owner(), batch.batchId()));
    assertThat((Integer) JsonPath.read(detail, "$.counts.failed")).isEqualTo(1);
    assertThat((Integer) JsonPath.read(detail, "$.counts.created")).isEqualTo(1);

    // re-run: a new batch of the same file marks both duplicates (email) and skips them
    var rerun = validatedClients(mockMvc, owner(), file, mapping());
    assertThat((Integer) JsonPath.read(rerun.json(), "$.counts.duplicate")).isEqualTo(2);
    attestAndCommit(rerun);
    var rerunRows = rowsByRef(rerun.batchId());
    assertThat(rerunRows.get("U-1").get("status")).isEqualTo("SKIPPED_DUPLICATE");
    assertThat(resultEntity(rerun.batchId(), "U-1")).isEqualTo(UUID.fromString(uiClient));
    assertThat(rerunRows.get("U-2").get("status")).isEqualTo("SKIPPED_DUPLICATE");
  }

  // 6
  @Test
  void aRowWhoseCommitThrows_isRolledBackAndFailed_andTheOthersAreCreated() throws Exception {
    String fieldId =
        TestEntityHelper.extractId(
            mockMvc
                .perform(
                    post("/api/field-definitions")
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"entityType": "CUSTOMER", "name": "Take-on score", "slug": "takeon_score",
                             "fieldType": "NUMBER", "required": false, "sortOrder": 0,
                             "validation": {"max": 10}}
                            """))
                .andExpect(status().isCreated())
                .andReturn());
    String file =
        TakeOnClientsFiles.HEADER.replace("Branch\n", "Branch,Score\n")
            + row("Score High", "score.high@example.test")
                .ref("S-1")
                .toString()
                .replace("\n", ",8\n")
            + row("Score Low", "score.low@example.test")
                .ref("S-2")
                .toString()
                .replace("\n", ",2\n");
    var body = new HashMap<>(mapping());
    body.put("customFields", Map.of("takeon_score", "Score"));
    var batch = validatedClients(mockMvc, owner(), file, body);
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(2);
    // the firm tightens the field after the dry run: the commit's validator refuses 8
    mockMvc
        .perform(
            put("/api/field-definitions/" + fieldId)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Take-on score", "required": false, "sortOrder": 0,
                     "validation": {"max": 5}}
                    """))
        .andExpect(status().isOk());

    attestAndCommit(batch);
    var byRef = rowsByRef(batch.batchId());
    assertThat(byRef.get("S-1").get("status")).isEqualTo("FAILED");
    assertThat(failureReason(batch.batchId(), "S-1"))
        .isEqualTo(
            "A custom field value was refused. Correct it in the previous system, then take it on"
                + " in a new batch.");
    assertThat(byRef.get("S-2").get("status")).isEqualTo("CREATED");
    assertThat(
            count(
                "SELECT count(*) FROM %s.customers WHERE lower(email) = ?",
                "score.high@example.test"))
        .as("the failed row's client was rolled back")
        .isZero();
    var kept =
        inTenant(() -> customers.findById(resultEntity(batch.batchId(), "S-2")).orElseThrow())
            .getCustomFields();
    assertThat(new java.math.BigDecimal(String.valueOf(kept.get("takeon_score"))))
        .isEqualByComparingTo("2");
  }

  // 7
  @Test
  void aCommitThatStopsMidBatch_resumesWhereItStopped_andCreatesEachClientOnce() throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Resume One", "resume.one@example.test").ref("M-1"),
                row("Resume Two", "resume.two@example.test").ref("M-2"),
                row("Resume Three", "resume.three@example.test").ref("M-3")),
            mapping());
    attestOk(batch);
    TakeOnTestDrain.commit(commitService, schema, ORG_ID, ownerId, batch.batchId(), batch.hash());
    TakeOnTestDrain.run(drain, schema, ORG_ID, Duration.ZERO); // stops after one row
    var mid = json(detail(mockMvc, owner(), batch.batchId()));
    assertThat((String) JsonPath.read(mid, "$.status")).isEqualTo("COMMITTING");
    assertThat((Integer) JsonPath.read(mid, "$.counts.created")).isEqualTo(1);
    assertThat((Integer) JsonPath.read(mid, "$.counts.valid")).isEqualTo(3);
    assertThat((List<String>) JsonPath.read(mid, "$.allowedActions")).isEmpty();

    TakeOnTestDrain.run(drain, schema, ORG_ID); // the next run (sweep or redelivery) finishes it
    TakeOnTestDrain.run(drain, schema, ORG_ID); // and a redelivered run finds nothing to do
    var done = json(detail(mockMvc, owner(), batch.batchId()));
    assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("COMPLETED");
    assertThat((Integer) JsonPath.read(done, "$.counts.created")).isEqualTo(3);
    for (String email :
        List.of(
            "resume.one@example.test", "resume.two@example.test", "resume.three@example.test")) {
      assertThat(count("SELECT count(*) FROM %s.customers WHERE email = ?", email)).isEqualTo(1);
    }
    assertThat(
            count(
                "SELECT count(*) FROM %s.audit_events WHERE event_type = 'takeon.batch.committed'"
                    + " AND entity_id = ?",
                batch.batchId()))
        .isEqualTo(1);
  }

  // 8
  @Test
  void attestation_isRequired_hashBound_andNeedsManageCompliance() throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Attest Client", "attest.client@example.test").ref("A-1")),
            mapping(Map.of("cddRecordsLocation", "Old system archive")));
    var before = json(detail(mockMvc, owner(), batch.batchId()));
    assertThat((List<String>) JsonPath.read(before, "$.allowedActions"))
        .contains("ATTEST", "REVALIDATE", "CANCEL")
        .doesNotContain("COMMIT", "REVOKE_ATTESTATION");
    String statement = JsonPath.read(before, "$.attestationStatement.text");
    assertThat((String) JsonPath.read(before, "$.attestationStatement.version"))
        .isEqualTo("FICA-ATTEST-v1");
    assertThat(statement)
        .startsWith(
            "I confirm that customer due diligence for the clients in this batch was"
                + " performed by "
                + FIRM
                + " in its previous system")
        .endsWith(
            "These clients will be shown as verified in the prior system, not as verified"
                + " in Kazi.");

    // commit without an attestation → 409
    assertThatThrownBy(
            () ->
                TakeOnTestDrain.commit(
                    commitService, schema, ORG_ID, ownerId, batch.batchId(), batch.hash()))
        .isInstanceOfSatisfying(
            TakeOnProblemException.class,
            e -> assertThat(e.getCode()).isEqualTo("TAKE_ON_ATTESTATION_REQUIRED"));
    // a custom role with MANAGE_TAKE_ON + CUSTOMER_MANAGEMENT but not MANAGE_COMPLIANCE → 403
    attest(
            mockMvc,
            clientsOnly(),
            batch.batchId(),
            batch.hash(),
            Map.of("cddRecordsLocation", "Old system archive"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
    // … and it is not offered ATTEST
    var asClientsOnly = json(detail(mockMvc, clientsOnly(), batch.batchId()));
    assertThat((List<String>) JsonPath.read(asClientsOnly, "$.allowedActions"))
        .doesNotContain("ATTEST");
    // the wrong hash → 409; defaults other than the mapping's → 400; another version → 400
    attest(
            mockMvc,
            owner(),
            batch.batchId(),
            "f".repeat(64),
            Map.of("cddRecordsLocation", "Old system archive"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("HASH_MISMATCH"))
        .andExpect(jsonPath("$.title").value("The batch changed since it was signed off"))
        .andExpect(jsonPath("$.detail").value("Re-validate and sign off again before committing."));
    attest(
            mockMvc,
            owner(),
            batch.batchId(),
            batch.hash(),
            Map.of("cddRecordsLocation", "Elsewhere"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Batch defaults changed"))
        .andExpect(jsonPath("$.detail").value(DEFAULTS_CHANGED));
    attest(mockMvc, owner(), batch.batchId(), batch.hash(), Map.of())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Batch defaults changed"))
        .andExpect(jsonPath("$.detail").value(DEFAULTS_CHANGED));

    var attested =
        json(
            attest(
                    mockMvc,
                    owner(),
                    batch.batchId(),
                    batch.hash(),
                    Map.of("cddRecordsLocation", "Old system archive"))
                .andExpect(status().isOk()));
    assertThat((List<String>) JsonPath.read(attested, "$.allowedActions"))
        .contains("COMMIT", "REVOKE_ATTESTATION")
        .doesNotContain("ATTEST");
    assertThat((String) JsonPath.read(attested, "$.signOffs[0].kind"))
        .isEqualTo("FICA_ATTESTATION");
    assertThat((String) JsonPath.read(attested, "$.signOffs[0].memberName"))
        .isEqualTo("Clients Owner");
    assertThat((String) JsonPath.read(attested, "$.signOffs[0].roleName")).isEqualTo("Owner");
    var stored =
        jdbc.queryForMap(
            "SELECT statement_text, member_name, role_name, batch_defaults::text AS defaults,"
                + " validation_hash FROM \"%s\".take_on_sign_offs WHERE batch_id = ?"
                    .formatted(schema),
            batch.batchId());
    assertThat(stored.get("statement_text")).isEqualTo(statement);
    assertThat((String) stored.get("defaults")).contains("Old system archive");
    assertThat(stored.get("validation_hash")).isEqualTo(batch.hash());
    // one live attestation per batch
    attest(
            mockMvc,
            owner(),
            batch.batchId(),
            batch.hash(),
            Map.of("cddRecordsLocation", "Old system archive"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("TAKE_ON_ALREADY_ATTESTED"))
        .andExpect(jsonPath("$.title").value("Batch already attested"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "This take-on batch has already been attested. Withdraw the attestation to"
                        + " attest again."));
    var audit = TakeOnTestApi.auditEvents(jdbc, schema, batch.batchId());
    var attestedEvent =
        audit.stream()
            .filter(a -> a.eventType().equals("takeon.batch.attested"))
            .findFirst()
            .orElseThrow();
    assertThat(attestedEvent.details())
        .containsOnlyKeys("batch_id", "sign_off_id", "statement_version", "row_count")
        .containsEntry("statement_version", "FICA-ATTEST-v1")
        .containsEntry("row_count", 1);

    // revoke before commit: ATTEST is offered again and the commit refused
    String signOffId = JsonPath.read(attested, "$.signOffs[0].id");
    var revoked =
        json(
            revokeSignOff(mockMvc, owner(), batch.batchId(), UUID.fromString(signOffId))
                .andExpect(status().isOk()));
    assertThat((List<?>) JsonPath.read(revoked, "$.signOffs")).isEmpty();
    assertThat((List<String>) JsonPath.read(revoked, "$.allowedActions")).contains("ATTEST");
    assertThat(
            TakeOnTestApi.auditEvents(jdbc, schema, batch.batchId()).stream()
                .filter(a -> a.eventType().equals("takeon.batch.sign_off_revoked"))
                .findFirst()
                .orElseThrow()
                .details())
        .containsEntry("kind", "FICA_ATTESTATION")
        .containsEntry("reason_code", "REVOKED");

    // attest again, then a re-validation that changes the hash revokes it (REVALIDATED)
    attestOk(batch);
    TakeOnTestApi.map(
            mockMvc,
            owner(),
            batch.batchId(),
            sourceId(batch.batchId()),
            mapping(Map.of("cddRecordsLocation", "New archive")))
        .andExpect(status().isOk());
    String newHash =
        JsonPath.read(
            json(
                TakeOnTestApi.validate(mockMvc, owner(), batch.batchId())
                    .andExpect(status().isOk())),
            "$.validationHash");
    assertThat(newHash).isNotEqualTo(batch.hash());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".take_on_sign_offs WHERE batch_id = ? AND revoked_at IS NULL"
                    .formatted(schema),
                Integer.class,
                batch.batchId()))
        .isZero();
    assertThatThrownBy(
            () ->
                TakeOnTestDrain.commit(
                    commitService, schema, ORG_ID, ownerId, batch.batchId(), newHash))
        .isInstanceOfSatisfying(
            TakeOnProblemException.class,
            e -> assertThat(e.getCode()).isEqualTo("TAKE_ON_ATTESTATION_REQUIRED"));
  }

  // 9
  @Test
  void portalAccess_isCreatedOnlyOnRequest_sendsNoEmail_andIsIdempotent() throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Portal One", "portal.one@example.test").ref("P-1"),
                row("Portal Two", "portal.two@example.test").ref("P-2")),
            mapping());
    // before the commit: not completed → 409
    portalContacts(mockMvc, owner(), batch.batchId())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("TAKE_ON_NOT_COMPLETED"))
        .andExpect(jsonPath("$.title").value("Take-on batch not completed"))
        .andExpect(
            jsonPath("$.detail")
                .value("Portal access can be created once the take-on has been committed."));
    attestAndCommit(batch);
    UUID one = resultEntity(batch.batchId(), "P-1");
    UUID two = resultEntity(batch.batchId(), "P-2");
    assertThat(inTenant(() -> portalContactRepository.findByCustomerId(one))).isEmpty();

    int mailBefore = GreenMailTestSupport.getInstance().getReceivedMessages().length;
    portalContacts(mockMvc, clientsOnly(), batch.batchId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.createdCount").value(2))
        .andExpect(jsonPath("$.skippedCount").value(0))
        .andExpect(jsonPath("$.batch.status").value("COMPLETED"));
    for (UUID client : List.of(one, two)) {
      var contacts = inTenant(() -> portalContactRepository.findByCustomerId(client));
      assertThat(contacts).hasSize(1);
      assertThat(contacts.getFirst().getRole()).isEqualTo(PortalContact.ContactRole.GENERAL);
    }
    assertThat(inTenant(() -> portalContactRepository.findByCustomerId(one)).getFirst().getEmail())
        .isEqualTo("portal.one@example.test");
    portalContacts(mockMvc, owner(), batch.batchId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.createdCount").value(0))
        .andExpect(jsonPath("$.skippedCount").value(2));
    assertThat(GreenMailTestSupport.getInstance().getReceivedMessages().length)
        .as("creating portal access sends no email")
        .isEqualTo(mailBefore);
    assertThat(
            TakeOnTestApi.auditEvents(jdbc, schema, batch.batchId()).stream()
                .filter(a -> a.eventType().equals("takeon.portal_contacts.created"))
                .map(TakeOnTestApi.AuditRow::details)
                .toList())
        .containsExactly(
            Map.of("batch_id", batch.batchId().toString(), "created_count", 2, "skipped_count", 0),
            Map.of("batch_id", batch.batchId().toString(), "created_count", 0, "skipped_count", 2));
  }

  // 10
  @Test
  void resultCsv_listsEveryRowWithItsStatusLabelAndRecord() throws Exception {
    TestEntityHelper.createCustomer(
        mockMvc, ownerJwt(), "Csv Existing", "csv.existing@example.test");
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Csv New", "csv.new@example.test").ref("C-1"),
                row("Csv Dup", "csv.existing@example.test").ref("=C-2"),
                row("Csv Bad", "").ref("C-3")),
            mapping());
    resultCsv(mockMvc, owner(), batch.batchId())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("TAKE_ON_NO_RESULT"))
        .andExpect(jsonPath("$.title").value("No result yet"))
        .andExpect(
            jsonPath("$.detail")
                .value("The result report is available once the take-on has been committed."));
    attestAndCommit(batch);
    var response =
        resultCsv(mockMvc, owner(), batch.batchId())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(response.getContentType()).startsWith("text/csv");
    assertThat(response.getHeader("Content-Disposition"))
        .contains("take-on-result-" + batch.batchId() + ".csv");
    String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    var lines = body.replace("﻿", "").lines().toList();
    assertThat(lines.getFirst()).isEqualTo("Row,Previous system ID,Status,Kazi record ID,Message");
    assertThat(lines.get(1)).startsWith("2,C-1,Created," + resultEntity(batch.batchId(), "C-1"));
    assertThat(lines.get(2)).startsWith("3,'=C-2,Duplicate — skipped,"); // formula defused
    assertThat(lines.get(3))
        .isEqualTo("4,C-3,Error,,An email address is required to create a client in Kazi");
    resultCsv(mockMvc, clientsOnly(), batch.batchId()).andExpect(status().isOk());
  }

  // 11
  @Test
  void anonymisingATakenOnClient_redactsItsTakeOnRows_andTheirSourceRef_butKeepsTheShell()
      throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Anon Client", "anon.client@example.test").ref("Z-1"),
                row("Anon Client Again", "ANON.CLIENT@example.test").ref("Z-2")),
            mapping());
    // validated before the first commit, so its row FAILS on the email clash when it commits
    var clash =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Anon Clash", "anon.client@example.test").ref("Z-4")),
            mapping());
    var later =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Anon Later", "anon.later@example.test").ref("Z-3")),
            mapping());
    attestAndCommit(batch);
    attestAndCommit(clash);
    assertThat(rowStatusByRef(clash.batchId(), "Z-4")).isEqualTo("FAILED");
    UUID client = resultEntity(batch.batchId(), "Z-1");
    mockMvc
        .perform(
            post("/api/clients/" + client + "/anonymize")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmationName\": \"Anon Client\", \"reason\": \"test\"}"))
        .andExpect(status().isOk());
    // the created row and the in-file repeat of its email are redacted — the previous system's
    // id too (free text: it may be an email or a name, CR-3); the shells (number, status) stay
    for (int rowNumber : List.of(2, 3)) {
      var r = rowByNumber(batch.batchId(), rowNumber);
      assertThat((String) r.get("raw")).as("row %d", rowNumber).isEqualTo("{\"redacted\": true}");
      assertThat((String) r.get("mapped"))
          .as("row %d", rowNumber)
          .isEqualTo("{\"redacted\": true}");
      assertThat(r.get("dedup_key")).as("row %d", rowNumber).isNull();
      assertThat(r.get("source_ref")).as("row %d", rowNumber).isNull();
      assertThat((String) r.get("errors")).as("row %d", rowNumber).doesNotContain("message");
    }
    assertThat(rowByNumber(batch.batchId(), 2).get("status")).isEqualTo("CREATED");
    assertThat(rowByNumber(batch.batchId(), 3).get("status")).isEqualTo("ERROR");
    // … and the result report carries no previous-system id for them
    String report =
        resultCsv(mockMvc, owner(), batch.batchId())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(report).doesNotContain("Z-1").doesNotContain("Z-2");
    assertThat(
            jdbc.queryForObject(
                "SELECT raw_values::text FROM \"%s\".take_on_rows WHERE batch_id = ?"
                    .formatted(schema),
                String.class,
                clash.batchId()))
        .as("the row that failed on the email clash is redacted too")
        .isEqualTo("{\"redacted\": true}");
    // another client's rows are untouched
    var untouched =
        jdbc.queryForObject(
            "SELECT raw_values::text FROM \"%s\".take_on_rows WHERE batch_id = ?".formatted(schema),
            String.class,
            later.batchId());
    assertThat(untouched).contains("anon.later@example.test");
  }

  // 12 (review A-M1, B-H2)
  @Test
  void anInFileRepeatOfAnExistingClient_isAnErrorNotADuplicate_andTheSummaryCountsTheNewClients()
      throws Exception {
    TestEntityHelper.createCustomer(
        mockMvc, ownerJwt(), "Summary Existing", "summary.existing@example.test");
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Summary New", "summary.new@example.test").rating("High").ref("S-A"),
                row("Summary Existing", "summary.existing@example.test").ref("S-B"),
                row("Summary Repeat", "SUMMARY.EXISTING@example.test").ref("S-C")),
            mapping());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.valid")).isEqualTo(2);
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.duplicate"))
        .as("only the valid match is a skipped duplicate")
        .isEqualTo(1);
    var byRef = rowsByRef(batch.batchId());
    assertError(byRef.get("S-C"), "DUPLICATE_IN_FILE", "email", "Duplicate of row 3 in this file");
    assertThat(byRef.get("S-C").get("duplicateOfId"))
        .as("the error row keeps its link to the client (anonymisation)")
        .isEqualTo(byRef.get("S-B").get("duplicateOfId"));
    // the FICA summary, computed at validation over the one row that will become a client
    assertThat((Integer) JsonPath.read(batch.json(), "$.summary.clients")).isEqualTo(1);
    assertThat((Map<String, Object>) JsonPath.read(batch.json(), "$.summary.fica"))
        .containsEntry("ficaClientType", Map.of("NATURAL_PERSON", 1))
        .containsEntry("riskRating", Map.of("HIGH", 1))
        .containsEntry("beneficialOwnershipStatus", Map.of("NOT_APPLICABLE", 1))
        .containsEntry("screeningStatus", Map.of("SCREENED_CLEAR", 1))
        .containsEntry("sourceOfFundsStatus", Map.of("NOT_REQUIRED", 1));
    // the attestation's row_count agrees: valid − duplicate = 1
    attestOk(batch);
    var attested =
        TakeOnTestApi.auditEvents(jdbc, schema, batch.batchId()).stream()
            .filter(a -> a.eventType().equals("takeon.batch.attested"))
            .findFirst()
            .orElseThrow();
    assertThat(attested.details()).containsEntry("row_count", 1);
  }

  // OBS-655A-1: the row list narrows to exactly the rows the Duplicates card counts
  @Test
  void theDuplicatesFilter_listsExactlyTheValidRowsTheDuplicatesCardCounts() throws Exception {
    TestEntityHelper.createCustomer(
        mockMvc, ownerJwt(), "Filter Existing", "filter.existing@example.test");
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Filter New", "filter.new@example.test").ref("DF-A"),
                row("Filter Existing", "FILTER.existing@example.test").ref("DF-B"),
                row("Filter Repeat", "filter.existing@example.test").ref("DF-C")),
            mapping());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.duplicate")).isEqualTo(1);

    var duplicates =
        rowContent(
            json(rows(mockMvc, owner(), batch.batchId(), "status=VALIDATED&duplicatesOnly=true")));
    assertThat(duplicates).extracting(r -> r.get("sourceRef")).containsExactly("DF-B");
    assertThat(duplicates.getFirst().get("duplicateOfId")).isNotNull();
    // the flag alone also lists the error row that keeps its link to the client (D-102) ...
    assertThat(rowContent(json(rows(mockMvc, owner(), batch.batchId(), "duplicatesOnly=true"))))
        .extracting(r -> r.get("sourceRef"))
        .containsExactly("DF-B", "DF-C");
    // ... and without it the status filter is unchanged
    assertThat(rowContent(json(rows(mockMvc, owner(), batch.batchId(), "status=VALIDATED"))))
        .extracting(r -> r.get("sourceRef"))
        .containsExactly("DF-A", "DF-B");
  }

  // OBS-655A-2: a refused risk rating or source of funds is not replaced by the blank default
  @Test
  void aRefusedRiskRatingOrSourceOfFunds_isNotReplacedByTheBlankDefault() throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Rating Refused", "rating.refused@example.test")
                    .rating("VERY_HIGH")
                    .ref("RR-1"),
                row("Rating Blank Error", "").rating("").ref("RR-2"),
                row("Rating Blank Valid", "rating.blank@example.test").rating("").ref("RR-3")),
            mapping());
    var byRef = rowsByRef(batch.batchId());
    assertError(
        byRef.get("RR-1"),
        "ENUM_INVALID",
        "riskRating",
        "VERY_HIGH is not one of: Low, Medium, High, Unrated");
    assertThat(values(byRef.get("RR-1"))).doesNotContainKey("riskRating");
    // a blank rating is still "Not risk-rated", on an error row as on a valid one
    assertThat(values(byRef.get("RR-2"))).containsEntry("riskRating", "UNRATED");
    assertThat(byRef.get("RR-3").get("status")).isEqualTo("VALIDATED");
    assertThat(values(byRef.get("RR-3"))).containsEntry("riskRating", "UNRATED");
    // the FICA summary counts the one valid row only
    assertThat((Integer) JsonPath.read(batch.json(), "$.summary.clients")).isEqualTo(1);
    assertThat((Map<String, Object>) JsonPath.read(batch.json(), "$.summary.fica"))
        .containsEntry("riskRating", Map.of("UNRATED", 1));

    // The same for source of funds: the "Rating" column mapped to it instead.
    @SuppressWarnings("unchecked")
    var targets = new HashMap<>((Map<String, String>) mapping().get("targets"));
    targets.remove("riskRating");
    targets.put("sourceOfFundsStatus", "Rating");
    var sofMapping = new HashMap<>(mapping());
    sofMapping.put("targets", targets);
    var sof =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Funds Refused", "funds.refused@example.test").rating("Unknown").ref("SF-1"),
                row("Funds Blank", "funds.blank@example.test").rating("").ref("SF-2")),
            sofMapping);
    var sofByRef = rowsByRef(sof.batchId());
    assertError(
        sofByRef.get("SF-1"),
        "ENUM_INVALID",
        "sourceOfFundsStatus",
        "Unknown is not one of: Done, Not required, Outstanding");
    assertThat(values(sofByRef.get("SF-1"))).doesNotContainKey("sourceOfFundsStatus");
    assertThat(sofByRef.get("SF-2").get("status")).isEqualTo("VALIDATED");
    assertThat(values(sofByRef.get("SF-2"))).containsEntry("sourceOfFundsStatus", "NOT_REQUIRED");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> values(Map<String, Object> row) {
    return (Map<String, Object>) row.get("values");
  }

  // 13 (review A-H1)
  @Test
  void aDataDeletionRequest_dropsTheParkedColumns_andRedactsTheClientsTakeOnRows()
      throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Deletion Client", "deletion.client@example.test")
                    .branch("Sandton")
                    .ref("X-1")),
            mapping());
    attestAndCommit(batch);
    UUID client = resultEntity(batch.batchId(), "X-1");
    assertThat(customFieldsOf(client)).contains("takeon_parked").contains("Sandton");

    String requestId =
        JsonPath.read(
            json(
                mockMvc
                    .perform(
                        post("/api/data-requests")
                            .with(owner())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                ("{\"clientId\":\"%s\",\"requestType\":\"DELETION\","
                                        + "\"description\":\"The client asked\"}")
                                    .formatted(client)))
                    .andExpect(status().isCreated())),
            "$.id");
    mockMvc
        .perform(
            put("/api/data-requests/" + requestId + "/status")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"START_PROCESSING\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/data-requests/" + requestId + "/execute-deletion")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmClientName\":\"Deletion Client\"}"))
        .andExpect(status().isOk());

    assertThat(customFieldsOf(client)).doesNotContain("takeon_parked").doesNotContain("Sandton");
    var r = rowByNumber(batch.batchId(), 2);
    assertThat((String) r.get("raw")).isEqualTo("{\"redacted\": true}");
    assertThat((String) r.get("mapped")).isEqualTo("{\"redacted\": true}");
    assertThat(r.get("source_ref")).isNull();
    assertThat(r.get("dedup_key")).isNull();
  }

  // 14 (review CR-2)
  @Test
  void anonymisingAClientWhoseEmailChanged_redactsTheRowsKeyedByItsEarlierEmail() throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Moved Client", "moved.before@example.test").ref("M-1")),
            mapping());
    // an unlinked row keyed by the same email: an error in another batch, validated before the
    // commit, so it matched no client
    var stray =
        validatedClients(
            mockMvc,
            owner(),
            csv(
                row("Moved Client Elsewhere", "moved.before@example.test")
                    .address("1 Road", "Durban", "ZZ")
                    .ref("M-2")),
            mapping());
    attestAndCommit(batch);
    UUID client = resultEntity(batch.batchId(), "M-1");
    mockMvc
        .perform(
            put("/api/clients/" + client)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Moved Client\",\"email\":\"moved.after@example.test\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/clients/" + client + "/anonymize")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmationName\": \"Moved Client\", \"reason\": \"test\"}"))
        .andExpect(status().isOk());
    var r = rowByNumber(stray.batchId(), 2);
    assertThat((String) r.get("raw"))
        .as("the row keyed by the client's earlier email is redacted")
        .isEqualTo("{\"redacted\": true}");
    assertThat(r.get("dedup_key")).isNull();
  }

  // 15 (review CR-6)
  @Test
  void theCommitRecheck_matchesNameAndTaxNumberCaseInsensitively_asTheDryRunDoes()
      throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Case Tax Holdings", "case.tax@example.test").tax("AB123").ref("K-1")),
            mapping());
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.duplicate")).isZero();
    // a client created in the UI after the dry run, its tax number in another case
    UUID uiClient =
        UUID.fromString(
            TestEntityHelper.createCustomer(
                mockMvc, ownerJwt(), "Case Tax Holdings", "case.tax.ui@example.test"));
    jdbc.update(
        "UPDATE \"%s\".customers SET tax_number = 'ab123' WHERE id = ?".formatted(schema),
        uiClient);
    attestAndCommit(batch);
    assertThat(rowStatusByRef(batch.batchId(), "K-1")).isEqualTo("SKIPPED_DUPLICATE");
    assertThat(resultEntity(batch.batchId(), "K-1")).isEqualTo(uiClient);
  }

  // 16 (review CR-7)
  @Test
  void aBlockedBatch_isNeitherAttestedNorCommitted() throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Blocked Client", "blocked.client@example.test").ref("B-1")),
            mapping());
    setBlockCount(batch.batchId(), 1);
    assertThat(
            (List<String>)
                JsonPath.read(json(detail(mockMvc, owner(), batch.batchId())), "$.allowedActions"))
        .doesNotContain("ATTEST", "COMMIT");
    attest(mockMvc, owner(), batch.batchId(), batch.hash(), mappingDefaults(batch.batchId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("TAKE_ON_BLOCKED"))
        .andExpect(jsonPath("$.title").value("Take-on batch blocked"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Rows in this take-on batch block it. Correct them in the previous system,"
                        + " upload the corrected file and validate it again."));
    // attested while unblocked, then blocked: the claim refuses and the button is gone
    setBlockCount(batch.batchId(), 0);
    attestOk(batch);
    setBlockCount(batch.batchId(), 1);
    assertThat(
            (List<String>)
                JsonPath.read(json(detail(mockMvc, owner(), batch.batchId())), "$.allowedActions"))
        .doesNotContain("COMMIT")
        .contains("REVOKE_ATTESTATION");
    assertThatThrownBy(
            () ->
                TakeOnTestDrain.commit(
                    commitService, schema, ORG_ID, ownerId, batch.batchId(), batch.hash()))
        .isInstanceOfSatisfying(
            TakeOnProblemException.class,
            e -> assertThat(e.getCode()).isEqualTo("TAKE_ON_BLOCKED"));
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM \"%s\".take_on_batches WHERE id = ?".formatted(schema),
                String.class,
                batch.batchId()))
        .isEqualTo("VALIDATED");
    setBlockCount(batch.batchId(), 0);
  }

  // 17 (review CR-9)
  @Test
  void portalAccess_waitsForAClientLockedByAnAnonymisation_andThenSkipsTheAnonymisedClient()
      throws Exception {
    var batch =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Locked Client", "locked.client@example.test").ref("L-1")),
            mapping());
    attestAndCommit(batch);
    UUID client = resultEntity(batch.batchId(), "L-1");
    try (Connection anonymisation = dataSource.getConnection();
        ExecutorService pool = Executors.newSingleThreadExecutor()) {
      anonymisation.setAutoCommit(false);
      try (var lock =
          anonymisation.prepareStatement(
              "SELECT id FROM \"%s\".customers WHERE id = ? FOR UPDATE".formatted(schema))) {
        lock.setObject(1, client);
        lock.executeQuery().close();
      }
      Future<String> portal =
          pool.submit(() -> json(portalContacts(mockMvc, owner(), batch.batchId())));
      Thread.sleep(1_000);
      assertThat(portal.isDone())
          .as("portal access waits for the client an anonymisation holds")
          .isFalse();
      try (var anonymise =
          anonymisation.prepareStatement(
              ("UPDATE \"%s\".customers SET lifecycle_status = 'ANONYMIZED',"
                      + " name = 'Anonymized Client', email = 'anon-' || id || '@anonymized.invalid'"
                      + " WHERE id = ?")
                  .formatted(schema))) {
        anonymise.setObject(1, client);
        anonymise.executeUpdate();
      }
      anonymisation.commit();
      String result = portal.get(30, TimeUnit.SECONDS);
      assertThat((Integer) JsonPath.read(result, "$.createdCount")).isZero();
      assertThat((Integer) JsonPath.read(result, "$.skippedCount")).isEqualTo(1);
    }
    assertThat(inTenant(() -> portalContactRepository.findByCustomerId(client))).isEmpty();
  }

  // 18 (delta review M-1)
  @Test
  void anonymisingAClientWhoseEarlierEmailAnotherClientNowHolds_leavesThatClientsRowsAlone()
      throws Exception {
    var first =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Earlier Holder", "first.address@example.test").ref("H-1")),
            mapping());
    attestAndCommit(first);
    UUID earlier = resultEntity(first.batchId(), "H-1");
    // the earlier holder moves to the shared address, then on again — freeing it
    changeEmail(earlier, "Earlier Holder", "shared.address@example.test");
    changeEmail(earlier, "Earlier Holder", "own.address@example.test");
    // a second client is then taken on with the freed address …
    var second =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Current Holder", "shared.address@example.test").ref("H-2")),
            mapping());
    attestAndCommit(second);
    assertThat(rowStatusByRef(second.batchId(), "H-2"))
        .as("H-2: %s", failureReason(second.batchId(), "H-2"))
        .isEqualTo("CREATED");
    UUID current = resultEntity(second.batchId(), "H-2");
    assertThat(current).isNotEqualTo(earlier);
    // … and matched again by a later row (a duplicate of it, by the same address)
    var repeat =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Current Holder", "shared.address@example.test").ref("H-3")),
            mapping());
    attestAndCommit(repeat);
    assertThat(rowStatusByRef(repeat.batchId(), "H-3")).isEqualTo("SKIPPED_DUPLICATE");
    assertThat(resultEntity(repeat.batchId(), "H-3")).isEqualTo(current);
    mockMvc
        .perform(
            post("/api/clients/" + earlier + "/anonymize")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmationName\": \"Earlier Holder\", \"reason\": \"test\"}"))
        .andExpect(status().isOk());
    // the anonymised client's own row is redacted …
    var own = rowByNumber(first.batchId(), 2);
    assertThat((String) own.get("raw")).isEqualTo("{\"redacted\": true}");
    assertThat((String) own.get("mapped")).isEqualTo("{\"redacted\": true}");
    assertThat(own.get("source_ref")).isNull();
    assertThat(own.get("dedup_key")).isNull();
    // … the rows of the live client who now holds its earlier address keep their values
    for (var batch : List.of(second, repeat)) {
      var kept = rowByNumber(batch.batchId(), 2);
      assertThat((String) kept.get("raw"))
          .as("the other client's take-on row keeps its values")
          .contains("shared.address@example.test");
      assertThat((String) kept.get("mapped")).contains("shared.address@example.test");
      assertThat(kept.get("dedup_key")).isEqualTo("email:shared.address@example.test");
    }
    assertThat(rowByNumber(second.batchId(), 2).get("source_ref")).isEqualTo("H-2");
    assertThat(rowByNumber(repeat.batchId(), 2).get("source_ref")).isEqualTo("H-3");
    assertThat(inTenant(() -> takeOnRows.clientBySourceRef("H-2"))).contains(current);
    assertThat(inTenant(() -> takeOnRows.clientBySourceRef("H-3"))).contains(current);
  }

  // 19 (F-655A-3, founder ruling FR-2): the email stays reserved; the row says why
  @Test
  void anEmailAnEarlierClientWasTakenOnWith_failsTheRowWithTheSpecificReason() throws Exception {
    var first =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Reserved Holder", "reserved.address@example.test").ref("R-1")),
            mapping());
    attestAndCommit(first);
    UUID earlier = resultEntity(first.batchId(), "R-1");
    // the client moves off the address: no client in Kazi holds it any more …
    changeEmail(earlier, "Reserved Holder", "reserved.moved@example.test");
    // … but a later take-on of another client with it is still refused (RD-6, kept by FR-2)
    var later =
        validatedClients(
            mockMvc,
            owner(),
            csv(row("Second Reserved", "reserved.address@example.test").ref("R-2")),
            mapping());
    attestAndCommit(later);
    assertThat(rowStatusByRef(later.batchId(), "R-2")).isEqualTo("FAILED");
    assertThat(failureReason(later.batchId(), "R-2"))
        .isEqualTo(
            "Another client was taken on with this email address in an earlier take-on. The"
                + " address stays reserved for take-on, even if that client's email has since"
                + " changed in Kazi. Take this client on with a different email address, or add"
                + " them in Kazi directly.")
        .doesNotContain("Reserved Holder")
        .doesNotContain("reserved.address@example.test");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".customers WHERE name = 'Second Reserved'"
                    .formatted(schema),
                Integer.class))
        .as("the rolled-back row created no client")
        .isZero();
  }

  private void changeEmail(UUID clientId, String name, String email) throws Exception {
    mockMvc
        .perform(
            put("/api/clients/" + clientId)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"email\":\"%s\"}".formatted(name, email)))
        .andExpect(status().isOk());
  }

  // --- helpers ---

  private Map<String, Object> rowByNumber(UUID batchId, int rowNumber) {
    return jdbc.queryForMap(
        ("SELECT status, raw_values::text AS raw, mapped_values::text AS mapped, dedup_key,"
                + " source_ref, errors::text AS errors FROM \"%s\".take_on_rows"
                + " WHERE batch_id = ? AND row_number = ?")
            .formatted(schema),
        batchId,
        rowNumber);
  }

  private String customFieldsOf(UUID clientId) {
    String fields =
        jdbc.queryForObject(
            "SELECT custom_fields::text FROM \"%s\".customers WHERE id = ?".formatted(schema),
            String.class,
            clientId);
    return fields == null ? "" : fields;
  }

  private void setBlockCount(UUID batchId, int blocks) {
    jdbc.update(
        "UPDATE \"%s\".take_on_batches SET block_count = ? WHERE id = ?".formatted(schema),
        blocks,
        batchId);
  }

  private void attestOk(Validated batch) throws Exception {
    attest(mockMvc, owner(), batch.batchId(), batch.hash(), mappingDefaults(batch.batchId()))
        .andExpect(status().isOk());
  }

  private void attestAndCommit(Validated batch) throws Exception {
    attestOk(batch);
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, batch.batchId(), batch.hash());
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> mappingDefaults(UUID batchId) throws Exception {
    Map<String, String> defaults =
        JsonPath.read(json(detail(mockMvc, owner(), batchId)), "$.sources[0].mapping.defaults");
    return defaults == null ? Map.of() : defaults;
  }

  private UUID sourceId(UUID batchId) throws Exception {
    return UUID.fromString(
        JsonPath.read(json(detail(mockMvc, owner(), batchId)), "$.sources[0].id"));
  }

  private Map<String, Map<String, Object>> rowsByRef(UUID batchId) throws Exception {
    return rowContent(json(rows(mockMvc, owner(), batchId, "size=200"))).stream()
        .collect(Collectors.toMap(r -> (String) r.get("sourceRef"), r -> r));
  }

  private List<Map<String, Object>> createdRows(UUID batchId) {
    return jdbc.queryForList(
        "SELECT source_ref, result_entity_id::text AS result_entity_id, result_refs::text AS"
            + " result_refs FROM \"%s\".take_on_rows WHERE batch_id = ? AND status = 'CREATED'"
                .formatted(schema),
        batchId);
  }

  private UUID resultEntity(UUID batchId, String ref) {
    return jdbc.queryForObject(
        "SELECT result_entity_id FROM \"%s\".take_on_rows WHERE batch_id = ? AND source_ref = ?"
            .formatted(schema),
        UUID.class,
        batchId,
        ref);
  }

  private UUID clientIdByRef(UUID batchId, String ref) {
    return resultEntity(batchId, ref);
  }

  private String rowStatusByRef(UUID batchId, String ref) {
    return jdbc.queryForObject(
        "SELECT status FROM \"%s\".take_on_rows WHERE batch_id = ? AND source_ref = ?"
            .formatted(schema),
        String.class,
        batchId,
        ref);
  }

  private String failureReason(UUID batchId, String ref) {
    return jdbc.queryForObject(
        "SELECT failure_reason FROM \"%s\".take_on_rows WHERE batch_id = ? AND source_ref = ?"
            .formatted(schema),
        String.class,
        batchId,
        ref);
  }

  private Map<String, Object> attestationOf(UUID clientId) {
    return jdbc.queryForMap(
        "SELECT * FROM \"%s\".client_fica_attestations WHERE customer_id = ?".formatted(schema),
        clientId);
  }

  private long count(String sql, Object... args) {
    Long n = jdbc.queryForObject(sql.formatted("\"" + schema + "\""), Long.class, args);
    return n == null ? 0 : n;
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

  private static String json(org.springframework.test.web.servlet.ResultActions result)
      throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private <T> T inTenant(Callable<T> action) {
    return RequestScopes.callForTenant(schema, ORG_ID, action);
  }

  private String createRole(String name, String... capabilities) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"%s\",\"description\":\"Test role\",\"capabilities\":%s}"
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

  private RequestPostProcessor clientsOnly() {
    return TestJwtFactory.customRoleJwt(ORG_ID, CLIENTS_ONLY);
  }

  private JwtRequestPostProcessor ownerJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
