package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnGoLiveService.TakeOnGoLiveAcknowledgeRequest;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnGoLiveService.TakeOnGoLiveItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/take-on/go-live-checklist} (Phase 93 656A, §17.4.2): the firm's four go-live
 * acknowledgements. Every method needs {@code MANAGE_TAKE_ON} (§17.9); each returns the whole
 * checklist. One service call per method.
 */
@RestController
public class TakeOnGoLiveController {

  private final TakeOnGoLiveService service;

  public TakeOnGoLiveController(TakeOnGoLiveService service) {
    this.service = service;
  }

  @GetMapping("/api/take-on/go-live-checklist")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<List<TakeOnGoLiveItemResponse>> list() {
    return ResponseEntity.ok(service.list());
  }

  @PutMapping("/api/take-on/go-live-checklist/{itemKey}")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<List<TakeOnGoLiveItemResponse>> acknowledge(
      @PathVariable GoLiveItem itemKey,
      @Valid @RequestBody(required = false) TakeOnGoLiveAcknowledgeRequest request) {
    return ResponseEntity.ok(service.acknowledge(itemKey, request));
  }

  @DeleteMapping("/api/take-on/go-live-checklist/{itemKey}")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<List<TakeOnGoLiveItemResponse>> withdraw(@PathVariable GoLiveItem itemKey) {
    return ResponseEntity.ok(service.withdraw(itemKey));
  }
}
