package io.b2mash.b2b.b2bstrawman.activity;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for retrieving project activity feeds. Queries audit events scoped to a project,
 * batch-resolves actor names from the members table (for USER actors) and portal_contacts table
 * (for PORTAL_CONTACT actors), and formats each event into a human-readable activity item.
 */
@Service
public class ActivityService {

  private final AuditEventRepository auditEventRepository;
  private final MemberRepository memberRepository;
  private final PortalContactRepository portalContactRepository;
  private final ProjectAccessService projectAccessService;
  private final ActivityMessageFormatter activityMessageFormatter;

  public ActivityService(
      AuditEventRepository auditEventRepository,
      MemberRepository memberRepository,
      PortalContactRepository portalContactRepository,
      ProjectAccessService projectAccessService,
      ActivityMessageFormatter activityMessageFormatter) {
    this.auditEventRepository = auditEventRepository;
    this.memberRepository = memberRepository;
    this.portalContactRepository = portalContactRepository;
    this.projectAccessService = projectAccessService;
    this.activityMessageFormatter = activityMessageFormatter;
  }

  /**
   * Returns a paginated activity feed for the given project. Verifies the caller has view access,
   * fetches audit events filtered by optional entity type and since timestamp, resolves actor names
   * in batch (members for USER actors, portal_contacts for PORTAL_CONTACT actors), and formats each
   * event into an {@link ActivityItem}.
   *
   * @param projectId the project to fetch activity for
   * @param entityType optional entity type filter (uppercase from API, converted to lowercase for
   *     DB)
   * @param since optional timestamp filter -- only events after this instant
   * @param pageable pagination parameters
   * @param actor the authenticated actor for access control
   * @return a page of formatted activity items ordered by occurredAt DESC
   */
  @Transactional(readOnly = true)
  public Page<ActivityItem> getProjectActivity(
      UUID projectId, String entityType, Instant since, Pageable pageable, ActorContext actor) {

    // 1. Verify project access
    projectAccessService.requireViewAccess(projectId, actor);

    // 2. Convert entityType to lowercase for DB query (API accepts uppercase, DB stores lowercase)
    String normalizedEntityType = entityType != null ? entityType.toLowerCase(Locale.ROOT) : null;

    // 3. Fetch audit events page
    Page<AuditEvent> events =
        auditEventRepository.findByProjectId(
            projectId.toString(), normalizedEntityType, since, pageable);

    // 4. Resolve actors in batch and format (shared with Home recent activity)
    var items = formatEvents(events.getContent());

    return new PageImpl<>(items, pageable, events.getTotalElements());
  }

  /**
   * Formats audit events into activity items, batch-resolving actor names (members for USER actors,
   * portal_contacts for PORTAL_CONTACT actors — one query each, never per row). The single sentence
   * path for the matter feed and Home "Recent Activity" (ADR-349: one formatter, Home delegates),
   * so the same event reads the same sentence and actor on both. Performs no access check: callers
   * select the rows the actor may see. Items are returned in the order of {@code events}.
   *
   * @param events the audit events to format (already access-filtered by the caller)
   * @return one activity item per event, same order
   */
  @Transactional(readOnly = true)
  public List<ActivityItem> formatEvents(List<AuditEvent> events) {
    var memberActorIds =
        events.stream()
            .filter(e -> !"PORTAL_CONTACT".equals(e.getActorType()))
            .map(AuditEvent::getActorId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, Member> actorMap =
        memberRepository.findAllById(memberActorIds).stream()
            .collect(Collectors.toMap(Member::getId, Function.identity()));

    var portalContactIds =
        events.stream()
            .filter(e -> "PORTAL_CONTACT".equals(e.getActorType()))
            .map(AuditEvent::getActorId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, PortalContact> portalContactMap =
        portalContactRepository.findAllById(portalContactIds).stream()
            .collect(Collectors.toMap(PortalContact::getId, Function.identity()));

    return events.stream()
        .map(event -> activityMessageFormatter.format(event, actorMap, portalContactMap))
        .toList();
  }
}
