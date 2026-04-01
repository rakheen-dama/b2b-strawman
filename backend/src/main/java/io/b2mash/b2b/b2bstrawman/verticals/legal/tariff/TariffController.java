package io.b2mash.b2b.b2bstrawman.verticals.legal.tariff;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffService.CreateItemRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffService.CreateScheduleRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffService.ScheduleResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffService.TariffItemResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffService.UpdateItemRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffService.UpdateScheduleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TariffController {

  private final TariffService tariffService;

  public TariffController(TariffService tariffService) {
    this.tariffService = tariffService;
  }

  @GetMapping("/tariff-schedules")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<List<ScheduleResponse>> listSchedules() {
    return ResponseEntity.ok(tariffService.listSchedules());
  }

  @GetMapping("/tariff-schedules/{id}")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<ScheduleResponse> getSchedule(@PathVariable UUID id) {
    return ResponseEntity.ok(tariffService.getSchedule(id));
  }

  @GetMapping("/tariff-schedules/active")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<List<ScheduleResponse>> getActiveSchedule(
      @RequestParam String category, @RequestParam String courtLevel) {
    return ResponseEntity.ok(tariffService.getActiveSchedule(category, courtLevel));
  }

  @PostMapping("/tariff-schedules")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<ScheduleResponse> createSchedule(
      @Valid @RequestBody CreateScheduleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(tariffService.createSchedule(request));
  }

  @PutMapping("/tariff-schedules/{id}")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<ScheduleResponse> updateSchedule(
      @PathVariable UUID id, @Valid @RequestBody UpdateScheduleRequest request) {
    return ResponseEntity.ok(tariffService.updateSchedule(id, request));
  }

  @DeleteMapping("/tariff-schedules/{id}")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<Void> deleteSchedule(@PathVariable UUID id) {
    tariffService.deleteSchedule(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/tariff-schedules/{id}/clone")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<ScheduleResponse> cloneSchedule(@PathVariable UUID id) {
    return ResponseEntity.status(HttpStatus.CREATED).body(tariffService.cloneSchedule(id));
  }

  @GetMapping("/tariff-items")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<List<TariffItemResponse>> searchItems(
      @RequestParam UUID scheduleId,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String section) {
    return ResponseEntity.ok(tariffService.searchItems(scheduleId, search, section));
  }

  @GetMapping("/tariff-items/{id}")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<TariffItemResponse> getItem(@PathVariable UUID id) {
    return ResponseEntity.ok(tariffService.getItem(id));
  }

  @PostMapping("/tariff-schedules/{id}/items")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<TariffItemResponse> createItem(
      @PathVariable UUID id, @Valid @RequestBody CreateItemRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(tariffService.createItem(id, request));
  }

  @PutMapping("/tariff-items/{id}")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<TariffItemResponse> updateItem(
      @PathVariable UUID id, @Valid @RequestBody UpdateItemRequest request) {
    return ResponseEntity.ok(tariffService.updateItem(id, request));
  }

  @DeleteMapping("/tariff-items/{id}")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<Void> deleteItem(@PathVariable UUID id) {
    tariffService.deleteItem(id);
    return ResponseEntity.noContent().build();
  }
}
