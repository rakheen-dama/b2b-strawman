package io.b2mash.b2b.b2bstrawman.accessrequest;

import io.b2mash.b2b.b2bstrawman.accessrequest.dto.AccessRequestDtos.AccessRequestSubmission;
import io.b2mash.b2b.b2bstrawman.accessrequest.dto.AccessRequestDtos.OtpVerifyRequest;
import io.b2mash.b2b.b2bstrawman.accessrequest.dto.AccessRequestDtos.SubmitResponse;
import io.b2mash.b2b.b2bstrawman.accessrequest.dto.AccessRequestDtos.VerifyResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access-requests")
public class AccessRequestPublicController {

  private final AccessRequestService accessRequestService;

  public AccessRequestPublicController(AccessRequestService accessRequestService) {
    this.accessRequestService = accessRequestService;
  }

  @PostMapping
  public ResponseEntity<SubmitResponse> submitRequest(
      @Valid @RequestBody AccessRequestSubmission submission) {
    return ResponseEntity.ok(accessRequestService.submitRequest(submission));
  }

  @PostMapping("/verify")
  public ResponseEntity<VerifyResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
    return ResponseEntity.ok(accessRequestService.verifyOtp(request.email(), request.otp()));
  }
}
