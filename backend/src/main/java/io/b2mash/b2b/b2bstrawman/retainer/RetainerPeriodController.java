package io.b2mash.b2b.b2bstrawman.retainer;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.retainer.dto.PeriodCloseResult;
import io.b2mash.b2b.b2bstrawman.retainer.dto.PeriodSummary;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retainers")
public class RetainerPeriodController {

  private final RetainerPeriodService retainerPeriodService;

  public RetainerPeriodController(RetainerPeriodService retainerPeriodService) {
    this.retainerPeriodService = retainerPeriodService;
  }

  @GetMapping("/{id}/periods")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Page<PeriodSummary>> listPeriods(
      @PathVariable UUID id, @PageableDefault(size = 20) Pageable pageable) {
    Page<PeriodSummary> page =
        retainerPeriodService.listPeriods(id, pageable).map(PeriodSummary::from);
    return ResponseEntity.ok(page);
  }

  @GetMapping("/{id}/periods/current")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<PeriodSummary> getCurrentPeriod(@PathVariable UUID id) {
    return ResponseEntity.ok(PeriodSummary.from(retainerPeriodService.getCurrentPeriod(id)));
  }

  @PostMapping("/{id}/periods/current/close")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<PeriodCloseResult> closePeriod(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    var result = retainerPeriodService.closePeriod(id, memberId);
    return ResponseEntity.ok(PeriodCloseResult.from(result));
  }
}
