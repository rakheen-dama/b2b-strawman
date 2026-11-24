package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.addSource;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.create;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.map;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.position;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.validate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The trust take-on of the architecture §17.3.13 figures through the real write path, for the 657C
 * integration tests (Phase 93): clients and matters taken on by a clients and a matters batch (the
 * firm's previous-system ids A, B, C … and matter numbers A1, B1, C1 …), fresh trust accounts
 * through {@code POST /api/trust-accounts}, the four trust sections uploaded and mapped, the
 * position entered, the dry run, the owner's sign-off, the claim ({@link TakeOnTestDrain}) and the
 * drain. One per test class (its own tenant); nothing is written by SQL.
 */
public final class TakeOnTrustWorld {

  /** The cutover date of §17.3.13. */
  public static final String C = "2026-10-31";

  /** One take-on's accounts: the banking account G and fresh investment accounts I3 and I4. */
  public record Accounts(UUID g, UUID i3, UUID i4, String i3Number, String i4Number) {}

  /** A validated trust batch: its id, hash and the validation response. */
  public record Validated(UUID batchId, String hash, String json) {}

  private final MockMvc mockMvc;
  private final JdbcTemplate jdbc;
  private final TakeOnCommitService commitService;
  private final TakeOnDrainJobHandler drain;
  private final String orgId;
  private final String ownerSubject;
  private final String schema;
  private final UUID ownerId;
  private final String prefix;
  private final AtomicInteger accounts = new AtomicInteger();

  public TakeOnTrustWorld(
      MockMvc mockMvc,
      JdbcTemplate jdbc,
      TakeOnCommitService commitService,
      TakeOnDrainJobHandler drain,
      String orgId,
      String ownerSubject,
      String schema,
      UUID ownerId,
      String prefix) {
    this.mockMvc = mockMvc;
    this.jdbc = jdbc;
    this.commitService = commitService;
    this.drain = drain;
    this.orgId = orgId;
    this.ownerSubject = ownerSubject;
    this.schema = schema;
    this.ownerId = ownerId;
    this.prefix = prefix;
  }

  public RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(orgId, ownerSubject);
  }

  /**
   * Takes on a client per {@code refs} entry (previous-system id = the ref, e.g. {@code A}) and a
   * matter numbered {@code ref + "1"} for it, through committed clients and matters batches.
   */
  public void takeOnClientsAndMatters(List<String> refs) throws Exception {
    var clientRows = new ArrayList<TakeOnClientsFiles.Row>();
    var matterRows = new ArrayList<TakeOnMattersFiles.Row>();
    for (String ref : refs) {
      clientRows.add(
          TakeOnClientsFiles.row(
                  "Client " + ref + " " + prefix,
                  prefix + "." + ref.toLowerCase(Locale.ROOT) + "@example.test")
              .ref(ref));
      matterRows.add(TakeOnMattersFiles.row("Matter of " + ref).clientRef(ref).number(ref + "1"));
    }
    var clients =
        TakeOnTestApi.validatedClients(
            mockMvc,
            owner(),
            TakeOnClientsFiles.csv(clientRows.toArray(TakeOnClientsFiles.Row[]::new)),
            TakeOnClientsFiles.mapping());
    TakeOnTestApi.attest(mockMvc, owner(), clients.batchId(), clients.hash(), Map.of())
        .andExpect(status().isOk());
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, orgId, ownerId, clients.batchId(), clients.hash());
    var matters =
        TakeOnTestApi.validatedMatters(
            mockMvc,
            owner(),
            TakeOnMattersFiles.csv(matterRows.toArray(TakeOnMattersFiles.Row[]::new)),
            TakeOnMattersFiles.mapping());
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, orgId, ownerId, matters.batchId(), matters.hash());
  }

  /** The client taken on with previous-system id {@code ref} (read-only SQL). */
  public UUID client(String ref) {
    return jdbc.queryForObject(
        ("SELECT result_entity_id FROM \"%s\".take_on_rows WHERE dataset = 'CLIENTS'"
                + " AND source_ref = ? AND status = 'CREATED'")
            .formatted(schema),
        UUID.class,
        ref);
  }

  /** The matter numbered {@code number} (read-only SQL). */
  public UUID matter(String number) {
    return jdbc.queryForObject(
        "SELECT id FROM \"%s\".projects WHERE reference_number = ?".formatted(schema),
        UUID.class,
        number);
  }

  /** Fresh G (general, no dual approval), I3 (s86(3)) and I4 (s86(4)). */
  public Accounts freshAccounts() throws Exception {
    return freshAccounts(false);
  }

  public Accounts freshAccounts(boolean dualApproval) throws Exception {
    int n = accounts.incrementAndGet();
    String i3Number = prefix.toUpperCase(Locale.ROOT) + "-I3-" + n;
    String i4Number = prefix.toUpperCase(Locale.ROOT) + "-I4-" + n;
    UUID g =
        trustAccount(prefix + " Trust " + n, "62%08d".formatted(n), "GENERAL", null, dualApproval);
    UUID i3 = trustAccount(prefix + " I3 " + n, i3Number, "INVESTMENT", "FIRM_DISCRETION", false);
    UUID i4 =
        trustAccount(prefix + " I4 " + n, i4Number, "INVESTMENT", "CLIENT_INSTRUCTION", false);
    return new Accounts(g, i3, i4, i3Number, i4Number);
  }

  public UUID trustAccount(
      String name, String number, String type, String basis, boolean dualApproval)
      throws Exception {
    String basisField = basis == null ? "" : ", \"investmentBasis\": \"%s\"".formatted(basis);
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"accountName": "%s", "bankName": "First National Bank",
                         "branchCode": "250655", "accountNumber": "%s",
                         "accountType": "%s", "isPrimary": false,
                         "requireDualApproval": %s, "openedDate": "2025-01-01"%s}
                        """
                            .formatted(name, number, type, dualApproval, basisField)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(TestEntityHelper.extractId(result));
  }

  /** The §17.3.13 files for {@code accounts}. */
  public Map<String, List<String>> files(Accounts accounts) {
    return TakeOnTrustFiles.section17313(accounts.i3Number(), accounts.i4Number());
  }

  /** Uploads every section of {@code files} into a new trust batch on {@code g}, maps it. */
  public UUID upload(UUID g, Map<String, List<String>> files) throws Exception {
    UUID batch = null;
    for (String section : TakeOnTrustFiles.SECTIONS) {
      var rows = files.get(section);
      if (rows == null || rows.isEmpty()) {
        continue;
      }
      byte[] bytes = TakeOnTestFiles.csv(TakeOnTrustFiles.csv(section, rows));
      String fileName = section.toLowerCase(Locale.ROOT) + ".csv";
      UUID source;
      if (batch == null) {
        UUID[] ids =
            create(
                mockMvc,
                owner(),
                "TRUST_POSITION",
                fileName,
                bytes,
                "section",
                section,
                "trustAccountId",
                g.toString());
        batch = ids[0];
        source = ids[1];
      } else {
        String added =
            json(
                addSource(mockMvc, owner(), batch, section, fileName, bytes)
                    .andExpect(status().isCreated()));
        source = UUID.fromString(JsonPath.read(added, "$.sourceId"));
      }
      map(mockMvc, owner(), batch, source, TakeOnTrustFiles.mapping(section))
          .andExpect(status().isOk());
    }
    return batch;
  }

  /** Upload, map, enter {@code position} and validate (expects 200). */
  public Validated validated(UUID g, Map<String, List<String>> files, Map<String, Object> position)
      throws Exception {
    UUID batch = upload(g, files);
    position(mockMvc, owner(), batch, position).andExpect(status().isOk());
    String json = json(validate(mockMvc, owner(), batch).andExpect(status().isOk()));
    return new Validated(batch, JsonPath.read(json, "$.validationHash"), json);
  }

  /** The §17.3.13 take-on of {@code accounts}, validated. */
  public Validated validated(Accounts accounts) throws Exception {
    return validated(accounts.g(), files(accounts), TakeOnTrustFiles.section17313Position(C));
  }

  /** The owner signs off (expects 200). */
  public void signOff(Validated batch) throws Exception {
    TakeOnTestApi.signOff(mockMvc, owner(), batch.batchId(), batch.hash())
        .andExpect(status().isOk());
  }

  /** The claim as the owner (batch {@code VALIDATED → COMMITTING}). */
  public void claim(Validated batch) {
    TakeOnTestDrain.commit(commitService, schema, orgId, ownerId, batch.batchId(), batch.hash());
  }

  public void drain() {
    TakeOnTestDrain.run(drain, schema, orgId);
  }

  /** Validated, signed off by the owner, claimed and drained. */
  public Validated committed(Accounts accounts) throws Exception {
    var batch = validated(accounts);
    signOff(batch);
    claim(batch);
    drain();
    return batch;
  }

  /** The batch as the owner reads it. */
  public String batchJson(UUID batchId) throws Exception {
    return json(TakeOnTestApi.detail(mockMvc, owner(), batchId).andExpect(status().isOk()));
  }

  public static String json(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }
}
