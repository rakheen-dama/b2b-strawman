package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.attest;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.portalContacts;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.resultCsv;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.revokeSignOff;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.validatedClients;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 93 655A (§17.6.4 (a) and (b), R-5; extended by 657B and 658A): the full clients take-on
 * runs on rows whose name, email, ID number, tax number, phone, records location, previous-system
 * id and a parked amount are distinctive canaries; then every {@code audit_events} row written
 * since the test started whose type starts {@code takeon.}, is {@code customer.fica_attested}, or
 * is {@code customer.created} with {@code source = TAKE_ON} must have (a) only UUID, enum-name,
 * number, boolean or SAFE_TOKEN values and (b) no canary in any case or with separators removed.
 * The platform's {@code actor_name} (the staff member, D-32) is split off first. 657B added the
 * trust flow; 658A the debtor opening balances flow, whose {@code invoice.created} ({@code source =
 * TAKE_ON}) and {@code invoice.brought_forward_issued} rows must carry no client reference,
 * fee-note number, description or amount either.
 *
 * <p>The job payload of {@code take_on_drain} is {@code null} by construction ({@code
 * TakeOnCommitFacade}, {@code TakeOnDrainSweep}); with the test profile's queue off there is no
 * queue row to read, so that half is asserted by {@code TakeOnCommitFacadeTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnAuditNoPiiIntegrationTest {

  private static final String ORG_ID = "org_takeon_no_pii_test";
  private static final String OWNER = "user_tnp_owner";

  private static final List<String> CANARIES =
      List.of(
          "Zanele Pii-Canary",
          "pii.canary@example.test",
          "8001015009087",
          "9123456789",
          "+27 82 555 0199",
          "Canary Archive Box 77",
          "PIICANARY-1",
          "R 12 345,67",
          "Canaryville");

  private static final Pattern UUID_PATTERN =
      Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
  private static final Pattern ENUM_NAME = Pattern.compile("^[A-Z][A-Z0-9_]*$");
  private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;

  private String schema;
  private UUID ownerId;

  @BeforeAll
  void setup() throws Exception {
    schema = provisioningService.provisionTenant(ORG_ID, "No PII Firm", "legal-za").schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "tnp_owner@test.com", "Privacy Owner", "owner"));
  }

  @Test
  void theTakeOnAuditTrailCarriesNoPersonalInformation() throws Exception {
    Instant started = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();

    String file =
        "Client name,Email,Phone,Client type,CDD completed date,Beneficial ownership,Screening,"
            + "Screening date,Records,Rating,Tax number,ID number,Address,City,Country,Previous ID,"
            + "Amount\n"
            + "Zanele Pii-Canary,pii.canary@example.test,+27 82 555 0199,Individual,01/02/2024,"
            + "Done,Screened clear,01/02/2024,Canary Archive Box 77,High,9123456789,"
            + "8001015009087,1 Canary Lane,Canaryville,ZA,PIICANARY-1,\"R 12 345,67\"\n";
    var targets = new HashMap<String, String>();
    targets.put("name", "Client name");
    targets.put("email", "Email");
    targets.put("phone", "Phone");
    targets.put("ficaClientType", "Client type");
    targets.put("cddCompletedDate", "CDD completed date");
    targets.put("beneficialOwnershipStatus", "Beneficial ownership");
    targets.put("screeningStatus", "Screening");
    targets.put("screeningDate", "Screening date");
    targets.put("cddRecordsLocation", "Records");
    targets.put("riskRating", "Rating");
    targets.put("taxNumber", "Tax number");
    targets.put("idNumber", "ID number");
    targets.put("addressLine1", "Address");
    targets.put("city", "City");
    targets.put("country", "Country");
    targets.put("sourceRef", "Previous ID");
    var mapping = new HashMap<String, Object>();
    mapping.put("targets", targets);
    mapping.put("parked", List.of("Amount"));

    // An attestation withdrawn and given again (sign_off_revoked), then the commit, portal access
    // and the result report — every take-on audit event 653A and 655A write.
    var batch = validatedClients(mockMvc, owner(), file, mapping);
    var attested =
        attest(mockMvc, owner(), batch.batchId(), batch.hash(), Map.of())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String signOffId = JsonPath.read(attested, "$.signOffs[0].id");
    revokeSignOff(mockMvc, owner(), batch.batchId(), UUID.fromString(signOffId))
        .andExpect(status().isOk());
    attest(mockMvc, owner(), batch.batchId(), batch.hash(), Map.of()).andExpect(status().isOk());
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, batch.batchId(), batch.hash());
    portalContacts(mockMvc, owner(), batch.batchId()).andExpect(status().isOk());
    resultCsv(mockMvc, owner(), batch.batchId()).andExpect(status().isOk());
    TakeOnTestApi.cancel(
            mockMvc, owner(), validatedClients(mockMvc, owner(), file, mapping).batchId())
        .andExpect(status().isOk());

    var rows =
        jdbc.queryForList(
            ("SELECT event_type, details::text AS details FROM \"%s\".audit_events"
                    + " WHERE occurred_at >= ? AND (event_type LIKE 'takeon.%%'"
                    + " OR event_type = 'customer.fica_attested'"
                    + " OR (event_type = 'customer.created' AND details->>'source' = 'TAKE_ON'))")
                .formatted(schema),
            Timestamp.from(started));
    assertThat(rows)
        .extracting(r -> (String) r.get("event_type"))
        .contains(
            "takeon.batch.created",
            "takeon.source.uploaded",
            "takeon.source.mapped",
            "takeon.batch.validated",
            "takeon.batch.attested",
            "takeon.batch.sign_off_revoked",
            "takeon.batch.commit_requested",
            "customer.created",
            "customer.fica_attested",
            "takeon.batch.committed",
            "takeon.portal_contacts.created",
            "takeon.batch.cancelled");

    for (var row : rows) {
      @SuppressWarnings("unchecked")
      Map<String, Object> details = JSON.readValue((String) row.get("details"), Map.class);
      details.remove("actor_name"); // D-32: the platform's staff-name stamp, not take-on data
      for (var entry : details.entrySet()) {
        String where = row.get("event_type") + "." + entry.getKey();
        Object value = entry.getValue();
        assertThat(isSafe(value))
            .as("%s = %s is not an id, enum, number or token", where, value)
            .isTrue();
        String normalised = normalise(String.valueOf(value));
        for (String canary : CANARIES) {
          assertThat(normalised)
              .as("%s carries canary %s", where, canary)
              .doesNotContain(normalise(canary));
        }
      }
    }
  }

  /**
   * 657C: the trust flow — upload, dry run, sign-off, withdrawal, sign-off again, commit and the
   * take-on report — on a client whose previous-system id and balance are canaries. Every {@code
   * takeon.*} row it writes (now {@code signed_off}, {@code report.generated} and {@code committed}
   * for {@code TRUST_POSITION}) carries ids, enums, numbers, booleans and tokens only.
   */
  @Test
  void theTrustTakeOnAuditTrailCarriesNoPersonalInformationOrAmount() throws Exception {
    Instant started = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
    var world =
        new TakeOnTrustWorld(
            mockMvc, jdbc, commitService, drain, ORG_ID, OWNER, schema, ownerId, "tnp");
    world.takeOnClientsAndMatters(List.of("A", "B", "C", "TCANARY"));
    var acc = world.freshAccounts();
    var files =
        TakeOnTrustFiles.withRows(
            world.files(acc),
            TakeOnTrustFiles.CLIENT_LEDGER,
            List.of(
                "A,A1,300000.00,",
                "B,B1,150000.00,",
                "C,C1,49500.00,",
                "TCANARY,TCANARY1,12345.67,TCANARY-PREV"));
    var position = TakeOnTrustFiles.section17313Position(TakeOnTrustWorld.C);
    position =
        TakeOnTrustFiles.withFigure(position, "bankBalance", new java.math.BigDecimal("430845.67"));
    position =
        TakeOnTrustFiles.withFigure(
            position, "cashbookBalance", new java.math.BigDecimal("415845.67"));
    var batch = world.validated(acc.g(), files, position);
    assertThat((Integer) JsonPath.read(batch.json(), "$.counts.blocks")).isZero();
    world.signOff(batch);
    TakeOnTestApi.withdrawSignOff(mockMvc, owner(), batch.batchId()).andExpect(status().isOk());
    world.signOff(batch);
    world.claim(batch);
    world.drain();
    assertThat((String) JsonPath.read(world.batchJson(batch.batchId()), "$.status"))
        .isEqualTo("COMPLETED");
    // 657D: both report files downloaded (takeon.report.downloaded — report id and format)
    UUID reportId = UUID.fromString(JsonPath.read(world.batchJson(batch.batchId()), "$.reportId"));
    TakeOnTestApi.reportFile(mockMvc, owner(), reportId, "pdf").andExpect(status().isOk());
    TakeOnTestApi.reportFile(mockMvc, owner(), reportId, "csv").andExpect(status().isOk());

    var rows =
        jdbc.queryForList(
            ("SELECT event_type, details::text AS details FROM \"%s\".audit_events"
                    + " WHERE occurred_at >= ? AND event_type LIKE 'takeon.%%'")
                .formatted(schema),
            Timestamp.from(started));
    assertThat(rows)
        .extracting(r -> (String) r.get("event_type"))
        .contains(
            "takeon.batch.validated",
            "takeon.batch.signed_off",
            "takeon.batch.sign_off_revoked",
            "takeon.batch.commit_requested",
            "takeon.report.generated",
            "takeon.report.downloaded",
            "takeon.batch.committed");
    var canaries = List.of("TCANARY", "TCANARY-PREV", "12345.67", "R 12 345,67", "12 345,67");
    for (var row : rows) {
      @SuppressWarnings("unchecked")
      Map<String, Object> details = JSON.readValue((String) row.get("details"), Map.class);
      details.remove("actor_name"); // D-32: the platform's staff-name stamp, not take-on data
      for (var entry : details.entrySet()) {
        String where = row.get("event_type") + "." + entry.getKey();
        Object value = entry.getValue();
        assertThat(isSafe(value))
            .as("%s = %s is not an id, enum, number or token", where, value)
            .isTrue();
        String normalised = normalise(String.valueOf(value));
        for (String canary : canaries) {
          assertThat(normalised)
              .as("%s carries canary %s", where, canary)
              .doesNotContain(normalise(canary));
        }
      }
    }
  }

  /**
   * 658A: a clients take-on with canary name, email and previous-system id, then a debtors take-on
   * whose client references, fee-note numbers, amounts and description are canaries. Every {@code
   * takeon.*} row, the take-on's {@code invoice.created} and every {@code
   * invoice.brought_forward_issued} carry ids, enums, numbers, booleans and tokens only.
   */
  @Test
  void theDebtorTakeOnAuditTrailCarriesNoPersonalInformationOrAmount() throws Exception {
    Instant started = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
    var clients =
        validatedClients(
            mockMvc,
            owner(),
            TakeOnClientsFiles.csv(
                TakeOnClientsFiles.row("Dineo Pii-Canary", "debtor.canary@example.test")
                    .ref("PIIDEBTOR-1")),
            TakeOnClientsFiles.mapping());
    attest(mockMvc, owner(), clients.batchId(), clients.hash(), Map.of())
        .andExpect(status().isOk());
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, clients.batchId(), clients.hash());

    var debtors =
        TakeOnTestApi.validatedDebtors(
            mockMvc,
            owner(),
            TakeOnDebtorsFiles.csv(
                TakeOnDebtorsFiles.row("PIIDEBTOR-1", "PIIFN-7788")
                    .amount("12345.67")
                    .description("Canary fee note")
                    .ref("PIIFNREF-1"),
                TakeOnDebtorsFiles.row("debtor.canary@example.test", "PIIFN-7789")
                    .amount("9876.54")
                    .ref("PIIFNREF-2")),
            TakeOnDebtorsFiles.mapping(),
            "2026-10-31");
    assertThat((Integer) JsonPath.read(debtors.json(), "$.counts.valid")).isEqualTo(2);
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, ownerId, debtors.batchId(), debtors.hash());
    resultCsv(mockMvc, owner(), debtors.batchId()).andExpect(status().isOk());

    // The issued fee notes, by the debtors batch; their audit rows are selected by id, so an
    // invoice.created without source=TAKE_ON cannot hide from the checks below.
    List<UUID> issued =
        jdbc.queryForList(
            "SELECT id FROM \"%s\".invoices WHERE take_on_batch_id = ?".formatted(schema),
            UUID.class,
            debtors.batchId());
    assertThat(issued).hasSize(2);
    var invoiceRows =
        jdbc.queryForList(
            ("SELECT event_type, entity_id, details::text AS details FROM \"%s\".audit_events"
                    + " WHERE entity_id IN (?, ?) AND event_type IN"
                    + " ('invoice.created', 'invoice.brought_forward_issued')")
                .formatted(schema),
            issued.get(0),
            issued.get(1));
    for (String type : List.of("invoice.created", "invoice.brought_forward_issued")) {
      assertThat(invoiceRows)
          .filteredOn(r -> type.equals(r.get("event_type")))
          .extracting(r -> (UUID) r.get("entity_id"))
          .as("exactly one %s per issued fee note", type)
          .containsExactlyInAnyOrderElementsOf(issued);
    }
    for (var row : invoiceRows) {
      if ("invoice.created".equals(row.get("event_type"))) {
        @SuppressWarnings("unchecked")
        Map<String, Object> details = JSON.readValue((String) row.get("details"), Map.class);
        assertThat(details)
            .as("invoice.created %s", row.get("entity_id"))
            .containsEntry("source", "TAKE_ON");
      }
    }
    var rows = new ArrayList<Map<String, Object>>(invoiceRows);
    rows.addAll(
        jdbc.queryForList(
            ("SELECT event_type, entity_id, details::text AS details FROM \"%s\".audit_events"
                    + " WHERE occurred_at >= ? AND event_type LIKE 'takeon.%%'")
                .formatted(schema),
            Timestamp.from(started)));
    assertThat(rows)
        .extracting(r -> (String) r.get("event_type"))
        .contains(
            "takeon.batch.created",
            "takeon.batch.validated",
            "takeon.batch.commit_requested",
            "takeon.batch.committed");
    var canaries =
        List.of(
            "Dineo Pii-Canary",
            "debtor.canary@example.test",
            "PIIDEBTOR-1",
            "PIIFN-7788",
            "PIIFN-7789",
            "PIIFNREF-1",
            "PIIFNREF-2",
            "12345.67",
            "9876.54",
            "Canary fee note");
    for (var row : rows) {
      @SuppressWarnings("unchecked")
      Map<String, Object> details = JSON.readValue((String) row.get("details"), Map.class);
      details.remove("actor_name"); // D-32: the platform's staff-name stamp, not take-on data
      for (var entry : details.entrySet()) {
        String where = row.get("event_type") + "." + entry.getKey();
        Object value = entry.getValue();
        assertThat(isSafe(value))
            .as("%s = %s is not an id, enum, number or token", where, value)
            .isTrue();
        String normalised = normalise(String.valueOf(value));
        for (String canary : canaries) {
          assertThat(normalised)
              .as("%s carries canary %s", where, canary)
              .doesNotContain(normalise(canary));
        }
      }
    }
  }

  private static boolean isSafe(Object value) {
    return switch (value) {
      case Number n -> true;
      case Boolean b -> true;
      case String s ->
          UUID_PATTERN.matcher(s).matches()
              || ENUM_NAME.matcher(s).matches()
              || SAFE_TOKEN.matcher(s).matches();
      case null, default -> false;
    };
  }

  /** Lower-cased with spaces, dots, dashes, plus signs, commas and at signs removed. */
  private static String normalise(String s) {
    return s.toLowerCase(Locale.ROOT).replaceAll("[\\s.\\-+@,]", "");
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
