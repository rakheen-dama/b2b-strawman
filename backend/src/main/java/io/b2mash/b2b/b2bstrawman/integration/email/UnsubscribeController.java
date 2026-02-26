package io.b2mash.b2b.b2bstrawman.integration.email;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
public class UnsubscribeController {

  private final UnsubscribeService unsubscribeService;

  public UnsubscribeController(UnsubscribeService unsubscribeService) {
    this.unsubscribeService = unsubscribeService;
  }

  @GetMapping("/unsubscribe")
  public ResponseEntity<String> unsubscribe(@RequestParam String token) {
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(unsubscribeService.processUnsubscribe(token));
  }
}
