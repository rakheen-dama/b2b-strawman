package io.b2mash.b2b.b2bstrawman.verticals.legal.tariff;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TariffControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_tariff_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TariffScheduleRepository scheduleRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Tariff Controller Test Org", null)
            .schemaName();
    syncMember(
        ORG_ID, "user_tariff_ctrl_owner", "tariff_ctrl@test.com", "Tariff Ctrl Owner", "owner");
    syncMember(
        ORG_ID,
        "user_tariff_ctrl_member",
        "tariff_ctrl_member@test.com",
        "Tariff Ctrl Member",
        "member");

    // Enable the lssa_tariff module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("lssa_tariff"));
                      orgSettingsRepository.save(settings);
                    }));
  }

  @Test
  void postSchedule_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/tariff-schedules")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "LSSA 2024/2025 High Court",
                      "category": "PARTY_AND_PARTY",
                      "courtLevel": "HIGH_COURT",
                      "effectiveFrom": "2024-04-01",
                      "effectiveTo": null,
                      "source": "LSSA Gazette 2024"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("LSSA 2024/2025 High Court"))
        .andExpect(jsonPath("$.category").value("PARTY_AND_PARTY"))
        .andExpect(jsonPath("$.courtLevel").value("HIGH_COURT"))
        .andExpect(jsonPath("$.isSystem").value(false))
        .andExpect(jsonPath("$.isActive").value(true));
  }

  @Test
  void postClone_createsCustomCopy() throws Exception {
    // Create a system schedule directly in the tenant
    final UUID[] scheduleId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var schedule =
                          new TariffSchedule(
                              "Clone Test System",
                              "ATTORNEY_AND_CLIENT",
                              "HIGH_COURT",
                              java.time.LocalDate.of(2024, 4, 1),
                              null,
                              "LSSA Official");
                      schedule.setSystem(true);
                      var item =
                          new TariffItem(
                              schedule,
                              "1(a)",
                              "Instructions",
                              "Taking instructions to sue",
                              new java.math.BigDecimal("500.00"),
                              "PER_ITEM",
                              null,
                              1);
                      schedule.getItems().add(item);
                      schedule = scheduleRepository.saveAndFlush(schedule);
                      scheduleId[0] = schedule.getId();
                    }));

    mockMvc
        .perform(post("/api/tariff-schedules/" + scheduleId[0] + "/clone").with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isSystem").value(false))
        .andExpect(jsonPath("$.name").value("Clone Test System (Copy)"))
        .andExpect(jsonPath("$.itemCount").value(1));
  }

  @Test
  void putSystemSchedule_returns400() throws Exception {
    // Create a system schedule directly
    final UUID[] scheduleId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var schedule =
                          new TariffSchedule(
                              "System Readonly",
                              "PARTY_AND_PARTY",
                              "HIGH_COURT",
                              java.time.LocalDate.of(2024, 1, 1),
                              null,
                              null);
                      schedule.setSystem(true);
                      schedule = scheduleRepository.saveAndFlush(schedule);
                      scheduleId[0] = schedule.getId();
                    }));

    mockMvc
        .perform(
            put("/api/tariff-schedules/" + scheduleId[0])
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Modified",
                      "category": "PARTY_AND_PARTY",
                      "courtLevel": "HIGH_COURT",
                      "effectiveFrom": "2024-01-01",
                      "isActive": true,
                      "source": null
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postItem_addsToSchedule() throws Exception {
    // Create a custom schedule via API
    var createResult =
        mockMvc
            .perform(
                post("/api/tariff-schedules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Item Test Schedule",
                          "category": "PARTY_AND_PARTY",
                          "courtLevel": "MAGISTRATE_COURT",
                          "effectiveFrom": "2024-04-01",
                          "source": null
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String scheduleId =
        com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/tariff-schedules/" + scheduleId + "/items")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "itemNumber": "1(a)",
                      "section": "Instructions",
                      "description": "Taking instructions to sue or defend",
                      "amount": 500.00,
                      "unit": "PER_ITEM",
                      "notes": null,
                      "sortOrder": 1
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.itemNumber").value("1(a)"))
        .andExpect(jsonPath("$.section").value("Instructions"))
        .andExpect(jsonPath("$.amount").value(500.00));
  }

  @Test
  void getItemsWithSearch_returnsMatching() throws Exception {
    // Create a schedule with items
    var createResult =
        mockMvc
            .perform(
                post("/api/tariff-schedules")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Search Items Schedule",
                          "category": "PARTY_AND_PARTY",
                          "courtLevel": "HIGH_COURT",
                          "effectiveFrom": "2024-04-01",
                          "source": null
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String scheduleId =
        com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Add items
    mockMvc
        .perform(
            post("/api/tariff-schedules/" + scheduleId + "/items")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "itemNumber": "1(a)",
                      "section": "Instructions",
                      "description": "Taking instructions to institute action",
                      "amount": 500.00,
                      "unit": "PER_ITEM",
                      "sortOrder": 1
                    }
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/tariff-schedules/" + scheduleId + "/items")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "itemNumber": "5(a)",
                      "section": "Appearances",
                      "description": "Attendance at court hearing",
                      "amount": 1200.00,
                      "unit": "PER_DAY",
                      "sortOrder": 2
                    }
                    """))
        .andExpect(status().isCreated());

    // Search for items containing "instructions"
    mockMvc
        .perform(
            get("/api/tariff-items")
                .with(ownerJwt())
                .param("scheduleId", scheduleId)
                .param("search", "instructions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].description").value("Taking instructions to institute action"));
  }

  @Test
  void postSchedule_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/tariff-schedules")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Should Fail",
                      "category": "PARTY_AND_PARTY",
                      "courtLevel": "HIGH_COURT",
                      "effectiveFrom": "2024-01-01"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_tariff_ctrl_owner")
                    .claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_tariff_ctrl_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }
}
