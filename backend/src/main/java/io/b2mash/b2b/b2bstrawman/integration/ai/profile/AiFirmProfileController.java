package io.b2mash.b2b.b2bstrawman.integration.ai.profile;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiFirmProfileController {

  private final AiFirmProfileService profileService;

  public AiFirmProfileController(AiFirmProfileService profileService) {
    this.profileService = profileService;
  }

  @GetMapping("/profile")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_MANAGE")
  public ResponseEntity<AiFirmProfileResponse> getProfile() {
    return ResponseEntity.ok(AiFirmProfileResponse.from(profileService.getOrCreateProfile()));
  }

  @PutMapping("/profile")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_MANAGE")
  public ResponseEntity<AiFirmProfileResponse> updateProfile(
      @RequestBody UpdateAiFirmProfileRequest request) {
    return ResponseEntity.ok(AiFirmProfileResponse.from(profileService.updateProfile(request)));
  }
}
