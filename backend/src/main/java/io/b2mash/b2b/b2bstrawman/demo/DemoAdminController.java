package io.b2mash.b2b.b2bstrawman.demo;

import io.b2mash.b2b.b2bstrawman.demo.DemoDtos.DemoProvisionRequest;
import io.b2mash.b2b.b2bstrawman.demo.DemoDtos.DemoProvisionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform-admin/demo")
@PreAuthorize("@platformSecurityService.isPlatformAdmin()")
public class DemoAdminController {

  private final DemoProvisionService demoProvisionService;

  public DemoAdminController(DemoProvisionService demoProvisionService) {
    this.demoProvisionService = demoProvisionService;
  }

  @PostMapping("/provision")
  public ResponseEntity<DemoProvisionResponse> provisionDemo(
      @Valid @RequestBody DemoProvisionRequest request, JwtAuthenticationToken auth) {
    return ResponseEntity.ok(
        demoProvisionService.provisionDemo(request, auth.getToken().getSubject()));
  }
}
