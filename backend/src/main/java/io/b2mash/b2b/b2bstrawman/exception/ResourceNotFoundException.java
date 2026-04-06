package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ResourceNotFoundException extends ErrorResponseException {

  public ResourceNotFoundException(String resourceType, Object id) {
    super(
        HttpStatus.NOT_FOUND,
        ProblemDetailFactory.create(
            HttpStatus.NOT_FOUND,
            resourceType + " not found",
            "No " + resourceType.toLowerCase() + " found with id " + id),
        null);
  }

  public static ResourceNotFoundException withDetail(String title, String detail) {
    return new ResourceNotFoundException(title, detail, HttpStatus.NOT_FOUND);
  }

  private ResourceNotFoundException(String title, String detail, HttpStatus status) {
    super(status, ProblemDetailFactory.create(status, title, detail), null);
  }
}
