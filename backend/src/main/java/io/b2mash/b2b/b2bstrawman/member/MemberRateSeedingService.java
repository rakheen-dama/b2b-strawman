package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.billingrate.BillingRate;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.costrate.CostRate;
import io.b2mash.b2b.b2bstrawman.costrate.CostRateRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry.RateCardDefaults;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry.RoleRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds per-member billing and cost rates from the tenant's vertical profile's {@code
 * rateCardDefaults} block. Called on JIT member creation so newly synced members immediately have
 * applicable rates for time tracking, invoicing, and profitability reporting.
 *
 * <p>Idempotent: if a member-default billing rate or cost rate already exists, the corresponding
 * insert is skipped. Safe to invoke multiple times for the same member.
 */
@Service
public class MemberRateSeedingService {

  private static final Logger log = LoggerFactory.getLogger(MemberRateSeedingService.class);

  private final OrgSettingsRepository orgSettingsRepository;
  private final VerticalProfileRegistry verticalProfileRegistry;
  private final BillingRateRepository billingRateRepository;
  private final CostRateRepository costRateRepository;

  public MemberRateSeedingService(
      OrgSettingsRepository orgSettingsRepository,
      VerticalProfileRegistry verticalProfileRegistry,
      BillingRateRepository billingRateRepository,
      CostRateRepository costRateRepository) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.verticalProfileRegistry = verticalProfileRegistry;
    this.billingRateRepository = billingRateRepository;
    this.costRateRepository = costRateRepository;
  }

  /**
   * Seeds MEMBER_DEFAULT billing and cost rates for the given member based on the tenant's vertical
   * profile. No-op if the tenant has no vertical profile, the profile has no {@code
   * rateCardDefaults}, no role entry matches the member's role slug, or rates already exist for the
   * member.
   */
  @Transactional
  public void seedDefaultRatesIfMissing(Member member) {
    if (member == null || member.getId() == null) {
      return;
    }
    String roleSlug = member.getOrgRole();
    if (roleSlug == null || roleSlug.isBlank()) {
      return;
    }

    var settingsOpt = orgSettingsRepository.findForCurrentTenant();
    if (settingsOpt.isEmpty()) {
      return;
    }
    String profileId = settingsOpt.get().getVerticalProfile();
    if (profileId == null || profileId.isBlank()) {
      return;
    }

    var profileOpt = verticalProfileRegistry.getProfile(profileId);
    if (profileOpt.isEmpty()) {
      return;
    }
    RateCardDefaults defaults = profileOpt.get().rateCardDefaults();
    if (defaults == null) {
      return;
    }

    String currency = defaults.currency();
    if (currency == null || currency.isBlank()) {
      return;
    }

    Optional<BigDecimal> billingRate = findRateForRole(defaults.billingRates(), roleSlug);
    if (billingRate.isPresent()
        && billingRateRepository.findMemberDefaultEarliest(member.getId()).isEmpty()) {
      var rate =
          new BillingRate(
              member.getId(), null, null, currency, billingRate.get(), LocalDate.now(), null);
      try {
        billingRateRepository.save(rate);
        log.info(
            "Seeded MEMBER_DEFAULT billing rate {} {} for member {} (role={}) from profile {}",
            billingRate.get(),
            currency,
            member.getId(),
            roleSlug,
            profileId);
      } catch (DataIntegrityViolationException concurrentInsert) {
        log.debug(
            "Concurrent insert won billing-rate seed race for member {} — idempotent no-op",
            member.getId());
      }
    }

    Optional<BigDecimal> costRate = findRateForRole(defaults.costRates(), roleSlug);
    if (costRate.isPresent() && costRateRepository.findByMemberId(member.getId()).isEmpty()) {
      var rate = new CostRate(member.getId(), currency, costRate.get(), LocalDate.now(), null);
      try {
        costRateRepository.save(rate);
        log.info(
            "Seeded cost rate {} {} for member {} (role={}) from profile {}",
            costRate.get(),
            currency,
            member.getId(),
            roleSlug,
            profileId);
      } catch (DataIntegrityViolationException concurrentInsert) {
        log.debug(
            "Concurrent insert won cost-rate seed race for member {} — idempotent no-op",
            member.getId());
      }
    }
  }

  private static Optional<BigDecimal> findRateForRole(List<RoleRate> rates, String roleSlug) {
    if (rates == null || rates.isEmpty() || roleSlug == null) {
      return Optional.empty();
    }
    for (RoleRate entry : rates) {
      if (entry.roleName() != null && entry.roleName().equalsIgnoreCase(roleSlug)) {
        return Optional.ofNullable(entry.hourlyRate());
      }
    }
    return Optional.empty();
  }
}
