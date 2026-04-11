package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChecklistInstantiationService {

  private static final Logger log = LoggerFactory.getLogger(ChecklistInstantiationService.class);

  private final ChecklistTemplateRepository templateRepository;
  private final ChecklistInstanceRepository instanceRepository;
  private final ChecklistInstanceService instanceService;

  public ChecklistInstantiationService(
      ChecklistTemplateRepository templateRepository,
      ChecklistInstanceRepository instanceRepository,
      @Lazy ChecklistInstanceService instanceService) {
    this.templateRepository = templateRepository;
    this.instanceRepository = instanceRepository;
    this.instanceService = instanceService;
  }

  @Transactional
  public List<ChecklistInstance> instantiateForCustomer(Customer customer) {
    UUID customerId = customer.getId();
    String customerType = customer.getCustomerType().name();

    // Match templates for this customer type AND the ANY wildcard type
    var matchingTemplates =
        templateRepository.findByActiveAndAutoInstantiateAndCustomerTypeIn(
            true, true, List.of(customerType, "ANY"));

    // Most-specific-match (GAP-S5-05): if any template matches the exact customer type, skip
    // the ANY fallback entirely. This enforces PR #996's intent — typed packs
    // (legal-za-individual-onboarding / legal-za-trust-onboarding) fully replace the legacy
    // ANY pack (legal-za-client-onboarding), and mixing a typed pack with an ANY pack has no
    // valid product use case today. Any future universal pack (e.g., sanctions screening)
    // should be modelled via explicit typed variants rather than an ANY fallback.
    boolean hasTypedTemplate =
        matchingTemplates.stream().anyMatch(t -> customerType.equals(t.getCustomerType()));
    var finalTemplates =
        hasTypedTemplate
            ? matchingTemplates.stream()
                .filter(t -> customerType.equals(t.getCustomerType()))
                .toList()
            : matchingTemplates;

    List<ChecklistInstance> created = new ArrayList<>();
    for (var template : finalTemplates) {
      if (instanceRepository.existsByCustomerIdAndTemplateId(customerId, template.getId())) {
        log.debug(
            "Skipping template '{}' — instance already exists for customer {}",
            template.getName(),
            customerId);
        continue;
      }
      var instance = instanceService.createFromTemplate(template.getId(), customerId, customer);
      created.add(instance);
      log.info("Auto-instantiated checklist '{}' for customer {}", template.getName(), customerId);
    }

    log.info(
        "Instantiated {} checklist(s) for customer {} (type={}, typedAvailable={})",
        created.size(),
        customerId,
        customerType,
        hasTypedTemplate);
    return created;
  }

  @Transactional
  public int cancelActiveInstances(UUID customerId) {
    var instances = instanceRepository.findByCustomerId(customerId);
    int count = 0;
    for (var instance : instances) {
      if ("IN_PROGRESS".equals(instance.getStatus())) {
        instance.cancel();
        instanceRepository.save(instance);
        count++;
        log.info("Cancelled checklist instance {} for customer {}", instance.getId(), customerId);
      }
    }
    log.info("Cancelled {} active checklist instance(s) for customer {}", count, customerId);
    return count;
  }
}
