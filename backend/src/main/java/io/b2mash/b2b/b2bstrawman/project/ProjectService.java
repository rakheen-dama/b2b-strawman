package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.audit.AuditDeltaBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerProjectLinkedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.diary.NextDiaryDateProjection;
import io.b2mash.b2b.b2bstrawman.diary.NextDiaryDateRepository;
import io.b2mash.b2b.b2bstrawman.event.ProjectArchivedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectReopenedEvent;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.dto.MatterListRow;
import io.b2mash.b2b.b2bstrawman.project.dto.MatterResponse;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.tag.TagFilterUtil;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskStatus;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.view.CustomFieldFilterUtil;
import io.b2mash.b2b.b2bstrawman.view.ViewFilterHelper;
import io.b2mash.b2b.b2bstrawman.worktype.WorkTypes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

  private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

  // Module ids read by the Matters list projection (Epic 615A).
  private static final String DISBURSEMENTS_MODULE = "disbursements";
  private static final String TRUST_ACCOUNTING_MODULE = "trust_accounting";
  private static final String COURT_CALENDAR_MODULE = "court_calendar";

  private final ProjectRepository repository;
  private final ProjectMemberRepository projectMemberRepository;
  private final ProjectAccessService projectAccessService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberNameResolver memberNameResolver;
  private final TaskRepository taskRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final ProjectFieldService projectFieldService;
  private final ProjectDeletionGuard projectDeletionGuard;
  private final CustomerProjectRepository customerProjectRepository;
  private final EntityTagService entityTagService;
  private final ViewFilterHelper viewFilterHelper;
  private final MatterListRowRepository matterListRowRepository;
  private final NextDiaryDateRepository nextDiaryDateRepository;
  private final ClientLedgerCardRepository clientLedgerCardRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final Clock clock;

  public ProjectService(
      ProjectRepository repository,
      ProjectMemberRepository projectMemberRepository,
      ProjectAccessService projectAccessService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      MemberNameResolver memberNameResolver,
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository,
      ProjectFieldService projectFieldService,
      ProjectDeletionGuard projectDeletionGuard,
      CustomerProjectRepository customerProjectRepository,
      EntityTagService entityTagService,
      ViewFilterHelper viewFilterHelper,
      MatterListRowRepository matterListRowRepository,
      NextDiaryDateRepository nextDiaryDateRepository,
      ClientLedgerCardRepository clientLedgerCardRepository,
      OrgSettingsRepository orgSettingsRepository,
      Clock clock) {
    this.repository = repository;
    this.projectMemberRepository = projectMemberRepository;
    this.projectAccessService = projectAccessService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.memberNameResolver = memberNameResolver;
    this.taskRepository = taskRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.projectFieldService = projectFieldService;
    this.projectDeletionGuard = projectDeletionGuard;
    this.customerProjectRepository = customerProjectRepository;
    this.entityTagService = entityTagService;
    this.viewFilterHelper = viewFilterHelper;
    this.matterListRowRepository = matterListRowRepository;
    this.nextDiaryDateRepository = nextDiaryDateRepository;
    this.clientLedgerCardRepository = clientLedgerCardRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<MatterWithRole> listProjects(ActorContext actor) {
    if (Roles.ORG_OWNER.equals(actor.orgRole()) || Roles.ORG_ADMIN.equals(actor.orgRole())) {
      return repository.findAllProjectsWithRole(actor.memberId());
    }
    return repository.findProjectsForMember(actor.memberId());
  }

  /**
   * Lists projects as {@link MatterResponse} DTOs, applying the optional view / status / dueBefore
   * / customerId / custom-field / tag filters used by {@code GET /api/matters}.
   *
   * <p>Two filtering modes exist:
   *
   * <ul>
   *   <li><b>View-based</b> ({@code view != null}, server-side SQL): resolves the saved view's
   *       WHERE clause, applies member-level access control for non-admins, then narrows by
   *       customerId and attaches tags/member names. If the view produced no WHERE clause the
   *       method falls through to the in-memory path.
   *   <li><b>In-memory</b> (default): loads the actor's accessible projects, then applies the
   *       status (default ACTIVE-only), dueBefore, customerId, custom-field, and tag filters in
   *       sequence.
   * </ul>
   *
   * Moved verbatim from {@code ProjectController.listProjects} for TD-009 thin-controller cleanup —
   * behavior-preserving.
   */
  @Transactional(readOnly = true)
  public List<MatterResponse> listProjects(
      UUID view,
      String status,
      LocalDate dueBefore,
      UUID customerId,
      Map<String, String> allParams,
      ActorContext actor) {

    // --- View-based filtering (server-side SQL) ---
    if (view != null) {
      // Build access control set: regular members only see projects they have access to
      boolean isAdminOrOwner = actor.isOwnerOrAdmin();
      Set<UUID> accessibleIds = null;
      if (!isAdminOrOwner) {
        accessibleIds =
            listProjects(actor).stream()
                .map(pwr -> pwr.matter().getId())
                .collect(Collectors.toSet());
      }

      List<Project> filtered =
          viewFilterHelper.applyViewFilter(
              // ADR-342: storage key, do not rename
              view, "PROJECT", "projects", Project.class, accessibleIds, Project::getId);

      if (filtered != null) {
        // Apply customerId filter to view-based results
        if (customerId != null) {
          filtered = filtered.stream().filter(p -> customerId.equals(p.getCustomerId())).toList();
        }
        var projectIds = filtered.stream().map(Project::getId).toList();
        // ADR-342: storage key, do not rename
        var tagsByEntityId = entityTagService.getEntityTagsBatch("PROJECT", projectIds);
        var memberNames = resolveProjectMemberNames(filtered);

        return filtered.stream()
            .map(
                p ->
                    MatterResponse.from(
                        p, null, tagsByEntityId.getOrDefault(p.getId(), List.of()), memberNames))
            .toList();
      }
    }

    // --- Fallback: existing in-memory filtering ---
    var projectsWithRoles = listProjects(actor);

    // Apply status filter (default: ACTIVE only)
    List<ProjectStatus> statusFilter = parseProjectStatuses(status);
    if (statusFilter != null) {
      projectsWithRoles =
          projectsWithRoles.stream()
              .filter(pwr -> statusFilter.contains(pwr.matter().getStatus()))
              .toList();
    }

    // Apply dueBefore filter
    if (dueBefore != null) {
      projectsWithRoles =
          projectsWithRoles.stream()
              .filter(
                  pwr ->
                      pwr.matter().getDueDate() != null
                          && pwr.matter().getDueDate().isBefore(dueBefore))
              .toList();
    }

    // Apply customerId filter
    if (customerId != null) {
      projectsWithRoles =
          projectsWithRoles.stream()
              .filter(pwr -> customerId.equals(pwr.matter().getCustomerId()))
              .toList();
    }

    // Batch-load tags for all projects (2 queries instead of 2N)
    var projectIds = projectsWithRoles.stream().map(pwr -> pwr.matter().getId()).toList();
    // ADR-342: storage key, do not rename
    var tagsByEntityId = entityTagService.getEntityTagsBatch("PROJECT", projectIds);
    var allProjects = projectsWithRoles.stream().map(MatterWithRole::matter).toList();
    var memberNames = resolveProjectMemberNames(allProjects);

    var projects =
        projectsWithRoles.stream()
            .map(
                pwr ->
                    MatterResponse.from(
                        pwr.matter(),
                        pwr.matterRole(),
                        tagsByEntityId.getOrDefault(pwr.matter().getId(), List.of()),
                        memberNames))
            .toList();

    // Apply custom field filtering if present
    Map<String, String> customFieldFilters =
        CustomFieldFilterUtil.extractCustomFieldFilters(allParams);
    if (!customFieldFilters.isEmpty()) {
      projects =
          projects.stream()
              .filter(
                  p ->
                      CustomFieldFilterUtil.matchesCustomFieldFilters(
                          p.customFields(), customFieldFilters))
              .toList();
    }

    // Apply tag filtering if present
    List<String> tagSlugs = TagFilterUtil.extractTagSlugs(allParams);
    if (!tagSlugs.isEmpty()) {
      projects =
          projects.stream()
              .filter(p -> TagFilterUtil.matchesTagFilter(p.tags(), tagSlugs))
              .toList();
    }

    return projects;
  }

  private static List<ProjectStatus> parseProjectStatuses(String status) {
    if (status == null || status.isBlank()) {
      return List.of(ProjectStatus.ACTIVE); // Default: show only ACTIVE
    }
    if ("ALL".equalsIgnoreCase(status)) {
      return null; // null = no filter
    }
    return Arrays.stream(status.split(","))
        .map(String::trim)
        .map(
            s -> {
              try {
                return ProjectStatus.valueOf(s.toUpperCase());
              } catch (IllegalArgumentException e) {
                throw new InvalidStateException(
                    "Invalid matter status",
                    "Invalid matter status: '"
                        + s
                        + "'. Valid values: "
                        + Arrays.toString(ProjectStatus.values()));
              }
            })
        .toList();
  }

  // --- Matters list projection (Phase 87, Epic 615A — arch §12.2.6, §12.3.4) ---

  /**
   * Base rows plus whether a saved view produced them (custom-field/tag params apply otherwise).
   */
  private record BaseSelection(List<MatterWithRole> matters, boolean viewApplied) {}

  /**
   * {@code GET /api/matters} rows: the same base selection as {@link #listProjects(UUID, String,
   * LocalDate, UUID, Map, ActorContext)} (saved view, or accessible matters with status — default
   * ACTIVE — / dueBefore / customerId), narrowed by the list params, then enriched by batched
   * aggregates merged in Java. Every narrowing happens here in the service, never in the
   * controller: {@code inactiveSince} is a SQL predicate (one statement over the surviving ids);
   * {@code mine}, {@code hasUnbilled} and the custom-field/tag params filter the loaded rows in
   * Java ({@code mine} on the base query's caller role, {@code hasUnbilled} on the aggregate
   * statement's unbilled minutes, tags on the batched tag lookup). Cheap filters run before the
   * aggregate fan-out; tags run last because they need the tag batch.
   *
   * <p>Statement budget on the default path (no view, no inactiveSince): base 1 + org settings 1 +
   * matter aggregates 1 + next diary 1 + trust 0–1 + tags 1–2 = at most 7, independent of page size
   * (pinned by {@code MatterListRowIntegrationTest}). A saved view adds 1–2 (view lookup + caller
   * roles); {@code inactiveSince} adds 1.
   *
   * <p>Redaction: {@code unbilledValue} needs FINANCIAL_VISIBILITY (owner/admin always — the 614A
   * home rule); {@code trustBalance} needs VIEW_TRUST and the trust_accounting module. Minutes and
   * counts are never redacted. Legal diary kinds (court dates, prescriptions) count only with the
   * court_calendar module and VIEW_LEGAL — the One Diary gate.
   */
  @Transactional(readOnly = true)
  public List<MatterListRow> listMatterRows(MatterListQuery q, ActorContext actor) {
    BaseSelection base = selectBase(q, actor);
    List<MatterWithRole> matters = base.matters();

    // mine: the caller has a project_members row (role resolved by the base query)
    if (q.mine()) {
      matters = matters.stream().filter(m -> m.matterRole() != null).toList();
    }
    // custom-field params (in-memory path only, exactly as listProjects): on the base rows'
    // projects.custom_fields, before any aggregate work
    Map<String, String> customFieldFilters =
        base.viewApplied()
            ? Map.of()
            : CustomFieldFilterUtil.extractCustomFieldFilters(q.allParams());
    if (!customFieldFilters.isEmpty()) {
      matters =
          matters.stream()
              .filter(
                  m ->
                      CustomFieldFilterUtil.matchesCustomFieldFilters(
                          customFieldsOf(m.matter()), customFieldFilters))
              .toList();
    }
    // inactiveSince: SQL predicate over the surviving ids (+1 statement, only when present)
    if (q.inactiveSince() != null && !matters.isEmpty()) {
      Set<UUID> inactive =
          new HashSet<>(
              matterListRowRepository.findInactiveSince(idsOf(matters), q.inactiveSince()));
      matters = matters.stream().filter(m -> inactive.contains(m.matter().getId())).toList();
    }
    if (matters.isEmpty()) {
      return List.of();
    }

    // ONE settings read for currency + module flags (getDefaultCurrency()/isModuleEnabled() each
    // re-run the settings query).
    Optional<OrgSettings> settings = orgSettingsRepository.findForCurrentTenant();
    String currency =
        settings.map(OrgSettings::getDefaultCurrency).orElse(OrgSettingsService.DEFAULT_CURRENCY);
    Set<String> modules = settings.map(s -> Set.copyOf(s.getEnabledModules())).orElse(Set.of());
    boolean seesValue =
        actor.isOwnerOrAdmin() || RequestScopes.hasCapability("FINANCIAL_VISIBILITY");
    boolean seesTrust =
        modules.contains(TRUST_ACCOUNTING_MODULE) && RequestScopes.hasCapability("VIEW_TRUST");
    boolean includeLegalKinds =
        modules.contains(COURT_CALENDAR_MODULE) && RequestScopes.hasCapability("VIEW_LEGAL");

    Map<UUID, MatterListAggregateProjection> aggregates = new HashMap<>();
    for (var a :
        matterListRowRepository.findAggregates(
            idsOf(matters), currency, modules.contains(DISBURSEMENTS_MODULE))) {
      aggregates.put(a.getMatterId(), a);
    }
    // hasUnbilled: unbilled billable minutes > 0 (computed by the aggregate statement)
    if (q.hasUnbilled()) {
      matters =
          matters.stream().filter(m -> minutesOf(aggregates.get(m.matter().getId())) > 0).toList();
      if (matters.isEmpty()) {
        return List.of();
      }
    }

    List<UUID> ids = idsOf(matters);
    Map<UUID, NextDiaryDateProjection> nextDiary = new HashMap<>();
    for (var n :
        nextDiaryDateRepository.findNextByMatterIdIn(
            ids, LocalDate.now(clock), includeLegalKinds)) {
      nextDiary.put(n.getMatterId(), n);
    }
    Map<UUID, BigDecimal> trustByClient = seesTrust ? trustByClient(matters) : Map.of();
    // ADR-342: storage key, do not rename
    var tagsByEntityId = entityTagService.getEntityTagsBatch("PROJECT", ids);

    List<MatterListRow> rows =
        matters.stream()
            .map(
                m ->
                    toRow(
                        m,
                        aggregates.get(m.matter().getId()),
                        nextDiary.get(m.matter().getId()),
                        trustByClient,
                        tagsByEntityId.getOrDefault(m.matter().getId(), List.of()),
                        currency,
                        seesValue,
                        seesTrust))
            .toList();

    // tag params: in-memory path only, exactly as listProjects does (needs the tag batch above)
    if (!base.viewApplied()) {
      List<String> tagSlugs = TagFilterUtil.extractTagSlugs(q.allParams());
      if (!tagSlugs.isEmpty()) {
        rows =
            rows.stream().filter(r -> TagFilterUtil.matchesTagFilter(r.tags(), tagSlugs)).toList();
      }
    }
    return rows;
  }

  /**
   * Base selection with the same semantics as {@link #listProjects(UUID, String, LocalDate, UUID,
   * Map, ActorContext)}: a saved view (ADR-055, ViewFilterHelper untouched) narrowed by customerId,
   * else the actor's accessible matters with the status (default ACTIVE) / dueBefore / customerId
   * filters. The view path resolves the caller's role per matter in one query, which is also the
   * non-admin access set (the same rows as {@code findProjectsForMember}).
   */
  private BaseSelection selectBase(MatterListQuery q, ActorContext actor) {
    if (q.view() != null) {
      Map<UUID, String> callerRoles = new HashMap<>();
      for (ProjectMember pm : projectMemberRepository.findByMemberId(actor.memberId())) {
        callerRoles.put(pm.getProjectId(), pm.getProjectRole());
      }
      Set<UUID> accessibleIds = actor.isOwnerOrAdmin() ? null : callerRoles.keySet();
      List<Project> filtered =
          viewFilterHelper.applyViewFilter(
              // ADR-342: storage key, do not rename
              q.view(), "PROJECT", "projects", Project.class, accessibleIds, Project::getId);
      if (filtered != null) {
        return new BaseSelection(
            filtered.stream()
                .filter(p -> q.customerId() == null || q.customerId().equals(p.getCustomerId()))
                .map(p -> new MatterWithRole(p, callerRoles.get(p.getId())))
                .toList(),
            true);
      }
    }

    List<MatterWithRole> matters = listProjects(actor);
    List<ProjectStatus> statusFilter = parseProjectStatuses(q.status());
    LocalDate dueBefore = q.dueBefore();
    UUID customerId = q.customerId();
    return new BaseSelection(
        matters.stream()
            .filter(m -> statusFilter == null || statusFilter.contains(m.matter().getStatus()))
            .filter(
                m ->
                    dueBefore == null
                        || (m.matter().getDueDate() != null
                            && m.matter().getDueDate().isBefore(dueBefore)))
            .filter(m -> customerId == null || customerId.equals(m.matter().getCustomerId()))
            .toList(),
        false);
  }

  private static List<UUID> idsOf(List<MatterWithRole> matters) {
    return matters.stream().map(m -> m.matter().getId()).toList();
  }

  private static int minutesOf(MatterListAggregateProjection a) {
    return a == null || a.getUnbilledMinutes() == null ? 0 : a.getUnbilledMinutes().intValue();
  }

  private Map<UUID, BigDecimal> trustByClient(List<MatterWithRole> matters) {
    Set<UUID> clientIds =
        matters.stream()
            .map(m -> m.matter().getCustomerId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (clientIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, BigDecimal> balances = new HashMap<>();
    for (var row : clientLedgerCardRepository.sumBalancesByCustomerIdIn(clientIds)) {
      balances.put(row.getCustomerId(), row.getBalance());
    }
    return balances;
  }

  private static MatterListRow toRow(
      MatterWithRole mwr,
      MatterListAggregateProjection agg,
      NextDiaryDateProjection next,
      Map<UUID, BigDecimal> trustByClient,
      List<TagResponse> tags,
      String currency,
      boolean seesValue,
      boolean seesTrust) {
    Project p = mwr.matter();
    BigDecimal value = null;
    if (seesValue) {
      BigDecimal time =
          agg == null || agg.getUnbilledTimeValue() == null
              ? BigDecimal.ZERO
              : agg.getUnbilledTimeValue();
      BigDecimal disbursements =
          agg == null || agg.getUnbilledDisbursementAmount() == null
              ? BigDecimal.ZERO
              : agg.getUnbilledDisbursementAmount();
      value = time.add(disbursements).setScale(2, RoundingMode.HALF_UP);
    }
    BigDecimal trust =
        seesTrust && p.getCustomerId() != null
            ? trustByClient.getOrDefault(p.getCustomerId(), BigDecimal.ZERO)
            : null;
    return new MatterListRow(
        p.getId(),
        p.getName(),
        p.getDescription(),
        p.getReferenceNumber(),
        p.getStatus().name(),
        p.getWorkType(),
        p.getPriority(),
        p.getDueDate(),
        p.getCreatedAt(),
        p.getCustomerId(),
        agg == null ? null : agg.getClientName(),
        agg == null ? null : agg.getFeeEarnerId(),
        agg == null ? null : agg.getFeeEarnerName(),
        mwr.matterRole(),
        minutesOf(agg),
        value,
        currency,
        trust,
        next == null ? null : next.getNextDate(),
        next == null ? null : next.getNextKind(),
        agg == null || agg.getOpenTaskCount() == null ? 0 : agg.getOpenTaskCount().intValue(),
        tags,
        customFieldsOf(p));
  }

  private static Map<String, Object> customFieldsOf(Project p) {
    return p.getCustomFields() != null ? p.getCustomFields() : Map.of();
  }

  @Transactional(readOnly = true)
  public MatterWithRole getProject(UUID id, ActorContext actor) {
    var project =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, id));
    var access = projectAccessService.requireViewAccess(id, actor);
    return new MatterWithRole(project, access.projectRole());
  }

  @Transactional
  public Project createProject(String name, String description, UUID createdBy) {
    return createProject(name, description, createdBy, null, null, null, null, null, null, null);
  }

  @Transactional
  public Project createProject(
      String name,
      String description,
      UUID createdBy,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {
    return createProject(
        name,
        description,
        createdBy,
        customFields,
        appliedFieldGroups,
        null,
        null,
        null,
        null,
        null);
  }

  @Transactional
  public Project createProject(
      String name,
      String description,
      UUID createdBy,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      UUID customerId,
      LocalDate dueDate) {
    return createProject(
        name,
        description,
        createdBy,
        customFields,
        appliedFieldGroups,
        customerId,
        dueDate,
        null,
        null,
        null);
  }

  @Transactional
  public Project createProject(
      String name,
      String description,
      UUID createdBy,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      UUID customerId,
      LocalDate dueDate,
      String referenceNumber,
      ProjectPriority priority,
      String workType) {
    // Canonical form first so auto-apply resolution and the stored value agree.
    workType = WorkTypes.normalizeForStorage(workType);
    // Validate customer link and resolve customer name for naming pattern
    String customerName = null;
    if (customerId != null) {
      customerName = projectFieldService.resolveCustomerName(customerId);
    }

    // Validate fields, apply naming pattern, resolve auto-apply groups. GAP-L-37-regression-
    // 2026-04-25: pass workType so work-type-scoped groups (e.g. conveyancing-za-project) only
    // auto-attach to matching projects; unscoped groups continue to apply regardless.
    var fieldResult =
        projectFieldService.prepareForCreate(
            name, customFields, appliedFieldGroups, customerName, workType);

    var project = new Project(fieldResult.resolvedName(), description, createdBy);
    project.setCustomFields(fieldResult.validatedFields());
    project.setAppliedFieldGroups(fieldResult.mergedFieldGroups());
    if (customerId != null) {
      project.setCustomerId(customerId);
    }
    if (dueDate != null) {
      project.setDueDate(dueDate);
    }
    if (referenceNumber != null) {
      project.setReferenceNumber(referenceNumber);
    }
    if (priority != null) {
      project.setPriority(priority);
    }
    if (workType != null) {
      project.setWorkType(workType);
    }

    project = repository.save(project);

    var lead = new ProjectMember(project.getId(), createdBy, Roles.PROJECT_LEAD, null);
    projectMemberRepository.save(lead);

    // Create CustomerProject join record for consistency with CustomerProjectRepository queries
    if (customerId != null) {
      customerProjectRepository.save(new CustomerProject(customerId, project.getId(), createdBy));

      // Publish event for portal read model sync
      String tenantIdForLink = RequestScopes.getTenantIdOrNull();
      String orgIdForLink = RequestScopes.getOrgIdOrNull();
      eventPublisher.publishEvent(
          new CustomerProjectLinkedEvent(
              customerId, project.getId(), orgIdForLink, tenantIdForLink));
    }

    log.info("Created project {} with lead member {}", project.getId(), createdBy);

    var auditDetails = new LinkedHashMap<String, Object>();
    auditDetails.put("name", project.getName());
    // OBS-8801: project.created is a project-lifecycle event surfaced in the matter Activity feed
    // (findByProjectId) — both feed queries scope on details->>'project_id', so it is load-bearing.
    auditDetails.put("project_id", project.getId().toString());
    if (customerId != null) {
      auditDetails.put("customerId", customerId.toString());
    }
    if (dueDate != null) {
      auditDetails.put("dueDate", dueDate.toString());
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.created")
            // ADR-342: storage key, do not rename
            .entityType("project")
            .entityId(project.getId())
            .details(auditDetails)
            .build());

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new ProjectCreatedEvent(
            project.getId(), project.getName(), project.getDescription(), null, orgId, tenantId));

    return project;
  }

  @Transactional
  public MatterWithRole updateProject(
      UUID id, String name, String description, ActorContext actor) {
    return updateProject(id, name, description, actor, null, null, null, null, null, null, null);
  }

  @Transactional
  public MatterWithRole updateProject(
      UUID id,
      String name,
      String description,
      ActorContext actor,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {
    return updateProject(
        id,
        name,
        description,
        actor,
        customFields,
        appliedFieldGroups,
        null,
        null,
        null,
        null,
        null);
  }

  @Transactional
  public MatterWithRole updateProject(
      UUID id,
      String name,
      String description,
      ActorContext actor,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      UUID customerId,
      LocalDate dueDate) {
    return updateProject(
        id,
        name,
        description,
        actor,
        customFields,
        appliedFieldGroups,
        customerId,
        dueDate,
        null,
        null,
        null);
  }

  @Transactional
  public MatterWithRole updateProject(
      UUID id,
      String name,
      String description,
      ActorContext actor,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      UUID customerId,
      LocalDate dueDate,
      String referenceNumber,
      ProjectPriority priority,
      String workType) {
    var project =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, id));
    var access = projectAccessService.requireEditAccess(id, actor);

    // Validate customer link if provided. GAP-L-35: pass the existing customerId so the
    // guard can distinguish "new/changed link" (full CREATE_PROJECT gate) from "unchanged
    // link" (lighter UPDATE_CUSTOM_FIELDS gate) — Save Custom Fields re-sends the existing
    // customerId in the full PUT body and must not be re-gated for PROSPECT engagements.
    if (customerId != null) {
      projectFieldService.validateCustomerLink(customerId, project.getCustomerId());
    }

    // Capture old values before mutation
    String oldName = project.getName();
    String oldDescription = project.getDescription();
    UUID oldCustomerId = project.getCustomerId();
    LocalDate oldDueDate = project.getDueDate();
    String oldReferenceNumber = project.getReferenceNumber();
    ProjectPriority oldPriority = project.getPriority();
    String oldWorkType = project.getWorkType();

    // Validate and set custom fields
    if (customFields != null) {
      Map<String, Object> validatedFields =
          projectFieldService.validateFields(
              customFields,
              appliedFieldGroups != null ? appliedFieldGroups : project.getAppliedFieldGroups());
      project.setCustomFields(validatedFields);
    }
    if (appliedFieldGroups != null) {
      project.setAppliedFieldGroups(appliedFieldGroups);
    }

    // Use Project.update() which applies null-means-no-change convention
    project.update(name, description, customerId, dueDate, referenceNumber, priority, workType);
    project = repository.save(project);

    // Create CustomerProject join record if customerId was set/changed and no link exists yet
    UUID newCustomerId = project.getCustomerId();
    if (newCustomerId != null
        && !customerProjectRepository.existsByCustomerIdAndProjectId(newCustomerId, id)) {
      customerProjectRepository.save(new CustomerProject(newCustomerId, id, actor.memberId()));

      // Publish event for portal read model sync
      String tenantIdForLink = RequestScopes.getTenantIdOrNull();
      String orgIdForLink = RequestScopes.getOrgIdOrNull();
      eventPublisher.publishEvent(
          new CustomerProjectLinkedEvent(newCustomerId, id, orgIdForLink, tenantIdForLink));
    }

    // Build delta map -- only include changed fields (use actual values from entity)
    LocalDate newDueDate = project.getDueDate();
    var details =
        new AuditDeltaBuilder()
            .track("name", oldName, name)
            .track("description", oldDescription, description)
            .trackAsString("customerId", oldCustomerId, newCustomerId)
            .trackAsString("dueDate", oldDueDate, newDueDate)
            .track("referenceNumber", oldReferenceNumber, referenceNumber)
            .trackAsString("priority", oldPriority, priority)
            // Persisted value, not the argument: an omitted workType (null = no change) must not
            // read as a change to "", and a supplied one is compared in canonical form.
            .track("workType", oldWorkType, project.getWorkType())
            .buildMutable();
    // OBS-8801: project.updated is a project-lifecycle event surfaced in the matter Activity feed
    // (findByProjectId) — both feed queries scope on details->>'project_id', so it is load-bearing.
    details.put("project_id", project.getId().toString());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.updated")
            // ADR-342: storage key, do not rename
            .entityType("project")
            .entityId(project.getId())
            .details(details)
            .build());

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new ProjectUpdatedEvent(
            project.getId(), project.getName(), project.getDescription(), null, orgId, tenantId));

    return new MatterWithRole(project, access.projectRole());
  }

  @Transactional
  public List<FieldDefinitionResponse> setFieldGroups(
      UUID id, List<UUID> appliedFieldGroups, ActorContext actor) {
    var project =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, id));
    projectAccessService.requireEditAccess(id, actor);

    var result = projectFieldService.setFieldGroups(project, appliedFieldGroups);
    repository.save(project);

    return result;
  }

  @Transactional
  public void deleteProject(UUID id) {
    RequestScopes.requireOwner();
    var project =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, id));

    // Guard 1: Status check — only ACTIVE projects can be deleted
    if (project.getStatus() != ProjectStatus.ACTIVE) {
      String status = project.getStatus().name().toLowerCase();
      String article = "aeiou".indexOf(status.charAt(0)) >= 0 ? "an" : "a";
      throw new ResourceConflictException(
          "Cannot delete matter",
          "Cannot delete %s %s matter. Reopen the matter first.".formatted(article, status));
    }

    projectDeletionGuard.checkAndExecute(id);

    repository.delete(project);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.deleted")
            // ADR-342: storage key, do not rename
            .entityType("project")
            .entityId(project.getId())
            // OBS-8801: include project_id so the matter Activity feed (findByProjectId, which
            // scopes
            // on details->>'project_id') surfaces the deletion consistently with the rest of the
            // family.
            .details(Map.of("name", project.getName(), "project_id", project.getId().toString()))
            .build());
  }

  // --- Project lifecycle methods (Epic 204A) ---

  @Transactional
  public Project completeProject(
      UUID projectId, boolean acknowledgeUnbilledTime, ActorContext actor) {
    var project =
        repository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, projectId));

    projectAccessService.requireEditAccess(projectId, actor);

    // Guardrail 1: open tasks
    long openTaskCount =
        taskRepository.countByProjectIdAndStatusNotIn(
            projectId, List.of(TaskStatus.DONE, TaskStatus.CANCELLED));
    if (openTaskCount > 0) {
      throw new InvalidStateException(
          "Cannot complete matter",
          "Matter has %d open task(s). Complete or cancel all tasks before completing the matter."
              .formatted(openTaskCount));
    }

    // Guardrail 2: unbilled time
    var unbilledSummary = timeEntryRepository.countUnbilledByProjectId(projectId);
    long unbilledCount = unbilledSummary.getEntryCount();
    double unbilledHours = unbilledSummary.getTotalHours();
    if (unbilledCount > 0 && !acknowledgeUnbilledTime) {
      throw new ResourceConflictException(
          "Unbilled time entries",
          "Matter has %d unbilled time entry/entries (%.1f hours). Acknowledge to proceed."
              .formatted(unbilledCount, unbilledHours));
    }

    project.complete(actor.memberId());
    project = repository.save(project);
    log.info("Project {} completed by member {}", projectId, actor.memberId());

    var auditDetails = new LinkedHashMap<String, Object>();
    auditDetails.put("name", project.getName());
    // OBS-8801: project.completed is a project-lifecycle event surfaced in the matter Activity feed
    // (findByProjectId) — both feed queries scope on details->>'project_id', so it is load-bearing.
    auditDetails.put("project_id", project.getId().toString());
    auditDetails.put("completed_by", actor.memberId().toString());
    if (unbilledCount > 0 && acknowledgeUnbilledTime) {
      auditDetails.put("unbilled_time_waived", true);
      auditDetails.put("unbilled_entry_count", unbilledCount);
      auditDetails.put("unbilled_hours", unbilledHours);
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.completed")
            // ADR-342: storage key, do not rename
            .entityType("project")
            .entityId(project.getId())
            .details(auditDetails)
            .build());

    String actorName = memberNameResolver.resolveName(actor.memberId());
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new ProjectCompletedEvent(
            "project.completed",
            "project",
            project.getId(),
            project.getId(),
            actor.memberId(),
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("name", project.getName()),
            actor.memberId(),
            project.getName(),
            unbilledCount > 0 && acknowledgeUnbilledTime,
            RequestScopes.AUTOMATION_EXECUTION_ID.isBound()
                ? RequestScopes.AUTOMATION_EXECUTION_ID.get()
                : null));

    return project;
  }

  @Transactional
  public Project archiveProject(UUID projectId, ActorContext actor) {
    var project =
        repository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, projectId));

    projectAccessService.requireEditAccess(projectId, actor);

    project.archive(actor.memberId());
    project = repository.save(project);
    log.info("Project {} archived by member {}", projectId, actor.memberId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.archived")
            // ADR-342: storage key, do not rename
            .entityType("project")
            .entityId(project.getId())
            // OBS-8801: include project_id so the matter Activity feed (findByProjectId, scoping on
            // details->>'project_id') surfaces this project-lifecycle event with the rest of the
            // family.
            .details(
                Map.of(
                    "name", project.getName(),
                    "archived_by", actor.memberId().toString(),
                    "project_id", project.getId().toString()))
            .build());

    String actorName = memberNameResolver.resolveName(actor.memberId());
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new ProjectArchivedEvent(
            "project.archived",
            "project",
            project.getId(),
            project.getId(),
            actor.memberId(),
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("name", project.getName()),
            actor.memberId(),
            project.getName()));

    return project;
  }

  @Transactional
  public Project reopenProject(UUID projectId, ActorContext actor) {
    var project =
        repository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, projectId));

    projectAccessService.requireEditAccess(projectId, actor);

    String previousStatus = project.getStatus().name();

    project.reopen();
    project = repository.save(project);
    log.info("Project {} reopened by member {}", projectId, actor.memberId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.reopened")
            // ADR-342: storage key, do not rename
            .entityType("project")
            .entityId(project.getId())
            // OBS-8801: include project_id so the matter Activity feed (findByProjectId, scoping on
            // details->>'project_id') surfaces this project-lifecycle event with the rest of the
            // family.
            .details(
                Map.of(
                    "name", project.getName(),
                    "reopened_by", actor.memberId().toString(),
                    "previous_status", previousStatus,
                    "project_id", project.getId().toString()))
            .build());

    String actorName = memberNameResolver.resolveName(actor.memberId());
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new ProjectReopenedEvent(
            "project.reopened",
            "project",
            project.getId(),
            project.getId(),
            actor.memberId(),
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("name", project.getName()),
            actor.memberId(),
            project.getName(),
            previousStatus));

    return project;
  }

  // --- Name Resolution (moved from controller for BE-007) ---

  /**
   * Batch-loads member names for all createdBy and completedBy IDs referenced by the given
   * projects.
   */
  public Map<UUID, String> resolveProjectMemberNames(List<Project> projects) {
    var ids =
        projects.stream()
            .flatMap(p -> java.util.stream.Stream.of(p.getCreatedBy(), p.getCompletedBy()))
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();

    if (ids.isEmpty()) {
      return Map.of();
    }

    return memberNameResolver.resolveNames(ids);
  }
}
