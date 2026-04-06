package io.b2mash.b2b.b2bstrawman.seeder;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTask;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTaskRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds project templates with pre-populated tasks from pack JSON definitions. Each template entry
 * creates a ProjectTemplate with source "SEEDER" and TemplateTask records linked by templateId.
 */
@Service
public class ProjectTemplatePackSeeder extends AbstractPackSeeder<ProjectTemplatePackDefinition> {

  private static final String PACK_LOCATION = "classpath:project-template-packs/*.json";

  /**
   * Sentinel UUID used as createdBy for seeder-created project templates. Distinguishable from real
   * member IDs.
   */
  public static final UUID SEEDER_CREATED_BY =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  private final ProjectTemplateRepository projectTemplateRepository;
  private final TemplateTaskRepository templateTaskRepository;

  public ProjectTemplatePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      ProjectTemplateRepository projectTemplateRepository,
      TemplateTaskRepository templateTaskRepository) {
    super(resourceResolver, objectMapper, orgSettingsRepository, tenantTransactionHelper);
    this.projectTemplateRepository = projectTemplateRepository;
    this.templateTaskRepository = templateTaskRepository;
  }

  @Override
  protected String getPackResourcePattern() {
    return PACK_LOCATION;
  }

  @Override
  protected Class<ProjectTemplatePackDefinition> getPackDefinitionType() {
    return ProjectTemplatePackDefinition.class;
  }

  @Override
  protected String getPackTypeName() {
    return "project-template";
  }

  @Override
  protected String getPackId(ProjectTemplatePackDefinition pack) {
    return pack.packId();
  }

  @Override
  protected String getPackVersion(ProjectTemplatePackDefinition pack) {
    return String.valueOf(pack.version());
  }

  @Override
  protected String getVerticalProfile(ProjectTemplatePackDefinition pack) {
    return pack.verticalProfile();
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getProjectTemplatePackStatus() == null) {
      return false;
    }
    return settings.getProjectTemplatePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, ProjectTemplatePackDefinition pack) {
    settings.recordProjectTemplatePackApplication(pack.packId(), pack.version());
  }

  @Override
  protected void applyPack(
      ProjectTemplatePackDefinition pack, Resource packResource, String tenantId) {
    for (ProjectTemplatePackDefinition.TemplateEntry entry : pack.templates()) {
      var template =
          new ProjectTemplate(
              entry.name(),
              entry.namePattern(),
              entry.description(),
              entry.billableDefault(),
              "SEEDER",
              null, // sourceProjectId — not derived from an existing project
              SEEDER_CREATED_BY);
      template = projectTemplateRepository.save(template);

      if (entry.tasks() != null) {
        int sortOrder = 1;
        for (ProjectTemplatePackDefinition.TaskEntry taskDef : entry.tasks()) {
          var task =
              new TemplateTask(
                  template.getId(),
                  taskDef.name(),
                  taskDef.description(),
                  taskDef.estimatedHours(),
                  sortOrder++,
                  taskDef.billable(),
                  taskDef.assigneeRole());
          templateTaskRepository.save(task);
        }
      }

      log.debug(
          "Created seeded project template '{}' with {} tasks for tenant {}",
          entry.name(),
          entry.tasks() != null ? entry.tasks().size() : 0,
          tenantId);
    }
  }
}
