package io.b2mash.b2b.b2bstrawman.retainer;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.retainer.dto.PeriodCloseResult;
import io.b2mash.b2b.b2bstrawman.retainer.dto.PeriodSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
  public ResponseEntity<Page<PeriodSummary>> listPeriods(
      @PathVariable UUID id, @PageableDefault(size = 20) Pageable pageable) {
    var periodsPage = retainerPeriodService.listPeriods(id, pageable);
    var memberNames = retainerPeriodService.resolvePeriodNames(periodsPage.getContent());
    Page<PeriodSummary> page = periodsPage.map(p -> PeriodSummary.from(p, memberNames));
    return ResponseEntity.ok(page);
  }

  @GetMapping("/{id}/periods/current")
  public ResponseEntity<PeriodSummary> getCurrentPeriod(@PathVariable UUID id) {
    var period = retainerPeriodService.getCurrentPeriod(id);
    var memberNames = retainerPeriodService.resolvePeriodNames(List.of(period));
    return ResponseEntity.ok(PeriodSummary.from(period, memberNames));
  }

  @PostMapping("/{id}/periods/current/close")
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<PeriodCloseResult> closePeriod(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    var result = retainerPeriodService.closePeriod(id, memberId);
    return ResponseEntity.ok(PeriodCloseResult.from(result));
  }
}
