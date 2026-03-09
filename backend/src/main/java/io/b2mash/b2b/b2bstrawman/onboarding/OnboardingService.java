package io.b2mash.b2b.b2bstrawman.onboarding;

import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

  private final OrgSettingsRepository orgSettingsRepository;
  private final ProjectRepository projectRepository;
  private final CustomerRepository customerRepository;
  private final MemberRepository memberRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final BillingRateRepository billingRateRepository;
  private final InvoiceRepository invoiceRepository;

  public OnboardingService(
      OrgSettingsRepository orgSettingsRepository,
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      MemberRepository memberRepository,
      TimeEntryRepository timeEntryRepository,
      BillingRateRepository billingRateRepository,
      InvoiceRepository invoiceRepository) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
    this.memberRepository = memberRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.billingRateRepository = billingRateRepository;
    this.invoiceRepository = invoiceRepository;
  }

  @Transactional(readOnly = true)
  public OnboardingProgressResponse getProgress() {
    boolean dismissed =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(OrgSettings::isOnboardingDismissed)
            .orElse(false);

    var steps =
        List.of(
            new OnboardingStep("CREATE_PROJECT", projectRepository.count() > 0),
            new OnboardingStep("ADD_CUSTOMER", customerRepository.count() > 0),
            new OnboardingStep("INVITE_MEMBER", memberRepository.count() > 1),
            new OnboardingStep("LOG_TIME", timeEntryRepository.count() > 0),
            new OnboardingStep("SETUP_RATES", billingRateRepository.count() > 0),
            new OnboardingStep("CREATE_INVOICE", invoiceRepository.count() > 0));

    int completedCount = (int) steps.stream().filter(OnboardingStep::completed).count();

    return new OnboardingProgressResponse(steps, dismissed, completedCount, steps.size());
  }

  @Transactional
  public void dismiss() {
    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings("USD");
                  return orgSettingsRepository.save(newSettings);
                });

    settings.dismissOnboarding();
    orgSettingsRepository.save(settings);
  }
}
