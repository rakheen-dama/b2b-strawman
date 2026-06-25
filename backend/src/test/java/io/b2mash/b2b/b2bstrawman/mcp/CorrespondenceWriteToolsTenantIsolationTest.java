package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.correspondence.CorrespondenceRepository;
import io.b2mash.b2b.b2bstrawman.mcp.dto.FileCorrespondenceToolResponse;
import io.b2mash.b2b.b2bstrawman.mcp.tool.CorrespondenceWriteTools;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 582B.5 — mandatory tenant-isolation test. Correspondence filed via {@code file_correspondence}
 * under tenant A's schema is invisible under tenant B's schema, and a write executed while bound to
 * tenant B lands in B's schema (never bleeds into A). Isolation is enforced by the standard
 * schema-per-tenant {@code search_path}; no Phase-81-specific isolation code exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorrespondenceWriteToolsTenantIsolationTest {

  private static final String ORG_A = "org_mcp_582_iso_a";
  private static final String ORG_B = "org_mcp_582_iso_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceWriteTools tools;
  @Autowired private CorrespondenceRepository correspondenceRepository;

  private Tenant tenantA;
  private Tenant tenantB;

  private record Tenant(String orgId, String schema, UUID ownerMemberId, UUID matterId) {}

  @BeforeAll
  void setup() throws Exception {
    tenantA = provision(ORG_A, "MCP 582 Iso A", "user_a");
    tenantB = provision(ORG_B, "MCP 582 Iso B", "user_b");
  }

  private Tenant provision(String orgId, String orgName, String userSubject) throws Exception {
    provisioningService.provisionTenant(orgId, orgName, null);
    UUID owner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, orgId, userSubject, userSubject + "@test.com", "Owner", "owner"));
    String schema =
        orgSchemaMappingRepository.findByClerkOrgId(orgId).orElseThrow().getSchemaName();
    JwtRequestPostProcessor jwt = TestJwtFactory.ownerJwt(orgId, userSubject);
    UUID matterId =
        UUID.fromString(TestEntityHelper.createProject(mockMvc, jwt, "Matter " + orgId));

    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, owner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> enablementService.enable("popia-egress-v1")));
    return new Tenant(orgId, schema, owner, matterId);
  }

  @Test
  void correspondenceFiledInTenantAIsInvisibleUnderTenantB() {
    String messageId = "<iso-a-1@mail.test>";
    var filed =
        fileAs(
            tenantA,
            tools ->
                (FileCorrespondenceToolResponse)
                    tools.fileCorrespondence(
                        tenantA.matterId(),
                        null,
                        messageId,
                        "Tenant A subject",
                        null,
                        null,
                        "a@b.co",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    assertThat(filed.idempotent()).isFalse();

    // Visible under A's schema.
    boolean visibleInA =
        inTenant(
                tenantA.schema(), () -> correspondenceRepository.findById(filed.correspondenceId()))
            .isPresent();
    assertThat(visibleInA).isTrue();

    // Not found under B's schema — pure search_path isolation.
    boolean visibleInB =
        inTenant(
                tenantB.schema(), () -> correspondenceRepository.findById(filed.correspondenceId()))
            .isPresent();
    assertThat(visibleInB).isFalse();

    boolean byMessageIdInB =
        inTenant(tenantB.schema(), () -> correspondenceRepository.findByMessageId(messageId))
            .isPresent();
    assertThat(byMessageIdInB).isFalse();
  }

  @Test
  void memberInTenantBFilesIntoTenantBOnly() {
    String messageId = "<iso-b-1@mail.test>";
    var filed =
        fileAs(
            tenantB,
            tools ->
                (FileCorrespondenceToolResponse)
                    tools.fileCorrespondence(
                        tenantB.matterId(),
                        null,
                        messageId,
                        "Tenant B subject",
                        null,
                        null,
                        "a@b.co",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    assertThat(filed.idempotent()).isFalse();

    boolean inB =
        inTenant(tenantB.schema(), () -> correspondenceRepository.findByMessageId(messageId))
            .isPresent();
    assertThat(inB).isTrue();

    boolean inA =
        inTenant(tenantA.schema(), () -> correspondenceRepository.findByMessageId(messageId))
            .isPresent();
    assertThat(inA).isFalse();

    boolean idInA =
        inTenant(
                tenantA.schema(), () -> correspondenceRepository.findById(filed.correspondenceId()))
            .isPresent();
    assertThat(idInA).isFalse();
  }

  private FileCorrespondenceToolResponse fileAs(
      Tenant tenant, java.util.function.Function<CorrespondenceWriteTools, Object> body) {
    Object[] holder = new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenant.schema())
        .where(RequestScopes.ORG_ID, tenant.orgId())
        .where(RequestScopes.MEMBER_ID, tenant.ownerMemberId())
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("MCP_ACCESS", "MCP_WRITE"))
        .run(() -> holder[0] = body.apply(tools));
    return (FileCorrespondenceToolResponse) holder[0];
  }

  private <T> T inTenant(String schema, java.util.function.Supplier<T> body) {
    @SuppressWarnings("unchecked")
    T[] holder = (T[]) new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(() -> holder[0] = transactionTemplate.execute(tx -> body.get()));
    return holder[0];
  }
}
