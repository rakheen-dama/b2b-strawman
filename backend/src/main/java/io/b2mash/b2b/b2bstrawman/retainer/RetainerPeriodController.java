package io.b2mash.b2b.b2bstrawman.retainer;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.retainer.dto.PeriodCloseResult;
import io.b2mash.b2b.b2bstrawman.retainer.dto.PeriodSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final MemberRepository memberRepository;

  public RetainerPeriodController(
      RetainerPeriodService retainerPeriodService, MemberRepository memberRepository) {
    this.retainerPeriodService = retainerPeriodService;
    this.memberRepository = memberRepository;
  }

  @GetMapping("/{id}/periods")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Page<PeriodSummary>> listPeriods(
      @PathVariable UUID id, @PageableDefault(size = 20) Pageable pageable) {
    var periodsPage = retainerPeriodService.listPeriods(id, pageable);
    var memberNames = resolvePeriodNames(periodsPage.getContent());
    Page<PeriodSummary> page = periodsPage.map(p -> PeriodSummary.from(p, memberNames));
    return ResponseEntity.ok(page);
  }

  @GetMapping("/{id}/periods/current")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<PeriodSummary> getCurrentPeriod(@PathVariable UUID id) {
    var period = retainerPeriodService.getCurrentPeriod(id);
    var memberNames = resolvePeriodNames(List.of(period));
    return ResponseEntity.ok(PeriodSummary.from(period, memberNames));
  }

  @PostMapping("/{id}/periods/current/close")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<PeriodCloseResult> closePeriod(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    var result = retainerPeriodService.closePeriod(id, memberId);
    return ResponseEntity.ok(PeriodCloseResult.from(result));
  }

  private Map<UUID, String> resolvePeriodNames(List<RetainerPeriod> periods) {
    var ids =
        periods.stream()
            .map(RetainerPeriod::getClosedBy)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (ids.isEmpty()) return Map.of();
    return memberRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                Member::getId, m -> m.getName() != null ? m.getName() : "", (a, b) -> a));
  }
}
