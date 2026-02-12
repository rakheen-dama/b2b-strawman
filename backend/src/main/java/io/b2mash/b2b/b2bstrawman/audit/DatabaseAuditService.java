package io.b2mash.b2b.b2bstrawman.audit;

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

  public DatabaseAuditService(AuditEventRepository auditEventRepository) {
    this.auditEventRepository = auditEventRepository;
  }

  @Override
  @Transactional
  public void log(AuditEventRecord record) {
    var event = new AuditEvent(record);
    auditEventRepository.save(event);
    log.debug(
        "Recorded audit event: type={}, entity={}/{}, actor={}",
        record.eventType(),
        record.entityType(),
        record.entityId(),
        record.actorId());
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
}
