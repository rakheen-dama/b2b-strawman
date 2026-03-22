/**
 * Keycloak form selectors. These match the default Keycloak theme.
 * If the docteams theme customizes the login page, update these selectors.
 *
 * To discover selectors: start the dev stack, navigate to
 * http://localhost:8180/realms/docteams/account/ and inspect the login form.
 */
export const SELECTORS = {
  LOGIN: {
    USERNAME_INPUT: '#username',
    PASSWORD_INPUT: '#password',
    SUBMIT_BUTTON: '#kc-login',
  },
  REGISTER: {
    FIRST_NAME_INPUT: '#firstName',
    LAST_NAME_INPUT: '#lastName',
    PASSWORD_INPUT: '#password',
    PASSWORD_CONFIRM_INPUT: '#password-confirm',
    SUBMIT_BUTTON: 'input[type="submit"], button[type="submit"]',
  },
} as const
