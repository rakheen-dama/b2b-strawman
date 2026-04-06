package io.b2mash.b2b.b2bstrawman.customer.dto;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CustomerDtos {

  private CustomerDtos() {}

  public record CreateCustomerRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @NotBlank(message = "email is required")
          @Email(message = "email must be a valid email address")
          @Size(max = 255, message = "email must be at most 255 characters")
          String email,
      @Size(max = 50, message = "phone must be at most 50 characters") String phone,
      @Size(max = 100, message = "idNumber must be at most 100 characters") String idNumber,
      String notes,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      CustomerType customerType) {}

  public record UpdateCustomerRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @NotBlank(message = "email is required")
          @Email(message = "email must be a valid email address")
          @Size(max = 255, message = "email must be at most 255 characters")
          String email,
      @Size(max = 50, message = "phone must be at most 50 characters") String phone,
      @Size(max = 100, message = "idNumber must be at most 100 characters") String idNumber,
      String notes,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {}

  public record CustomerResponse(
      UUID id,
      String name,
      String email,
      String phone,
      String idNumber,
      String status,
      String notes,
      UUID createdBy,
      String createdByName,
      Instant createdAt,
      Instant updatedAt,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      List<TagResponse> tags,
      LifecycleStatus lifecycleStatus,
      CustomerType customerType,
      Instant lifecycleStatusChangedAt) {

    public static CustomerResponse from(Customer customer) {
      return from(customer, List.of(), Map.of());
    }

    public static CustomerResponse from(Customer customer, List<TagResponse> tags) {
      return from(customer, tags, Map.of());
    }

    public static CustomerResponse from(
        Customer customer, List<TagResponse> tags, Map<UUID, String> memberNames) {
      return new CustomerResponse(
          customer.getId(),
          customer.getName(),
          customer.getEmail(),
          customer.getPhone(),
          customer.getIdNumber(),
          customer.getStatus(),
          customer.getNotes(),
          customer.getCreatedBy(),
          customer.getCreatedBy() != null ? memberNames.get(customer.getCreatedBy()) : null,
          customer.getCreatedAt(),
          customer.getUpdatedAt(),
          customer.getCustomFields(),
          customer.getAppliedFieldGroups(),
          tags,
          customer.getLifecycleStatus(),
          customer.getCustomerType(),
          customer.getLifecycleStatusChangedAt());
    }
  }

  public record CustomerProjectResponse(
      UUID customerId, UUID projectId, UUID linkedBy, Instant createdAt) {

    public static CustomerProjectResponse from(CustomerProject link) {
      return new CustomerProjectResponse(
          link.getCustomerId(), link.getProjectId(), link.getLinkedBy(), link.getCreatedAt());
    }
  }

  public record LinkedProjectResponse(UUID id, String name, String description, Instant createdAt) {

    public static LinkedProjectResponse from(Project project) {
      return new LinkedProjectResponse(
          project.getId(), project.getName(), project.getDescription(), project.getCreatedAt());
    }
  }

  public record TransitionRequest(@NotBlank String targetStatus, String notes) {}

  public record TransitionResponse(
      UUID id,
      String name,
      String lifecycleStatus,
      Instant lifecycleStatusChangedAt,
      UUID lifecycleStatusChangedBy,
      String lifecycleStatusChangedByName) {

    public static TransitionResponse from(Customer customer) {
      return from(customer, Map.of());
    }

    public static TransitionResponse from(Customer customer, Map<UUID, String> memberNames) {
      return new TransitionResponse(
          customer.getId(),
          customer.getName(),
          customer.getLifecycleStatus().name(),
          customer.getLifecycleStatusChangedAt(),
          customer.getLifecycleStatusChangedBy(),
          customer.getLifecycleStatusChangedBy() != null
              ? memberNames.get(customer.getLifecycleStatusChangedBy())
              : null);
    }
  }

  public record DormancyCheckResult(int thresholdDays, List<DormancyCandidate> candidates) {}

  public record DormancyCandidate(
      UUID customerId, String customerName, Instant lastActivityDate, long daysSinceActivity) {}
}
