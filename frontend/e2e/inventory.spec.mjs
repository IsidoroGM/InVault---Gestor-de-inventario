import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import {
  api,
  createUser,
  login,
  movement,
  seedFixture,
  settings,
} from '../../deploy/tests/quality-api.mjs';

let token;
test.beforeEach(async () => {
  token = (await login()).accessToken;
});

async function signIn(page, username = 'admin', password = settings().password) {
  await page.goto('/login');
  await page.getByLabel('Usuario', { exact: true }).fill(username);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Entrar en InVault' }).click();
}
async function visit(page, path) {
  await page.goto(path);
  await page.waitForLoadState('networkidle');
}
async function select(page, scope, label, option) {
  await scope.getByRole('combobox', { name: label, exact: true }).click();
  await page.getByRole('option', { name: option, exact: true }).click();
}
async function prepareMovement(page, fixture, quantity, type = 'Entrada') {
  await visit(page, '/movements');
  await page.getByRole('button', { name: 'Registrar movimiento', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await select(page, dialog, 'Producto', fixture.product.sku + ' · ' + fixture.product.name);
  await dialog.getByRole('combobox', { name: 'Lote', exact: true }).click();
  await page.getByRole('option').filter({ hasText: fixture.batch.batchCode }).click();
  await select(page, dialog, 'Tipo', type);
  await dialog.getByLabel('Cantidad', { exact: true }).fill(String(quantity));
  return dialog;
}
async function confirmMovement(page, dialog) {
  const response = page.waitForResponse(
    (r) => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/stock/movements',
  );
  await dialog.getByRole('button', { name: /^(Registrar movimiento|Reintentar envío)$/ }).click();
  expect((await response).status()).toBe(201);
  await expect(dialog).toBeHidden();
}
async function quantity(fixture) {
  return (await api('/api/batches/' + fixture.batch.id, { token })).quantity;
}

test('temporary password, session revocation and logout work through the UI', async ({ page }) => {
  const user = await createUser(token, 'WAREHOUSE', { changePassword: false });
  const oldSession = await login(user.username, user.temporaryPassword);
  await signIn(page, user.username, user.temporaryPassword);
  await expect(page).toHaveURL(/\/change-password$/);
  await page.getByLabel('Contraseña actual', { exact: true }).fill(user.temporaryPassword);
  await page.getByLabel('Nueva contraseña', { exact: true }).fill(settings().password);
  await page.getByLabel('Confirmar nueva contraseña', { exact: true }).fill(settings().password);
  await page.getByRole('button', { name: 'Guardar nueva contraseña' }).click();
  await expect(page).toHaveURL(/\/login\?passwordChanged=1$/);
  await api('/api/products/active', { token: oldSession.accessToken, expected: 401 });
  await signIn(page, user.username);
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.getByRole('button', { name: 'Salir', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/products');
  await expect(page).toHaveURL(/\/login/);
});

test('product and batch forms lead to auditable inbound and outbound balances', async ({
  page,
}) => {
  const fixture = await seedFixture(token);
  await signIn(page);
  await expect(page).toHaveURL(/\/dashboard$/);
  await visit(page, '/products');
  await page.getByRole('button', { name: 'Nuevo producto', exact: true }).click();
  let dialog = page.getByRole('dialog');
  const sku = fixture.product.sku + '-UI';
  const name = fixture.product.name + ' UI';
  await dialog.getByLabel('SKU', { exact: true }).fill(sku);
  await dialog.getByLabel('Nombre', { exact: true }).fill(name);
  await select(page, dialog, 'Categoría', fixture.category.name);
  await select(page, dialog, 'Ubicación', fixture.location.name);
  await select(page, dialog, 'Unidad', fixture.unit.name + ' (q)');
  await dialog.getByLabel('Stock mínimo', { exact: true }).fill('2');
  await dialog.getByRole('button', { name: 'Crear producto', exact: true }).click();
  await expect(dialog).toBeHidden();
  const product = (await api('/api/products/active', { token })).find((p) => p.sku === sku);
  expect(product).toBeTruthy();
  await page.getByLabel('Buscar productos', { exact: true }).fill(sku);
  await expect(page.getByText(sku, { exact: true }).first()).toBeVisible();
  await visit(page, '/batches');
  await page.getByRole('button', { name: 'Nuevo lote', exact: true }).click();
  dialog = page.getByRole('dialog');
  const batchCode = fixture.batch.batchCode + '-UI';
  await select(page, dialog, 'Producto', sku + ' · ' + name);
  await dialog.getByLabel('Código de lote', { exact: true }).fill(batchCode);
  await expect(dialog.getByLabel('Cantidad', { exact: true })).toHaveCount(0);
  await dialog.getByRole('button', { name: 'Crear lote', exact: true }).click();
  await expect(dialog).toBeHidden();
  const batch = (
    await api('/api/batches/product/' + product.id + '?size=100', { token })
  ).content.find((b) => b.batchCode === batchCode);
  const selected = { product, batch };
  await confirmMovement(page, await prepareMovement(page, selected, 10));
  await confirmMovement(page, await prepareMovement(page, selected, 2.5, 'Salida'));
  expect(await quantity(selected)).toBe(7.5);
  await visit(page, '/stock');
  await page.getByRole('searchbox').fill(sku);
  await expect(page.locator('.stock-grid article').filter({ hasText: sku })).toContainText('7.5');
  await visit(page, '/audit');
  await expect(page.getByRole('heading', { name: 'Auditoría', exact: true })).toBeVisible();
  const history = await api('/api/stock/movements/batch/' + batch.id, { token });
  expect(history.totalElements).toBe(2);
  expect(history.content.every((m) => m.username === 'admin')).toBe(true);
});

test('a committed movement with a lost response survives reload and has one effect', async ({
  page,
}) => {
  const fixture = await seedFixture(token);
  await signIn(page);
  await expect(page).toHaveURL(/\/dashboard$/);
  const dialog = await prepareMovement(page, fixture, 3.125);
  let firstKey;
  await page.route(
    '**/api/stock/movements',
    async (route) => {
      if (route.request().method() !== 'POST') return route.continue();
      firstKey = route.request().headers()['idempotency-key'];
      const applied = await route.fetch();
      expect(applied.status()).toBe(201);
      await route.abort('connectionreset');
    },
    { times: 1 },
  );
  await dialog.getByRole('button', { name: 'Registrar movimiento', exact: true }).click();
  await expect(dialog.getByRole('button', { name: 'Reintentar envío', exact: true })).toBeVisible();
  expect(await quantity(fixture)).toBe(3.125);
  await page.reload();
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: 'Registrar movimiento', exact: true }).click();
  const recovered = page.getByRole('dialog');
  await expect(recovered.getByLabel('Cantidad', { exact: true })).toBeDisabled();
  const retry = page.waitForRequest(
    (r) => r.method() === 'POST' && new URL(r.url()).pathname === '/api/stock/movements',
  );
  await confirmMovement(page, recovered);
  expect((await retry).headers()['idempotency-key']).toBe(firstKey);
  expect(await quantity(fixture)).toBe(3.125);
  expect(
    (await api('/api/stock/movements/batch/' + fixture.batch.id, { token })).totalElements,
  ).toBe(1);
});

for (const role of ['READ_ONLY', 'WAREHOUSE', 'SUPERVISOR']) {
  test(role + ' permissions apply in navigation and the server', async ({ page }) => {
    const user = await createUser(token, role);
    await signIn(page, user.username);
    await expect(page).toHaveURL(/\/dashboard$/);
    await visit(page, '/movements');
    await expect(
      page.getByRole('button', { name: 'Registrar movimiento', exact: true }),
    ).toHaveCount(role === 'READ_ONLY' ? 0 : 1);
    await page.goto('/users');
    if (role === 'SUPERVISOR')
      await expect(
        page.getByRole('heading', { name: 'Usuarios y roles', exact: true }),
      ).toBeVisible();
    else await expect(page).toHaveURL(/\/forbidden$/);
    if (role === 'READ_ONLY') {
      const readonly = (await login(user.username)).accessToken;
      await api('/api/units', {
        method: 'POST',
        token: readonly,
        expected: 403,
        data: { code: 'DENY', name: 'Denied' },
      });
    }
  });
}

test('a second browser receives live stock and refreshes missed changes on reconnect', async ({
  page,
  browser,
}) => {
  const fixture = await seedFixture(token);
  await signIn(page);
  await expect(page).toHaveURL(/\/dashboard$/);
  const otherContext = await browser.newContext({
    baseURL: settings().url,
    serviceWorkers: 'block',
  });
  try {
    const other = await otherContext.newPage();
    await signIn(other);
    await expect(other).toHaveURL(/\/dashboard$/);
    await visit(other, '/stock');
    await other.getByRole('searchbox').fill(fixture.product.sku);
    await expect(other.locator('[title="Tiempo real: connected"]')).toHaveCount(1);
    const card = other.locator('.stock-grid article').filter({ hasText: fixture.product.sku });
    await api('/api/stock/movements', {
      method: 'POST',
      token,
      expected: 201,
      key: randomUUID(),
      data: movement(fixture, 7.125),
    });
    await expect(card).toContainText('7.125');
    await otherContext.setOffline(true);
    await expect(other.getByText('Estás trabajando sin conexión', { exact: true })).toBeVisible();
    await api('/api/stock/movements', {
      method: 'POST',
      token,
      expected: 201,
      key: randomUUID(),
      data: movement(fixture, 1),
    });
    await otherContext.setOffline(false);
    await expect(other.locator('[title="Tiempo real: connected"]')).toHaveCount(1);
    await expect(card).toContainText('8.125');
  } finally {
    await otherContext.close();
  }
});

test('offline submission never changes stock or replays automatically after reconnection', async ({
  page,
  context,
}) => {
  const fixture = await seedFixture(token);
  await signIn(page);
  await expect(page).toHaveURL(/\/dashboard$/);
  const dialog = await prepareMovement(page, fixture, 0.5);
  await context.setOffline(true);
  await expect(page.getByText('Estás trabajando sin conexión', { exact: true })).toBeVisible();
  await dialog.getByRole('button', { name: 'Registrar movimiento', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText(/conexión/i);
  expect(await quantity(fixture)).toBe(0);
  await context.setOffline(false);
  await expect(page.getByText('Estás trabajando sin conexión', { exact: true })).toBeHidden();
  expect(await quantity(fixture)).toBe(0);
  await confirmMovement(page, dialog);
  expect(await quantity(fixture)).toBe(0.5);
});
