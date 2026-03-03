package io.b2mash.b2b.b2bstrawman.keycloak;

import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.KeycloakInvitation;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.KeycloakOrganization;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * REST client for the Keycloak Admin API. Manages organizations, members, and invitations.
 * Conditionally activated only when {@code keycloak.admin.enabled=true}.
 */
@Service
@ConditionalOnProperty(name = "keycloak.admin.enabled", havingValue = "true")
public class KeycloakAdminService {

  private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

  private final RestClient restClient;

  public KeycloakAdminService(RestClient keycloakAdminRestClient) {
    this.restClient = keycloakAdminRestClient;
  }

  /** Creates an organization in Keycloak. Returns the created org with its Keycloak-assigned ID. */
  public KeycloakOrganization createOrganization(String name, String alias) {
    log.debug("Creating Keycloak organization: name={}, alias={}", name, alias);

    var response =
        restClient
            .post()
            .uri("/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", name, "alias", alias, "enabled", true))
            .retrieve()
            .onStatus(
                HttpStatusCode::is4xxClientError,
                (request, errorResponse) -> {
                  if (errorResponse.getStatusCode() == HttpStatus.CONFLICT) {
                    throw new ResourceConflictException(
                        "Organization conflict",
                        "Organization with alias '" + alias + "' already exists");
                  }
                })
            .toBodilessEntity();

    var location = response.getHeaders().getLocation();
    var orgId = extractIdFromLocation(location);

    log.info("Created Keycloak organization: id={}, alias={}", orgId, alias);
    return new KeycloakOrganization(orgId, name, alias, true);
  }

  /** Deletes an organization from Keycloak. */
  public void deleteOrganization(String orgId) {
    log.debug("Deleting Keycloak organization: id={}", orgId);

    restClient
        .delete()
        .uri("/organizations/{orgId}", orgId)
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            (request, response) -> {
              if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Organization", orgId);
              }
            })
        .toBodilessEntity();

    log.info("Deleted Keycloak organization: id={}", orgId);
  }

  /** Adds an existing Keycloak user as a member of an organization. */
  public void addMember(String orgId, String userId) {
    log.debug("Adding member to Keycloak organization: orgId={}, userId={}", orgId, userId);

    restClient
        .post()
        .uri("/organizations/{orgId}/members", orgId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(userId)
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            (request, response) -> {
              if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Organization or User", orgId);
              }
              if (response.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ResourceConflictException(
                    "Member conflict",
                    "User '" + userId + "' is already a member of organization '" + orgId + "'");
              }
            })
        .toBodilessEntity();

    log.info("Added member to Keycloak organization: orgId={}, userId={}", orgId, userId);
  }

  /** Invites a user to an organization by email. Keycloak sends the invitation email. */
  public void inviteToOrganization(String orgId, String email) {
    log.debug("Inviting user to Keycloak organization: orgId={}, email={}", orgId, email);

    var formData = new LinkedMultiValueMap<String, String>();
    formData.add("email", email);

    restClient
        .post()
        .uri("/organizations/{orgId}/members/invite-user", orgId)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(formData)
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            (request, response) -> {
              if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Organization", orgId);
              }
            })
        .toBodilessEntity();

    log.info("Invited user to Keycloak organization: orgId={}, email={}", orgId, email);
  }

  /** Lists pending invitations for an organization. */
  public List<KeycloakInvitation> listInvitations(String orgId) {
    log.debug("Listing invitations for Keycloak organization: orgId={}", orgId);

    return restClient
        .get()
        .uri("/organizations/{orgId}/invitations", orgId)
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            (request, response) -> {
              if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Organization", orgId);
              }
            })
        .body(new ParameterizedTypeReference<>() {});
  }

  /** Cancels a pending invitation. */
  public void cancelInvitation(String orgId, String invitationId) {
    log.debug("Cancelling invitation: orgId={}, invitationId={}", orgId, invitationId);

    restClient
        .delete()
        .uri("/organizations/{orgId}/invitations/{invitationId}", orgId, invitationId)
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            (request, response) -> {
              if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Invitation", invitationId);
              }
            })
        .toBodilessEntity();

    log.info("Cancelled invitation: orgId={}, invitationId={}", orgId, invitationId);
  }

  /** Lists all organizations a user belongs to. */
  public List<KeycloakOrganization> getUserOrganizations(String userId) {
    log.debug("Getting organizations for user: userId={}", userId);

    return restClient
        .get()
        .uri("/organizations/members/{userId}/organizations", userId)
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            (request, response) -> {
              if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("User", userId);
              }
            })
        .body(new ParameterizedTypeReference<>() {});
  }

  private String extractIdFromLocation(URI location) {
    if (location == null) {
      throw new IllegalStateException("Keycloak did not return a Location header");
    }
    var path = location.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }
}
