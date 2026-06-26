package io.b2mash.b2b.b2bstrawman.correspondence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceCommand;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
 * 586A — view-access + tenant-isolation on the correspondence list endpoints. The access gate is
 * the SAME project view-access the documents-list uses (NOT MCP capabilities): a viewer with no MCP
 * capability reads fine; a member without project view-access gets 404 (security-by-obscurity); and
 * tenant B cannot read tenant A's list. Surefire ({@code -Dtest=...}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorrespondenceListAccessTest {

  private static final String ORG_A = "org_corr_586_a";
  private static final String ORG_B = "org_corr_586_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceService correspondenceService;

  private String schemaA;
  private UUID ownerAMemberId;
  private UUID matterA;
  private UUID clientA;
  private JwtRequestPostProcessor ownerA;
  private JwtRequestPostProcessor outsiderA; // org-A member NOT assigned to matterA
  private JwtRequestPostProcessor ownerB;

  @BeforeAll
  void setup() throws Exception {
    // ── Tenant A ──
    provisioningService.provisionTenant(ORG_A, "Corr 586 Org A", null);
    ownerAMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_A, "user_a_owner", "a_owner@test.com", "OwnerA", "owner"));
    // a plain member of org A who is NOT a member of matterA -> no project view-access
    TestMemberHelper.syncMember(
        mockMvc, ORG_A, "user_a_outsider", "a_outsider@test.com", "OutsiderA", "member");
    schemaA = orgSchemaMappingRepository.findByClerkOrgId(ORG_A).orElseThrow().getSchemaName();

    ownerA = TestJwtFactory.ownerJwt(ORG_A, "user_a_owner");
    outsiderA = TestJwtFactory.memberJwt(ORG_A, "user_a_outsider");
    matterA = UUID.fromString(TestEntityHelper.createProject(mockMvc, ownerA, "Matter A"));
    clientA =
        UUID.fromString(
            TestEntityHelper.createCustomer(mockMvc, ownerA, "Client A", "ca@test.com"));
    seedA("<a-corr-1@mail.test>", "Tenant A subject");

    // ── Tenant B ──
    provisioningService.provisionTenant(ORG_B, "Corr 586 Org B", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_B, "user_b_owner", "b_owner@test.com", "OwnerB", "owner");
    ownerB = TestJwtFactory.ownerJwt(ORG_B, "user_b_owner");
  }

  private void seedA(String messageId, String subject) {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaA)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx ->
                        correspondenceService.fileInbound(
                            new FileCorrespondenceCommand(
                                matterA,
                                clientA,
                                messageId,
                                subject,
                                "Body",
                                null,
                                "from@a.test",
                                null,
                                null,
                                null,
                                null,
                                null,
                                "MCP"),
                            new ActorContext(ownerAMemberId, "owner"))));
  }

  @Test
  void viewerWithNoMcpCapabilityCanReadProjectCorrespondence() throws Exception {
    // ownerA has NO MCP capability granted — proves the gate is plain project view-access, not MCP.
    mockMvc
        .perform(get("/api/projects/{id}/correspondence", matterA).with(ownerA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].subject").value("Tenant A subject"));
  }

  @Test
  void memberWithoutProjectViewAccessGets404() throws Exception {
    // ResourceNotFoundException -> 404 (security-by-obscurity) when the member can't view the
    // matter.
    mockMvc
        .perform(get("/api/projects/{id}/correspondence", matterA).with(outsiderA))
        .andExpect(status().isNotFound());
  }

  @Test
  void tenantBCannotReadTenantAProjectCorrespondence() throws Exception {
    // search_path isolation: matterA is invisible in tenant B -> requireViewAccess throws 404.
    mockMvc
        .perform(get("/api/projects/{id}/correspondence", matterA).with(ownerB))
        .andExpect(status().isNotFound());
  }

  @Test
  void tenantBCannotReadTenantACustomerCorrespondence() throws Exception {
    // clientA is invisible in tenant B -> existence check throws 404.
    mockMvc
        .perform(get("/api/customers/{id}/correspondence", clientA).with(ownerB))
        .andExpect(status().isNotFound());
  }
}
