package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.MemberRow;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.ProjectStaffingResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.TeamCapacityGrid;
import io.b2mash.b2b.b2bstrawman.capacity.dto.UtilizationDtos.TeamUtilizationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.UtilizationDtos.WeekUtilization;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CapacityController {

  private final CapacityService capacityService;
  private final UtilizationService utilizationService;

  public CapacityController(
      CapacityService capacityService, UtilizationService utilizationService) {
    this.capacityService = capacityService;
    this.utilizationService = utilizationService;
  }

  @GetMapping("/api/capacity/team")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<TeamCapacityGrid> getTeamCapacityGrid(
      @RequestParam LocalDate weekStart, @RequestParam LocalDate weekEnd) {
    return ResponseEntity.ok(capacityService.getTeamCapacityGrid(weekStart, weekEnd));
  }

  @GetMapping("/api/capacity/members/{memberId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<MemberRow> getMemberCapacityDetail(
      @PathVariable UUID memberId,
      @RequestParam LocalDate weekStart,
      @RequestParam LocalDate weekEnd) {
    return ResponseEntity.ok(capacityService.getMemberCapacityDetail(memberId, weekStart, weekEnd));
  }

  @GetMapping("/api/capacity/projects/{projectId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<ProjectStaffingResponse> getProjectStaffing(
      @PathVariable UUID projectId,
      @RequestParam LocalDate weekStart,
      @RequestParam LocalDate weekEnd) {
    return ResponseEntity.ok(capacityService.getProjectStaffing(projectId, weekStart, weekEnd));
  }

  @GetMapping("/api/utilization/team")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<TeamUtilizationResponse> getTeamUtilization(
      @RequestParam LocalDate weekStart, @RequestParam LocalDate weekEnd) {
    return ResponseEntity.ok(utilizationService.getTeamUtilization(weekStart, weekEnd));
  }

  @GetMapping("/api/utilization/members/{memberId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<List<WeekUtilization>> getMemberUtilization(
      @PathVariable UUID memberId,
      @RequestParam LocalDate weekStart,
      @RequestParam LocalDate weekEnd) {
    return ResponseEntity.ok(utilizationService.getMemberUtilization(memberId, weekStart, weekEnd));
  }
}
