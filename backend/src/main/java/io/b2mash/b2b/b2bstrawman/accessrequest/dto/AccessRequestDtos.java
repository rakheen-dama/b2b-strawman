package io.b2mash.b2b.b2bstrawman.accessrequest.dto;

import io.b2mash.b2b.b2bstrawman.accessrequest.AccessRequest;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class AccessRequestDtos {

  private AccessRequestDtos() {}

  public record AccessRequestSubmission(
      @NotBlank String email,
      @NotBlank String fullName,
      @NotBlank String organizationName,
      @NotBlank String country,
      @NotBlank String industry) {}

  public record OtpVerifyRequest(@NotBlank String email, @NotBlank String otp) {}

  public record AccessRequestResponse(
      UUID id,
      String email,
      String fullName,
      String organizationName,
      String country,
      String industry,
      String status,
      Instant otpVerifiedAt,
      Instant reviewedAt,
      String reviewedBy,
      Instant createdAt,
      Instant updatedAt) {

    public static AccessRequestResponse from(AccessRequest entity) {
      return new AccessRequestResponse(
          entity.getId(),
          entity.getEmail(),
          entity.getFullName(),
          entity.getOrganizationName(),
          entity.getCountry(),
          entity.getIndustry(),
          entity.getStatus().name(),
          entity.getOtpVerifiedAt(),
          entity.getReviewedAt(),
          entity.getReviewedBy(),
          entity.getCreatedAt(),
          entity.getUpdatedAt());
    }
  }

  public record SubmitResponse(String message, int expiresInMinutes) {}

  public record VerifyResponse(String message) {}
}
