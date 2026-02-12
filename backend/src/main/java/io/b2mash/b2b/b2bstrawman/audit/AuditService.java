package io.b2mash.b2b.b2bstrawman.audit;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for recording and querying audit events. Implementations are tenant-scoped --
 * queries return only events visible to the current tenant (via Hibernate @Filter).
 */
public interface AuditService {

  /**
   * Records a single audit event within the current transaction. If the enclosing transaction rolls
   * back, the audit event is also rolled back (no REQUIRES_NEW).
   *
   * @param record the audit event data to persist
   */
  void log(AuditEventRecord record);

  /**
   * Queries audit events matching the given filter, scoped to the current tenant via Hibernate
   * {@code @Filter}. All filter fields are optional -- null means "no filter on this field".
   *
   * @param filter query filter with optional entity type, entity ID, actor ID, event type prefix,
   *     and time range
   * @param pageable pagination and sorting parameters
   * @return a page of matching audit events ordered by occurredAt DESC
   */
  Page<AuditEvent> findEvents(AuditEventFilter filter, Pageable pageable);

  /**
   * Counts audit events grouped by event type for the current tenant. Uses Hibernate
   * {@code @Filter} for tenant isolation.
   *
   * @return list of event type counts ordered by count descending
   */
  List<AuditEventRepository.EventTypeCount> countEventsByType();
}
