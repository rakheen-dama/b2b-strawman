package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.capacity.dto.UtilizationDtos.TeamUtilizationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.UtilizationDtos.WeekUtilization;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.time.DayOfWeek;
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
public class UtilizationController {

  private final UtilizationService utilizationService;

  public UtilizationController(UtilizationService utilizationService) {
    this.utilizationService = utilizationService;
  }

  @GetMapping("/api/utilization/team")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<TeamUtilizationResponse> getTeamUtilization(
      @RequestParam LocalDate weekStart, @RequestParam LocalDate weekEnd) {
    validateMondayStart(weekStart);
    return ResponseEntity.ok(utilizationService.getTeamUtilization(weekStart, weekEnd));
  }

  @GetMapping("/api/utilization/members/{memberId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<List<WeekUtilization>> getMemberUtilization(
      @PathVariable UUID memberId,
      @RequestParam LocalDate weekStart,
      @RequestParam LocalDate weekEnd) {
    validateMondayStart(weekStart);
    return ResponseEntity.ok(utilizationService.getMemberUtilization(memberId, weekStart, weekEnd));
  }

  private void validateMondayStart(LocalDate weekStart) {
    if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new InvalidStateException("Invalid week start", "weekStart must be a Monday");
    }
  }
}
