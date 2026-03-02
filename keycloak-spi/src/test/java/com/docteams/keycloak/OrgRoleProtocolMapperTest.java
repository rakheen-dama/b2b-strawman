package com.docteams.keycloak;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.representations.AccessToken;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrgRoleProtocolMapperTest {

  private OrgRoleProtocolMapper mapper;

  @Mock private KeycloakSession session;
  @Mock private UserSessionModel userSession;
  @Mock private ClientSessionContext clientSessionCtx;
  @Mock private AuthenticatedClientSessionModel clientSession;
  @Mock private ProtocolMapperModel mappingModel;
  @Mock private OrganizationProvider orgProvider;
  @Mock private OrganizationModel org;
  @Mock private UserModel user;

  private AccessToken token;

  private static final String ORG_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String ORG_ALIAS = "acme-corp";

  @BeforeEach
  void setUp() {
    mapper = new OrgRoleProtocolMapper();
    token = new AccessToken();
    lenient().when(clientSessionCtx.getClientSession()).thenReturn(clientSession);
  }

  @Test
  void noKcOrgNote_skipsClaim() {
    when(clientSession.getNote("kc_org")).thenReturn(null);

    AccessToken result =
        mapper.transformAccessToken(token, mappingModel, session, userSession, clientSessionCtx);

    assertNull(result.getOtherClaims().get("o"));
  }

  @Test
  void noOrgProvider_skipsClaim() {
    when(clientSession.getNote("kc_org")).thenReturn(ORG_ALIAS);
    when(session.getProvider(OrganizationProvider.class)).thenReturn(null);

    AccessToken result =
        mapper.transformAccessToken(token, mappingModel, session, userSession, clientSessionCtx);

    assertNull(result.getOtherClaims().get("o"));
  }

  @Test
  void orgNotFound_skipsClaim() {
    when(clientSession.getNote("kc_org")).thenReturn(ORG_ALIAS);
    when(session.getProvider(OrganizationProvider.class)).thenReturn(orgProvider);
    when(orgProvider.getByAlias(ORG_ALIAS)).thenReturn(null);
    when(orgProvider.getById(ORG_ALIAS)).thenReturn(null);

    AccessToken result =
        mapper.transformAccessToken(token, mappingModel, session, userSession, clientSessionCtx);

    assertNull(result.getOtherClaims().get("o"));
  }

  @Test
  void userNotMember_skipsClaim() {
    when(clientSession.getNote("kc_org")).thenReturn(ORG_ALIAS);
    when(session.getProvider(OrganizationProvider.class)).thenReturn(orgProvider);
    when(orgProvider.getByAlias(ORG_ALIAS)).thenReturn(org);
    when(userSession.getUser()).thenReturn(user);
    when(org.isMember(user)).thenReturn(false);

    AccessToken result =
        mapper.transformAccessToken(token, mappingModel, session, userSession, clientSessionCtx);

    assertNull(result.getOtherClaims().get("o"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void memberWithNoRoleAttribute_getsDefaultMemberRole() {
    when(clientSession.getNote("kc_org")).thenReturn(ORG_ALIAS);
    when(session.getProvider(OrganizationProvider.class)).thenReturn(orgProvider);
    when(orgProvider.getByAlias(ORG_ALIAS)).thenReturn(org);
    when(userSession.getUser()).thenReturn(user);
    when(org.isMember(user)).thenReturn(true);
    when(org.getId()).thenReturn(ORG_ID);
    when(org.getAlias()).thenReturn(ORG_ALIAS);
    when(user.getAttributeStream("org:" + ORG_ID + ":role")).thenReturn(Stream.empty());

    AccessToken result =
        mapper.transformAccessToken(token, mappingModel, session, userSession, clientSessionCtx);

    Object oClaim = result.getOtherClaims().get("o");
    assertNotNull(oClaim);
    assertInstanceOf(Map.class, oClaim);
    Map<String, Object> orgMap = (Map<String, Object>) oClaim;
    assertEquals(ORG_ID, orgMap.get("id"));
    assertEquals("member", orgMap.get("rol"));
    assertEquals(ORG_ALIAS, orgMap.get("slg"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void memberWithOwnerRole_getsOwnerInClaim() {
    when(clientSession.getNote("kc_org")).thenReturn(ORG_ALIAS);
    when(session.getProvider(OrganizationProvider.class)).thenReturn(orgProvider);
    when(orgProvider.getByAlias(ORG_ALIAS)).thenReturn(org);
    when(userSession.getUser()).thenReturn(user);
    when(org.isMember(user)).thenReturn(true);
    when(org.getId()).thenReturn(ORG_ID);
    when(org.getAlias()).thenReturn(ORG_ALIAS);
    when(user.getAttributeStream("org:" + ORG_ID + ":role")).thenReturn(Stream.of("owner"));

    AccessToken result =
        mapper.transformAccessToken(token, mappingModel, session, userSession, clientSessionCtx);

    Map<String, Object> orgMap = (Map<String, Object>) result.getOtherClaims().get("o");
    assertEquals("owner", orgMap.get("rol"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void memberWithAdminRole_getsAdminInClaim() {
    when(clientSession.getNote("kc_org")).thenReturn(ORG_ALIAS);
    when(session.getProvider(OrganizationProvider.class)).thenReturn(orgProvider);
    when(orgProvider.getByAlias(ORG_ALIAS)).thenReturn(org);
    when(userSession.getUser()).thenReturn(user);
    when(org.isMember(user)).thenReturn(true);
    when(org.getId()).thenReturn(ORG_ID);
    when(org.getAlias()).thenReturn(ORG_ALIAS);
    when(user.getAttributeStream("org:" + ORG_ID + ":role")).thenReturn(Stream.of("admin"));

    AccessToken result =
        mapper.transformAccessToken(token, mappingModel, session, userSession, clientSessionCtx);

    Map<String, Object> orgMap = (Map<String, Object>) result.getOtherClaims().get("o");
    assertEquals("admin", orgMap.get("rol"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void resolvedViaOrgId_whenAliasNotFound() {
    when(clientSession.getNote("kc_org")).thenReturn(ORG_ID);
    when(session.getProvider(OrganizationProvider.class)).thenReturn(orgProvider);
    when(orgProvider.getByAlias(ORG_ID)).thenReturn(null);
    when(orgProvider.getById(ORG_ID)).thenReturn(org);
    when(userSession.getUser()).thenReturn(user);
    when(org.isMember(user)).thenReturn(true);
    when(org.getId()).thenReturn(ORG_ID);
    when(org.getAlias()).thenReturn(ORG_ALIAS);
    when(user.getAttributeStream("org:" + ORG_ID + ":role")).thenReturn(Stream.empty());

    AccessToken result =
        mapper.transformAccessToken(token, mappingModel, session, userSession, clientSessionCtx);

    Object oClaim = result.getOtherClaims().get("o");
    assertNotNull(oClaim);
    Map<String, Object> orgMap = (Map<String, Object>) oClaim;
    assertEquals(ORG_ID, orgMap.get("id"));
    assertEquals(ORG_ALIAS, orgMap.get("slg"));
  }

  @Test
  void resolveRole_returnsDefaultWhenNoAttribute() {
    when(user.getAttributeStream("org:" + ORG_ID + ":role")).thenReturn(Stream.empty());

    String role = mapper.resolveRole(ORG_ID, user);

    assertEquals("member", role);
  }

  @Test
  void resolveRole_returnsAttributeValue() {
    when(user.getAttributeStream("org:" + ORG_ID + ":role")).thenReturn(Stream.of("owner"));

    String role = mapper.resolveRole(ORG_ID, user);

    assertEquals("owner", role);
  }

  @Test
  void providerIdIsCorrect() {
    assertEquals("docteams-org-role-mapper", mapper.getId());
  }

  @Test
  void displayMetadataIsSet() {
    assertNotNull(mapper.getDisplayType());
    assertNotNull(mapper.getDisplayCategory());
    assertNotNull(mapper.getHelpText());
  }

  @Test
  void configPropertiesIsEmpty() {
    assertTrue(mapper.getConfigProperties().isEmpty());
  }
}
