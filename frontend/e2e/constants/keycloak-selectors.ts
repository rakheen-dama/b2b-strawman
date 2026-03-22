/**
 * Keycloak form selectors matching the custom Keycloakify React theme.
 * Source: compose/keycloak/theme/src/login/pages/Login.tsx
 *         compose/keycloak/theme/src/login/pages/Register.tsx
 *
 * To discover selectors: start the dev stack, navigate to
 * http://localhost:8180/realms/docteams/account/ and inspect the login form.
 */
export const SELECTORS = {
  LOGIN: {
    USERNAME_INPUT: '#username',
    PASSWORD_INPUT: '#password',
    SUBMIT_BUTTON: 'button[name="login"]',
  },
  REGISTER: {
    FIRST_NAME_INPUT: '#firstName',
    LAST_NAME_INPUT: '#lastName',
    EMAIL_INPUT: '#email',
    PASSWORD_INPUT: '#password',
    PASSWORD_CONFIRM_INPUT: '#password-confirm',
    SUBMIT_BUTTON: 'button[type="submit"]',
  },
} as const
