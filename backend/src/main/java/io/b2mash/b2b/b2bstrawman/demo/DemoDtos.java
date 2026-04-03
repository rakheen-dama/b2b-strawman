package io.b2mash.b2b.b2bstrawman.demo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public final class DemoDtos {

  private DemoDtos() {}

  public record DemoProvisionRequest(
      @NotBlank String organizationName,
      @NotBlank String verticalProfile,
      @NotBlank @Email String adminEmail,
      boolean seedDemoData) {}

  public record DemoProvisionResponse(
      UUID organizationId,
      String organizationSlug,
      String organizationName,
      String verticalProfile,
      String loginUrl,
      boolean demoDataSeeded,
      String adminNote,
      String tempPassword) {}

  public record DemoReseedResponse(
      UUID organizationId,
      String organizationName,
      boolean success,
      String verticalProfile,
      String error) {}

  public record DemoCleanupRequest(@NotBlank String confirmOrganizationName) {}

  public record DemoCleanupResponse(
      UUID organizationId,
      String organizationName,
      boolean keycloakCleaned,
      boolean schemaCleaned,
      boolean publicRecordsCleaned,
      boolean s3Cleaned,
      List<String> errors) {}
}
