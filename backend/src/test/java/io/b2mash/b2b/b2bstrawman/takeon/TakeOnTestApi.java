package io.b2mash.b2b.b2bstrawman.takeon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drives the take-on endpoints the way the wizard does (the real write path: multipart upload →
 * mapping → validate), so no test inserts take-on rows by SQL. Thin request builders; assertions
 * stay in the tests.
 */
public final class TakeOnTestApi {

  public static final String CSV = "text/csv";
  public static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private TakeOnTestApi() {}

  /** {@code POST /api/take-on/batches} with {@code params} as extra form fields. */
  public static ResultActions upload(
      MockMvc mockMvc,
      RequestPostProcessor jwt,
      String dataset,
      String fileName,
      String contentType,
      byte[] bytes,
      String... params)
      throws Exception {
    var request =
        multipart("/api/take-on/batches")
            .file(new MockMultipartFile("file", fileName, contentType, bytes))
            .param("dataset", dataset);
    for (int i = 0; i + 1 < params.length; i += 2) {
      request.param(params[i], params[i + 1]);
    }
    return mockMvc.perform(request.with(jwt));
  }

  /** Creates a batch and returns {@code [batchId, sourceId]} (expects 201). */
  public static UUID[] create(
      MockMvc mockMvc,
      RequestPostProcessor jwt,
      String dataset,
      String fileName,
      byte[] bytes,
      String... params)
      throws Exception {
    String contentType = fileName.endsWith(".xlsx") ? XLSX : CSV;
    var body =
        upload(mockMvc, jwt, dataset, fileName, contentType, bytes, params)
            .andReturn()
            .getResponse();
    if (body.getStatus() != 201) {
      throw new AssertionError(
          "upload failed: " + body.getStatus() + " " + body.getContentAsString());
    }
    String json = body.getContentAsString();
    return new UUID[] {
      UUID.fromString(JsonPath.read(json, "$.batch.id")),
      UUID.fromString(JsonPath.read(json, "$.sourceId"))
    };
  }

  public static ResultActions addSource(
      MockMvc mockMvc,
      RequestPostProcessor jwt,
      UUID batchId,
      String section,
      String fileName,
      byte[] bytes)
      throws Exception {
    var request =
        multipart("/api/take-on/batches/" + batchId + "/sources")
            .file(
                new MockMultipartFile(
                    "file", fileName, fileName.endsWith(".xlsx") ? XLSX : CSV, bytes));
    if (section != null) {
      request.param("section", section);
    }
    return mockMvc.perform(request.with(jwt));
  }

  /** {@code PUT …/mapping} with {@code targets} and optional extras. */
  public static ResultActions map(
      MockMvc mockMvc,
      RequestPostProcessor jwt,
      UUID batchId,
      UUID sourceId,
      Map<String, Object> body)
      throws Exception {
    return mockMvc.perform(
        json(put("/api/take-on/batches/" + batchId + "/sources/" + sourceId + "/mapping"), body)
            .with(jwt));
  }

  public static ResultActions mapTargets(
      MockMvc mockMvc,
      RequestPostProcessor jwt,
      UUID batchId,
      UUID sourceId,
      Map<String, String> targets)
      throws Exception {
    return map(mockMvc, jwt, batchId, sourceId, Map.of("targets", targets));
  }

  public static ResultActions validate(MockMvc mockMvc, RequestPostProcessor jwt, UUID batchId)
      throws Exception {
    return mockMvc.perform(post("/api/take-on/batches/" + batchId + "/validate").with(jwt));
  }

  public static ResultActions position(
      MockMvc mockMvc, RequestPostProcessor jwt, UUID batchId, String cutoverDate)
      throws Exception {
    return mockMvc.perform(
        json(
                put("/api/take-on/batches/" + batchId + "/position"),
                Map.of("cutoverDate", cutoverDate))
            .with(jwt));
  }

  /** {@code PUT …/position} with a full body (657B: the trust position's figures). */
  public static ResultActions position(
      MockMvc mockMvc, RequestPostProcessor jwt, UUID batchId, Map<String, Object> body)
      throws Exception {
    return mockMvc.perform(
        json(put("/api/take-on/batches/" + batchId + "/position"), body).with(jwt));
  }

  public static ResultActions sheet(
      MockMvc mockMvc,
      RequestPostProcessor jwt,
      UUID batchId,
      UUID sourceId,
      String sheetName,
      int headerRow)
      throws Exception {
    var body = new HashMap<String, Object>();
    body.put("sheetName", sheetName);
    body.put("headerRow", headerRow);
    return mockMvc.perform(
        json(put("/api/take-on/batches/" + batchId + "/sources/" + sourceId + "/sheet"), body)
            .with(jwt));
  }

  public static ResultActions cancel(MockMvc mockMvc, RequestPostProcessor jwt, UUID batchId)
      throws Exception {
    return mockMvc.perform(post("/api/take-on/batches/" + batchId + "/cancel").with(jwt));
  }

  public static ResultActions detail(MockMvc mockMvc, RequestPostProcessor jwt, UUID batchId)
      throws Exception {
    return mockMvc.perform(get("/api/take-on/batches/" + batchId).with(jwt));
  }

  public static ResultActions rows(
      MockMvc mockMvc, RequestPostProcessor jwt, UUID batchId, String query) throws Exception {
    return mockMvc.perform(
        get("/api/take-on/batches/" + batchId + "/rows" + (query == null ? "" : "?" + query))
            .with(jwt));
  }

  // --- 655A: attestation, commit, results ---

  /** {@code POST …/attestation} with {@code FICA-ATTEST-v1} and the given defaults. */
  public static ResultActions attest(
      MockMvc mockMvc,
      RequestPostProcessor jwt,
      UUID batchId,
      String validationHash,
      Map<String, String> defaults)
      throws Exception {
    var body = new HashMap<String, Object>();
    body.put("validationHash", validationHash);
    body.put("statementVersion", TakeOnStatements.FICA_ATTEST_V1);
    body.put("defaults", defaults == null ? Map.of() : defaults);
    return mockMvc.perform(
        json(post("/api/take-on/batches/" + batchId + "/attestation"), body).with(jwt));
  }

  public static ResultActions revokeSignOff(
      MockMvc mockMvc, RequestPostProcessor jwt, UUID batchId, UUID signOffId) throws Exception {
    return mockMvc.perform(
        delete("/api/take-on/batches/" + batchId + "/sign-offs/" + signOffId).with(jwt));
  }

  /**
   * {@code POST …/commit} through the controller (refused while the test profile's queue is off).
   */
  public static ResultActions commit(
      MockMvc mockMvc, RequestPostProcessor jwt, UUID batchId, String validationHash)
      throws Exception {
    return mockMvc.perform(
        json(
                post("/api/take-on/batches/" + batchId + "/commit"),
                Map.of("validationHash", validationHash == null ? "" : validationHash))
            .with(jwt));
  }

  public static ResultActions resultCsv(MockMvc mockMvc, RequestPostProcessor jwt, UUID batchId)
      throws Exception {
    return mockMvc.perform(get("/api/take-on/batches/" + batchId + "/result.csv").with(jwt));
  }

  public static ResultActions portalContacts(
      MockMvc mockMvc, RequestPostProcessor jwt, UUID batchId) throws Exception {
    return mockMvc.perform(post("/api/take-on/batches/" + batchId + "/portal-contacts").with(jwt));
  }

  /** A validated batch: its id and the hash every attestation and commit binds to. */
  public record Validated(UUID batchId, String hash, String json) {}

  /** Uploads {@code csv} as a clients file, maps {@code targets}, validates (expects 200). */
  public static Validated validatedClients(
      MockMvc mockMvc, RequestPostProcessor jwt, String csv, Map<String, Object> mapping)
      throws Exception {
    return validated(mockMvc, jwt, "CLIENTS", "clients.csv", csv, mapping);
  }

  /** Uploads {@code csv} as a matters file (656A), maps it, validates (expects 200). */
  public static Validated validatedMatters(
      MockMvc mockMvc, RequestPostProcessor jwt, String csv, Map<String, Object> mapping)
      throws Exception {
    return validated(mockMvc, jwt, "MATTERS", "matters.csv", csv, mapping);
  }

  private static Validated validated(
      MockMvc mockMvc,
      RequestPostProcessor jwt,
      String dataset,
      String fileName,
      String csv,
      Map<String, Object> mapping)
      throws Exception {
    UUID[] ids =
        create(
            mockMvc, jwt, dataset, fileName, csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    map(mockMvc, jwt, ids[0], ids[1], mapping)
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    String json =
        validate(mockMvc, jwt, ids[0])
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Validated(ids[0], JsonPath.read(json, "$.validationHash"), json);
  }

  /** The {@code content} of a rows page as maps. */
  public static List<Map<String, Object>> rowContent(String json) {
    return JsonPath.read(json, "$.content");
  }

  /**
   * One audit row as read back (read-only SQL — evidence only). {@code details} are the event's own
   * params: the audit service's {@code actor_name} (the acting staff member, stamped on every event
   * by the platform) is split off into {@code actorName}.
   */
  public record AuditRow(
      String eventType, String actorType, String actorName, Map<String, Object> details) {}

  /** The audit rows of one entity, oldest first. */
  @SuppressWarnings("unchecked")
  public static List<AuditRow> auditEvents(JdbcTemplate jdbc, String schema, UUID entityId) {
    return jdbc
        .queryForList(
            "SELECT event_type, actor_type, details::text AS details FROM \"%s\".audit_events"
                    .formatted(schema)
                + " WHERE entity_id = ? ORDER BY occurred_at, id",
            entityId)
        .stream()
        .map(
            r -> {
              var details =
                  r.get("details") == null
                      ? new HashMap<String, Object>()
                      : new HashMap<String, Object>(
                          JSON.readValue((String) r.get("details"), Map.class));
              Object actorName = details.remove("actor_name");
              return new AuditRow(
                  (String) r.get("event_type"),
                  (String) r.get("actor_type"),
                  actorName == null ? null : actorName.toString(),
                  details);
            })
        .toList();
  }

  /** Backdates only this test's own batch (C-18 — no other way to age a batch). */
  public static void backdate(JdbcTemplate jdbc, String schema, UUID batchId, int days) {
    jdbc.update(
        "UPDATE \"%s\".take_on_batches SET created_at = now() - make_interval(days => ?) WHERE id = ?"
            .formatted(schema),
        days,
        batchId);
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder builder, Object body) {
    return builder.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
  }
}
