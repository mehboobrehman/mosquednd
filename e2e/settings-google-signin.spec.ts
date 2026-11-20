import { test, expect, Page } from '@playwright/test';

test.describe('Settings - Google Account Sign-in Flow', () => {
  
  test.beforeEach(async ({ page }: { page: Page }) => {
    page.on('console', (msg: any) => console.log(`BROWSER: ${msg.text()}`));
    await page.addInitScript(() => localStorage.clear());
    await page.goto('/settings-signin.html');
  });

  test.afterEach(async ({ page }: { page: Page }) => {
    try {
      await page.evaluate(() => localStorage.clear());
    } catch (e) {
      // ignore if context destroyed
    }
  });

  test('1. Initial State: displays unlinked/disconnected state correctly', async ({ page }: { page: Page }) => {
    const authSection = page.locator('[data-testid="google-auth-section"]');
    await expect(authSection).toBeVisible();

    const status = page.locator('[data-testid="google-auth-status"]');
    await expect(status).toHaveText('Not connected');

    const signInButton = page.locator('[data-testid="google-auth-button"]');
    await expect(signInButton).toBeVisible();
    await expect(signInButton).toHaveText(/Sign in with Google/i);

    const profileContainer = page.locator('[data-testid="google-user-profile"]');
    await expect(profileContainer).not.toBeVisible();
  });

  test('2. Successful Connection: connects account and updates UI with email and avatar', async ({ page }: { page: Page }) => {
    await page.evaluate(() => {
      // @ts-ignore
      if (typeof window.setTestMode === 'function') window.setTestMode('success');
    });

    const signInButton = page.locator('[data-testid="google-auth-button"]');
    await signInButton.click();

    const status = page.locator('[data-testid="google-auth-status"]');
    await expect(status).toHaveText('Connected', { timeout: 5000 });

    await expect(signInButton).toHaveText(/Disconnect/i);

    const profileContainer = page.locator('[data-testid="google-user-profile"]');
    await expect(profileContainer).toBeVisible();

    const email = page.locator('[data-testid="google-email"]');
    await expect(email).toHaveText('mehboob.rehman@live.com');

    const avatar = page.locator('[data-testid="google-avatar"]');
    await expect(avatar).toBeVisible();

    const toast = page.locator('[data-testid="error-toast"]');
    await expect(toast).toContainText(/Successfully signed in/i);
  });

  test('3. Revoke / Disconnect: clicks Disconnect, confirms prompt, reverts to unlinked state', async ({ page }: { page: Page }) => {
    await page.evaluate(() => {
      // @ts-ignore
      if (typeof window.setTestMode === 'function') window.setTestMode('success');
    });

    await signInUser(page);

    const disconnectButton = page.locator('[data-testid="google-auth-button"]');
    await expect(disconnectButton).toHaveText(/Disconnect/i);
    await disconnectButton.click();

    const modal = page.locator('[data-testid="disconnect-modal"]');
    await expect(modal).toBeVisible();

    const confirmBtn = page.locator('[data-testid="confirm-disconnect-btn"]');
    await confirmBtn.click();

    const status = page.locator('[data-testid="google-auth-status"]');
    await expect(status).toHaveText('Not connected', { timeout: 5000 });

    const signInButton = page.locator('[data-testid="google-auth-button"]');
    await expect(signInButton).toHaveText(/Sign in with Google/i);

    const profileContainer = page.locator('[data-testid="google-user-profile"]');
    await expect(profileContainer).not.toBeVisible();
  });

  test('4. Error Handling: simulates OAuth failure and verifies error toast without hanging', async ({ page }: { page: Page }) => {
    await page.evaluate(() => {
      // @ts-ignore
      if (typeof window.setTestMode === 'function') window.setTestMode('error');
    });

    const signInButton = page.locator('[data-testid="google-auth-button"]');
    await signInButton.click();

    const toast = page.locator('[data-testid="error-toast"]');
    await expect(toast).toBeVisible({ timeout: 5000 });
    await expect(toast).toContainText(/failed|error/i);

    await expect(signInButton).toBeEnabled();
    await expect(signInButton).toHaveText(/Sign in with Google/i);

    const status = page.locator('[data-testid="google-auth-status"]');
    await expect(status).toHaveText('Not connected');
  });

  test('4b. Error Handling: simulates network timeout and verifies error toast', async ({ page }: { page: Page }) => {
    await page.evaluate(() => {
      // @ts-ignore
      if (typeof window.setTestMode === 'function') window.setTestMode('timeout');
    });

    const signInButton = page.locator('[data-testid="google-auth-button"]');
    await signInButton.click();

    const toast = page.locator('[data-testid="error-toast"]');
    await expect(toast).toBeVisible({ timeout: 10000 });
    await expect(toast).toContainText(/timeout|connection/i);

    await expect(signInButton).toBeEnabled();
  });
});

async function signInUser(page: Page) {
  const signInButton = page.locator('[data-testid="google-auth-button"]');
  await signInButton.click();
  await expect(page.locator('[data-testid="google-auth-status"]')).toHaveText('Connected', { timeout: 5000 });
}
