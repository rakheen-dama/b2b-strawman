/**
 * Keycloak login/registration page selectors.
 * Based on the docteams Keycloakify theme (compose/keycloak/theme/).
 * If theme changes, update selectors here — all fixtures reference this file.
 */
export const KC_LOGIN = {
  username: '#username',
  password: '#password',
  submit: 'input[name="login"], button[type="submit"]',
  error: '[role="alert"]',
} as const

export const KC_REGISTER = {
  firstName: '#firstName',
  lastName: '#lastName',
  email: '#email',
  password: '#password',
  passwordConfirm: '#password-confirm',
  submit: 'button[type="submit"], input[type="submit"]',
} as const
