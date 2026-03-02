package com.docteams.keycloak;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessToken;

/**
 * Keycloak protocol mapper that injects a DocTeams-compatible organization role claim.
 *
 * <p>Produces: {@code "o": { "id": "<org-uuid>", "rol": "<role>", "slg": "<alias>" }}
 *
 * <p>This matches the JWT v2 format expected by the DocTeams backend (JwtClaimExtractor). The role
 * is resolved from the user attribute {@code org:<orgId>:role}, which is set during member
 * provisioning. If no role attribute is found, defaults to "member".
 */
public class OrgRoleProtocolMapper extends AbstractOIDCProtocolMapper
    implements OIDCAccessTokenMapper {

  public static final String PROVIDER_ID = "docteams-org-role-mapper";

  /** User attribute pattern for org role: {@code org:<orgId>:role}. */
  static final String ORG_ROLE_ATTRIBUTE_PREFIX = "org:";

  static final String ORG_ROLE_ATTRIBUTE_SUFFIX = ":role";

  static final String DEFAULT_ROLE = "member";

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayType() {
    return "DocTeams Org Role Mapper";
  }

  @Override
  public String getDisplayCategory() {
    return TOKEN_MAPPER_CATEGORY;
  }

  @Override
  public String getHelpText() {
    return "Injects organization ID, role, and slug as the 'o' claim in DocTeams JWT format";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return List.of();
  }

  @Override
  public AccessToken transformAccessToken(
      AccessToken token,
      ProtocolMapperModel mappingModel,
      KeycloakSession session,
      UserSessionModel userSession,
      ClientSessionContext clientSessionCtx) {

    // 1. Read active org from kc_org session note
    String kcOrg = clientSessionCtx.getClientSession().getNote("kc_org");
    if (kcOrg == null) {
      return token;
    }

    // 2. Get OrganizationProvider
    OrganizationProvider orgProvider = session.getProvider(OrganizationProvider.class);
    if (orgProvider == null) {
      return token;
    }

    // 3. Resolve org (kc_org can be alias or ID)
    OrganizationModel org = orgProvider.getByAlias(kcOrg);
    if (org == null) {
      org = orgProvider.getById(kcOrg);
    }
    if (org == null) {
      return token;
    }

    // 4. Check user membership
    UserModel user = userSession.getUser();
    if (!org.isMember(user)) {
      return token;
    }

    // 5. Resolve role from user attribute (defaults to "member")
    String role = resolveRole(org.getId(), user);

    // 6. Build and set the "o" claim
    Map<String, Object> orgClaim = new HashMap<>();
    orgClaim.put("id", org.getId());
    orgClaim.put("rol", role);
    orgClaim.put("slg", org.getAlias());
    token.getOtherClaims().put("o", orgClaim);

    return token;
  }

  /**
   * Resolves the user's role within the organization from the user attribute {@code
   * org:<orgId>:role}. Returns "member" if no role attribute is set.
   */
  String resolveRole(String orgId, UserModel user) {
    String attributeKey = ORG_ROLE_ATTRIBUTE_PREFIX + orgId + ORG_ROLE_ATTRIBUTE_SUFFIX;
    List<String> values = user.getAttributeStream(attributeKey).toList();
    if (values.isEmpty()) {
      return DEFAULT_ROLE;
    }
    return values.getFirst();
  }
}
