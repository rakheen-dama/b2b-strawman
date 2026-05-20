package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.dto.SyncEntryResponse;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.dto.SyncSummaryResponse;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingSyncControllerIntegrationTest {

  private static final String ORG_ID = "org_sync_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  @MockitoBean private AccountingSyncService syncService;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID syncViewerMemberId;
  private UUID integrationManagerMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Sync Ctrl Test Org", null);

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_sync_ctrl_owner",
                "sync_ctrl_owner@test.com",
                "Sync Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Assign system owner role
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember = memberRepository.findById(ownerMemberId).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);
            });

    // Member WITH INTEGRATION_VIEW_SYNC_STATUS but WITHOUT FINANCIAL_RECONCILE
    syncViewerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_sync_ctrl_viewer",
                "sync_ctrl_viewer@test.com",
                "Sync Viewer",
                "member"));

    // Member WITH INTEGRATION_MANAGE capability (for retry/resync write operations)
    integrationManagerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_sync_ctrl_mgr",
                "sync_ctrl_mgr@test.com",
                "Sync Manager",
                "member"));

    // Member WITHOUT any sync capabilities
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_sync_ctrl_nocap",
                "sync_ctrl_nocap@test.com",
                "Sync NoCap",
                "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              // Role with INTEGRATION_VIEW_SYNC_STATUS only (no FINANCIAL_RECONCILE, no
              // INTEGRATION_MANAGE)
              var syncViewerRole =
                  orgRoleService.createRole(
                      new OrgRoleDtos.CreateOrgRoleRequest(
                          "Sync Viewer",
                          "Can view sync status",
                          Set.of("INTEGRATION_VIEW_SYNC_STATUS")));
              var syncViewer = memberRepository.findById(syncViewerMemberId).orElseThrow();
              syncViewer.setOrgRoleEntity(
                  orgRoleRepository.findById(syncViewerRole.id()).orElseThrow());
              memberRepository.save(syncViewer);

              // Role with INTEGRATION_MANAGE for write operations (retry/resync)
              var integrationManagerRole =
                  orgRoleService.createRole(
                      new OrgRoleDtos.CreateOrgRoleRequest(
                          "Integration Manager",
                          "Can manage integrations",
                          Set.of("INTEGRATION_MANAGE")));
              var integrationManager =
                  memberRepository.findById(integrationManagerMemberId).orElseThrow();
              integrationManager.setOrgRoleEntity(
                  orgRoleRepository.findById(integrationManagerRole.id()).orElseThrow());
              memberRepository.save(integrationManager);

              // Role without any sync capabilities
              var noCapRole =
                  orgRoleService.createRole(
                      new OrgRoleDtos.CreateOrgRoleRequest(
                          "No Caps", "No sync caps", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(noCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });
  }

  @Test
  void getSummary_requiresSyncStatus_returns403ForNoCap() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations/sync/summary")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_nocap", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getSummary_syncViewerCanAccess() throws Exception {
    when(syncService.getSyncSummaryResponse())
        .thenReturn(new SyncSummaryResponse(3, 0, 42, 0, 0, 0, 0, null, null));

    mockMvc
        .perform(
            get("/api/integrations/sync/summary")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_viewer", "member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pending").value(3))
        .andExpect(jsonPath("$.completed").value(42));
  }

  // --- RBAC tests for GET endpoints (H-3) ---

  @Test
  void getEntries_returns403ForNoCap() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations/sync/entries")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_nocap", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getEntries_syncViewerCanAccess() throws Exception {
    when(syncService.getEntryResponses(any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(
            get("/api/integrations/sync/entries")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_viewer", "member")))
        .andExpect(status().isOk());
  }

  @Test
  void getEntryById_returns403ForNoCap() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations/sync/entries/" + UUID.randomUUID())
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_nocap", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getEntryById_syncViewerCanAccess() throws Exception {
    UUID entryId = UUID.randomUUID();
    var mockResponse =
        new SyncEntryResponse(
            entryId,
            SyncEntityType.INVOICE,
            UUID.randomUUID(),
            "xero",
            SyncDirection.PUSH,
            SyncState.PENDING,
            0,
            "KAZI-INV-test",
            null,
            null,
            null,
            SyncTrigger.EVENT,
            null,
            null);
    when(syncService.getEntryResponseById(entryId)).thenReturn(mockResponse);

    mockMvc
        .perform(
            get("/api/integrations/sync/entries/" + entryId)
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_viewer", "member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entityType").value("INVOICE"));
  }

  @Test
  void getInvoiceSyncStatus_returns403ForNoCap() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations/sync/invoice/" + UUID.randomUUID() + "/status")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_nocap", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getInvoiceSyncStatus_syncViewerCanAccess() throws Exception {
    UUID invoiceId = UUID.randomUUID();
    when(syncService.getInvoiceSyncStatusResponses(invoiceId)).thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/integrations/sync/invoice/" + invoiceId + "/status")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_viewer", "member")))
        .andExpect(status().isOk());
  }

  // --- Retry/resync require INTEGRATION_MANAGE (H-6) ---

  @Test
  void retry_requiresIntegrationManage_returns403ForNoCap() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/sync/" + UUID.randomUUID() + "/retry")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_nocap", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void retry_requiresIntegrationManage_returns403ForSyncViewer() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/sync/" + UUID.randomUUID() + "/retry")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_viewer", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void retry_integrationManagerCanAccess() throws Exception {
    UUID entryId = UUID.randomUUID();
    doNothing().when(syncService).retryFromDeadLetter(entryId);

    mockMvc
        .perform(
            post("/api/integrations/sync/" + entryId + "/retry")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_mgr", "member")))
        .andExpect(status().isNoContent());
  }

  @Test
  void reconcile_returns403ForIntegrationManager() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/sync/" + UUID.randomUUID() + "/reconcile")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_mgr", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void reconcile_requiresFinancialReconcile_returns403ForSyncViewer() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/sync/" + UUID.randomUUID() + "/reconcile")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_viewer", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void reconcile_ownerCanAccess() throws Exception {
    UUID entryId = UUID.randomUUID();
    doNothing().when(syncService).resolveReconcileDrift(entryId);

    mockMvc
        .perform(
            post("/api/integrations/sync/" + entryId + "/reconcile")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_sync_ctrl_owner")))
        .andExpect(status().isNoContent());
  }

  @Test
  void resync_requiresIntegrationManage_returns403ForNoCap() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/sync/invoice/" + UUID.randomUUID() + "/resync")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_nocap", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void resync_requiresIntegrationManage_returns403ForSyncViewer() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/sync/invoice/" + UUID.randomUUID() + "/resync")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_viewer", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void resync_integrationManagerCanAccess() throws Exception {
    UUID invoiceId = UUID.randomUUID();
    doNothing().when(syncService).enqueueInvoicePush(any(UUID.class), any(SyncTrigger.class));

    mockMvc
        .perform(
            post("/api/integrations/sync/invoice/" + invoiceId + "/resync")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_sync_ctrl_mgr", "member")))
        .andExpect(status().isNoContent());
  }
}
