package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.CreateLeaveRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.LeaveBlockResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.UpdateLeaveRequest;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveBlockService {

  private final LeaveBlockRepository leaveBlockRepository;

  public LeaveBlockService(LeaveBlockRepository leaveBlockRepository) {
    this.leaveBlockRepository = leaveBlockRepository;
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

    block.update(request.startDate(), request.endDate(), request.note());
    block = leaveBlockRepository.save(block);
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
