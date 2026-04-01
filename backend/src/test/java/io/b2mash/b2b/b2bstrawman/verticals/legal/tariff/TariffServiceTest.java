package io.b2mash.b2b.b2bstrawman.verticals.legal.tariff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ModuleNotEnabledException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffService.CreateItemRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffService.CreateScheduleRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffService.UpdateScheduleRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TariffServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_tariff_svc_test";
  private static final String DISABLED_ORG_ID = "org_tariff_svc_disabled";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TariffService tariffService;
  @Autowired private TariffScheduleRepository scheduleRepository;

  private String tenantSchema;
  private String disabledTenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    // Provision enabled tenant
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Tariff Service Test Org", null).schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_tariff_svc_owner",
                "tariff_svc@test.com",
                "Tariff Svc Owner",
                "owner"));

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

    // Provision disabled tenant (no modules enabled)
    disabledTenantSchema =
        provisioningService
            .provisionTenant(DISABLED_ORG_ID, "Tariff Disabled Org", null)
            .schemaName();
    planSyncService.syncPlan(DISABLED_ORG_ID, "pro-plan");
    syncMember(
        DISABLED_ORG_ID,
        "user_tariff_svc_dis",
        "tariff_dis@test.com",
        "Tariff Disabled Owner",
        "owner");
  }

  @Test
  void createSchedule_savesWithIsSystemFalse() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateScheduleRequest(
                          "LSSA 2024/2025 High Court",
                          "PARTY_AND_PARTY",
                          "HIGH_COURT",
                          LocalDate.of(2024, 4, 1),
                          null,
                          "LSSA Gazette 2024");

                  var response = tariffService.createSchedule(request);

                  assertThat(response.id()).isNotNull();
                  assertThat(response.name()).isEqualTo("LSSA 2024/2025 High Court");
                  assertThat(response.category()).isEqualTo("PARTY_AND_PARTY");
                  assertThat(response.courtLevel()).isEqualTo("HIGH_COURT");
                  assertThat(response.isSystem()).isFalse();
                  assertThat(response.isActive()).isTrue();
                }));
  }

  @Test
  void updateSchedule_onSystemSchedule_throwsInvalidState() {
    // Create a system schedule in one transaction
    final UUID[] scheduleId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var schedule =
                      new TariffSchedule(
                          "System Schedule",
                          "PARTY_AND_PARTY",
                          "HIGH_COURT",
                          LocalDate.of(2024, 1, 1),
                          null,
                          "LSSA Official");
                  schedule.setSystem(true);
                  schedule = scheduleRepository.saveAndFlush(schedule);
                  scheduleId[0] = schedule.getId();
                }));

    // Attempt update outside transaction -- service method has its own @Transactional
    runInTenant(
        () -> {
          var updateRequest =
              new UpdateScheduleRequest(
                  "Modified Name",
                  "PARTY_AND_PARTY",
                  "HIGH_COURT",
                  LocalDate.of(2024, 1, 1),
                  null,
                  true,
                  "Modified Source");

          assertThatThrownBy(() -> tariffService.updateSchedule(scheduleId[0], updateRequest))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void cloneSchedule_createsDeepCopyWithIsSystemFalse() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create a system schedule with items
                  var schedule =
                      new TariffSchedule(
                          "Clone Source Schedule",
                          "ATTORNEY_AND_CLIENT",
                          "MAGISTRATE_COURT",
                          LocalDate.of(2024, 4, 1),
                          LocalDate.of(2025, 3, 31),
                          "LSSA Gazette");
                  schedule.setSystem(true);

                  var item1 =
                      new TariffItem(
                          schedule,
                          "1(a)",
                          "Instructions",
                          "Taking instructions to sue or defend",
                          new BigDecimal("500.00"),
                          "PER_ITEM",
                          null,
                          1);
                  var item2 =
                      new TariffItem(
                          schedule,
                          "2(a)",
                          "Pleadings",
                          "Drawing and filing summons",
                          new BigDecimal("350.00"),
                          "PER_ITEM",
                          "Includes all copies",
                          2);
                  schedule.getItems().add(item1);
                  schedule.getItems().add(item2);
                  schedule = scheduleRepository.saveAndFlush(schedule);

                  var cloned = tariffService.cloneSchedule(schedule.getId());

                  assertThat(cloned.id()).isNotEqualTo(schedule.getId());
                  assertThat(cloned.name()).isEqualTo("Clone Source Schedule (Copy)");
                  assertThat(cloned.isSystem()).isFalse();
                  assertThat(cloned.category()).isEqualTo("ATTORNEY_AND_CLIENT");
                  assertThat(cloned.itemCount()).isEqualTo(2);
                }));
  }

  @Test
  void createItem_onSystemSchedule_throwsInvalidState() {
    // Create a system schedule in one transaction
    final UUID[] scheduleId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var schedule =
                      new TariffSchedule(
                          "System No Items",
                          "PARTY_AND_PARTY",
                          "HIGH_COURT",
                          LocalDate.of(2024, 1, 1),
                          null,
                          null);
                  schedule.setSystem(true);
                  schedule = scheduleRepository.saveAndFlush(schedule);
                  scheduleId[0] = schedule.getId();
                }));

    // Attempt add item outside transaction -- service method has its own @Transactional
    runInTenant(
        () -> {
          var itemRequest =
              new CreateItemRequest(
                  "1(a)",
                  "Instructions",
                  "Taking instructions",
                  new BigDecimal("500.00"),
                  "PER_ITEM",
                  null,
                  1);

          assertThatThrownBy(() -> tariffService.createItem(scheduleId[0], itemRequest))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void getActiveSchedule_returnsCorrectSchedule() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create active schedule
                  var active =
                      new TariffSchedule(
                          "Active Schedule",
                          "PARTY_AND_PARTY",
                          "CONSTITUTIONAL_COURT",
                          LocalDate.of(2024, 4, 1),
                          null,
                          null);
                  scheduleRepository.saveAndFlush(active);

                  // Create inactive schedule for same category/courtLevel
                  var inactive =
                      new TariffSchedule(
                          "Inactive Schedule",
                          "PARTY_AND_PARTY",
                          "CONSTITUTIONAL_COURT",
                          LocalDate.of(2023, 4, 1),
                          LocalDate.of(2024, 3, 31),
                          null);
                  inactive.setActive(false);
                  scheduleRepository.saveAndFlush(inactive);

                  var results =
                      tariffService.getActiveSchedule("PARTY_AND_PARTY", "CONSTITUTIONAL_COURT");

                  assertThat(results).isNotEmpty();
                  assertThat(results).allSatisfy(r -> assertThat(r.isActive()).isTrue());
                  assertThat(results.stream().map(s -> s.name()).toList())
                      .contains("Active Schedule");
                  assertThat(results.stream().map(s -> s.name()).toList())
                      .doesNotContain("Inactive Schedule");
                }));
  }

  @Test
  void searchItems_returnsMatchingItems() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create schedule with items
                  var request =
                      new CreateScheduleRequest(
                          "Search Test Schedule",
                          "PARTY_AND_PARTY",
                          "HIGH_COURT",
                          LocalDate.of(2024, 4, 1),
                          null,
                          null);
                  var schedule = tariffService.createSchedule(request);

                  tariffService.createItem(
                      schedule.id(),
                      new CreateItemRequest(
                          "1(a)",
                          "Instructions",
                          "Taking instructions to sue or defend",
                          new BigDecimal("500.00"),
                          "PER_ITEM",
                          null,
                          1));
                  tariffService.createItem(
                      schedule.id(),
                      new CreateItemRequest(
                          "2(a)",
                          "Pleadings",
                          "Drawing and filing summons",
                          new BigDecimal("350.00"),
                          "PER_ITEM",
                          null,
                          2));
                  tariffService.createItem(
                      schedule.id(),
                      new CreateItemRequest(
                          "3(a)",
                          "Appearances",
                          "Attendance at court for hearing",
                          new BigDecimal("1200.00"),
                          "PER_DAY",
                          null,
                          3));

                  var results = tariffService.searchItems(schedule.id(), "instructions", null);

                  assertThat(results).isNotEmpty();
                  assertThat(results.getFirst().description()).containsIgnoringCase("instructions");
                }));
  }

  @Test
  void createSchedule_throwsModuleNotEnabled_whenModuleDisabled() {
    ScopedValue.where(RequestScopes.TENANT_ID, disabledTenantSchema)
        .where(RequestScopes.ORG_ID, DISABLED_ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var request =
                  new CreateScheduleRequest(
                      "Should Fail",
                      "PARTY_AND_PARTY",
                      "HIGH_COURT",
                      LocalDate.of(2024, 1, 1),
                      null,
                      null);

              assertThatThrownBy(() -> tariffService.createSchedule(request))
                  .isInstanceOf(ModuleNotEnabledException.class);
            });
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

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
}
