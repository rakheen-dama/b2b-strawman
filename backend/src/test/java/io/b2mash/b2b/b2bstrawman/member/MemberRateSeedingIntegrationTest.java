package io.b2mash.b2b.b2bstrawman.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRate;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.costrate.CostRate;
import io.b2mash.b2b.b2bstrawman.costrate.CostRateRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for {@link MemberRateSeedingService} covering GAP-C-05 + GAP-C-06: newly synced
 * members on consulting-za tenants must receive MEMBER_DEFAULT billing and cost rates mapped from
 * the profile's {@code rateCardDefaults} block.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberRateSeedingIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_CONSULTING = "org_mrs_consulting";
  private static final String ORG_LEGAL = "org_mrs_legal";
  private static final String ORG_NO_PROFILE = "org_mrs_no_profile";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private BillingRateRepository billingRateRepository;
  @Autowired private CostRateRepository costRateRepository;

  @Test
  void consultingZaOwnerSyncSeedsOwnerRates() throws Exception {
    provisioningService.provisionTenant(ORG_CONSULTING, "MRS Consulting", "consulting-za");

    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_CONSULTING, "user_mrs_owner", "owner@mrs.test", "MRS Owner", "owner");

    UUID memberUuid = UUID.fromString(memberId);
    String schemaName = schemaFor(ORG_CONSULTING);

    List<BillingRate> billingRates =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
            .call(() -> billingRateRepository.findMemberDefaultEarliest(memberUuid));
    assertThat(billingRates).isNotEmpty();
    var billing = billingRates.get(0);
    assertThat(billing.getHourlyRate()).isEqualByComparingTo(new BigDecimal("1800"));
    assertThat(billing.getCurrency()).isEqualTo("ZAR");
    assertThat(billing.getMemberId()).isEqualTo(memberUuid);
    assertThat(billing.getProjectId()).isNull();
    assertThat(billing.getCustomerId()).isNull();

    List<CostRate> costRates =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
            .call(() -> costRateRepository.findByMemberId(memberUuid));
    assertThat(costRates).hasSize(1);
    var cost = costRates.get(0);
    assertThat(cost.getHourlyCost()).isEqualByComparingTo(new BigDecimal("850"));
    assertThat(cost.getCurrency()).isEqualTo("ZAR");
    assertThat(cost.getMemberId()).isEqualTo(memberUuid);
  }

  @Test
  void consultingZaAdminAndMemberSyncSeedsCorrectRolesRates() throws Exception {
    // Use the same provisioned tenant — different users
    provisioningService.provisionTenant(ORG_CONSULTING, "MRS Consulting", "consulting-za");

    String adminId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_CONSULTING, "user_mrs_admin", "admin@mrs.test", "Admin", "admin");
    String memberSlugId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_CONSULTING, "user_mrs_member", "member@mrs.test", "Member", "member");

    UUID adminUuid = UUID.fromString(adminId);
    UUID memberUuid = UUID.fromString(memberSlugId);
    String schemaName = schemaFor(ORG_CONSULTING);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var adminBilling = billingRateRepository.findMemberDefaultEarliest(adminUuid);
              assertThat(adminBilling).isNotEmpty();
              assertThat(adminBilling.get(0).getHourlyRate())
                  .isEqualByComparingTo(new BigDecimal("1200"));
              var adminCost = costRateRepository.findByMemberId(adminUuid);
              assertThat(adminCost).hasSize(1);
              assertThat(adminCost.get(0).getHourlyCost())
                  .isEqualByComparingTo(new BigDecimal("550"));

              var memberBilling = billingRateRepository.findMemberDefaultEarliest(memberUuid);
              assertThat(memberBilling).isNotEmpty();
              assertThat(memberBilling.get(0).getHourlyRate())
                  .isEqualByComparingTo(new BigDecimal("750"));
              var memberCost = costRateRepository.findByMemberId(memberUuid);
              assertThat(memberCost).hasSize(1);
              assertThat(memberCost.get(0).getHourlyCost())
                  .isEqualByComparingTo(new BigDecimal("375"));
            });
  }

  @Test
  void resyncingExistingMemberDoesNotDuplicateRates() throws Exception {
    provisioningService.provisionTenant(ORG_CONSULTING, "MRS Consulting", "consulting-za");

    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_CONSULTING, "user_mrs_idem", "idem@mrs.test", "Idempotent Owner", "owner");

    // Re-sync the same user — identity update should not add extra rates
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_mrs_idem",
                      "email": "idem-new@mrs.test",
                      "name": "Idempotent Owner",
                      "orgRole": "owner"
                    }
                    """
                        .formatted(ORG_CONSULTING)))
        .andExpect(status().isOk());

    UUID memberUuid = UUID.fromString(memberId);
    String schemaName = schemaFor(ORG_CONSULTING);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var billing = billingRateRepository.findMemberDefaultEarliest(memberUuid);
              assertThat(billing).hasSize(1);
              var cost = costRateRepository.findByMemberId(memberUuid);
              assertThat(cost).hasSize(1);
            });
  }

  @Test
  void tenantWithoutVerticalProfileDoesNotSeedRates() throws Exception {
    provisioningService.provisionTenant(ORG_NO_PROFILE, "No Profile Org", null);

    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_NO_PROFILE, "user_np_owner", "np@test.com", "NP Owner", "owner");

    UUID memberUuid = UUID.fromString(memberId);
    String schemaName = schemaFor(ORG_NO_PROFILE);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              assertThat(billingRateRepository.findMemberDefaultEarliest(memberUuid)).isEmpty();
              assertThat(costRateRepository.findByMemberId(memberUuid)).isEmpty();
            });
  }

  @Test
  void tenantOnProfileWithoutRateCardDefaultsDoesNotSeedRates() throws Exception {
    // legal-za has no rateCardDefaults block — profile loads, but no seeding happens.
    provisioningService.provisionTenant(ORG_LEGAL, "MRS Legal", "legal-za");

    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_LEGAL, "user_mrs_legal_owner", "legal@test.com", "Legal Owner", "owner");

    UUID memberUuid = UUID.fromString(memberId);
    String schemaName = schemaFor(ORG_LEGAL);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              assertThat(billingRateRepository.findMemberDefaultEarliest(memberUuid)).isEmpty();
              assertThat(costRateRepository.findByMemberId(memberUuid)).isEmpty();
            });
  }

  private static String schemaFor(String clerkOrgId) {
    return io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator.generateSchemaName(
        clerkOrgId);
  }
}
