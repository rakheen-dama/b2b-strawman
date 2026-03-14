package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.MemberRow;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.ProjectStaffingResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.TeamCapacityGrid;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CapacityController {

  private final CapacityGridService capacityGridService;

  public CapacityController(CapacityGridService capacityGridService) {
    this.capacityGridService = capacityGridService;
  }

  @GetMapping("/api/capacity/team")
  public ResponseEntity<TeamCapacityGrid> getTeamCapacityGrid(
      @RequestParam LocalDate weekStart, @RequestParam LocalDate weekEnd) {
    validateMondayStart(weekStart);
    return ResponseEntity.ok(capacityGridService.getTeamCapacityGrid(weekStart, weekEnd));
  }

  @GetMapping("/api/capacity/members/{memberId}")
  public ResponseEntity<MemberRow> getMemberCapacityDetail(
      @PathVariable UUID memberId,
      @RequestParam LocalDate weekStart,
      @RequestParam LocalDate weekEnd) {
    validateMondayStart(weekStart);
    return ResponseEntity.ok(
        capacityGridService.getMemberCapacityDetail(memberId, weekStart, weekEnd));
  }

  @GetMapping("/api/capacity/projects/{projectId}")
  public ResponseEntity<ProjectStaffingResponse> getProjectStaffing(
      @PathVariable UUID projectId,
      @RequestParam LocalDate weekStart,
      @RequestParam LocalDate weekEnd) {
    validateMondayStart(weekStart);
    return ResponseEntity.ok(capacityGridService.getProjectStaffing(projectId, weekStart, weekEnd));
  }

  private void validateMondayStart(LocalDate weekStart) {
    if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new InvalidStateException("Invalid week start", "weekStart must be a Monday");
    }
  }
}
