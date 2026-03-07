package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.CreateLeaveRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.LeaveBlockResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.UpdateLeaveRequest;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveBlockService {

  private final LeaveBlockRepository leaveBlockRepository;
  private final NotificationService notificationService;
  private final AuditService auditService;

  public LeaveBlockService(
      LeaveBlockRepository leaveBlockRepository,
      NotificationService notificationService,
      AuditService auditService) {
    this.leaveBlockRepository = leaveBlockRepository;
    this.notificationService = notificationService;
    this.auditService = auditService;
  }

  /** Lists all leave blocks for a member, ordered by startDate DESC. */
  @Transactional(readOnly = true)
  public List<LeaveBlockResponse> listLeaveForMember(UUID memberId) {
    return leaveBlockRepository.findByMemberIdOrderByStartDateDesc(memberId).stream()
        .map(this::toResponse)
        .toList();
  }

  /** Lists all leave blocks overlapping the given date range (team calendar). */
  @Transactional(readOnly = true)
  public List<LeaveBlockResponse> listAllLeave(LocalDate startDate, LocalDate endDate) {
    return leaveBlockRepository.findAllOverlapping(startDate, endDate).stream()
        .map(this::toResponse)
        .toList();
  }

  /** Creates a new leave block. Enforces self-service RBAC and date validation. */
  @Transactional
  public LeaveBlockResponse createLeaveBlock(
      UUID memberId, CreateLeaveRequest request, UUID createdBy) {
    enforceSelfServiceRbac(memberId);
    validateDates(request.startDate(), request.endDate());

    var block =
        new LeaveBlock(memberId, request.startDate(), request.endDate(), request.note(), createdBy);
    block = leaveBlockRepository.save(block);

    // Notification: LEAVE_CREATED (only when admin/owner creates leave for another member)
    if (!createdBy.equals(memberId)) {
      notificationService.createIfEnabled(
          memberId,
          "LEAVE_CREATED",
          "Leave created: %s to %s".formatted(request.startDate(), request.endDate()),
          request.note(),
          "LEAVE_BLOCK",
          block.getId(),
          null);
    }

    // Audit event: leave_block.created
    Map<String, Object> details = new HashMap<>();
    details.put("member_id", memberId.toString());
    details.put("start_date", request.startDate().toString());
    details.put("end_date", request.endDate().toString());
    if (request.note() != null) {
      details.put("note", request.note());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("leave_block.created")
            .entityType("leave_block")
            .entityId(block.getId())
            .details(details)
            .build());

    return toResponse(block);
  }

  /** Updates an existing leave block. Enforces self-service RBAC and date validation. */
  @Transactional
  public LeaveBlockResponse updateLeaveBlock(UUID memberId, UUID id, UpdateLeaveRequest request) {
    enforceSelfServiceRbac(memberId);
    validateDates(request.startDate(), request.endDate());

    var block =
        leaveBlockRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("LeaveBlock", id));
    if (!block.getMemberId().equals(memberId)) {
      throw new ResourceNotFoundException("LeaveBlock", id);
    }

    LocalDate oldStartDate = block.getStartDate();
    LocalDate oldEndDate = block.getEndDate();

    block.update(request.startDate(), request.endDate(), request.note());
    block = leaveBlockRepository.save(block);

    // Audit event: leave_block.updated
    Map<String, Object> details = new HashMap<>();
    details.put("member_id", memberId.toString());
    details.put("old_start_date", oldStartDate.toString());
    details.put("old_end_date", oldEndDate.toString());
    details.put("new_start_date", request.startDate().toString());
    details.put("new_end_date", request.endDate().toString());
    if (request.note() != null) {
      details.put("note", request.note());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("leave_block.updated")
            .entityType("leave_block")
            .entityId(block.getId())
            .details(details)
            .build());

    return toResponse(block);
  }

  /** Deletes a leave block. Enforces self-service RBAC. */
  @Transactional
  public void deleteLeaveBlock(UUID memberId, UUID id) {
    enforceSelfServiceRbac(memberId);

    var block =
        leaveBlockRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("LeaveBlock", id));
    if (!block.getMemberId().equals(memberId)) {
      throw new ResourceNotFoundException("LeaveBlock", id);
    }

    // Audit event: leave_block.deleted (capture before delete)
    Map<String, Object> details = new HashMap<>();
    details.put("member_id", block.getMemberId().toString());
    details.put("start_date", block.getStartDate().toString());
    details.put("end_date", block.getEndDate().toString());
    if (block.getNote() != null) {
      details.put("note", block.getNote());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("leave_block.deleted")
            .entityType("leave_block")
            .entityId(block.getId())
            .details(details)
            .build());

    leaveBlockRepository.delete(block);
  }

  private void enforceSelfServiceRbac(UUID memberId) {
    UUID currentMemberId = RequestScopes.requireMemberId();
    String role = RequestScopes.getOrgRole();
    if ("member".equals(role) && !currentMemberId.equals(memberId)) {
      throw new ForbiddenException("Access denied", "Members can only manage their own leave");
    }
  }

  private void validateDates(LocalDate startDate, LocalDate endDate) {
    if (endDate.isBefore(startDate)) {
      throw new InvalidStateException(
          "Invalid leave dates", "endDate must be on or after startDate");
    }
  }

  private LeaveBlockResponse toResponse(LeaveBlock block) {
    return new LeaveBlockResponse(
        block.getId(),
        block.getMemberId(),
        block.getStartDate(),
        block.getEndDate(),
        block.getNote(),
        block.getCreatedBy(),
        block.getCreatedAt());
  }
}
