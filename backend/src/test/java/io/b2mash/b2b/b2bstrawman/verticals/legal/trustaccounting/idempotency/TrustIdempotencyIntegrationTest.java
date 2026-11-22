package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestIdempotency;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 648B F-11: the {@code Idempotency-Key} protocol on trust writes, over real HTTP (MockMvc) and the
 * real tenant schema. Every test uses its own client / reference / key, so rows are counted by
 * reference (no count-bleed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustIdempotencyIntegrationTest {
  private static final String ORG_ID = "org_trust_idem_protocol_test";
  private static final String OWNER = "user_idem_owner";
  private static final String ADMIN = "user_idem_admin";
  private static final String KEY = TestIdempotency.HEADER;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TrustIdempotencyPurgeJob purgeJob;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;

  private String schema;
  private UUID ownerId;
  private String accountId;

  @BeforeAll
  void setup() throws Exception {
    schema = provisioningService.provisionTenant(ORG_ID, "Idem Protocol Org", null).schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "idem_owner@test.com", "Idem Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, ADMIN, "idem_admin@test.com", "Idem Admin", "admin");
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
    accountId =
        TestEntityHelper.extractId(
            mockMvc
                .perform(
                    post("/api/trust-accounts")
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"accountName": "Idem Protocol Trust", "bankName": "Test Bank",
                             "branchCode": "250655", "accountNumber": "62000000888",
                             "accountType": "GENERAL", "isPrimary": false,
                             "requireDualApproval": false, "openedDate": "2026-01-15"}
                            """))
                .andReturn());
  }

  // --- same key, same request -------------------------------------------------------------

  @Test
  void sameKeySameRequest_postsOnce_andReplaysTheFirstResponseVerbatim() throws Exception {
    var client = newClient("replay");
    var key = UUID.randomUUID().toString();
    var body = deposit(client, "1000.00", "IDEM-REPLAY");

    var first = postDeposit(key, body, owner());
    var second = postDeposit(key, body, owner());

    assertThat(first.getStatus()).isEqualTo(201);
    assertThat(second.getStatus()).isEqualTo(201);
    assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
    assertThat(second.getContentType()).isEqualTo(first.getContentType());
    assertThat(second.getHeader("Location")).isEqualTo(first.getHeader("Location"));
    assertThat(first.getHeader(TrustIdempotencyService.REPLAYED_HEADER)).isNull();
    assertThat(second.getHeader(TrustIdempotencyService.REPLAYED_HEADER)).isEqualTo("true");
    assertThat(postings("IDEM-REPLAY")).isEqualTo(1);
    assertThat(stateOf(key)).isEqualTo("COMPLETED");
  }

  @Test
  void sameKeySameRequest_layoutOfTheJsonDoesNotMatter() throws Exception {
    var client = newClient("layout");
    var key = UUID.randomUUID().toString();
    var body = deposit(client, "250.00", "IDEM-LAYOUT");
    // the same members, reordered and re-spaced
    var reordered =
        """
        { "transactionDate":"2026-03-01", "reference":"IDEM-LAYOUT", "amount":250.00,
          "matterId":"%s", "clientId":"%s", "description":"Idempotency test" }
        """
            .formatted(client.matterId(), client.clientId());

    assertThat(postDeposit(key, body, owner()).getStatus()).isEqualTo(201);
    var retry = postDeposit(key, reordered, owner());

    assertThat(retry.getStatus()).isEqualTo(201);
    assertThat(retry.getHeader(TrustIdempotencyService.REPLAYED_HEADER)).isEqualTo("true");
    assertThat(postings("IDEM-LAYOUT")).isEqualTo(1);
  }

  @Test
  void aStoredLocationHeaderIsReplayed() throws Exception {
    var client = newClient("location");
    var key = UUID.randomUUID().toString();
    var body = deposit(client, "300.00", "IDEM-LOCATION");
    postDeposit(key, body, owner());
    // No trust endpoint sets Location today; prove a stored one is replayed with the body.
    jdbc.update(
        ("UPDATE \"%s\".trust_idempotency_keys SET response_headers = response_headers"
                + " || '{\"Location\": \"/api/trust-transactions/replayed\"}'::jsonb"
                + " WHERE idempotency_key = ?::uuid")
            .formatted(schema),
        key);

    var retry = postDeposit(key, body, owner());

    assertThat(retry.getStatus()).isEqualTo(201);
    assertThat(retry.getHeader("Location")).isEqualTo("/api/trust-transactions/replayed");
    assertThat(postings("IDEM-LOCATION")).isEqualTo(1);
  }

  // --- refusals -----------------------------------------------------------------------------

  @Test
  void sameKeyDifferentBody_isRefusedWith409_andNothingMorePosts() throws Exception {
    var client = newClient("different body");
    var key = UUID.randomUUID().toString();
    postDeposit(key, deposit(client, "100.00", "IDEM-DIFF-BODY"), owner());

    var reused = postDeposit(key, deposit(client, "999.00", "IDEM-DIFF-BODY"), owner());

    assertProblem(
        reused,
        409,
        "Idempotency key reused",
        "Idempotency key reused with a different request",
        TrustIdempotencyService.REUSED_CODE);
    assertThat(postings("IDEM-DIFF-BODY")).isEqualTo(1);
  }

  @Test
  void sameKeyOnADifferentEndpoint_isRefusedWith409() throws Exception {
    var client = newClient("different route");
    var key = UUID.randomUUID().toString();
    var body = deposit(client, "100.00", "IDEM-DIFF-ROUTE");
    postDeposit(key, body, owner());

    var reused =
        perform(
            post("/api/trust-accounts/" + accountId + "/transactions/payment")
                .header(KEY, key)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

    assertThat(reused.getStatus()).isEqualTo(409);
    assertThat((String) JsonPath.read(reused.getContentAsString(), "$.code"))
        .isEqualTo(TrustIdempotencyService.REUSED_CODE);
    assertThat(postings("IDEM-DIFF-ROUTE")).isEqualTo(1);
  }

  @Test
  void sameKeyDifferentMember_isRefusedWith409() throws Exception {
    var client = newClient("different member");
    var key = UUID.randomUUID().toString();
    var body = deposit(client, "100.00", "IDEM-DIFF-MEMBER");
    postDeposit(key, body, owner());

    var reused = postDeposit(key, body, TestJwtFactory.adminJwt(ORG_ID, ADMIN));

    assertProblem(
        reused,
        409,
        "Idempotency key reused",
        "Idempotency key reused with a different request",
        TrustIdempotencyService.REUSED_CODE);
    assertThat(postings("IDEM-DIFF-MEMBER")).isEqualTo(1);
  }

  @Test
  void anInProgressKey_isRefusedWith409_andNeverReExecuted() throws Exception {
    var client = newClient("in progress");
    var key = UUID.randomUUID().toString();
    var body = deposit(client, "100.00", "IDEM-IN-PROGRESS");
    postDeposit(key, body, owner());
    // Seed the state a crash between the write and its completion leaves: IN_PROGRESS.
    jdbc.update(
        ("UPDATE \"%s\".trust_idempotency_keys SET state = 'IN_PROGRESS', response_status = NULL,"
                + " response_body = NULL, response_headers = NULL, completed_at = NULL"
                + " WHERE idempotency_key = ?::uuid")
            .formatted(schema),
        key);

    var retry = postDeposit(key, body, owner());

    assertProblem(
        retry,
        409,
        "Request still in progress",
        "A request with this key is still in progress or its outcome is unknown — check the"
            + " ledger before retrying",
        TrustIdempotencyService.IN_PROGRESS_CODE);
    assertThat(postings("IDEM-IN-PROGRESS")).isEqualTo(1);
    assertThat(stateOf(key)).isEqualTo("IN_PROGRESS");
  }

  @Test
  void aMissingKey_isRefusedWith400_andNothingPosts() throws Exception {
    var client = newClient("missing key");

    var refused =
        perform(
            post("/api/trust-accounts/" + accountId + "/transactions/deposit")
                .with(owner())
                .with(TestIdempotency.withoutIdempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(deposit(client, "100.00", "IDEM-MISSING")));

    assertProblem(
        refused,
        400,
        "Idempotency-Key header required",
        "Every trust write needs an Idempotency-Key header (a UUID) so that a retry never posts"
            + " twice.",
        null);
    assertThat(postings("IDEM-MISSING")).isZero();
  }

  @Test
  void aMalformedKey_isRefusedWith400_andNothingPosts() throws Exception {
    var client = newClient("malformed key");
    for (var malformed : List.of("not-a-uuid", "1-1-1-1-1", "")) {
      var refused = postDeposit(malformed, deposit(client, "100.00", "IDEM-MALFORMED"), owner());
      assertThat(refused.getStatus()).as("key '%s'", malformed).isEqualTo(400);
    }
    var refused = postDeposit("not-a-uuid", deposit(client, "100.00", "IDEM-MALFORMED"), owner());
    assertProblem(
        refused,
        400,
        "Invalid Idempotency-Key",
        "The Idempotency-Key header must be a UUID.",
        null);
    assertThat(postings("IDEM-MALFORMED")).isZero();
  }

  @Test
  void aRefusal_isReplayedForTheSameKey_evenAfterItsCauseIsGone() throws Exception {
    var source = newClient("refusal source");
    var target = newClient("refusal target");
    var key = UUID.randomUUID().toString();
    var transfer =
        """
        {"sourceClientId": "%s", "sourceMatterId": "%s", "targetClientId": "%s",
         "targetMatterId": "%s", "amount": 500.00, "reference": "IDEM-REFUSAL",
         "description": "Refused transfer", "transactionDate": "2026-03-02"}
        """
            .formatted(source.clientId(), source.matterId(), target.clientId(), target.matterId());

    var refused = postTransfer(key, transfer);
    assertThat(refused.getStatus()).isEqualTo(400);
    assertThat((String) JsonPath.read(refused.getContentAsString(), "$.title"))
        .isEqualTo("Insufficient trust balance");

    // The source can now cover the transfer — the same key still replays the refusal.
    postDeposit(
        UUID.randomUUID().toString(), deposit(source, "1000.00", "IDEM-REFUSAL-FUND"), owner());
    var retry = postTransfer(key, transfer);

    assertThat(retry.getStatus()).isEqualTo(400);
    assertThat(retry.getContentAsString()).isEqualTo(refused.getContentAsString());
    assertThat(retry.getHeader(TrustIdempotencyService.REPLAYED_HEADER)).isEqualTo("true");
    assertThat(postings("IDEM-REFUSAL")).isZero();

    // A new key is a new request: it runs and posts.
    assertThat(postTransfer(UUID.randomUUID().toString(), transfer).getStatus()).isEqualTo(201);
    assertThat(postings("IDEM-REFUSAL")).isEqualTo(2); // the transfer's pair of rows
  }

  // --- bounded buffering (security review MEDIUM) ---------------------------------------------

  @Test
  void anOversizedJsonBody_isRefusedWith413_beforeTheKeyIsClaimedOrTheWriteRuns() throws Exception {
    var client = newClient("oversized");
    var key = UUID.randomUUID().toString();
    var padding = "x".repeat(TrustIdempotencyFilter.MAX_JSON_BODY_BYTES);
    var body =
        """
        {"clientId": "%s", "matterId": "%s", "amount": 100.00, "reference": "IDEM-OVERSIZED",
         "description": "%s", "transactionDate": "2026-03-01"}
        """
            .formatted(client.clientId(), client.matterId(), padding);

    var refused = postDeposit(key, body, owner());

    assertProblem(
        refused,
        413,
        "Request body too large",
        "A trust write's request body may not exceed 256 KB.",
        null);
    assertThat(stateOf(key)).as("no key claimed").isNull();
    assertThat(postings("IDEM-OVERSIZED")).isZero();
  }

  @Test
  void aBodyJustUnderTheLimit_isAccepted_andReplayed() throws Exception {
    var client = newClient("at limit");
    var key = UUID.randomUUID().toString();
    var template =
        """
        {"clientId": "%s", "matterId": "%s", "amount": 100.00, "reference": "IDEM-AT-LIMIT",
         "description": "Idempotency test", "transactionDate": "2026-03-01", "pad": "%s"}
        """;
    int frame =
        template
            .formatted(client.clientId(), client.matterId(), "")
            .getBytes(StandardCharsets.UTF_8)
            .length;
    var body =
        template.formatted(
            client.clientId(),
            client.matterId(),
            "x".repeat(TrustIdempotencyFilter.MAX_JSON_BODY_BYTES - frame));
    assertThat(body.getBytes(StandardCharsets.UTF_8))
        .hasSize(TrustIdempotencyFilter.MAX_JSON_BODY_BYTES);

    var first = postDeposit(key, body, owner());
    var retry = postDeposit(key, body, owner());

    assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(201);
    assertThat(retry.getHeader(TrustIdempotencyService.REPLAYED_HEADER)).isEqualTo("true");
    assertThat(postings("IDEM-AT-LIMIT")).isEqualTo(1);
  }

  @Test
  void anUnauthenticatedOversizedRequest_isRefusedBySecurityFirst() throws Exception {
    var refused =
        perform(
            post("/api/trust-accounts/" + accountId + "/transactions/deposit")
                .header(KEY, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"pad\": \""
                        + "x".repeat(2 * TrustIdempotencyFilter.MAX_JSON_BODY_BYTES)
                        + "\"}"));

    assertThat(refused.getStatus()).isEqualTo(401);
  }

  // --- concurrency ----------------------------------------------------------------------------

  /**
   * Two requests with one key at once: the first claims the key and then blocks inside its write (a
   * gate connection holds the trust account's row lock, which the deposit's posting lock waits for
   * — observed in {@code pg_stat_activity}, no sleeps); the second is refused with 409 while the
   * first is in progress. Released, the first posts — exactly one deposit — and a later retry
   * replays it.
   */
  @Test
  void concurrentRequestsWithOneKey_exactlyOneExecutes() throws Exception {
    var client = newClient("concurrent");
    var key = UUID.randomUUID().toString();
    var body = deposit(client, "700.00", "IDEM-CONCURRENT");
    var firstResponse = new AtomicReference<MockHttpServletResponse>();
    var firstError = new AtomicReference<Throwable>();
    var pool = dataSource.unwrap(HikariDataSource.class);
    Thread first = null;
    try (Connection gate =
        DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword())) {
      gate.setAutoCommit(false);
      int gatePid;
      try (var st = gate.createStatement();
          var rs = st.executeQuery("SELECT pg_backend_pid()")) {
        rs.next();
        gatePid = rs.getInt(1);
      }
      try (var ps =
          gate.prepareStatement(
              "SELECT id FROM \"%s\".trust_accounts WHERE id = ? FOR UPDATE".formatted(schema))) {
        ps.setObject(1, UUID.fromString(accountId));
        ps.executeQuery().close();
      }
      try {
        first =
            Thread.ofPlatform()
                .start(
                    () -> {
                      try {
                        firstResponse.set(postDeposit(key, body, owner()));
                      } catch (Throwable t) {
                        firstError.set(t);
                      }
                    });
        awaitBlockedBy(gatePid);
        assertThat(stateOf(key)).as("the first claimed the key").isEqualTo("IN_PROGRESS");

        var second = postDeposit(key, body, owner());

        assertThat(second.getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(second.getContentAsString(), "$.code"))
            .isEqualTo(TrustIdempotencyService.IN_PROGRESS_CODE);
      } finally {
        gate.rollback();
      }
    } finally {
      if (first != null) {
        first.join(TimeUnit.SECONDS.toMillis(60));
      }
    }
    assertThat(firstError.get()).isNull();
    assertThat(firstResponse.get().getStatus()).isEqualTo(201);
    assertThat(postings("IDEM-CONCURRENT")).isEqualTo(1);
    var later = postDeposit(key, body, owner());
    assertThat(later.getStatus()).isEqualTo(201);
    assertThat(later.getContentAsString()).isEqualTo(firstResponse.get().getContentAsString());
    assertThat(postings("IDEM-CONCURRENT")).isEqualTo(1);
  }

  // --- multipart ------------------------------------------------------------------------------

  @Test
  void aStatementImport_isIdempotent_andADifferentFileUnderTheSameKeyIsRefused() throws Exception {
    var account =
        TestEntityHelper.extractId(
            mockMvc
                .perform(
                    post("/api/trust-accounts")
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"accountName": "Idem Import Trust", "bankName": "Test Bank",
                             "branchCode": "250655", "accountNumber": "62000000999",
                             "accountType": "GENERAL", "isPrimary": false,
                             "requireDualApproval": false, "openedDate": "2026-01-15"}
                            """))
                .andReturn());
    var key = UUID.randomUUID().toString();
    var march = csv("fnb-march.csv", "45000.00");

    var first = importStatement(account, key, march);
    var retry = importStatement(account, key, march);

    assertThat(first.getStatus()).isEqualTo(201);
    assertThat(retry.getStatus()).isEqualTo(201);
    assertThat(retry.getContentAsString()).isEqualTo(first.getContentAsString());
    assertThat(retry.getHeader(TrustIdempotencyService.REPLAYED_HEADER)).isEqualTo("true");
    assertThat(statements(account)).isEqualTo(1);

    var different = importStatement(account, key, csv("fnb-march.csv", "45001.00"));
    assertThat(different.getStatus()).isEqualTo(409);
    assertThat((String) JsonPath.read(different.getContentAsString(), "$.code"))
        .isEqualTo(TrustIdempotencyService.REUSED_CODE);
    assertThat(statements(account)).isEqualTo(1);
  }

  // --- retention ------------------------------------------------------------------------------

  @Test
  void thePurge_deletesKeysOlderThanSevenDays_only() {
    var old = UUID.randomUUID();
    var recent = UUID.randomUUID();
    var now = Instant.now();
    seedKey(old, now.minus(Duration.ofDays(7)).minus(Duration.ofMinutes(5)));
    seedKey(recent, now.minus(Duration.ofDays(7)).plus(Duration.ofHours(1)));

    purgeJob.purgeExpiredKeys();

    assertThat(stateOf(old.toString())).isNull();
    assertThat(stateOf(recent.toString())).isEqualTo("COMPLETED");
  }

  // --- helpers --------------------------------------------------------------------------------

  private record Client(String clientId, UUID matterId) {}

  private Client newClient(String label) throws Exception {
    var clientId =
        TestEntityHelper.createCustomer(
            mockMvc, owner(), "Idem " + label, "idem_" + label.replace(' ', '_') + "@test.com");
    var matterId =
        TestTrustMatterHelper.openClientMatter(
            schema,
            transactionTemplate,
            projectRepository,
            UUID.fromString(clientId),
            "Idem matter " + label,
            ownerId);
    return new Client(clientId, matterId);
  }

  private static String deposit(Client client, String amount, String reference) {
    return """
        {"clientId": "%s", "matterId": "%s", "amount": %s, "reference": "%s",
         "description": "Idempotency test", "transactionDate": "2026-03-01"}
        """
        .formatted(client.clientId(), client.matterId(), amount, reference);
  }

  private MockHttpServletResponse postDeposit(String key, String body, RequestPostProcessor jwt)
      throws Exception {
    return perform(
        post("/api/trust-accounts/" + accountId + "/transactions/deposit")
            .header(KEY, key)
            .with(jwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private MockHttpServletResponse postTransfer(String key, String body) throws Exception {
    return perform(
        post("/api/trust-accounts/" + accountId + "/transactions/transfer")
            .header(KEY, key)
            .with(owner())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private MockHttpServletResponse importStatement(String account, String key, MockMultipartFile f)
      throws Exception {
    return perform(
        multipart("/api/trust-accounts/{accountId}/bank-statements", account)
            .file(f)
            .header(KEY, key)
            .with(owner()));
  }

  private static MockMultipartFile csv(String name, String firstAmount) {
    var content =
        """
        FNB Trust Account Statement - Account 62000000999
        Date,Description,Amount,Balance,Reference
        10/03/2026,EFT from Johnson Estate,%s,211500.00,REF-JE-IDEM
        15/03/2026,Payment to SARS,-12500.00,199000.00,SARS-IDEM
        """
            .formatted(firstAmount);
    return new MockMultipartFile(
        "file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
  }

  private MockHttpServletResponse perform(
      org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
    var response = mockMvc.perform(request).andReturn().getResponse();
    response.setCharacterEncoding("UTF-8");
    return response;
  }

  private static JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private static void assertProblem(
      MockHttpServletResponse response, int status, String title, String detail, String code)
      throws Exception {
    var json = response.getContentAsString();
    assertThat(response.getStatus()).as(json).isEqualTo(status);
    assertThat((String) JsonPath.read(json, "$.title")).isEqualTo(title);
    assertThat((String) JsonPath.read(json, "$.detail")).isEqualTo(detail);
    assertThat((Integer) JsonPath.read(json, "$.status")).isEqualTo(status);
    if (code != null) {
      assertThat((String) JsonPath.read(json, "$.code")).isEqualTo(code);
    }
  }

  private long postings(String reference) {
    Long n =
        jdbc.queryForObject(
            "SELECT count(*) FROM \"%s\".trust_transactions WHERE reference = ?".formatted(schema),
            Long.class,
            reference);
    return n == null ? 0 : n;
  }

  private long statements(String account) {
    Long n =
        jdbc.queryForObject(
            "SELECT count(*) FROM \"%s\".bank_statements WHERE trust_account_id = ?::uuid"
                .formatted(schema),
            Long.class,
            account);
    return n == null ? 0 : n;
  }

  private String stateOf(String key) {
    var states =
        jdbc.queryForList(
            "SELECT state FROM \"%s\".trust_idempotency_keys WHERE idempotency_key = ?::uuid"
                .formatted(schema),
            String.class,
            key);
    return states.isEmpty() ? null : states.getFirst();
  }

  private void seedKey(UUID key, Instant createdAt) {
    jdbc.update(
        ("INSERT INTO \"%s\".trust_idempotency_keys (idempotency_key, member_id, http_method,"
                + " route_template, request_hash, state, response_status, response_body,"
                + " created_at, completed_at) VALUES (?, ?, 'POST', '/api/x', 'h', 'COMPLETED',"
                + " 201, '{}', ?, ?)")
            .formatted(schema),
        key,
        ownerId,
        Timestamp.from(createdAt),
        Timestamp.from(createdAt));
  }

  /** Waits (bounded, 30 s) until a backend is blocked by {@code holderPid}. */
  private void awaitBlockedBy(int holderPid) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
              Long.class,
              holderPid);
      if (waiting != null && waiting >= 1) {
        return;
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); // poll interval
    }
    throw new AssertionError("the first request did not block on the gate within 30 s");
  }
}
