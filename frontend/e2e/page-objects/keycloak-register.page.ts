import { Page } from '@playwright/test'
import { SELECTORS } from '../constants/keycloak-selectors'

export class KeycloakRegisterPage {
  constructor(private page: Page) {}

  async waitForReady(): Promise<void> {
    await this.page.waitForSelector(SELECTORS.REGISTER.FIRST_NAME_INPUT, {
      timeout: 15_000,
    })
  }

  async register(
    firstName: string,
    lastName: string,
    password: string
  ): Promise<void> {
    await this.page.fill(SELECTORS.REGISTER.FIRST_NAME_INPUT, firstName)
    await this.page.fill(SELECTORS.REGISTER.LAST_NAME_INPUT, lastName)
    await this.page.fill(SELECTORS.REGISTER.PASSWORD_INPUT, password)
    await this.page.fill(SELECTORS.REGISTER.PASSWORD_CONFIRM_INPUT, password)
    await this.page.click(SELECTORS.REGISTER.SUBMIT_BUTTON)
  }
}
