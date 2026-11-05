package io.b2mash.b2b.b2bstrawman.task;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The single owner of "is this matter client-linked?" for portal task projection (Phase 89 633A,
 * §14.2.5, R7.3). Extracted verbatim from {@code TaskService.publishPortalTaskEventIfLinked}; used
 * by {@link TaskService} (ad-hoc tasks, sort order 0) and {@code ProjectTemplateService} (template
 * instantiation, the template task's sort order — B5).
 */
@Component
public class PortalTaskEventPublisher {

  private final CustomerProjectRepository customerProjectRepository;
  private final MemberNameResolver memberNameResolver;
  private final ApplicationEventPublisher eventPublisher;

  public PortalTaskEventPublisher(
      CustomerProjectRepository customerProjectRepository,
      MemberNameResolver memberNameResolver,
      ApplicationEventPublisher eventPublisher) {
    this.customerProjectRepository = customerProjectRepository;
    this.memberNameResolver = memberNameResolver;
    this.eventPublisher = eventPublisher;
  }

  /** Ad-hoc tasks: publishes with sort order 0 (unchanged {@code TaskService} behaviour). */
  public void publishIfLinked(Task task, PortalTaskEventFactory eventFactory) {
    publishIfLinked(task, 0, eventFactory);
  }

  /**
   * Publishes a portal task event if the task's project is linked to any customer. The event
   * factory is used to construct the correct event subtype (created, updated).
   */
  public void publishIfLinked(Task task, int sortOrder, PortalTaskEventFactory eventFactory) {
    var customerLinks = customerProjectRepository.findByProjectId(task.getProjectId());
    if (!customerLinks.isEmpty()) {
      String tenantId = RequestScopes.requireTenantId();
      String orgId = RequestScopes.requireOrgId();
      String assigneeName =
          task.getAssigneeId() != null
              ? memberNameResolver.resolveNameOrNull(task.getAssigneeId())
              : null;
      eventPublisher.publishEvent(
          eventFactory.create(
              task.getId(),
              task.getProjectId(),
              task.getTitle(),
              task.getStatus().name(),
              assigneeName,
              sortOrder,
              orgId,
              tenantId));
    }
  }

  @FunctionalInterface
  public interface PortalTaskEventFactory {
    Object create(
        UUID taskId,
        UUID projectId,
        String name,
        String status,
        String assigneeName,
        int sortOrder,
        String orgId,
        String tenantId);
  }
}
