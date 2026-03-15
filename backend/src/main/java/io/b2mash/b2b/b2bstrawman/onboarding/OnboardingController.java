package io.b2mash.b2b.b2bstrawman.onboarding;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

  private final OnboardingService onboardingService;

  public OnboardingController(OnboardingService onboardingService) {
    this.onboardingService = onboardingService;
  }

  @GetMapping("/progress")
  public ResponseEntity<OnboardingProgressResponse> getProgress() {
    return ResponseEntity.ok(onboardingService.getProgress());
  }

  @PostMapping("/dismiss")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<Void> dismiss() {
    onboardingService.dismiss();
    return ResponseEntity.noContent().build();
  }
}
