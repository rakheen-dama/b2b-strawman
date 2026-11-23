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
 * The platform's {@code actor_name} (the staff member, D-32) is split off first.
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
