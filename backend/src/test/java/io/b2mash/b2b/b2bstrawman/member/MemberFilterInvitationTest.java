package io.b2mash.b2b.b2bstrawman.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.invitation.InvitationStatus;
import io.b2mash.b2b.b2bstrawman.invitation.PendingInvitation;
import io.b2mash.b2b.b2bstrawman.invitation.PendingInvitationRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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

/**
 * Integration tests verifying that MemberFilter resolves roles from pending invitations when
 * lazy-creating members.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberFilterInvitationTest {

  private static final String ORG_ID = "org_inv_flow_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private PendingInvitationRepository invitationRepository;

  private String schemaName;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invitation Flow Test Org", null);
    schemaName =
        mappingRepository
            .findByClerkOrgId(ORG_ID)
            .map(OrgSchemaMapping::getSchemaName)
            .orElseThrow();

    // Create an owner member to use as invitedBy
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_inv_flow_owner", "inv_flow_owner@test.com", "Inv Owner", "owner");
  }

  @Test
  void shouldAssignInvitationRoleOnFirstLogin() throws Exception {
    // Create a pending invitation for admin role
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              OrgRole adminRole = orgRoleRepository.findBySlug("admin").orElseThrow();
              Member owner =
                  memberRepository.findByClerkUserId("user_inv_flow_owner").orElseThrow();
              var invitation =
                  new PendingInvitation(
                      "invited_admin@test.com",
                      adminRole,
                      owner,
                      Instant.now().plus(7, ChronoUnit.DAYS));
              invitationRepository.save(invitation);
            });

    // User logs in for the first time with the invited email
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_invited_admin")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "member"))
                                    .claim("email", "invited_admin@test.com"))))
        .andExpect(status().isOk());

    // Verify the member was created with the invitation's admin role
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_invited_admin");
              assertThat(member).isPresent();
              assertThat(member.get().getOrgRole()).isEqualTo("admin");
            });
  }

  @Test
  void shouldMarkInvitationAsAcceptedAfterMemberCreation() throws Exception {
    // Create a pending invitation
    UUID[] invitationId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              OrgRole memberRole = orgRoleRepository.findBySlug("member").orElseThrow();
              Member owner =
                  memberRepository.findByClerkUserId("user_inv_flow_owner").orElseThrow();
              var invitation =
                  new PendingInvitation(
                      "invited_accepted@test.com",
                      memberRole,
                      owner,
                      Instant.now().plus(7, ChronoUnit.DAYS));
              invitation = invitationRepository.save(invitation);
              invitationId[0] = invitation.getId();
            });

    // User logs in
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_invited_accepted")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "member"))
                                    .claim("email", "invited_accepted@test.com"))))
        .andExpect(status().isOk());

    // Verify the invitation is now ACCEPTED
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var invitation = invitationRepository.findById(invitationId[0]);
              assertThat(invitation).isPresent();
              assertThat(invitation.get().getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            });
  }

  @Test
  void shouldFallbackToMemberRoleWithExpiredInvitation() throws Exception {
    // Create an expired invitation
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              OrgRole adminRole = orgRoleRepository.findBySlug("admin").orElseThrow();
              Member owner =
                  memberRepository.findByClerkUserId("user_inv_flow_owner").orElseThrow();
              var invitation =
                  new PendingInvitation(
                      "invited_expired@test.com",
                      adminRole,
                      owner,
                      Instant.now().minus(1, ChronoUnit.DAYS)); // Already expired
              invitationRepository.save(invitation);
            });

    // User logs in with an expired invitation
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_invited_expired")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "member"))
                                    .claim("email", "invited_expired@test.com"))))
        .andExpect(status().isOk());

    // Verify the member gets default "member" role, not "admin" from expired invitation
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_invited_expired");
              assertThat(member).isPresent();
              assertThat(member.get().getOrgRole()).isEqualTo("member");
            });
  }

  @Test
  void shouldGetDefaultMemberRoleWithoutInvitation() throws Exception {
    // User logs in without any invitation
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_no_invitation")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "admin"))
                                    .claim("email", "no_invitation@test.com"))))
        .andExpect(status().isOk());

    // Verify the member gets default "member" role
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_no_invitation");
              assertThat(member).isPresent();
              assertThat(member.get().getOrgRole()).isEqualTo("member");
            });
  }
}
