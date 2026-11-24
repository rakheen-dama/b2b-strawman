package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.create;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Phase 93 653A: a take-on batch lives in its tenant's schema only — every batch and source
 * endpoint answers 404 from another tenant, the other tenant's history does not list it, and the
 * archived file's S3 key carries the owning tenant's schema. (Extended by 655A, 656A — matters and
 * the go-live checklist — 657C, 658A.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnTenantIsolationIntegrationTest {

  private static final String ORG_A = "org_takeon_iso_a";
  private static final String ORG_B = "org_takeon_iso_b";
  private static final String OWNER_A = "user_toi_owner_a";
  private static final String OWNER_B = "user_toi_owner_b";
  private static final String CSV =
      "Client name,Email,Client type,CDD completed date,Beneficial ownership,Screening,Records\n"
          + "A,iso@example.test,Individual,01/02/2024,Done,Not screened,Box\n";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private StorageService storageService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;

  private String schemaA;
  private String schemaB;
  private UUID batchId;
  private UUID sourceId;
  private UUID ownerAId;
  private UUID ownerBId;

  @BeforeAll
  void setup() throws Exception {
    schemaA = provisioningService.provisionTenant(ORG_A, "Take-on Iso A", "legal-za").schemaName();
    schemaB = provisioningService.provisionTenant(ORG_B, "Take-on Iso B", "legal-za").schemaName();
    ownerAId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_A, OWNER_A, "toi_a@test.com", "Iso A", "owner"));
    ownerBId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_B, OWNER_B, "toi_b@test.com", "Iso B", "owner"));
    UUID[] ids = create(mockMvc, ownerA(), "CLIENTS", "iso.csv", TakeOnTestFiles.csv(CSV));
    batchId = ids[0];
    sourceId = ids[1];
  }

  @Test
  void everyBatchAndSourceEndpointIsNotFoundFromAnotherTenant() throws Exception {
    String batch = "/api/take-on/batches/" + batchId;
    String source = batch + "/sources/" + sourceId;
    List<AbstractMockHttpServletRequestBuilder<?>> requests =
        List.<AbstractMockHttpServletRequestBuilder<?>>of(
            get(batch),
            get(batch + "/rows"),
            get(source + "/preview"),
            get(source + "/file"),
            multipart(batch + "/sources")
                .file(new MockMultipartFile("file", "x.csv", "text/csv", CSV.getBytes()))
                .param("section", "MAIN"),
            delete(source),
            json(put(source + "/sheet"), "{\"sheetName\":\"CSV\",\"headerRow\":1}"),
            json(put(source + "/mapping"), "{\"targets\":{}}"),
            json(put(batch + "/position"), "{\"cutoverDate\":\"2026-10-31\"}"),
            post(batch + "/validate"),
            post(batch + "/cancel"),
            // 655A
            json(
                post(batch + "/attestation"),
                "{\"validationHash\":\"x\",\"statementVersion\":\"FICA-ATTEST-v1\",\"defaults\":{}}"),
            delete(batch + "/sign-offs/" + UUID.randomUUID()),
            get(batch + "/result.csv"),
            post(batch + "/portal-contacts"));
    for (var request : requests) {
      mockMvc
          .perform(request.with(ownerB()))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.title").value("Take-on batch not found"));
    }
    // still intact for its own tenant
    mockMvc
        .perform(get(batch).with(ownerA()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UPLOADED"));
  }

  /**
   * 655A: a commit claim from another tenant cannot see the batch (the controller's commit is
   * refused up front while the test profile's queue is off, so the claim is driven directly), and a
   * committed batch's clients exist only in the owning tenant's schema — another tenant's drain
   * never touches it.
   */
  @Test
  void committedClientsExistOnlyInTheOwningTenant() throws Exception {
    var batch =
        TakeOnTestApi.validatedClients(
            mockMvc,
            ownerA(),
            TakeOnClientsFiles.csv(TakeOnClientsFiles.row("Iso Client", "iso.client@example.test")),
            TakeOnClientsFiles.mapping());
    TakeOnTestApi.attest(mockMvc, ownerA(), batch.batchId(), batch.hash(), java.util.Map.of())
        .andExpect(status().isOk());
    assertThatThrownBy(
            () ->
                TakeOnTestDrain.commit(
                    commitService, schemaB, ORG_B, ownerBId, batch.batchId(), batch.hash()))
        .isInstanceOf(ResourceNotFoundException.class);
    TakeOnTestDrain.commit(commitService, schemaA, ORG_A, ownerAId, batch.batchId(), batch.hash());
    TakeOnTestDrain.run(drain, schemaB, ORG_B); // B's drain sees no committing batch of A's
    assertThat(batchStatus(schemaA, batch.batchId())).isEqualTo("COMMITTING");
    TakeOnTestDrain.run(drain, schemaA, ORG_A);
    assertThat(batchStatus(schemaA, batch.batchId())).isEqualTo("COMPLETED");
    assertThat(clientsWithEmail(schemaA, "iso.client@example.test")).isEqualTo(1);
    assertThat(clientsWithEmail(schemaB, "iso.client@example.test")).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".client_fica_attestations".formatted(schemaB),
                Integer.class))
        .isZero();
  }

  /**
   * 656A: a matters batch committed in A creates its matter in A's schema only, linked to A's
   * client; B's drain never touches it.
   */
  @Test
  void committedMattersExistOnlyInTheOwningTenant() throws Exception {
    var clients =
        TakeOnTestApi.validatedClients(
            mockMvc,
            ownerA(),
            TakeOnClientsFiles.csv(
                TakeOnClientsFiles.row("Iso Matter Client", "iso.matter.client@example.test")),
            TakeOnClientsFiles.mapping());
    TakeOnTestApi.attest(mockMvc, ownerA(), clients.batchId(), clients.hash(), java.util.Map.of())
        .andExpect(status().isOk());
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schemaA, ORG_A, ownerAId, clients.batchId(), clients.hash());
    var matters =
        TakeOnTestApi.validatedMatters(
            mockMvc,
            ownerA(),
            TakeOnMattersFiles.csv(
                TakeOnMattersFiles.row("Iso matter")
                    .clientEmail("iso.matter.client@example.test")
                    .number("ISO-M-1")),
            TakeOnMattersFiles.mapping());
    assertThatThrownBy(
            () ->
                TakeOnTestDrain.commit(
                    commitService, schemaB, ORG_B, ownerBId, matters.batchId(), matters.hash()))
        .isInstanceOf(ResourceNotFoundException.class);
    TakeOnTestDrain.commit(
        commitService, schemaA, ORG_A, ownerAId, matters.batchId(), matters.hash());
    TakeOnTestDrain.run(drain, schemaB, ORG_B);
    assertThat(batchStatus(schemaA, matters.batchId())).isEqualTo("COMMITTING");
    TakeOnTestDrain.run(drain, schemaA, ORG_A);
    assertThat(batchStatus(schemaA, matters.batchId())).isEqualTo("COMPLETED");
    assertThat(mattersWithReference(schemaA, "ISO-M-1")).isEqualTo(1);
    assertThat(mattersWithReference(schemaB, "ISO-M-1")).isZero();
  }

  /** 656A: the go-live checklist is the firm's own — B sees nothing A acknowledged. */
  @Test
  void anotherTenantsGoLiveChecklistShowsNothingThisTenantAcknowledged() throws Exception {
    String url = "/api/take-on/go-live-checklist";
    mockMvc
        .perform(
            json(put(url + "/AUDITOR_INFORMED"), "{\"note\":\"Told on 1 November\"}")
                .with(ownerA()))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(url).with(ownerB()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.itemKey == 'AUDITOR_INFORMED')].acknowledged").value(false))
        .andExpect(jsonPath("$[?(@.acknowledged == true)]").isEmpty());
    // B cannot withdraw A's acknowledgement: in B there is nothing to withdraw
    mockMvc
        .perform(delete(url + "/AUDITOR_INFORMED").with(ownerB()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(url).with(ownerA()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.itemKey == 'AUDITOR_INFORMED')].acknowledged").value(true));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".take_on_go_live_items".formatted(schemaB),
                Integer.class))
        .isZero();
  }

  /**
   * 657C: a trust take-on committed in A — its opening rows, unidentified items, investments and
   * report exist only in A's schema; B's sign-off queue is empty of it, B's signer endpoints and
   * B's claim cannot see it (404), and A's report is not found in B.
   */
  @Test
  void aCommittedTrustTakeOnExistsOnlyInTheOwningTenant() throws Exception {
    var world =
        new TakeOnTrustWorld(
            mockMvc, jdbc, commitService, drain, ORG_A, OWNER_A, schemaA, ownerAId, "toi");
    world.takeOnClientsAndMatters(List.of("A", "B", "C"));
    var acc = world.freshAccounts();
    var batch = world.validated(acc);
    world.signOff(batch);

    for (var request :
        List.<MockHttpServletRequestBuilder>of(
            get("/api/take-on/batches/" + batch.batchId() + "/sign-off-review"),
            json(
                post("/api/take-on/batches/" + batch.batchId() + "/sign-off"),
                "{\"validationHash\":\"%s\",\"statementVersion\":\"TAKEON-SIGNOFF-v1\",\"declarationAccepted\":true}"
                    .formatted(batch.hash())),
            delete("/api/take-on/batches/" + batch.batchId() + "/trust-sign-off"))) {
      mockMvc
          .perform(request.with(ownerB()))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.title").value("Take-on batch not found"));
    }
    List<String> queueB =
        JsonPath.read(
            mockMvc
                .perform(get("/api/take-on/sign-off-queue").with(ownerB()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$[*].batchId");
    assertThat(queueB).doesNotContain(batch.batchId().toString());
    assertThatThrownBy(
            () ->
                TakeOnTestDrain.commit(
                    commitService, schemaB, ORG_B, ownerBId, batch.batchId(), batch.hash()))
        .isInstanceOf(ResourceNotFoundException.class);

    world.claim(batch);
    TakeOnTestDrain.run(drain, schemaB, ORG_B); // B's drain sees no committing batch of A's
    assertThat(batchStatus(schemaA, batch.batchId())).isEqualTo("COMMITTING");
    world.drain();
    assertThat(batchStatus(schemaA, batch.batchId())).isEqualTo("COMPLETED");

    UUID reportId = UUID.fromString(JsonPath.read(world.batchJson(batch.batchId()), "$.reportId"));
    for (var check :
        List.of(
            List.of("trust_transactions", "take_on_batch_id"),
            List.of("unidentified_deposits", "take_on_batch_id"),
            List.of("trust_investments", "take_on_batch_id"),
            List.of("take_on_reports", "batch_id"))) {
      String sql = "SELECT count(*) FROM \"%s\".%s WHERE %s = ?";
      assertThat(
              jdbc.queryForObject(
                  sql.formatted(schemaA, check.get(0), check.get(1)),
                  Integer.class,
                  batch.batchId()))
          .as("A holds its %s", check.get(0))
          .isPositive();
      assertThat(
              jdbc.queryForObject(
                  sql.formatted(schemaB, check.get(0), check.get(1)),
                  Integer.class,
                  batch.batchId()))
          .as("B holds none of A's %s", check.get(0))
          .isZero();
    }
    mockMvc
        .perform(get("/api/take-on/reports/" + reportId).with(ownerB()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/take-on/reports/" + reportId).with(ownerA()))
        .andExpect(status().isOk());
    // 657D: nor can B download A's report files, or ask for them to be rendered
    for (String format : List.of("pdf", "csv")) {
      reportNotFound(TakeOnTestApi.reportFile(mockMvc, ownerB(), reportId, format), reportId);
      TakeOnTestApi.reportFile(mockMvc, ownerA(), reportId, format).andExpect(status().isOk());
    }
    reportNotFound(TakeOnTestApi.renderReport(mockMvc, ownerB(), reportId), reportId);
  }

  /** 657D: another tenant's report reads as not found, with the whole ProblemDetail. */
  private static void reportNotFound(ResultActions result, UUID reportId) throws Exception {
    result
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Take-on report not found"))
        .andExpect(jsonPath("$.detail").value("No take-on report found with id " + reportId));
  }

  private int mattersWithReference(String schema, String reference) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".projects WHERE reference_number = ?".formatted(schema),
        Integer.class,
        reference);
  }

  private String batchStatus(String schema, UUID id) {
    return jdbc.queryForObject(
        "SELECT status FROM \"%s\".take_on_batches WHERE id = ?".formatted(schema),
        String.class,
        id);
  }

  private int clientsWithEmail(String schema, String email) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".customers WHERE lower(email) = ?".formatted(schema),
        Integer.class,
        email);
  }

  @Test
  void anotherTenantsHistoryDoesNotListTheBatch() throws Exception {
    String json =
        mockMvc
            .perform(get("/api/take-on/batches?size=200").with(ownerB()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(json, "$.content[*].id");
    assertThat(ids).doesNotContain(batchId.toString());
  }

  @Test
  void theArchivedFileKeyCarriesTheOwningTenantsSchema() {
    assertThat(storageService.listKeys(TakeOnStorageKeys.batchPrefix(schemaA, batchId)))
        .singleElement()
        .satisfies(key -> assertThat(key).startsWith("take-on/" + schemaA + "/" + batchId + "/"));
    assertThat(storageService.listKeys(TakeOnStorageKeys.batchPrefix(schemaB, batchId))).isEmpty();
    assertThat(schemaA).isNotEqualTo(schemaB);
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder builder, String body) {
    return builder.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private RequestPostProcessor ownerA() {
    return TestJwtFactory.ownerJwt(ORG_A, OWNER_A);
  }

  private RequestPostProcessor ownerB() {
    return TestJwtFactory.ownerJwt(ORG_B, OWNER_B);
  }
}
