package io.b2mash.b2b.b2bstrawman.onboarding;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
  @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN', 'ROLE_ORG_OWNER')")
  public ResponseEntity<Void> dismiss() {
    onboardingService.dismiss();
    return ResponseEntity.noContent().build();
  }
}
