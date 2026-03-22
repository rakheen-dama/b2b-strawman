import { Page } from '@playwright/test'
import { SELECTORS } from '../constants/keycloak-selectors'

export class KeycloakLoginPage {
  constructor(private page: Page) {}

  async waitForReady(): Promise<void> {
    await this.page.waitForSelector(SELECTORS.LOGIN.USERNAME_INPUT, {
      timeout: 15_000,
    })
  }

  async login(email: string, password: string): Promise<void> {
    await this.page.fill(SELECTORS.LOGIN.USERNAME_INPUT, email)
    await this.page.fill(SELECTORS.LOGIN.PASSWORD_INPUT, password)
    await this.page.click(SELECTORS.LOGIN.SUBMIT_BUTTON)
  }
}
