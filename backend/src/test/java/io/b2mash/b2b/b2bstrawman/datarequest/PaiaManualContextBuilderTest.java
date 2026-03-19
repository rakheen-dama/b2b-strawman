package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicy;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicyRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateContextHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaiaManualContextBuilderTest {

  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private RetentionPolicyRepository retentionPolicyRepository;
  @Mock private ProcessingActivityRepository processingActivityRepository;
  @Mock private TemplateContextHelper contextHelper;

  private PaiaManualContextBuilder builder;

  @BeforeEach
  void setUp() {
    builder =
        new PaiaManualContextBuilder(
            orgSettingsRepository,
            retentionPolicyRepository,
            processingActivityRepository,
            contextHelper);
  }

  @Test
  void buildContext_includesOrgNameFromContextHelper() {
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test")
        .run(
            () -> {
              var orgMap = new LinkedHashMap<String, Object>();
              orgMap.put("name", "Test Organization");
              when(contextHelper.buildOrgContext()).thenReturn(orgMap);
              when(orgSettingsRepository.findForCurrentTenant())
                  .thenReturn(Optional.of(createSettings()));
              when(retentionPolicyRepository.findByActive(true)).thenReturn(List.of());
              when(processingActivityRepository.findAll()).thenReturn(List.of());

              Map<String, Object> context = builder.buildContext();

              assertThat(context.get("orgName")).isEqualTo("Test Organization");
              assertThat(context.get("org")).isEqualTo(orgMap);
            });
  }

  @Test
  void buildContext_includesInformationOfficerFields() {
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test")
        .run(
            () -> {
              when(contextHelper.buildOrgContext()).thenReturn(new LinkedHashMap<>());
              var settings = createSettings();
              when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(settings));
              when(retentionPolicyRepository.findByActive(true)).thenReturn(List.of());
              when(processingActivityRepository.findAll()).thenReturn(List.of());

              Map<String, Object> context = builder.buildContext();

              assertThat(context.get("informationOfficerName")).isEqualTo("Jane Doe");
              assertThat(context.get("informationOfficerEmail")).isEqualTo("jane@example.com");
            });
  }

  @Test
  void buildContext_includesRetentionPolicies() {
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test")
        .run(
            () -> {
              when(contextHelper.buildOrgContext()).thenReturn(new LinkedHashMap<>());
              when(orgSettingsRepository.findForCurrentTenant())
                  .thenReturn(Optional.of(createSettings()));

              var policy =
                  new RetentionPolicy("Financial Records", 60, "End of financial year", "ARCHIVE");
              when(retentionPolicyRepository.findByActive(true)).thenReturn(List.of(policy));
              when(processingActivityRepository.findAll()).thenReturn(List.of());

              Map<String, Object> context = builder.buildContext();

              @SuppressWarnings("unchecked")
              var policies = (List<RetentionPolicy>) context.get("retentionPolicies");
              assertThat(policies).hasSize(1);
              assertThat(policies.getFirst().getRecordType()).isEqualTo("Financial Records");
            });
  }

  @Test
  void buildContext_includesProcessingActivities() {
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test")
        .run(
            () -> {
              when(contextHelper.buildOrgContext()).thenReturn(new LinkedHashMap<>());
              when(orgSettingsRepository.findForCurrentTenant())
                  .thenReturn(Optional.of(createSettings()));
              when(retentionPolicyRepository.findByActive(true)).thenReturn(List.of());

              var activity =
                  new ProcessingActivity(
                      "HR Data", "Employee records", "CONSENT", "Employees", "36 months", null);
              when(processingActivityRepository.findAll()).thenReturn(List.of(activity));

              Map<String, Object> context = builder.buildContext();

              @SuppressWarnings("unchecked")
              var activities = (List<ProcessingActivity>) context.get("processingActivities");
              assertThat(activities).hasSize(1);
              assertThat(activities.getFirst().getCategory()).isEqualTo("HR Data");
            });
  }

  private OrgSettings createSettings() {
    var settings = new OrgSettings("ZAR");
    settings.updateDataProtectionSettings("ZA", true, 36, 60, "Jane Doe", "jane@example.com");
    return settings;
  }
}
