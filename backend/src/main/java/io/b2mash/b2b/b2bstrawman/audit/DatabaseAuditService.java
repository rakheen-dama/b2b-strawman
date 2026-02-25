package io.b2mash.b2b.b2bstrawman.audit;

import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed implementation of {@link AuditService}. Delegates persistence and querying to
 * {@link AuditEventRepository}.
 *
 * <p>Transaction semantics: {@code log()} participates in the caller's transaction (no
 * REQUIRES_NEW). If the domain operation rolls back, the audit event rolls back too.
 */
@Service
@EnableConfigurationProperties(AuditRetentionProperties.class)
public class DatabaseAuditService implements AuditService {

  private static final Logger log = LoggerFactory.getLogger(DatabaseAuditService.class);

  private final AuditEventRepository auditEventRepository;
  private final MemberRepository memberRepository;

  public DatabaseAuditService(
      AuditEventRepository auditEventRepository, MemberRepository memberRepository) {
    this.auditEventRepository = auditEventRepository;
    this.memberRepository = memberRepository;
  }

  @Override
  @Transactional
  public void log(AuditEventRecord record) {
    var enrichedRecord = enrichActorName(record);
    var event = new AuditEvent(enrichedRecord);
    auditEventRepository.save(event);
    log.debug(
        "Recorded audit event: type={}, entity={}/{}, actor={}",
        record.eventType(),
        record.entityType(),
        record.entityId(),
        record.actorId());
  }

  /**
   * Ensures the {@code actor_name} key is present in the record's details map. If the caller
   * already set it (e.g. PortalCommentService), the existing value is preserved. Otherwise, the
   * name is resolved from the member repository for USER actors, or defaults to "System".
   */
  private AuditEventRecord enrichActorName(AuditEventRecord record) {
    var details =
        new HashMap<String, Object>(record.details() != null ? record.details() : Map.of());
    if (!details.containsKey("actor_name")) {
      if (record.actorId() != null && "USER".equals(record.actorType())) {
        memberRepository
            .findById(record.actorId())
            .ifPresent(member -> details.put("actor_name", member.getName()));
      }
      if (!details.containsKey("actor_name")) {
        details.put("actor_name", "System");
      }
    }
    return new AuditEventRecord(
        record.eventType(),
        record.entityType(),
        record.entityId(),
        record.actorId(),
        record.actorType(),
        record.source(),
        record.ipAddress(),
        record.userAgent(),
        details);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AuditEvent> findEvents(AuditEventFilter filter, Pageable pageable) {
    return auditEventRepository.findByFilter(
        filter.entityType(),
        filter.entityId(),
        filter.actorId(),
        filter.eventType(),
        filter.from(),
        filter.to(),
        pageable);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditEventRepository.EventTypeCount> countEventsByType() {
    return auditEventRepository.countByEventType();
  }
}
