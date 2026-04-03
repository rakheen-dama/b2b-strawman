package io.b2mash.b2b.b2bstrawman.demo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
}
