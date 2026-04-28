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

    // 4. Batch-resolve USER actor names from members table
    var memberActorIds =
        events.stream()
            .filter(e -> !"PORTAL_CONTACT".equals(e.getActorType()))
            .map(AuditEvent::getActorId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, Member> actorMap =
        memberRepository.findAllById(memberActorIds).stream()
            .collect(Collectors.toMap(Member::getId, Function.identity()));

    // 5. Batch-resolve PORTAL_CONTACT actor names from portal_contacts table
    var portalContactIds =
        events.stream()
            .filter(e -> "PORTAL_CONTACT".equals(e.getActorType()))
            .map(AuditEvent::getActorId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, PortalContact> portalContactMap =
        portalContactRepository.findAllById(portalContactIds).stream()
            .collect(Collectors.toMap(PortalContact::getId, Function.identity()));

    // 6. Format each event using the actor maps
    var items =
        events.stream()
            .map(event -> activityMessageFormatter.format(event, actorMap, portalContactMap))
            .toList();

    return new PageImpl<>(items, pageable, events.getTotalElements());
  }
}
