package io.b2mash.b2b.b2bstrawman.correspondence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceCommand;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceResult;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Service-level idempotency + linkage tests for {@link CorrespondenceService}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorrespondenceServiceTest {

  private static final String ORG_ID = "org_corr_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CorrespondenceService correspondenceService;
  @Autowired private CorrespondenceRepository correspondenceRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Corr Svc Test Org", null);
    var memberStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_corr_owner", "corr_owner@test.com", "Corr Owner", "owner");
    memberId = UUID.fromString(memberStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private <T> T runInTenant(java.util.function.Supplier<T> body) {
    T[] holder = (T[]) new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> holder[0] = body.get());
    return holder[0];
  }

  private ActorContext actor() {
    return new ActorContext(memberId, "owner");
  }

  @Test
  void fileInboundPersistsInboundWithLinkage() {
    UUID projectId = UUID.randomUUID();
    String messageId = "msg-persist-" + UUID.randomUUID() + "@example.com";
    var result =
        runInTenant(
            () -> {
              var cmd =
                  new FileCorrespondenceCommand(
                      projectId,
                      null,
                      messageId,
                      "Hello",
                      "body",
                      null,
                      "sender@example.com",
                      List.of("to@example.com"),
                      List.of(),
                      null,
                      null,
                      null,
                      "MCP");
              return correspondenceService.fileInbound(cmd, actor());
            });

    assertThat(result.idempotent()).isFalse();
    runInTenant(
        () -> {
          var saved = correspondenceRepository.findById(result.correspondenceId()).orElseThrow();
          assertThat(saved.getDirection()).isEqualTo(Direction.INBOUND);
          assertThat(saved.getProjectId()).isEqualTo(projectId);
          assertThat(saved.getCustomerId()).isNull();
          assertThat(saved.getFromAddress()).isEqualTo("sender@example.com");
          assertThat(saved.getToAddresses()).containsExactly("to@example.com");
          assertThat(saved.getFiledByMemberId()).isEqualTo(memberId);
          assertThat(saved.getSource()).isEqualTo("MCP");
          assertThat(saved.getFiledAt()).isNotNull();
          return null;
        });
  }

  @Test
  void reFileSameMessageIdReturnsSameIdAndPersistsNothingNew() {
    UUID projectId = UUID.randomUUID();
    String messageId = "msg-idem-" + UUID.randomUUID() + "@example.com";

    FileCorrespondenceResult first =
        runInTenant(
            () -> correspondenceService.fileInbound(cmd(projectId, null, messageId), actor()));
    assertThat(first.idempotent()).isFalse();

    long countAfterFirst = runInTenant(correspondenceRepository::count);

    FileCorrespondenceResult second =
        runInTenant(
            () -> correspondenceService.fileInbound(cmd(projectId, null, messageId), actor()));

    assertThat(second.idempotent()).isTrue();
    assertThat(second.correspondenceId()).isEqualTo(first.correspondenceId());

    long countAfterSecond = runInTenant(correspondenceRepository::count);
    assertThat(countAfterSecond).isEqualTo(countAfterFirst);
  }

  @Test
  void reFileSameMessageIdDifferentLinkageDoesNotMutateExistingRow() {
    UUID originalProject = UUID.randomUUID();
    UUID differentProject = UUID.randomUUID();
    String messageId = "msg-relink-" + UUID.randomUUID() + "@example.com";

    FileCorrespondenceResult first =
        runInTenant(
            () ->
                correspondenceService.fileInbound(cmd(originalProject, null, messageId), actor()));

    FileCorrespondenceResult second =
        runInTenant(
            () ->
                correspondenceService.fileInbound(cmd(differentProject, null, messageId), actor()));

    assertThat(second.idempotent()).isTrue();
    assertThat(second.correspondenceId()).isEqualTo(first.correspondenceId());

    runInTenant(
        () -> {
          var saved = correspondenceRepository.findById(first.correspondenceId()).orElseThrow();
          // linkage NOT re-linked: still the original project
          assertThat(saved.getProjectId()).isEqualTo(originalProject);
          return null;
        });
  }

  @Test
  void bothNullLinkageRejectedAtServiceLevel() {
    String messageId = "msg-nolink-" + UUID.randomUUID() + "@example.com";
    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> correspondenceService.fileInbound(cmd(null, null, messageId), actor())))
        .isInstanceOf(InvalidStateException.class);
  }

  private FileCorrespondenceCommand cmd(UUID matterId, UUID customerId, String messageId) {
    return new FileCorrespondenceCommand(
        matterId,
        customerId,
        messageId,
        "Subject",
        "body text",
        null,
        "from@example.com",
        List.of("to@example.com"),
        List.of("cc@example.com"),
        null,
        null,
        null,
        "MCP");
  }
}
