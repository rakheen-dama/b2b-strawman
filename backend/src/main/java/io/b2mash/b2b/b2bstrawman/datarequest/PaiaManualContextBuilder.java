package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicyRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateContextHelper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the rendering context for a PAIA Section 51 manual. This is a standalone builder — it
 * does NOT implement {@code TemplateContextBuilder} because the PAIA manual is org-level (no entity
 * ID required).
 */
@Component
public class PaiaManualContextBuilder {

  private final OrgSettingsRepository orgSettingsRepository;
  private final RetentionPolicyRepository retentionPolicyRepository;
  private final ProcessingActivityRepository processingActivityRepository;
  private final TemplateContextHelper contextHelper;

  public PaiaManualContextBuilder(
      OrgSettingsRepository orgSettingsRepository,
      RetentionPolicyRepository retentionPolicyRepository,
      ProcessingActivityRepository processingActivityRepository,
      TemplateContextHelper contextHelper) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.retentionPolicyRepository = retentionPolicyRepository;
    this.processingActivityRepository = processingActivityRepository;
    this.contextHelper = contextHelper;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> buildContext() {
    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseThrow(() -> new ResourceNotFoundException("OrgSettings", "current tenant"));

    var context = new LinkedHashMap<String, Object>();
    var orgMap = contextHelper.buildOrgContext();
    context.put("org", orgMap);
    context.put("orgName", orgMap.get("name"));
    context.put("informationOfficerName", settings.getInformationOfficerName());
    context.put("informationOfficerEmail", settings.getInformationOfficerEmail());
    context.put("jurisdiction", settings.getDataProtectionJurisdiction());
    context.put("retentionPolicies", retentionPolicyRepository.findByActive(true));
    context.put("processingActivities", processingActivityRepository.findAll());
    context.put("generatedDate", LocalDate.now().toString());
    return context;
  }
}
