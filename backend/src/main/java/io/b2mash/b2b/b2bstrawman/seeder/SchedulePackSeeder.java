package io.b2mash.b2b.b2bstrawman.seeder;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.schedule.RecurringSchedule;
import io.b2mash.b2b.b2bstrawman.schedule.RecurringScheduleRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds recurring schedules in PAUSED state from schedule pack JSON definitions. Seeded schedules
 * have no customer assigned (customer_id is NULL) and must be configured by the tenant before
 * activation.
 */
@Service
public class SchedulePackSeeder extends AbstractPackSeeder<SchedulePackDefinition> {

  private static final String PACK_LOCATION = "classpath:schedule-packs/*.json";

  /**
   * Sentinel UUID used as createdBy for seeder-created recurring schedules. Distinguishable from
   * real member IDs. Public so tests outside this package can reference it directly.
   */
  public static final UUID SEEDER_CREATED_BY =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final RecurringScheduleRepository recurringScheduleRepository;
  private final ProjectTemplateRepository projectTemplateRepository;

  public SchedulePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      RecurringScheduleRepository recurringScheduleRepository,
      ProjectTemplateRepository projectTemplateRepository) {
    super(resourceResolver, objectMapper, orgSettingsRepository, tenantTransactionHelper);
    this.recurringScheduleRepository = recurringScheduleRepository;
    this.projectTemplateRepository = projectTemplateRepository;
  }

  @Override
  protected String getPackResourcePattern() {
    return PACK_LOCATION;
  }

  @Override
  protected Class<SchedulePackDefinition> getPackDefinitionType() {
    return SchedulePackDefinition.class;
  }

  @Override
  protected String getPackTypeName() {
    return "schedule";
  }

  @Override
  protected String getPackId(SchedulePackDefinition pack) {
    return pack.packId();
  }

  @Override
  protected String getPackVersion(SchedulePackDefinition pack) {
    return String.valueOf(pack.version());
  }

  @Override
  protected String getVerticalProfile(SchedulePackDefinition pack) {
    return pack.verticalProfile();
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getSchedulePackStatus() == null) {
      return false;
    }
    return settings.getSchedulePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, SchedulePackDefinition pack) {
    settings.recordSchedulePackApplication(pack.packId(), pack.version());
  }

  private static final Set<String> VALID_FREQUENCIES =
      Set.of("WEEKLY", "FORTNIGHTLY", "MONTHLY", "QUARTERLY", "SEMI_ANNUALLY", "ANNUALLY");

  @Override
  protected void applyPack(SchedulePackDefinition pack, Resource packResource, String tenantId) {
    // Load all templates once for O(1) lookup per entry
    Map<String, ProjectTemplate> templatesByName =
        projectTemplateRepository.findAllByOrderByNameAsc().stream()
            .collect(Collectors.toMap(ProjectTemplate::getName, Function.identity(), (a, b) -> a));

    for (SchedulePackDefinition.ScheduleEntry entry : pack.schedules()) {
      // Validate recurrence value
      if (!VALID_FREQUENCIES.contains(entry.recurrence())) {
        log.warn(
            "Invalid recurrence '{}' for schedule '{}' in pack {} — skipping entry",
            entry.recurrence(),
            entry.name(),
            pack.packId());
        continue;
      }

      var template = templatesByName.get(entry.projectTemplateName());
      if (template == null) {
        log.warn(
            "Project template '{}' not found for schedule '{}' in pack {} — skipping entry",
            entry.projectTemplateName(),
            entry.name(),
            pack.packId());
        continue;
      }

      var schedule =
          new RecurringSchedule(
              template.getId(),
              null, // customerId — null for seeded template schedules
              entry.name(),
              entry.recurrence(),
              LocalDate.now(), // startDate placeholder
              null, // endDate — open-ended
              7, // leadTimeDays default
              null, // projectLeadMemberId
              SEEDER_CREATED_BY);
      schedule.setStatus("PAUSED");
      schedule.setNextExecutionDate(null);
      if (entry.postCreateActions() != null) {
        schedule.setPostCreateActions(entry.postCreateActions());
      }
      recurringScheduleRepository.save(schedule);
      log.debug(
          "Created seeded recurring schedule '{}' ({}) for tenant {}",
          entry.name(),
          entry.recurrence(),
          tenantId);
    }
  }
}
