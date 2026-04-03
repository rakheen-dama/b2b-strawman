package io.b2mash.b2b.b2bstrawman.demo;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoProvisionServiceTest {

  private static final String BASE_PATH = "/api/platform-admin/demo";

  @Autowired private MockMvc mockMvc;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;

  @MockitoBean private KeycloakAdminClient keycloakAdminClient;

  @Test
  void provision_createsKeycloakOrgAndUserAndSchema() throws Exception {
    when(keycloakAdminClient.findUserByEmail("demo@example.com")).thenReturn(Optional.empty());
    when(keycloakAdminClient.createUser(eq("demo@example.com"), eq("Demo"), eq("Admin"), any()))
        .thenReturn("kc-user-001");
    when(keycloakAdminClient.createOrganization(
            "Demo Accounting Firm", "demo-accounting-firm", "kc-user-001"))
        .thenReturn("kc-org-001");

    mockMvc
        .perform(
            post(BASE_PATH + "/provision")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationName": "Demo Accounting Firm",
                      "verticalProfile": "accounting",
                      "adminEmail": "demo@example.com",
                      "seedDemoData": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationSlug").value("demo-accounting-firm"))
        .andExpect(jsonPath("$.organizationName").value("Demo Accounting Firm"))
        .andExpect(jsonPath("$.verticalProfile").value("accounting"));

    verify(keycloakAdminClient)
        .createOrganization("Demo Accounting Firm", "demo-accounting-firm", "kc-user-001");
    verify(keycloakAdminClient).addMember("kc-org-001", "kc-user-001");
    verify(keycloakAdminClient).updateMemberRole("kc-org-001", "kc-user-001", "owner");
  }

  @Test
  void provision_setsSubscriptionToActivePilot() throws Exception {
    when(keycloakAdminClient.findUserByEmail("pilot@example.com"))
        .thenReturn(Optional.of("kc-user-002"));
    when(keycloakAdminClient.createOrganization("Pilot Org", "pilot-org", "kc-user-002"))
        .thenReturn("kc-org-002");

    mockMvc
        .perform(
            post(BASE_PATH + "/provision")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationName": "Pilot Org",
                      "verticalProfile": "legal",
                      "adminEmail": "pilot@example.com",
                      "seedDemoData": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.adminNote").exists());

    // Verify subscription was overridden to ACTIVE/PILOT
    var org = organizationRepository.findByExternalOrgId("pilot-org").orElseThrow();
    var sub = subscriptionRepository.findByOrganizationId(org.getId()).orElseThrow();
    assert sub.getSubscriptionStatus()
        == io.b2mash.b2b.b2bstrawman.billing.Subscription.SubscriptionStatus.ACTIVE;
    assert sub.getBillingMethod() == io.b2mash.b2b.b2bstrawman.billing.BillingMethod.PILOT;
  }

  @Test
  void provision_withExistingKeycloakUser_reusesUser() throws Exception {
    when(keycloakAdminClient.findUserByEmail("existing@example.com"))
        .thenReturn(Optional.of("kc-existing-user"));
    when(keycloakAdminClient.createOrganization(
            "Existing User Org", "existing-user-org", "kc-existing-user"))
        .thenReturn("kc-org-003");

    mockMvc
        .perform(
            post(BASE_PATH + "/provision")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationName": "Existing User Org",
                      "verticalProfile": "accounting",
                      "adminEmail": "existing@example.com",
                      "seedDemoData": false
                    }
                    """))
        .andExpect(status().isOk());

    // createUser should NOT have been called since findUserByEmail returned a user
    verify(keycloakAdminClient, never())
        .createUser(anyString(), anyString(), anyString(), anyString());
    verify(keycloakAdminClient).addMember("kc-org-003", "kc-existing-user");
  }

  @Test
  void provision_withSeedDemoDataFalse_returnsNotSeeded() throws Exception {
    when(keycloakAdminClient.findUserByEmail("noseed@example.com"))
        .thenReturn(Optional.of("kc-user-noseed"));
    when(keycloakAdminClient.createOrganization("No Seed Org", "no-seed-org", "kc-user-noseed"))
        .thenReturn("kc-org-noseed");

    mockMvc
        .perform(
            post(BASE_PATH + "/provision")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationName": "No Seed Org",
                      "verticalProfile": "accounting",
                      "adminEmail": "noseed@example.com",
                      "seedDemoData": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.demoDataSeeded").value(false));
  }

  @Test
  void provision_returnsCorrectLoginUrl() throws Exception {
    when(keycloakAdminClient.findUserByEmail("login@example.com"))
        .thenReturn(Optional.of("kc-user-login"));
    when(keycloakAdminClient.createOrganization("Login Url Org", "login-url-org", "kc-user-login"))
        .thenReturn("kc-org-login");

    mockMvc
        .perform(
            post(BASE_PATH + "/provision")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationName": "Login Url Org",
                      "verticalProfile": "legal",
                      "adminEmail": "login@example.com",
                      "seedDemoData": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loginUrl", containsString("login-url-org")));
  }

  @Test
  void provision_emitsAuditLog_noException() throws Exception {
    when(keycloakAdminClient.findUserByEmail("audit@example.com"))
        .thenReturn(Optional.of("kc-user-audit"));
    when(keycloakAdminClient.createOrganization("Audit Org", "audit-org", "kc-user-audit"))
        .thenReturn("kc-org-audit");

    // Verifies the full provisioning flow completes without exception,
    // including the audit log step
    mockMvc
        .perform(
            post(BASE_PATH + "/provision")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationName": "Audit Org",
                      "verticalProfile": "accounting",
                      "adminEmail": "audit@example.com",
                      "seedDemoData": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationId").exists());
  }

  @Test
  void provision_nonAdmin_returns403() throws Exception {
    mockMvc
        .perform(
            post(BASE_PATH + "/provision")
                .with(regularJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationName": "Forbidden Org",
                      "verticalProfile": "accounting",
                      "adminEmail": "forbidden@example.com",
                      "seedDemoData": false
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void provision_invalidEmail_returns400() throws Exception {
    mockMvc
        .perform(
            post(BASE_PATH + "/provision")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationName": "Bad Email Org",
                      "verticalProfile": "accounting",
                      "adminEmail": "not-an-email",
                      "seedDemoData": false
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_platform_admin").claim("groups", List.of("platform-admins")));
  }

  private JwtRequestPostProcessor regularJwt() {
    return jwt().jwt(j -> j.subject("user_regular"));
  }
}
