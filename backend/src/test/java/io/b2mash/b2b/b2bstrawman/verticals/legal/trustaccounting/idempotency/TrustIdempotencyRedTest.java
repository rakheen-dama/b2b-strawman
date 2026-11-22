package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** 648B F-11 red: a lost-response retry of a deposit (same request, same key) posts twice. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustIdempotencyRedTest {
  private static final String ORG_ID = "org_trust_idem_red_test";
  private static final String OWNER = "user_idem_red_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private JdbcTemplate jdbc;

  private String schema;
  private String accountId;
  private String clientId;
  private UUID matterId;

  @BeforeAll
  void setup() throws Exception {
    schema = provisioningService.provisionTenant(ORG_ID, "Idem Red Org", null).schemaName();
    var ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "idem_red_owner@test.com", "Idem Red Owner", "owner"));
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
    var jwt = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    accountId =
        TestEntityHelper.extractId(
            mockMvc
                .perform(
                    post("/api/trust-accounts")
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"accountName": "Idem Red Trust", "bankName": "Test Bank",
                             "branchCode": "250655", "accountNumber": "62000000777",
                             "accountType": "GENERAL", "isPrimary": false,
                             "requireDualApproval": false, "openedDate": "2026-01-15"}
                            """))
                .andReturn());
    clientId =
        TestEntityHelper.createCustomer(mockMvc, jwt, "Idem Red Client", "idem_red@test.com");
    matterId =
        TestTrustMatterHelper.openClientMatter(
            schema,
            transactionTemplate,
            projectRepository,
            UUID.fromString(clientId),
            "Idem red matter",
            ownerId);
  }

  @Test
  void aRetryWithTheSameKeyAndTheSameRequest_postsOnce() throws Exception {
    var key = UUID.randomUUID().toString();
    var body =
        """
        {"clientId": "%s", "matterId": "%s", "amount": 1000.00, "reference": "IDEM-RED-1",
         "description": "Lost-response retry", "transactionDate": "2026-03-01"}
        """
            .formatted(clientId, matterId);
    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc.perform(
          post("/api/trust-accounts/" + accountId + "/transactions/deposit")
              .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
              .header("Idempotency-Key", key)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body));
    }
    Long postings =
        jdbc.queryForObject(
            "SELECT count(*) FROM \"%s\".trust_transactions WHERE reference = 'IDEM-RED-1'"
                .formatted(schema),
            Long.class);
    assertThat(postings).as("deposits posted for one submit intent").isEqualTo(1L);
  }
}
