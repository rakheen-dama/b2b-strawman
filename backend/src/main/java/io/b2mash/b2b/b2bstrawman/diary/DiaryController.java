package io.b2mash.b2b.b2bstrawman.diary;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One Diary read (Phase 87, ADR-339). No {@code @RequiresCapability} — same visibility as {@code
 * GET /api/calendar}; court/prescription kinds are gated inside {@link DiaryService} and the kinds
 * actually served are listed in the {@code X-Diary-Types} response header.
 */
@RestController
@RequestMapping("/api/diary")
public class DiaryController {

  private final DiaryService diaryService;

  public DiaryController(DiaryService diaryService) {
    this.diaryService = diaryService;
  }

  @GetMapping
  public ResponseEntity<List<DiaryEventResponse>> getDiary(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) Set<DiaryKind> types,
      @RequestParam(required = false) UUID feeEarnerId,
      @RequestParam(required = false) UUID matterId,
      @RequestParam(defaultValue = "true") boolean includeOverdue,
      ActorContext actor) {
    var result =
        diaryService.getEvents(
            actor, new DiaryQuery(from, to, types, feeEarnerId, matterId, includeOverdue));
    return ResponseEntity.ok()
        .header(DiaryService.TYPES_HEADER, result.typesHeader())
        .body(result.events());
  }
}
