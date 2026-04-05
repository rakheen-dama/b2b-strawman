package io.b2mash.b2b.b2bstrawman.deadline;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeadlineControllerTest {
  private static final String ENABLED_ORG_ID = "org_deadline_ctrl_enabled";
  private static final String DISABLED_ORG_ID = "org_deadline_ctrl_disabled";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String enabledTenantSchema;
  private UUID memberId;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    // Provision tenant with regulatory_deadlines module enabled
    enabledTenantSchema =
        provisioningService
            .provisionTenant(ENABLED_ORG_ID, "Deadline Ctrl Enabled Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ENABLED_ORG_ID,
                "user_dctrl_owner",
                "dctrl_owner@test.com",
                "DCtrl Owner",
                "owner"));
    TestMemberHelper.syncMember(
        mockMvc,
        ENABLED_ORG_ID,
        "user_dctrl_admin",
        "dctrl_admin@test.com",
        "DCtrl Admin",
        "admin");
    TestMemberHelper.syncMember(
        mockMvc,
        ENABLED_ORG_ID,
        "user_dctrl_member",
        "dctrl_member@test.com",
        "DCtrl Member",
        "member");

    // Enable the regulatory_deadlines module
    ScopedValue.where(RequestScopes.TENANT_ID, enabledTenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("regulatory_deadlines"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create a customer with FYE so deadlines can be calculated
    ScopedValue.where(RequestScopes.TENANT_ID, enabledTenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ID, ENABLED_ORG_ID)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          createActiveCustomer(
                              "Deadline Test Corp", "deadline-test@test.com", memberId);
                      customer.setCustomFields(Map.of("financial_year_end", "2025-02-28"));
                      customer = customerRepository.saveAndFlush(customer);
                      customerId = customer.getId();
                    }));

    // Provision tenant with no modules enabled (default empty list)
    provisioningService.provisionTenant(DISABLED_ORG_ID, "Deadline Ctrl Disabled Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        DISABLED_ORG_ID,
        "user_dctrl_dis_owner",
        "dctrl_dis@test.com",
        "DCtrl Dis Owner",
        "owner");
  }

  @Test
  void getDeadlines_returns200WithCalculatedDeadlines() throws Exception {
    mockMvc
        .perform(
            get("/api/deadlines")
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .with(TestJwtFactory.ownerJwt(ENABLED_ORG_ID, "user_dctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].customerId").exists())
        .andExpect(jsonPath("$[0].deadlineTypeSlug").exists())
        .andExpect(jsonPath("$[0].dueDate").exists())
        .andExpect(jsonPath("$[0].status").exists());
  }

  @Test
  void getDeadlineSummary_returns200WithAggregatedCounts() throws Exception {
    mockMvc
        .perform(
            get("/api/deadlines/summary")
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .with(TestJwtFactory.ownerJwt(ENABLED_ORG_ID, "user_dctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].month").exists())
        .andExpect(jsonPath("$[0].category").exists())
        .andExpect(jsonPath("$[0].total").exists());
  }

  @Test
  void getCustomerDeadlines_returns200ForSpecificCustomer() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/{id}/deadlines", customerId)
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .with(TestJwtFactory.ownerJwt(ENABLED_ORG_ID, "user_dctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].customerId").value(customerId.toString()));
  }

  @Test
  void putFilingStatus_adminRole_returns200() throws Exception {
    mockMvc
        .perform(
            put("/api/deadlines/filing-status")
                .with(TestJwtFactory.adminJwt(ENABLED_ORG_ID, "user_dctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "items": [
                        {
                          "customerId": "%s",
                          "deadlineTypeSlug": "sars_provisional_1",
                          "periodKey": "2026",
                          "status": "filed",
                          "notes": "Filed via test",
                          "linkedProjectId": null
                        }
                      ]
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].status").value("filed"))
        .andExpect(jsonPath("$[0].deadlineTypeSlug").value("sars_provisional_1"));
  }

  @Test
  void listFilingStatuses_returns200WithCreatedStatus() throws Exception {
    // First, create a filing status via PUT
    mockMvc
        .perform(
            put("/api/deadlines/filing-status")
                .with(TestJwtFactory.adminJwt(ENABLED_ORG_ID, "user_dctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "items": [
                        {
                          "customerId": "%s",
                          "deadlineTypeSlug": "sars_annual_return",
                          "periodKey": "2026",
                          "status": "filed",
                          "notes": "Created for GET test",
                          "linkedProjectId": null
                        }
                      ]
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isOk());

    // Then, list filing statuses via GET and verify the created one is present
    mockMvc
        .perform(
            get("/api/filing-statuses")
                .param("customerId", customerId.toString())
                .param("deadlineTypeSlug", "sars_annual_return")
                .with(TestJwtFactory.ownerJwt(ENABLED_ORG_ID, "user_dctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].customerId").value(customerId.toString()))
        .andExpect(jsonPath("$[0].deadlineTypeSlug").value("sars_annual_return"))
        .andExpect(jsonPath("$[0].status").value("filed"))
        .andExpect(jsonPath("$[0].periodKey").value("2026"));
  }

  @Test
  void putFilingStatus_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            put("/api/deadlines/filing-status")
                .with(TestJwtFactory.memberJwt(ENABLED_ORG_ID, "user_dctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "items": [
                        {
                          "customerId": "%s",
                          "deadlineTypeSlug": "sars_provisional_1",
                          "periodKey": "2026",
                          "status": "filed",
                          "notes": "Should fail",
                          "linkedProjectId": null
                        }
                      ]
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void getDeadlines_returns403_whenModuleDisabled() throws Exception {
    mockMvc
        .perform(
            get("/api/deadlines")
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .with(TestJwtFactory.ownerJwt(DISABLED_ORG_ID, "user_dctrl_dis_owner")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getDeadlines_returns400_whenMissingRequiredParams() throws Exception {
    mockMvc
        .perform(
            get("/api/deadlines").with(TestJwtFactory.ownerJwt(ENABLED_ORG_ID, "user_dctrl_owner")))
        .andExpect(status().isBadRequest());
  }
}
