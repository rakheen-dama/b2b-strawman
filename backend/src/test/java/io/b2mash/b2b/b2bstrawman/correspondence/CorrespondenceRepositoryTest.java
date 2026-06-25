package io.b2mash.b2b.b2bstrawman.correspondence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Repository-level tests: V132 provisioning clean, JSONB round-trip, and the {@code
 * chk_correspondence_linkage} DB CHECK firing on both-null linkage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorrespondenceRepositoryTest {

  private static final String ORG_ID = "org_corr_repo_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CorrespondenceRepository correspondenceRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    // Provisioning runs all tenant migrations including V132 — if V132 is broken this throws.
    provisioningService.provisionTenant(ORG_ID, "Corr Repo Test Org", null);
    var memberStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_corr_repo", "corr_repo@test.com", "Corr Repo", "owner");
    memberId = UUID.fromString(memberStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  @Test
  void jsonbAddressListsRoundTrip() {
    UUID[] idHolder = new UUID[1];
    var toAddresses = List.of("alice@example.com", "bob@example.com");
    var ccAddresses = List.of("carol@example.com");

    runInTenant(
        () -> {
          var c =
              new Correspondence(
                  null,
                  UUID.randomUUID(),
                  "Subject",
                  "body",
                  null,
                  "from@example.com",
                  toAddresses,
                  ccAddresses,
                  null,
                  null,
                  null,
                  "msg-jsonb-" + UUID.randomUUID() + "@example.com",
                  "MCP",
                  memberId);
          idHolder[0] = correspondenceRepository.saveAndFlush(c).getId();
        });

    runInTenant(
        () -> {
          var reloaded = correspondenceRepository.findById(idHolder[0]).orElseThrow();
          assertThat(reloaded.getToAddresses())
              .containsExactly("alice@example.com", "bob@example.com");
          assertThat(reloaded.getCcAddresses()).containsExactly("carol@example.com");
        });
  }

  @Test
  void findByMessageIdReturnsTheRow() {
    String messageId = "msg-find-" + UUID.randomUUID() + "@example.com";
    runInTenant(
        () -> {
          var c =
              new Correspondence(
                  UUID.randomUUID(),
                  null,
                  "S",
                  null,
                  null,
                  "from@example.com",
                  List.of(),
                  List.of(),
                  null,
                  null,
                  null,
                  messageId,
                  "MCP",
                  memberId);
          correspondenceRepository.saveAndFlush(c);
        });

    runInTenant(
        () -> {
          var found = correspondenceRepository.findByMessageId(messageId);
          assertThat(found).isPresent();
          assertThat(found.get().getMessageId()).isEqualTo(messageId);
        });
  }

  @Test
  void bothNullLinkageRejectedByDbCheckConstraint() {
    // Bypass service validateLinkage by persisting directly with both linkage columns null.
    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> {
                      var c =
                          new Correspondence(
                              null, // customerId
                              null, // projectId
                              "Subject",
                              null,
                              null,
                              "from@example.com",
                              List.of(),
                              List.of(),
                              null,
                              null,
                              null,
                              "msg-dbcheck-" + UUID.randomUUID() + "@example.com",
                              "MCP",
                              memberId);
                      correspondenceRepository.saveAndFlush(c); // flush forces INSERT → CHECK fires
                    }))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
